package io.vertx.openshift.it;

import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.response.Response;
import com.jayway.restassured.specification.RequestSpecification;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.ServiceStatus;
import io.vertx.it.openshift.utils.AbstractTestClass;
import io.vertx.it.openshift.utils.OC;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.jayway.restassured.RestAssured.*;
import static io.vertx.it.openshift.utils.Ensure.ensureThat;
import static io.vertx.it.openshift.utils.Kube.awaitUntilPodIsReady;
import static io.vertx.it.openshift.utils.Kube.name;
import static org.hamcrest.core.IsEqual.equalTo;


public abstract class AbstractInternalDBTestClass extends AbstractDBTestClass {

  @Ignore
  @Test
  public void restartDBTest () {
    System.out.println("Start DB restart test");
    shutDownDB();
    startDB();
    CRUDTest();
  }

  @Ignore
  @Test
  public void runDBAfterDeployAppTest () throws IOException, InterruptedException {
    cleanup();
    shutDownDB();
    deploymentAssistant.deployApplication();
    TimeUnit.SECONDS.sleep(WAIT);
    startDB();
    deploymentAssistant.awaitApplicationReadinessOrFail();
    ensureThat("Test if app is fully deployed", () -> get("/healthcheck").then().assertThat().statusCode(200));
    //Test if app is connected to DB
    CRUDTest();
  }

  private void shutDownDB() {
    scaleDB(0);
  }

  private void startDB() {
    scaleDB(1);
  }

  private void scaleDB (int replicas) {
    OC.execute("scale","deploymentconfig", "--replicas="+replicas, DB_NAME );
  }

  @AfterClass
  public static void dbCleanup() throws IOException {
    client.deploymentConfigs().withName(DB_NAME).withGracePeriod(0).delete();
    client.services().withName(DB_NAME).withGracePeriod(0).delete();
    client.imageStreams().withName(DB_NAME).withGracePeriod(0).delete();
  }

}
