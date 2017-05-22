package io.vertx.openshift.http2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import static com.jayway.awaitility.Awaitility.await;

import static io.vertx.it.openshift.utils.Kube.securedUrlForRoute;
import static io.vertx.it.openshift.utils.Kube.urlForRoute;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.fabric8.kubernetes.assertions.Assertions;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.it.openshift.utils.AbstractTestClass;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 * @author Slavomir Krupa
 */
public class Http2IT extends AbstractTestClass {

  private static final String NAME = "http2-it";
  private Vertx vertx;

  @Before
  public void setup() {
    vertx = Vertx.vertx();
  }

  @After
  public void tearDown() {
    vertx.close();
  }

  @BeforeClass
  public static void initialize() throws IOException {
    deployAndAwaitStart();
  }

  /**
   * Checks that we can call a HTTP/2 endpoint exposed by a pod through a "TLS passthrough" route.
   */
  @Test
  public void testHttp2_H2() throws Exception {
    Assertions.assertThat(client).deployments().pods().isPodReadyForPeriod();
    AtomicReference<String> response = new AtomicReference<>();

    HttpClientOptions options = new HttpClientOptions()
      .setSsl(true)
      .setUseAlpn(true)
      .setProtocolVersion(HttpVersion.HTTP_2)
      .setTrustAll(true);

    final String host = securedUrlForRoute(client.routes().withName(NAME).get()).getHost();
    System.out.println("Host: " + host);
    System.out.println("Port: " + 443);

    vertx.createHttpClient(options)
      .getNow(443, host, "/",
        resp -> {
          System.out.println("Got response " + resp.statusCode() + " with protocol " + resp.version());
          resp.bodyHandler(body -> {
            System.out.println("Got data " + body.toString("ISO-8859-1"));
            response.set(body.toString("ISO-8859-1"));
          });
        });
    await().atMost(1, TimeUnit.MINUTES).untilAtomic(response, is(notNullValue()));

    assertThat(response.get())
      .contains("version = HTTP_2")
      .contains("Hello from vert.x!");
  }

  /**
   * Checks that we can call a HTTP/2 - H2C endpoint exposed by a pod from another pod (no route).
   */
  @Test
  public void testHttp2_Internal_H2C() throws Exception {
    Assertions.assertThat(client).deployments().pods().isPodReadyForPeriod();

    AtomicReference<String> response = new AtomicReference<>();

    HttpClientOptions options = new HttpClientOptions();

    final String host = urlForRoute(client.routes().withName("aloha").get()).getHost();
    System.out.println("Host: " + host);

    vertx.createHttpClient(options)
      .getNow(80, host, "/front",
        resp -> {
          System.out.println("Got H2C response " + resp.statusCode() + " with protocol " + resp.version());
          resp.bodyHandler(body -> {
            System.out.println("Got H2C data " + body.toString("ISO-8859-1"));
            response.set(body.toString("ISO-8859-1"));
          });
        });
    await().atMost(1, TimeUnit.MINUTES).untilAtomic(response, is(notNullValue()));

    assertThat(response.get())
      .startsWith("Aloha HTTP_2");
  }

  /**
   * Checks that we can call a HTTP/2 - H2C endpoint exposed by a pod.
   */
  @Test
  @Ignore("Openshift does not support HTTP/2 H2C route")
  public void testHttp2_H2C() throws Exception {
    Assertions.assertThat(client).deployments().pods().isPodReadyForPeriod();

    AtomicReference<String> response = new AtomicReference<>();

    HttpClientOptions options = new HttpClientOptions()
      .setProtocolVersion(HttpVersion.HTTP_2);

    final String host = securedUrlForRoute(client.routes().withName("aloha").get()).getHost();
    System.out.println("Host: " + host);

    vertx.createHttpClient(options)
      .getNow(80, host, "/",
        resp -> {
          System.out.println("Got H2C response " + resp.statusCode() + " with protocol " + resp.version());
          resp.bodyHandler(body -> {
            System.out.println("Got H2C data " + body.toString("ISO-8859-1"));
            response.set(body.toString("ISO-8859-1"));
          });
        });
    await().atMost(1, TimeUnit.MINUTES).untilAtomic(response, is(notNullValue()));

    assertThat(response.get())
      .contains("Aloha HTTP_2");
  }

}
