package io.vertx.openshift.embedded;

import io.vertx.core.Vertx;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class Main {

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    vertx.createHttpServer()
        .requestHandler(req -> req.response().end("Hello World!"))
        .listen(8080, ar -> {
          if (ar.failed()) {
            System.out.println("D'oh, can't start the HTTP server");
            ar.cause().printStackTrace();
          } else {
            System.out.println("HTTP server started on port " + ar.result().actualPort());
          }
        });
  }

}
