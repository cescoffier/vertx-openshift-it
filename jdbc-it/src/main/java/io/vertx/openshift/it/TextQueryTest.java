package io.vertx.openshift.it;

import io.vertx.core.Handler;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.RoutingContext;

import java.util.Collections;

import static io.vertx.openshift.it.TestUtil.*;

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
      if (ar.failed()) {
        fail(rc, ar.cause());
        return;
      }

      SQLConnection connection = ar.result();
      rc.response().bodyEndHandler(v -> {
        connection.close();
      });

      connection.query("select 24/2 as test_column", res -> {
        if (res.failed()) {
          fail(rc, res.cause());
          return;
        }

        ResultSet resultSet = res.result();
        if (!resultSet.getColumnNames().equals(Collections.singletonList("test_column"))) {
          fail(rc, String.join(",", resultSet.getColumnNames()));
        } else if (resultSet.getResults().size() != 1) {
          fail(rc, String.valueOf(resultSet.getResults().size()));
        } else if (!resultSet.getResults().get(0).getInteger(0).equals(12)) {
          fail(rc, String.valueOf(resultSet.getResults().get(0).getInteger(0)));
        } else if (resultSet.getNumRows() != 1) {
          fail(rc, String.valueOf(resultSet.getNumRows()));
        } else {
          rc.response().setStatusCode(200).end();
        }
      });
    });
  }

}
