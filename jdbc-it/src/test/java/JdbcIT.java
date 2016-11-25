import com.jayway.restassured.RestAssured;
import org.junit.Test;

/**
 * @author Thomas Segismont
 */
public class JdbcIT {

  public static final String BASE_URL = System.getProperty("BASE_URL", "http://localhost:8080");

  @Test
  public void testTextQuery() {
    RestAssured.get(BASE_URL + "/text_query").then()
      .assertThat()
      .statusCode(200);
  }

  @Test
  public void testQueryWithParams() {
    RestAssured.get(BASE_URL + "/query_with_params").then()
      .assertThat()
      .statusCode(200);
  }

  @Test
  public void testCRUD() {
    RestAssured.get(BASE_URL + "/crud").then()
      .assertThat()
      .statusCode(200);
  }

  @Test
  public void testUpdateWithParams() {
    RestAssured.get(BASE_URL + "/update_with_params").then()
      .assertThat()
      .statusCode(200);
  }

  @Test
  public void testStoredProcedure() {
    RestAssured.get(BASE_URL + "/stored_procedure").then()
      .assertThat()
      .statusCode(200);
  }

  @Test
  public void testBatchUpdates() {
    RestAssured.get(BASE_URL + "/batch_updates").then()
      .assertThat()
      .statusCode(200);
  }
}
