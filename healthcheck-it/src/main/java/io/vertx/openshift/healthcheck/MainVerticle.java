package io.vertx.openshift.healthcheck;

import java.util.Arrays;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;

/**
 * @author Slavomír Krupa (slavomir.krupa@gmail.com)
 */
public class MainVerticle extends AbstractVerticle {
  private String standardDeploymentId;
  private String healthCheckDeploymentID;

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    Future<String> standardServer = Future.future();
    Future<String> healthCheckServer = Future.future();
    vertx.deployVerticle(RealHealthCheckHttpVerticle.class.getName(), r -> {
      if (r.succeeded()) {
        standardServer.complete();
        standardDeploymentId = r.result();
        System.out.println("RealHealthCheck started with deployment id = " + r.result());
      } else {
        standardServer.fail(r.cause());
      }
    });
    vertx.deployVerticle(HealthCheckHttpVerticle.class.getName(), r -> {
      if (r.succeeded()) {
        healthCheckServer.complete();
        healthCheckDeploymentID = r.result();
        System.out.println("Server started with deployment id = " + r.result());
      } else {
        healthCheckServer.fail(r.cause());
      }
    });
    CompositeFuture.all(Arrays.asList(standardServer, healthCheckServer)).setHandler(
      r -> {
        if (r.succeeded()) {
          startFuture.complete();
        } else {
          startFuture.fail(r.cause());
        }
      }
    );
  }

  @Override
  public void stop(Future<Void> stopFuture) throws Exception {
    Future<Void> standardServer = Future.future();
    Future<Void> healthCheckServer = Future.future();
    vertx.undeploy(healthCheckDeploymentID, standardServer.completer());
    vertx.undeploy(standardDeploymentId, healthCheckServer.completer());
    CompositeFuture.all(Arrays.asList(standardServer, healthCheckServer)).setHandler(
      r -> {
        if (r.succeeded()) {
          stopFuture.complete();
        } else {
          stopFuture.fail(r.cause());
        }
      }
    );
  }
}
