package io.vertx.openshift.micrometer;

import io.vertx.core.Launcher;
import io.vertx.core.VertxOptions;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.VertxPrometheusOptions;

/**
 * @author Martin Spisiak (mspisiak@redhat.com) on 2018-12-11.
 */
public class MicrometerMetricsLauncher extends Launcher {

  public static void main(String[] args) {
    new MicrometerMetricsLauncher().dispatch(args);
  }

  @Override
  public void beforeStartingVertx(VertxOptions options) {
    options.setMetricsOptions(new MicrometerMetricsOptions()
      .setPrometheusOptions(new VertxPrometheusOptions().setEnabled(true))
      .setEnabled(true));
  }

//  Uncomment the following to get histograms
//  @Override
//  public void afterStartingVertx(Vertx vertx) {
//    PrometheusMeterRegistry registry = (PrometheusMeterRegistry) BackendRegistries.getDefaultNow();
//    registry.config().meterFilter(
//      new MeterFilter() {
//        @Override
//        public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
//          return DistributionStatisticConfig.builder()
//            .percentilesHistogram(true)
//            .build()
//            .merge(config);
//        }
//      });
//  }
}
