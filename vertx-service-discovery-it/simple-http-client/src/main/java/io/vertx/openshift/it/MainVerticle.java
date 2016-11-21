package io.vertx.openshift.it;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.ServiceReference;
import io.vertx.servicediscovery.kubernetes.KubernetesServiceImporter;
import io.vertx.servicediscovery.types.HttpEndpoint;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class MainVerticle extends AbstractVerticle {

  private final String ENDPOINT_NAME = "simple-http-endpoint";

  private ServiceDiscovery discovery;

  @Override
  public void start() throws Exception {
    Future<Void> discoveryReady = Future.future();

    discovery = ServiceDiscovery.create(vertx)
        .registerServiceImporter(new KubernetesServiceImporter(), new JsonObject(), discoveryReady.completer());

    discoveryReady
        .compose((v) -> {
          Future<HttpClient> clientFuture = Future.future();
          HttpEndpoint.getClient(discovery, new JsonObject().put("name", ENDPOINT_NAME),
              new JsonObject().put("keepAlive", false), clientFuture.completer());
          return clientFuture;
        })
        .compose(client -> {
          Future<HttpServer> serverFuture = Future.future();

          HttpClient clientUsingDns = vertx.createHttpClient(new HttpClientOptions().setDefaultHost(ENDPOINT_NAME)
              .setKeepAlive(false));
          Router router = Router.router(vertx);
          router.route().handler(BodyHandler.create());
          router.get("/dns").handler(rc -> forward(rc, clientUsingDns));
          router.get("/discovery").handler(rc -> forward(rc, client));
          router.get("/ref").handler(rc -> forwardWithRef(rc));

          vertx.createHttpServer().requestHandler(router::accept).listen(8080, serverFuture.completer());
          return serverFuture;
        }).setHandler(ar -> {
      if (ar.failed()) {
        ar.cause().printStackTrace();
      } else {
        System.out.println("Ready to serve .... on port " + ar.result().actualPort());
      }
    });
  }

  private void forwardWithRef(RoutingContext rc) {
    discovery.getRecord(new JsonObject().put("name", ENDPOINT_NAME), ar -> {
      if (ar.failed()) {
        rc.response().setStatusCode(503).end("Service unavailable");
      } else {
        ServiceReference reference = discovery.getReferenceWithConfiguration(ar.result(),
            new JsonObject().put("keepAlive", false));
        HttpClient client = reference.get();
        forward(rc, client);
      }
    });
  }

  private void forward(RoutingContext rc, HttpClient client) {
    String message = rc.request().getParam("message");
    client.get("/?message=" + message, response -> {
      if (response.statusCode() != 200) {
        rc.response().setStatusCode(response.statusCode()).end(response.statusMessage());
      } else {
        response.bodyHandler(buffer -> {
          JsonObject object = buffer.toJsonObject();
          rc.response().putHeader("content-type", "application/json;charset=UTF-8")
              .end(object.encodePrettily());
        });
      }
    })
        .setTimeout(3000)
        .exceptionHandler(t -> rc.response().setStatusCode(503).end(t.getMessage()))
        .end();
  }
}
