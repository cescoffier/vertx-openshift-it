package io.vertx.openshift.mysql;

import io.vertx.it.openshift.utils.OC;
import io.vertx.openshift.utils.AbstractInternalDBTestClass;
import org.junit.BeforeClass;

import java.io.IOException;

import static io.vertx.it.openshift.utils.Kube.awaitUntilPodIsReady;

/**
 * Integration tests for internal mysql db
 * Includes only initialize things
 * Test suite is extended from AbstractInternalDBTestClass
 */
public class MySQLInternalDbIT extends AbstractInternalDBTestClass {

  /**
   * This method initialize db and application before tests will be started
   * @throws IOException
   */
  @BeforeClass
  public static void initialize() throws IOException {
    System.out.println("Deploying mysql");

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
    OC.execute("new-app", "registry.access.redhat.com/rhscl/mysql-57-rhel7",
      "-e", "MYSQL_USER=vertx",
      "-e", "MYSQL_DATABASE=testdb",
      "-e", "MYSQL_PASSWORD=password",
      "--name=" + DB_NAME);
    awaitUntilPodIsReady(client, DB_NAME);
  }
}
