package io.vertx.openshift.micrometer;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

import java.util.Random;

/**
 * @author Martin Spisiak (mspisiak@redhat.com) on 03/10/2018.
 */
public class Greetings {
  private static final String[] GREETINGS = {
    "Hello world!",
    "Bonjour monde!",
    "Hallo Welt!",
    "Hola Mundo!"
  };
  private static final Random RND = new Random();

  private Greetings() {
  }

  static void get(Vertx vertx, Handler<AsyncResult<String>> responseHandler) {
    vertx.executeBlocking(fut -> {
      // Simulate worker pool processing time between 200ms and 2s
      int processingTime = RND.nextInt(1800) + 200;
      try {
        Thread.sleep(processingTime);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      fut.complete(GREETINGS[RND.nextInt(4)]);
    }, responseHandler);
  }
}
