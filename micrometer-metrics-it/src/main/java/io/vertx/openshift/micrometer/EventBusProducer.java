package io.vertx.openshift.micrometer;

import io.vertx.core.AbstractVerticle;

/**
 * @author Martin Spisiak (mspisiak@redhat.com) on 03/10/2018.
 */
public class EventBusProducer extends AbstractVerticle {
  @Override
  public void start() throws Exception {
    vertx.setPeriodic(1000, x -> Greetings.get(vertx, greetingResult -> vertx.eventBus().send("greeting", greetingResult.result())));
  }
}
