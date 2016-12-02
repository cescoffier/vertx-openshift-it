package io.vertx.openshift.it.configuration;

import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.assertions.internal.Assertions;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.OpenShiftClient;
import io.vertx.core.json.JsonObject;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.jayway.awaitility.Awaitility.await;
import static io.restassured.RestAssured.get;
import static io.vertx.openshift.it.configuration.OpenshiftHelper.oc_execute;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Arquillian.class)
@RunAsClient
public class ConfigurationIT {

  private static final String NAME = "http-endpoint-config";

  @ArquillianResource
  DefaultKubernetesClient client;

  private OpenShiftClient oc;

  private Route route;
  private ConfigMap config;

  @Before
  public void initialize() {
    oc = client.adapt(OpenShiftClient.class);
    initializeServiceAccount();

    config = oc.configMaps().withName("my-config-map").get();
    if (config == null) {
      config = oc.configMaps().createNew()
        .withNewMetadata().withName("my-config-map").endMetadata()
        .withData(ImmutableMap.of("key", "value", "date", Long.toString(System.currentTimeMillis())))
        .done();
    }

    // The route is exposed using .vagrant.f8 suffix, delegate to openshift to
    // get a public URL
    oc.routes()
      .withName(NAME).delete();

    Route route = oc.routes().createNew()
      .withNewMetadata().withName(NAME).endMetadata()
      .withNewSpec()
      .withNewTo().withName(NAME).withKind("Service").endTo()
      .endSpec()
      .done();

    Assertions.assertThat(route).isNotNull();
    this.route = route;

    awaitUntilAllPodsAreReady();
    awaitUntilRouteIsServed();
  }


  @Test
  public void testRetrievingConfig() throws InterruptedException {
    System.out.println(url(null));
    String response =
      get(url("/all")).then().assertThat().statusCode(200).extract().asString();


    JsonObject json = new JsonObject(response);

    assertThat(json.getLong("date")).isNotNull().isPositive();
    assertThat(json.getString("key")).isEqualTo("value");
    assertThat(json.getString("java.io.tmpdir")).isEqualTo("/tmp");
    assertThat(json.getString("HOSTNAME")).startsWith("http-endpoint-config");
    assertThat(json.getString("KUBERNETES_NAMESPACE")).isEqualToIgnoringCase(client.getNamespace());
//    assertThat(json.getString("password")).isEqualTo("secret");

    oc.configMaps().withName(config.getMetadata().getName()).edit()
      .withData(ImmutableMap.of("key", "value-2",
        "date", Long.toString(System.currentTimeMillis()),
        "yet", "another"))
      .done();

    await().until(() -> get(url("/all")).asString().contains("another"));

    response =
      get(url("/all")).then().assertThat().statusCode(200).extract().asString();

    json = new JsonObject(response);
    assertThat(json.getLong("date")).isNotNull().isPositive();
    assertThat(json.getString("key")).isEqualTo("value-2");
    assertThat(json.getString("yet")).isEqualTo("another");
    assertThat(json.getString("java.io.tmpdir")).isEqualTo("/tmp");
    assertThat(json.getString("HOSTNAME")).startsWith("http-endpoint-config");
    assertThat(json.getString("KUBERNETES_NAMESPACE")).isEqualToIgnoringCase(client.getNamespace());
  }

  public void initializeServiceAccount() {
    oc_execute("policy", "add-role-to-user", "view", "admin", "-n", client.getNamespace());
    oc_execute("policy", "add-role-to-group", "view",
      "system:serviceaccounts", "-n", client.getNamespace());
  }

  public void awaitUntilRouteIsServed() {
    await().atMost(3, TimeUnit.MINUTES).until(() -> {
      try {
        return get(url(null)).getStatusCode() < 500;
      } catch (Exception e) {
        return false;
      }
    });
  }

  public void awaitUntilAllPodsAreReady() {
    await().atMost(2, TimeUnit.MINUTES).until(() -> {
      List<Pod> items = client.pods().list().getItems();
      for (Pod pod : items) {
        if (!KubernetesHelper.isPodReady(pod)) {
          return false;
        }
      }
      return true;
    });
  }

  public URL url(String path) {
    try {
      return new URL("http://" + route.getSpec().getHost() + (path != null ? path : ""));
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }
}
