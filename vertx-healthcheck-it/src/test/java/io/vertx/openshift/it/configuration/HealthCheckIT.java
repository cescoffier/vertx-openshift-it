package io.vertx.openshift.it.configuration;

import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.OpenShiftClient;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import io.vertx.it.openshift.utils.OC;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.URL;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.jayway.awaitility.Awaitility.await;
import static io.vertx.it.openshift.utils.Ensure.ensureThat;
import static io.vertx.it.openshift.utils.Kube.*;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Arquillian.class)
@RunAsClient
public class HealthCheckIT {

  private static final String NAME = "vertx-healthcheck-it";

  @ArquillianResource
  DefaultKubernetesClient client;

  private OpenShiftClient oc;

  private Route route;

  @Before
  public void initialize() {
    oc = oc(client);

    // The route is exposed using .vagrant.f8 suffix, delegate to openshift to
    // get a public URL
    route = createRouteForService(client, NAME, true);

    ensureThat("the application has successfully started", () -> {
      awaitUntilAllPodsAreReady(client);
      awaitUntilRouteIsServed(route);
      awaitUntilRouteIsServed(route, "/");
    });
  }

  @After
  public void tearDown() {
    RestAssured.get(urlForRoute(route, "/checks/reset")).then().statusCode(200);
  }


  @Test
  @Ignore("Ignore until the fabric8 maven plugin integrate the Vert.x healtch checks")
  public void testProbes() throws Exception {
    DeploymentConfig dc = oc.deploymentConfigs().withName(NAME).get();

    ensureThat("the deployment config " + NAME + " exists", () -> assertThat(dc).isNotNull());

    ensureThat("the liveness probe is configured correctly", () -> {
      Probe livenessProbe = dc.getSpec().getTemplate().getSpec().getContainers().get(0).getLivenessProbe();
      assertThat(livenessProbe).isNotNull();
      assertThat(livenessProbe.getHttpGet().getPort()).isEqualTo(8080);
      assertThat(livenessProbe.getHttpGet().getPath()).isEqualTo("/health");
    });

    ensureThat("the readiness probe is configured correctly", () -> {
      Probe readinessProbe = dc.getSpec().getTemplate().getSpec().getContainers().get(0).getReadinessProbe();
      assertThat(readinessProbe).isNotNull();
      assertThat(readinessProbe.getHttpGet().getPort()).isEqualTo(8080);
      assertThat(readinessProbe.getHttpGet().getPath()).isEqualTo("/health");
    });
  }

  @Test
  public void testWithoutProcedure() throws InterruptedException {
    URL url = urlForRoute(route, "/health");
    ensureThat("we can retrieve the state and it's a 204", () -> {
      awaitUntilRouteIsServed(route, "/health");
      Response resp = RestAssured.get(url).andReturn();
      assertThat(resp.statusCode()).isEqualTo(204);
    });
  }


  @Test
  public void testWithOkProcedure() throws InterruptedException {
    ensureThat("we can add a health check procedure", () -> {
      URL add = urlForRoute(route, "/checks/ok");
      RestAssured.get(add).then().assertThat().statusCode(200);
    });

    URL url = urlForRoute(route, "/health");
    ensureThat("we can retrieve the state and it's a 200", () -> {
      awaitUntilRouteIsServed(route, "/health");
      Response resp = RestAssured.get(url).andReturn();
      assertThat(resp.statusCode()).isEqualTo(200);
    });
  }

  @Test
  public void testWithKoProcedure() throws InterruptedException {
    ensureThat("we can add a health check procedure", () -> {
      URL add = urlForRoute(route, "/checks/ko");
      RestAssured.get(add).then().assertThat().statusCode(200);
    });

    URL url = urlForRoute(route, "/health");
    ensureThat("we can retrieve the state and it's a 503", () -> {
      Response resp = RestAssured.get(url).andReturn();
      assertThat(resp.statusCode()).isEqualTo(503);
    });
  }
}
