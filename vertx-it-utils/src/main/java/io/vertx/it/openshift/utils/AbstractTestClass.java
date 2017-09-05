package io.vertx.it.openshift.utils;

import com.jayway.restassured.RestAssured;
import io.fabric8.openshift.client.OpenShiftClient;
import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.AfterClass;
import org.junit.Rule;

import java.io.File;
import java.io.IOException;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;

import static com.jayway.awaitility.Awaitility.*;
import static com.jayway.restassured.RestAssured.*;
import static io.vertx.it.openshift.utils.Ensure.*;
import static java.util.Collections.*;

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
      .atMost(3, TimeUnit.MINUTES)
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

  @AfterClass
  public static void cleanup() {
    if (!keepResources) {
      deploymentAssistant.cleanup();
      deleteDeployPods();
    }
  }
}
