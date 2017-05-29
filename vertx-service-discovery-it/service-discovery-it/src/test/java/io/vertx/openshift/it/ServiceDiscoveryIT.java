package io.vertx.openshift.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;

import static com.jayway.awaitility.Awaitility.await;
import static com.jayway.restassured.RestAssured.get;
import static com.jayway.restassured.RestAssured.given;

import static io.vertx.it.openshift.utils.Ensure.ensureThat;
import static io.vertx.it.openshift.utils.Kube.awaitUntilAllPodsAreReady;
import static io.vertx.it.openshift.utils.Kube.setReplicasAndWait;
import static io.vertx.it.openshift.utils.Kube.sleep;
import static io.vertx.it.openshift.utils.Kube.urlForRoute;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.openshift.api.model.Route;
import io.vertx.it.openshift.utils.AbstractTestClass;
import io.vertx.it.openshift.utils.OC;
import io.vertx.it.openshift.utils.OpenShiftHelper;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class ServiceDiscoveryIT extends AbstractTestClass {

  private static Route route;
  private static OpenShiftHelper someServiceHelper;

  @BeforeClass
  public static void initialize() throws IOException {
    initializeServiceAccount();

    deploymentAssistant.deploy("database", new File("src/test/resources/templates/database.yml"));
    deploymentAssistant.awaitPodReadinessOrFail(
      pod -> "my-database".equals(pod.getMetadata().getLabels().get("app"))
    );

    SortedMap<String, File> dependencies = new TreeMap<>();
    dependencies.put("A-Endpoints", new File("../some-http-services/target/classes/META-INF/fabric8/openshift.yml"));
    dependencies.put("B-Gateway", new File("../discovery-gateway/target/classes/META-INF/fabric8/openshift.yml"));
    dependencies.forEach((name, template) ->
      ensureThat(String.format("template file %s can be deployed", template), () -> deploymentAssistant.deploy(name, template))
    );

    ensureThat("the gateway is up and running", () ->
      await().atMost(5, TimeUnit.MINUTES).catchUncaughtExceptions().until(() -> {
        Service service = client.services().withName("discovery-gateway").get();
        assertThat(service).isNotNull();

        route = client.routes().withName("discovery-gateway").get();
        assertThat(route).isNotNull();

        get(urlForRoute(route, "/health")).then().statusCode(200);
      }));
    someServiceHelper = new OpenShiftHelper(client, "some-http-services");
  }

  private static void initializeServiceAccount() {
    OC.execute("policy", "add-role-to-user", "view", "admin", "-n", client.getNamespace());
    OC.execute("policy", "add-role-to-user", "view", "-n", client.getNamespace(), "-z", "default");
    OC.execute("policy", "add-role-to-group", "view", "system:serviceaccounts", "-n", client.getNamespace());
  }

  @Before
  public void resetReplicasCount() {
    someServiceHelper.setReplicasAndWait(1);
    await().atMost(5, TimeUnit.MINUTES).until(() -> {
      get(urlForRoute(client.routes().withName("some-http-services").get()))
        .then().assertThat().statusCode(200);
    });
  }


  @Test
  public void testHttpClientWithDNS() throws Exception {
    String uuid = UUID.randomUUID().toString();
    ensureThat("service can be resolved using DNS", () ->
      await().atMost(1, TimeUnit.MINUTES).until(() ->
        given().queryParam("message", uuid)
          .get(urlForRoute(route, "/dns/http"))
          .then().assertThat().statusCode(200)
          .body("timestamp", is(notNullValue()))
          .body("message", is(uuid))
      )
    );
  }

  @Test
  public void testWebClientWithDNS() throws Exception {
    String uuid = UUID.randomUUID().toString();
    ensureThat("service can be resolved using DNS", () ->
      await().atMost(1, TimeUnit.MINUTES).until(() ->
        given().queryParam("message", uuid)
          .get(urlForRoute(route, "/dns/web"))
          .then().assertThat().statusCode(200)
          .body("timestamp", is(notNullValue()))
          .body("message", is(uuid))
      )
    );
  }

  @Test
  public void testWithHttpClientDiscovery() throws Exception {
    String uuid = UUID.randomUUID().toString();
    ensureThat("service can be resolved using service discovery", () ->
      await().atMost(1, TimeUnit.MINUTES).until(() ->
        given().queryParam("message", uuid)
          .get(urlForRoute(route, "/services/http"))
          .then().assertThat().statusCode(200)
          .body("timestamp", is(notNullValue()))
          .body("message", is(uuid))
      )
    );
  }

  @Test
  public void testWithWebClientDiscovery() throws Exception {
    String uuid = UUID.randomUUID().toString();
    ensureThat("service can be resolved using service discovery", () ->
      await().atMost(1, TimeUnit.MINUTES).until(() ->
        given().queryParam("message", uuid)
          .get(urlForRoute(route, "/services/web"))
          .then().assertThat().statusCode(200)
          .body("timestamp", is(notNullValue()))
          .body("message", is(uuid))
      )
    );
  }

  @Test
  public void testHttpClientWithReference() throws Exception {
    String uuid = UUID.randomUUID().toString();
    ensureThat("service can be resolved using a service reference", () ->
      await().atMost(1, TimeUnit.MINUTES).until(() ->
        given().queryParam("message", uuid)
          .get(urlForRoute(route, "/ref/http"))
          .then().assertThat().statusCode(200)
          .body("timestamp", is(notNullValue()))
          .body("message", is(uuid))
      )
    );
  }

  @Test
  public void testWebClientWithReference() throws Exception {
    String uuid = UUID.randomUUID().toString();
    ensureThat("service can be resolved using a service reference", () ->
      await().atMost(1, TimeUnit.MINUTES).until(() ->
        given().queryParam("message", uuid)
          .get(urlForRoute(route, "/ref/http"))
          .then().assertThat().statusCode(200)
          .body("timestamp", is(notNullValue()))
          .body("message", is(uuid))
      )
    );
  }

  @Test
  public void testDatabaseUsingDNS() throws Exception {
    ensureThat("database can be resolved using DNS", () ->
      await().atMost(1, TimeUnit.MINUTES).until(() ->
        get(urlForRoute(route, "/dns/db"))
          .then().assertThat().statusCode(200)
      )
    );
  }

  @Test
  public void testDatabaseUsingDiscovery() throws Exception {
    ensureThat("database can be resolved using service discovery", () ->
      await().atMost(1, TimeUnit.MINUTES).until(() ->
        get(urlForRoute(route, "/services/db"))
          .then().assertThat().statusCode(200)
      )
    );
  }

  @Test
  public void testMissingServiceWithDns() throws Exception {
    setReplicasAndWait(client, "some-http-services", 0);

    String uuid = UUID.randomUUID().toString();

    ensureThat("you cannot call a missing service using DNS", () ->
      given().queryParam("message", uuid)
        .get(urlForRoute(route, "/dns/web"))
        .then().assertThat().statusCode(500));
  }

  @Test
  public void testMissingServiceWithDiscovery() throws Exception {
    someServiceHelper.setReplicasAndWait(0);
    String uuid = UUID.randomUUID().toString();
    ensureThat("you cannot call a missing service using service discovery", () ->
      given().queryParam("message", uuid)
        .get(urlForRoute(route, "/services/http"))
        .then().assertThat().statusCode(503)
    );

  }

  @Test
  public void testMissingServiceWithRef() throws Exception {
    someServiceHelper.setReplicasAndWait(0);

    String uuid = UUID.randomUUID().toString();
    ensureThat("you cannot call a missing service using a service reference", () ->
      given().queryParam("message", uuid)
        .get(urlForRoute(route, "/ref/web"))
        .then().assertThat().statusCode(500));


  }

  @Test
  public void testServerSideRoutingWithHttpClient() throws Exception {
    ensureThat("two replicas can be created and everyone is fine", () -> {
      someServiceHelper.setReplicasAndWait(2);
      awaitUntilAllPodsAreReady(client);
    });

    String uuid = UUID.randomUUID().toString();

    final Set<String> hosts = new HashSet<>();
    ensureThat("the server side routing is working when accessed directly", () -> {
      // Direct access
      for (int i = 0; i < 500; i++) {
        String host = given().queryParam("message", uuid)
          .get(urlForRoute(client.routes().withName("some-http-services").get()))
          .then().assertThat().statusCode(200).extract().path("hostname");

        assertThat(host).isNotNull().isNotEmpty();
        hosts.add(host);
        sleep(100);
      }

      softly.assertThat(hosts).hasSize(2);
    });

    hosts.clear();

    ensureThat("the server side routing is working correctly when accessed using service discovery and HTTP clients",
      () -> {
        // Using discovery
        for (int i = 0; i < 100; i++) {
          String host = given().queryParam("message", uuid)
            .get(urlForRoute(route, "/services/http"))
            .then().assertThat().statusCode(200).extract().path("hostname");

          assertThat(host).isNotNull().isNotEmpty();
          hosts.add(host);
          sleep(100);
        }
      });
    softly.assertThat(hosts).hasSize(2);

    hosts.clear();
    ensureThat("the server side routing is working correctly when accessed using service discovery and Web clients",
      () -> {
        // Using discovery
        for (int i = 0; i < 100; i++) {
          String host = given().queryParam("message", uuid)
            .get(urlForRoute(route, "/services/web"))
            .then().assertThat().statusCode(200).extract().path("hostname");

          assertThat(host).isNotNull().isNotEmpty();
          hosts.add(host);
          sleep(100);
        }
      });
    softly.assertThat(hosts).hasSize(2);

    hosts.clear();

    // Same with dns
    ensureThat("the server side routing is working correctly when accessed using DNS", () -> {
      for (int i = 0; i < 100; i++) {
        String host = given().queryParam("message", uuid)
          .get(urlForRoute(route, "/dns/http"))
          .then().assertThat().statusCode(200).extract().path("hostname");

        assertThat(host).isNotNull().isNotEmpty();
        hosts.add(host);
        sleep(100);
      }

      softly.assertThat(hosts).hasSize(2);
    });

    hosts.clear();

    ensureThat("the server side routing is working correctly when accessed using DNS and Web clients", () -> {
      for (int i = 0; i < 100; i++) {
        String host = given().queryParam("message", uuid)
          .get(urlForRoute(route, "/dns/web"))
          .then().assertThat().statusCode(200).extract().path("hostname");

        assertThat(host).isNotNull().isNotEmpty();
        hosts.add(host);
        sleep(100);
      }

      softly.assertThat(hosts).hasSize(2);
    });

  }


}
