package io.vertx.openshift.mqtt.subscriber;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;

import java.nio.charset.Charset;

/**
 * @author Martin Spisiak (mspisiak@redhat.com) on 20/03/18.
 */
public class MqttSubscriber extends AbstractVerticle {

  @Override
  public void start() {
    MqttClientOptions options = new MqttClientOptions();
//      .setTrustAll(true)
//      .setSsl(true);

    MqttClient client = MqttClient.create(vertx, options);

    client.publishHandler(received -> {
      System.out.println("Received msg from topic " + received.topicName()
        + "\nwith content " + received.payload().toString(Charset.defaultCharset())
        + "\nand QoS " + received.qosLevel());
    });

    client.publishCompletionHandler(id -> System.out.println("msg id: " + id));

    client.subscribeCompletionHandler(msg -> {
      System.out.println("SUBACK from server with QoS: " + msg.grantedQoSLevels());

      client.publish("/openshift",
        Buffer.buffer("Hello from MQTT on OpenShift!"),
        MqttQoS.AT_LEAST_ONCE,
        false,
        false,
        s -> System.out.println("Publish sent to a server")
      );
    });

    client.unsubscribeCompletionHandler(h -> {
      System.out.println("Received UNSUBACK from server");

      client.disconnect(d -> System.out.println("Disconnected from server"));
    });

    client.connect(1883, "mqtt-service", s -> {
      if (s.succeeded()) {
        System.out.println(s.result().toString());
        client.subscribe("/openshift", 1);
      } else {
        System.err.println("An error has occured: " + s.cause().getMessage());
      }
    });

    // Just for healthcheck purposes
    Router router = Router.router(vertx);
    router.get("/").handler(this::healthcheck);

    vertx
      .createHttpServer()
      .requestHandler(router::accept)
      .listen(8080);
  }

  private void healthcheck(RoutingContext rc) {
    rc.response().end("Hello healthcheck!");
  }
}
