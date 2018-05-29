package io.vertx.openshift.proxy;

import io.fabric8.openshift.api.model.Route;
import io.vertx.it.openshift.utils.AbstractTestClass;
import io.vertx.it.openshift.utils.ChromeDeployment;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.util.Date;

import static io.vertx.it.openshift.utils.Ensure.ensureThat;
import static io.vertx.it.openshift.utils.Kube.urlForRoute;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Martin Spisiak (mspisiak@redhat.com) on 25/05/18.
 */
public class SockJSProxyIT extends AbstractTestClass {
  private static ChromeDeployment CHROME;
  private static WebDriver webDriver;

  @BeforeClass
  public static void initialize() throws Exception {
    deployAndAwaitStartWithRoute("/");
    CHROME = new ChromeDeployment(client);
    webDriver = CHROME.deployAndAwaitStart().connectToWebService();
  }

  @Test
  public void testSockJSProxy() {
    ensureThat("the sockjs proxy serves requests correctly", () -> {
      Route route = client.routes().withName("sockjs-service-proxy").get();
      webDriver.get(urlForRoute(route).toString());
    });

    ensureThat("the sockjs proxy returns correct value", () -> {
      boolean elemTextEqualsVal = (new WebDriverWait(webDriver, 10))
        .until(ExpectedConditions.textToBe(By.id("eb-status"), "Hello Martin from Vert.x running on OpenShift!"));
      assertThat(elemTextEqualsVal).isTrue();
    });
  }

  @AfterClass
  public static void chromeClose() throws Exception {
    CHROME.close();
  }

  private void analyzeLog() {
    LogEntries logEntries = webDriver.manage().logs().get(LogType.BROWSER);
    for (LogEntry entry : logEntries) {
      System.out.println(new Date(entry.getTimestamp()) + " " + entry.getLevel() + " " + entry.getMessage());
    }
  }

}
