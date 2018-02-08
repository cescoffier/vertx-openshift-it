package io.vertx.openshift.it.cluster.ru;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.ext.cluster.infinispan.InfinispanClusterManager;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.healthchecks.Status;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.ResponseContentTypeHandler;
import org.infinispan.health.Health;
import org.infinispan.health.HealthStatus;
import org.infinispan.manager.DefaultCacheManager;

/**
 * @author Thomas Segismont
 */
public class AsyncMapVerticle extends AbstractVerticle {

  public static void main(String[] args) {
    Vertx.clusteredVertx(new VertxOptions(), ar -> {
      if (ar.succeeded()) {
        Vertx vertx = ar.result();
        vertx.deployVerticle(new AsyncMapVerticle());
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
    router.get("/health").handler(rc -> rc.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/plain").end("OK"));
    router.get("/ready").handler(HealthCheckHandler.createWithHealthChecks(createHealthChecks()));
    router.put("/stuff/*").handler(BodyHandler.create());
    router.route("/stuff/*").handler(ResponseContentTypeHandler.create());
    router.get("/stuff/:map/:key").produces("application/json").handler(this::handeGet);
    router.put("/stuff/:map/:key").consumes("application/json").handler(this::handePut);
    return router;
  }

  private void handeGet(RoutingContext rc) {
    getAsyncMap(rc).compose(map -> {
      String key = rc.pathParam("key");
      if (key == null) {
        return Future.failedFuture("Missing key path param");
      }
      Future<JsonObject> future = Future.future();
      map.get(key, future);
      return future;
    }).setHandler(ar -> {
      if (ar.succeeded()) {
        JsonObject result = ar.result();
        if (result == null) {
          rc.response().setStatusCode(404).end();
        } else {
          rc.response().end(Json.encodeToBuffer(result));
        }
      } else {
        rc.fail(ar.cause());
      }
    });
  }

  private void handePut(RoutingContext rc) {
    getAsyncMap(rc).compose(map -> {
      String key = rc.pathParam("key");
      if (key == null) {
        return Future.failedFuture("Missing key path param");
      }
      Future<Void> future = Future.future();
      map.put(key, rc.getBodyAsJson(), future);
      return future;
    }).setHandler(ar -> {
      if (ar.succeeded()) {
        rc.response().setStatusCode(201).end();
      } else {
        rc.fail(ar.cause());
      }
    });
  }

  private Future<AsyncMap<String, JsonObject>> getAsyncMap(RoutingContext rc) {
    String map = rc.pathParam("map");
    if (map == null) {
      return Future.failedFuture("Missing map path param");
    }
    Future<AsyncMap<String, JsonObject>> future = Future.future();
    vertx.sharedData().getAsyncMap(map, future);
    return future;
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
