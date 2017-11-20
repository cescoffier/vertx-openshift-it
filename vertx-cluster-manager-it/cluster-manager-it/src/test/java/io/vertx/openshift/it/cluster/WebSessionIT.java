package io.vertx.openshift.it.cluster;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.openshift.api.model.Route;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.it.openshift.utils.AbstractTestClass;
import io.vertx.it.openshift.utils.Ensure;
import io.vertx.it.openshift.utils.Kube;
import io.vertx.it.openshift.utils.OC;
import io.vertx.it.openshift.utils.OpenShiftHelper;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.*;
import static io.restassured.RestAssured.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

/**
 * @author Thomas Segismont
 */
public class WebSessionIT extends AbstractTestClass {

  private static final String APPLICATION_NAME = "cluster-web-session";

  private static Route route;
  private static OpenShiftHelper clusterWebSessionHelper;

  private Vertx vertx;


  @BeforeClass
  public static void initialize() throws IOException {
    initializeServiceAccount();

    SortedMap<String, File> dependencies = new TreeMap<>();
    dependencies.put("A-WebSession", new File("../" + APPLICATION_NAME + "/target/classes/META-INF/fabric8/openshift.yml"));
    dependencies.forEach((name, template) ->
      Ensure.ensureThat(String.format("template file %s can be deployed", template), () -> deploymentAssistant.deploy(name, template))
    );

    Ensure.ensureThat("The web-session app is up and running", () ->
      await().atMost(5, TimeUnit.MINUTES).catchUncaughtExceptions().untilAsserted(() -> {
        Service service = client.services().withName(APPLICATION_NAME).get();
        Assertions.assertThat(service).isNotNull();

        route = client.routes().withName(APPLICATION_NAME).get();
        Assertions.assertThat(route).isNotNull();

        get(Kube.urlForRoute(route, "/health")).then().statusCode(200);
      }));
    clusterWebSessionHelper = new OpenShiftHelper(client, APPLICATION_NAME);
  }

  private static void initializeServiceAccount() {
    OC.execute("policy", "add-role-to-user", "view", "admin", "-n", client.getNamespace());
    OC.execute("policy", "add-role-to-user", "view", "-n", client.getNamespace(), "-z", "default");
    OC.execute("policy", "add-role-to-group", "view", "system:serviceaccounts", "-n", client.getNamespace());
  }


  @Before
  public void beforeEach() {
    vertx = Vertx.vertx();
    scaleTo(3);
  }


  private void scaleTo(int replicaCount) {
    clusterWebSessionHelper.setReplicasAndWait(replicaCount);
    await().atMost(5, TimeUnit.MINUTES).untilAsserted(() -> {
      for (int i = 0; i < replicaCount; i++) {
        get(Kube.urlForRoute(client.routes().withName(APPLICATION_NAME).get(), "/health"))
          .then().assertThat().statusCode(200);
      }
    });
  }


  @After
  public void afterEach() {
    vertx.close();
  }

  @Test
  public void testClusteredWebSession() throws Exception {
    HttpClient httpClient = vertx.createHttpClient();

    AtomicReference<Map.Entry<String, String>> cookie = new AtomicReference<>();
    int loops = 30;
    for (int i = 0; i < loops; i++) {
      String key = getKey(i);
      String value = getValue(i);
      URL url = Kube.urlForRoute(client.routes().withName(APPLICATION_NAME).get(), "/web-session/" + key);
      CountDownLatch latch = new CountDownLatch(1);
      HttpClientRequest request = httpClient.putAbs(url.toString());
      Optional.ofNullable(cookie.get())
        .ifPresent(c -> request.headers().add("cookie", c.getKey() + "=" + c.getValue()));
      request
        .handler(resp -> {
          resp.cookies().stream()
            .map(c -> c.split("=", 2))
            .map(split -> new SimpleImmutableEntry<>(split[0], split[1].split(";")[0]))
            .filter(entry -> "vertx-web.session".equals(entry.getKey()))
            .forEach(cookie::set);
          latch.countDown();
        })
        .exceptionHandler(t -> {
          t.printStackTrace();
          latch.countDown();
        }).end(value);
      latch.await(1, TimeUnit.MINUTES);
      TimeUnit.SECONDS.sleep(1); // Give some time to the replication operation
    }

    scaleTo(2);
    TimeUnit.SECONDS.sleep(10); // Give some time to the rebalancing process

    assertNotNull("No session cookie", cookie.get());

    for (int i = 0; i < loops; i++) {
      String key = getKey(i);
      String value = getValue(i);
      URL url = Kube.urlForRoute(client.routes().withName(APPLICATION_NAME).get(), "/web-session/" + key);
      given().cookie(cookie.get().getKey(), cookie.get().getValue()).when()
        .get(url)
        .then().assertThat().statusCode(200).body(equalTo(value));
    }
  }

  private String getKey(int i) {
    return "key" + i;
  }

  private String getValue(int i) {
    return "value" + i;
  }
}
