package io.vertx.openshift.it;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.configuration.ConfigurationRetriever;
import io.vertx.ext.configuration.ConfigurationRetrieverOptions;
import io.vertx.ext.configuration.ConfigurationStoreOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class ConfigurableHttpVerticle extends AbstractVerticle {

  private ConfigurationRetriever retriever;
  private JsonObject configuration;

  @Override
  public void start() throws Exception {
    retriever = initializeConfiguration();

    retriever.getConfigurationFuture()
        .setHandler(res -> {
          if (res.failed()) {
            throw new RuntimeException("Unable to retrieve the configuration", res.cause());
          }

          configuration = res.result();
          if (configuration == null) {
            configuration = new JsonObject();
          }
          retriever.listen(change -> {
            configuration = change.getNewConfiguration();
            System.out.println("New configuration:\n" + configuration.encodePrettily());
          });


          Router router = Router.router(vertx);
          router.get("/all").handler(this::printAll);

          vertx.createHttpServer()
              .requestHandler(router::accept)
              .listen(8080, ar -> {
                if (ar.succeeded()) {
                  System.out.println("Server listening on port " + ar.result().actualPort());
                } else {
                  System.out.println("Unable to start the server: " + ar.cause().getMessage());
                }
              });
        });
  }

  @Override
  public void stop() throws Exception {
    retriever.close();
  }

  private void printAll(RoutingContext rc) {
    rc.response()
        .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
        .end(configuration.copy().put("trace", "1").encodePrettily());
  }

  private ConfigurationRetriever initializeConfiguration() {
    ConfigurationStoreOptions cm = new ConfigurationStoreOptions()
        .setType("configmap")
        .setConfig(new JsonObject()
            .put("name", "my-config-map")
        );

    //TODO Need to find a way to give visibility to secrets.

//    ConfigurationStoreOptions secret = new ConfigurationStoreOptions()
//        .setType("configmap")
//        .setConfig(new JsonObject()
//            .put("name", "my-secret")
//            .put("secret", true)
//        );

    ConfigurationStoreOptions sys = new ConfigurationStoreOptions().setType("sys");
    ConfigurationStoreOptions env = new ConfigurationStoreOptions().setType("env");

    return ConfigurationRetriever.create(vertx,
        new ConfigurationRetrieverOptions()
            .addStore(cm)
            .addStore(sys)
            .addStore(env)
//            .addStore(secret)
    );
  }
}
