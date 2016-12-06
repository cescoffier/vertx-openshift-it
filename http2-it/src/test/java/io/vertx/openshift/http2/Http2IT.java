package io.vertx.openshift.http2;

import io.fabric8.kubernetes.assertions.Assertions;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.OpenShiftClient;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpVersion;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.jayway.awaitility.Awaitility.await;
import static io.fabric8.kubernetes.assertions.internal.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
@RunWith(Arquillian.class)
@RunAsClient
public class Http2IT {

  private static final String NAME = "http2-it";

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
      .withNewTls().withTermination("passthrough").endTls()
      .endSpec()
      .done();

    assertThat(route).isNotNull();
    this.route = route;
  }

  @Test
  public void test() throws Exception {
    Assertions.assertThat(client).deployments().pods().isPodReadyForPeriod();

    AtomicReference<String> response = new AtomicReference<>();

    HttpClientOptions options = new HttpClientOptions()
      .setSsl(true)
      .setUseAlpn(true)
      .setProtocolVersion(HttpVersion.HTTP_2)
      .setTrustAll(true);

    Vertx vertx = Vertx.vertx();

    System.out.println("Host: " + url().getHost());
    System.out.println("Port: " + 443);

    vertx.createHttpClient(options)
      .getNow(443, url().getHost(), "/",
        resp -> {
          System.out.println("Got response " + resp.statusCode() + " with protocol " + resp.version());
          resp.bodyHandler(body -> {
            System.out.println("Got data " + body.toString("ISO-8859-1"));
            response.set(body.toString("ISO-8859-1"));
          });
        });
    await().atMost(5, TimeUnit.MINUTES).untilAtomic(response, is(notNullValue()));

    assertThat(response.get())
      .contains("version = HTTP_2")
      .contains("Hello from vert.x!");
  }


  private URL url() {
    try {
      return new URL("https://" + route.getSpec().getHost());
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }
}
