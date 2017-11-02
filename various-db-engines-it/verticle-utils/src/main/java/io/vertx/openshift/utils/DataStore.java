package io.vertx.openshift.utils;

import io.vertx.core.json.JsonObject;
import rx.Completable;
import rx.Observable;
import rx.Single;

/**
 * @author Martin Spisiak (mspisiak@redhat.com) on 03/10/17.
 */
public interface DataStore {
  Single<JsonObject> create(JsonObject item);

  Observable<JsonObject> readAll();

  Single<JsonObject> read(long id);

  Completable update(long id, JsonObject item);

  Completable delete(long id);
}
