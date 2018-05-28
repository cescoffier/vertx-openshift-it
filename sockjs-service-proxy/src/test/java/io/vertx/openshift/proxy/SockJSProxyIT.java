package io.vertx.openshift.proxy;

import io.fabric8.openshift.api.model.Route;
import io.vertx.it.openshift.utils.AbstractTestClass;
import io.vertx.it.openshift.utils.PhantomJSDeployment;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import static io.vertx.it.openshift.utils.Ensure.ensureThat;
import static io.vertx.it.openshift.utils.Kube.urlForRoute;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Martin Spisiak (mspisiak@redhat.com) on 25/05/18.
 */
public class SockJSProxyIT extends AbstractTestClass {
  private static PhantomJSDeployment PHANTOM;
  private static WebDriver webDriver;

  @BeforeClass
  public static void initialize() throws Exception {
    deployAndAwaitStartWithRoute("/");
    PHANTOM = new PhantomJSDeployment(client);
    webDriver = PHANTOM.deployAndAwaitStart().connectToWebService();
  }

  @Test
  public void testSockJSProxy() {
    ensureThat("the sockjs proxy serves requests correctly", () -> {
      Route route = client.routes().withName("sockjs-service-proxy").get();
      webDriver.get(urlForRoute(route).toString());
    });

    System.out.println(webDriver.getTitle());
    System.out.println(webDriver.getPageSource());

    ensureThat("the sockjs proxy returns correct value", () -> {
      boolean elemTextEqualsVal = (new WebDriverWait(webDriver, 10))
        .until(ExpectedConditions.textToBe(By.id("eb-status"), "Hello Martin from Vert.x running on OpenShift!"));
      assertThat(elemTextEqualsVal).isTrue();
    });
  }

  @AfterClass
  public static void phantomClose() throws Exception {
    PHANTOM.close();
  }
}
