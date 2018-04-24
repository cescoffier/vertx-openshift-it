package io.vertx.it.openshift.utils;

import static org.assertj.core.api.Fail.fail;

import static org.awaitility.Awaitility.await;
import static io.restassured.RestAssured.get;

import org.awaitility.Duration;
import io.restassured.response.Response;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.OpenShiftClient;


/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class Kube {

  private static final int DEFAULT_WAIT_TIME = 5;

  public static String name(HasMetadata object) {
    String name = object.getMetadata().getName();
    return Objects.requireNonNull(name);
  }

  public static OpenShiftClient oc(KubernetesClient client) {
    return Objects.requireNonNull(client).adapt(OpenShiftClient.class);
  }

  public static Route createRouteForService(KubernetesClient client, String svc, boolean deleteIfExist) {
    OpenShiftClient oc = oc(client);

    Route existing = oc.routes().withName(svc).get();
    if (existing != null && deleteIfExist) {
      oc.routes().withName(name(existing)).delete();
    }

    if (existing != null && !deleteIfExist) {
      return existing;
    }

    return oc.routes().createNew()
      .withNewMetadata().withName(svc).endMetadata()
      .withNewSpec()
      .withNewTo().withName(svc).withKind("Service").endTo()
      .endSpec()
      .done();
  }

  public static Service createServiceIfNeeded(KubernetesClient client, String name, String type) {
    Objects.requireNonNull(name);

    Service service = client.services().withName(name).get();
    if (service != null) {
      return service;
    }

    Map<String, String> labels = new HashMap<>();
    if (type != null) {
      labels.put("service-type", type);
    }

    labels.put("name", name);

    return client.services().createNew()
      .withNewMetadata()
      .withName(name)
      .withLabels(labels)
      .endMetadata()
      .withNewSpec()
      .addNewPort()
      .withProtocol("TCP")
      .withPort(80)
      .withNewTargetPort(8080)
      .endPort()
      .addToSelector("name", name)
      .withType("ClusterIP")
      .withSessionAffinity("None")
      .endSpec()
      .done();
  }


  public static URL securedUrlForRoute(Route route) {
    return urlForRoute("https://", route);
  }

  public static URL urlForRoute(Route route) {
    return urlForRoute("http://", route);
  }


  private static URL urlForRoute(String protocol, Route route) {
    try {
      return new URL(protocol + Objects.requireNonNull(route).getSpec().getHost());
    } catch (MalformedURLException e) {
      fail("Unable to compute the url for route " + name(route), e);
      throw new IllegalArgumentException(e);
    }
  }

  public static URL urlForRoute(Route route, String path) {
    try {
      return new URL("http://" + route.getSpec().getHost() + path);
    } catch (MalformedURLException e) {
      fail("Unable to compute the url for route " + name(route), e);
      throw new IllegalArgumentException(e);
    }
  }

  public static boolean isRouteServed(Route route) {
    try {
      return get(urlForRoute(route)).getStatusCode() < 500;
    } catch (Exception e) {
      return false;
    }
  }

  public static boolean isRouteServed(Route route, String path, AtomicReference<String> resp) {
    try {
      Response response = get(urlForRoute(route, path));
      if (response.getStatusCode() < 500) {
        resp.set(response.asString());
        return true;
      }
      return false;
    } catch (Exception e) {
      return false;
    }
  }

  public static DeploymentConfig setReplicasAndWait(KubernetesClient client, String name, int number) {
    OpenShiftClient oc = oc(client);

    DeploymentConfig config = oc.deploymentConfigs().withName(name).get();
    if (config == null) {
      fail("Unable to find the deployment config " + name);
      return null;
    }

    if (config.getSpec().getReplicas() == number) {
      return config;
    }

    config = oc.deploymentConfigs().withName(name)
      .edit().editSpec().withReplicas(number).endSpec().done();

    if (number == 0) {
      // Wait until no pods
      await().atMost(duration()).until(() -> getPodsForDeploymentConfig(client, name).size() == 0);
    } else {
      // Wait until the right number of pods
      await().atMost(duration()).until(() -> getPodsForDeploymentConfig(client, name).size() == number);
      // Wait for readiness
      await().atMost(duration()).until(() ->
        getPodsForDeploymentConfig(client, name).stream().filter(KubernetesHelper::isPodReady).count() == number);
    }

    return config;
  }

  public static List<Pod> getPodsForDeploymentConfig(KubernetesClient client, String name) {
    List<Pod> pods = oc(client).pods().list().getItems();
    return pods.stream().filter(pod -> name(pod).startsWith(name) && !name(pod).endsWith("-build"))
      .collect(Collectors.toList());
  }

  public static void sleep(long time) {
    try {
      Thread.sleep(time);
    } catch (InterruptedException e) {
      // Ignore
    }
  }

  public static void awaitUntilRouteIsServed(Route route) {
    await().atMost(duration()).until(() -> Kube.isRouteServed(route));
  }

  public static String awaitUntilRouteIsServed(Route route, String path) {
    AtomicReference<String> resp = new AtomicReference<>();
    await().atMost(duration()).until(() -> Kube.isRouteServed(route, path, resp));

    return resp.get();
  }

  public static void awaitUntilPodIsReady(KubernetesClient client, String name) {
    await().atMost(duration()).until(() -> {
      List<Pod> items = client.pods().list().getItems();
      for (Pod pod : items) {
        String podName = name(pod);
        if (podName.startsWith(name) && !podName.endsWith("-build") && !podName.endsWith("-deploy")) {
          return KubernetesHelper.isPodReady(pod);
        }
      }
      return false;
    });
  }

  public static void awaitUntilAllPodsAreReady(KubernetesClient client) {
    await().atMost(duration()).until(() -> {
      List<Pod> items = client.pods().list().getItems();
      for (Pod pod : items) {
        if (!pod.getMetadata().getName().endsWith("-build")) {
          return KubernetesHelper.isPodReady(pod);
        }
      }
      return false;
    });
  }

  public static Duration duration() {
    return duration(DEFAULT_WAIT_TIME);
  }

  public static Duration duration(int minutes) {
    String online = System.getenv("USE_OPENSHIFT_ONLINE");

    if (online != null) {
      minutes = minutes * 5;
    }

    return new Duration(minutes, TimeUnit.MINUTES);


  }


}
