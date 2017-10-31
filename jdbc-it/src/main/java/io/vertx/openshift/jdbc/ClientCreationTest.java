package io.vertx.openshift.jdbc;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.web.RoutingContext;
import org.postgresql.ds.PGPoolingDataSource;

import java.sql.SQLException;

import static io.vertx.openshift.jdbc.TestUtil.*;

/**
 * @author Thomas Segismont
 */
public class ClientCreationTest implements Handler<RoutingContext> {

  private final Vertx vertx;
  private final JsonObject config;

  public ClientCreationTest(Vertx vertx, JsonObject config) {
    this.vertx = vertx;
    this.config = config;
  }

  public String getPath() {
    return "/client_creation";
  }

  @Override
  public void handle(RoutingContext rc) {
    vertx.<PGPoolingDataSource>executeBlocking(fut -> {
      PGPoolingDataSource dataSource = new PGPoolingDataSource();
      dataSource.setUrl(config.getString("url"));
      dataSource.setUser(config.getString("user"));
      dataSource.setPassword(config.getString("password"));
      try {
        dataSource.initialize();
        fut.complete(dataSource);
      } catch (SQLException e) {
        fut.fail(e);
      }
    }, iniRes -> {
      if (iniRes.failed()) {
        fail(rc, iniRes.cause());
        return;
      }

      PGPoolingDataSource dataSource = iniRes.result();
      rc.addBodyEndHandler(v -> vertx.executeBlocking(fut -> {
        dataSource.close();
        fut.complete();
      }, null));

      JDBCClient jdbcClient = JDBCClient.create(vertx, dataSource);
      rc.addBodyEndHandler(v -> jdbcClient.close());

      jdbcClient.getConnection(ar -> {
        if (ar.failed()) {
          fail(rc, ar.cause());
          return;
        }

        ar.result().close();
        rc.response().setStatusCode(200).end();
      });
    });
  }

}
