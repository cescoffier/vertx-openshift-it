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

    HttpServer server =
      vertx.createHttpServer(new HttpServerOptions().
        setUseAlpn(true).
        setSsl(true).
        setPemKeyCertOptions(
          new PemKeyCertOptions().setKeyPath("server-key.pem").setCertPath("server-cert.pem")
        )
      );

    server.requestHandler(req -> {
      req.response().putHeader("content-type", "text/html").end("<html><body>" +
        "<h1>Hello from vert.x!</h1>" +
        "<p>version = " + req.version() + "</p>" +
        "</body></html>");
    }).listen(8080);
  }
}
