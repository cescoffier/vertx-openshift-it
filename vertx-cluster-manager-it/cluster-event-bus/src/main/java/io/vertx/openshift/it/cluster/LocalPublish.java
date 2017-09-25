package io.vertx.openshift.it.cluster;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;

/**
 * @author Thomas Segismont
 */
public class LocalPublish extends DistributedPublish {

  public static Future<LocalPublish> createLocalPublish(Vertx vertx) {
    Future<Void> future = Future.future();
    LocalPublish localPublish = new LocalPublish(vertx, LocalPublish.class.getName());
    localPublish.setup(future);
    return future.map(localPublish);
  }

  LocalPublish(Vertx vertx, String address) {
    super(vertx, address);
  }

  @Override
  MessageConsumer<String> createMessageConsumer(EventBus eventBus, String address) {
    return eventBus.localConsumer(address);
  }

  @Override
  public String path() {
    return "/local-pub/:loops";
  }
}
