package io.vertx.openshift.cb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

import static org.awaitility.Awaitility.await;
import static io.restassured.RestAssured.get;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import io.restassured.RestAssured;
import io.restassured.response.Response;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.Route;
import io.vertx.it.openshift.utils.OpenShiftTestAssistant;
import io.vertx.circuitbreaker.CircuitBreakerState;
import io.vertx.core.json.JsonObject;

public class CircuitBreakerIT {

  private static final String NAME_SERVICE_APP = "name-service";
  private static final String GREETING_SERVICE_APP = "greeting-service";

  private static final String OK = "ok";
  private static final String FAIL = "fail";
  private static final String HELLO_OK = "Hello, World!";
  private static final String HELLO_FALLBACK = "Hello, Fallback!";

  private static final long SLEEP_WINDOW = 5000l;
  private static final long REQUEST_THRESHOLD = 3;

  private static final OpenShiftTestAssistant OPENSHIFT = new OpenShiftTestAssistant();

  private static String nameBaseUri;
  private static String greetingBaseUri;

  @BeforeClass
  public static void setup() throws Exception {

    nameBaseUri = deployApp(NAME_SERVICE_APP, System.getProperty("nameServiceTemplate"));
    greetingBaseUri = deployApp(GREETING_SERVICE_APP, System.getProperty("greetingServiceTemplate"));

    await().atMost(5, TimeUnit.MINUTES).until(() -> {
      List<Pod> list = OPENSHIFT.client().pods().inNamespace(OPENSHIFT.project()).list().getItems();
      return list.stream()
        .filter(pod -> pod.getMetadata().getName().startsWith(NAME_SERVICE_APP) || pod.getMetadata().getName().startsWith(GREETING_SERVICE_APP))
        .filter(pod -> "running".equalsIgnoreCase(pod.getStatus().getPhase())).collect(Collectors.toList()).size() >= 2;
    });

    System.out.println("Pods running, waiting for probes...");

    await().pollInterval(1, TimeUnit.SECONDS).atMost(5, TimeUnit.MINUTES).catchUncaughtExceptions().until(() ->
      get(greetingBaseUri + "/health").statusCode() == 200 && get(nameBaseUri + "/health").statusCode() == 200

    );
  }

  @AfterClass
  public static void teardown() throws Exception {
    OPENSHIFT.cleanup();
  }

  @Before
  public void assureServiceIsWorking() {
    await().atMost(10, TimeUnit.SECONDS).until(() -> testGreeting(HELLO_OK));
  }

  @Test
  public void testThatCircuitBreakerIsClosedByDefault() throws InterruptedException {
    assertCircuitBreaker(CircuitBreakerState.CLOSED);
    assertGreeting(HELLO_OK);
  }

  @Test
  public void testThatCircuitBreakerIsOpenedAfterFailures() throws InterruptedException {
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
  public void testThatWeExposeHalfOpenState() throws InterruptedException {
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

  /**
   * @param name
   * @param templatePath
   * @return the app route
   * @throws IOException
   */
  private static String deployApp(String name, String templatePath) throws IOException {
    String appName = "";
    List<? extends HasMetadata> entities = OPENSHIFT.deploy(name, new File(templatePath));

    Optional<String> first = entities.stream().filter(hm -> hm instanceof DeploymentConfig).map(hm -> (DeploymentConfig) hm)
      .map(dc -> dc.getMetadata().getName()).findFirst();
    if (first.isPresent()) {
      appName = first.get();
    } else {
      throw new IllegalStateException("Application deployment config not found");
    }
    Route route = OPENSHIFT.client().routes().inNamespace(OPENSHIFT.project()).withName(appName).get();
    assertThat(route).isNotNull();
    return "http://" + route.getSpec().getHost();
  }
}
