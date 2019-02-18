package io.vertx.openshift.healthcheck;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.client.OpenShiftClient;
import io.restassured.RestAssured;
import io.vertx.it.openshift.utils.OpenShiftHelper;
import org.arquillian.cube.openshift.impl.enricher.AwaitRoute;
import org.arquillian.cube.openshift.impl.enricher.RouteURL;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.URL;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.restassured.RestAssured.get;
import static io.vertx.it.openshift.utils.Ensure.ensureThat;
import static io.vertx.openshift.healthcheck.HealthCheckHttpVerticle.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Awaitility.with;

@RunWith(Arquillian.class)
public class HealthCheckIT {

  private static final String HEALTH = "health";

  private static final String APP_NAME = System.getProperty("app.name");

  private static boolean setUpDone = false;

  private OpenShiftHelper helper;

  @RouteURL("healthcheck-it")
  @AwaitRoute(path = "/health", timeout = 15, timeoutUnit = TimeUnit.SECONDS, statusCode = 204)
  private URL route;

  @ArquillianResource
  private OpenShiftClient client;

  @Before
  public void setUp() {
    RestAssured.baseURI = route.toString();
    helper = new OpenShiftHelper(client, APP_NAME);
  }

  @After
  public void resetHealthChecks() {
    get(RESET).then().statusCode(200);
  }

  @Test
  public void testLivenessIsPresent() throws Exception {
    final List<Container> containers = getContainers();
    ensureThat("the liveness probe is configured correctly", () -> {
      containers.forEach(c -> {
        final Probe livenessProbe = c.getLivenessProbe();
        assertThat(livenessProbe).as("Liveness probe should not be null.").isNotNull();
        assertThat(livenessProbe.getHttpGet().getPort().getIntVal()).as("port should be defined for liveness probe").isEqualTo(8088);
        assertThat(livenessProbe.getHttpGet().getPath()).as("path should be defined for liveness probe").isEqualTo("/isAlive");
      });
    });
  }

  @Test
  public void testReadinessIsPresent() throws Exception {
    final List<Container> containers = getContainers();
    ensureThat("the readiness probe is configured correctly", () -> {
      containers.forEach(c -> {
        Probe readinessProbe = c.getReadinessProbe();
        assertThat(readinessProbe).as("Readiness probe should not be null.").isNotNull();
        assertThat(readinessProbe.getHttpGet().getPort().getIntVal()).as("port should be defined for readiness probe").isEqualTo(8088);
        assertThat(readinessProbe.getHttpGet().getPath()).as("path should be defined for readiness probe").isEqualTo("/start");
      });
    });
  }

  private List<Container> getContainers() {
    DeploymentConfig dc = client.deploymentConfigs().withName(APP_NAME).get();
    ensureThat("the deployment config " + APP_NAME + " exists", () -> assertThat(dc).isNotNull());
    return dc.getSpec().getTemplate().getSpec().getContainers();
  }

  @Test
  public void testWithoutProcedure() throws InterruptedException {
    ensureThat("we can retrieve the state and it's a 204", () -> {
      get(HEALTH).then().statusCode(204);
    });
  }

  @Test
  public void testWithOkProcedure() throws InterruptedException {
    ensureThat("we can add a health check procedure", () -> {
      get(CHECKS_OK).then().assertThat().statusCode(200);
    });

    ensureThat("we can retrieve the state and it's a 200", () -> {
      get(HEALTH).then().statusCode(200);
    });
  }

  @Test
  public void testWithKoProcedure() throws InterruptedException {
    ensureThat("we can add a health check procedure", () -> {
      get(CHECKS_CONTENT_KO).then().statusCode(200);
    });

    ensureThat("we can retrieve the state and it's a 503", () -> {
      get(HEALTH).then().statusCode(503);
    });
  }

  @Test
  public void testRealHealthChecksAreNotExposed() throws InterruptedException {
    ensureThat("real health checks are not exposed", () -> {
      get("start").then().statusCode(404);
      get("isAlive").then().statusCode(404);
    });
  }

