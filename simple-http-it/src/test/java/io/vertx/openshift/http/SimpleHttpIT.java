package io.vertx.openshift.http;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.OpenShiftClient;
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
import java.net.URL;
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

    assertThat(route).isNotNull();
    this.route = route;
  }

  @Test
  public void testRoot() throws Exception {
    assertThat(client).deployments().pods().isPodReadyForPeriod();
    await().atMost(1, TimeUnit.MINUTES).until(this::isServed);

    get(url()).then().assertThat().statusCode(200)
      .body(containsString("Hello Vert.x!"));
  }

  @Test
  public void testHeaders() throws Exception {
    await().atMost(1, TimeUnit.MINUTES).until(this::isServed);

    given()
      .header("id", "abc")
      .param("key", "NuDVhdsfYmNkDLOZQ")
      .when()
      .get(url("/headers"))
      .then()
      .assertThat().statusCode(200).body("id", is("abc"));
  }

  @Test
  public void testForm() throws Exception {
    await().atMost(1, TimeUnit.MINUTES).until(this::isServed);

    given()
      .formParam("name", "Vert.x")
      .formParam("org", "Eclipse")
      .when()
      .post(url("/form"))
      .then()
      .assertThat().statusCode(200)
      .body("org", is("Eclipse"))
      .body("name", is("Vert.x"));
  }

  @Test
  public void testWrittenFile() throws Exception {
    await().atMost(1, TimeUnit.MINUTES).until(this::isServed);

    get(url("/write"))
      .then()
      .assertThat().statusCode(200);

    get(url("/tmp"))
      .then()
      .assertThat().statusCode(200)
      .body(containsString("hello"));
  }

  @Test
  public void testWithWebSocket() throws MalformedURLException {
    await().atMost(1, TimeUnit.MINUTES).until(this::isServed);

    OkHttpClient client = new OkHttpClient.Builder()
      .readTimeout(0, TimeUnit.MILLISECONDS)
      .build();

    Request request = new Request.Builder()
      .url(url("/ws"))
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
  }


  @Test
  public void testIncreasingReplicas() throws Exception {
    oc.deploymentConfigs().withName(NAME)
      .edit().editSpec().withReplicas(2).endSpec().done();

    await().until(() -> client.pods()
      .withLabel("project", NAME).list().getItems().size() == 2);

    Set<String> hosts = new HashSet<>();

    for (int i = 0; i < 500; i++) {
      io.restassured.response.Response response = get(url("/host"));
      if (response.statusCode() == 200) {
        hosts.add(response.asString());
      }
      Thread.sleep(100);
    }

    assertThat(hosts).hasSize(2);

    oc.deploymentConfigs().withName(NAME)
      .edit().editSpec().withReplicas(1).endSpec().done();

    await().atMost(1, TimeUnit.MINUTES)
      .until(() -> client.pods()
        .withLabel("project", NAME).list().getItems().size() == 1);

    hosts.clear();
    for (int i = 0; i < 100; i++) {
      io.restassured.response.Response response = get(url("/host"));
      if (response.statusCode() == 200) {
        hosts.add(response.asString());
      }
      Thread.sleep(100);
    }

    assertThat(hosts).hasSize(1);
  }


  private URL url() {
    try {
      return new URL("http://" + route.getSpec().getHost());
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  private URL url(String path) {
    try {
      return new URL("http://" + route.getSpec().getHost() + path);
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
