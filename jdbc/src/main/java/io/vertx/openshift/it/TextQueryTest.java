package io.vertx.openshift.it;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.RoutingContext;

import java.io.PrintWriter;
import java.io.StringWriter;
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
        fail(rc, ar.cause());
        return;
      }

      SQLConnection connection = ar.result();
      connection.query("select 24/2 as test_column", res -> {
        if (!ar.succeeded()) {
          fail(rc, ar.cause());
          return;
        }

        rc.response().bodyEndHandler(v -> {
          connection.close();
        });

        if (res.succeeded()) {
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
        }
      });
    });
  }

  private static void fail(RoutingContext rc, String msg) {
    fail(rc, new AssertionError(msg));
  }

  private static void fail(RoutingContext rc, Throwable t) {
    rc.response().setStatusCode(500).putHeader(HttpHeaders.CONTENT_TYPE, "text/plain").end(throwableToString(t));
  }

  private static String throwableToString(Throwable t) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    t.printStackTrace(pw);
    pw.flush();
    pw.close();
    return sw.toString();
  }
}
