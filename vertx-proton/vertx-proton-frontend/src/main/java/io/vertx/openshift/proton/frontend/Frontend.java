package io.vertx.openshift.proton.frontend;

import io.reactivex.Completable;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.proton.ProtonClient;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;
import io.vertx.reactivex.CompletableHelper;
import io.vertx.reactivex.config.ConfigRetriever;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.impl.AsyncResultCompletable;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import io.vertx.reactivex.ext.web.handler.StaticHandler;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Source;
import org.apache.qpid.proton.message.Message;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Martin Spisiak (mspisiak@redhat.com) on 27/07/18.
 */
public class Frontend extends AbstractVerticle {
  private static final Logger LOGGER = LoggerFactory.getLogger(Frontend.class);
  private static final String ID = "frontend-vertx-" + UUID.randomUUID()
    .toString().substring(0, 4);

  private ProtonSender requestSender;
  private ProtonReceiver responseReceiver;

  private final AtomicInteger requestSequence = new AtomicInteger(0);
  private final Queue<Message> requestMessages = new ConcurrentLinkedQueue<>();
  private final Data data = new Data();

  @Override
  public void start(Future<Void> future) {
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());
    router.post("/api/send-request").handler(this::handleSendRequest);
    router.get("/api/receive-response").handler(this::handleReceiveResponse);
    router.get("/api/data").handler(this::handleGetData);
    router.get("/api/health").handler(rc -> rc.response().end("OK"));
    router.get("/*").handler(StaticHandler.create());

    ConfigRetriever.create(vertx).rxGetConfig()
      .flatMapCompletable(json -> {
        String amqpHost = json.getString("MESSAGING_SERVICE_HOST", "localhost");
        int amqpPort = json.getInteger("MESSAGING_SERVICE_PORT", 5672);
        String amqpUser = json.getString("MESSAGING_SERVICE_USER", "work-queue");
        String amqpPassword = json.getString("MESSAGING_SERVICE_PASSWORD", "work-queue");

        String httpHost = json.getString("HTTP_HOST", "0.0.0.0");
        int httpPort = json.getInteger("HTTP_PORT", 8080);

        // AMQP
        ProtonClient client = ProtonClient.create(vertx.getDelegate());
        Future<Void> connected = Future.future();
        client.connect(amqpHost, amqpPort, amqpUser, amqpPassword, result -> {
          if (result.failed()) {
            connected.fail(result.cause());
          } else {
            ProtonConnection conn = result.result();
            conn.setContainer(ID);
            conn.open();

            sendRequests(conn);
            receiveWorkerUpdates(conn);
            pruneStaleWorkers();
            connected.complete();
          }
        });

        Completable brokerConnected = new AsyncResultCompletable(connected::setHandler);
        Completable serverStarted = vertx.createHttpServer()
          .requestHandler(router::accept)
          .rxListen(httpPort, httpHost)
          .toCompletable();

        return brokerConnected.andThen(serverStarted);
      })
      .subscribe(CompletableHelper.toObserver(future));
  }

  private void sendRequests(ProtonConnection conn) {
    requestSender = conn.createSender("work-queue/requests");

    // Using a null address and setting the source dynamic tells
    // the remote peer to generate the reply address.
    responseReceiver = conn.createReceiver(null);
    Source source = (Source) responseReceiver.getSource();
    source.setDynamic(true);

    responseReceiver.openHandler(result -> requestSender.sendQueueDrainHandler(s -> doSendRequests()));

    responseReceiver.handler((delivery, message) -> {
      Map props = message.getApplicationProperties().getValue();
      String workerId = (String) props.get("workerId");
      String requestId = (String) message.getCorrelationId();
      String text = (String) ((AmqpValue) message.getBody()).getValue();
      Response response = new Response(requestId, workerId, text);

      data.getResponses().put(response.getRequestId(), response);

      LOGGER.info("{0}: Received {1}", ID, response);
    });

    requestSender.open();
    responseReceiver.open();
  }

  private void doSendRequests() {
    if (responseReceiver == null) {
      return;
    }

    if (responseReceiver.getRemoteSource().getAddress() == null) {
      return;
    }

    while (!requestSender.sendQueueFull()) {
      Message message = requestMessages.poll();

      if (message == null) {
        break;
      }

      message.setReplyTo(responseReceiver.getRemoteSource().getAddress());

      requestSender.send(message);

      LOGGER.info("{0}: Sent {1}", ID, message);
    }
  }

  private void receiveWorkerUpdates(ProtonConnection conn) {
    ProtonReceiver receiver = conn.createReceiver("work-queue/worker-updates");

    receiver.handler((delivery, message) -> {
      Map props = message.getApplicationProperties().getValue();
      String workerId = (String) props.get("workerId");
      long timestamp = (long) props.get("timestamp");
      long requestsProcessed = (long) props.get("requestsProcessed");
      long processingErrors = (long) props.get("processingErrors");

      WorkerUpdate update = new WorkerUpdate(workerId, timestamp, requestsProcessed,
        processingErrors);

      data.getWorkers().put(update.getWorkerId(), update);
    });

    receiver.open();
  }

  private void handleSendRequest(RoutingContext rc) {
    String json = rc.getBodyAsString();
    String requestId = ID + "/" + requestSequence.incrementAndGet();
    Request request = Json.decodeValue(json, Request.class);
    Map<String, Object> props = new HashMap<>();
    props.put("uppercase", request.isUppercase());
    props.put("reverse", request.isReverse());

    Message message = Message.Factory.create();
    message.setMessageId(requestId);
    message.setAddress("work-queue/requests");
    message.setBody(new AmqpValue(request.getText()));
    message.setApplicationProperties(new ApplicationProperties(props));

    requestMessages.add(message);

    data.getRequestIds().add(requestId);

    doSendRequests();

    rc.response().setStatusCode(202).end(requestId);
  }

  private void handleReceiveResponse(RoutingContext rc) {
    String value = rc.request().getParam("request");

    if (value == null) {
      rc.fail(500);
      return;
    }

    Response response = data.getResponses().get(value);

    if (response == null) {
      rc.response().setStatusCode(404).end();
      return;
    }

    rc.response()
      .setStatusCode(200)
      .putHeader("Content-Type", "application/json; charset=utf-8")
      .end(Json.encodePrettily(response));
  }

  private void handleGetData(RoutingContext rc) {
    rc.response()
      .putHeader("Content-Type", "application/json; charset=utf-8")
      .end(Json.encodePrettily(data));
  }

  private void pruneStaleWorkers() {
    vertx.setPeriodic(5000, timer -> {
      LOGGER.debug("{0}: Pruning stale workers", ID);

      Map<String, WorkerUpdate> workers = data.getWorkers();
      long now = System.currentTimeMillis();

      for (Map.Entry<String, WorkerUpdate> entry : workers.entrySet()) {
        String workerId = entry.getKey();
        WorkerUpdate update = entry.getValue();

        if (now - update.getTimestamp() > 10 * 1000) {
          workers.remove(workerId);
          LOGGER.info("{0}: Pruned {1}", ID, workerId);
        }
      }
    });
  }
}
