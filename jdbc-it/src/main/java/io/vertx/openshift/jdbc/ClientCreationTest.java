package io.vertx.openshift.jdbc;

import io.agroal.api.AgroalDataSource;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.jdbc.spi.impl.AgroalCPDataSourceProvider;
import io.vertx.ext.web.RoutingContext;

import java.sql.SQLException;

import static io.vertx.openshift.jdbc.TestUtil.fail;

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
    vertx.<AgroalDataSource>executeBlocking(fut -> {
      AgroalCPDataSourceProvider provider = new AgroalCPDataSourceProvider();
      AgroalDataSource dataSource;
      try {
        dataSource = (AgroalDataSource) provider.getDataSource(config);
        fut.complete(dataSource);
      } catch (SQLException e) {
        fut.fail(e);
      }
    }, iniRes -> {
      if (iniRes.failed()) {
        fail(rc, iniRes.cause());
        return;
      }

      AgroalDataSource dataSource = iniRes.result();
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
