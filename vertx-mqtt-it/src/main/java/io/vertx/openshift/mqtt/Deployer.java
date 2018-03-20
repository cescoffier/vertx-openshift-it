package io.vertx.openshift.mqtt;

import io.vertx.core.AbstractVerticle;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * @author Martin Spisiak (mspisiak@redhat.com) on 20/03/18.
 */
public class Deployer extends AbstractVerticle {

  public void start() {
    vertx.deployVerticle(MqttBroker.class.getName());
    vertx.deployVerticle(MqttSubscriber.class.getName());

    Router router = Router.router(vertx);
    router.get("/").handler(this::healthcheck);

    vertx
      .createHttpServer()
      .requestHandler(router::accept)
      .listen(8080);
  }

  private void healthcheck(RoutingContext rc) {
    rc.response().end("Hello healthcheck!");
  }
}
