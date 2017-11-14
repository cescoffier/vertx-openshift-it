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
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.*;
import static io.restassured.RestAssured.*;

/**
 * @author Thomas Segismont
 */
public class DeliveryToFailingPodIT extends AbstractTestClass {

  private static final String APPLICATION_GROUP = "cluster-delivery-to-failing-pod";
  private static final String RECEIVER_APPLICATION_NAME = "cluster-dtfp-receiver";
  private static final String SENDER_APPLICATION_NAME = "cluster-dtfp-sender";

  private static Route route;
  private static OpenShiftHelper receiverHelper;
  private static OpenShiftHelper senderHelper;

  private Vertx vertx;

  @BeforeClass
  public static void initialize() throws IOException {
    initializeServiceAccount();

    SortedMap<String, File> dependencies = new TreeMap<>();
    dependencies.put("A-Receiver", new File("../" + APPLICATION_GROUP + "/" + RECEIVER_APPLICATION_NAME + "/target/classes/META-INF/fabric8/openshift.yml"));
    dependencies.put("B-Sender", new File("../" + APPLICATION_GROUP + "/" + SENDER_APPLICATION_NAME + "/target/classes/META-INF/fabric8/openshift.yml"));
    dependencies.forEach((name, template) ->
      Ensure.ensureThat(String.format("template file %s can be deployed", template), () -> deploymentAssistant.deploy(name, template))
    );

    ensureRunning("receiver", RECEIVER_APPLICATION_NAME);
    receiverHelper = new OpenShiftHelper(client, RECEIVER_APPLICATION_NAME);
    ensureRunning("sender", SENDER_APPLICATION_NAME);
    senderHelper = new OpenShiftHelper(client, SENDER_APPLICATION_NAME);
  }

  private static void ensureRunning(String shortName, String name) {
    Ensure.ensureThat("The " + shortName + " app is up and running", () ->
      await().atMost(5, TimeUnit.MINUTES).catchUncaughtExceptions().until(() -> {
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
    senderHelper.setReplicasAndWait(3);
    await().atMost(5, TimeUnit.MINUTES).until(() -> {
      for (int i = 0; i < 3; i++) {
        get(Kube.urlForRoute(client.routes().withName(SENDER_APPLICATION_NAME).get(), "/health"))
          .then().assertThat().statusCode(200);
      }
    });
  }

  @After
  public void afterEach() {
    vertx.close();
  }

  @Test
  public void testDeliveryToFailingPod() throws Exception {
    URL funcUrl = Kube.urlForRoute(client.routes().withName(SENDER_APPLICATION_NAME).get(), "/deliver-to-functional-pod");
    get(funcUrl)
      .then().assertThat().statusCode(200);

    receiverHelper.setReplicasAndWait(0);

    URL failUrl = Kube.urlForRoute(client.routes().withName(SENDER_APPLICATION_NAME).get(), "/deliver-to-failing-pod");
    get(failUrl)
      .then().assertThat().statusCode(200);
  }
}
