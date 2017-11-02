package io.vertx.openshift.utils;

import org.junit.Before;
import org.junit.BeforeClass;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.jayway.awaitility.Awaitility.await;
import static com.jayway.restassured.RestAssured.get;

public abstract class AbstractExternalDBTestClass extends AbstractDBTestClass {

  @BeforeClass
  public static void initialize() throws IOException {
    deployAndAwaitStartWithRoute("/healthcheck");
  }

  @Before
  public void awaitReadiness() {
    System.out.println("Pods running, waiting for probes...");

    await("Pods running, waiting for probes...").pollInterval(1, TimeUnit.SECONDS)
      .atMost(5, TimeUnit.MINUTES).catchUncaughtExceptions().until(() -> get("/healthcheck").getStatusCode() == 200);
  }
}
