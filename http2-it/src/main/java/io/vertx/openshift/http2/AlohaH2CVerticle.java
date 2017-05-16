package io.vertx.openshift.http2;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class AlohaH2CVerticle extends AbstractVerticle {


  private WebClient client;

  @Override
  public void start() throws Exception {

    client = WebClient.create(vertx, new WebClientOptions().setProtocolVersion(HttpVersion.HTTP_2));

    Router router = Router.router(vertx);
    router.get("/").handler(this::hello);
    router.get("/health").handler(rc -> rc.response().end("OK"));
    router.get("/front").handler(this::front);

    HttpServer server =
      vertx.createHttpServer(new HttpServerOptions());

    server.requestHandler(router::accept)
      .listen(8081);
  }

  private void hello(RoutingContext rc) {
    rc.response().end("Aloha " + rc.request().version());
  }

  private void front(RoutingContext rc) {
    client.get(80, "aloha", "/")
      .send(resp -> {
        if (resp.succeeded()) {
          rc.response().end(resp.result().body() + " " + rc.request().version());
        } else {
          rc.fail(resp.cause());
        }
      });
  }
}
