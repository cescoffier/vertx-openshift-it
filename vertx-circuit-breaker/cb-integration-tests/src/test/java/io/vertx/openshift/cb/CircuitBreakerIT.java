package io.vertx.openshift.cb;

import io.fabric8.kubernetes.api.model.Pod;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.vertx.circuitbreaker.CircuitBreakerState;
import io.vertx.core.json.JsonObject;
import io.vertx.it.openshift.utils.AbstractTestClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.get;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.core.IsEqual.equalTo;

public class CircuitBreakerIT extends AbstractTestClass {

  private static final String NAME_SERVICE_APP = "name-service";
  private static final String GREETING_SERVICE_APP = "greeting-service";

  private static final String OK = "ok";
  private static final String FAIL = "fail";
  private static final String HELLO_OK = "Hello, World!";
  private static final String HELLO_FALLBACK = "Hello, Fallback!";

  private static final long SLEEP_WINDOW = 5000L;
  private static final long REQUEST_THRESHOLD = 3;

  private static String nameBaseUri;
  private static String greetingBaseUri;

  @BeforeClass
  public static void setup() throws Exception {

    nameBaseUri = deployApp(NAME_SERVICE_APP, System.getProperty("nameServiceTemplate"));
    greetingBaseUri = deployApp(GREETING_SERVICE_APP, System.getProperty("greetingServiceTemplate"));

    await().atMost(5, TimeUnit.MINUTES).until(() -> {
      List<Pod> list = client.pods().inNamespace(deploymentAssistant.project()).list().getItems();
      return list.stream()
        .filter(pod -> pod.getMetadata().getName().startsWith(NAME_SERVICE_APP) || pod.getMetadata().getName().startsWith(GREETING_SERVICE_APP))
        .filter(pod -> "running".equalsIgnoreCase(pod.getStatus().getPhase())).collect(Collectors.toList()).size() >= 2;
    });

    System.out.println("Pods running, waiting for probes...");

    await().pollInterval(1, TimeUnit.SECONDS).atMost(5, TimeUnit.MINUTES).catchUncaughtExceptions().until(() ->
      get(greetingBaseUri + "/health").statusCode() == 200 && get(nameBaseUri + "/health").statusCode() == 200

    );
  }

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
    return RestAssured.when().get(greetingBaseUri + "/api/greeting");
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
    return RestAssured.when().get(greetingBaseUri + "/api/cb-state");
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
      .body(new JsonObject().put("state", state).encodePrettily()).put(nameBaseUri + "/api/state");
    response.then().assertThat().statusCode(200).body("state", equalTo(state));
  }
}
