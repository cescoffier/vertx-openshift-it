package io.vertx.openshift.healthcheck;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.healthchecks.Status;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 * @author Slavomir Krupa (slavomir.krupa@gmail.com)
 */
public class HealthCheckHttpVerticle extends AbstractVerticle {

  public static final String RESET = "/checks/reset";
  public static final String CHECKS_OK = "checks/ok";
  public static final String CHECKS_KO = "checks/ko";
  public static final String REAL_CHECKS_TIMEOUT = "checks/timeout";
  public static final String REAL_CHECKS_THROW_EXCEPTION = "checks/throw_exception";
  public static final String CHECKS_CONTENT_OK = "checks/content/ok";
  public static final String CHECKS_CONTENT_KO = "checks/content/ko";
  public static final String EVENTBUS_CHECKS = "eventbus_checks";

  @Override
  public void start(Future<Void> future) throws Exception {
    Router router = Router.router(vertx);
    final EventBus eventBus = vertx.eventBus();
    HealthChecks testHealthChecks = HealthChecks.create(vertx);
    router.get("/health").handler(HealthCheckHandler.createWithHealthChecks(testHealthChecks));

    router.get(RESET).handler(rc -> {
      testHealthChecks.unregister("checks");
      respondOk(rc);
    });

    router.get("/" + CHECKS_OK).handler(rc -> {
      testHealthChecks.register(CHECKS_OK, fut -> fut.complete(Status.OK()));
      respondOk(rc);
    });

    router.get("/" + CHECKS_KO).handler(rc -> {
      testHealthChecks.register(CHECKS_KO, fut -> fut.complete(Status.KO()));
      respondOk(rc);
    });

    router.get("/" + CHECKS_CONTENT_OK).handler(rc -> {
      testHealthChecks.register(CHECKS_CONTENT_OK, fut -> fut.complete(Status.KO()));
      respondOk(rc);
    });

    router.get("/" + CHECKS_CONTENT_KO).handler(rc -> {
      testHealthChecks.register(CHECKS_CONTENT_KO, fut -> fut.complete(Status.KO()));
      respondOk(rc);
    });

    router.get("/" + EVENTBUS_CHECKS).handler(rc -> {
      testHealthChecks.invoke(json -> {
        rc.response().end(json.encodePrettily());
      });
    });

    router.get("/" + REAL_CHECKS_THROW_EXCEPTION).handler(rc -> {
        eventBus.publish(REAL_CHECKS_THROW_EXCEPTION, null);
        respondOk(rc);
      }
    );
    router.get("/" + REAL_CHECKS_TIMEOUT).handler(rc -> {
        eventBus.publish(REAL_CHECKS_TIMEOUT, null);
        respondOk(rc);
      }
    );
    router.get("/killOne").handler(rc -> {
        eventBus.send("kill", "kill");
        respondOk(rc);
      }
    );
    router.get("/killAll").handler(rc -> {
        eventBus.publish("kill", "kill");
        respondOk(rc);
      }
    );

    vertx.createHttpServer()
      .requestHandler(router::accept)
      .listen(8080, handler -> {
        if (handler.succeeded()) {
          future.complete();
        } else {
          future.fail(handler.cause());
        }
      });
  }

  private void respondOk(RoutingContext rc) {
    rc.response().end("OK");
  }
}
