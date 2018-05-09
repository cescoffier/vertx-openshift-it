package io.vertx.openshift.oracledb;

import io.vertx.core.json.JsonObject;
import io.vertx.openshift.utils.AbstractDatabaseVerticle;
import io.vertx.openshift.utils.TestUtils;
import io.vertx.reactivex.core.http.HttpServer;
import io.vertx.reactivex.ext.jdbc.JDBCClient;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import io.reactivex.Completable;
import io.reactivex.Single;

import static io.vertx.openshift.utils.Errors.error;

/**
 * @author Martin Spisiak (mspisiak@redhat.com) on 12/10/17.
 */
public class OracleVerticle extends AbstractDatabaseVerticle {
  private final static String VEGETABLE_TABLE_EXISTS_QUERY = "select count(*) as count\n" +
    "from all_objects\n" +
    "where object_type in ('TABLE','VIEW')\n" +
    "and object_name = 'vegetables'";
  private final static String DROP_VEGETABLE_TABLE = "DROP TABLE vegetables";

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

    JsonObject config = TestUtils.allocateDatabase("oracle", true);

    JDBCClient jdbcClient = JDBCClient.createShared(vertx, config);

    vegetableTableExists(jdbcClient).subscribe(VegetableTableExists -> {
      Completable firstAction = initDatabase(vertx, jdbcClient);
      if (VegetableTableExists) {
        firstAction = dropVegetableTable(jdbcClient).andThen(initDatabase(vertx, jdbcClient));
      }
      firstAction
        .andThen(initHttpServer(router, jdbcClient))
        .subscribe(
          (http) -> System.out.println("Server ready on port " + http.actualPort()),
          Throwable::printStackTrace
        );
    });
  }

  @Override
  protected Single<HttpServer> initHttpServer(Router router, JDBCClient client) {
    this.store = new JdbcOracleVegetableStore(client);
    return vertx
      .createHttpServer()
      .requestHandler(router::accept)
      .rxListen(8080);
  }

  @Override
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
        json -> ((JdbcOracleVegetableStore) store).read(json.getString("rowId"))
          .subscribe(entries -> ctx.response()
            .putHeader("Location", "/api/vegetables/" + json.getLong("id"))
            .putHeader("Content-Type", "application/json")
            .setStatusCode(201)
            .end(entries.encodePrettily())),
        err -> {
          writeError(ctx, err);
        }
      );
  }

  private Single<Boolean> vegetableTableExists(JDBCClient jdbc) {
    return jdbc.rxGetConnection()
      .flatMap(conn -> conn
        .rxQuery(VEGETABLE_TABLE_EXISTS_QUERY)
        .map(resultSet -> resultSet.getRows().get(0).getLong("COUNT") != 0));
  }

  private Completable dropVegetableTable(JDBCClient jdbc) {
    return jdbc.rxGetConnection()
      .flatMap(conn -> conn.rxQuery(DROP_VEGETABLE_TABLE)).toCompletable();
  }

}
