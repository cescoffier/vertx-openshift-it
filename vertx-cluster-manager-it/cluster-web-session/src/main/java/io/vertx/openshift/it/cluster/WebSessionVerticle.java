package io.vertx.openshift.it.cluster;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CookieHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.ClusteredSessionStore;
import io.vertx.ext.web.sstore.SessionStore;

/**
 * @author Thomas Segismont
 */
public class WebSessionVerticle extends AbstractVerticle {

  public static void main(String[] args) {
    Vertx.clusteredVertx(new VertxOptions(), ar -> {
      if (ar.succeeded()) {
        Vertx vertx = ar.result();
        vertx.deployVerticle(new WebSessionVerticle());
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
    router.route().handler(CookieHandler.create());
    SessionStore store = ClusteredSessionStore.create(vertx);
    SessionHandler sessionHandler = SessionHandler.create(store);
    router.route().handler(sessionHandler);
    router.put("/web-session/:key").handler(rc -> {
      String key = rc.pathParam("key");
      String value = rc.getBodyAsString();
      rc.session().put(key, value);
      sendResponse(rc, Future.succeededFuture("OK"));
    });
    router.get("/web-session/:key").handler(rc -> {
      String key = rc.pathParam("key");
      String value = rc.session().get(key);
      sendResponse(rc, Future.succeededFuture(value));
    });
    router.get("/health").handler(rc -> {
      rc.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/plain").end("OK");
    });
    return router;
  }

  private <T> void sendResponse(RoutingContext rc, AsyncResult<T> ar) {
    if (ar.succeeded()) {
      rc.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/plain").end(String.valueOf(ar.result()));
    } else {
      rc.fail(ar.cause());
    }
  }
}
