package io.vertx.openshift.it;

import static io.vertx.openshift.it.HealthCheckHttpVerticle.REAL_CHECKS_THROW_EXCEPTION;
import static io.vertx.openshift.it.HealthCheckHttpVerticle.REAL_CHECKS_TIMEOUT;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.web.Router;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 * @author Slavomir Krupa (slavomir.krupa@gmail.com)
 */
public class RealHealthCheckHttpVerticle extends AbstractVerticle {

  public static final String KILL = "kill";

  @Override
  public void start(Future<Void> future) throws Exception {
    Router internalRouter = Router.router(vertx);

    HealthChecks globalHealthChecks = HealthChecks.create(vertx);

    final HealthCheckHandler globalHealthChecksHandler = HealthCheckHandler.createWithHealthChecks(globalHealthChecks);
    internalRouter.get("/isAlive").handler(globalHealthChecksHandler);
    internalRouter.get("/start").handler(globalHealthChecksHandler);

    vertx.eventBus().consumer(KILL,
      m -> globalHealthChecks.register(
        m.body().toString(),
        f -> f.fail(m.body().toString())
      )
    );
    vertx.eventBus().consumer(REAL_CHECKS_TIMEOUT,
      m -> globalHealthChecks.register(REAL_CHECKS_TIMEOUT,
        f -> {
        })
    );

    vertx.eventBus().consumer(REAL_CHECKS_THROW_EXCEPTION,
      m -> globalHealthChecks.register(REAL_CHECKS_THROW_EXCEPTION,
        f -> {
          throw new IllegalArgumentException();
        })
    );

    vertx.createHttpServer()
      .requestHandler(internalRouter::accept)
      .listen(8088, handler -> {
        if (handler.succeeded()) {
          future.complete();
        } else {
          future.fail(handler.cause());
        }
      });
  }
}
