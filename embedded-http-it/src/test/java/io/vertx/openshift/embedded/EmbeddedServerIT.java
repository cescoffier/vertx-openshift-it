package io.vertx.openshift.embedded;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.OpenShiftClient;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static com.jayway.awaitility.Awaitility.await;
import static io.fabric8.kubernetes.assertions.Assertions.assertThat;
import static io.restassured.RestAssured.get;
import static org.assertj.core.api.Fail.fail;
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
  private OpenShiftClient oc;

  @Before
  public void initialize() {
    oc = client.adapt(OpenShiftClient.class);
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

    ensureThat("the route is exposed", () -> assertThat(route).isNotNull());
    this.route = route;
  }

  private <T> T ensureThat(String msg, Callable<T> callable) {
    try {
      return callable.call();
    } catch (Throwable t) {
      fail("Fail ensuring '" + msg + "'", t);
      return null;
    }
  }

  private void ensureThat(String msg, Runnable runnable) {
    try {
      runnable.run();
    } catch (Throwable t) {
      fail("Fail ensuring '" + msg + "'", t);
    }
  }

  @Test
  public void testAppProvisionsRunningPods() throws Exception {
    ensureThat("the pod is running for 10 seconds", () ->
      assertThat(client).deployments().pods().isPodReadyForPeriod());
  }

  @Test
  public void testInvokingTheService() {
    ensureThat("the route is accessible",
      () -> await().atMost(1, TimeUnit.MINUTES).until(this::isServed));

    ensureThat("the route is served correctly", () ->
      get(url()).then().assertThat()
        .statusCode(200)
        .body(containsString("Hello World!")));
  }

  @Test
  public void testWithTwoReplicas() {
    oc.deploymentConfigs().withName(NAME)
      .edit().editSpec().withReplicas(2).endSpec().done();

    ensureThat("the route is accessible",
      () -> await().atMost(1, TimeUnit.MINUTES).until(this::isServed));

    ensureThat("the route can be called several times in a raw", () -> {
      get(url()).then().assertThat().statusCode(200)
        .body(containsString("Hello World!"));
      get(url()).then().assertThat().statusCode(200)
        .body(containsString("Hello World!"));
      get(url()).then().assertThat().statusCode(200)
        .body(containsString("Hello World!"));
      get(url()).then().assertThat().statusCode(200)
        .body(containsString("Hello World!"));
    });

    oc.deploymentConfigs().withName(NAME)
      .edit().editSpec().withReplicas(1).endSpec().done();
  }


  private URL url() {
    try {
      return new URL("http://" + route.getSpec().getHost());
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  private boolean isServed() {
    try {
      return get(url()).getStatusCode() == 200;
    } catch (Exception e) {
      return false;
    }
  }


}
