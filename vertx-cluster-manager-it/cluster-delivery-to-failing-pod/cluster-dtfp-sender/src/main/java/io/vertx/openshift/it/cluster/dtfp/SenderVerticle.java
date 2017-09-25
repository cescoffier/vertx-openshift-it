package io.vertx.openshift.it.cluster.dtfp;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;

/**
 * @author Thomas Segismont
 */
public class SenderVerticle extends AbstractVerticle {

  public static void main(String[] args) {
    Vertx.clusteredVertx(new VertxOptions(), ar -> {
      if (ar.succeeded()) {
        Vertx vertx = ar.result();
        vertx.deployVerticle(new SenderVerticle());
      } else {
        ar.cause().printStackTrace();
      }
    });
  }

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    startHttpServer()
      .<Void>mapEmpty()
      .setHandler(startFuture);
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
    router.get("/deliver-to-functional-pod").handler(rc -> {
      vertx.eventBus().<String>send("foobar", "ping", ar -> {
        if (ar.succeeded()) {
          rc.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/plain").end(ar.result().body());
        } else {
          ar.cause().printStackTrace();
          rc.fail(500);
        }
      });
    });
    router.get("/deliver-to-failing-pod").handler(rc -> {
      vertx.eventBus().<String>send("foobar", "ping", ar -> {
        if (ar.failed() && ar.cause() instanceof ReplyException) {
          ReplyException replyException = (ReplyException) ar.cause();
          if (replyException.failureType() == ReplyFailure.NO_HANDLERS) {
            rc.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/plain").end("OK");
            return;
          }
        }
        rc.fail(500);
      });
    });
    router.get("/health").handler(rc -> {
      rc.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/plain").end("OK");
    });
    return router;
  }
}
