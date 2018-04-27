package io.vertx.openshift.mongodb.models;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.BulkOperation;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.MongoClient;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;


public class MongoDBVegetableStore {

  protected final MongoClient client;
  public static final String VEGETABLE_COLLECTION = "vegetables";

  public MongoDBVegetableStore(MongoClient mongoClient) {
    this.client = mongoClient;
  }

  public Future<JsonObject> insertOrReplace(JsonObject item) {
    Future<JsonObject> createFuture = Future.future();
    client.save(VEGETABLE_COLLECTION, item, response -> {
      if (response.succeeded()) {
        createFuture.complete(new JsonObject()
          .put("id", response.result()));
      } else {
        createFuture.fail(response.cause());
      }
    });

    return createFuture;
  }

  public Future<JsonObject> insert(JsonObject item) {
    Future<JsonObject> createFuture = Future.future();
    client.insert(VEGETABLE_COLLECTION, item, response -> {
      if (response.succeeded()) {
        createFuture.complete(new JsonObject()
          .put("id", response.result()));
      } else {
        createFuture.fail(response.cause());
      }
    });

    return createFuture;
  }

  public Future<JsonObject> update(String id, JsonObject item) {
    JsonObject findQuery = new JsonObject().put("_id", id);
    Future<JsonObject> updateFuture = Future.future();
    client.updateCollection(VEGETABLE_COLLECTION, findQuery, new JsonObject().put("$set", item), requestResult -> {
      if (requestResult.failed()) {
        updateFuture.fail(requestResult.cause().getMessage());
      } else {
        updateFuture.complete(requestResult.result().toJson());
      }
    });
    return updateFuture;
  }


  public Future<JsonObject> replace(String id, JsonObject item) {
    JsonObject findQuery = new JsonObject().put("_id", id);
    Future<JsonObject> updateFuture = Future.future();
    client.replaceDocuments(VEGETABLE_COLLECTION, findQuery, item, requestResult -> {
      if (requestResult.failed()) {
        updateFuture.fail(requestResult.cause().getMessage());
      } else {
        updateFuture.complete(requestResult.result().toJson());
      }
    });
    return updateFuture;
  }

  public Future<JsonObject> readAll() {
    Future<JsonObject> readAllFuture = Future.future();
    JsonArray result = new JsonArray();
    client.findBatch(VEGETABLE_COLLECTION, new JsonObject())
      .handler(jsonObject -> {
        result.add(jsonObject);
      })
      .exceptionHandler(throwable -> readAllFuture.fail(throwable))
      .endHandler(event -> {
        readAllFuture.complete(new JsonObject().put("result", result));
      });
    return readAllFuture;
  }

  public Future<JsonObject> countAll() {
    Future<JsonObject> countResult = Future.future();
    client.count(VEGETABLE_COLLECTION, new JsonObject(), event -> {
      if (event.succeeded()) {
        countResult.complete(new JsonObject().put("count", event.result()));
      } else {
        countResult.fail(event.cause());
      }
    });
    return countResult;
  }

  public Future<JsonObject> read(String id) {
    Future<JsonObject> readFuture = Future.future();
    JsonObject findQuery = new JsonObject().put("_id", id);
    client.findOne(VEGETABLE_COLLECTION, findQuery, null, requestResult -> {
      if (requestResult.succeeded()) {
        if (Objects.isNull(requestResult.result())) {
          readFuture.fail(new NoSuchElementException("Document with id: " + id + " not found"));
        } else {
          readFuture.complete(requestResult.result());
        }
      } else {
        readFuture.fail(requestResult.cause());
      }
    });
    return readFuture;
  }

  public Future<JsonObject> readPage(int page, int limit) {
    Future<JsonObject> returnedPage = Future.future();

    FindOptions options = new FindOptions().setSkip((page - 1) * limit).setLimit(limit);
    client.findWithOptions(VEGETABLE_COLLECTION, new JsonObject(), options, event -> {
      if (event.succeeded()) {
        JsonArray pageVegetables = event.map(jsonObjects -> {
          JsonArray jsonArray = new JsonArray();
          for (JsonObject object : jsonObjects) {
            jsonArray.add(object);
          }
          return jsonArray;
        }).result();
        returnedPage.complete(new JsonObject().put("result", pageVegetables));
      }
    });
    return returnedPage;
  }

  public Future<JsonObject> delete(String id) {
    JsonObject findQuery = new JsonObject().put("_id", id);
    Future<JsonObject> deleteFuture = Future.future();
    client.removeDocument(VEGETABLE_COLLECTION, findQuery, requestResult -> {
      if (requestResult.succeeded()) {
        if (requestResult.result().getRemovedCount() == 0) {
          deleteFuture.fail(new NoSuchElementException("Document with id: " + id + " not found"));
        } else {
          deleteFuture.complete(new JsonObject().put("removed", requestResult.result().toJson()));
        }
      } else {
        deleteFuture.fail(requestResult.cause().getMessage());
      }
    });
    return deleteFuture;
  }

  public Future<JsonObject> multipleAdd(JsonArray data) {

    Future<JsonObject> multipleAddResult = Future.future();
    List<BulkOperation> operations = new ArrayList<>();

    for (int i = 0; i < data.size(); i++) {
      operations.add(BulkOperation.createInsert(data.getJsonObject(i)));
    }

    JsonObject withoutStockQuery = new JsonObject()
      .put("stock", new JsonObject().put("$exists", false));
    JsonObject updateTo = new JsonObject()
      .put("$set", new JsonObject().put("stock", 0));

    operations.add(BulkOperation.createUpdate(withoutStockQuery, updateTo));

    client.bulkWrite(VEGETABLE_COLLECTION, operations, event -> {
      if (event.succeeded()) {
        multipleAddResult.complete(event.result().toJson());
      } else {
        multipleAddResult.fail(event.cause());
      }
    });

    return multipleAddResult;
  }

  public Future<JsonObject> multipleReplace(JsonArray data) {

    Future<JsonObject> multipleAddResult = Future.future();
    List<BulkOperation> operations = new ArrayList<>();

    for (int i = 0; i < data.size(); i++) {
      JsonObject object = data.getJsonObject(i);
      String id = object.getString("_id");
      if (Objects.isNull(id) || id.isEmpty()) {
        return Future.failedFuture("Id is required");
      }
      operations.add(BulkOperation.createReplace(new JsonObject().put("_id", id), object));
    }

    client.bulkWrite(VEGETABLE_COLLECTION, operations, event -> {
      if (event.succeeded()) {
        multipleAddResult.complete(event.result().toJson());
      } else {
        multipleAddResult.fail(event.cause());
      }
    });

    return multipleAddResult;
  }

  public Future<JsonObject> multipleDelete(JsonObject data) {

    Future<JsonObject> multipleAddResult = Future.future();
    List<BulkOperation> operations = new ArrayList<>();

    JsonArray ids = data.getJsonArray("ids");


    if (Objects.isNull(ids) || ids.size() == 0) {
      return Future.failedFuture("Ids are required");
    }

    for (int i = 0; i < ids.size(); i++) {
      String id = ids.getString(i);
      operations.add(BulkOperation.createDelete(new JsonObject().put("_id", id)));
    }

    client.bulkWrite(VEGETABLE_COLLECTION, operations, event -> {
      if (event.succeeded()) {
        multipleAddResult.complete(event.result().toJson());
      } else {
        multipleAddResult.fail(event.cause());
      }
    });

    return multipleAddResult;
  }
}

