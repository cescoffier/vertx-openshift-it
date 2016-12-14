package io.vertx.openshift.it;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.RoutingContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

import static io.vertx.openshift.it.TestUtil.*;
import static java.util.stream.Collectors.*;

/**
 * @author Thomas Segismont
 */
public class StoredProcedureTest implements Handler<RoutingContext> {

  private final JDBCClient jdbcClient;

  public StoredProcedureTest(JDBCClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  public String getPath() {
    return "/stored_procedure";
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

      String statsFunc = "{ call animal_stats(?, ?, ?) }";
      connection.callWithParams(statsFunc, new JsonArray().add(false), new JsonArray().addNull().add("BIGINT").add("REAL"), sres -> {
        if (sres.failed()) {
          fail(rc, sres.cause());
          return;
        }

        ResultSet statsResult = sres.result();
        JsonArray output = statsResult.getOutput();
        if (output == null) {
          fail(rc, "output is null");
          return;
        }
        if (output.size() != 3) {
          fail(rc, output.toString());
          return;
        }
        if (output.getValue(0) != null) {
          fail(rc, output.toString());
          return;
        }
        if (output.getLong(1) != 3) {
          fail(rc, output.toString());
          return;
        }
        BigDecimal bigDecimal = new BigDecimal(output.getDouble(2)).setScale(2, RoundingMode.HALF_UP);
        if (!bigDecimal.equals(new BigDecimal("33.33"))) {
          fail(rc, output.toString());
          return;
        }

        String loadFunc = "{ call load_animals(?) }";
        connection.callWithParams(loadFunc, new JsonArray().add(true), new JsonArray(), lres -> {
          if (lres.failed()) {
            fail(rc, lres.cause());
            return;
          }

          ResultSet loadResult = lres.result();
          List<JsonArray> results = loadResult.getResults();
          if (results.size() != 2) {
            fail(rc, results.toString());
            return;
          }
          if (!loadResult.getColumnNames().equals(Arrays.asList("id", "name"))) {
            fail(rc, results.toString());
            return;
          }
          List<String> names = results.stream().map(array -> array.getString(1)).collect(toList());
          if (!names.equals(Arrays.asList("dog", "cat"))) {
            fail(rc, results.toString());
            return;
          }
          rc.response().setStatusCode(200).end();
        });
      });
    });
  }

}
