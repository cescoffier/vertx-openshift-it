package io.vertx.openshift.it;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.UpdateResult;
import io.vertx.ext.web.RoutingContext;

import java.util.Collections;

import static io.vertx.openshift.it.TestUtil.*;

/**
 * @author Thomas Segismont
 */
public class CRUDTest implements Handler<RoutingContext> {

  private final JDBCClient jdbcClient;

  public CRUDTest(JDBCClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  public String getPath() {
    return "/crud";
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

      String calypso = "Calypso";
      connection.updateWithParams("insert into ship (name) values (?)", new JsonArray().add(calypso), ires -> {
        if (!ires.succeeded()) {
          fail(rc, ires.cause());
          return;
        }

        UpdateResult insertResult = ires.result();
        if (insertResult.getUpdated() != 1) {
          fail(rc, String.valueOf(insertResult.getUpdated()));
          return;
        }

        Long calypsoId = insertResult.getKeys().getLong(0);
        connection.queryWithParams("select name from ship where id = ?", new JsonArray().add(calypsoId), sres -> {
          if (!sres.succeeded()) {
            fail(rc, sres.cause());
            return;
          }

          ResultSet resultSet = sres.result();
          if (!resultSet.getColumnNames().equals(Collections.singletonList("name"))) {
            fail(rc, String.join(",", resultSet.getColumnNames()));
            return;
          } else if (resultSet.getResults().size() != 1) {
            fail(rc, String.valueOf(resultSet.getResults().size()));
            return;
          } else if (!resultSet.getResults().get(0).getString(0).equals(calypso)) {
            fail(rc, resultSet.getResults().get(0).getString(0));
            return;
          } else if (resultSet.getNumRows() != 1) {
            fail(rc, String.valueOf(resultSet.getNumRows()));
            return;
          }

          connection.updateWithParams("update ship set name = ? where id = ?", new JsonArray().add("Alcyone").add(calypsoId), ures -> {
            if (!ures.succeeded()) {
              fail(rc, ures.cause());
              return;
            }

            UpdateResult updateResult = ures.result();
            if (updateResult.getUpdated() != 1) {
              fail(rc, String.valueOf(updateResult.getUpdated()));
              return;
            }

            connection.updateWithParams("delete from ship where id = ?", new JsonArray().add(calypsoId), dres -> {
              if (!dres.succeeded()) {
                fail(rc, dres.cause());
                return;
              }

              UpdateResult deleteResult = dres.result();
              if (deleteResult.getUpdated() != 1) {
                fail(rc, String.valueOf(deleteResult.getUpdated()));
              } else {
                rc.response().setStatusCode(200).end();
              }
            });
          });
        });
      });
    });
  }

}
