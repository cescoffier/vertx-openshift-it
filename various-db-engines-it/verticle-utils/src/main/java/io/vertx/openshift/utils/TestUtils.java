package io.vertx.openshift.utils;

import io.vertx.core.json.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * This class serves as a container for various database utilities (static methods)
 *
 * @author Martin Spisiak (mspisiak@redhat.com) on 18/10/17.
 */
public class TestUtils {
  /**
   * Red Hat-specific database allocator URL, you need to provide a valid URL here,
   * otherwise the app won't be able to connect to the database.
   */
  private static final String DB_ALLOCATOR_URL =
    System.getenv().getOrDefault("dbAllocatorUrl","");

  /**
   * This method serves as internal or external database allocator (depends on @param external value)
   *
   * @param database database engine name (one of the following [oracle, postgresql, mysql])
   * @param external specifies whether to return a database configuration for external database or internal
   * @return JsonObject database connection configuration
   */
  public static JsonObject allocateDatabase(String database, boolean external) {
    if (external) {
      if (Objects.equals(DB_ALLOCATOR_URL, ""))
        throw new IllegalArgumentException("dbAllocatorUrl env value has to be specified !");
      return allocateExternalDatabase(database);
    } else {
      return allocateInternalDatabase(database);
    }
  }

  private static JsonObject allocateExternalDatabase(String database) {
    Properties connectionParams = new Properties();

    try {
      InputStream inputStream = new URL(DB_ALLOCATOR_URL + createAllocatorUrl(database)).openStream();
      connectionParams.load(inputStream);
    } catch (IOException e) {
      e.printStackTrace();
    }

    return new JsonObject()
      .put("url", connectionParams.getProperty("db.jdbc_url"))
      .put("user", connectionParams.getProperty("db.username"))
      .put("driver_class", connectionParams.getProperty("db.jdbc_class"))
      .put("password", connectionParams.getProperty("db.password"));
  }

  private static JsonObject allocateInternalDatabase(String database) {
    String jdbcURL, jdbcDriver, jdbcUser, jdbcPassword;

    switch (database) {
      case "postgresql":
        jdbcURL = "jdbc:postgresql://db/testdb";
        jdbcDriver = "org.postgresql.Driver";
        jdbcUser = "vertx";
        jdbcPassword = "password";
        break;
      case "mysql":
        jdbcURL = "jdbc:mysql://db/testdb";
        jdbcDriver = "com.mysql.jdbc.Driver";
        jdbcUser = "vertx";
        jdbcPassword = "password";
        break;
      default:
        throw new IllegalArgumentException(database + " is not a valid argument, needs to be one of [postgresql, mysql]");
    }

    return new JsonObject()
      .put("url", jdbcURL)
      .put("driver_class", jdbcDriver)
      .put("user", jdbcUser)
      .put("password", jdbcPassword);
  }

  private static String createAllocatorUrl(String db) {
    StringBuilder stringBuilder = new StringBuilder("?operation=allocate");
    Map<String, String> dbAllocatorParams = new HashMap<>();
    switch (db) {
      case "oracle":
        dbAllocatorParams.put("expression", "oracle12c");
        break;
      case "postgresql":
        dbAllocatorParams.put("expression", "postgresql96");
        break;
      case "mysql":
        dbAllocatorParams.put("expression", "mysql57");
        break;
      default:
        throw new IllegalArgumentException(db + " is not a valid argument, needs to be one of [oracle, postgresql, mysql]");
    }
    dbAllocatorParams.put("requestee", "xPaas-test");
    dbAllocatorParams.put("expiry", "60");

    for (Map.Entry entry : dbAllocatorParams.entrySet()) {
      stringBuilder.append("&").append(entry.getKey()).append("=").append(entry.getValue());
    }

    return stringBuilder.toString();
  }
}
