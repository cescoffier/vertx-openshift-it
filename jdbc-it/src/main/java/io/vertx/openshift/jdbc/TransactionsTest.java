package io.vertx.openshift.jdbc;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.RoutingContext;

/**
 * @author Thomas Segismont
 */
public class TransactionsTest implements Handler<RoutingContext> {

  private final JDBCClient jdbcClient;

  public TransactionsTest(JDBCClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  public String getPath() {
    return "/transactions";
  }

  @Override
  public void handle(RoutingContext rc) {
    jdbcClient.getConnection(ar -> {
      if (ar.failed()) {
        TestUtil.fail(rc, ar.cause());
        return;
      }

      SQLConnection connection = ar.result();
      rc.response().bodyEndHandler(v -> {
        connection.close();
      });

      connection.setAutoCommit(false, acres -> {
        if (acres.failed()) {
          TestUtil.fail(rc, acres.cause());
          return;
        }

        connection.updateWithParams("delete from cake where name = ?", new JsonArray().add("black forest"), dres -> {
          if (dres.failed()) {
            TestUtil.fail(rc, dres.cause());
            return;
          }

          connection.query("select count(*) as count from cake", cres -> {
            if (cres.failed()) {
              TestUtil.fail(rc, cres.cause());
              return;
            }

            if (cres.result().getResults().get(0).getInteger(0) != 2) {
              TestUtil.fail(rc, String.valueOf(cres.result().getResults()));
            }

            connection.rollback(rres -> {
              if (rres.failed()) {
                TestUtil.fail(rc, rres.cause());
                return;
              }

              connection.query("select count(*) as count from cake", cres2 -> {
                if (!cres2.succeeded()) {
                  TestUtil.fail(rc, cres2.cause());
                  return;
                }

                if (cres2.result().getResults().get(0).getInteger(0) != 3) {
                  TestUtil.fail(rc, String.valueOf(cres2.result().getResults()));
                } else {
                  rc.response().setStatusCode(200).end();
                }
              });
            });
          });
        });
      });
    });
  }
}
