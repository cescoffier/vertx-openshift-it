package io.vertx.openshift.it;

import com.jayway.restassured.RestAssured;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.openshift.api.model.Route;
import okhttp3.*;
import okhttp3.ws.WebSocket;
import okhttp3.ws.WebSocketCall;
import okhttp3.ws.WebSocketListener;
import okio.Buffer;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
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
import static okhttp3.ws.WebSocket.TEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;


/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
@RunWith(Arquillian.class)
@RunAsClient
public class SimpleHttpIT extends AbstractKubernetesIT {

  private Route route;
  private Service service;

  @Before
  public void prepare() {
    service = client.services().withName("simple-http").get();
    route = oc.routes().withName("simple-http").get();
    boolean needToWait = service == null || route == null;
    if (service == null) {
      service = createDefaultService("simple-http");
    }
    if (route == null) {
      route = expose(oc, service);
    }
    if (needToWait) {
      assertThat(client).deployments().pods().isPodReadyForPeriod();
    }
  }


  @Test
  public void testRoot() throws Exception {
    RestAssured.get(url(route)).then().assertThat().statusCode(200).body(containsString("Hello Vert.x!"));
  }

  @Test
  public void testHeaders() throws Exception {
    RestAssured
        .given()
        .header("id", "abc")
        .param("key", "NuDVhdsfYmNkDLOZQ")
        .when()
        .get(url(route, "/headers"))
        .then()
        .assertThat().statusCode(200).body("id", is("abc"));
  }

  @Test
  public void testForm() throws Exception {
    RestAssured
        .given()
        .formParam("name", "Vert.x")
        .formParam("org", "Eclipse")
        .when()
        .post(url(route, "/form"))
        .then()
        .assertThat().statusCode(200)
        .body("org", is("Eclipse"))
        .body("name", is("Vert.x"));
  }

  @Test
  public void testWrittenFile() throws Exception {
    RestAssured
        .get(url(route, "/write"))
        .then()
        .assertThat().statusCode(200);

    RestAssured
        .get(url(route, "/tmp"))
        .then()
        .assertThat().statusCode(200)
        .body(containsString("hello"));
  }

  @Test
  public void testWithWebSocket() throws MalformedURLException {
    OkHttpClient client = new OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build();

    Request request = new Request.Builder()
        .url(url(route, "/ws"))
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
    oc.deploymentConfigs().withName("simple-http").edit().editSpec().withReplicas(2).endSpec().done();

    await().until(() -> client.pods().withLabel("project", "simple-http").list().getItems().size() == 2);

    Set<String> hosts = new HashSet<>();

    for (int i = 0; i < 500; i++) {
      hosts.add(RestAssured.get(url(route, "/host")).asString());
      Thread.sleep(100);
    }

    assertThat(hosts).hasSize(2);


    oc.deploymentConfigs().withName("simple-http").edit().editSpec().withReplicas(1).endSpec().done();

    await().atMost(1, TimeUnit.MINUTES)
        .until(() -> client.pods().withLabel("project", "simple-http").list().getItems().size() == 1);

    hosts.clear();
    for (int i = 0; i < 100; i++) {
      hosts.add(RestAssured.get(url(route, "/host")).asString());
    }

    assertThat(hosts).hasSize(1);
  }

//  @Test
//  public void testFileWithVolumeMounted() throws InterruptedException, MalformedURLException, FileNotFoundException {
////    Pod pod = client.pods().withLabel("project", "simple-http").list().getItems().get(0);
////    client.pods().withName(pod.getMetadata().getName()).edit().editSpec().addNewVolume()
////        .withName("my-vol")
////        .withNewHostPath("/tmp/data")
////        .endVolume()
////        .endSpec().done();
//
//    System.out.println("Starting...");
//    DeploymentConfig dc = oc.deploymentConfigs().withName("simple-http").get();
//    Container container = dc.getSpec().getTemplate().getSpec().getContainers().get(0);
//    Container containerWithVolume = new ContainerBuilder(container).addNewVolumeMount().withMountPath("/data")
//        .withName("my-storage").endVolumeMount().build();
//
//    System.out.println("Volumes" + dc.getSpec().getTemplate().getSpec().getVolumes());
//    Volume volume = new VolumeBuilder().withNewHostPath("/tmp/data").withName("my-storage").build();
//
//    oc.deploymentConfigs().withName("simple-http").edit()
//        .editSpec()
//        .editTemplate()
//        .editSpec()
//        .withVolumes(volume)
//        .removeFromContainers(container)
//        .addToContainers(containerWithVolume)
//        .endSpec()
//        .endTemplate()
//        .endSpec()
//        .done();
//
//    String response = RestAssured.get(url(route, "/write")).asString();
//    System.out.println(response);
//
//    Thread.sleep(50000000);
//
//
//  }

}
