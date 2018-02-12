package io.vertx.openshift.sockjs;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.bridge.BridgeEventType;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.ext.web.handler.sockjs.SockJSHandlerOptions;
import io.vertx.ext.web.handler.sockjs.Transport;

import java.util.Arrays;

/**
 * @author Slavomir Krupa
 */
public class SockVerticle extends AbstractVerticle {

  private static final String TO_CLIENT = "to.client";
  private static final String TO_SERVER = "to.server";
  private static final BridgeOptions PERMITTED_OPTIONS = new BridgeOptions()
    .addOutboundPermitted(new PermittedOptions().setAddress(TO_CLIENT))
    .addInboundPermitted(new PermittedOptions().setAddressRegex(TO_SERVER));
  private static final String STATUS = "status";

  private Router router;

  private JsonObject status = new JsonObject();

  @Override
  public void start() throws Exception {

    setUpRouter();
    vertx.createHttpServer().requestHandler(router::accept).listen(8080);

    // Publish a message to the address "security-check" every second
    vertx.setPeriodic(1000, t -> vertx.eventBus().publish("security-check", "security-check-error"));
    // Publish a message to the address "to-client" every second
    vertx.setPeriodic(5000, (time) -> vertx.eventBus().publish(TO_CLIENT, status.encodePrettily()));
    vertx.eventBus().consumer(TO_SERVER, (content) -> status.put(content.body().toString() + "-EB", true));
//    vertx.eventBus().localConsumer(TO_SERVER, (m) -> {
//      status.put("Error-" + System.currentTimeMillis(), "ERROR " + TO_SERVER + " address used on local consumer");
//    });
  }

  private void setUpRouter() {
    router = Router.router(vertx);

    for (Transport usedTransport : Transport.values()) {
      setupSockJSEventBusHandler(usedTransport);
      setupSockJSHandler(usedTransport);
    }
    router.route("/" + STATUS).handler(rc -> rc.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json").end(status.encodePrettily()));
    // Serve the static resources
    router.route().handler(StaticHandler.create());
  }

  private void setupSockJSHandler(Transport usedTransport) {

  }

  private void setupSockJSEventBusHandler(Transport usedTransport) {
    status.put(usedTransport.toString() + "-EB", false);
    status.put(usedTransport.toString() + "-sock", false);
    SockJSHandlerOptions handlerOptions = new SockJSHandlerOptions();
    Arrays.stream(Transport.values())
      .filter(t -> usedTransport != t)
      .map(Enum::name)
      .map(handlerOptions::addDisabledTransport);
    router.route("/eventbus/" + usedTransport.name() + "/*").handler(SockJSHandler.create(vertx, handlerOptions).bridge(PERMITTED_OPTIONS, event -> {
      if (event.type() == BridgeEventType.SOCKET_CREATED) {
        System.out.println(usedTransport.name() + " socket was created");
      }

      if (event.type() == BridgeEventType.SEND) {
        vertx.eventBus().publish(TO_CLIENT, new JsonObject().put("content", event.getRawMessage()).encode());
      }
      event.complete(true);
      if (event.type() == BridgeEventType.SOCKET_CLOSED) {
        System.out.println(usedTransport.name() + " socket was closed");
      }
    }));
    router.route("/sock/" + usedTransport.name() + "/*").handler(SockJSHandler.create(vertx, handlerOptions).socketHandler(socket -> {
      socket.handler(content -> {
        status.put(content.toString() + "-sock", true);
        socket.write(content);
      });
    }));
  }

}
