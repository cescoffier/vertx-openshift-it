package io.vertx.openshift.config;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

/**
 * @author Martin Spišiak (mspisiak@redhat.com) on 25/05/17.
 */

public class EventBusPublish extends AbstractVerticle {
  private static final String eventBusTemplate = "Great, you have just served a configuration over the event bus !";
  private EventBus eventBus;

  @Override
  public void start(Future<Void> future) {
    // Create a router object.
    Router router = Router.router(vertx);
    eventBus = vertx.eventBus();

    router.get("/").handler(rc -> {
      rc.response().putHeader(CONTENT_TYPE, "application/json; charset=utf-8")
        .end(new JsonObject().put("greeting", "Hello from event bus service !").encodePrettily());
    });

    router.get("/eventbus/").handler(this::publishMsg);

    vertx
      .createHttpServer()
      .requestHandler(router::accept)
      .listen(
        config().getInteger("http.port", 8080), ar -> {
          if (ar.succeeded()) {
            eventBus.publish("event-bus-config", new JsonObject().put("event-bus", eventBusTemplate));
            System.out.println("Server starter on port " + ar.result().actualPort());
          } else {
            System.out.println("Unable to start server: " + ar.cause().getMessage());
          }
          future.handle(ar.mapEmpty());
        });

  }

  private void publishMsg(RoutingContext rc) {
    String msg = rc.request().getParam("msg");
    if (msg == null) {
      msg = "the event bus";
    }
    String reply = "Hello configuration from " + msg + " !";

    JsonObject message = new JsonObject().put("eventBus", reply);
    eventBus.publish("event-bus-config", message);

    rc.response().putHeader(CONTENT_TYPE, "application/json; charset=utf-8")
      .end(new JsonObject().put("info", "You have just published a message on the event bus !")
                           .mergeIn(message).encodePrettily());
  }
}
