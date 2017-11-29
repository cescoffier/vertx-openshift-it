package io.vertx.openshift.jdbc;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.RoutingContext;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static io.vertx.openshift.jdbc.TestUtil.*;

/**
 * @author Thomas Segismont
 */
public class SpecialDatatypesTest implements Handler<RoutingContext> {

  private final JDBCClient jdbcClient;

  public SpecialDatatypesTest(JDBCClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  public String getPath() {
    return "/special_datatypes";
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

      ZoneId zoneId = ZoneId.of("Europe/Paris");
      ZonedDateTime zonedDateTime = ZonedDateTime.of(1980, 10, 16, 5, 28, 33, 0, zoneId);
      Instant instant = zonedDateTime.toInstant();
      String uuid = UUID.randomUUID().toString();

      String sqlInsert = "insert into item (cid,name,created_on,created_at,created) values (?,?,?,?,?)";
      JsonArray paramsInsert = new JsonArray()
        .add(uuid)
        .add("My special item")
        .add(zonedDateTime.toLocalDate().toString())
        .add(zonedDateTime.toLocalTime().toString())
        .add(instant);
      connection.updateWithParams(sqlInsert, paramsInsert, ires -> {
        if (ires.failed()) {
          fail(rc, ires.cause());
          return;
        }

        String sqlSelect = "select cid,created_on,created_at,created from item where cid = ?";
        connection.queryWithParams(sqlSelect, new JsonArray().add(uuid), sres -> {
          if (sres.failed()) {
            fail(rc, sres.cause());
            return;
          }

          ResultSet resultSet = sres.result();
          JsonArray row = resultSet.getResults().get(0);
          if (!row.getString(0).equals(uuid)) {
            fail(rc, String.valueOf(row.getString(0)));
          } else if (!row.getString(1).equals("1980-10-16")) {
            fail(rc, String.valueOf(row.getString(1)));
          } else if (!row.getString(2).equals("05:28:33")) {
            fail(rc, String.valueOf(row.getString(1)));
          } else if (!row.getInstant(3).equals(instant)) {
            fail(rc, String.valueOf(row.getInstant(3)));
          } else {
            rc.response().setStatusCode(200).end();
          }
        });
      });
    });
  }

}
