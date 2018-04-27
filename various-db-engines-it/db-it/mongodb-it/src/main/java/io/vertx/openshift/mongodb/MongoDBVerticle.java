package io.vertx.openshift.mongodb;


import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.openshift.mongodb.exceptions.UnprocessableEntityException;
import io.vertx.openshift.mongodb.exceptions.UnsupportedMediaTypeException;
import io.vertx.openshift.mongodb.models.MongoDBVegetableStore;

import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * @author Adam Koniar (akoniar@redhat.com) on 11/04/18.
 */
public class MongoDBVerticle extends AbstractVerticle {

  protected MongoDBVegetableStore store;
  private static final int PAGE_LIMIT = 5;

  public void start() throws Exception {
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());
    router.get("/api/vegetables/page/:number").handler(this::validatePage);
    router.get("/api/vegetables/page/:number").handler(this::getPage);
    router.post("/api/vegetables/add").handler(this::isPayloadValidJsonArray);
    router.post("/api/vegetables/add").handler(this::add);
    router.put("/api/vegetables/update").handler(this::isPayloadValidJsonArray);
    router.put("/api/vegetables/update").handler(this::update);
    router.delete("/api/vegetables/delete").handler(this::isPayloadValidJsonObject);
    router.delete("/api/vegetables/delete").handler(this::delete);
    router.route("/api/vegetables/:id").handler(this::validateId);
    router.post("/api/vegetables").handler(this::isPayloadValidJsonObject);
    router.put("/api/vegetables/:id").handler(this::isPayloadValidJsonObject);
    router.patch("/api/vegetables/:id").handler(this::isPayloadValidJsonObject);
    router.get("/api/vegetables").handler(this::getAll);
    router.post("/api/vegetables").handler(this::addOne);
    router.post("/api/vegetables:id").handler(this::addOrReplaceOne);
    router.get("/api/vegetables/:id").handler(this::getOne);
    router.put("/api/vegetables/:id").handler(this::replaceOne);
    router.patch("/api/vegetables/:id").handler(this::updateOne);
    router.delete("/api/vegetables/:id").handler(this::deleteOne);

    router.get("/healthcheck").handler(rc -> rc.response().end("OK"));

    JsonObject config = new JsonObject()
      .put("connection_string", "mongodb://db")
      .put("host", "db")
      .put("username", "vertx")
      .put("password", "password")
      .put("db_name", "testdb");

    MongoClient mongoClient = MongoClient.createShared(this.vertx, config);
      Future<Void> initServer = initDatabase(mongoClient)
      .setHandler(event -> {
        if (event.succeeded()) {
          HttpServer http = initHttpServer(router, mongoClient);
          System.out.println("Server ready on port " + http.actualPort());
        } else {
          System.out.println("Server is not deployed: " + event.cause().getMessage());
        }
      });


