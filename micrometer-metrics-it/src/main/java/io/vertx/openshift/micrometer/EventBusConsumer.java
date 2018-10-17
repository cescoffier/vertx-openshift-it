package io.vertx.openshift.micrometer;

import io.vertx.core.AbstractVerticle;

/**
 * @author Martin Spisiak (mspisiak@redhat.com) on 03/10/2018.
 */
public class EventBusConsumer extends AbstractVerticle {
  @Override
  public void start() throws Exception {
    vertx.eventBus().<String>consumer("greeting", message -> {
      String greeting = message.body();
      System.out.println("Received: " + greeting);
      Greetings.get(vertx, greetingResult -> message.reply(greetingResult.result()));
    });
  }
}
