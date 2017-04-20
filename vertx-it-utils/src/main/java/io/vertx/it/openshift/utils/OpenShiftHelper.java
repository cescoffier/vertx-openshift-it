package io.vertx.it.openshift.utils;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.client.OpenShiftClient;

/**
 * @author Slavomír Krupa (slavomir.krupa@gmail.com)
 */
public class OpenShiftHelper {

  private OpenShiftClient client;
  private String applicationName;

  public OpenShiftHelper(OpenShiftClient client, String applicationName) {
    this.client = client;
    this.applicationName = applicationName;
  }

  public DeploymentConfig setReplicasAndWait(int number) {
    return Kube.setReplicasAndWait(client, applicationName, number);
  }

  public List<Pod> getReadyPods() {
    return getFilteredPods(KubernetesHelper::isPodReady);
  }

  public List<Pod> getFilteredPods(Predicate<Pod> filter) {
    return getPods().stream().filter(filter).collect(Collectors.toList());
  }

  public List<Pod> getPods() {
    return Kube.getPodsForDeploymentConfig(client, applicationName);
  }
}
