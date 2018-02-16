package io.vertx.openshift.it.cluster.ru;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.cluster.infinispan.InfinispanClusterManager;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.healthchecks.Status;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.ResponseContentTypeHandler;
import io.vertx.ext.web.handler.TimeoutHandler;
import org.infinispan.health.Health;
import org.infinispan.health.HealthStatus;
import org.infinispan.manager.EmbeddedCacheManager;

import java.util.concurrent.atomic.AtomicLong;

import static java.util.concurrent.TimeUnit.*;

/**
 * @author Thomas Segismont
 */
public class EventBusSenderVerticle extends AbstractVerticle {

  private static final long SEND_TIMEOUT = MILLISECONDS.convert(1, SECONDS);
  private static final long API_TIMEOUT = MILLISECONDS.convert(30, SECONDS);

  private final AtomicLong inFlight = new AtomicLong();
  private final AtomicLong sent = new AtomicLong();
  private final AtomicLong failures = new AtomicLong();
  private long timerId;

  public static void main(String[] args) {
    Vertx.clusteredVertx(new VertxOptions(), ar -> {
      if (ar.succeeded()) {
        Vertx vertx = ar.result();
        vertx.deployVerticle(new EventBusSenderVerticle());
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
    router.route("/api/*").handler(ResponseContentTypeHandler.create());
    router.route("/api/*").handler(TimeoutHandler.create(API_TIMEOUT));
    router.get("/api/start_sending").produces("application/json").handler(rc -> {
      timerId = vertx.setPeriodic(10, l -> {
        sent.incrementAndGet();
        inFlight.incrementAndGet();
        sendRequest(2);
      });
      rc.response().end();
    });
    router.get("/api/stop_sending").produces("application/json").handler(rc -> {
      vertx.cancelTimer(timerId);
      waitForAllResponses(ar -> {
        JsonObject result = new JsonObject()
          .put("sent", sent.get())
          .put("failures", failures.get());
        rc.response().end(result.toBuffer());
      });
    });
    return router;
  }

  private void waitForAllResponses(Handler<AsyncResult<Void>> handler) {
    if (inFlight.get() > 0) {
      vertx.setTimer(10, l -> waitForAllResponses(handler));
    } else {
      handler.handle(Future.future());
    }
  }

  private void sendRequest(int attempts) {
    if (attempts < 1) {
      failures.incrementAndGet();
      inFlight.decrementAndGet();
    }
    vertx.eventBus().send("foo", "bar", new DeliveryOptions().setSendTimeout(SEND_TIMEOUT), ar -> {
      if (ar.failed()) {
        sendRequest(attempts - 1);
      } else {
        inFlight.decrementAndGet();
      }
    });
  }

  private HealthChecks createHealthChecks() {
    return HealthChecks.create(vertx)
      .register("ispn-cluster-status", future -> {
        VertxInternal vertxInternal = (VertxInternal) vertx;
        InfinispanClusterManager clusterManager = (InfinispanClusterManager) vertxInternal.getClusterManager();
        EmbeddedCacheManager cacheManager = (EmbeddedCacheManager) clusterManager.getCacheContainer();
        Health health = cacheManager.getHealth();
        HealthStatus healthStatus = health.getClusterHealth().getHealthStatus();
        Status status = new Status()
          .setOk(healthStatus == HealthStatus.HEALTHY)
          .setData(JsonObject.mapFrom(health));
        future.complete(status);
      });
  }
}
