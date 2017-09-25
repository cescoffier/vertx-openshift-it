package io.vertx.openshift.it.cluster;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.JsonArray;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.web.RoutingContext;

import java.util.UUID;

/**
 * @author Thomas Segismont
 */
public class DistributedPeerToPeer implements TestHandler {

  public static Future<DistributedPeerToPeer> createDistributedPeerToPeer(Vertx vertx) {
    Future<Void> future = Future.future();
    DistributedPeerToPeer distributedPeerToPeer = new DistributedPeerToPeer(vertx, DistributedPeerToPeer.class.getName());
    distributedPeerToPeer.setup(future);
    return future.map(distributedPeerToPeer);
  }

  private final VertxInternal vertx;
  private final String address;
  private final ClusterManager clusterManager;

  DistributedPeerToPeer(Vertx vertx, String address) {
    this.vertx = (VertxInternal) vertx;
    this.address = address;
    clusterManager = this.vertx.getClusterManager();
  }

  void setup(Handler<AsyncResult<Void>> handler) {
    EventBus eventBus = vertx.eventBus();
    MessageConsumer<String> consumer = createMessageConsumer(eventBus, address);
    consumer.handler(msg -> {
      eventBus.send(msg.body(), clusterManager.getNodeID());
    }).completionHandler(ar -> {
      if (ar.succeeded()) {
        handler.handle(Future.succeededFuture());
      } else {
        handler.handle(Future.failedFuture(ar.cause()));
      }
    });
  }

  MessageConsumer<String> createMessageConsumer(EventBus eventBus, String address) {
    return eventBus.consumer(address);
  }

  @Override
  public void handle(RoutingContext rc) {
    int loops = Integer.parseInt(rc.pathParam("loops"));
    EventBus eventBus = vertx.eventBus();
    String testAddress = UUID.randomUUID().toString();
    ConcurrentHashSet<String> nodes = new ConcurrentHashSet<>();
    eventBus.<String>consumer(testAddress, msg -> {
      nodes.add(msg.body());
    }).completionHandler(ar -> {
      if (ar.succeeded()) {
        for (int i = 0; i < loops; i++) {
          eventBus.send(address, testAddress);
        }
        vertx.setTimer(1000, l -> {
          Buffer result = nodes.stream()
            .collect(JsonArray::new, JsonArray::add, JsonArray::addAll)
            .toBuffer();
          rc.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json").end(result);
        });
      } else {
        rc.fail(ar.cause());
      }
    });
  }

  @Override
  public String path() {
    return "/dist-p2p/:loops";
  }
}
