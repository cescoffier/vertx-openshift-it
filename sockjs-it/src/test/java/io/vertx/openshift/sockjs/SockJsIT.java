package io.vertx.openshift.sockjs;

import static org.awaitility.Awaitility.await;
import static io.restassured.RestAssured.get;

import static io.vertx.it.openshift.utils.Ensure.ensureThat;
import static io.vertx.it.openshift.utils.Kube.urlForRoute;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import io.restassured.path.json.JsonPath;

import java.util.concurrent.TimeUnit;

import io.fabric8.openshift.api.model.Route;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.sockjs.Transport;
import io.vertx.it.openshift.utils.AbstractTestClass;
import io.vertx.it.openshift.utils.PhantomJSDeployment;

public class SockJsIT extends AbstractTestClass {

  private static final String EB_STATUS = "eb-status";
  private static final String SOCK_STATUS = "sock-status";
  private static PhantomJSDeployment PHANTOM;
  private static WebDriver webDriver;

  @BeforeClass
  public static void initialize() throws Exception {
    PHANTOM = new PhantomJSDeployment(client).deploy();
    deployAndAwaitStartWithRoute("/status");
    webDriver = PHANTOM.deployAndAwaitStart().connectToWebService();
  }

  @Test
  public void checkSockStatuses() throws InterruptedException {
    ensureThat("we can navigate to application ", () -> {
      final Route route = client.routes().withName(deploymentAssistant.applicationName()).get();
      webDriver.navigate().to(urlForRoute(route));
    });
    ensureThat("both statuses are updated", () -> {
      await()
        .atMost(3, TimeUnit.MINUTES)
        .catchUncaughtExceptions()
        .until(() -> isElementEmpty(EB_STATUS));
      await()
        .atMost(3, TimeUnit.MINUTES)
        .catchUncaughtExceptions()
        .until(() -> isElementEmpty(SOCK_STATUS));
    });
    ensureThat("all transports are working and asserted", () -> {
      final JsonPath serverStatus = get("/status").body().jsonPath();
      final JsonObject clientSideEbStatus = getJsonById(EB_STATUS);
      final JsonObject clientSideSockStatus = getJsonById(SOCK_STATUS);
      for (Transport t : Transport.values()) {
        softly.assertThat(serverStatus.getBoolean(t + "-EB")).as("Transport " + t + " should be used on server side (on EventBus). ").isTrue();
        softly.assertThat(serverStatus.getBoolean(t + "-sock")).as("Transport " + t + " should be used on server side (on SockJS). ").isTrue();
        softly.assertThat(clientSideEbStatus.getInteger(t.name())).as("Transport " + t + " should be used on client side (on EventBus). ").isGreaterThan(0);
        softly.assertThat(clientSideSockStatus.getInteger(t.name())).as("Transport " + t + " should be used on client side (on SockJS). ").isGreaterThan(0);
      }
    });
  }

  public Boolean isElementEmpty(String selector) {
    return !getJsonById(selector).isEmpty();
  }

  public JsonObject getJsonById(String selector) {
    final WebElement element = webDriver.findElement(By.id(selector));
    return new JsonObject(element.getText());
  }

  @AfterClass
  public static void undeployPhantom() throws Exception {
    PHANTOM.close();
  }
}
