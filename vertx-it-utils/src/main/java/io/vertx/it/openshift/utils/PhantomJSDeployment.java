package io.vertx.it.openshift.utils;

import static io.vertx.it.openshift.utils.Kube.awaitUntilRouteIsServed;
import static io.vertx.it.openshift.utils.Kube.createRouteForService;
import static io.vertx.it.openshift.utils.Kube.urlForRoute;

import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.net.URL;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.OpenShiftClient;

/**
 * @author Slavomír Krupa (slavomir.krupa@gmail.com)
 */

public class PhantomJSDeployment implements AutoCloseable {

  public static final String PHANTOMJS = "phantomjs";
  private OpenShiftClient client;
  private Route route;
  private Pod pod;
  private Service service;
  private WebDriver driver;

  public PhantomJSDeployment(OpenShiftClient client) {
    this.client = client;
  }

  public PhantomJSDeployment deploy() {
    System.out.println("Deploying Phantom ");
    createPhantomService();
    createPhantomPod();
    createPhantomRoute();
    return this;
  }

  public PhantomJSDeployment deployAndAwaitStart() {
    deploy();
    awaitUntilRouteIsServed(route, "/status");
    return this;
  }

  public URL getUrl() {
    if (retrieveRoute()) {
      return urlForRoute(route);
    }
    throw new IllegalStateException("PhantomJS is not deployed");
  }

  public WebDriver connectToWebService() {
    DesiredCapabilities capabilities = DesiredCapabilities.phantomjs();
    driver = new RemoteWebDriver(getUrl(), capabilities);
    driver.manage().window().setSize(new Dimension(1920, 1080));
    return driver;
  }

  private void createPhantomRoute() {
    if (!retrieveRoute()) {
      route = createRouteForService(client, PHANTOMJS, true);
    }
  }

  private boolean retrieveRoute() {
    route = client.routes().withName(PHANTOMJS).get();
    return route != null;
  }

  private boolean retrievePod() {
    pod = client.pods().withName(PHANTOMJS).get();
    return pod != null;
  }

  private boolean retrieveService() {
    service = client.services().withName(PHANTOMJS).get();
    return service != null;
  }

  private void createPhantomPod() {
    if (!retrievePod()) {
      Container c = new ContainerBuilder()
        .withName(PHANTOMJS)
        .withImage("maschmid/phantomjs")
        .withImagePullPolicy("Always")
        .withEnv(new EnvVar("IGNORE_SSL_ERRORS", "true", null))
        .withPorts(new ContainerPortBuilder().withContainerPort(4444).withName("webdriver").build())
        .build();

      Pod pb = new PodBuilder()
        .withNewMetadata()
        .withName(PHANTOMJS)
        .addToLabels("name", PHANTOMJS)
        .endMetadata()
        .withNewSpec()
        .withTerminationGracePeriodSeconds(0L)
        .withDnsPolicy("ClusterFirst")
        .withRestartPolicy("Always")
        .withContainers(c)
        .endSpec()
        .build();
      client.pods().create(pb);
    }
  }

  private void createPhantomService() {
    if (!retrieveService()) {
      ServicePort sp = new ServicePortBuilder()
        .withProtocol("TCP")
        .withPort(4444)
        .withNewTargetPort(4444)
        .build();
      Service sb = new ServiceBuilder()
        .withNewMetadata()
        .withName(PHANTOMJS)
        .addToLabels("name", PHANTOMJS)
        .endMetadata()
        .withNewSpec()
        .withSessionAffinity("None")
        .addToSelector("name", PHANTOMJS)
        .addToPorts(sp)
        .endSpec()
        .build();
      client.services().create(sb);
    }
  }

  @Override
  public void close() throws Exception {
    if (driver != null) {
      driver.close();
      driver = null;
    }
    retrievePod();
    client.pods().delete(pod);
    retrieveService();
    client.services().delete(service);
    retrieveRoute();
    client.routes().delete(route);
  }
}
