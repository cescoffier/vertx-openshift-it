package io.vertx.openshift.mqtt.broker;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import io.vertx.mqtt.MqttTopicSubscription;
import io.vertx.mqtt.messages.MqttSubscribeMessage;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Martin Spisiak (mspisiak@redhat.com) on 15/03/18.
 */
public class MqttBroker extends AbstractVerticle {

  @Override
  public void start() {
    MqttServerOptions options = new MqttServerOptions();
//      .setKeyCertOptions(new PemKeyCertOptions()
//        .setKeyPath("private.pem")
//        .setCertPath("public.pem"))
//      .setSsl(true);

    MqttServer mqttServer = MqttServer.create(vertx, options);
    mqttServer.endpointHandler(this::configureEndpoint).listen(start -> {
      if (start.succeeded()) {
        System.out.println("MQTT server is listening on port " + start.result().actualPort());
      } else {
        System.out.println("Error on starting the server");
        start.cause().printStackTrace();
      }
    });

    //Just for healthcheck purposes
    Router router = Router.router(vertx);
    router.get("/").handler(this::healthcheck);

    vertx
      .createHttpServer()
      .requestHandler(router::accept)
      .listen(8080);
  }

  private void configureEndpoint(MqttEndpoint endpoint) {
    System.out.println("MQTT client [" + endpoint.clientIdentifier() + "] request to connect, clean session = "
      + endpoint.isCleanSession());

    if (endpoint.auth() != null) {
      System.out.println("[username = " + endpoint.auth().userName() + ", password = "
        + endpoint.auth().password() + "]");
    }

    if (endpoint.will() != null) {
      System.out.println("[will topic = " + endpoint.will().willTopic() + " msg = " + endpoint.will().willMessage()
        + " QoS = " + endpoint.will().willQos() + " isRetain = " + endpoint.will().isWillRetain() + "]");
    }

    System.out.println("[keep alive timeout = " + endpoint.keepAliveTimeSeconds() + "]");

    endpoint.accept(true);

    endpoint.disconnectHandler(dc -> System.out.println("Received disconnect from client"));

    configureSubscribeHandler(endpoint);

    configureUnsubscribeHandler(endpoint);

    configurePublishHandler(endpoint);

    endpoint.pingHandler(v -> System.out.println("Ping received from client"));

    endpoint.closeHandler(v -> System.out.println("MQTT Server closed"));
  }

  private void configureSubscribeHandler(MqttEndpoint endpoint) {
    endpoint.subscribeHandler(subscribe -> {
      List<MqttQoS> grantedQosLevels = new ArrayList<>();
      for (MqttTopicSubscription s: subscribe.topicSubscriptions()) {
        System.out.println("Subscription for " + s.topicName() + " with QoS " + s.qualityOfService());
        grantedQosLevels.add(s.qualityOfService());
      }

      endpoint.subscribeAcknowledge(subscribe.messageId(), grantedQosLevels);

      endpoint.publish(subscribe.topicSubscriptions().get(0).topicName(),
        Buffer.buffer("Hello from the Vert.x MQTT server!"),
        subscribe.topicSubscriptions().get(0).qualityOfService(),
        false,
        false);

      endpoint.publishAcknowledgeHandler(messageId -> System.out.println("Received ack for message = " +  messageId))
        .publishReceivedHandler(endpoint::publishRelease)
        .publishCompletionHandler(messageId -> System.out.println("Received ack for message = " +  messageId));
    });
  }

  private void configureUnsubscribeHandler(MqttEndpoint endpoint) {
    endpoint.unsubscribeHandler(unsubscribe -> {
      for (String t : unsubscribe.topics()) {
        System.out.println("Unsubscription for " + t);
      }

      endpoint.unsubscribeAcknowledge(unsubscribe.messageId());
    });
  }

  private void configurePublishHandler(MqttEndpoint endpoint) {
    endpoint.publishHandler(message -> {
      System.out.println("Just received message [" + message.payload().toString(Charset.defaultCharset())
        + "] with QoS [" + message.qosLevel() + "]");

      if (message.qosLevel() == MqttQoS.AT_LEAST_ONCE) {
        endpoint.publishAcknowledge(message.messageId());
      } else if (message.qosLevel() == MqttQoS.EXACTLY_ONCE) {
        endpoint.publishReceived(message.messageId());
      }
    }).publishReleaseHandler(endpoint::publishComplete);
  }

  private void healthcheck(RoutingContext rc) {
    rc.response().end("Hello healthcheck!");
  }
}
