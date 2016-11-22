package io.vertx.openshift.it;

import io.vertx.core.Handler;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.RoutingContext;

import java.util.Collections;

/**
 * @author Thomas Segismont
 */
public class TextQueryTest implements Handler<RoutingContext> {

  private final JDBCClient jdbcClient;

  public TextQueryTest(JDBCClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  public String getPath() {
    return "/text_query";
  }

  @Override
  public void handle(RoutingContext rc) {
    jdbcClient.getConnection(ar -> {
      if (!ar.succeeded()) {
        fail(rc, "No connection");
        return;
      }

      SQLConnection connection = ar.result();
      connection.query("select 24/2 as test_column", res -> {
        if (res.succeeded()) {
          ResultSet resultSet = res.result();
          if (resultSet.getColumnNames().equals(Collections.singletonList("test_column"))
            && resultSet.getNumRows() == 1
            && resultSet.getResults().size() == 1
            && resultSet.getResults().get(0).getInteger(0).equals(12)) {
            rc.response().setStatusCode(200).end();
          } else {
            fail(rc, "Not good");
          }

        } else {
          fail(rc, res.cause());
        }
      });

      rc.response().bodyEndHandler(v -> {
        connection.close();
      });
    });
  }

  private void fail(RoutingContext rc, String msg) {
    rc.response().setStatusCode(500).end(msg);
  }

  private void fail(RoutingContext rc, Throwable t) {
    rc.response().setStatusCode(500).end(t.getMessage());
  }
}
