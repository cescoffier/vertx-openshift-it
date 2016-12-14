package io.vertx.openshift.embedded;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.api.model.Route;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static com.jayway.awaitility.Awaitility.await;
import static io.fabric8.kubernetes.assertions.Assertions.assertThat;
import static io.restassured.RestAssured.get;
import static io.vertx.it.openshift.utils.Ensure.ensureThat;
import static io.vertx.it.openshift.utils.Kube.*;
import static org.hamcrest.Matchers.containsString;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
@RunWith(Arquillian.class)
@RunAsClient
public class EmbeddedServerIT {

  private static final String NAME = "embedded-http";

  @ArquillianResource
  private KubernetesClient client;

  private Route route;

  @Before
  public void initialize() {
    // The route is exposed using .vagrant.f8 suffix, delegate to openshift to
    // get a public URL
    this.route = createRouteForService(client, NAME, true);
    ensureThat("the route is exposed", () -> assertThat(route).isNotNull());
  }


  @Test
  public void testAppProvisionsRunningPods() throws Exception {
    ensureThat("the pod is running for 10 seconds", () ->
      assertThat(client).deployments().pods().isPodReadyForPeriod());
  }

  @Test
  public void testInvokingTheService() {
    ensureThat("the route is accessible",
      () -> await().atMost(1, TimeUnit.MINUTES).until(() -> isRouteServed(route)));

    ensureThat("the route is served correctly", () ->
      get(urlForRoute(route)).then().assertThat()
        .statusCode(200)
        .body(containsString("Hello World!")));
  }

  @Test
  public void testWithTwoReplicas() {
    ensureThat("the pods are ready after we update the number of replicas to 2",
      () -> setReplicasAndWait(client, NAME, 2));

    ensureThat("the route is accessible",
      () -> await().atMost(1, TimeUnit.MINUTES).until(() -> isRouteServed(route)));

    ensureThat("the route can be called several times in a raw", () -> {
      get(urlForRoute(route)).then().assertThat().statusCode(200)
        .body(containsString("Hello World!"));
      get(urlForRoute(route)).then().assertThat().statusCode(200)
        .body(containsString("Hello World!"));
      get(urlForRoute(route)).then().assertThat().statusCode(200)
        .body(containsString("Hello World!"));
      get(urlForRoute(route)).then().assertThat().statusCode(200)
        .body(containsString("Hello World!"));
    });

    ensureThat("the pods are ready after we update the number of replicas to 1",
      () -> setReplicasAndWait(client, NAME, 1));
  }



}
