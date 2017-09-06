package io.vertx.openshift.it.cluster;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.TimeoutHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Thomas Segismont
 */
public class EventBusVerticle extends AbstractVerticle {

  public static void main(String[] args) {
    Vertx.clusteredVertx(new VertxOptions(), ar -> {
      if (ar.succeeded()) {
        Vertx vertx = ar.result();
        vertx.deployVerticle(new EventBusVerticle());
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
    List<Future> futures = new ArrayList<>();
    futures.add(LocalPeerToPeer.createLocalPeerToPeer(vertx));
    futures.add(DistributedPeerToPeer.createDistributedPeerToPeer(vertx));
    futures.add(LocalPublish.createLocalPublish(vertx));
    futures.add(DistributedPublish.createDistributedPublish(vertx));
    CompositeFuture allHandlers = CompositeFuture.all(futures);
    return allHandlers.compose(cf -> {
      Future<HttpServer> future = Future.future();
      Router router = setupRouter(cf.list());
      vertx.createHttpServer()
        .requestHandler(router::accept)
        .listen(8080, future);
      return future;
    });
  }

  private Router setupRouter(List<TestHandler> testHandlers) {
    Router router = Router.router(vertx);
    router.route().handler(TimeoutHandler.create(10 * 1000));
    testHandlers.forEach(testHandler -> router.get("/event-bus" + testHandler.path()).handler(testHandler));
    router.get("/health").handler(rc -> {
      rc.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/plain").end("OK");
    });
    return router;
  }

}
