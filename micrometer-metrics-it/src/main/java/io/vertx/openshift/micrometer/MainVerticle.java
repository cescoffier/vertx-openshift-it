package io.vertx.openshift.micrometer;

import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.vertx.core.AbstractVerticle;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.micrometer.backends.BackendRegistries;

/**
 * @author Martin Spisiak (mspisiak@redhat.com) on 03/10/2018.
 */
public class MainVerticle extends AbstractVerticle {

  @Override
  public void start() {
    Router router = Router.router(vertx);
    router.get("/").handler(ctx -> Greetings.get(vertx, greetingResult -> ctx.response().end(greetingResult.result())));
    router.get("/hello").handler(this::sayHello);
    router.route("/metrics").handler(rc -> {
      PrometheusMeterRegistry prometheusRegistry = (PrometheusMeterRegistry) BackendRegistries.getDefaultNow();
      if (prometheusRegistry != null) {
        String response = prometheusRegistry.scrape();
        rc.response().end(response);
      } else {
        System.out.println("Something has failed here.");
        rc.fail(500);
      }
    });
    vertx.createHttpServer().requestHandler(router::accept).listen(8080);

    vertx.deployVerticle(new EventBusConsumer());
    vertx.deployVerticle(new EventBusProducer());
  }

  private void sayHello(RoutingContext routingContext) {
    routingContext.response().end("Hello " + routingContext.request().version());
  }
}
