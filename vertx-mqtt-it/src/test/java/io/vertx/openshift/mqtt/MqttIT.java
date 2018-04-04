package io.vertx.openshift.mqtt;

import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.api.model.Service;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.it.openshift.utils.AbstractTestClass;
import io.vertx.it.openshift.utils.OC;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import org.junit.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.vertx.it.openshift.utils.Ensure.ensureThat;
import static io.vertx.it.openshift.utils.Kube.securedUrlForRoute;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * @author Martin Spisiak (mspisiak@redhat.com) on 21/03/18.
 */
public class MqttIT extends AbstractTestClass {

  private static String mqttRoute;
  private static int port;
  private static MqttClient mqttSecuredClient;
  private static MqttClient mqttInsecureClient;
  private static Vertx vertx;
  private static Process p1;
  private static List<AtomicReference<String>> responses = new ArrayList<>();
  private static List<AtomicReference<String>> acks = new ArrayList<>();
  private static Map<String, Integer> securedMqttTopics = ImmutableMap.<String, Integer>builder()
    .put("/securedQoS0", 0)
    .put("/securedQoS1", 1)
    .put("/securedQoS2", 2)
    .build();
  private static Map<String, Integer> insecureMqttTopics = ImmutableMap.<String, Integer>builder()
    .put("/insecureQoS0", 0)
    .put("/insecureQoS1", 1)
    .put("/insecureQoS2", 2)
    .build();
  private static MqttClientOptions mqttClientOptions = new MqttClientOptions()
    .setTrustAll(true)
    .setSsl(true);

  @Before
  public void setup() {

  }

  @After
  public void tearDown() {
    responses.clear();
    acks.clear();
  }

  @BeforeClass
  public static void initialize() throws IOException {
    File template = new File("target/classes/META-INF/fabric8/openshift.yml");
    ensureThat(String.format("template file %s can be deployed", template), () ->
      deploymentAssistant.deploy("mqtt-broker", template));

    ensureThat("the mqtt server is up and running", () ->
      await("Waiting for the mqtt server to be ready..").atMost(5, TimeUnit.MINUTES).untilAsserted(() -> {
        Service service = client.services().withName("mqtt-secured-service").get();
        assertThat(service).isNotNull();
        assertThat(client.deploymentConfigs().withName("vertx-mqtt-it").isReady()).isTrue();
      }));

    mqttRoute = securedUrlForRoute(client.routes().withName("mqtt-secured-route").get()).getHost();

    /* on OSO, the only way to test the insecure MQTT client connecting to server running in OpenShift
    is through port forwarding. */
    String pod = OC.executeWithQuotes(false,
      "get", "po", "--selector", "app=vertx-mqtt-it", "-o", "jsonpath='{.items[0].metadata.name}'")
      .replace("'", "");
    p1 = Runtime.getRuntime().exec("oc port-forward " + pod + " :1883");
    BufferedReader reader = new BufferedReader(new InputStreamReader(p1.getInputStream()));
    Pattern pattern = Pattern.compile(".*:(\\d+)");
    Matcher matcher = pattern.matcher(reader.readLine());
    String podString = "";
    while (matcher.find()) {
      podString = matcher.group(1);
    }

    port = Integer.parseInt(podString);
    vertx = Vertx.vertx();
    mqttSecuredClient = clientSetup(MqttClient.create(vertx, mqttClientOptions));
    mqttInsecureClient = clientSetup(MqttClient.create(vertx));
  }

  @AfterClass
  public static void destroy() {
    p1.destroy();
    vertx.close();
  }

  @Test
  public void testMqttSecuredConnection() {
    ensureThat("secured client is able to connect to the secured server", () -> {
      mqttSecuredClient.connect(443, mqttRoute, s -> {
        if (s.succeeded()) {
          System.out.println("Connection successful");
        } else {
          System.err.println("An error has occured: " + s.cause().getMessage());
        }
      });
      await().atMost(30, TimeUnit.SECONDS).until(() ->
        mqttSecuredClient.isConnected()
      );
    });

    ensureThat("secured client is able to subcribe to various topics with different QoS level", () -> {
      mqttSecuredClient.subscribe(securedMqttTopics);
      await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
        for (int i = 0; i < responses.size(); i++) {
          assertThat(responses.get(i).get())
            .contains("topic /securedQoS" + i)
            .contains("Hello from the Vert.x MQTT server!")
            .contains("QoS " + MqttQoS.valueOf(i));
        }
      });
    });

    ensureThat("secured client is able to publish to various topics with different QoS level", () -> {
      securedMqttTopics.forEach((k, v) -> {
        mqttSecuredClient.publish(k,
          Buffer.buffer("Hello from MQTT on" + k + "!"),
          MqttQoS.valueOf(v),
          false,
          false,
          h -> assertThat(h.succeeded()).isTrue());

        if (v > 0) await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
          assertThat(acks.size()).isEqualTo(v);
          assertThat(acks.get(v - 1).get())
            .isNotNull()
            .contains("Received PUBACK or PUBCOMP");
        });
      });
    });
  }

  @Test
  public void testMqttInsecureConnection() {
    ensureThat("insecure client is able to connect to the server", () -> {
      mqttInsecureClient.connect(port, "localhost", s -> {
        if (s.succeeded()) {
          System.out.println("Connection successful");
        } else {
          System.err.println("An error has occured: " + s.cause().getMessage());
        }
      });
      await().atMost(30, TimeUnit.SECONDS).until(() ->
        mqttInsecureClient.isConnected()
      );
    });

    ensureThat("insecure client is able to subcribe to various topics with different QoS level", () -> {
      mqttInsecureClient.subscribe(insecureMqttTopics);
      await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
        for (int i = 0; i < responses.size(); i++) {
          assertThat(responses.get(i).get())
            .contains("topic /insecureQoS" + i)
            .contains("Hello from the Vert.x MQTT server!")
            .contains("QoS " + MqttQoS.valueOf(i));
        }
      });
    });

    ensureThat("insecure client is able to publish to various topics with different QoS level", () -> {
      insecureMqttTopics.forEach((k, v) -> {
        mqttInsecureClient.publish(k,
          Buffer.buffer("Hello from MQTT on" + k + "!"),
          MqttQoS.valueOf(v),
          false,
          false,
          h -> assertThat(h.succeeded()).isTrue());

        if (v > 0) await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
          assertThat(acks.size()).isEqualTo(v);
          assertThat(acks.get(v - 1).get())
            .isNotNull()
            .contains("Received PUBACK or PUBCOMP");
        });
      });
    });
  }

  private static MqttClient clientSetup(MqttClient client) {
    AtomicReference<String> resp = new AtomicReference<>();
    AtomicReference<String> ack = new AtomicReference<>();
    return
      client.publishHandler(received -> {
        resp.set(client.clientId() + ": Received msg from topic " + received.topicName()
          + "\nwith content " + received.payload().toString(Charset.defaultCharset())
          + "\nand QoS " + received.qosLevel());
        responses.add(resp);
      })
      .publishCompletionHandler(id -> {
        ack.set(client.clientId() + ": Received PUBACK or PUBCOMP from server with id: " + id);
        acks.add(ack);
      })
      .subscribeCompletionHandler(msg ->
        System.out.println(client.clientId() + ": Subscribe(s) successful - received SUBACK from server with QoS: " + msg.grantedQoSLevels()))
      .unsubscribeCompletionHandler(h ->
        System.out.println(client.clientId() + ": Unsubscribe successful - received UNSUBACK from server"))
      .exceptionHandler(h -> {
        System.err.println(h.getMessage());
        h.printStackTrace();
      });
  }
}
