package io.vertx.openshift.it.cluster.dtfp;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;

/**
 * @author Thomas Segismont
 */
public class ReceiverVerticle extends AbstractVerticle {

  public static void main(String[] args) {
    Vertx.clusteredVertx(new VertxOptions(), ar -> {
      if (ar.succeeded()) {
        Vertx vertx = ar.result();
        vertx.deployVerticle(new ReceiverVerticle());
      } else {
        ar.cause().printStackTrace();
      }
    });
  }

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    setupConsumer()
      .compose(v -> startHttpServer())
      .<Void>mapEmpty()
      .setHandler(startFuture);
  }

  private Future<Void> setupConsumer() {
    Future<Void> future = Future.future();
    vertx.eventBus().consumer("foobar", msg -> {
      msg.reply("pong");
    }).completionHandler(future);
    return future;
  }

  private Future<HttpServer> startHttpServer() {
    Future<HttpServer> future = Future.future();
    Router router = setupRouter();
    vertx.createHttpServer()
      .requestHandler(router::accept)
      .listen(8080, future);
    return future;
  }

  private Router setupRouter() {
    Router router = Router.router(vertx);
    router.get("/health").handler(rc -> {
      rc.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/plain").end("OK");
    });
    return router;
  }
}
