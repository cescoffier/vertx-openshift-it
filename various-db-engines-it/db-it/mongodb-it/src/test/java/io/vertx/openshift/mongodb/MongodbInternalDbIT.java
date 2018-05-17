package io.vertx.openshift.mongodb;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.vertx.it.openshift.utils.OC;
import io.vertx.openshift.utils.AbstractInternalDBTestClass;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;

import static io.restassured.RestAssured.*;
import static io.vertx.it.openshift.utils.Ensure.ensureThat;
import static io.vertx.it.openshift.utils.Kube.awaitUntilPodIsReady;
import static org.hamcrest.core.IsEqual.equalTo;

/**
 * Integration tests for internal mongodb db
 * Includes only initialize things
 * Test suite is extended from AbstractInternalDBTestClass
 */
public class MongodbInternalDbIT extends AbstractInternalDBTestClass {

  /**
   * This method initialize db and application before tests will be started
   * @throws IOException
   */
  @BeforeClass
  public static void initialize() throws IOException, InterruptedException {
    System.out.println("Deploying mongodb");

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
    OC.execute("new-app", "registry.access.redhat.com/rhscl/mongodb-26-rhel7",
      "-e", "MONGODB_USER=vertx",
      "-e", "MONGODB_DATABASE=testdb",
      "-e", "MONGODB_PASSWORD=password",
      "-e", "MONGODB_ADMIN_PASSWORD=password",
      "--name=" + DB_NAME);

    awaitUntilPodIsReady(client, DB_NAME);
  }

  @Ignore
  @Test
  @Override
  public void runDBAfterDeployAppTest() throws Exception { }

  @Override
  public void CRUDTest() {
    System.out.println("Starting CRUD test");
    String vegetableName = "Pickles";
    String updatedVegetableName = "Cucumbers";
    int amountOfVegetable = 128;

    //Test create new item

    Response postResponse = createItem(vegetableName);
    ensureThat("we can create a new vegetable", () -> postResponse
      .then().assertThat().statusCode(201).contentType("application/json")
    );
    String vegetableId = postResponse.getBody().jsonPath().getString("id");

    //Test get created
    ensureThat("the vegetable has been created", () -> get(API_LIST_ROUTE + vegetableId)
      .then().assertThat().body("name", equalTo(vegetableName))
    );

    //Test update created
    JSONObject modifyVegetable = new JSONObject().put("name", updatedVegetableName).put("amount", amountOfVegetable).put("id", vegetableId);
    ensureThat("we can update the vegetable", () -> updateItem(vegetableId, modifyVegetable)
      .then().assertThat().statusCode(201)
    );

    //Test updated
    ensureThat("the vegetable has been updated", () -> get(API_LIST_ROUTE + vegetableId)
      .then().assertThat().body("name", equalTo(updatedVegetableName)).and().body("amount", equalTo(amountOfVegetable))
    );

    //Test delete one
    ensureThat("we can delete the vegetable", () -> delete(API_LIST_ROUTE + vegetableId)
      .then().assertThat().statusCode(204)
    );

    //Test get deleted
    ensureThat("the vegetable has been deleted", () -> get(API_LIST_ROUTE + vegetableId)
      .then().assertThat().statusCode(404)
    );
  }

  public Response updateItem(String id, JSONObject body) {
    return given().body(ContentType.JSON).body(body.toString()).put(API_LIST_ROUTE + id);
  }

  @Test
  public void bulkTest(){

    JSONObject Tomato = new JSONObject()
      .put("name","Tomato")
      .put("stock",10);
    JSONObject Celery = new JSONObject()
      .put("name","Celery")
      .put("stock",10);
    JSONObject Cabbage = new JSONObject()
      .put("name","Cabbage")
      .put("stock",10);
    JSONObject Broccoli = new JSONObject()
      .put("name","Broccoli");
    JSONArray testData = new JSONArray();
    testData.put(Tomato);
    testData.put(Cabbage);
    testData.put(Celery);
    testData.put(Broccoli);
    //Create
    Response addResponse = given().body(testData.toString()).contentType(ContentType.JSON).post(API_LIST_ROUTE+"add");
    ensureThat("We can bulk create vegetables",()->{
      addResponse.then().assertThat().statusCode(201);
    });
    //GET
    Response getPageResponse = get(API_LIST_ROUTE+"page/1");
    ensureThat("Get saved data", ()-> getPageResponse.then().assertThat().statusCode(200));
    JSONObject givenData = new JSONObject(getPageResponse.getBody().prettyPrint());
    JSONArray result = givenData.getJSONArray("result");

    //UPDATE
    JSONArray updatedData = new JSONArray();
    JSONObject deleteVegetablesId = new JSONObject();
    JSONArray ids = new JSONArray();

    for (int i = 0; i < result.length();i++){
        JSONObject object = result.getJSONObject(i);
        updatedData.put(object.put("stock",6));
        ids.put(object.getString("_id"));
    }
    deleteVegetablesId.put("ids", ids);

    Response updateResponse = given().body(updatedData.toString()).put(API_LIST_ROUTE+"update");
    ensureThat("We can bulk update" , () -> {
      updateResponse.then().statusCode(201);
    });


    ensureThat("We can bulk delete", () -> {
      given().body(deleteVegetablesId.toString()).delete(API_LIST_ROUTE+"delete").then().assertThat().statusCode(204);
    });

  }
}
