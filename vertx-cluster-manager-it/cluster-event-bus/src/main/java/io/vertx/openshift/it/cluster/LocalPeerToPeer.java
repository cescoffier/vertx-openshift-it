package io.vertx.openshift.it.cluster;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;

/**
 * @author Thomas Segismont
 */
public class LocalPeerToPeer extends DistributedPeerToPeer {

  public static Future<LocalPeerToPeer> createLocalPeerToPeer(Vertx vertx) {
    Future<Void> future = Future.future();
    LocalPeerToPeer localPeerToPeer = new LocalPeerToPeer(vertx, LocalPeerToPeer.class.getName());
    localPeerToPeer.setup(future);
    return future.map(localPeerToPeer);
  }

  private LocalPeerToPeer(Vertx vertx, String address) {
    super(vertx, address);
  }

  @Override
  MessageConsumer<String> createMessageConsumer(EventBus eventBus, String address) {
    return eventBus.localConsumer(address);
  }

  @Override
  public String path() {
    return "/local-p2p/:loops";
  }
}
