package io.vertx.openshift.it;

import com.jayway.restassured.RestAssured;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.OpenShiftClient;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.jayway.awaitility.Awaitility.await;
import static io.vertx.openshift.it.OpenshiftHelper.oc_execute;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class AbstractKubernetesIT {

  @ArquillianResource
  DefaultKubernetesClient client;

  OpenShiftClient oc;


  @Before
  public void injectOpenShiftClient() {
    oc = client.adapt(OpenShiftClient.class);
  }

  @AfterClass
  public static void switchBackToDefault() {
    oc_execute("project", "default");
  }


  public URL url(Route route) {
    try {
      return new URL("http://" + route.getSpec().getHost());
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  public URL url(Route route, String path) {
    try {
      return new URL("http://" + route.getSpec().getHost() + path);
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }


  public Route expose(OpenShiftClient oc, Service service) {
    String name = service.getMetadata().getName();

    System.out.println("SWitching to project " + oc.getNamespace());
    oc_execute("project", oc.getNamespace());
    oc_execute("expose", "service", name);

    Route route = oc.routes().withName(name).get();
    System.out.println("Created route: " + route);
    return route;
  }

  public Service createDefaultService(String project, String type) {
    Objects.requireNonNull(project);


    Map<String, String> labels = new HashMap<>();
    if (type != null) {
      labels.put("service-type", type);
    }

    labels.put("project", project);
    labels.put("name", project);

    return client.services().createNew()
        .withNewMetadata()
        .withName(project)
        .withLabels(labels)
        .endMetadata()
        .withNewSpec()
        .addNewPort()
        .withProtocol("TCP")
        .withPort(80)
        .withNewTargetPort(8080)
        .endPort()
        .addToSelector("project", project)
        .withType("ClusterIP")
        .withSessionAffinity("None")
        .endSpec()
        .done();
  }

  public String name(HasMetadata object) {
    return object.getMetadata().getName();
  }


  public void initializeServiceAccount() {
    oc_execute("policy", "add-role-to-user", "view", "admin", "-n", client.getNamespace());
    oc_execute("policy", "add-role-to-group", "view",
        "system:serviceaccounts", "-n", client.getNamespace());
  }


  public void awaitUntilRouteIsServed(Route route) {
    await().atMost(3, TimeUnit.MINUTES).until(() -> {
      try {
        return RestAssured.get(url(route)).getStatusCode() < 500;
      } catch (Exception e) {
        return false;
      }
    });
  }

  public void awaitUntilAllPodsAreReady() {
    await().atMost(2, TimeUnit.MINUTES).until(() -> {
      List<Pod> items = client.pods().list().getItems();
      for (Pod pod : items) {
        if (!KubernetesHelper.isPodReady(pod)) {
          return false;
        }
      }
      return true;
    });
  }

}
