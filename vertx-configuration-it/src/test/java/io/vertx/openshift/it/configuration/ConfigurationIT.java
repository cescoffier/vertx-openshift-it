package io.vertx.openshift.it.configuration;

import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.OpenShiftClient;
import io.vertx.core.json.JsonObject;
import io.vertx.it.openshift.utils.OC;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.URL;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.jayway.awaitility.Awaitility.await;
import static io.restassured.RestAssured.get;
import static io.vertx.it.openshift.utils.Ensure.ensureThat;
import static io.vertx.it.openshift.utils.Kube.*;
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
    oc = oc(client);
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
    route = createRouteForService(client, NAME, true);

    ensureThat("the application has successfully started", () -> {
      awaitUntilAllPodsAreReady(client);
      awaitUntilRouteIsServed(route);
      awaitUntilRouteIsServed(route, "/all");
    });
  }


  @Test
  public void testRetrievingConfig() throws InterruptedException {
    final AtomicReference<String> response = new AtomicReference<>();
    URL url = urlForRoute(route, "/all");

    ensureThat("we can retrieve the application configuration", () -> {
      String r = awaitUntilRouteIsServed(route, "/all");
      System.out.println("=====> " + r);
      response.set(r);
    });


    ensureThat("the configuration is the expected configuration", () -> {
      JsonObject json = new JsonObject(response.get());
      assertThat(json.getLong("date")).isNotNull().isPositive();
      assertThat(json.getString("key")).isEqualTo("value");
      assertThat(json.getString("java.io.tmpdir")).isEqualTo("/tmp");
      assertThat(json.getString("HOSTNAME")).startsWith("http-endpoint-config");
      assertThat(json.getString("KUBERNETES_NAMESPACE")).isEqualToIgnoringCase(client.getNamespace());
    });

    oc.configMaps().withName(config.getMetadata().getName()).edit()
      .withData(ImmutableMap.of("key", "value-2",
        "date", Long.toString(System.currentTimeMillis()),
        "yet", "another"))
      .done();

    ensureThat("the new configuration has been read", () -> {
      AtomicReference<String> reference = new AtomicReference<>();
      await().atMost(1, TimeUnit.MINUTES).until(() -> {
        String r = awaitUntilRouteIsServed(route, "/all");
        reference.set(r);
        return r.contains("value-2")  && r.contains("another");
      });
      response.set(reference.get());
    });

    ensureThat("the new configuration is the expected configuration", () -> {
      JsonObject json = new JsonObject(response.get());
      assertThat(json.getLong("date")).isNotNull().isPositive();
      assertThat(json.getString("key")).isEqualTo("value-2");
      assertThat(json.getString("yet")).isEqualTo("another");
      assertThat(json.getString("java.io.tmpdir")).isEqualTo("/tmp");
      assertThat(json.getString("HOSTNAME")).startsWith("http-endpoint-config");
      assertThat(json.getString("KUBERNETES_NAMESPACE")).isEqualToIgnoringCase(client.getNamespace());
    });
  }

  public void initializeServiceAccount() {
    OC.execute("policy", "add-role-to-user", "view", "admin", "-n", client.getNamespace());
    OC.execute("policy", "add-role-to-group", "view",
      "system:serviceaccounts", "-n", client.getNamespace());
  }
}
