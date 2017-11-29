package io.vertx.openshift.cb;

import static io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_JSON;
import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;


public class NameServiceVerticle extends AbstractVerticle {

  public static final String NAME = "World";
  /**
   * Current state, possible value: "fail", "ok".
   */
  private String state = "ok";

  @Override
  public void start() throws Exception {

    Router router = Router.router(vertx);

    router.route().handler(BodyHandler.create());
    router.get("/health").handler(rc -> rc.response().end("OK"));
    router.route().handler(CorsHandler.create("*").allowedMethod(HttpMethod.GET).allowedMethod(HttpMethod.PUT));
    router.get("/api/state").handler(rc ->
      rc.response()
        .putHeader(CONTENT_TYPE, APPLICATION_JSON)
        .end(new JsonObject().put("state", state).encodePrettily())
    );

    router.put("/api/state").handler(rc -> {
      JsonObject json = rc.getBodyAsJson();
      state = json.getString("state");
      rc.response()
        .putHeader(CONTENT_TYPE, APPLICATION_JSON)
        .end(new JsonObject().put("state", state).encodePrettily());
    });

    router.get("/api/name").handler(rc -> {
      switch (state) {
        case "ok":
          rc.response()
            .putHeader(CONTENT_TYPE, APPLICATION_JSON)
            .end(new JsonObject().put("name", NAME).encode());
          break;
        default:
          rc.fail(new Exception("Name Service Down"));
          break;
      }
    });
    vertx.createHttpServer()
      .requestHandler(router::accept)
      .listen(8080);
  }
}
