package io.vertx.openshift.it;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.RoutingContext;

import java.util.Arrays;
import java.util.Random;

import static io.vertx.openshift.it.TestUtil.*;

/**
 * @author Thomas Segismont
 */
public class BinaryTest implements Handler<RoutingContext> {

  private final JDBCClient jdbcClient;

  public BinaryTest(JDBCClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  public String getPath() {
    return "/binary";
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

      String imageName = "random image";
      byte[] imageContent = new byte[1024];
      new Random().nextBytes(imageContent);

      String sqlInsert = "insert into image (name,content) values (?, decode(?, 'base64'))";
      JsonArray paramsInsert = new JsonArray()
        .add(imageName)
        .add(imageContent);
      connection.updateWithParams(sqlInsert, paramsInsert, ires -> {
        if (!ires.succeeded()) {
          fail(rc, ires.cause());
          return;
        }

        Long imageId = ires.result().getKeys().getLong(0);

        String sqlSelect = "select name, content from image where id = ?";
        connection.queryWithParams(sqlSelect, new JsonArray().add(imageId), sres -> {
          if (!sres.succeeded()) {
            fail(rc, sres.cause());
            return;
          }

          ResultSet resultSet = sres.result();
          JsonArray row = resultSet.getResults().get(0);
          if (!row.getString(0).equals(imageName)) {
            fail(rc, String.valueOf(row.getString(0)));
          } else if (!Arrays.equals(imageContent, row.getBinary(1))) {
            fail(rc, "Binary content differs");
          } else {
            rc.response().setStatusCode(200).end();
          }
        });
      });
    });
  }

}
