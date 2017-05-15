package io.vertx.openshift.http2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import static com.jayway.awaitility.Awaitility.await;

import static io.vertx.it.openshift.utils.Kube.securedUrlForRoute;

import org.junit.BeforeClass;
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

  @BeforeClass
  public static void initialize() throws IOException {
    deployAndAwaitStart();

  }

  @Test
  public void test() throws Exception {
    Assertions.assertThat(client).deployments().pods().isPodReadyForPeriod(60000, 60000);

    AtomicReference<String> response = new AtomicReference<>();

    HttpClientOptions options = new HttpClientOptions()
      .setSsl(true)
      .setUseAlpn(true)
      .setProtocolVersion(HttpVersion.HTTP_2)
      .setTrustAll(true);

    Vertx vertx = Vertx.vertx();

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
    await().atMost(5, TimeUnit.MINUTES).untilAtomic(response, is(notNullValue()));

    assertThat(response.get())
      .contains("version = HTTP_2")
      .contains("Hello from vert.x!");
  }

}
