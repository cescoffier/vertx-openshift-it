package io.vertx.openshift.utils;

import org.junit.AfterClass;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.jayway.awaitility.Awaitility.await;
import static com.jayway.restassured.RestAssured.get;
import static io.vertx.it.openshift.utils.Kube.awaitUntilPodIsReady;

/**
 * Include specific tests and help function for internal db
 */
public abstract class AbstractInternalDBTestClass extends AbstractDBTestClass {

  @Test
  public void runDBAfterDeployAppTest () throws Exception {
    System.out.println("Run deploy app before deploy db");
    dbCleanup();
    cleanup();
    deploymentAssistant.deployApplication();
    TimeUnit.SECONDS.sleep(10);
    deployDB();
    awaitUntilPodIsReady(client, DB_NAME);
    await("Pods running, waiting for probes...").pollInterval(1, TimeUnit.SECONDS)
      .atMost(5, TimeUnit.MINUTES).catchUncaughtExceptions().until(() -> get("/healthcheck").getStatusCode() == 200);
    //Test if app is connected to DB
    CRUDTest();
  }

  @AfterClass
  public static void dbCleanup() throws IOException {
    client.deploymentConfigs().withName(DB_NAME).withGracePeriod(0).delete();
    client.services().withName(DB_NAME).withGracePeriod(0).delete();
    client.imageStreams().withName(DB_NAME).withGracePeriod(0).delete();
  }

  protected abstract void deployDB();
}
