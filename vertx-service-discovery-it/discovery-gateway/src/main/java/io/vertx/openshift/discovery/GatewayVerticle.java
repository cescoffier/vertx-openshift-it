package io.vertx.openshift.discovery;

import io.reactivex.Single;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.http.HttpClient;
import io.vertx.reactivex.ext.jdbc.JDBCClient;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;
import io.vertx.reactivex.ext.web.codec.BodyCodec;
import io.vertx.reactivex.servicediscovery.ServiceDiscovery;
import io.vertx.reactivex.servicediscovery.types.HttpEndpoint;
import io.vertx.reactivex.servicediscovery.types.JDBCDataSource;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class GatewayVerticle extends AbstractVerticle {

  private final String ENDPOINT_NAME = "some-http-services";

  private ServiceDiscovery discovery;

  // Using service discovery
  private HttpClient client;
  private WebClient web;
  private JDBCClient database;

  // Using DNS
  private HttpClient dnsClient;
  private WebClient dnsWeb;
  private JDBCClient dnsDatabase;

  @Override
  public void start() throws Exception {

    dnsClient = vertx.createHttpClient(new HttpClientOptions().setDefaultHost(ENDPOINT_NAME).setDefaultPort(8080)
      .setKeepAlive(false));
    dnsWeb = WebClient.create(vertx, new WebClientOptions().setDefaultHost(ENDPOINT_NAME).setDefaultPort(8080)
      .setKeepAlive(false));
    dnsDatabase = JDBCClient.createShared(vertx, new JsonObject()
      .put("url", "jdbc:postgresql://my-database:5432/my_data")
      .put("driver_class", "org.postgresql.Driver")
      .put("user", "luke")
      .put("password", "secret")
    );

    Router router = Router.router(vertx);
    router.get("/health").handler(rc -> rc.response().end("OK"));
    router.get("/services/http").handler(this::invokeHttpService);
    router.get("/services/web").handler(this::invokeWebService);
    router.get("/services/db").handler(this::checkDb);

    router.get("/dns/http").handler(this::invokeHttpServiceWithDns);
    router.get("/dns/web").handler(this::invokeWebServiceWithDns);
    router.get("/dns/db").handler(this::checkDbWithDns);


    router.get("/ref/http").handler(this::invokeHttpServiceWithRef);
    router.get("/ref/web").handler(this::invokeWebServiceWithRef);

    router.put("/webclient").handler(this::webclient);


    ServiceDiscovery.create(vertx, discovery -> {
      this.discovery = discovery;

      Single<WebClient> svc1 = HttpEndpoint.rxGetWebClient(discovery, svc -> svc.getName().equals(ENDPOINT_NAME),
        new JsonObject().put("keepAlive", false));

      Single<HttpClient> svc2 = HttpEndpoint.rxGetClient(discovery, svc -> svc.getName().equals(ENDPOINT_NAME),
        new JsonObject().put("keepAlive", false));

      Single<JDBCClient> svc3 = JDBCDataSource.rxGetJDBCClient(discovery, svc -> svc.getName().equals("my-database"),
        new JsonObject()
          .put("url", "jdbc:postgresql://my-database:5432/my_data")
          .put("driver_class", "org.postgresql.Driver")
          .put("user", "luke")
          .put("password", "secret"));

      Single.zip(svc1, svc2, svc3, (wc, hc, db) -> {
        this.web = wc;
        this.client = hc;
        this.database = db;

        return vertx.createHttpServer()
          .requestHandler(router::accept)
          .listen(8080);
      }).subscribe();
    });
  }

  private void webclient(RoutingContext routingContext) {
    String host = routingContext.request().getParam("host");
    final HttpClient httpClient = vertx.createHttpClient(new HttpClientOptions().setDefaultHost(host).setKeepAlive(false));
    forward(routingContext, httpClient, true);
  }

  private void checkDbWithDns(RoutingContext rc) {
    dnsDatabase.rxGetConnection()
      .subscribe(
        connection -> {
          rc.response().end("OK");
          connection.close();
        },
        rc::fail
      );
  }

  private void checkDb(RoutingContext rc) {
    database.rxGetConnection()
      .subscribe(
        connection -> {
          rc.response().end("OK");
          connection.close();
        },
        rc::fail
      );
  }

  private void invokeHttpServiceWithRef(RoutingContext rc) {
    discovery
      .rxGetRecord(rec -> rec.getName().equals(ENDPOINT_NAME))
      .map(rec -> discovery.getReference(rec))
      .map(ref -> ref.getAs(HttpClient.class))
      .subscribe(
        cl -> forward(rc, cl, true),
        rc::fail
      );
  }

  private void invokeWebServiceWithRef(RoutingContext rc) {
    discovery
      .rxGetRecord(rec -> rec.getName().equals(ENDPOINT_NAME))
      .map(rec -> discovery.getReference(rec))
      .map(ref -> ref.getAs(WebClient.class))
      .subscribe(
        cl -> forward(rc, cl, true),
        rc::fail
      );
  }

  private void invokeHttpServiceWithDns(RoutingContext rc) {
    forward(rc, dnsClient, false);
  }

  private void invokeWebServiceWithDns(RoutingContext rc) {
    forward(rc, dnsWeb, false);
  }

  private void invokeWebService(RoutingContext rc) {
    forward(rc, web, false);
  }

  private void invokeHttpService(RoutingContext rc) {
    forward(rc, client, false);
  }

  private void forward(RoutingContext rc, HttpClient client, boolean close) {
    String message = rc.request().getParam("message");
    client.get("/?message=" + message, response -> {
      if (response.statusCode() != 200) {
        rc.response().setStatusCode(response.statusCode()).end(response.statusMessage());
      } else {
        response.bodyHandler(buffer -> {
          JsonObject object = buffer.toJsonObject();
          rc.response().putHeader("content-type", "application/json;charset=UTF-8")
            .end(object.encodePrettily());
          if (close) {
            client.close();
          }
        });
      }
    })
      .setTimeout(3000)
      .exceptionHandler(t -> {
        t.printStackTrace();
        rc.response().setStatusCode(503).end(t.getMessage());
      })
      .end();
  }

  private void forward(RoutingContext rc, WebClient client, boolean close) {
    String message = rc.request().getParam("message");
    client.get("/?message=" + message)
      .timeout(3000)
      .as(BodyCodec.jsonObject()).rxSend()
      .map(HttpResponse::body)
      .subscribe(
        json -> {
          rc.response()
            .putHeader("content-type", "application/json;charset=UTF-8")
            .end(json.encodePrettily());
          if (close) {
            client.close();
          }
        },
        rc::fail
      );
  }
}
