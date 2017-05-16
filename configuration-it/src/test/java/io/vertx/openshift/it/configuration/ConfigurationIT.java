package io.vertx.openshift.it.configuration;

import static com.jayway.awaitility.Awaitility.await;
import static com.jayway.restassured.RestAssured.defaultParser;
import static com.jayway.restassured.RestAssured.get;

import static io.vertx.it.openshift.utils.Ensure.ensureThat;

import org.junit.AfterClass;
import org.junit.Before;
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
      softly.assertThat(response.getInt("'http.port'")).isNotNull().isNotNegative();
      softly.assertThat(response.getString("propertiesExampleOption")).isEqualTo("A properties example option");
      softly.assertThat(response.getString("jsonExampleOption")).isEqualTo("A JSON example option");
      softly.assertThat(response.getString("toBeOverwritten")).isEqualTo("This is defined in YAML file.");
      softly.assertThat(response.getString("'map.items'.mapItem1")).isEqualTo("Overwrites value in JSON config file");
      softly.assertThat(response.getInt("'map.items'.mapItem2")).isEqualTo(0);
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
      await().atMost(2, TimeUnit.MINUTES).until(() ->
        get("/all").getBody().jsonPath().getString("key").equals("value-2"));
    });

    ensureThat("the new configuration is the expected configuration", () -> {
      final JsonPath response = get("/all").getBody().jsonPath();
      softly.assertThat(response.getDouble("date")).isNotNull().isPositive();
      softly.assertThat(response.getString("key")).isEqualTo("value-2");
      softly.assertThat(response.getString("yet")).isEqualTo("another");
      softly.assertThat(response.getString("HOSTNAME")).startsWith("vertx-configuration-it");
      softly.assertThat(response.getString("KUBERNETES_NAMESPACE")).isEqualToIgnoringCase(client.getNamespace());
      softly.assertThat(response.getInt("'http.port'")).isNotNull().isNotNegative();
      softly.assertThat(response.getString("propertiesExampleOption")).isEqualTo("A properties example option");
      softly.assertThat(response.getString("jsonExampleOption")).isEqualTo("A JSON example option");
      softly.assertThat(response.getString("toBeOverwritten")).isEqualTo("This is defined in YAML file.");
      softly.assertThat(response.getString("'map.items'.mapItem1")).isEqualTo("Overwrites value in JSON config file");
      softly.assertThat(response.getInt("'map.items'.mapItem2")).isEqualTo(0);
    });

    createOrEditConfigMap(DEFAULT_MAP);
    ensureThat("the old configuration can be read", () -> {
      await().atMost(2, TimeUnit.MINUTES).until(() ->
        get("/all").getBody().jsonPath().getString("key").equals("value"));
    });
  }

  @Test
  public void testDeleteConfig() throws InterruptedException {
    client.configMaps().withName(CONFIG_MAP).delete();
    ensureThat("empty config map is returned when the actual one is deleted", () -> {
      await().atMost(2, TimeUnit.MINUTES).until(() ->
        get("/all").getBody().jsonPath().getString("key") == null);
    });

    createOrEditConfigMap(DEFAULT_MAP);
    await().atMost(2, TimeUnit.MINUTES).until(() ->
      get("/all").getBody().jsonPath().getString("key") != null);
  }

  @AfterClass
  public static void clean() {
    client.configMaps().withName(CONFIG_MAP).withGracePeriod(0).delete();
    OC.removeServiceAccount(client.getNamespace());
  }
}
