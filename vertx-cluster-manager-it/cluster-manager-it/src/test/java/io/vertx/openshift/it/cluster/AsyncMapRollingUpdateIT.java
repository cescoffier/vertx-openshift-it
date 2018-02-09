package io.vertx.openshift.it.cluster;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.openshift.api.model.Route;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.it.openshift.utils.AbstractTestClass;
import io.vertx.it.openshift.utils.Ensure;
import io.vertx.it.openshift.utils.Kube;
import io.vertx.it.openshift.utils.OC;
import io.vertx.it.openshift.utils.OpenShiftHelper;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.*;
import static org.awaitility.Awaitility.*;
import static org.hamcrest.CoreMatchers.*;

/**
 * @author Thomas Segismont
 */
public class AsyncMapRollingUpdateIT extends AbstractTestClass {

  private static final String APPLICATION_GROUP = "cluster-rolling-update";
  private static final String APPLICATION_NAME = "cluster-ru-asyncmap";

  private static Route route;
  private static OpenShiftHelper clusterRuAsyncMapHelper;

  @Rule
  public final TestName testName = new TestName();
  private Vertx vertx;

  @BeforeClass
  public static void initialize() throws IOException {
    initializeServiceAccount();

    SortedMap<String, File> dependencies = new TreeMap<>();
    dependencies.put("A-RUAsyncMap", new File("../" + APPLICATION_GROUP + "/" + APPLICATION_NAME + "/target/classes/META-INF/fabric8/openshift.yml"));
    dependencies.forEach((name, template) ->
      Ensure.ensureThat(String.format("template file %s can be deployed", template), () -> deploymentAssistant.deploy(name, template))
    );

    Ensure.ensureThat("The asyncmap app is up and running", () ->
      await().atMost(5, TimeUnit.MINUTES).catchUncaughtExceptions().untilAsserted(() -> {
        Service service = client.services().withName(APPLICATION_NAME).get();
        Assertions.assertThat(service).isNotNull();

        route = client.routes().withName(APPLICATION_NAME).get();
        Assertions.assertThat(route).isNotNull();

        get(Kube.urlForRoute(route, "/ready")).then().statusCode(200);
      }));
    clusterRuAsyncMapHelper = new OpenShiftHelper(client, APPLICATION_NAME);
  }

  private static void initializeServiceAccount() {
    OC.execute("policy", "add-role-to-user", "view", "admin", "-n", client.getNamespace());
    OC.execute("policy", "add-role-to-user", "view", "-n", client.getNamespace(), "-z", "default");
    OC.execute("policy", "add-role-to-group", "view", "system:serviceaccounts", "-n", client.getNamespace());
  }

  @Before
  public void beforeEach() {
    vertx = Vertx.vertx();
    int replicaCount = 3;
    clusterRuAsyncMapHelper.setReplicasAndWait(replicaCount);
    await().atMost(5, TimeUnit.MINUTES).untilAsserted(() -> {
      for (int i = 0; i < replicaCount; i++) {
        get(Kube.urlForRoute(client.routes().withName(APPLICATION_NAME).get(), "/ready"))
          .then().assertThat().statusCode(200);
      }
    });
  }

  @After
  public void afterEach() {
    vertx.close();
  }

  @Test
  public void testAsyncMapDataNotLost() throws Exception {
    int loops = 30;

    HttpClient httpClient = vertx.createHttpClient();
    CountDownLatch putLatch = new CountDownLatch(loops);
    for (int i = 0; i < loops; i++) {
      String mapName = "map-" + i % 2;
      String mapKey = testName.getMethodName() + "-" + i;
      URL url = Kube.urlForRoute(client.routes().withName(APPLICATION_NAME).get(), "/stuff/" + mapName + "/" + mapKey);
      httpClient.putAbs(url.toString())
        .handler(resp -> putLatch.countDown())
        .putHeader("Content-Type", "application/json")
        .exceptionHandler(t -> putLatch.countDown())
        .end(new JsonObject().put("index", i).toBuffer());
    }
    putLatch.await(1, TimeUnit.MINUTES);

    OC.execute("rollout", "latest", APPLICATION_NAME);
    OC.execute("rollout", "status", "dc/" + APPLICATION_NAME); // blocks until done

    for (int i = 0; i < loops; i++) {
      String mapName = "map-" + i % 2;
      String mapKey = testName.getMethodName() + "-" + i;
      URL url = Kube.urlForRoute(client.routes().withName(APPLICATION_NAME).get(), "/stuff/" + mapName + "/" + mapKey);
      get(url)
        .then().assertThat().statusCode(200).body("index", equalTo(i));
    }
  }
}
