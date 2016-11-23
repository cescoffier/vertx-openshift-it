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
}
