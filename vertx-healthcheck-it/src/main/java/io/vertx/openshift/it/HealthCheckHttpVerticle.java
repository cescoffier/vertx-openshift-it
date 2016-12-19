package io.vertx.openshift.it;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.ext.web.Router;
import me.escoffier.vertx.healthchecks.HealthCheckHandler;
import me.escoffier.vertx.healthchecks.HealthChecks;
import me.escoffier.vertx.healthchecks.Status;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class HealthCheckHttpVerticle extends AbstractVerticle {

  @Override
  public void start(Future<Void> future) throws Exception {
    Router router = Router.router(vertx);
    HealthChecks healthChecks = HealthChecks.create(vertx);

    router.get("/health").handler(HealthCheckHandler.create(healthChecks));

    router.get("/").handler(rc -> rc.response().end("hello"));

    router.get("/checks/reset").handler(rc -> {
      healthChecks.unregister("checks");
      rc.response().end("OK");
    });

    router.get("/checks/ok").handler(rc -> {
      healthChecks.register("checks/ok", fut -> fut.complete(Status.OK()));
      rc.response().end("OK");
    });

    router.get("/checks/ko").handler(rc -> {
      healthChecks.register("checks/ok", fut -> fut.complete(Status.KO()));
      rc.response().end("OK");
    });

    router.get("/checks").handler(rc -> {
      healthChecks.invoke(json -> {
        rc.response().end(json.encodePrettily());
      });

    });

    vertx.createHttpServer()
      .requestHandler(router::accept)
      .listen(8080, r -> {
        if (r.succeeded()) {
          future.complete();
        } else {
          future.fail(r.cause());
        }
      });
  }
}
