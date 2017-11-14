package io.vertx.openshift.it.cluster;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.openshift.api.model.Route;
import io.vertx.it.openshift.utils.AbstractTestClass;
import io.vertx.it.openshift.utils.Ensure;
import io.vertx.it.openshift.utils.Kube;
import io.vertx.it.openshift.utils.OC;
import io.vertx.it.openshift.utils.OpenShiftHelper;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import static com.jayway.awaitility.Awaitility.*;
import static io.restassured.RestAssured.*;
import static org.hamcrest.CoreMatchers.*;

/**
 * @author Thomas Segismont
 */
public class EventBusIT extends AbstractTestClass {

  private static final String APPLICATION_NAME = "cluster-event-bus";

  private static Route route;
  private static OpenShiftHelper clusterEventBusHelper;

  @BeforeClass
  public static void initialize() throws IOException {
    initializeServiceAccount();

    SortedMap<String, File> dependencies = new TreeMap<>();
    dependencies.put("A-EventBus", new File("../" + APPLICATION_NAME + "/target/classes/META-INF/fabric8/openshift.yml"));
    dependencies.forEach((name, template) ->
      Ensure.ensureThat(String.format("template file %s can be deployed", template), () -> deploymentAssistant.deploy(name, template))
    );

    Ensure.ensureThat("The event-bus app is up and running", () ->
      await().atMost(5, TimeUnit.MINUTES).catchUncaughtExceptions().until(() -> {
        Service service = client.services().withName(APPLICATION_NAME).get();
        Assertions.assertThat(service).isNotNull();

        route = client.routes().withName(APPLICATION_NAME).get();
        Assertions.assertThat(route).isNotNull();

        get(Kube.urlForRoute(route, "/health")).then().statusCode(200);
      }));
    clusterEventBusHelper = new OpenShiftHelper(client, APPLICATION_NAME);
  }

  private static void initializeServiceAccount() {
    OC.execute("policy", "add-role-to-user", "view", "admin", "-n", client.getNamespace());
    OC.execute("policy", "add-role-to-user", "view", "-n", client.getNamespace(), "-z", "default");
    OC.execute("policy", "add-role-to-group", "view", "system:serviceaccounts", "-n", client.getNamespace());
  }

  @Before
  public void beforeEach() {
    scaleTo(3);
  }

  private void scaleTo(int replicaCount) {
    clusterEventBusHelper.setReplicasAndWait(replicaCount);
    await().atMost(5, TimeUnit.MINUTES).until(() -> {
      for (int i = 0; i < replicaCount; i++) {
        get(Kube.urlForRoute(client.routes().withName(APPLICATION_NAME).get(), "/health"))
          .then().assertThat().statusCode(200);
      }
    });
  }

  @Test
  public void testLocalPeerToPeer() throws Exception {
    int loops = 30;
    URL url = Kube.urlForRoute(client.routes().withName(APPLICATION_NAME).get(), "/event-bus/local-p2p/" + loops);
    get(url)
      .then().assertThat().statusCode(200).body("size()", is(1));
  }

  @Test
  public void testDistPeerToPeer() throws Exception {
    int loops = 30;
    URL url = Kube.urlForRoute(client.routes().withName(APPLICATION_NAME).get(), "/event-bus/dist-p2p/" + loops);
    get(url)
      .then().assertThat().statusCode(200).body("size()", is(3));
  }

  @Test
  public void testLocalPublish() throws Exception {
    int loops = 30;
    URL url = Kube.urlForRoute(client.routes().withName(APPLICATION_NAME).get(), "/event-bus/local-pub/" + loops);
    get(url)
      .then().assertThat().statusCode(200).body(equalTo(String.valueOf(loops)));
  }

  @Test
  public void testDistPublish() throws Exception {
    int loops = 30;
    URL url = Kube.urlForRoute(client.routes().withName(APPLICATION_NAME).get(), "/event-bus/dist-pub/" + loops);
    get(url)
      .then().assertThat().statusCode(200).body(equalTo(String.valueOf(loops * 3)));
  }

  @Test
  public void testLocalRequestReply() throws Exception {
    int loops = 30;
    URL url = Kube.urlForRoute(client.routes().withName(APPLICATION_NAME).get(), "/event-bus/local-request-reply/" + loops);
    get(url)
      .then().assertThat().statusCode(200).body("size()", is(1));
  }

  @Test
  public void testDistRequestReply() throws Exception {
    int loops = 30;
    URL url = Kube.urlForRoute(client.routes().withName(APPLICATION_NAME).get(), "/event-bus/dist-request-reply/" + loops);
    get(url)
      .then().assertThat().statusCode(200).body("size()", is(3));
  }

  @Test
  public void testDistRequestTimeout() throws Exception {
    int loops = 30;
    URL url = Kube.urlForRoute(client.routes().withName(APPLICATION_NAME).get(), "/event-bus/dist-timeout/" + loops);
    get(url)
      .then().assertThat().statusCode(200).body(equalTo(String.valueOf(15)));
  }
}
