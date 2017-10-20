package io.vertx.openshift.it;

import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.response.Response;
import com.jayway.restassured.specification.RequestSpecification;
import io.vertx.it.openshift.utils.AbstractTestClass;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.jayway.awaitility.Awaitility.await;
import static com.jayway.restassured.RestAssured.delete;
import static com.jayway.restassured.RestAssured.get;
import static com.jayway.restassured.RestAssured.given;
import static io.vertx.it.openshift.utils.Ensure.ensureThat;
import static org.hamcrest.core.IsEqual.equalTo;

/**
 * @author Martin Spisiak (mspisiak@redhat.com) on 19/10/17.
 */
public class OracleExternalDbIT extends AbstractTestClass {

  private static final String API_LIST_ROUTE = "/api/vegetables/";

  @BeforeClass
  public static void initialize() throws IOException {
    deployAndAwaitStartWithRoute("/healthcheck");
  }

  @Before
  public void awaitReadiness() {
    System.out.println("Pods running, waiting for probes...");

    await("Pods running, waiting for probes...").pollInterval(1, TimeUnit.SECONDS).atMost(5, TimeUnit.MINUTES).catchUncaughtExceptions().until(() ->
      get("/healthcheck").getStatusCode() == 200
    );
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

  private Response createItem (String name) {
    return setRequestJSONBody(new JSONObject().put("name",name)).post(API_LIST_ROUTE);
  }

  private Response updateItem (int id ,JSONObject body) {
    return  setRequestJSONBody(body).put(API_LIST_ROUTE+id);
  }

  private RequestSpecification setRequestJSONBody (JSONObject body) {
    return given().content(ContentType.JSON).body(body.toString());
  }
}
