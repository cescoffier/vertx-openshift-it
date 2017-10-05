package io.vertx.openshift.it;

import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.response.Response;
import com.jayway.restassured.specification.RequestSpecification;
import io.vertx.it.openshift.utils.AbstractTestClass;
import org.json.JSONObject;

import static com.jayway.restassured.RestAssured.delete;
import static com.jayway.restassured.RestAssured.get;
import static com.jayway.restassured.RestAssured.given;
import static io.vertx.it.openshift.utils.Ensure.ensureThat;
import static org.hamcrest.core.IsEqual.equalTo;

public class DBTest extends AbstractTestClass {
  public static final String API_LIST_ROUTE = "/api/vegetables/";
  public void CRUDTest() {
    String vegetableName = "Pickles";
    String updatedVegetableName = "Cucumbers";
    int ammoutOfVegetable = 128;

    //Test create new item
    Response postResponse = createItem(vegetableName);
    ensureThat("Test of response of create new vegetable", () -> postResponse.then().assertThat().body("name", equalTo(vegetableName)));
    int vegetableId = postResponse.getBody().jsonPath().getInt("id");

    //Test get created
    ensureThat("Get created vegetable", () -> get(API_LIST_ROUTE+vegetableId).then().assertThat().body("name",equalTo(vegetableName)));

    //Test update created
    JSONObject modifyVegetable = new JSONObject().put("name",updatedVegetableName).put("amount",ammoutOfVegetable).put("id",vegetableId);
    ensureThat("Update one item", () -> updateItem(vegetableId,modifyVegetable).then().assertThat().body("name", equalTo("Cucumbers")).and().body("amount",equalTo(ammoutOfVegetable)));

    //Test updated
    ensureThat("Get created vegetable", () -> get(API_LIST_ROUTE+vegetableId).then().assertThat().body("name",equalTo(updatedVegetableName)).and().body("amount",equalTo(ammoutOfVegetable)));

    //Test delete one
    ensureThat("Delete item", () -> delete(API_LIST_ROUTE+vegetableId).then().assertThat().statusCode(204));

    //Test get deleted
    ensureThat("Get created vegetable", () -> get(API_LIST_ROUTE+vegetableId).then().assertThat().statusCode(404));
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
}
