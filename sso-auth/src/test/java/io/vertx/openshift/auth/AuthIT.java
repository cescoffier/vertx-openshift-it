package io.vertx.openshift.auth;

import io.restassured.RestAssured;
import io.vertx.it.openshift.utils.AbstractTestClass;
import io.vertx.it.openshift.utils.Kube;
import io.vertx.it.openshift.utils.OC;
import org.junit.*;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.vertx.it.openshift.utils.Ensure.ensureThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static io.restassured.RestAssured.*;
import static org.hamcrest.CoreMatchers.*;

public class AuthIT extends AbstractTestClass {

  private static String ssoEndpoint;

  @BeforeClass
  public static void init() throws IOException {
    OC.execute("create", "-f", "service.sso.yaml");
    ssoEndpoint = OC.executeWithQuotes(false,
      "get", "route", "secure-sso", "-o", "jsonpath='{\"https://\"}{.spec.host}{\"/auth\"}'")
      .replace("'", "");

    /* Await the sso server to be ready so we can make token requests. We cannot use @AwaitRoute as we are deploying
    the sso server as part of the tests.
    When making requests too early (the sso server is not ready yet), it throws SSLHandshakeException,
    so we are taking care of that in the try-catch here. */
    await().atMost(5, TimeUnit.MINUTES).until(() -> {
      try {
        return given().relaxedHTTPSValidation().when().get(ssoEndpoint).statusCode() == 200;
      } catch (Exception ex) {
        return false;
      }
    });

    // replace ${SSO_AUTH_SERVER_URL} placeholder in OpenShift template with actual SSO server URL
    Path path = Paths.get("target/classes/META-INF/fabric8/openshift.yml");
    Charset charset = StandardCharsets.UTF_8;
    String content = new String(Files.readAllBytes(path), charset);
    content = content.replaceAll("\\$\\{SSO_AUTH_SERVER_URL}", ssoEndpoint);
    Files.write(path, content.getBytes(charset));

    File template = new File("target/classes/META-INF/fabric8/openshift.yml");
    ensureThat(String.format("template file %s can be deployed", template), () ->
      deploymentAssistant.deploy("sso-auth", template));

    ensureThat("the sso-auth app is up and running", () ->
      await("Waiting for the sso-auth app to be ready..").atMost(5, TimeUnit.MINUTES).untilAsserted(() ->
        assertThat(client.deploymentConfigs().withName("sso-auth").isReady()).isTrue()));

    RestAssured.baseURI = Kube.urlForRoute(client.routes().withName("sso-auth").get()).toString();
  }

  @AfterClass
  public static void deleteSSO() {
    OC.execute("delete", "all", "--selector", "application=sso");
    OC.execute("delete", "secret", "sso-app-secret");
    OC.execute("delete", "secret", "sso-demo-secret");
    OC.execute("delete", "serviceaccount", "sso-service-account");
  }

  @Test
  public void JWTDefaultUserDefaultFrom() {
    String token = getToken("alice", "password");

    given().header("Authorization", "Bearer " + token)
      .when().get("/jwt-greeting")
      .then().body("content", equalTo("Hello, World!"));
  }

  @Test
  public void JWTDefaultUserCustomFrom() {
    String token = getToken("alice", "password");

    given().header("Authorization", "Bearer " + token)
      .when().get("/jwt-greeting?name=Scott")
      .then().body("content", equalTo("Hello, Scott!"));
  }

  @Test
  public void JWTAdminUser() {
    String token = getToken("admin", "admin");

    given().header("Authorization", "Bearer " + token)
      .when().get("/jwt-greeting")
      .then().statusCode(403); // should be 403 Forbidden, as the admin user does not have the required role
  }

  @Test
  public void JWTBadPassword() {
    String token = getToken("alice", "bad");

    given().header("Authorization", "Bearer " + token)
      .when().get("/jwt-greeting?name=Scott")
      .then().statusCode(401); // should be 401 Unauthorized, as auth fails because of providing bad password (token is null)
  }

  @Test
  public void OAuth2DefaultUserDefaultFrom() {
    given()
      .param("username", "alice")
      .param("password", "password")
      .when()
      .post("/oauth2-greeting")
      .then()
      .body("content", equalTo("Hello, World!"));
  }

  @Test
  public void OAuth2DefaultUserCustomFrom() {
    given()
      .param("username", "alice")
      .param("password", "password")
      .param("name", "Scott")
      .when()
      .post("/oauth2-greeting")
      .then()
      .body("content", equalTo("Hello, Scott!"));
  }

  @Test
  public void OAuth2AdminUser() {
    given()
      .param("username", "admin")
      .param("password", "admin")
      .when()
      .post("/oauth2-greeting")
      .then()
      .statusCode(403); // should be 403 Forbidden, as the admin user does not have the required role
  }

  @Test
  public void OAuth2BadPassword() {
    given()
      .param("username", "alice")
      .param("password", "bad")
      .param("name", "Scott")
      .when()
      .post("/oauth2-greeting")
      .then()
      .statusCode(401); // should be 401 Unauthorized, as auth fails because of providing bad password (token is null)
  }

  private String getToken(String username, String password) {
    Map<String, String> requestParams = new HashMap<>();
    try {
      requestParams.put("grant_type", "password");
      requestParams.put("username", URLEncoder.encode(username, "UTF8"));
      requestParams.put("password", URLEncoder.encode(password, "UTF8"));
      requestParams.put("client_id", "demoapp");
      requestParams.put("client_secret", "1daa57a2-b60e-468b-a3ac-25bd2dc2eadc");
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }

    return given()
      .relaxedHTTPSValidation()
      .params(requestParams)
      .when()
      .post(ssoEndpoint + "/realms/master/protocol/openid-connect/token")
      .path("access_token");
  }
}
