package io.vertx.openshift.it;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.openshift.it.impl.JdbcVegetableStore;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.Future;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.http.HttpServer;
import io.vertx.rxjava.core.http.HttpServerResponse;
import io.vertx.rxjava.ext.jdbc.JDBCClient;
import io.vertx.rxjava.ext.web.Router;
import io.vertx.rxjava.ext.web.RoutingContext;
import io.vertx.rxjava.ext.web.handler.BodyHandler;
import rx.Completable;
import rx.Observable;
import rx.Single;

import java.util.NoSuchElementException;

import static io.vertx.openshift.it.Errors.error;

/**
 * @author Martin Spisiak (mspisiak@redhat.com) on 03/10/17.
 */
public class MySQLVerticle extends AbstractVerticle {
  protected String JDBC_URL = System.getenv().getOrDefault("JDBC_URL",
    "jdbc:mysql://db-mysql/testdb");
  protected String JDBC_USER = System.getenv().getOrDefault("JDBC_USER", "vertx");
  protected String JDBC_PASSWORD = System.getenv().getOrDefault("JDBC_PASSWORD", "password");
  private DataStore store;

  @Override
  public void start() throws Exception {
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());
    router.route("/api/vegetables/:id").handler(this::validateId);
    router.get("/api/vegetables").handler(this::getAll);
    router.post("/api/vegetables").handler(this::addOne);
    router.get("/api/vegetables/:id").handler(this::getOne);
    router.put("/api/vegetables/:id").handler(this::updateOne);
    router.delete("/api/vegetables/:id").handler(this::deleteOne);

    router.get("/healthcheck").handler(rc -> rc.response().end("OK"));

    JsonObject config = new JsonObject()
      .put("url", JDBC_URL)
      .put("driver_class", "com.mysql.jdbc.Driver")
      .put("user", JDBC_USER)
      .put("password", JDBC_PASSWORD);

    JDBCClient jdbcClient = JDBCClient.createShared(vertx, config);

    initDatabase(vertx, jdbcClient)
      .andThen(initHttpServer(router, jdbcClient))
      .subscribe(
        (http) -> System.out.println("Server ready on port " + http.actualPort()),
        Throwable::printStackTrace
      );
  }

  private Completable initDatabase(Vertx vertx, JDBCClient jdbc) {
    return jdbc.rxGetConnection()
      .flatMapCompletable(connection ->
        vertx.fileSystem().rxReadFile("db_init.sql")
          .flatMapObservable(buffer -> Observable.from(buffer.toString().replaceAll(";.*$","").split(";")))
          .flatMapSingle(connection::rxExecute)
          .doAfterTerminate(connection::close)
          .toCompletable()
      );
  }

  private Single<HttpServer> initHttpServer(Router router, JDBCClient client) {
    store = new JdbcVegetableStore(client);
    // Create the HTTP server and pass the "accept" method to the request handler.
    return vertx
      .createHttpServer()
      .requestHandler(router::accept)
      .rxListen(8080);
  }

  private void validateId(RoutingContext ctx) {
    try {
      ctx.put("id", Long.parseLong(ctx.pathParam("id")));
      // continue with the next handler in the route
      ctx.next();
    } catch (NumberFormatException e) {
      error(ctx, 400, "invalid id: " + e.getCause());
    }
  }

  private void getAll(RoutingContext ctx) {
    HttpServerResponse response = ctx.response()
      .putHeader("Content-Type", "application/json");
    JsonArray res = new JsonArray();
    store.readAll()
      .subscribe(
        res::add,
        err -> error(ctx, 415, err),
        () -> response.end(res.encodePrettily())
      );
  }

  private void getOne(RoutingContext ctx) {
    HttpServerResponse response = ctx.response()
      .putHeader("Content-Type", "application/json");

    store.read(ctx.get("id"))
      .subscribe(
        json -> response.end(json.encodePrettily()),
        err -> {
          if (err instanceof NoSuchElementException) {
            error(ctx, 404, err);
          } else if (err instanceof IllegalArgumentException) {
            error(ctx, 415, err);
          } else {
            error(ctx, 500, err);
          }
        }
      );
  }

  private void addOne(RoutingContext ctx) {
    JsonObject item;
    try {
      item = ctx.getBodyAsJson();
    } catch (RuntimeException e) {
      error(ctx, 415, "invalid payload");
      return;
    }

    if (item == null) {
      error(ctx, 415, "invalid payload");
      return;
    }

    store.create(item)
      .subscribe(
        json ->
          ctx.response()
            .putHeader("Location", "/api/vegetables/" + json.getLong("id"))
            .putHeader("Content-Type", "application/json")
            .setStatusCode(201)
            .end(json.encodePrettily()),
        err -> writeError(ctx, err)
      );
  }

  private void updateOne(RoutingContext ctx) {
    JsonObject item;
    try {
      item = ctx.getBodyAsJson();
    } catch (RuntimeException e) {
      error(ctx, 415, "invalid payload");
      return;
    }

    if (item == null) {
      error(ctx, 415, "invalid payload");
      return;
    }

    store.update(ctx.get("id"), item)
      .subscribe(
        () ->
          ctx.response()
            .putHeader("Content-Type", "application/json")
            .setStatusCode(200)
            .end(item.put("id", ctx.<Long>get("id")).encodePrettily()),
        err -> writeError(ctx, err)
      );
  }

  private void writeError(RoutingContext ctx, Throwable err) {
    if (err instanceof NoSuchElementException) {
      error(ctx, 404, err);
    } else if (err instanceof IllegalArgumentException) {
      error(ctx, 422, err);
    } else {
      error(ctx, 409, err);
    }
  }

  private void deleteOne(RoutingContext ctx) {
    store.delete(ctx.get("id"))
      .subscribe(
        () ->
          ctx.response()
            .setStatusCode(204)
            .end(),
        err -> {
          if (err instanceof NoSuchElementException) {
            error(ctx, 404, err);
          } else {
            error(ctx, 415, err);
          }
        }
      );
  }

  private String getEnv(String key, String dv) {
    String s = System.getenv(key);
    if (s == null) {
      return dv;
    }
    return s;
  }
}
