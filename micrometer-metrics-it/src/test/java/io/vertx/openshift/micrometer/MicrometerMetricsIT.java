package io.vertx.openshift.micrometer;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.vertx.it.openshift.utils.AbstractTestClass;
import io.vertx.it.openshift.utils.OC;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.get;
import static io.restassured.RestAssured.given;
import static io.vertx.it.openshift.utils.Ensure.ensureThat;
import static io.vertx.it.openshift.utils.Kube.urlForRoute;
import static org.awaitility.Awaitility.await;

/**
 * TODO implement more tests
 *
 * @author Martin Spisiak (mspisiak@redhat.com) on 04/10/2018.
 */
public class MicrometerMetricsIT extends AbstractTestClass {
  private static final String APP_NAME = "micrometer-metrics";
  private static String route;

  @BeforeClass
  public static void initialize() throws Exception {
    String processedPrometheus;
    String prometheusUrl;
    OC.execute("create", "secret", "generic", "prom", "--from-file=target/classes/prometheus.yml");
    processedPrometheus = OC.execute(false, "process", "-f", "target/classes/prometheus-standalone.yml");
    try (PrintWriter out = new PrintWriter("processed-prometheus.yml")) {
      out.println(processedPrometheus);
    }
    OC.execute("apply", "-f", "processed-prometheus.yml");

    route = deployApp(APP_NAME, "target/classes/META-INF/fabric8/openshift.yml");
    RestAssured.baseURI = prometheusUrl = urlForRoute(client.routes().withName("prom").get()).toString();

    await(String.format("the route is accessible at %s.", route))
      .atMost(5, TimeUnit.MINUTES)
      .until(() -> get(route).statusCode() <= 204 && given().relaxedHTTPSValidation().get(prometheusUrl + "/metrics").statusCode() <= 204);
  }

  @Test
  public void testVertxEndpointAvailable() {
    ensureThat("Vert.x server is up according to Prometheus", () ->
      await().atMost(10, TimeUnit.SECONDS).until(() -> given()
        .accept(ContentType.JSON)
        .get("/api/v1/targets")
        .body()
        .jsonPath()
        .getString("data.activeTargets.findAll { it -> it.labels.job == \"vertx\" }.health")
        .equalsIgnoreCase("[up]"))
    );
  }

  @AfterClass
  public static void destroyResources() throws IOException {
    OC.execute("delete", "all", "-l", "name=prom");
    OC.execute("delete", "secret", "-l", "name=prom");
    OC.execute("delete", "secret", "prom");
    boolean delete = new File("processed-prometheus.yml").delete();
    if (delete) System.out.println("Processed prometheus template file successfully deleted");
    else throw new IOException("Unable to delete processed prometheus template file");
  }
}
