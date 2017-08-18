package io.vertx.openshift.it;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;

import java.util.ArrayList;
import java.util.List;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

/**
 * @author Martin Spišiak (mspisiak@redhat.com) on 25/05/17.
 */

public class HttpConfigDeployment extends AbstractVerticle {

  private EventBus eventBus;
  private List<Object> eventbusContent = new ArrayList<>();

  @Override
  public void start(Future<Void> future) {
    // Create a router object.
    Router router = Router.router(vertx);
    eventBus = vertx.eventBus();

    router.get("/conf").handler(this::config);
    router.get("/*").handler(StaticHandler.create());

    // Create the HTTP server and pass the "accept" method to the request handler.
    vertx
      .createHttpServer()
      .requestHandler(router::accept)
      .listen(
        // Retrieve the port from the configuration, default to 8080.
        config().getInteger("http.port", 8080), ar -> {
          if (ar.succeeded()) {
            eventBus.consumer("event-bus-config", msg -> {
              eventbusContent.add(msg.body());
            }).completionHandler(res -> {
              if (res.succeeded()) {
                System.out.println("Registration succeeded: event-bus-config");
              }
              else {
                System.out.println("Registration failed: " + res.cause());
              }
            });
            System.out.println("Server starter on port " + ar.result().actualPort());
          } else {
            System.out.println("Unable to start server: " + ar.cause().getMessage());
          }
          future.handle(ar.mapEmpty());
        });

  }

  private void config(RoutingContext rc) {
    String httpTemplate = "Congratulations, you have just served a configuration over HTTP !";
    JsonObject jsonObject = new JsonObject()
      .put("httpConfigContent", httpTemplate)
      .put("eventBusMessageContent", eventbusContent);

    rc.response().putHeader(CONTENT_TYPE, "application/json; charset=utf-8")
      .end(jsonObject.encodePrettily());
  }
}
