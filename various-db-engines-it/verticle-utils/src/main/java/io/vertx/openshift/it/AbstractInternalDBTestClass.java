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


public abstract class AbstractInternalDBTestClass extends AbstractTestClass {

  private static final String API_LIST_ROUTE = "/api/vegetables/";
  static final String DB_NAME = "db";
  private static final int WAIT = 30;


  @Test
  public void restartDBTest () {
    System.out.println("Start DB restart test");
    shutDownDB();
    startDB();
    CRUDTest();
  }

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


  @Test
  public void CRUDTest() {
    System.out.println("Starting CRUD test");
    String vegetableName = "Pickles";
    String updatedVegetableName = "Cucumbers";
    int ammoutOfVegetable = 128;

    //Test create new item
    Response postResponse = createItem(vegetableName);
    ensureThat("we can create a new vegetable", () -> postResponse.then().assertThat().body("name", equalTo(vegetableName)));
    int vegetableId = postResponse.getBody().jsonPath().getInt("id");

    //Test get created
    ensureThat("the vegetable has been created", () -> get(API_LIST_ROUTE+vegetableId).then().assertThat().body("name",equalTo(vegetableName)));

    //Test update created
    JSONObject modifyVegetable = new JSONObject().put("name",updatedVegetableName).put("amount",ammoutOfVegetable).put("id",vegetableId);
    ensureThat("we can update the vegetable", () -> updateItem(vegetableId,modifyVegetable).then().assertThat().body("name", equalTo("Cucumbers")).and().body("amount",equalTo(ammoutOfVegetable)));

    //Test updated
    ensureThat("the vegetable has been updated", () -> get(API_LIST_ROUTE+vegetableId).then().assertThat().body("name",equalTo(updatedVegetableName)).and().body("amount",equalTo(ammoutOfVegetable)));

    //Test delete one
    ensureThat("we can delete the vegetable", () -> delete(API_LIST_ROUTE+vegetableId).then().assertThat().statusCode(204));

    //Test get deleted
    ensureThat("the vegetable has been deleted", () -> get(API_LIST_ROUTE+vegetableId).then().assertThat().statusCode(404));
  }

  public Response createItem (String name) {
    return setRequestJSONBody(new JSONObject().put("name",name)).post(API_LIST_ROUTE);
  }

  public Response updateItem (int id ,JSONObject body) {
    return  setRequestJSONBody(body).put(API_LIST_ROUTE+id);
  }

  private RequestSpecification setRequestJSONBody (JSONObject body) {
    return given().content(ContentType.JSON).body(body.toString());
  }

  @AfterClass
  public static void dbCleanup() throws IOException {
    client.deploymentConfigs().withName(DB_NAME).withGracePeriod(0).delete();
    client.services().withName(DB_NAME).withGracePeriod(0).delete();
    client.imageStreams().withName(DB_NAME).withGracePeriod(0).delete();
  }

}
