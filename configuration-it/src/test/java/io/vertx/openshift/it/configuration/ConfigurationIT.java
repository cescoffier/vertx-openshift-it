package io.vertx.openshift.it.configuration;

import static com.jayway.awaitility.Awaitility.await;
import static com.jayway.restassured.RestAssured.get;

import static io.vertx.it.openshift.utils.Ensure.ensureThat;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;
import com.jayway.restassured.path.json.JsonPath;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.vertx.it.openshift.utils.AbstractTestClass;
import io.vertx.it.openshift.utils.OC;

public class ConfigurationIT extends AbstractTestClass {

  public static final String CONFIG_MAP = "my-config-map";
  public static final ImmutableMap<String, String> DEFAULT_MAP = ImmutableMap.of(
    "key", "value",
    "date", Long.toString(System.currentTimeMillis()));

  @BeforeClass
  public static void initialize() throws IOException {
    createOrEditConfigMap(DEFAULT_MAP);
    OC.initializeServiceAccount(client.getNamespace());
    deployAndAwaitStartWithRoute("/all");
  }

  private static void createOrEditConfigMap(Map<String, String> content) {
    client.configMaps().createOrReplaceWithNew()
      .withNewMetadata().withName(CONFIG_MAP).endMetadata()
      .withData(content)
      .done();
  }

  @Test
  public void testRetrievingConfig() throws InterruptedException {

    ensureThat("we can retrieve the application configuration", () -> {
      get("/all").then().statusCode(200);
    });
    ensureThat("the configuration is the expected configuration", () -> {
      final JsonPath response = get("/all").getBody().jsonPath();
      softly.assertThat(response.getDouble("date")).isNotNull().isPositive();
      softly.assertThat(response.getString("key")).isEqualTo("value");
      softly.assertThat(response.getString("HOSTNAME")).startsWith("vertx-configuration-it");
      softly.assertThat(response.getString("KUBERNETES_NAMESPACE")).isEqualToIgnoringCase(client.getNamespace());
    });
  }

  @Test
  public void testChangingConfiguration() throws InterruptedException {
    createOrEditConfigMap(
      ImmutableMap.of(
        "key", "value-2",
        "date", Long.toString(System.currentTimeMillis()),
        "yet", "another")
    );


    ensureThat("the new configuration has been read", () -> {
      await().atMost(1, TimeUnit.MINUTES).until(() ->
        get("/all").getBody().jsonPath().getString("key").equals("value-2"));
    });

    ensureThat("the new configuration is the expected configuration", () -> {
      final JsonPath response = get("/all").getBody().jsonPath();
      softly.assertThat(response.getDouble("date")).isNotNull().isPositive();
      softly.assertThat(response.getString("key")).isEqualTo("value-2");
      softly.assertThat(response.getString("yet")).isEqualTo("another");
      softly.assertThat(response.getString("HOSTNAME")).startsWith("vertx-configuration-it");
      softly.assertThat(response.getString("KUBERNETES_NAMESPACE")).isEqualToIgnoringCase(client.getNamespace());
    });

    createOrEditConfigMap(DEFAULT_MAP);
    ensureThat("the old configuration can be read", () -> {
      await().atMost(2, TimeUnit.MINUTES).until(() -> {
        get("/all").getBody().jsonPath().getString("key").equals("value");
      });
    });
  }

  @AfterClass
  public static void clean() {
    client.configMaps().withName(CONFIG_MAP).withGracePeriod(0).delete();
    OC.removeServiceAccount(client.getNamespace());
  }
}
