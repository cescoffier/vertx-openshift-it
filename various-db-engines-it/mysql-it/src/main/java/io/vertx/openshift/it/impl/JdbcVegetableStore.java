package io.vertx.openshift.it.impl;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.ResultSet;
import io.vertx.openshift.it.DataStore;
import io.vertx.rxjava.ext.jdbc.JDBCClient;
import io.vertx.rxjava.ext.sql.SQLRowStream;
import rx.Completable;
import rx.Observable;
import rx.Single;

import java.util.NoSuchElementException;

/**
 * @author Martin Spisiak (mspisiak@redhat.com) on 03/10/17.
 */
public class JdbcVegetableStore implements DataStore {
  private static final String INSERT = "INSERT INTO vegetables (name, amount) VALUES (?, ?)";

  private static final String SELECT_ONE = "SELECT * FROM vegetables WHERE id = ?";

  private static final String SELECT_ALL = "SELECT * FROM vegetables";

  private static final String UPDATE = "UPDATE vegetables SET name = ?, amount = ? WHERE id = ?";

  private static final String DELETE = "DELETE FROM vegetables WHERE id = ?";

  private final JDBCClient client;

  public JdbcVegetableStore(JDBCClient jdbcClient) {
    this.client = jdbcClient;
  }

  @Override
  public Single<JsonObject> create(JsonObject item) {
    if (item == null) {
      return Single.error(new IllegalArgumentException("The item must not be null"));
    }
    if (item.getString("name") == null || item.getString("name").isEmpty()) {
      return Single.error(new IllegalArgumentException("The name must not be null or empty"));
    }
    if (item.getInteger("stock", 0) < 0) {
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
          .map(ur -> item.put("id", ur.getKeys().getLong(0)))
          .doAfterTerminate(conn::close);
      });
  }

  @Override
  public Observable<JsonObject> readAll() {
    return client.rxGetConnection()
      .flatMapObservable(conn ->
        conn
          .rxQueryStream(SELECT_ALL)
          .flatMapObservable(SQLRowStream::toObservable)
          .doAfterTerminate(conn::close))
      .map(array ->
        new JsonObject()
          .put("id", array.getLong(0))
          .put("name", array.getString(1))
          .put("amount", array.getInteger(2))
      );
  }

  @Override
  public Single<JsonObject> read(long id) {
    return client.rxGetConnection()
      .flatMap(conn -> {
        JsonArray param = new JsonArray().add(id);
        return conn
          .rxQueryWithParams(SELECT_ONE, param)
          .map(ResultSet::getRows)
          .flatMap(list -> {
            if (list.isEmpty()) {
              return Single.error(new NoSuchElementException("Item '" + id + "' not found"));
            } else {
              return Single.just(list.get(0));
            }
          })
          .doAfterTerminate(conn::close);
      });
  }

  @Override
  public Completable update(long id, JsonObject item) {
    if (item == null) {
      return Completable.error(new IllegalArgumentException("The item must not be null"));
    }
    if (item.getString("name") == null || item.getString("name").isEmpty()) {
      return Completable.error(new IllegalArgumentException("The name must not be null or empty"));
    }
    if (item.getInteger("amount", 0) < 0) {
      return Completable.error(new IllegalArgumentException("The amount must greater or equal to 0"));
    }
    if (item.containsKey("id") && id != item.getInteger("id")) {
      return Completable.error(new IllegalArgumentException("The 'id' cannot be changed"));
    }

    return client.rxGetConnection()
      .flatMapCompletable(conn -> {
        JsonArray params = new JsonArray().add(item.getValue("name")).add(item.getValue("amount", 0)).add(id);
        return conn.rxUpdateWithParams(UPDATE, params)
          .flatMapCompletable(up -> {
            if (up.getUpdated() == 0) {
              return Completable.error(new NoSuchElementException("Unknown item '" + id + "'"));
            }
            return Completable.complete();
          })
          .doAfterTerminate(conn::close);
      });
  }

  @Override
  public Completable delete(long id) {
    return client.rxGetConnection()
      .flatMapCompletable(conn -> {
        JsonArray params = new JsonArray().add(id);
        return conn.rxUpdateWithParams(DELETE, params)
          .flatMapCompletable(up -> {
            if (up.getUpdated() == 0) {
              return Completable.error(new NoSuchElementException("Unknown item '" + id + "'"));
            }
            return Completable.complete();
          })
          .doAfterTerminate(conn::close);
      });
  }
}
