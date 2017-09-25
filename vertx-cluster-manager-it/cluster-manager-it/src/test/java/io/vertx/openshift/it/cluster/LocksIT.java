package io.vertx.openshift.it.cluster;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.openshift.api.model.Route;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.it.openshift.utils.AbstractTestClass;
import io.vertx.it.openshift.utils.Ensure;
import io.vertx.it.openshift.utils.Kube;
import io.vertx.it.openshift.utils.OC;
import io.vertx.it.openshift.utils.OpenShiftHelper;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
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
import java.util.concurrent.atomic.AtomicInteger;

import static com.jayway.awaitility.Awaitility.*;
import static com.jayway.restassured.RestAssured.*;
import static org.junit.Assert.*;

/**
 * @author Thomas Segismont
 */
public class LocksIT extends AbstractTestClass {

  private static final String APPLICATION_NAME = "cluster-locks";

  private static Route route;
  private static OpenShiftHelper clusterLocksHelper;

  @Rule
  public final TestName testName = new TestName();
  private Vertx vertx;

  @BeforeClass
  public static void initialize() throws IOException {
    initializeServiceAccount();

    SortedMap<String, File> dependencies = new TreeMap<>();
    dependencies.put("A-Locks", new File("../" + APPLICATION_NAME + "/target/classes/META-INF/fabric8/openshift.yml"));
    dependencies.forEach((name, template) ->
      Ensure.ensureThat(String.format("template file %s can be deployed", template), () -> deploymentAssistant.deploy(name, template))
    );

    Ensure.ensureThat("The locks app is up and running", () ->
      await().atMost(5, TimeUnit.MINUTES).catchUncaughtExceptions().until(() -> {
        Service service = client.services().withName(APPLICATION_NAME).get();
        Assertions.assertThat(service).isNotNull();

        route = client.routes().withName(APPLICATION_NAME).get();
        Assertions.assertThat(route).isNotNull();

        get(Kube.urlForRoute(route, "/health")).then().statusCode(200);
      }));
    clusterLocksHelper = new OpenShiftHelper(client, APPLICATION_NAME);
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
    clusterLocksHelper.setReplicasAndWait(replicaCount);
    await().atMost(5, TimeUnit.MINUTES).until(() -> {
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
  @Ignore("Currently, IPSN locks can be re-acquired on the same VM")
  public void testAcquireLock() throws Exception {
    HttpClient httpClient = vertx.createHttpClient();
    URL url = Kube.urlForRoute(client.routes().withName(APPLICATION_NAME).get(), "/locks/" + testName.getMethodName());

    int loops = 30;
    AtomicInteger acquired = new AtomicInteger();
    CountDownLatch latch = new CountDownLatch(loops);
    for (int i = 0; i < loops; i++) {
      httpClient.getAbs(url.toString())
        .handler(resp -> {
          if (resp.statusCode() == 200) {
            acquired.incrementAndGet();
          }
          latch.countDown();
        })
        .exceptionHandler(t -> {
          latch.countDown();
        }).end();
    }

    latch.await(1, TimeUnit.MINUTES);

    assertEquals(1, acquired.get());
  }
}
