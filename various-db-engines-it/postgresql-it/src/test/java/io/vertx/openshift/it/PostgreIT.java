package io.vertx.openshift.it;

import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.response.Response;
import com.jayway.restassured.specification.RequestSpecification;
import com.jayway.restassured.path.json.JsonPath;
import io.vertx.it.openshift.utils.AbstractTestClass;
import io.vertx.it.openshift.utils.OC;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import static com.jayway.restassured.RestAssured.*;
import static io.vertx.it.openshift.utils.Ensure.ensureThat;
import static io.vertx.it.openshift.utils.Kube.awaitUntilPodIsReady;
import static org.hamcrest.core.IsEqual.equalTo;

/**
 * @author Martin Spisiak (mspisiak@redhat.com) on 03/10/17.
 */
public class PostgreIT extends DBTest {
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

    deployAndAwaitStartWithRoute("/healthcheck");
  }

  @AfterClass
  public static void dbCleanup() throws IOException {
    client.deploymentConfigs().withName(POSTGRES).withGracePeriod(0).delete();
    client.services().withName(POSTGRES).withGracePeriod(0).delete();
    client.imageStreams().withName(POSTGRES).withGracePeriod(0).delete();
  }


  @Test
  public void CRUDTest() {
    super.CRUDTest();
  }

}
