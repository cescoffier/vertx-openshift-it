package io.vertx.openshift.it;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.RoutingContext;

import static io.vertx.openshift.it.TestUtil.*;

/**
 * @author Thomas Segismont
 */
public class DDLTest implements Handler<RoutingContext> {

  private final JDBCClient jdbcClient;

  public DDLTest(JDBCClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  public String getPath() {
    return "/ddl";
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

      String sql = "alter table definition add constraint name_unique unique (name)";
      connection.execute(sql, execute -> {
        if (execute.failed()) {
          fail(rc, execute.cause());
          return;
        }

        connection.updateWithParams("insert into definition (name) values (?)", new JsonArray().add("def"), ires -> {
          if (ires.failed()) {
            fail(rc, ires.cause());
            return;
          }

          connection.updateWithParams("insert into definition (name) values (?)", new JsonArray().add("def"), ires2 -> {
            if (ires2.succeeded()) {
              fail(rc, "Unique constraint was not applied");
            } else {
              rc.response().setStatusCode(200).end();
            }
          });
        });
      });
    });
  }
}
