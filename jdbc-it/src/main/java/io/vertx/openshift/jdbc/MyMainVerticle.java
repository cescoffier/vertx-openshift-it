package io.vertx.openshift.jdbc;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.flywaydb.core.Flyway;

/**
 * @author Thomas Segismont
 */
public class MyMainVerticle extends AbstractVerticle {

  public static final String JDBC_URL = System.getenv().getOrDefault("JDBC_URL",
    "jdbc:postgresql://postgres/testdb");
  public static final String JDBC_USER = System.getenv().getOrDefault("JDBC_USER", "vertx");
  public static final String JDBC_PASSWORD = System.getenv().getOrDefault("JDBC_PASSWORD", "password");
  private JDBCClient jdbcClient;

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    Router router = Router.router(vertx);

    JsonObject config = new JsonObject()
      .put("jdbcUrl", JDBC_URL)
      .put("driverClassName", "org.postgresql.Driver")
      .put("principal", JDBC_USER)
      .put("credential", JDBC_PASSWORD)
      .put("castUUID", true);

    jdbcClient = JDBCClient.createNonShared(vertx, config);

    router.route("/").handler(rc -> rc.response().end("OK"));
    router.route("/init").handler(this::init);

    /* === Add tests here === */

    TextQueryTest textQueryTest = new TextQueryTest(jdbcClient);
    router.route(textQueryTest.getPath()).handler(textQueryTest);

    QueryWithParamsTest queryWithParamsTest = new QueryWithParamsTest(jdbcClient);
    router.route(queryWithParamsTest.getPath()).handler(queryWithParamsTest);

    CRUDTest crudTest = new CRUDTest(jdbcClient);
    router.route(crudTest.getPath()).handler(crudTest);

    UpdateWithParamsTest updateWithParamsTest = new UpdateWithParamsTest(jdbcClient);
    router.route(updateWithParamsTest.getPath()).handler(updateWithParamsTest);

    StoredProcedureTest storedProcedureTest = new StoredProcedureTest(jdbcClient);
    router.route(storedProcedureTest.getPath()).handler(storedProcedureTest);

    BatchUpdatesTest batchUpdatesTest = new BatchUpdatesTest(jdbcClient);
    router.route(batchUpdatesTest.getPath()).handler(batchUpdatesTest);

    StreamingResultsTest streamingResultsTest = new StreamingResultsTest(jdbcClient);
    router.route(streamingResultsTest.getPath()).handler(streamingResultsTest);

    TransactionsTest transactionsTest = new TransactionsTest(jdbcClient);
    router.route(transactionsTest.getPath()).handler(transactionsTest);

    DDLTest ddlTest = new DDLTest(jdbcClient);
    router.route(ddlTest.getPath()).handler(ddlTest);

    SpecialDatatypesTest specialDatatypesTest = new SpecialDatatypesTest(jdbcClient);
    router.route(specialDatatypesTest.getPath()).handler(specialDatatypesTest);

    BinaryTest binaryTest = new BinaryTest(jdbcClient);
    router.route(binaryTest.getPath()).handler(binaryTest);

    ClientCreationTest clientCreationTest = new ClientCreationTest(vertx, config);
    router.route(clientCreationTest.getPath()).handler(clientCreationTest);

    /* === */

    vertx.createHttpServer(new HttpServerOptions().setPort(8080))
      .requestHandler(router::accept)
      .listen(ar -> {
        if (ar.succeeded()) {
          startFuture.complete();
        } else {
          startFuture.fail(ar.cause());
        }
      });
  }


  private void init(RoutingContext routingContext) {
    jdbcClient.getConnection(x -> {
      if (x.failed()) {
        x.cause().printStackTrace();
        routingContext.fail(x.cause());
      } else {
        x.result().close();
        vertx.<Void>executeBlocking(future -> {
          Flyway flyway = new Flyway();
          flyway.setDataSource(JDBC_URL, JDBC_USER, JDBC_PASSWORD);
          flyway.clean();
          flyway.migrate();
          future.complete();
        }, ar -> {
          if (ar.succeeded()) {
            routingContext.response().end("OK");
          } else {
            routingContext.fail(ar.cause());
          }
        });
      }
    });
  }

  // For local testing only
  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    vertx.deployVerticle(new MyMainVerticle());
  }
}