    if (initServer.failed()) {
      throw new Exception("Cannot deploy server");
    }
  }


  protected Future<Void> initDatabase(MongoClient mongo) {
    String collectionName = MongoDBVegetableStore.VEGETABLE_COLLECTION;
    Future<Void> deployDatabase = Future.future();
    mongo.getCollections(getCollectionResponse -> {
      if (getCollectionResponse.succeeded()) {
        if (getCollectionResponse.result().contains(collectionName)) {
          System.out.println("Collection " + collectionName + " exists");
          deployDatabase.complete();
        } else {
          mongo.createCollection(collectionName, result -> {
            if (result.succeeded()) {
              System.out.println("Collection " + collectionName + " was created");
              deployDatabase.complete();
            } else {
              deployDatabase.fail(result.cause());
            }
          });
        }
      } else {
        deployDatabase.fail(getCollectionResponse.cause());
      }
    });
    return deployDatabase;
  }


  protected HttpServer initHttpServer(Router router, MongoClient client) {
    this.store = new MongoDBVegetableStore(client);
    return vertx
      .createHttpServer()
      .requestHandler(router::accept)
      .listen(8080);
  }

  protected void validateId(RoutingContext ctx) {
    if (ctx.pathParam("id").matches("[0-9a-fA-F]+$")) {
      ctx.put("id", ctx.pathParam("id"));
      // continue with the next handler in the route
      ctx.next();
    } else {
      error(ctx, 400, "invalid id: ");
    }

  }

  protected void validatePage(RoutingContext ctx) {
    try {
      int number = Integer.parseInt(ctx.pathParam("number"));
      if (number > 0) {
        ctx.put("page", number);
        ctx.next();
      } else {
        error(ctx, 400, "invalid page: ");
      }
    } catch (RuntimeException error) {
      error(ctx, 400, "invalid page: ");
    }
  }

  protected void getAll(RoutingContext ctx) {
    CompositeFuture.join(store.readAll(), store.countAll())
      .setHandler(event -> {
        if (event.succeeded()) {
          JsonObject result = new JsonObject();
          for (Object futureResult : event.result().list()) {
            result = ((JsonObject) futureResult).mergeIn(result);
          }
          ctx.response()
            .putHeader("Content-Type", "application/json")
            .setStatusCode(200)
            .end(result.encodePrettily());
        } else {
          writeError(ctx, event.cause());
        }
      });
  }

  protected void getPage(RoutingContext ctx) {
    int page = ctx.get("page");

    store.readPage(page, PAGE_LIMIT).setHandler(readPageEvent -> {
      if (readPageEvent.succeeded()) {
        ctx.response()
          .putHeader("Content-Type", "application/json")
          .setStatusCode(200)
          .end(readPageEvent.result().encodePrettily());
      } else {
        writeError(ctx, readPageEvent.cause());
      }
    });
  }

  protected void getOne(RoutingContext ctx) {
    store.read(ctx.get("id"))
      .setHandler(event -> {
        if (event.succeeded()) {
          ctx.response()
            .putHeader("Content-Type", "application/json")
            .setStatusCode(200)
            .end(event.result().encodePrettily());
        } else {
          writeError(ctx, event.cause());
        }
      });
  }

  protected void addOne(RoutingContext ctx) {
    JsonObject item = ctx.get("jsonBody");
    if(item != null) {
      store.insert(item)
        .setHandler(event -> {
          if (event.succeeded()) {
            ctx.response()
              .putHeader("Content-Type", "application/json")
              .setStatusCode(201)
              .end(event.result().encodePrettily());
          } else {
            writeError(ctx, event.cause());
          }
        });
    }else {
      writeError(ctx,new Exception(ctx.getBodyAsString()));
    }
  }

  protected void addOrReplaceOne(RoutingContext ctx) {
    JsonObject item = ctx.get("jsonBody");
    item.put("_id", ctx.get("id").toString());

    store.insertOrReplace(item)
      .setHandler(event -> {
        if (event.succeeded()) {
          ctx.response()
            .putHeader("Content-Type", "application/json")
            .setStatusCode(201)
            .end(event.result().encodePrettily());
        } else {
          writeError(ctx, event.cause());
        }
      });
  }

  protected void replaceOne(RoutingContext ctx) {
    JsonObject item = ctx.get("jsonBody");

    store.replace(ctx.get("id"), item)
      .setHandler(event -> {
        if (event.succeeded()) {
          ctx.response()
            .putHeader("Content-Type", "application/json")
            .setStatusCode(201)
            .end(event.result().encodePrettily());
        } else {
          writeError(ctx, event.cause());
        }
      });
  }

  protected void updateOne(RoutingContext ctx) {
    JsonObject item = ctx.get("jsonBody");

    store.update(ctx.get("id"), item)
      .setHandler(event -> {
        if (event.succeeded()) {
          ctx.response()
            .putHeader("Content-Type", "application/json")
            .setStatusCode(201)
            .end(event.result().encodePrettily());
        } else {
          writeError(ctx, event.cause());
        }
      });
  }

  protected void add(RoutingContext ctx) {
    JsonArray item = ctx.get("jsonBody");
    store.multipleAdd(item)
      .setHandler(event -> {
        if (event.succeeded()) {
          ctx.response()
            .putHeader("Content-Type", "application/json")
            .setStatusCode(201)
            .end(event.result().encodePrettily());
        } else {
          writeError(ctx, event.cause());
        }
      });
  }

  protected void update(RoutingContext ctx) {
    JsonArray item = ctx.get("jsonBody");
    store.multipleReplace(item)
      .setHandler(event -> {
        if (event.succeeded()) {
          ctx.response()
            .putHeader("Content-Type", "application/json")
            .setStatusCode(201)
            .end(event.result().encodePrettily());
        } else {
          writeError(ctx, event.cause());
        }
      });
  }

  protected void delete(RoutingContext ctx) {
    JsonObject item = ctx.get("jsonBody");
    store.multipleDelete(item)
      .setHandler(event -> {
        if (event.succeeded()) {
          ctx.response()
            .putHeader("Content-Type", "application/json")
            .setStatusCode(204)
            .end(event.result().encodePrettily());
        } else {
          writeError(ctx, event.cause());
        }
      });
  }


  private void isPayloadValidJsonObject(RoutingContext ctx) {
    JsonObject item;
    try {
      item = ctx.getBodyAsJson();
    } catch (RuntimeException e) {
      writeError(ctx, new UnsupportedMediaTypeException("Payload isn't json object"));
      return;
    }
    if (Objects.isNull(item)) {
      writeError(ctx, new UnsupportedMediaTypeException("Payload isn't set"));
    } else if (ctx.request().method() != HttpMethod.PATCH
      && ctx.request().method() != HttpMethod.DELETE
      && Objects.isNull(item.getString("name"))) {
      writeError(ctx, new UnprocessableEntityException("Name is required!"));
    } else {
      ctx.put("jsonBody", item);
      ctx.next();
    }
  }

  private void isPayloadValidJsonArray(RoutingContext ctx) {
    JsonArray item;
    System.out.println(ctx.getBodyAsJsonArray().toString());
    try {
      item = ctx.getBodyAsJsonArray();
    } catch (RuntimeException e) {
      writeError(ctx, new UnsupportedMediaTypeException("Payload isn't json array"));
      return;
    }
    if (Objects.isNull(item)) {
      writeError(ctx, new UnsupportedMediaTypeException("Payload isn't set"));
    } else {
      ctx.put("jsonBody", item);
      ctx.next();
    }
  }

  protected void deleteOne(RoutingContext ctx) {
    store.delete(ctx.get("id")).setHandler(event -> {
      if (event.succeeded()) {
        ctx.response()
          .setStatusCode(204)
          .end(event.result().encodePrettily());
      } else {
        writeError(ctx, event.cause());
      }
    });
  }


  protected void writeError(RoutingContext ctx, Throwable err) {
    if (err instanceof NoSuchElementException) {
      error(ctx, 404, err.getMessage());
    } else if (err instanceof UnprocessableEntityException) {
      error(ctx, 422, err.getMessage());
    } else if (err instanceof UnsupportedMediaTypeException) {
      error(ctx, 415, err.getMessage());
    } else {
      error(ctx, 409, err.getMessage());
    }
  }

  public static void error(RoutingContext ctx, int status, String cause) {
    JsonObject error = new JsonObject()
      .put("error", cause)
      .put("code", status)
      .put("path", ctx.request().path());
    ctx.response()
      .putHeader("Content-Type", "application/json")
      .setStatusCode(status)
      .end(error.encodePrettily());
  }

}
