package io.vertx.openshift.http2;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.PemKeyCertOptions;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class MainVerticle extends AbstractVerticle {

  @Override
  public void start() throws Exception {
    vertx.deployVerticle(AlohaH2CVerticle.class.getName());
    vertx.deployVerticle(HelloHttp2Verticle.class.getName());
  }
}
