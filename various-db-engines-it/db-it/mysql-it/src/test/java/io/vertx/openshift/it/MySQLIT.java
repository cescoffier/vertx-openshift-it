package io.vertx.openshift.it;

import io.vertx.it.openshift.utils.OC;
import org.junit.BeforeClass;

import java.io.IOException;

import static io.vertx.it.openshift.utils.Kube.awaitUntilPodIsReady;

public class MySQLIT extends AbstractDBTestClass {

  @BeforeClass
  public static void initialize() throws IOException {
    System.out.println("Deploying postgres");

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
