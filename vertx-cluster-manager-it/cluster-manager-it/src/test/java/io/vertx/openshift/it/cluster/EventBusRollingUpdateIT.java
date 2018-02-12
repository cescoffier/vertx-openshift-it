package io.vertx.openshift.it.cluster;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.openshift.api.model.Route;
import io.vertx.core.Vertx;
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
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.*;
import static org.awaitility.Awaitility.*;
import static org.hamcrest.Matchers.*;

/**
 * @author Thomas Segismont
 */
public class EventBusRollingUpdateIT extends AbstractTestClass {

  private static final String APPLICATION_GROUP = "cluster-rolling-update";
  private static final String SENDER_APPLICATION_NAME = "cluster-ru-eventbus-sender";
  private static final String RECEIVER_APPLICATION_NAME = "cluster-ru-eventbus-receiver";

  private static Route route;
  private static OpenShiftHelper receiverHelper;

  @Rule
  public final TestName testName = new TestName();
  private Vertx vertx;

  @BeforeClass
  public static void initialize() throws IOException {
    initializeServiceAccount();

    SortedMap<String, File> dependencies = new TreeMap<>();
    dependencies.put("A-RUEventBusSender", new File("../" + APPLICATION_GROUP + "/" + SENDER_APPLICATION_NAME + "/target/classes/META-INF/fabric8/openshift.yml"));
    dependencies.put("B-RUEventBusReceiver", new File("../" + APPLICATION_GROUP + "/" + RECEIVER_APPLICATION_NAME + "/target/classes/META-INF/fabric8/openshift.yml"));
    dependencies.forEach((name, template) ->
      Ensure.ensureThat(String.format("template file %s can be deployed", template), () -> deploymentAssistant.deploy(name, template))
    );

    ensureRunning("sender", SENDER_APPLICATION_NAME);
    ensureRunning("receiver", RECEIVER_APPLICATION_NAME);
    receiverHelper = new OpenShiftHelper(client, RECEIVER_APPLICATION_NAME);
  }

  private static void ensureRunning(String shortName, String name) {
    Ensure.ensureThat("The " + shortName + " app is up and running", () ->
      await().atMost(5, TimeUnit.MINUTES).catchUncaughtExceptions().untilAsserted(() -> {
        Service service = client.services().withName(name).get();
        Assertions.assertThat(service).isNotNull();

        route = client.routes().withName(name).get();
        Assertions.assertThat(route).isNotNull();

        get(Kube.urlForRoute(route, "/health")).then().statusCode(200);
      }));
  }

  private static void initializeServiceAccount() {
    OC.execute("policy", "add-role-to-user", "view", "admin", "-n", client.getNamespace());
    OC.execute("policy", "add-role-to-user", "view", "-n", client.getNamespace(), "-z", "default");
    OC.execute("policy", "add-role-to-group", "view", "system:serviceaccounts", "-n", client.getNamespace());
  }

  @Before
  public void beforeEach() {
    vertx = Vertx.vertx();
    receiverHelper.setReplicasAndWait(3);
    await().atMost(5, TimeUnit.MINUTES).untilAsserted(() -> {
      for (int i = 0; i < 3; i++) {
        get(Kube.urlForRoute(client.routes().withName(RECEIVER_APPLICATION_NAME).get(), "/health"))
          .then().assertThat().statusCode(200);
      }
    });
  }

  @After
  public void afterEach() {
    vertx.close();
  }

  @Test
  public void testNoFailures() throws Exception {
    URL start = Kube.urlForRoute(client.routes().withName(SENDER_APPLICATION_NAME).get(), "/api/start_sending");
    get(start)
      .then().assertThat().statusCode(200);

    OC.execute("rollout", "latest", RECEIVER_APPLICATION_NAME);
    OC.execute("rollout", "status", "dc/" + RECEIVER_APPLICATION_NAME); // blocks until done

    URL stop = Kube.urlForRoute(client.routes().withName(SENDER_APPLICATION_NAME).get(), "/api/stop_sending");
    get(stop)
      .then().log().ifValidationFails().assertThat().statusCode(200).body("failures", equalTo(0));
  }
}
