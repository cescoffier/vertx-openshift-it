package io.vertx.openshift.it;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class HttpVerticle extends AbstractVerticle {

  @Override
  public void start() throws Exception {
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());
    router.get().handler(rc -> {
      String message = rc.request().getParam("message");
      HttpServerResponse response = rc.response();
      response.setStatusCode(200);
      response.putHeader("content-type", "application/json;charset=UTF-8");
      JsonObject entries = new JsonObject().put("timestamp", System.currentTimeMillis()).put("message", message)
          .put("hostname", System.getenv("HOSTNAME"));
      response.end(entries.encodePrettily());
    });

    vertx.createHttpServer().requestHandler(router::accept).listen(8080, ar -> {
      if (ar.succeeded()) {
        System.out.println("Server exposed on port 8080");
      } else {
        System.out.println("Unable to start the HTTP server");
        ar.cause().printStackTrace();
      }
    });
  }
}
