package io.vertx.openshift.utils;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.openshift.utils.impl.JdbcVegetableStore;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.http.HttpServer;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.jdbc.JDBCClient;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.NoSuchElementException;

import static io.vertx.openshift.utils.Errors.error;

/**
 * @author Martin Spisiak (mspisiak@redhat.com) on 03/10/17.
 */
public abstract class AbstractDatabaseVerticle extends AbstractVerticle {
  protected boolean isExternalDB = Boolean.valueOf(System.getenv().getOrDefault("externalDb", "true"));
  protected DataStore store;

  protected Completable initDatabase(Vertx vertx, JDBCClient jdbc) {
    return jdbc.rxGetConnection()
      .flatMapCompletable(connection ->
        vertx.fileSystem().rxReadFile("db_init.sql")
          .flatMapObservable(buffer -> Observable.fromArray(buffer.toString().replaceAll(";.*$", "").split(";")))
          .flatMapCompletable(connection::rxExecute)
          .doAfterTerminate(connection::close)
      );
  }

  protected Single<HttpServer> initHttpServer(Router router, JDBCClient client) {
    this.store = new JdbcVegetableStore(client);
    return vertx
      .createHttpServer()
      .requestHandler(router::accept)
      .rxListen(8080);
  }

  protected void validateId(RoutingContext ctx) {
    try {
      ctx.put("id", Long.parseLong(ctx.pathParam("id")));
      // continue with the next handler in the route
      ctx.next();
    } catch (NumberFormatException e) {
      error(ctx, 400, "invalid id: " + e.getCause());
    }
  }

  protected void getAll(RoutingContext ctx) {
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

  protected void getOne(RoutingContext ctx) {
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

  protected void addOne(RoutingContext ctx) {
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
            .putHeader("Location", "/api/ve" +
              "" + json.getLong("id"))
            .putHeader("Content-Type", "application/json")
            .setStatusCode(201)
            .end(json.encodePrettily()),
        err -> writeError(ctx, err)
      );
  }

  protected void updateOne(RoutingContext ctx) {
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

  protected void writeError(RoutingContext ctx, Throwable err) {
    if (err instanceof NoSuchElementException) {
      error(ctx, 404, err);
    } else if (err instanceof IllegalArgumentException) {
      error(ctx, 422, err);
    } else {
      error(ctx, 409, err);
    }
  }

  protected void deleteOne(RoutingContext ctx) {
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
}
