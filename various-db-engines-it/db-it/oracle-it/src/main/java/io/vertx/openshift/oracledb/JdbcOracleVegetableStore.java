package io.vertx.openshift.oracledb;

import io.reactivex.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.openshift.utils.impl.JdbcVegetableStore;
import io.vertx.reactivex.ext.jdbc.JDBCClient;


public class JdbcOracleVegetableStore extends JdbcVegetableStore {

  public JdbcOracleVegetableStore(JDBCClient jdbcClient) {
    super(jdbcClient);
  }

  /**
   * This function transfer keys of data getting from parent function to lower case
   *
   * @param id
   * @return Single<JsonObject>
   */
  @Override
  public Single<JsonObject> read(long id) {
    return super.read(id).map(entries -> {
      JsonObject result = new JsonObject();
      entries.forEach(stringObjectEntry -> result.put(stringObjectEntry.getKey().toLowerCase(), stringObjectEntry.getValue()));
      return result;
    });
  }

  /**
   * This function get one Item from oracle db by rowId
   *
   * @param rowId
   * @return Single<JsonObject>
   */
  public Single<JsonObject> read(String rowId) {
    return client.rxGetConnection()
      .flatMap(conn -> {
        return conn
          .rxQueryWithParams("SELECT * FROM vegetables WHERE rowid = ?", new JsonArray().add(rowId))
          .doOnError(throwable -> System.out.println(throwable.getMessage()))
          .map(resultSet -> {
            System.out.println("Result: " + resultSet.getRows().size());
            JsonObject result = new JsonObject();
            resultSet.getRows().get(0)
              .forEach(stringObjectEntry ->
                result.put(stringObjectEntry.getKey().toLowerCase(), stringObjectEntry.getValue()
                )
              );
            return result;
          })
          .doAfterTerminate(conn::close);
      });
  }

  /**
   * This function create one item in db and return rowId of created item
   *
   * @param item
   * @return Single<JsonObject>
   */
  @Override
  public Single<JsonObject> create(JsonObject item) {
    if (item == null) {
      return Single.error(new IllegalArgumentException("The item must not be null"));
    }
    if (item.getString("name") == null || item.getString("name").isEmpty()) {
      return Single.error(new IllegalArgumentException("The name must not be null or empty"));
    }
    if (item.getInteger("amount", 0) < 0) {
      return Single.error(new IllegalArgumentException("The amount must greater or equal to 0"));
    }
    if (item.containsKey("id")) {
      return Single.error(new IllegalArgumentException("The created item already contains an 'id'"));
    }

    return client.rxGetConnection()
      .flatMap(conn -> {
        JsonArray params = new JsonArray().add(item.getValue("name")).add(item.getValue("amount", 0));
        return conn
          .rxUpdateWithParams(INSERT, params)
          .map(ur -> item.put("rowId", ur.getKeys().getString(0)))
          .doAfterTerminate(conn::close);
      });
  }
}

