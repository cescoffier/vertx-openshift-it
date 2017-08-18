package io.vertx.openshift.it;

import static com.jayway.awaitility.Awaitility.await;
import static com.jayway.restassured.RestAssured.get;

import static io.vertx.it.openshift.utils.Ensure.ensureThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.restassured.RestAssured;
import io.fabric8.kubernetes.api.model.Pod;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;
import com.jayway.restassured.path.json.JsonPath;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.DoneableConfigMap;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.Route;
import io.vertx.it.openshift.utils.AbstractTestClass;
import io.vertx.it.openshift.utils.OC;

public class ConfigurationIT extends AbstractTestClass {
  private final String HTTP_CONFIG_EXPECTED_STRING = "Congratulations, you have just served a configuration over HTTP !";
  private final String EVENT_BUS_EXPECTED_STRING = "Hello configuration from the event bus !";

  private static final String CONFIG_MAP = "my-config-map";
  private static final ImmutableMap<String, String> DEFAULT_MAP = ImmutableMap.of(
    "key", "value",
    "date", Long.toString(System.currentTimeMillis()));

  @BeforeClass
  public static void initialize() throws IOException {
    String eventbusBaseUri, httpBaseUri, configBaseUri;
    String eventbusServiceApp = "eventbus-service";
    String httpServiceApp = "http-service";
    String configServiceApp = "config-service";

    createOrEditConfigMap(DEFAULT_MAP);
    OC.initializeServiceAccount(client.getNamespace());
    OC.initializeSystemServiceAccount(client.getNamespace());

    eventbusBaseUri = deployApp(eventbusServiceApp, System.getProperty("eventbusServiceTemplate"));

    httpBaseUri = deployApp(httpServiceApp, System.getProperty("httpServiceTemplate"));

    configBaseUri = deployApp(configServiceApp, System.getProperty("configServiceTemplate"));

    await().atMost(5, TimeUnit.MINUTES).until(() -> {
      List<Pod> list = client.pods().inNamespace(deploymentAssistant.project()).list().getItems();
      return list.stream()
        .filter(pod -> pod.getMetadata().getName().startsWith(eventbusServiceApp)
          || pod.getMetadata().getName().startsWith(httpServiceApp)
          || pod.getMetadata().getName().startsWith(configServiceApp))
        .filter(pod -> "running".equalsIgnoreCase(pod.getStatus().getPhase())).collect(Collectors.toList()).size() >= 3;
    });

    System.out.println("Pods running, waiting for probes...");

    await("Pods running, waiting for probes...").pollInterval(1, TimeUnit.SECONDS).atMost(6, TimeUnit.MINUTES).catchUncaughtExceptions().until(() ->
      get(configBaseUri + "/all").getStatusCode() == 200
      && get(eventbusBaseUri + "/eventbus").getStatusCode() == 200
      && get(httpBaseUri + "/conf").getStatusCode() == 200
    );

    get(eventbusBaseUri + "/eventbus"); // Just in case, to call event bus publish

    RestAssured.baseURI = configBaseUri;
  }

  private static void createOrEditConfigMap(Map<String, String> content) {
    DoneableConfigMap cfgMap;
    if (client.configMaps().withName(CONFIG_MAP).get() != null) {
      cfgMap = client.configMaps().withName(CONFIG_MAP).edit();
    } else {
      cfgMap = client.configMaps().createNew();
    }

    cfgMap.withNewMetadata().withName(CONFIG_MAP).endMetadata()
      .withData(content)
      .done();
  }

  @Test
  public void testRetrievingConfig() throws InterruptedException {

    ensureThat("we can retrieve the application configuration", () -> {
      await().atMost(2, TimeUnit.MINUTES).until(() ->
        get("/all").getBody().jsonPath().getString("eventBus") != null); // So event bus config store has some time to load
    });
    ensureThat("the configuration is the expected configuration", () -> {
      final JsonPath response = get("/all").getBody().jsonPath();
      softly.assertThat(response.getDouble("date")).isNotNull().isPositive();
      softly.assertThat(response.getString("key")).isEqualTo("value");
      softly.assertThat(response.getString("HOSTNAME")).startsWith("config-service");
      softly.assertThat(response.getString("KUBERNETES_NAMESPACE")).isEqualToIgnoringCase(client.getNamespace());
      softly.assertThat(response.getInt("'http.port'")).isNotNull().isNotNegative();
      softly.assertThat(response.getString("propertiesExampleOption")).isEqualTo("A properties example option");
      softly.assertThat(response.getString("jsonExampleOption")).isEqualTo("A JSON example option");
      softly.assertThat(response.getString("toBeOverwritten")).isEqualTo("This is defined in YAML file.");
      softly.assertThat(response.getString("'map.items'.mapItem1")).isEqualTo("Overwrites value in JSON config file");
      softly.assertThat(response.getInt("'map.items'.mapItem2")).isEqualTo(0);
      softly.assertThat(response.getString("httpConfigContent")).isEqualTo(HTTP_CONFIG_EXPECTED_STRING);
//      softly.assertThat(response.getString("dirConfigKey2")).isEqualTo("How to achieve perfection");
      softly.assertThat(response.getString("eventBus")).isEqualTo(EVENT_BUS_EXPECTED_STRING);
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
      softly.assertThat(response.getString("HOSTNAME")).startsWith("config-service");
      softly.assertThat(response.getString("KUBERNETES_NAMESPACE")).isEqualToIgnoringCase(client.getNamespace());
      softly.assertThat(response.getInt("'http.port'")).isNotNull().isNotNegative();
      softly.assertThat(response.getString("propertiesExampleOption")).isEqualTo("A properties example option");
      softly.assertThat(response.getString("jsonExampleOption")).isEqualTo("A JSON example option");
      softly.assertThat(response.getString("toBeOverwritten")).isEqualTo("This is defined in YAML file.");
      softly.assertThat(response.getString("'map.items'.mapItem1")).isEqualTo("Overwrites value in JSON config file");
      softly.assertThat(response.getInt("'map.items'.mapItem2")).isEqualTo(0);
      softly.assertThat(response.getString("httpConfigContent")).isEqualTo(HTTP_CONFIG_EXPECTED_STRING);
//      softly.assertThat(response.getString("dirConfigKey2")).isEqualTo("How to achieve perfection");
      softly.assertThat(response.getString("eventBus")).isEqualTo(EVENT_BUS_EXPECTED_STRING);
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
    OC.removeSystemServiceAccount(client.getNamespace());
  }


  private static String deployApp(String name, String templatePath) throws IOException {
    String appName;
    List<? extends HasMetadata> entities = deploymentAssistant.deploy(name, new File(templatePath));

    Optional<String> first = entities.stream().filter(hm -> hm instanceof DeploymentConfig).map(hm -> (DeploymentConfig) hm)
      .map(dc -> dc.getMetadata().getName()).findFirst();
    if (first.isPresent()) {
      appName = first.get();
    } else {
      throw new IllegalStateException("Application deployment config not found");
    }
    Route route = deploymentAssistant.client().routes().inNamespace(deploymentAssistant.project()).withName(appName).get();
    assertThat(route).isNotNull();
    return "http://" + route.getSpec().getHost();
  }
}
