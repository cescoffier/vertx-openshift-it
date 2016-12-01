package io.vertx.openshift.it;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.UpdateResult;
import io.vertx.ext.web.RoutingContext;

import static io.vertx.openshift.it.TestUtil.*;

/**
 * @author Thomas Segismont
 */
public class UpdateWithParamsTest implements Handler<RoutingContext> {

  private final JDBCClient jdbcClient;

  public UpdateWithParamsTest(JDBCClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  public String getPath() {
    return "/update_with_params";
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

      connection.updateWithParams("update car set name = ? where name = ?", new JsonArray().add("chick hicks").add("martin"), ures -> {
        if (!ures.succeeded()) {
          fail(rc, ures.cause());
          return;
        }

        UpdateResult updateResult = ures.result();
        if (updateResult.getUpdated() != 1) {
          fail(rc, String.valueOf(updateResult.getUpdated()));
        } else {
          rc.response().setStatusCode(200).end();
        }
      });
    });
  }

}
