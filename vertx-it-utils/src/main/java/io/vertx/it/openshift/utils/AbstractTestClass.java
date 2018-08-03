package io.vertx.it.openshift.utils;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.OpenShiftClient;
import io.restassured.RestAssured;
import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.AfterClass;
import org.junit.Rule;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;

import static io.fabric8.openshift.assertions.Assertions.assertThat;
import static io.restassured.RestAssured.get;
import static io.vertx.it.openshift.utils.Ensure.ensureThat;
import static java.util.Collections.emptySortedMap;
import static org.awaitility.Awaitility.await;

/**
 * @author Slavomír Krupa (slavomir.krupa@gmail.com)
 */
public class AbstractTestClass {

  protected static final Boolean keepResources = Boolean.getBoolean("keepResources");

  protected static OpenShiftTestAssistant deploymentAssistant = new OpenShiftTestAssistant();
  protected static OpenShiftClient client = deploymentAssistant.client();
  protected static OpenShiftHelper helper = new OpenShiftHelper(client, "");

  @Rule
  public final JUnitSoftAssertions softly = new JUnitSoftAssertions();


  public static void deployAndAwaitStartWithRoute() throws IOException {
    deployAndAwaitStartWithRoute("");
  }

  public static void deployAndAwaitStartWithRoute(String pathSuffix) throws IOException {
    deployAndAwaitStartWithRoute(emptySortedMap(), pathSuffix);
  }


  public static void deployAndAwaitStartWithRoute(SortedMap<String, File> otherDeployments, String pathSuffix) throws IOException {

    deployAndAwaitStart(otherDeployments);
    await(String.format("the route is accessible at %s%s .", RestAssured.baseURI, pathSuffix))
      .atMost(10, TimeUnit.MINUTES)
      .catchUncaughtExceptions()
      .until(() -> get(pathSuffix).statusCode() <= 204);
  }

  private static void deleteDeployPods() {
    client.pods().list().getItems().stream().filter(p -> p.getMetadata().getName().endsWith("-deploy")).forEach(
      p -> client.pods().delete(p));
  }

  public static void deployAndAwaitStart() throws IOException {
    deployAndAwaitStart(emptySortedMap());
  }

  public static void deployAndAwaitStart(SortedMap<String, File> otherDeployments) throws IOException {
    otherDeployments.forEach((name, template) ->
      ensureThat(String.format("template file %s can be deployed", template), () -> deploymentAssistant.deploy(name, template))
    );
    ensureThat("application can be deployed", deploymentAssistant::deployApplication);
    ensureThat("application is started", deploymentAssistant::awaitApplicationReadinessOrFail);
    helper = new OpenShiftHelper(client, deploymentAssistant.applicationName());
  }

  public static String deployApp(String name, String templatePath) throws IOException {
    String appName;
    List<? extends HasMetadata> entities = deploymentAssistant.deploy(name, new File(templatePath));

    Optional<String> first = entities.stream().filter(hm -> hm instanceof DeploymentConfig).map(hm -> (DeploymentConfig) hm)
      .map(dc -> dc.getMetadata().getName()).findFirst();
    if (first.isPresent()) {
      appName = first.get();
    } else {
      throw new IllegalStateException("Application deployment config not found");
    }

    Route route = deploymentAssistant.getRoute(appName);
    assertThat(route).isNotNull();
    return "http://" + route.getSpec().getHost();
  }

  @AfterClass
  public static void cleanup() {
    if (!keepResources) {
      deploymentAssistant.cleanup();
      deleteDeployPods();
    }
  }
}
