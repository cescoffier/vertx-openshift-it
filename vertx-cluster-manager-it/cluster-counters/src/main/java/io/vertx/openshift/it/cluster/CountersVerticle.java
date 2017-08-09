package io.vertx.openshift.it.cluster;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.core.shareddata.Counter;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * @author Thomas Segismont
 */
public class CountersVerticle extends AbstractVerticle {

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
    router.post().handler(BodyHandler.create());
    router.post("/counters/:name").handler(rc -> {
      String name = rc.pathParam("name");
      long value = Long.parseLong(rc.getBodyAsString());
      getCounter(name)
        .compose(c -> addAndGet(c, value))
        .setHandler(ar -> sendResponse(rc, ar));
    });
    router.get("/counters/:name").handler(rc -> {
      String name = rc.pathParam("name");
      getCounter(name)
        .compose(this::get)
        .setHandler(ar -> sendResponse(rc, ar));
    });
    router.get("/health").handler(rc -> {
      rc.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/plain").end("OK");
    });
    return router;
  }

  private Future<Counter> getCounter(String name) {
    Future<Counter> future = Future.future();
    vertx.sharedData().getCounter(name, future);
    return future;
  }

  private Future<Long> addAndGet(Counter counter, long value) {
    Future<Long> future = Future.future();
    counter.addAndGet(value, future);
    return future;
  }

  private Future<Long> get(Counter counter) {
    Future<Long> future = Future.future();
    counter.get(future);
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
