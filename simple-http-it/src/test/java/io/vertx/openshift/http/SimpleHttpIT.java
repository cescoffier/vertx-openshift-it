package io.vertx.openshift.http;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import static org.awaitility.Awaitility.await;
import static io.restassured.RestAssured.get;
import static io.restassured.RestAssured.given;

import static io.fabric8.kubernetes.assertions.Assertions.assertThat;
import static io.vertx.it.openshift.utils.Ensure.ensureThat;
import static io.vertx.it.openshift.utils.Kube.setReplicasAndWait;
import static io.vertx.it.openshift.utils.Kube.sleep;

import okio.ByteString;
import org.junit.BeforeClass;
import org.junit.Test;

import org.assertj.core.api.Assertions;

import io.restassured.RestAssured;
import io.restassured.response.Response;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.vertx.it.openshift.utils.AbstractTestClass;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;


/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class SimpleHttpIT extends AbstractTestClass {

  @BeforeClass
  public static void initialize() throws IOException {
    deployAndAwaitStartWithRoute();
    await("route should be eventually served").atMost(5, TimeUnit.MINUTES).catchUncaughtExceptions().until(() -> get().getStatusCode() < 500);
  }

  @Test
  public void stabilityTest() throws Exception {
    ensureThat("the pod is ready and stay ready for a bit",
      () -> assertThat(deploymentAssistant.client()).deployments().pods().isPodReadyForPeriod());
    await("That one pod is present.").atMost(1, TimeUnit.MINUTES).catchUncaughtExceptions()
      .untilAsserted(() -> assertThat(deploymentAssistant.client()).pods().runningStatus().hasSize(1));
  }

  @Test
  public void testRoot() throws Exception {
    ensureThat("the HTTP response is the expected one", () ->
      get().then().assertThat().statusCode(200)
        .body(equalTo("Hello Vert.x!"))
    );
  }

  @Test
  public void testHeaders() throws Exception {
    ensureThat("the HTTP response has the expected headers", () ->
      given()
        .header("id", "abc")
        .param("key", "NuDVhdsfYmNkDLOZQ")
        .when()
        .get("/headers"))
        .then()
      .assertThat().statusCode(200).body("id", is("abc"));

  }

  @Test
  public void testForm() throws Exception {
    ensureThat("the HTTP server can receive forms", () ->
      given()
        .formParam("name", "Vert.x")
        .formParam("org", "Eclipse")
        .when()
        .post("/form")
        .then()
        .assertThat().statusCode(200)
        .body("org", is("Eclipse"))
        .body("name", is("Vert.x"))
    );
  }

  @Test
  public void testWrittenFile() throws Exception {

    ensureThat("the HTTP server can write into a file", () ->
      get("/write"))
      .then()
      .assertThat()
      .statusCode(200);

    ensureThat("the written file can be read", () ->
      get("/tmp"))
      .then()
      .assertThat()
      .statusCode(200)
      .body(containsString("hello"));
  }

  @Test
  public void testWithWebSocket() throws MalformedURLException {
    ensureThat("the websocket connection works", () -> {
      OkHttpClient client = new OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build();

      Request request = new Request.Builder()
        .url(RestAssured.baseURI + "/ws")
        .build();

      List<String> messages = new ArrayList<>();

      WebSocketListener webSocketListener = new WebSocketListener() {
        @Override
        public void onOpen(WebSocket webSocket, okhttp3.Response response) {
          webSocket.send("Hello...");
          webSocket.send("...World!");
          webSocket.close(1000, "Goodbye, World!");
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, okhttp3.Response response) {
          System.err.println("An error has occured: " + t.getMessage());
          t.printStackTrace();
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
          messages.add(text);
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
          messages.add(bytes.utf8());
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
          webSocket.close(1000, null);
        }
      };

      client.newWebSocket(request, webSocketListener);

      await().until(() -> messages.size() >= 2);
      Assertions.assertThat(messages).hasSize(2)
        .contains("Hello...", "...World!");
    });
  }


  @Test
  public void testIncreasingReplicas() throws Exception {
    setReplicasAndWait(deploymentAssistant.client(), deploymentAssistant.applicationName(), 2);

    Set<String> hosts = new HashSet<>();

    ensureThat("the server side routing does the load balancing correctly", () -> {
      for (int i = 0; i < 500; i++) {
        Response response = get("/host");
        if (response.statusCode() == 200) {
          hosts.add(response.asString());
        }
        sleep(100);
      }

      Assertions.assertThat(hosts).hasSize(2);
    });

    setReplicasAndWait(deploymentAssistant.client(), deploymentAssistant.applicationName(), 1);

    hosts.clear();

    ensureThat("the service side routing does target the same pod when only one is available", () -> {
      for (int i = 0; i < 100; i++) {
        Response response = get("/host");
        if (response.statusCode() == 200) {
          hosts.add(response.asString());
        }
        sleep(100);
      }
      Assertions.assertThat(hosts).hasSize(1);
    });

  }
}
