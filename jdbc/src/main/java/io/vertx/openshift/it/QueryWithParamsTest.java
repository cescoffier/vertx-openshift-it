package io.vertx.openshift.it;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.RoutingContext;

import java.util.Collections;

import static io.vertx.openshift.it.TestUtil.*;

/**
 * @author Thomas Segismont
 */
public class QueryWithParamsTest implements Handler<RoutingContext> {

  private final JDBCClient jdbcClient;

  public QueryWithParamsTest(JDBCClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  public String getPath() {
    return "/query_with_params";
  }

  @Override
  public void handle(RoutingContext rc) {
    jdbcClient.getConnection(ar -> {
      if (!ar.succeeded()) {
        fail(rc, ar.cause());
        return;
      }

      SQLConnection connection = ar.result();
      rc.response().bodyEndHandler(v -> {
        connection.close();
      });

      connection.queryWithParams("select name from person where id=?", new JsonArray().add(2), res -> {
        if (!res.succeeded()) {
          fail(rc, res.cause());
          return;
        }

        ResultSet resultSet = res.result();
        if (!resultSet.getColumnNames().equals(Collections.singletonList("name"))) {
          fail(rc, String.join(",", resultSet.getColumnNames()));
        } else if (resultSet.getResults().size() != 1) {
          fail(rc, String.valueOf(resultSet.getResults().size()));
        } else if (!resultSet.getResults().get(0).getString(0).equals("titi")) {
          fail(rc, resultSet.getResults().get(0).getString(0));
        } else if (resultSet.getNumRows() != 1) {
          fail(rc, String.valueOf(resultSet.getNumRows()));
        } else {
          rc.response().setStatusCode(200).end();
        }
      });
    });
  }

}
