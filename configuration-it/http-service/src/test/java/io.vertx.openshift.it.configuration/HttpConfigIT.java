package io.vertx.openshift.it.configuration;

import com.jayway.restassured.path.json.JsonPath;
import io.vertx.it.openshift.utils.AbstractTestClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static com.jayway.restassured.RestAssured.get;
import static io.vertx.it.openshift.utils.Ensure.ensureThat;

public class HttpConfigIT extends AbstractTestClass {
  private static final String HTTP_CONFIG_EXPECTED_STRING = "Congratulations, you have just served a configuration over HTTP !";

  @BeforeClass
  public static void initialize() throws IOException {
    deployAndAwaitStartWithRoute("/conf");
  }

  @Test
  public void testConfigRetrieval() throws InterruptedException {
    ensureThat("we can retrieve the http application configuration", () -> get("/conf").then().statusCode(200));
    ensureThat("the configuration is the expected configuration", () -> {
      final JsonPath response = get("/conf").getBody().jsonPath();
      softly.assertThat(response.getString("http-config-content")).isEqualTo(HTTP_CONFIG_EXPECTED_STRING);
    });
  }
}
