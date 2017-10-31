package io.vertx.openshift.cb;

import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.PermittedOptions;
import io.vertx.rxjava.circuitbreaker.CircuitBreaker;
import io.vertx.rxjava.circuitbreaker.HystrixMetricHandler;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.ext.web.Router;
import io.vertx.rxjava.ext.web.RoutingContext;
import io.vertx.rxjava.ext.web.client.HttpResponse;
import io.vertx.rxjava.ext.web.client.WebClient;
import io.vertx.rxjava.ext.web.handler.StaticHandler;
import io.vertx.rxjava.ext.web.handler.sockjs.SockJSHandler;

import static io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_JSON;
import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

public class GreetingServiceVerticle extends AbstractVerticle {

  protected static final String template = "Hello, %s!";

  private CircuitBreaker circuit;
  private WebClient client;

  @Override
  public void start() throws Exception {
    circuit = CircuitBreaker.create("circuit-breaker", vertx,
      new CircuitBreakerOptions()
        .setFallbackOnFailure(true)
        .setMaxFailures(3)
        .setResetTimeout(5000)
        .setNotificationAddress("circuit-breaker")
        .setTimeout(1000)
    );

    client = WebClient.create(vertx, new WebClientOptions()
      .setDefaultHost("name-service")
      .setDefaultPort(8080));

    Router router = Router.router(vertx);

    router.get("/health").handler(rc -> rc.response().end("OK"));
    router.get("/eventbus/*").handler(getSockJsHandler());
    // The address is the circuit breaker notification address configured above.
    router.get("/metrics").handler(HystrixMetricHandler.create(vertx, "circuit-breaker"));


    router.get("/api/greeting").handler(this::greeting);
    router.get("/api/cb-state").handler(
      rc -> rc.response()
        .putHeader(CONTENT_TYPE.toString(), APPLICATION_JSON.toString())
        .end(new JsonObject().put("state", circuit.state()).encodePrettily()));
    router.get("/*").handler(StaticHandler.create());

    vertx.createHttpServer()
      .requestHandler(router::accept)
      .listen(8080);
  }

  private void greeting(RoutingContext rc) {
    circuit.rxExecuteCommandWithFallback(
      future ->
        client.get("/api/name").rxSend()
          .doOnEach(r -> System.out.println(r.getValue().bodyAsString()))
          .map(HttpResponse::bodyAsJsonObject)
          .map(json -> json.getString("name"))
          .subscribe(
            future::complete,
            future::fail
          ),
      error -> {
        System.out.println("Fallback called for " + error.getMessage());
        error.printStackTrace();
        return "Fallback";
      }
    )
      .subscribe(
        name -> {
          JsonObject response = new JsonObject()
            .put("content", String.format(template, name));
          rc.response()
            .putHeader(CONTENT_TYPE.toString(), APPLICATION_JSON.toString())
            .end(response.encode());
        }
      );
  }

  private Handler<RoutingContext> getSockJsHandler() {
    SockJSHandler sockJSHandler = SockJSHandler.create(vertx);
    BridgeOptions options = new BridgeOptions();
    options.addInboundPermitted(
      new PermittedOptions().setAddress("circuit-breaker"));
    options.addOutboundPermitted(
      new PermittedOptions().setAddress("circuit-breaker"));
    return sockJSHandler.bridge(options);
  }
}
