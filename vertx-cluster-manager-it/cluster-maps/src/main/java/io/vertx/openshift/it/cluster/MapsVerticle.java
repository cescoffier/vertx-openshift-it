package io.vertx.openshift.it.cluster;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * @author Thomas Segismont
 */
public class MapsVerticle extends AbstractVerticle {

  public static void main(String[] args) {
    Vertx.clusteredVertx(new VertxOptions(), ar -> {
      if (ar.succeeded()) {
        Vertx vertx = ar.result();
        vertx.deployVerticle(new MapsVerticle());
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
    router.put().handler(BodyHandler.create());
    router.put("/maps/:name/:key").handler(rc -> {
      String name = rc.pathParam("name");
      String key = rc.pathParam("key");
      getAsyncMap(name)
        .compose(asyncMap -> put(asyncMap, key, rc.getBodyAsString()))
        .setHandler(ar -> sendResponse(rc, ar));
    });
    router.get("/maps/:name/:key").handler(rc -> {
      String name = rc.pathParam("name");
      String key = rc.pathParam("key");
      getAsyncMap(name)
        .compose(asyncMap -> get(asyncMap, key))
        .setHandler(ar -> sendResponse(rc, ar));
    });
    router.get("/health").handler(rc -> {
      rc.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/plain").end("OK");
    });
    return router;
  }

  private Future<AsyncMap<String, String>> getAsyncMap(String name) {
    Future<AsyncMap<String, String>> future = Future.future();
    vertx.sharedData().getClusterWideMap(name, future);
    return future;
  }

  private Future<Void> put(AsyncMap<String, String> asyncMap, String key, String value) {
    Future<Void> future = Future.future();
    asyncMap.put(key, value, future);
    return future;
  }

  private Future<String> get(AsyncMap<String, String> asyncMap, String key) {
    Future<String> future = Future.future();
    asyncMap.get(key, future);
    return future;
  }

  private <T> void sendResponse(RoutingContext rc, AsyncResult<T> ar) {
    if (ar.succeeded()) {
      String chunk = String.valueOf(ar.result());
      rc.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/plain").end(chunk);
    } else {
      rc.fail(ar.cause());
    }
  }
}
