package io.vertx.openshift.http;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.api.model.Route;
import okhttp3.*;
import okhttp3.ws.WebSocket;
import okhttp3.ws.WebSocketCall;
import okhttp3.ws.WebSocketListener;
import okio.Buffer;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.jayway.awaitility.Awaitility.await;
import static io.fabric8.kubernetes.assertions.Assertions.assertThat;
import static io.restassured.RestAssured.get;
import static io.restassured.RestAssured.given;
import static io.vertx.it.openshift.utils.Ensure.ensureThat;
import static io.vertx.it.openshift.utils.Kube.*;
import static okhttp3.ws.WebSocket.TEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;


/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
@RunWith(Arquillian.class)
@RunAsClient
public class SimpleHttpIT {

  private static final String NAME = "simple-http";

  @ArquillianResource
  private KubernetesClient client;

  private Route route;

  @Before
  public void initialize() {
    // The route is exposed using .vagrant.f8 suffix, delegate to openshift to
    // get a public URL
    route = createRouteForService(client, NAME, true);
    assertThat(route).isNotNull();
  }

  @Test
  public void testRoot() throws Exception {
    ensureThat("the pod is ready and stay ready for a bit",
      () -> assertThat(client).deployments().pods().isPodReadyForPeriod());

    ensureThat("the route is served correctly", () ->
      await().atMost(1, TimeUnit.MINUTES).until(() -> isRouteServed(route)));

    ensureThat("the HTTP response is the expected one", () ->
      get(urlForRoute(route)).then().assertThat().statusCode(200)
        .body(containsString("Hello Vert.x!"))
    );
  }

  @Test
  public void testHeaders() throws Exception {
    ensureThat("the route is served correctly", () ->
      await().atMost(1, TimeUnit.MINUTES).until(() -> isRouteServed(route)));

    ensureThat("the HTTP response has the expected headers", () ->
      given()
        .header("id", "abc")
        .param("key", "NuDVhdsfYmNkDLOZQ")
        .when()
        .get(urlForRoute(route, "/headers"))
        .then()
        .assertThat().statusCode(200).body("id", is("abc"))
    );
  }

  @Test
  public void testForm() throws Exception {
    ensureThat("the route is served correctly", () ->
      await().atMost(1, TimeUnit.MINUTES).until(() -> isRouteServed(route)));

    ensureThat("the HTTP server can receive forms", () ->
      given()
        .formParam("name", "Vert.x")
        .formParam("org", "Eclipse")
        .when()
        .post(urlForRoute(route, "/form"))
        .then()
        .assertThat().statusCode(200)
        .body("org", is("Eclipse"))
        .body("name", is("Vert.x"))
    );
  }

  @Test
  public void testWrittenFile() throws Exception {
    ensureThat("the route is served correctly",
      () -> await().atMost(1, TimeUnit.MINUTES).until(() -> isRouteServed(route)));

    ensureThat("the HTTP server can write into a file", () ->
      get(urlForRoute(route, "/write"))
        .then()
        .assertThat().statusCode(200)
    );

    ensureThat("the written file can be read", () ->
      get(urlForRoute(route, "/tmp"))
        .then()
        .assertThat().statusCode(200)
        .body(containsString("hello"))
    );
  }

  @Test
  public void testWithWebSocket() throws MalformedURLException {
    ensureThat("the route is served correctly",
      () -> await().atMost(1, TimeUnit.MINUTES).until(() -> isRouteServed(route)));

    ensureThat("the websocket connection works", () -> {
      OkHttpClient client = new OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build();

      Request request = new Request.Builder()
        .url(urlForRoute(route, "/ws"))
        .build();

      AtomicBoolean close = new AtomicBoolean();
      List<String> messages = new ArrayList<>();
      WebSocketCall.create(client, request).enqueue(new WebSocketListener() {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
          try {
            webSocket.sendMessage(RequestBody.create(TEXT, "Hello..."));
            webSocket.sendMessage(RequestBody.create(TEXT, "...World!"));
            webSocket.close(1000, "Goodbye, World!");
          } catch (IOException e) {
            e.printStackTrace();
          }

        }

        @Override
        public void onFailure(IOException e, Response response) {

        }

        @Override
        public void onMessage(ResponseBody responseBody) throws IOException {
          messages.add(responseBody.string());
        }

        @Override
        public void onPong(Buffer buffer) {

        }

        @Override
        public void onClose(int i, String s) {
          close.set(true);
        }
      });

      await().untilAtomic(close, is(true));
      assertThat(messages).hasSize(2)
        .contains("Hello...", "...World!");
    });
  }


  @Test
  public void testIncreasingReplicas() throws Exception {
    setReplicasAndWait(client, NAME, 2);

    Set<String> hosts = new HashSet<>();

    ensureThat("the server side routing does the load balancing correctly", () -> {
      for (int i = 0; i < 500; i++) {
        io.restassured.response.Response response = get(urlForRoute(route, "/host"));
        if (response.statusCode() == 200) {
          hosts.add(response.asString());
        }
        sleep(100);
      }

      assertThat(hosts).hasSize(2);
    });

    setReplicasAndWait(client, NAME, 1);

    hosts.clear();

    ensureThat("the service side routing does target the same pod when only one is available", () -> {
      for (int i = 0; i < 100; i++) {
        io.restassured.response.Response response = get(urlForRoute(route, "/host"));
        if (response.statusCode() == 200) {
          hosts.add(response.asString());
        }
        sleep(100);
      }
      assertThat(hosts).hasSize(1);
    });

  }




}
