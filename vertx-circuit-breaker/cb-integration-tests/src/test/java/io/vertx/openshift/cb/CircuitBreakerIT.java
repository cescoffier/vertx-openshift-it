package io.vertx.openshift.cb;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.vertx.circuitbreaker.CircuitBreakerState;
import io.vertx.core.json.JsonObject;
import org.arquillian.cube.openshift.impl.enricher.AwaitRoute;
import org.arquillian.cube.openshift.impl.enricher.RouteURL;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.URL;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.core.IsEqual.equalTo;

@RunWith(Arquillian.class)
public class CircuitBreakerIT {

  private static final String NAME_SERVICE_APP = "name-service";
  private static final String GREETING_SERVICE_APP = "greeting-service";

  private static final String OK = "ok";
  private static final String FAIL = "fail";
  private static final String HELLO_OK = "Hello, World!";
  private static final String HELLO_FALLBACK = "Hello, Fallback!";

  private static final long SLEEP_WINDOW = 5000L;
  private static final long REQUEST_THRESHOLD = 3;

  @RouteURL(NAME_SERVICE_APP)
  @AwaitRoute(path = "/health")
  private URL nameService;

  @RouteURL(GREETING_SERVICE_APP)
  @AwaitRoute(path = "/health")
  private URL greetingService;

  @Before
  public void assureServiceIsWorking() {
    await().atMost(10, TimeUnit.SECONDS).until(() -> testGreeting(HELLO_OK));
  }

  @Test
  public void testThatCircuitBreakerIsClosedByDefault() {
    assertCircuitBreaker(CircuitBreakerState.CLOSED);
    assertGreeting(HELLO_OK);
  }

  @Test
  public void testThatCircuitBreakerIsOpenedAfterFailures() {
    changeNameServiceState(FAIL);
    for (int i = 0; i < REQUEST_THRESHOLD; i++) {
      assertGreeting(HELLO_FALLBACK);
    }
    // Circuit breaker should be open now
    await().atMost(5, TimeUnit.SECONDS).until(() -> testCircuitBreakerState(CircuitBreakerState.OPEN));
    changeNameServiceState(OK);
    await().atMost(7, TimeUnit.SECONDS).pollDelay(SLEEP_WINDOW, TimeUnit.MILLISECONDS).until(() -> testGreeting(HELLO_OK));
    // The health counts should be reset
    assertCircuitBreaker(CircuitBreakerState.CLOSED);
  }

  @Test
  public void testThatWeExposeHalfOpenState() {
    changeNameServiceState(FAIL);
    for (int i = 0; i < REQUEST_THRESHOLD; i++) {
      assertGreeting(HELLO_FALLBACK);
    }
    // Circuit breaker should be open now
    await().atMost(5, TimeUnit.SECONDS).until(() -> testCircuitBreakerState(CircuitBreakerState.OPEN));
    await().atMost(5, TimeUnit.SECONDS).until(() -> testCircuitBreakerState(CircuitBreakerState.HALF_OPEN));
    // when half open state shows up we should switch to
    changeNameServiceState(OK);
    assertGreeting(HELLO_OK);
    assertCircuitBreaker(CircuitBreakerState.CLOSED);
  }

  private Response greetingResponse() {
    return RestAssured.when().get(greetingService + "api/greeting");
  }

  private void assertGreeting(String expected) {
    Response response = greetingResponse();
    response.then().statusCode(200).body("content", equalTo(expected));
  }

  private boolean testGreeting(String expected) {
    Response response = greetingResponse();
    response.then().statusCode(200);
    return response.getBody().jsonPath().getString("content").equals(expected);
  }

  private Response circuitBreakerResponse() {
    return RestAssured.when().get(greetingService + "api/cb-state");
  }

  private void assertCircuitBreaker(CircuitBreakerState expectedState) {
    Response response = circuitBreakerResponse();
    response.then().statusCode(200).body("state", equalTo(expectedState.name()));
  }

  private boolean testCircuitBreakerState(CircuitBreakerState expectedState) {
    Response response = circuitBreakerResponse();
    response.then().statusCode(200);
    return response.getBody().asString().contains(expectedState.name());
  }

  private void changeNameServiceState(String state) {
    Response response = RestAssured.given().header("Content-type", "application/json")
      .body(new JsonObject().put("state", state).encodePrettily()).put(nameService + "api/state");
    response.then().assertThat().statusCode(200).body("state", equalTo(state));
  }
}
