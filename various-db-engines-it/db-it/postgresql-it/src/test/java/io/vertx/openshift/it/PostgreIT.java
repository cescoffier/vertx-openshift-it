package io.vertx.openshift.it;

import io.vertx.it.openshift.utils.OC;
import org.junit.BeforeClass;

import java.io.IOException;

import static com.jayway.restassured.RestAssured.delete;
import static com.jayway.restassured.RestAssured.get;
import static com.jayway.restassured.RestAssured.given;
import static io.vertx.it.openshift.utils.Ensure.ensureThat;
import static io.vertx.it.openshift.utils.Kube.awaitUntilPodIsReady;

public class PostgreIT extends AbstractDBTestClass {

  @BeforeClass
  public static void initialize() throws IOException {
    System.out.println("Deploying postgres");

    OC.execute("project", client.getNamespace());
    OC.execute("new-app", "openshift/postgresql-92-centos7",
      "-e", "POSTGRESQL_USER=vertx",
      "-e", "POSTGRESQL_DATABASE=testdb",
      "-e", "POSTGRESQL_PASSWORD=password",
      "--name=" + DB_NAME);


    awaitUntilPodIsReady(client, DB_NAME);

    deployAndAwaitStartWithRoute("/healthcheck");
  }


}
