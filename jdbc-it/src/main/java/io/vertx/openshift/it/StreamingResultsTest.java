package io.vertx.openshift.it;

import io.vertx.core.Handler;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.parsetools.RecordParser;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.SQLRowStream;
import io.vertx.ext.web.RoutingContext;

import java.util.ArrayList;
import java.util.List;

import static io.vertx.openshift.it.TestUtil.*;
import static java.util.stream.Collectors.*;

/**
 * @author Thomas Segismont
 */
public class StreamingResultsTest implements Handler<RoutingContext> {

  private final JDBCClient jdbcClient;

  public StreamingResultsTest(JDBCClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  public String getPath() {
    return "/streaming_results";
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

      FileSystem fileSystem = rc.vertx().fileSystem();
      fileSystem.open("db/migration/strings", new OpenOptions(), ores -> {
        if (ores.failed()) {
          fail(rc, ores.cause());
          return;
        }

        AsyncFile asyncFile = ores.result();
        List<String> values = new ArrayList<>();
        RecordParser parser = RecordParser.newDelimited("\n", out -> values.add(out.toString()));
        asyncFile.handler(parser);
        asyncFile.endHandler(v -> {
          asyncFile.close();

          List<String> statements = values.stream()
            .map(value -> "insert into random_string (value) values ('" + value + "')")
            .collect(toList());
          connection.batch(statements, ires -> {
            if (ires.failed()) {
              fail(rc, ires.cause());
              return;
            }

            connection.queryStream("select value from random_string", sres -> {
              if (sres.failed()) {
                fail(rc, sres.cause());
                return;
              }

              List<String> storedValues = new ArrayList<>(values.size());
              SQLRowStream rowStream = sres.result();
              rowStream.handler(row -> {
                storedValues.add(row.getString(0));
              }).endHandler(endRows -> {
                if (!storedValues.equals(values)) {
                  fail(rc, storedValues.toString());
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
