package io.vertx.openshift.it;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.jayway.awaitility.Awaitility.await;
import static com.jayway.restassured.RestAssured.get;

/**
 * @author Martin Spisiak (mspisiak@redhat.com) on 19/10/17.
 */
public class OracleExternalDbIT extends AbstractInternalDBTestClass {

  private static final String API_LIST_ROUTE = "/api/vegetables/";

  @BeforeClass
  public static void initialize() throws IOException {
    deployAndAwaitStartWithRoute("/healthcheck");
  }

  @Test
  @Ignore
  @Override
  public void restartDBTest () {
    super.restartDBTest();
  }

  @Test
  @Ignore
  @Override
  public void runDBAfterDeployAppTest () throws IOException, InterruptedException {
    super.runDBAfterDeployAppTest();
  }

  @Before
  public void awaitReadiness() {
    System.out.println("Pods running, waiting for probes...");

    await("Pods running, waiting for probes...").pollInterval(1, TimeUnit.SECONDS).atMost(5, TimeUnit.MINUTES).catchUncaughtExceptions().until(() ->
      get("/healthcheck").getStatusCode() == 200
    );
  }


}
