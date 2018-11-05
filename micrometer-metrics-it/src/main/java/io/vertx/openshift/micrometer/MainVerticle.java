package io.vertx.openshift.micrometer;

import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.VertxPrometheusOptions;
import io.vertx.micrometer.backends.BackendRegistries;

/**
 * @author Martin Spisiak (mspisiak@redhat.com) on 03/10/2018.
 */
public class MainVerticle extends AbstractVerticle {

  @Override
  public void start() {
    MicrometerMetricsOptions options = new MicrometerMetricsOptions()
      .setPrometheusOptions(new VertxPrometheusOptions().setEnabled(true))
      .setEnabled(true);
    Vertx vertx = Vertx.vertx(new VertxOptions().setMetricsOptions(options));

// Uncomment the following to get histograms
//    PrometheusMeterRegistry registry = (PrometheusMeterRegistry) BackendRegistries.getDefaultNow();
//    registry.config().meterFilter(
//        new MeterFilter() {
//          @Override
//          public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
//            return DistributionStatisticConfig.builder()
//                .percentilesHistogram(true)
//                .build()
//                .merge(config);
//          }
//        });

    Router router = Router.router(vertx);
    router.get("/").handler(ctx -> Greetings.get(vertx, greetingResult -> ctx.response().end(greetingResult.result())));
    router.get("/hello").handler(this::sayHello);
    router.route("/metrics").handler(rc -> {
      PrometheusMeterRegistry prometheusRegistry = (PrometheusMeterRegistry) BackendRegistries.getDefaultNow();
      if (prometheusRegistry != null) {
        String response = prometheusRegistry.scrape();
        rc.response().end(response);
      } else {
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
