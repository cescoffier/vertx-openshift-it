package io.vertx.openshift.it;

import io.vertx.it.openshift.utils.AbstractTestClass;
import io.vertx.it.openshift.utils.OC;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static com.jayway.restassured.RestAssured.get;
import static io.vertx.it.openshift.utils.Ensure.ensureThat;
import static io.vertx.it.openshift.utils.Kube.awaitUntilPodIsReady;

/**
 * @author Martin Spisiak (mspisiak@redhat.com) on 03/10/17.
 */
public class PostgreSQLTest extends AbstractTestClass {
  public static final String POSTGRES = "postgres";

  @BeforeClass
  public static void initialize() throws IOException {
    System.out.println("Deploying postgres");

    OC.execute("project", client.getNamespace());
    OC.execute("new-app", "openshift/postgresql-92-centos7",
      "-e", "POSTGRESQL_USER=vertx",
      "-e", "POSTGRESQL_DATABASE=testdb",
      "-e", "POSTGRESQL_PASSWORD=password",
      "--name=" + POSTGRES);

    awaitUntilPodIsReady(client, POSTGRES);

    deployAndAwaitStartWithRoute("/init");
  }

  @AfterClass
  public static void dbCleanup() throws IOException {
    client.deploymentConfigs().withName(POSTGRES).withGracePeriod(0).delete();
    client.services().withName(POSTGRES).withGracePeriod(0).delete();
    client.imageStreams().withName(POSTGRES).withGracePeriod(0).delete();
  }


  @Test
  public void testTextQuery() {
    ensureThat("we can execute a text query", () ->
      get("/text_query").then()
        .assertThat()
        .statusCode(200));
  }
}
