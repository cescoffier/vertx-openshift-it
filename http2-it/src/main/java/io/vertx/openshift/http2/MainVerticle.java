package io.vertx.openshift.http2;

import io.vertx.core.AbstractVerticle;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class MainVerticle extends AbstractVerticle {

  @Override
  public void start() throws Exception {
    vertx.deployVerticle(HelloHttp2Verticle.class.getName());
    vertx.deployVerticle(HelloGrpcVerticle.class.getName());
    vertx.deployVerticle(EdgeVerticle.class.getName());
  }
}
