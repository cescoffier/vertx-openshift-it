package io.vertx.openshift.it;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.RoutingContext;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.vertx.openshift.it.TestUtil.*;
import static java.util.stream.Collectors.*;

/**
 * @author Thomas Segismont
 */
public class BatchUpdatesTest implements Handler<RoutingContext> {

  private final JDBCClient jdbcClient;

  public BatchUpdatesTest(JDBCClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  public String getPath() {
    return "/batch_updates";
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

      Map<String, Integer> cityMembers = new HashMap<>();
      cityMembers.put("marseille", 2);
      cityMembers.put("amsterdam", 1);
      cityMembers.put("grenoble", 1);
      cityMembers.put("lyon", 1);

      List<String> inserts = cityMembers.keySet().stream()
        .map(name -> "insert into city (name, members_count) values('" + name + "', 0)")
        .collect(toList());
      connection.batch(inserts, ires -> {
        if (!ires.succeeded()) {
          fail(rc, ires.cause());
          return;
        }

        List<Integer> insertCounts = ires.result();
        if (!insertCounts.equals(Arrays.asList(1, 1, 1, 1))) {
          fail(rc, insertCounts.toString());
          return;
        }

        List<JsonArray> args = cityMembers.entrySet().stream()
          .map(e -> new JsonArray().add(e.getValue()).add(e.getKey()))
          .collect(toList());
        connection.batchWithParams("update city set members_count = ? where name = ?", args, ures -> {
          if (!ures.succeeded()) {
            fail(rc, ures.cause());
            return;
          }

          List<Integer> updateCounts = ires.result();
          if (!updateCounts.equals(Arrays.asList(1, 1, 1, 1))) {
            fail(rc, updateCounts.toString());
            return;
          }
          rc.response().setStatusCode(200).end();
        });
      });
    });
  }

}
