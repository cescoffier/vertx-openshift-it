package io.vertx.openshift.jdbc;

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
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class JdbcIT extends AbstractTestClass {

  public static final String POSTGRES = "postgres";

  @BeforeClass
  public static void initialize() throws IOException {

    // If not created, start the posgresSQL
    System.out.println("Deploying postgres");
    // Start PostGres SQL
//       oc new-app openshift/postgresql-92-centos7 \
//      -e POSTGRESQL_USER=user \
//      -e POSTGRESQL_DATABASE=db \
//      -e POSTGRESQL_PASSWORD=password

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

  @Test
  public void testQueryWithParams() {
    ensureThat("we can execute a query with parameters", () ->
      get("/query_with_params").then()
        .assertThat()
        .statusCode(200)
    );
  }

  @Test
  public void testCRUD() {
    ensureThat("we can execute a CRUD actions", () ->
      get("/crud").then()
        .assertThat()
        .statusCode(200)
    );
  }

  @Test
  public void testUpdateWithParams() {
    ensureThat("we can execute an update with parameters", () ->
      get("/update_with_params").then()
        .assertThat()
        .statusCode(200)
    );
  }

  @Test
  public void testStoredProcedure() {
    ensureThat("we can execute a stored procedure", () ->
      get("/stored_procedure").then()
        .assertThat()
        .statusCode(200)
    );
  }

  @Test
  public void testBatchUpdates() {
    ensureThat("we can execute updates in batch", () ->
      get("/batch_updates").then()
        .assertThat()
        .statusCode(200)
    );
  }

  @Test
  public void testStreamingResults() {
    ensureThat("we can execute read the results as a stream", () ->
      get("/streaming_results").then()
        .assertThat()
        .statusCode(200)
    );
  }

  @Test
  public void testTransactions() {
    ensureThat("we can execute transactions", () ->
      get("/transactions").then()
        .assertThat()
        .statusCode(200)
    );
  }

  @Test
  public void testSpecialDatatypes() {
    ensureThat("we can used special data types", () ->
      get("/special_datatypes").then()
        .assertThat()
        .statusCode(200)
    );
  }

  @Test
  public void testBinary() {
    ensureThat("we can execute retrieve binary data", () ->
      get("/binary").then()
        .assertThat()
        .statusCode(200)
    );
  }

  @Test
  public void testDdl() {
    ensureThat("we can use DDL", () ->
      get("/ddl").then()
        .assertThat()
        .statusCode(200)
    );
  }

  @Test
  public void testClientCreation() {
    ensureThat("we can create a JDBC client", () ->
      get("/client_creation").then()
        .assertThat()
        .statusCode(200)
    );
  }


}
