package io.vertx.openshift.it;

import com.google.common.collect.ImmutableMap;
import com.jayway.restassured.RestAssured;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.utils.Base64Encoder;
import io.vertx.core.json.JsonObject;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileNotFoundException;

import static com.jayway.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
@RunWith(Arquillian.class)
@RunAsClient
public class ConfigurationIT extends AbstractKubernetesIT {


  private Route route;
  private ConfigMap config;
  private Secret secret;

  @Before
  public void prepare() throws FileNotFoundException, InterruptedException {
    initializeServiceAccount();

    config = oc.configMaps().withName("my-config-map").get();
    if (config == null) {
      config = oc.configMaps().createNew()
          .withNewMetadata().withName("my-config-map").endMetadata()
          .withData(ImmutableMap.of("key", "value", "date", Long.toString(System.currentTimeMillis())))
          .done();
    }

    secret = oc.secrets().withName("my-secret").get();
    if (secret == null) {
      secret = oc.secrets().createNew()
          .withNewMetadata().withName("my-secret").endMetadata()
          .withData(ImmutableMap.of("password", encode("secret")))
          .done();
    }

    route = initializeHttpEndpoint();

    awaitUntilAllPodsAreReady();
    awaitUntilRouteIsServed(route);
  }

  private String encode(String secret) {
    return Base64Encoder.encode(secret);
  }

  public Route initializeHttpEndpoint() throws FileNotFoundException {
    String name = "http-endpoint-config";


    DeploymentConfig deployment = oc.deploymentConfigs().withName(name).get();
    assertThat(deployment).isNotNull();
    Service endpoint = client.services().withName(name).get();
    if (endpoint == null) {
      endpoint = createDefaultService(name, "http-endpoint");
    }


    route = oc.routes().withName(name).get();
    if (route != null) {
      // Delete and recreate
      oc.routes().withName(name).delete();
    }
    route = expose(oc, endpoint);
    return route;
  }

  @Test
  public void testRetrievingConfig() throws InterruptedException {
    System.out.println(url(route));
    String response =
        RestAssured.get(url(route, "/all")).then().assertThat().statusCode(200).extract().asString();


    JsonObject json = new JsonObject(response);

    assertThat(json.getLong("date")).isNotNull().isPositive();
    assertThat(json.getString("key")).isEqualTo("value");
    assertThat(json.getString("java.io.tmpdir")).isEqualTo("/tmp");
    assertThat(json.getString("HOSTNAME")).startsWith("http-endpoint-config");
    assertThat(json.getString("KUBERNETES_NAMESPACE")).isEqualToIgnoringCase(client.getNamespace());
//    assertThat(json.getString("password")).isEqualTo("secret");

    oc.configMaps().withName(name(config)).edit()
        .withData(ImmutableMap.of("key", "value-2",
            "date", Long.toString(System.currentTimeMillis()),
            "yet", "another"))
        .done();

    await().until(() -> RestAssured.get(url(route, "/all")).asString().contains("another"));

    response =
        RestAssured.get(url(route, "/all")).then().assertThat().statusCode(200).extract().asString();

    json = new JsonObject(response);
    assertThat(json.getLong("date")).isNotNull().isPositive();
    assertThat(json.getString("key")).isEqualTo("value-2");
    assertThat(json.getString("yet")).isEqualTo("another");
    assertThat(json.getString("java.io.tmpdir")).isEqualTo("/tmp");
    assertThat(json.getString("HOSTNAME")).startsWith("http-endpoint-config");
    assertThat(json.getString("KUBERNETES_NAMESPACE")).isEqualToIgnoringCase(client.getNamespace());
  }


}
