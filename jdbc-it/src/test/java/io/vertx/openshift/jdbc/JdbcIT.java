package io.vertx.openshift.jdbc;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.OpenShiftClient;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.MalformedURLException;
import java.net.URL;

import static io.fabric8.kubernetes.assertions.internal.Assertions.assertThat;
import static io.restassured.RestAssured.get;
import static io.vertx.it.openshift.utils.Ensure.ensureThat;
import static io.vertx.it.openshift.utils.Kube.awaitUntilPodIsReady;
import static io.vertx.it.openshift.utils.Kube.awaitUntilRouteIsServed;
import static io.vertx.it.openshift.utils.Kube.oc;
import static io.vertx.openshift.jdbc.OpenshiftHelper.oc_execute;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
@RunWith(Arquillian.class)
@RunAsClient
public class JdbcIT {

  private static final String NAME = "jdbc-it";

  @ArquillianResource
  private KubernetesClient client;

  private Route route;

  @Before
  public void initialize() {
    OpenShiftClient oc = oc(client);


    // If not created, start the posgresSQL
    if (oc.services().withName("postgres").get() == null) {
      System.out.println("Deploying postgres");
      // Start PostGres SQL
      // oc new-app openshift/postgresql-92-centos7 \
      //-e POSTGRESQL_USER=user \
      //-e POSTGRESQL_DATABASE=db \
      //-e POSTGRESQL_PASSWORD=password

      oc_execute("project", oc.getNamespace());
      oc_execute("new-app", "openshift/postgresql-92-centos7",
        "-e", "POSTGRESQL_USER=vertx",
        "-e", "POSTGRESQL_DATABASE=testdb",
        "-e", "POSTGRESQL_PASSWORD=password",
        "--name=postgres"
      );
      String existing = System.getenv("NAMESPACE_USE_EXISTING");
      if (existing == null) {
        oc_execute("project", "default");
      }
    } else {
      System.out.println("Postgres already deployed");
      awaitUntilPodIsReady(client, "postgres");
    }

    // The route is exposed using .vagrant.f8 suffix, delegate to openshift to
    // get a public URL
    oc.routes()
      .withName(NAME).delete();

    Route route = oc.routes().createNew()
      .withNewMetadata().withName(NAME).endMetadata()
      .withNewSpec()
      .withNewTo().withName(NAME).withKind("Service").endTo()
      .endSpec()
      .done();

    assertThat(route).isNotNull();
    this.route = route;

    awaitUntilRouteIsServed(route);

    get(url("/init")).then().assertThat().statusCode(200);
  }

  @Test
  public void testTextQuery() {
    ensureThat("we can execute a text query", () ->
      get(url("/text_query")).then()
        .assertThat()
        .statusCode(200));
  }

  @Test
  public void testQueryWithParams() {
    ensureThat("we can execute a query with parameters", () ->
      get(url("/query_with_params")).then()
      .assertThat()
      .statusCode(200)
    );
  }

  @Test
  public void testCRUD() {
    ensureThat("we can execute a CRUD actions", () ->
      get(url("/crud")).then()
      .assertThat()
      .statusCode(200)
    );
  }

  @Test
  public void testUpdateWithParams() {
    ensureThat("we can execute an update with parameters", () ->
      get(url("/update_with_params")).then()
      .assertThat()
      .statusCode(200)
    );
  }

  @Test
  public void testStoredProcedure() {
    ensureThat("we can execute a stored procedure", () ->
      get(url("/stored_procedure")).then()
      .assertThat()
      .statusCode(200)
    );
  }

  @Test
  public void testBatchUpdates() {
    ensureThat("we can execute updates in batch", () ->
      get(url("/batch_updates")).then()
      .assertThat()
      .statusCode(200)
    );
  }

  @Test
  public void testStreamingResults() {
    ensureThat("we can execute read the results as a stream", () ->
      get(url("/streaming_results")).then()
      .assertThat()
      .statusCode(200)
    );
  }

  @Test
  public void testTransactions() {
    ensureThat("we can execute transactions", () ->
      get(url("/transactions")).then()
      .assertThat()
      .statusCode(200)
    );
  }

  @Test
  public void testSpecialDatatypes() {
    ensureThat("we can used special data types", () ->
      get(url("/special_datatypes")).then()
      .assertThat()
      .statusCode(200)
    );
  }

  @Test
  public void testBinary() {
    ensureThat("we can execute retrieve binary data", () ->
      get(url("/binary")).then()
      .assertThat()
      .statusCode(200)
    );
  }

  @Test
  public void testDdl() {
    ensureThat("we can use DDL", () ->
      get(url("/ddl")).then()
      .assertThat()
      .statusCode(200)
    );
  }

  @Test
  public void testClientCreation() {
    ensureThat("we can create a JDBC client", () ->
      get(url("/client_creation")).then()
      .assertThat()
      .statusCode(200)
    );
  }

  private URL url(String path) {
    try {
      return new URL("http://" + route.getSpec().getHost() + path);
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

}