  @Test
  public void testScaledReplicasDoesNotSeeFailure() {
    helper.setReplicasAndWait(2);
    ensureThat("server can be killed", () -> {
      get("killOne").then().statusCode(200);
    });
    ensureThat("pod should be suspected by kubernetes.", () -> {
      await().atMost(4, TimeUnit.MINUTES).catchUncaughtExceptions().until(() -> helper.getReadyPods().size() == 1);
      get(HEALTH).then().statusCode(204);
    });
    AtomicBoolean wasAcessibleAllTheTime = new AtomicBoolean(true);
    ensureThat("new pod should be started", () ->
      with().conditionEvaluationListener(condition -> {
        if (get(HEALTH).getStatusCode() != 204) {
          System.out.printf("%s (elapsed time %dms, remaining time %dms)\n", condition.getDescription(), condition.getElapsedTimeInMS(), condition.getRemainingTimeInMS());
          wasAcessibleAllTheTime.set(false);
        }
      }).
        await().
        atMost(4, TimeUnit.MINUTES)
        .until(() -> helper.getReadyPods().size() == 2)
    );
    assertThat(wasAcessibleAllTheTime.get()).as("Application should be acessible even when one pod is ready.").isEqualTo(true);
    helper.setReplicasAndWait(1);
  }

  @Test
  public void testRealHealthCheckRestartPod() {
    ensureThat("server can be killed", () -> {
      get("killOne").then().statusCode(200);
    });
    ensureThat("killed server does not affect testing healthcheck", () -> {
      get(HEALTH).then().statusCode(204);
    });
    ensureThat("pod should be suspected by kubernetes.", () ->
      await().atMost(4, TimeUnit.MINUTES).catchUncaughtExceptions().until(() -> get(HEALTH).getStatusCode() != 204)
    );
    ensureThat("new pod should be started", () ->
      await().atMost(4, TimeUnit.MINUTES).catchUncaughtExceptions().until(() -> get(HEALTH).getStatusCode() == 204)
    );
  }

  @Test
  public void testRealHealthCheckTimeoutRestartPod() {
    ensureThat("server can be killed", () -> {
      get(REAL_CHECKS_TIMEOUT).then().statusCode(200);
    });
    ensureThat("killed server does not affect testing healthcheck", () -> {
      get(HEALTH).then().statusCode(204);
    });
    ensureThat("pod should be suspected by kubernetes.", () ->
      await().atMost(4, TimeUnit.MINUTES).catchUncaughtExceptions().until(() -> get(HEALTH).getStatusCode() != 204)
    );
    ensureThat("new pod should be started", () ->
      await().atMost(4, TimeUnit.MINUTES).catchUncaughtExceptions().until(() -> get(HEALTH).getStatusCode() == 204)
    );
  }

  @Test
  public void testRealHealthCheckExceptionRestartPod() {
    ensureThat("server can be killed", () -> {
      get(HealthCheckHttpVerticle.REAL_CHECKS_THROW_EXCEPTION).then().statusCode(200);
    });
    ensureThat("killed server does not affect testing healthcheck", () -> {
      get(HEALTH).then().statusCode(204);
    });
    ensureThat("pod should be suspected by kubernetes.", () ->
      await().atMost(6, TimeUnit.MINUTES).catchUncaughtExceptions().until(() -> get(HEALTH).getStatusCode() != 204)
    );
    ensureThat("new pod should be started", () ->
      await().atMost(4, TimeUnit.MINUTES).catchUncaughtExceptions().until(() -> get(HEALTH).getStatusCode() == 204)
    );
  }


  @Test
  public void testWithBothProcedure() throws InterruptedException {
    ensureThat("we can add a health check procedure", () -> {
      get(CHECKS_CONTENT_KO).then().statusCode(200);
      get(CHECKS_CONTENT_OK).then().statusCode(200);
    });

    ensureThat("we can retrieve the state and it's a 503", () -> {
      get(HEALTH).then().statusCode(503);
    });
  }
}
