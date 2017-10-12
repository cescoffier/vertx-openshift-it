package io.vertx.openshift.it;

import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.http.HttpServer;
import io.vertx.rxjava.ext.jdbc.JDBCClient;
import io.vertx.rxjava.ext.web.Router;
import io.vertx.rxjava.ext.web.handler.BodyHandler;
import rx.Completable;
import rx.Observable;
import rx.Single;

/**
 * @author Martin Spisiak (mspisiak@redhat.com) on 03/10/17.
 */
public class PostgreSQLVerticle extends VerticleUtil {
  protected String JDBC_URL = System.getenv().getOrDefault("JDBC_URL",
    "jdbc:postgresql://db/testdb");
  protected String JDBC_USER = System.getenv().getOrDefault("JDBC_USER", "vertx");
  protected String JDBC_PASSWORD = System.getenv().getOrDefault("JDBC_PASSWORD", "password");

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
      .put("driver_class", "org.postgresql.Driver")
      .put("user", JDBC_USER)
      .put("password", JDBC_PASSWORD);

    JDBCClient jdbcClient = JDBCClient.createShared(vertx, config);

    initDatabase(vertx, jdbcClient)
      .andThen(initHttpServer(router,jdbcClient))
      .subscribe(
        (http) -> System.out.println("Server ready on port " + http.actualPort()),
        Throwable::printStackTrace
      );
  }

}
