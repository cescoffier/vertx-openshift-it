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

import static org.awaitility.Awaitility.*;
import static io.restassured.RestAssured.*;
import static org.hamcrest.CoreMatchers.*;

/**
 * @author Thomas Segismont
 */
public class CountersIT extends AbstractTestClass {

  private static final String APPLICATION_NAME = "cluster-counters";

  private static Route route;
  private static OpenShiftHelper clusterCountersHelper;

  @Rule
  public final TestName testName = new TestName();
  private Vertx vertx;

  @BeforeClass
  public static void initialize() throws IOException {
    initializeServiceAccount();

    SortedMap<String, File> dependencies = new TreeMap<>();
    dependencies.put("A-Counters", new File("../" + APPLICATION_NAME + "/target/classes/META-INF/fabric8/openshift.yml"));
    dependencies.forEach((name, template) ->
      Ensure.ensureThat(String.format("template file %s can be deployed", template), () -> deploymentAssistant.deploy(name, template))
    );

    Ensure.ensureThat("The counters app is up and running", () ->
      await().atMost(5, TimeUnit.MINUTES).catchUncaughtExceptions().until(() -> {
        Service service = client.services().withName(APPLICATION_NAME).get();
        Assertions.assertThat(service).isNotNull();

        route = client.routes().withName(APPLICATION_NAME).get();
        Assertions.assertThat(route).isNotNull();

        get(Kube.urlForRoute(route, "/health")).then().statusCode(200);
      }));
    clusterCountersHelper = new OpenShiftHelper(client, APPLICATION_NAME);
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
    clusterCountersHelper.setReplicasAndWait(replicaCount);
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
  public void testClusterWideUpdate() throws Exception {
    HttpClient httpClient = vertx.createHttpClient();
    URL url = Kube.urlForRoute(client.routes().withName(APPLICATION_NAME).get(), "/counters/" + testName.getMethodName());

    int loops = 30;
    CountDownLatch latch = new CountDownLatch(loops);
    for (int i = 0; i < loops; i++) {
      httpClient.postAbs(url.toString())
        .handler(resp -> {
          latch.countDown();
        })
        .exceptionHandler(t -> {
          latch.countDown();
        }).end(String.valueOf(i));
    }

    latch.await(1, TimeUnit.MINUTES);

    get(url)
      .then().assertThat().statusCode(200).body(equalTo(String.valueOf((loops * (loops - 1)) / 2)));
  }
}
