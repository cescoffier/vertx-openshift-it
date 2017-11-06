package io.vertx.openshift.postgresql;

import io.vertx.it.openshift.utils.OC;
import io.vertx.openshift.utils.AbstractInternalDBTestClass;
import org.junit.BeforeClass;

import java.io.IOException;

import static io.vertx.it.openshift.utils.Kube.awaitUntilPodIsReady;

/**
 * Integration tests for internal postgre db
 * Includes only initialize things
 * Test suite is extended from AbstractInternalDBTestClass
 */
public class PostgreSQLInternalDbIT extends AbstractInternalDBTestClass {

  /**
   * This method initialize db and application before tests will be started
   * @throws IOException
   */
  @BeforeClass
  public static void initialize() throws IOException {
    System.out.println("Deploying postgres");

    initDB();
    deployAndAwaitStartWithRoute("/healthcheck");
  }

  /**
   * This method is used for runDBAfterDeployAppTest
   */
  @Override
  protected void deployDB (){
    initDB();
  }

  /**
   * Initialize and wait then db is ready
   */
  public static void initDB () {
    OC.execute("project", client.getNamespace());
    OC.execute("new-app", "registry.access.redhat.com/rhscl/postgresql-95-rhel7",
      "-e", "POSTGRESQL_USER=vertx",
      "-e", "POSTGRESQL_DATABASE=testdb",
      "-e", "POSTGRESQL_PASSWORD=password",
      "--name=" + DB_NAME);

    awaitUntilPodIsReady(client, DB_NAME);
  }

}
