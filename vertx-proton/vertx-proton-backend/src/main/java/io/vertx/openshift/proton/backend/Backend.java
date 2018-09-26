/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.vertx.openshift.proton.backend;

import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.proton.ProtonClient;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;
import io.vertx.reactivex.config.ConfigRetriever;
import io.vertx.reactivex.core.AbstractVerticle;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.message.Message;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class Backend extends AbstractVerticle {
  private static final Logger LOGGER = LoggerFactory.getLogger(Backend.class);
  private static final String ID = "worker-vertx-" + UUID.randomUUID()
    .toString().substring(0, 4);

  private static final AtomicInteger requestsProcessed = new AtomicInteger(0);
  private static final AtomicInteger processingErrors = new AtomicInteger(0);

  @Override
  public void start(Future<Void> future) {
    ConfigRetriever.create(vertx).rxGetConfig()
      .doOnSuccess(json -> {
        String amqpHost = json.getString("MESSAGING_SERVICE_HOST", "localhost");
        int amqpPort = json.getInteger("MESSAGING_SERVICE_PORT", 5672);
        String amqpUser = json.getString("MESSAGING_SERVICE_USER", "work-queue");
        String amqpPassword = json.getString("MESSAGING_SERVICE_PASSWORD", "work-queue");

        ProtonClient client = ProtonClient.create(vertx.getDelegate());
        client.connect(amqpHost, amqpPort, amqpUser, amqpPassword, result -> {
          if (result.failed()) {
            future.fail(result.cause());
          } else {
            ProtonConnection conn = result.result();
            conn.setContainer(ID);
            conn.open();

            receiveRequests(conn);
            sendUpdates(conn);
            future.complete();
          }
        });
      }).subscribe();
  }

  private void receiveRequests(ProtonConnection conn) {
    // Ordinarily, a sender or receiver is tied to a named message
    // source or target. By contrast, a null sender transmits
    // messages using an "anonymous" link and routes them to their
    // destination using the "to" property of the message.
    ProtonSender sender = conn.createSender(null);

    ProtonReceiver receiver = conn.createReceiver("work-queue/requests");

    receiver.handler((delivery, request) -> {
      LOGGER.info("{0}: Receiving request {1}", ID, request);
      String responseBody;

      try {
        responseBody = processRequest(request);
      } catch (Exception e) {
        LOGGER.error("{0}: Failed processing message: {1}", ID, e.getMessage());
        processingErrors.incrementAndGet();
        return;
      }

      Map<String, Object> props = new HashMap<>();
      props.put("workerId", conn.getContainer());

      Message response = Message.Factory.create();
      response.setAddress(request.getReplyTo());
      response.setCorrelationId(request.getMessageId());
      response.setBody(new AmqpValue(responseBody));
      response.setApplicationProperties(new ApplicationProperties(props));

      sender.send(response);

      requestsProcessed.incrementAndGet();

      LOGGER.info("{0}: Sent {1}", ID, response);
    });

    sender.open();
    receiver.open();
  }

  private String processRequest(Message request) {
    Map props = request.getApplicationProperties().getValue();
    boolean uppercase = (boolean) props.get("uppercase");
    boolean reverse = (boolean) props.get("reverse");
    String text = (String) ((AmqpValue) request.getBody()).getValue();

    if (uppercase) {
      text = text.toUpperCase();
    }

    if (reverse) {
      text = new StringBuilder(text).reverse().toString();
    }

    return text;
  }

  private void sendUpdates(ProtonConnection conn) {
    ProtonSender sender = conn.createSender("work-queue/worker-updates");

    vertx.setPeriodic(5000, timer -> {
      if (conn.isDisconnected()) {
        vertx.cancelTimer(timer);
        return;
      }

      if (sender.sendQueueFull()) {
        return;
      }

      LOGGER.debug("{0}: Sending status update", ID);

      Map<String, Object> properties = new HashMap<>();
      properties.put("workerId", conn.getContainer());
      properties.put("timestamp", System.currentTimeMillis());
      properties.put("requestsProcessed", (long) requestsProcessed.get());
      properties.put("processingErrors", (long) processingErrors.get());

      Message message = Message.Factory.create();
      message.setApplicationProperties(new ApplicationProperties(properties));

      sender.send(message);
    });

    sender.open();
  }
}
