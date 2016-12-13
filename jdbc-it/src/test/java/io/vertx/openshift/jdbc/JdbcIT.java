package io.vertx.openshift.jdbc;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.OpenShiftClient;
import io.restassured.RestAssured;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import static com.jayway.awaitility.Awaitility.*;
import static io.fabric8.kubernetes.assertions.internal.Assertions.*;
import static io.restassured.RestAssured.*;
import static io.vertx.openshift.jdbc.OpenshiftHelper.*;

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
  private OpenShiftClient oc;

  @Before
  public void initialize() {
    oc = client.adapt(OpenShiftClient.class);


    // If not created, start the posgresSQL
    if (oc.services().withName("postgres").get() == null) {
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
      oc_execute("project", "default");
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

    await().atMost(10, TimeUnit.MINUTES).until(this::isServed);

    RestAssured.get(url("/init")).then().assertThat().statusCode(200);
  }

  @Test
  public void testTextQuery() {

    RestAssured.get(url("/text_query")).then()
      .assertThat()
      .statusCode(200);
  }

  @Test
  public void testQueryWithParams() {
    RestAssured.get(url("/query_with_params")).then()
      .assertThat()
      .statusCode(200);
  }

  @Test
  public void testCRUD() {
    RestAssured.get(url("/crud")).then()
      .assertThat()
      .statusCode(200);
  }

  @Test
  public void testUpdateWithParams() {
    RestAssured.get(url("/update_with_params")).then()
      .assertThat()
      .statusCode(200);
  }

  @Test
  public void testStoredProcedure() {
    RestAssured.get(url("/stored_procedure")).then()
      .assertThat()
      .statusCode(200);
  }

  @Test
  public void testBatchUpdates() {
    RestAssured.get(url("/batch_updates")).then()
      .assertThat()
      .statusCode(200);
  }

  @Test
  public void testStreamingResults() {
    RestAssured.get(url("/streaming_results")).then()
      .assertThat()
      .statusCode(200);
  }

  @Test
  public void testTransactions() {
    RestAssured.get(url("/transactions")).then()
      .assertThat()
      .statusCode(200);
  }

  private URL url() {
    try {
      return new URL("http://" + route.getSpec().getHost());
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  private URL url(String path) {
    try {
      return new URL("http://" + route.getSpec().getHost() + path);
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  private boolean isServed() {
    try {
      return get(url()).getStatusCode() == 200;
    } catch (Exception e) {
      return false;
    }
  }
}
