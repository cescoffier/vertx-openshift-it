package io.vertx.openshift.it.cluster;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.core.shareddata.Lock;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * @author Thomas Segismont
 */
public class LocksVerticle extends AbstractVerticle {

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
    router.get("/locks/:name").handler(rc -> {
      String name = rc.pathParam("name");
      getLock(name)
        .compose(l -> {
          Future<Void> future = Future.future();
          vertx.setTimer(2000, tid -> {
            l.release();
            future.complete();
          });
          return future;
        }).setHandler(ar -> sendResponse(rc, ar));
    });
    router.get("/health").handler(rc -> {
      rc.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/plain").end("OK");
    });
    return router;
  }

  private Future<Lock> getLock(String name) {
    Future<Lock> future = Future.future();
    vertx.sharedData().getLockWithTimeout(name, 1000, future);
    return future;
  }

  private <T> void sendResponse(RoutingContext rc, AsyncResult<T> ar) {
    if (ar.succeeded()) {
      rc.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/plain").end(String.valueOf(ar.result()));
    } else {
      rc.fail(ar.cause());
    }
  }
}
