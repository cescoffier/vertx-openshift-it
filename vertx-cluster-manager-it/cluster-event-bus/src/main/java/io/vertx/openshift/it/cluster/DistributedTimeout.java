package io.vertx.openshift.it.cluster;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.web.RoutingContext;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Thomas Segismont
 */
public class DistributedTimeout implements TestHandler {

  public static Future<DistributedTimeout> createDistributedTimeout(Vertx vertx) {
    Future<Void> future = Future.future();
    DistributedTimeout distributedTimeout = new DistributedTimeout(vertx, DistributedTimeout.class.getName());
    distributedTimeout.setup(future);
    return future.map(distributedTimeout);
  }

  private final VertxInternal vertx;
  private final String address;
  private final ClusterManager clusterManager;

  DistributedTimeout(Vertx vertx, String address) {
    this.vertx = (VertxInternal) vertx;
    this.address = address;
    clusterManager = this.vertx.getClusterManager();
  }

  void setup(Handler<AsyncResult<Void>> handler) {
    EventBus eventBus = vertx.eventBus();
    MessageConsumer<Boolean> consumer = createMessageConsumer(eventBus, address);
    consumer.handler(msg -> {
      Boolean reply = msg.body();
      if (reply) {
        msg.reply(clusterManager.getNodeID());
      }
    }).completionHandler(ar -> {
      if (ar.succeeded()) {
        handler.handle(Future.succeededFuture());
      } else {
        handler.handle(Future.failedFuture(ar.cause()));
      }
    });
  }

  MessageConsumer<Boolean> createMessageConsumer(EventBus eventBus, String address) {
    return eventBus.consumer(address);
  }

  @Override
  public void handle(RoutingContext rc) {
    int loops = Integer.parseInt(rc.pathParam("loops"));
    EventBus eventBus = vertx.eventBus();
    AtomicInteger timeouts = new AtomicInteger();
    for (int i = 0; i < loops; i++) {
      boolean sendReply = i % 2 == 0;
      eventBus.<String>send(address, sendReply, new DeliveryOptions().setSendTimeout(1000), ar -> {
        if (ar.failed()) {
          if (ar.cause() instanceof ReplyException) {
            ReplyException replyException = (ReplyException) ar.cause();
            if (replyException.failureType() == ReplyFailure.TIMEOUT) {
              timeouts.incrementAndGet();
            }
          }
        }
      });
    }
    vertx.setTimer(2000, l -> {
      rc.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json").end(String.valueOf(timeouts.get()));
    });
  }

  @Override
  public String path() {
    return "/dist-timeout/:loops";
  }
}
