package io.vertx.openshift.proton;

import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.vertx.it.openshift.utils.AbstractTestClass;
import io.vertx.it.openshift.utils.OC;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;

/**
 * @author Martin Spisiak (mspisiak@redhat.com) on 31/07/18.
 */
public class ProtonIT extends AbstractTestClass {

  private static String dashboardUrl;
  private static String healthCheck = "/api/health";
  private String dataUrl;
  private String requestUrl;
  private String responseUrl;

  @BeforeClass
  public static void init() throws IOException {
    OC.execute("apply", "-f", System.getProperty("proton.amq"));
    deployApp("vertx-proton-backend", System.getProperty("proton.backend"));
    dashboardUrl = deployApp("vertx-proton-frontend", System.getProperty("proton.frontend"));
    RestAssured.baseURI = dashboardUrl;

    await(String.format("the route is accessible at %s%s.", dashboardUrl, healthCheck))
      .atMost(5, TimeUnit.MINUTES)
      .until(() -> get(healthCheck).statusCode() <= 204);

  }

  @Before
  public void before() {
    dataUrl = dashboardUrl + "/api/data";
    requestUrl = dashboardUrl + "/api/send-request";
    responseUrl = dashboardUrl + "/api/receive-response";
  }

  @Test
  public void shouldHandleRequest() {
    // Issue a request
    Response requestResponse = given()
      .body("{\"text\":\"test-message\",\"uppercase\":true,\"reverse\":true}")
      .and()
      .contentType("application/json")
      .when()
      .post(requestUrl)
      .thenReturn();

    assertThat(requestResponse.getStatusCode()).isEqualTo(202);
    String requestId = requestResponse.getBody().asString();

    // Wait for the request to be handled
    await().atMost(10, SECONDS)
      .untilAsserted(() -> given()
        .queryParam("request", requestId)
        .when()
        .get(responseUrl)
        .then()
        .statusCode(200)
        .body("requestId", is(equalTo(requestId)))
        .body("workerId", not((isEmptyString())))
        .body("text", is(equalTo("EGASSEM-TSET"))));

    JsonPath responseJson = given()
      .queryParam("request", requestId)
      .when()
      .get(responseUrl)
      .thenReturn()
      .jsonPath();
    String workerId = responseJson.getString("workerId");
    String text = responseJson.getString("text");

    // Verify data
    await().atMost(10, SECONDS)
      .untilAsserted(() -> when()
        .get(dataUrl)
        .then()
        .statusCode(200)
        .body("requestIds", hasItem(requestId))
        .body("responses.%s.requestId", withArgs(requestId), is(equalTo(requestId)))
        .body("responses.%s.workerId", withArgs(requestId), is(equalTo(workerId)))
        .body("responses.%s.text", withArgs(requestId), is(equalTo(text)))
        .body("workers.%s.workerId", withArgs(workerId), is(equalTo(workerId)))
        .body("workers.%s.timestamp", withArgs(workerId), is(notNullValue()))
        .body("workers.%s.requestsProcessed", withArgs(workerId), is(notNullValue()))
        .body("workers.%s.processingErrors", withArgs(workerId), is(notNullValue())));
  }

  @AfterClass
  public static void deleteAmq() {
    OC.execute("delete", "-f", System.getProperty("proton.amq"));
  }
}
