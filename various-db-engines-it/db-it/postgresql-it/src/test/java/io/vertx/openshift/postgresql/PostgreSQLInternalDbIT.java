package io.vertx.openshift.postgresql;

import io.vertx.it.openshift.utils.OC;
import io.vertx.openshift.utils.AbstractInternalDBTestClass;
import org.junit.BeforeClass;

import java.io.IOException;

import static io.vertx.it.openshift.utils.Kube.awaitUntilPodIsReady;

public class PostgreSQLInternalDbIT extends AbstractInternalDBTestClass {

  @BeforeClass
  public static void initialize() throws IOException {
    System.out.println("Deploying postgres");

    OC.execute("project", client.getNamespace());
    OC.execute("new-app", "registry.access.redhat.com/rhscl/postgresql-95-rhel7",
      "-e", "POSTGRESQL_USER=vertx",
      "-e", "POSTGRESQL_DATABASE=testdb",
      "-e", "POSTGRESQL_PASSWORD=password",
      "--name=" + DB_NAME);

    awaitUntilPodIsReady(client, DB_NAME);

    deployAndAwaitStartWithRoute("/healthcheck");
  }

  @Override
  protected void deployDB (){
    OC.execute("project", client.getNamespace());
    OC.execute("new-app", "registry.access.redhat.com/rhscl/postgresql-95-rhel7",
      "-e", "POSTGRESQL_USER=vertx",
      "-e", "POSTGRESQL_DATABASE=testdb",
      "-e", "POSTGRESQL_PASSWORD=password",
      "--name=" + DB_NAME);

    awaitUntilPodIsReady(client, DB_NAME);
  }

}
