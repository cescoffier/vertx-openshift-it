package io.vertx.openshift.it.cluster.ru;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.cluster.infinispan.InfinispanClusterManager;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.healthchecks.Status;
import io.vertx.ext.web.Router;
import org.infinispan.health.Health;
import org.infinispan.health.HealthStatus;
import org.infinispan.manager.DefaultCacheManager;

/**
 * @author Thomas Segismont
 */
public class EventBusReceiverVerticle extends AbstractVerticle {

  public static void main(String[] args) {
    Vertx.clusteredVertx(new VertxOptions(), ar -> {
      if (ar.succeeded()) {
        Vertx vertx = ar.result();
        vertx.deployVerticle(new EventBusReceiverVerticle());
      } else {
        ar.cause().printStackTrace();
      }
    });
  }

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    startHttpServer()
      .compose(v -> {
        Future<Void> future = Future.future();
        vertx.eventBus().consumer("foo", msg -> msg.reply(msg.body())).completionHandler(future);
        return future;
      }).setHandler(startFuture);
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
    router.get("/health").handler(rc -> rc.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/plain").end("OK"));
    router.get("/ready").handler(HealthCheckHandler.createWithHealthChecks(createHealthChecks()));
    return router;
  }

  private HealthChecks createHealthChecks() {
    return HealthChecks.create(vertx)
      .register("ispn-cluster-status", future -> {
        VertxInternal vertxInternal = (VertxInternal) vertx;
        InfinispanClusterManager clusterManager = (InfinispanClusterManager) vertxInternal.getClusterManager();
        DefaultCacheManager cacheManager = (DefaultCacheManager) clusterManager.getCacheContainer();
        Health health = cacheManager.getHealth();
        HealthStatus healthStatus = health.getClusterHealth().getHealthStatus();
        Status status = new Status()
          .setOk(healthStatus == HealthStatus.HEALTHY)
          .setData(JsonObject.mapFrom(health));
        future.complete(status);
      });
  }
}
