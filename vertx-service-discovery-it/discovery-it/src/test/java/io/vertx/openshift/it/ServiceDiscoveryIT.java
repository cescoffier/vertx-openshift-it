package io.vertx.openshift.it;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.OpenShiftClient;
import io.vertx.it.openshift.utils.Deployment;
import io.vertx.it.openshift.utils.OC;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static io.vertx.it.openshift.utils.Ensure.ensureThat;
import static io.vertx.it.openshift.utils.Kube.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;


/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
@RunWith(Arquillian.class)
@RunAsClient
public class ServiceDiscoveryIT {

  @ArquillianResource
  DefaultKubernetesClient client;

  private Route route;
  private Route endpointRoute;


  @Before
  public void prepare() throws IOException, InterruptedException {
//    cleanup();
    initializeServiceAccount();
    endpointRoute = initializeHttpEndpoint();
    route = initializeHttpClient();

    System.out.println(urlForRoute(route));
    System.out.println(urlForRoute(endpointRoute));

    ensureThat("the backend route is served", () -> awaitUntilRouteIsServed(endpointRoute));
    ensureThat("the frontend route is served", () -> awaitUntilRouteIsServed(route));
  }

  private void initializeServiceAccount() {
    OC.execute("policy", "add-role-to-user", "view", "admin", "-n", client.getNamespace());
    OC.execute("policy", "add-role-to-group", "view",
      "system:serviceaccounts", "-n", client.getNamespace());
    OC.execute("policy", "add-role-to-user",
      "system:image-puller", "system:serviceaccount:openshift:default",
      "-n", "default");
  }

  public Route initializeHttpEndpoint() throws IOException {
    String name = "simple-http-endpoint";

    ImageStream stream = Deployment.findImageStream(client, name);
    assertThat(stream).isNotNull();

    Deployment.deployIfNeeded(client, name, "src/test/resources/descriptors/http-endpoint-dc.json");
    setReplicasAndWait(client, name, 1);

    createServiceIfNeeded(client, name, "http-endpoint");
    return createRouteForService(client, name, true);
  }

  public Route initializeHttpClient() throws IOException {
    String name = "simple-http-client";

    ImageStream stream = Deployment.findImageStream(client, name);
    assertThat(stream).isNotNull();

    Deployment.deployIfNeeded(client, name, "src/test/resources/descriptors/http-client-dc.json");
    setReplicasAndWait(client, name, 1);

    createServiceIfNeeded(client, name, null);
    return createRouteForService(client, name, true);
  }


  @Test
  public void testWithDNS() throws Exception {
    String uuid = UUID.randomUUID().toString();
    ensureThat("service can be resolved using DNS", () ->
      given().queryParam("message", uuid)
        .get(urlForRoute(route, "/dns"))
        .then().assertThat().statusCode(200)
        .body("timestamp", is(notNullValue()))
        .body("message", is(uuid)));
  }

  @Test
  public void testWithDiscovery() throws Exception {
    String uuid = UUID.randomUUID().toString();
    ensureThat("service can be resolved using service discovery", () ->
      given().queryParam("message", uuid)
        .get(urlForRoute(route, "/discovery"))
        .then().assertThat().statusCode(200)
        .body("timestamp", is(notNullValue()))
        .body("message", is(uuid)));
  }

  @Test
  public void testWithReference() throws Exception {
    String uuid = UUID.randomUUID().toString();
    ensureThat("service can be resolved using a service reference", () ->
      given().queryParam("message", uuid)
        .get(urlForRoute(route, "/ref"))
        .then().assertThat().statusCode(200)
        .body("timestamp", is(notNullValue()))
        .body("message", is(uuid)));
  }

  @Test
  public void testMissingServiceWithDns() throws Exception {
    setReplicasAndWait(client, "simple-http-endpoint", 0);

    String uuid = UUID.randomUUID().toString();

    ensureThat("you cannot call a missing service using DNS", () ->
      given().queryParam("message", uuid)
        .get(urlForRoute(route, "/dns"))
        .then().assertThat().statusCode(503));
  }

  @Test
  public void testMissingServiceWithDiscovery() throws Exception {
    setReplicasAndWait(client, "simple-http-endpoint", 0);

    String uuid = UUID.randomUUID().toString();
    ensureThat("you cannot call a missing service using service discovery", () ->
      given().queryParam("message", uuid)
        .get(urlForRoute(route, "/discovery"))
        .then().assertThat().statusCode(503)
    );
  }

  @Test
  public void testMissingServiceWithRef() throws Exception {
    setReplicasAndWait(client, "simple-http-endpoint", 0);

    String uuid = UUID.randomUUID().toString();
    ensureThat("you cannot call a missing service using a service reference", () ->
      given().queryParam("message", uuid)
        .get(urlForRoute(route, "/ref"))
        .then().assertThat().statusCode(503));
  }

  @Test
  public void testServerSideRouting() throws Exception {
    ensureThat("two replicas can be created and everyone is fine", () -> {
      setReplicasAndWait(client, "simple-http-endpoint", 2);
      awaitUntilAllPodsAreReady(client);
    });

    String uuid = UUID.randomUUID().toString();

    final Set<String> hosts = new HashSet<>();
    ensureThat("the server side routing is working when accessed directly", () -> {
      // Direct access
      for (int i = 0; i < 500; i++) {
        String host = given().queryParam("message", uuid)
          .get(urlForRoute(endpointRoute, "/"))
          .then().assertThat().statusCode(200).extract().path("hostname");

        assertThat(host).isNotNull().isNotEmpty();
        hosts.add(host);
        sleep(100);
      }

      assertThat(hosts).hasSize(2);
    });

    hosts.clear();

    ensureThat("the server side routing is working correctly when accessed using service discovery", () -> {
      // Using discovery
      for (int i = 0; i < 100; i++) {
        String host = given().queryParam("message", uuid)
          .get(urlForRoute(route, "/discovery"))
          .then().assertThat().statusCode(200).extract().path("hostname");

        assertThat(host).isNotNull().isNotEmpty();
        hosts.add(host);
        sleep(100);
      }
    });

    assertThat(hosts).hasSize(2);

    hosts.clear();
    // Same with dns
    ensureThat("the server side routing is working correctly when accessed using DNS", () -> {
      for (int i = 0; i < 100; i++) {
        String host = given().queryParam("message", uuid)
          .get(urlForRoute(route, "/dns"))
          .then().assertThat().statusCode(200).extract().path("hostname");

        assertThat(host).isNotNull().isNotEmpty();
        hosts.add(host);
        sleep(100);
      }

      assertThat(hosts).hasSize(2);
    });

    hosts.clear();
    // Same with reference
    ensureThat("the server side routing is working correctly when accessed using reference", () -> {
      for (int i = 0; i < 100; i++) {
        String host = given().queryParam("message", uuid)
          .get(urlForRoute(route, "/ref"))
          .then().assertThat().statusCode(200).extract().path("hostname");

        assertThat(host).isNotNull().isNotEmpty();
        hosts.add(host);
        sleep(100);
      }
      assertThat(hosts).hasSize(2);
    });
  }

  private void cleanup() {
    OpenShiftClient oc = oc(client);
    oc.deploymentConfigs().delete();
    oc.pods().delete();
    oc.services().delete();
    oc.replicationControllers().delete();
  }


}
