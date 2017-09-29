package io.vertx.openshift.it;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class ConfigurableHttpVerticle extends AbstractVerticle {

  private ConfigRetriever retriever;
  private JsonObject configByListen;
  private JsonObject configByStream;

  @Override
  public void start() throws Exception {
    retriever = initializeConfig();

    retriever.getConfig(res -> {
      if (res.failed()) {
        throw new RuntimeException("Unable to retrieve the Config", res.cause());
      } else {
        configByListen = configByStream = (res.result() != null) ? res.result() : new JsonObject();

        Router router = Router.router(vertx);
        router.get("/all").handler(this::printAll);
        router.get("/all-by-stream").handler(this::printConfigByStream);
        router.get("/*").handler(StaticHandler.create("configuration"));

        retriever.listen(change -> {
          configByListen = change.getNewConfiguration();
          System.out.println("A new config by listening to change has been used");
        });

        retriever.configStream()
          .endHandler(event -> {
            System.out.println("A config stream has been closed.");
          })
          .exceptionHandler(event -> {
            System.err.println("An error has occurred: " + event.getMessage());
            event.printStackTrace();
          })
          .handler(conf -> {
            configByStream = conf;
            System.out.println("A new config from stream has been used");
          });

        vertx.createHttpServer()
          .requestHandler(router::accept)
          .listen(8080, ar -> {
            if (ar.succeeded()) {
              System.out.println("Server listening on port " + ar.result().actualPort());
            } else {
              System.out.println("Unable to start the server: " + ar.cause().getMessage());
            }
          });
      }
    });
  }

  @Override
  public void stop() throws Exception {
    retriever.close();
  }

  private void printAll(RoutingContext rc) {
    rc.response()
      .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
      .end(configByListen.copy().put("trace", "1").encodePrettily());
  }

  private void printConfigByStream(RoutingContext rc) {
    rc.response()
      .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
      .end(configByStream.copy().put("trace", "1").encodePrettily());
  }

  private ConfigRetriever initializeConfig() {
    ConfigStoreOptions cm = new ConfigStoreOptions()
      .setType("configmap")
      .setConfig(new JsonObject()
        .put("name", "my-config-map")
//        .put("optional", false)
      );

    ConfigStoreOptions httpStore = new ConfigStoreOptions()
      .setType("http")
      .setConfig(new JsonObject()
        .put("host", "http-service").put("port", 80).put("path", "/conf"));

    ConfigStoreOptions propertiesFile = new ConfigStoreOptions()
      .setType("file")
      .setFormat("properties")
      .setConfig(new JsonObject()
        .put("path", "configuration/my-config.properties")
      );

    ConfigStoreOptions jsonFile = new ConfigStoreOptions()
      .setType("file")
      .setFormat("json")
      .setConfig(new JsonObject()
        .put("path", "configuration/my-config.json")
      );

    ConfigStoreOptions ymlFile = new ConfigStoreOptions()
      .setType("file")
      .setFormat("yaml")
      .setConfig(new JsonObject()
        .put("path", "configuration/my-config.yml")
      );

    ConfigStoreOptions eb = new ConfigStoreOptions()
      .setType("event-bus")
      .setConfig(new JsonObject()
        .put("address", "event-bus-config")
      );

    ConfigStoreOptions dir = new ConfigStoreOptions()
      .setType("directory")
      .setConfig(new JsonObject().put("path", "configuration/config")
        .put("filesets", new JsonArray()
          .add(new JsonObject().put("pattern", "*.properties")
            .put("format", "properties"))
        ));

    ConfigStoreOptions sys = new ConfigStoreOptions().setType("sys");
    ConfigStoreOptions env = new ConfigStoreOptions().setType("env");

    return ConfigRetriever.create(vertx,
      new ConfigRetrieverOptions()
        .addStore(cm)
        .addStore(sys)
        .addStore(env)
        .addStore(httpStore)
        .addStore(propertiesFile)
        .addStore(eb)
        .addStore(dir)
        .addStore(jsonFile)
        .addStore(ymlFile)
        .setScanPeriod(10000)
    );
  }
}
