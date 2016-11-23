package io.vertx.openshift.it;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.web.Router;
import org.flywaydb.core.Flyway;

/**
 * @author Thomas Segismont
 */
public class MainVerticle extends AbstractVerticle {

  public static final String JDBC_URL = System.getenv().getOrDefault("JDBC_URL", "jdbc:postgresql:testdb");
  public static final String JDBC_USER = System.getenv().getOrDefault("JDBC_USER", "vertx");
  public static final String JDBC_PASSWORD = System.getenv().getOrDefault("JDBC_PASSWORD", "password");

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    vertx.<Void>executeBlocking(future -> {
      Flyway flyway = new Flyway();
      flyway.setDataSource(JDBC_URL, JDBC_USER, JDBC_PASSWORD);
      flyway.clean();
      flyway.migrate();
      future.complete();
    }, ar -> {
      if (ar.succeeded()) {
        setupTests(startFuture);
      } else {
        startFuture.fail(ar.cause());
      }
    });
  }

  private void setupTests(Future<Void> startFuture) {
    JsonObject config = new JsonObject()
      .put("url", JDBC_URL)
      .put("driver_class", "org.postgresql.Driver")
      .put("user", JDBC_USER)
      .put("password", JDBC_PASSWORD)
      .put("max_pool_size", 30);
    JDBCClient jdbcClient = JDBCClient.createNonShared(vertx, config);

    Router router = Router.router(vertx);

    TextQueryTest textQueryTest = new TextQueryTest(jdbcClient);
    router.route(textQueryTest.getPath()).handler(textQueryTest);

    QueryWithParamsTest queryWithParamsTest = new QueryWithParamsTest(jdbcClient);
    router.route(queryWithParamsTest.getPath()).handler(queryWithParamsTest);

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

  // For local testing only
  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    vertx.deployVerticle(new MainVerticle());
  }
}
