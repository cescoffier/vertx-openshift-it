package io.vertx.openshift.micrometer;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.arquillian.cube.openshift.impl.enricher.AwaitRoute;
import org.arquillian.cube.openshift.impl.enricher.RouteURL;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.URL;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static io.vertx.it.openshift.utils.Ensure.ensureThat;
import static org.awaitility.Awaitility.await;

/**
 * TODO implement more tests
 *
 * @author Martin Spisiak (mspisiak@redhat.com) on 04/10/2018.
 */
@RunWith(Arquillian.class)
public class MicrometerMetricsIT {

  private static final String APP_NAME = System.getProperty("app.name");

  @RouteURL("${app.name}")
  @AwaitRoute
  private URL route;

  @RouteURL("prom")
  @AwaitRoute
  private URL prometheusRoute;

  @Before
  public void startup() {
    RestAssured.baseURI = prometheusRoute.toString();
  }

  @Test
  public void testVertxEndpointAvailable() {
    ensureThat("Vert.x server is up according to Prometheus", () ->
      await().atMost(15, TimeUnit.SECONDS).until(() -> given()
        .accept(ContentType.JSON)
        .get("/api/v1/targets")
        .body()
        .jsonPath()
        .getString("data.activeTargets.findAll { it -> it.labels.job == \"vertx\" }.health")
        .equalsIgnoreCase("[up]"))
    );
  }
}
