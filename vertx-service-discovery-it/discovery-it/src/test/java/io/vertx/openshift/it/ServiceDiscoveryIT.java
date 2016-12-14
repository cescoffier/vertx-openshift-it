package io.vertx.openshift.it;

import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.api.model.Route;
import org.apache.commons.io.FileUtils;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.jayway.awaitility.Awaitility.await;
import static io.restassured.RestAssured.get;
import static io.restassured.RestAssured.given;
import static io.vertx.openshift.it.OpenshiftHelper.oc_execute;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;


/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
@RunWith(Arquillian.class)
@RunAsClient
public class ServiceDiscoveryIT extends AbstractKubernetesIT {

  private final String NAMESPACE = "vertx-service-discovery-it";

  private Route route;
  private Route endpointRoute;


  @Before
  public void prepare() throws IOException, InterruptedException {
//    cleanup();
    initializeServiceAccount();
    endpointRoute = initializeHttpEndpoint();
    route = initializeHttpClient();

    System.out.println(url(route));
    System.out.println(url(endpointRoute));

//    awaitUntilAllPodsAreReady();
    awaitUntilRouteIsServed(endpointRoute);
    awaitUntilRouteIsServed(route);

    System.out.println("Ready !");
  }

  private void awaitUntilRouteIsServed(Route route) {
    await().atMost(3, TimeUnit.MINUTES).until(() -> {
      try {
        return get(url(route)).getStatusCode() < 500;
      } catch (Exception e) {
        return false;
      }
    });
  }

  private void awaitUntilAllPodsAreReady() {
    await().atMost(2, TimeUnit.MINUTES).until(() -> {
      List<Pod> items = client.pods().inNamespace(NAMESPACE).list().getItems();
      for (Pod pod : items) {
        if (! pod.getMetadata().getName().endsWith("-build")) {
          if (!KubernetesHelper.isPodReady(pod)) {
            return false;
          }
        }
      }
      return true;
    });
  }

  private void initializeServiceAccount() {
    oc_execute("policy", "add-role-to-user", "view", "admin", "-n", client.getNamespace());
    oc_execute("policy", "add-role-to-group", "view",
      "system:serviceaccounts", "-n", client.getNamespace());
    oc_execute("policy", "add-role-to-user",
      "system:image-puller", "system:serviceaccount:openshift:default",
      "-n", "default");
  }

  public Route initializeHttpEndpoint() throws IOException {
    String name = "simple-http-endpoint";

    ImageStream stream = findImageStream(name, NAMESPACE);
    assertThat(stream).isNotNull();

    File file = filter("src/test/resources/descriptors/http-endpoint-dc.json",
      ImmutableMap.of("image", stream.getStatus().getDockerImageRepository()));

    String name2 = deploy(file);
    assertThat(name).isEqualTo(name2);

    DeploymentConfig deployment = oc.deploymentConfigs().withName(name).get();
    if (deployment.getSpec().getReplicas() != 1) {
      oc.deploymentConfigs().withName(name(deployment))
        .edit().editSpec().withReplicas(1).endSpec().done();
    }

    Service endpoint = client.services().withName(name).get();
    if (endpoint == null) {
      endpoint = createDefaultService(name, "http-endpoint");
      return expose(oc, endpoint);
    } else {
      return oc.routes().withName(name).get();
    }
  }

  public Route initializeHttpClient() throws IOException {
    String name = "simple-http-client";

    ImageStream stream = findImageStream(name, NAMESPACE);
    assertThat(stream).isNotNull();

    File file = filter("src/test/resources/descriptors/http-client-dc.json",
      ImmutableMap.of("image", stream.getStatus().getDockerImageRepository()));

    String name2 = deploy(file);
    assertThat(name).isEqualTo(name2);

    Service endpoint = client.services().withName(name).get();
    if (endpoint == null) {
      endpoint = createDefaultService(name, null);
      return expose(oc, endpoint);
    } else {
      return oc.routes().withName(name).get();
    }
  }


  @Test
  public void testWithDNS() throws Exception {
    String uuid = UUID.randomUUID().toString();
    given().queryParam("message", uuid)
      .get(url(route, "/dns"))
      .then().assertThat().statusCode(200)
      .body("timestamp", is(notNullValue()))
      .body("message", is(uuid));
  }

  @Test
  public void testWithDiscovery() throws Exception {
    String uuid = UUID.randomUUID().toString();
    given().queryParam("message", uuid)
      .get(url(route, "/discovery"))
      .then().assertThat().statusCode(200)
      .body("timestamp", is(notNullValue()))
      .body("message", is(uuid));
  }

  @Test
  public void testWithReference() throws Exception {
    String uuid = UUID.randomUUID().toString();
    given().queryParam("message", uuid)
      .get(url(route, "/ref"))
      .then().assertThat().statusCode(200)
      .body("timestamp", is(notNullValue()))
      .body("message", is(uuid));
  }

  @Test
  public void testMissingServiceWithDns() throws Exception {
    oc.deploymentConfigs().withName("simple-http-endpoint").edit()
      .editSpec().withReplicas(0).endSpec().done();

    await().atMost(1, TimeUnit.MINUTES).until(() -> {
      List<Pod> items = client.pods().inNamespace(NAMESPACE).list().getItems();
      return items.stream().filter(KubernetesHelper::isPodRunning).count() == 1;
    });

    String uuid = UUID.randomUUID().toString();
    given().queryParam("message", uuid)
      .get(url(route, "/dns"))
      .then().assertThat().statusCode(503);
  }

