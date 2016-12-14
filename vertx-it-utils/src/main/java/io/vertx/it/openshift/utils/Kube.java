package io.vertx.it.openshift.utils;

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.OpenShiftClient;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.jayway.awaitility.Awaitility.await;
import static io.restassured.RestAssured.get;
import static org.assertj.core.api.Fail.fail;


/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class Kube {

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
      oc.routes().delete(existing);
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

  public static URL urlForRoute(Route route) {
    try {
      return new URL("http://" + Objects.requireNonNull(route).getSpec().getHost());
    } catch (MalformedURLException e) {
      fail("Unable to compute the url for route " + name(route), e);
      return null;
    }
  }

  public static boolean isRouteServed(Route route) {
    try {
      return get(urlForRoute(route)).getStatusCode() == 200;
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

    config = oc.deploymentConfigs().withName(name)
      .edit().editSpec().withReplicas(number).endSpec().done();

    if (number == 0) {
      // Wait until no pods
      await().atMost(1, TimeUnit.MINUTES).until(() -> getPodsForDeploymentConfig(client, name).size() == 0);
    } else {
      // Wait until the right number of pods
      await().atMost(1, TimeUnit.MINUTES).until(() -> getPodsForDeploymentConfig(client, name).size() == number);
      // Wait for readiness
      await().atMost(1, TimeUnit.MINUTES).until(() ->
        getPodsForDeploymentConfig(client, name).stream().filter(KubernetesHelper::isPodReady).count() == number);
    }

    return config;
  }

  public static List<Pod> getPodsForDeploymentConfig(KubernetesClient client, String name) {
    List<Pod> pods = oc(client).pods().list().getItems();
    return pods.stream().filter(pod -> name(pod).startsWith(name) && !name(pod).endsWith("-build"))
      .collect(Collectors.toList());
  }

}
