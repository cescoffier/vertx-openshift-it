package io.vertx.openshift.it;

import io.vertx.it.openshift.utils.OC;
import org.junit.BeforeClass;

import java.io.IOException;

import static io.vertx.it.openshift.utils.Kube.awaitUntilPodIsReady;

public class MySQLInternalDbIT extends AbstractInternalDBTestClass {

  @BeforeClass
  public static void initialize() throws IOException {
    System.out.println("Deploying mysql");

    OC.execute("project", client.getNamespace());
    OC.execute("new-app", "mysql",
      "-e", "MYSQL_USER=vertx",
      "-e", "MYSQL_DATABASE=testdb",
      "-e", "MYSQL_PASSWORD=password",
      "--name=" + DB_NAME);


    awaitUntilPodIsReady(client, DB_NAME);
    deployAndAwaitStartWithRoute("/healthcheck");
  }
}