  @Test
  public void testMissingServiceWithDiscovery() throws Exception {
    oc.deploymentConfigs().withName("simple-http-endpoint").edit()
      .editSpec().withReplicas(0).endSpec().done();

    await().atMost(1, TimeUnit.MINUTES).until(() -> {
      List<Pod> items = client.pods().inNamespace(NAMESPACE).list().getItems();
      return items.stream().filter(KubernetesHelper::isPodRunning).count() == 1;
    });

    String uuid = UUID.randomUUID().toString();
    given().queryParam("message", uuid)
      .get(url(route, "/discovery"))
      .then().assertThat().statusCode(503);
  }

  @Test
  public void testMissingServiceWithRef() throws Exception {
    oc.deploymentConfigs().withName("simple-http-endpoint").edit()
      .editSpec().withReplicas(0).endSpec().done();

    await().atMost(1, TimeUnit.MINUTES).until(() -> {
      List<Pod> items = client.pods().inNamespace(NAMESPACE).list().getItems();
      return items.stream().filter(KubernetesHelper::isPodRunning).count() == 1;
    });

    String uuid = UUID.randomUUID().toString();
    given().queryParam("message", uuid)
      .get(url(route, "/ref"))
      .then().assertThat().statusCode(503);
  }

  @Test
  public void testServerSideRouting() throws Exception {
    oc.deploymentConfigs().withName("simple-http-endpoint").edit()
      .editSpec().withReplicas(2).endSpec().done();

    await().atMost(1, TimeUnit.MINUTES).until(() -> {
      List<Pod> items = client.pods().inNamespace(NAMESPACE).list().getItems();
      return items.stream().filter(KubernetesHelper::isPodRunning).count() == 3;
    });

    awaitUntilAllPodsAreReady();

    String uuid = UUID.randomUUID().toString();

    Set<String> hosts = new HashSet<>();

    // Direct access
    for (int i = 0; i < 500; i++) {
      String host = given().queryParam("message", uuid)
        .get(url(endpointRoute, "/"))
        .then().assertThat().statusCode(200).extract().path("hostname");

      assertThat(host).isNotNull().isNotEmpty();
      hosts.add(host);
      Thread.sleep(100);
    }

    assertThat(hosts).hasSize(2);
    hosts.clear();

    // Using discovery
    for (int i = 0; i < 100; i++) {
      String host = given().queryParam("message", uuid)
        .get(url(route, "/discovery"))
        .then().assertThat().statusCode(200).extract().path("hostname");

      assertThat(host).isNotNull().isNotEmpty();
      hosts.add(host);
      Thread.sleep(100);
    }

    assertThat(hosts).hasSize(2);

    // Same with dns
    hosts = new HashSet<>();

    for (int i = 0; i < 100; i++) {
      String host = given().queryParam("message", uuid)
        .get(url(route, "/dns"))
        .then().assertThat().statusCode(200).extract().path("hostname");

      assertThat(host).isNotNull().isNotEmpty();
      hosts.add(host);
      Thread.sleep(100);
    }

    assertThat(hosts).hasSize(2);

    // Same with reference
    hosts = new HashSet<>();

    for (int i = 0; i < 100; i++) {
      String host = given().queryParam("message", uuid)
        .get(url(route, "/ref"))
        .then().assertThat().statusCode(200).extract().path("hostname");

      assertThat(host).isNotNull().isNotEmpty();
      hosts.add(host);
      Thread.sleep(100);
    }

    assertThat(hosts).hasSize(2);
  }

  private File filter(String path, ImmutableMap<String, String> image) throws IOException {
    File input = new File(path);
    assertThat(input).isFile();
    assertThat(image).isNotNull();

    File tmp = File.createTempFile("openshift-it", ".json");
    String content = FileUtils.readFileToString(input);

    for (String key : image.keySet()) {
      content = content.replace("${" + key + "}", image.get(key));
    }

    FileUtils.write(tmp, content);

    return tmp;
  }

  private String deploy(File input) throws IOException {
    assertThat(input).isFile();
    byte[] bytes = FileUtils.readFileToByteArray(input);
    try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes)) {
      DeploymentConfig deploymentConfig = oc.deploymentConfigs().load(bis).get();
      assertThat(deploymentConfig).isNotNull();
      if (oc.deploymentConfigs().withName(deploymentConfig.getMetadata().getName()).get() != null) {
        System.out.println("Skipping the creation of dc/" + deploymentConfig.getMetadata().getName());
        return deploymentConfig.getMetadata().getName();
      }

      oc.deploymentConfigs().create(deploymentConfig);
      oc_execute("deploy", deploymentConfig.getMetadata().getName(), "--latest", "-n", oc.getNamespace());
      return deploymentConfig.getMetadata().getName();
    }
  }

  private ImageStream findImageStream(String name, String ns) {
    List<ImageStream> items = oc.imageStreams().inNamespace(ns).list().getItems();
    for (ImageStream item : items) {
      if (item.getMetadata().getName().equalsIgnoreCase(name)) {
        return item;
      }
    }

    return null;
  }

  private void cleanup() {
    oc.deploymentConfigs().inNamespace(NAMESPACE).delete();
    oc.pods().inNamespace(NAMESPACE).delete();
    oc.services().inNamespace(NAMESPACE).delete();
    oc.replicationControllers().inNamespace(NAMESPACE).delete();
  }


}
