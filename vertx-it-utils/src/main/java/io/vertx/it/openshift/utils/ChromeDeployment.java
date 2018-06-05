package io.vertx.it.openshift.utils;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.OpenShiftClient;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.net.MalformedURLException;
import java.net.URL;

import static io.vertx.it.openshift.utils.Kube.awaitUntilRouteIsServed;
import static io.vertx.it.openshift.utils.Kube.createRouteForService;
import static io.vertx.it.openshift.utils.Kube.urlForRoute;

/**
 * @author Martin Spisiak (mspisiak@redhat.com) on 29/05/18.
 */
public class ChromeDeployment implements AutoCloseable {
  public static final String CHROME = "chrome";
  private OpenShiftClient client;
  private Route route;
  private Pod pod;
  private Service service;
  private WebDriver driver;

  public ChromeDeployment(OpenShiftClient client) {
    this.client = client;
  }

  public ChromeDeployment deploy() {
    System.out.println("Deploying Chrome");
    createChromeService();
    createChromePod();
    createChromeRoute();
    return this;
  }

  public ChromeDeployment deployAndAwaitStart() {
    deploy();
    awaitUntilRouteIsServed(route, "/status");
    return this;
  }

  public URL getUrl() throws MalformedURLException {
    if (retrieveRoute()) {
      return new URL(urlForRoute(route).toString() + "/wd/hub");
    }
    throw new IllegalStateException("Chrome is not deployed");
  }

  public WebDriver connectToWebService() throws MalformedURLException {
    DesiredCapabilities capabilities = DesiredCapabilities.chrome();
    driver = new RemoteWebDriver(getUrl(), capabilities);
    driver.manage().window().setSize(new Dimension(1920, 1080));
    return driver;
  }

  private void createChromeRoute() {
    if (!retrieveRoute()) {
      route = createRouteForService(client, CHROME, true);
    }
  }

  private boolean retrieveRoute() {
    route = client.routes().withName(CHROME).get();
    return route != null;
  }

  private boolean retrievePod() {
    pod = client.pods().withName(CHROME).get();
    return pod != null;
  }

  private boolean retrieveService() {
    service = client.services().withName(CHROME).get();
    return service != null;
  }

  private void createChromePod() {
    if (!retrievePod()) {
      Container c = new ContainerBuilder()
        .withName(CHROME)
        .withImage("selenium/standalone-chrome")
        .withImagePullPolicy("Always")
        .withEnv(new EnvVar("IGNORE_SSL_ERRORS", "true", null))
        .withPorts(new ContainerPortBuilder().withContainerPort(4444).withName("webdriver").build())
        .build();

      Pod pb = new PodBuilder()
        .withNewMetadata()
        .withName(CHROME)
        .addToLabels("name", CHROME)
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

  private void createChromeService() {
    if (!retrieveService()) {
      ServicePort sp = new ServicePortBuilder()
        .withProtocol("TCP")
        .withPort(4444)
        .withNewTargetPort(4444)
        .build();
      Service sb = new ServiceBuilder()
        .withNewMetadata()
        .withName(CHROME)
        .addToLabels("name", CHROME)
        .endMetadata()
        .withNewSpec()
        .withSessionAffinity("None")
        .addToSelector("name", CHROME)
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
