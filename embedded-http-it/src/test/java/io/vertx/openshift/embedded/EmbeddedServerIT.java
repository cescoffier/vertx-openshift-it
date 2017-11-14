package io.vertx.openshift.embedded;

import static org.hamcrest.Matchers.containsString;

import static org.awaitility.Awaitility.await;
import static io.restassured.RestAssured.get;

import static io.fabric8.kubernetes.assertions.Assertions.assertThat;
import static io.vertx.it.openshift.utils.Ensure.ensureThat;
import static io.vertx.it.openshift.utils.Kube.setReplicasAndWait;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import io.vertx.it.openshift.utils.AbstractTestClass;


/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class EmbeddedServerIT extends AbstractTestClass {


  @BeforeClass
  public static void initialize() throws IOException {
    deployAndAwaitStartWithRoute();

  }

  @Test
  public void testContent() {
    ensureThat("the route is served correctly", () ->
      get().then().assertThat()
        .statusCode(200)
        .body(containsString("Hello World!")));
  }


  @Test
  public void testAppProvisionsRunningPods() throws Exception {
    ensureThat("the pod is running for 10 seconds", () ->
      assertThat(deploymentAssistant.client()).deployments().pods().isPodReadyForPeriod());
  }


  @Test
  public void testWithTwoReplicas() {
    ensureThat("the pods are ready after we update the number of replicas to 2",
      () -> setReplicasAndWait(deploymentAssistant.client(), deploymentAssistant.applicationName(), 2));

    ensureThat("the route is accessible",
      () -> await().atMost(1, TimeUnit.MINUTES).until(() -> get().then().statusCode(200)));

    ensureThat("the route can be called several times in a row", () -> {

      get().then().assertThat().statusCode(200)
        .body(containsString("Hello World!"));

    });

    ensureThat("the pods are ready after we update the number of replicas to 1",
      () -> setReplicasAndWait(deploymentAssistant.client(), deploymentAssistant.applicationName(), 1));
  }



}
