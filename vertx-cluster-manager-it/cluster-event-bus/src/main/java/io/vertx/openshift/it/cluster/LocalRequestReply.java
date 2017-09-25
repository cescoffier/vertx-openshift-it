package io.vertx.openshift.it.cluster;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;

/**
 * @author Thomas Segismont
 */
public class LocalRequestReply extends DistributedPeerToPeer {

  public static Future<LocalRequestReply> createLocalRequestReply(Vertx vertx) {
    Future<Void> future = Future.future();
    LocalRequestReply localRequestReply = new LocalRequestReply(vertx, LocalRequestReply.class.getName());
    localRequestReply.setup(future);
    return future.map(localRequestReply);
  }

  private LocalRequestReply(Vertx vertx, String address) {
    super(vertx, address);
  }

  @Override
  MessageConsumer<String> createMessageConsumer(EventBus eventBus, String address) {
    return eventBus.localConsumer(address);
  }

  @Override
  public String path() {
    return "/local-request-reply/:loops";
  }
}
