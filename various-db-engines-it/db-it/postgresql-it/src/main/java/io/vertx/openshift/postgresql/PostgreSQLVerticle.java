package io.vertx.openshift.postgresql;

import io.vertx.core.json.JsonObject;
import io.vertx.openshift.utils.AbstractDatabaseVerticle;
import io.vertx.openshift.utils.TestUtils;
import io.vertx.rxjava.ext.jdbc.JDBCClient;
import io.vertx.rxjava.ext.web.Router;
import io.vertx.rxjava.ext.web.handler.BodyHandler;

/**
 * @author Martin Spisiak (mspisiak@redhat.com) on 03/10/17.
 */
public class PostgreSQLVerticle extends AbstractDatabaseVerticle {

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

    JsonObject config = TestUtils.allocateDatabase("postgresql", isExternalDB);

    JDBCClient jdbcClient = JDBCClient.createShared(vertx, config);

    initDatabase(vertx, jdbcClient)
      .andThen(initHttpServer(router, jdbcClient))
      .subscribe(
        (http) -> System.out.println("Server ready on port " + http.actualPort()),
        Throwable::printStackTrace
      );
  }

}
