package io.vertx.openshift.http2;

import com.google.common.collect.Lists;
import io.fabric8.kubernetes.assertions.Assertions;
import io.grpc.ManagedChannel;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.grpc.VertxChannelBuilder;
import io.vertx.it.openshift.utils.AbstractTestClass;
import io.vertx.openshift.grpc.Empty;
import io.vertx.openshift.grpc.GreeterGrpc;
import io.vertx.openshift.grpc.HelloRequest;
import io.vertx.openshift.grpc.StreamRequest;
import org.junit.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.vertx.it.openshift.utils.Ensure.ensureThat;
import static io.vertx.it.openshift.utils.Kube.securedUrlForRoute;
import static io.vertx.it.openshift.utils.Kube.urlForRoute;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 * @author Slavomir Krupa
 */
public class Http2IT extends AbstractTestClass {

  private static final String NAME = "aloha";
  private Vertx vertx;
  private ManagedChannel channel;
  private GreeterGrpc.GreeterVertxStub stub;

  @Before
  public void setup() {
    Assertions.assertThat(client).deployments().pods().isPodReadyForPeriod();
    vertx = Vertx.vertx();
    channel = buildChannel();
    stub = GreeterGrpc.newVertxStub(channel);
  }

  @After
  public void tearDown() throws InterruptedException {
    channel.shutdown();
    assertThat(channel.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    vertx.close();
  }

  @BeforeClass
  public static void initialize() throws IOException {
    File template = new File("../grpc/target/classes/META-INF/fabric8/openshift.yml");
    ensureThat(String.format("template file %s can be deployed", template), () ->
      deploymentAssistant.deploy("grpc", template));

    ensureThat("the gRPC service is up and running", () ->
      await("Waiting for the gRPC service to be ready..").atMost(5, TimeUnit.MINUTES).untilAsserted(() -> {
        assertThat(client.deploymentConfigs().withName("grpc").isReady()).isTrue();
      })
    );

    deployAndAwaitStart();
  }

  /**
   * Checks that we can call a HTTP/2 endpoint exposed by a pod through a "TLS passthrough" route.
   */
  @Test
  public void testHttp2_H2() {
    AtomicReference<String> response = new AtomicReference<>();

    HttpClientOptions options = new HttpClientOptions()
      .setSsl(true)
      .setUseAlpn(true)
      .setProtocolVersion(HttpVersion.HTTP_2)
      .setTrustAll(true);

    ensureThat("we can call a HTTP/2 endpoint exposed by a pod through a \"TLS passthrough\" route.", () -> {
      final String host = securedUrlForRoute(client.routes().withName(NAME).get()).getHost();
      System.out.println("Host: " + host);
      System.out.println("Port: " + 443);

      vertx.createHttpClient(options)
        .getNow(443, host, "/",
          resp -> {
            System.out.println("Got response " + resp.statusCode() + " with protocol " + resp.version());
            resp.bodyHandler(body -> {
              System.out.println("Got data " + body.toString("ISO-8859-1"));
              response.set(body.toString("ISO-8859-1"));
            });
          });
      await().atMost(1, TimeUnit.MINUTES).untilAtomic(response, is(notNullValue()));

      assertThat(response.get())
        .contains("version = HTTP_2")
        .contains("Aloha from vert.x!");
    });
  }

  /**
   * Checks that we can call a HTTP/2 - H2C endpoint exposed by a pod from another pod (no route).
   */
  @Test
  public void testHttp2_Internal_H2C() {
    AtomicReference<String> response = new AtomicReference<>();

    HttpClientOptions options = new HttpClientOptions();

    ensureThat("we can call a HTTP/2 - H2C endpoint exposed by a pod from another pod (no route).", () -> {
      final String host = urlForRoute(client.routes().withName("http2").get()).getHost();
      System.out.println("Host: " + host);

      vertx.createHttpClient(options)
        .getNow(80, host, "/aloha",
          resp -> {
            System.out.println("Got H2C response " + resp.statusCode() + " with protocol " + resp.version());
            resp.bodyHandler(body -> {
              System.out.println("Got H2C data " + body.toString("ISO-8859-1"));
              response.set(body.toString("ISO-8859-1"));
            });
          });
      await().atMost(1, TimeUnit.MINUTES).untilAtomic(response, is(notNullValue()));

      assertThat(response.get())
        .contains("version = HTTP_2")
        .contains("Aloha from vert.x!")
        .contains("HTTP_1_1");
    });
  }

  /**
   * Checks that we can call a HTTP/2 - H2C endpoint exposed by a pod.
   */
  @Test
  @Ignore("Openshift does not support HTTP/2 H2C route")
  public void testHttp2_H2C() {
    AtomicReference<String> response = new AtomicReference<>();

    HttpClientOptions options = new HttpClientOptions()
      .setProtocolVersion(HttpVersion.HTTP_2)
      .setHttp2ClearTextUpgrade(false);

    ensureThat("we can call a HTTP/2 - H2C endpoint exposed by a pod.", () -> {
      final String host = securedUrlForRoute(client.routes().withName("aloha").get()).getHost();
      System.out.println("Host: " + host);

      vertx.createHttpClient(options)
        .getNow(80, host, "/",
          resp -> {
            System.out.println("Got H2C response " + resp.statusCode() + " with protocol " + resp.version());
            resp.bodyHandler(body -> {
              System.out.println("Got H2C data " + body.toString("ISO-8859-1"));
              response.set(body.toString("ISO-8859-1"));
            });
          });
      await().atMost(1, TimeUnit.MINUTES).untilAtomic(response, is(notNullValue()));

      assertThat(response.get())
        .contains("Aloha HTTP_2");
    });
  }

  /**
   * We make an HTTP GET request for <b>/hello</b> URI running on <b>EdgeVerticle</b>,
   * which in turn creates a gRPC call that's executed on <b>HelloGrpcVerticle</b>.
   */
  @Test
  public void testGRPCFromEdgeVerticle() {
    AtomicReference<String> response = new AtomicReference<>();
    final String host = urlForRoute(client.routes().withName("http2").get()).getHost();

    ensureThat("we can successfully make a simple gRPC from another pod (not using route).", () -> {
      System.out.println("Host: " + host);

      vertx.createHttpClient()
        .getNow(80, host, "/hello",
          resp -> {
            System.out.println("Got response " + resp.statusCode() + " with protocol " + resp.version());
            resp.bodyHandler(body -> {
              System.out.println("Got data " + body.toString("ISO-8859-1"));
              response.set(body.toString("ISO-8859-1"));
            });
          });
      await().atMost(1, TimeUnit.MINUTES).untilAtomic(response, is(notNullValue()));

      assertThat(response.get()).isEqualTo("Hello EdgeVerticle");
    });
  }

  /**
   * Checks that we can make a simple gRPC.
   */
  @Test
  public void testSimpleGRPC() {
    HelloRequest request = HelloRequest.newBuilder().setName("OpenShift").build();
    AtomicReference<String> result = new AtomicReference<>();

    ensureThat("we can make a simple gRPC.", () -> {
      stub.sayHello(request, asyncResponse -> {
        if (asyncResponse.succeeded()) {
          result.set(asyncResponse.result().getMessage());
        } else {
          asyncResponse.cause().printStackTrace();
        }
      });

      await().atMost(5, TimeUnit.MINUTES).untilAtomic(result, is(notNullValue()));

      assertThat(result.get()).contains("Hello OpenShift");
    });
  }

  /**
   * Checks that we can make a server-side streaming gRPC.
   */
  @Test
  public void testServerSideStreamingGRPC() {
    List<String> names = Lists.newArrayList("Vert.x", "running on", "OpenShift");
    StreamRequest request = StreamRequest.newBuilder()
      .addAllNames(names)
      .build();
    AtomicReference<List<String>> result = new AtomicReference<>(new ArrayList<>());

    ensureThat("we can make a server-side streaming gRPC.", () -> {
      stub.sayHelloStreamReply(request, stream -> {
        stream
          .exceptionHandler(Throwable::printStackTrace)
          .handler(resp -> {
            List<String> vals = result.get();
            List<String> newVals = new ArrayList<>(vals);
            newVals.add(resp.getMessage());
            result.compareAndSet(vals, newVals);
          })
          .endHandler(h -> {
            assertThat(result.get()).containsOnly("Streaming Vert.x", "Streaming running on", "Streaming OpenShift");
          });
      });
    });
  }

  /**
   * Checks that we can make a client-side streaming gRPC.
   */
  @Test
  public void testClientSideStreamingGRPC() {
    List<HelloRequest> requests = new ArrayList<>();
    requests.add(HelloRequest.newBuilder().setName("reactive").build());
    requests.add(HelloRequest.newBuilder().setName("rocks").build());

    ensureThat("we can make a client-side streaming gRPC.", () -> {
      stub.sayStreamHello(stream -> {
        stream.handler(res -> {
          if (res.failed()) {
            fail("Assertion failed", res.cause());
          } else {
            assertThat(res.result()).isNotNull();
            assertThat(res.result().getMessagesList()).containsOnly("Streamed reactive", "Streamed rocks");
          }
        });

        requests.forEach(stream::write);
        stream.end();
      });
    });
  }

  /**
   * Checks that we can make a bidirectional full-duplex gRPC.
   */
  @Test
  public void testBidirectionalFullDuplexStreamingGRPC() {
    List<HelloRequest> requests = new ArrayList<>();
    requests.add(HelloRequest.newBuilder().setName("communication").build());
    requests.add(HelloRequest.newBuilder().setName("satisfaction").build());
    requests.add(HelloRequest.newBuilder().setName("power").build());
    AtomicReference<List<String>> result = new AtomicReference<>(new ArrayList<>());

    ensureThat("we can make a bidirectional full-duplex gRPC.", () -> {
      stub.sayHelloFullDuplex(stream -> {
        stream
          .exceptionHandler(Throwable::printStackTrace)
          .handler(item -> {
            assertThat(item).isNotNull();
            List<String> vals = result.get();
            List<String> newVals = new ArrayList<>(vals);
            newVals.add(item.getMessage());
            result.compareAndSet(vals, newVals);
          })
          .endHandler(v -> {
            assertThat(result.get()).containsOnly("Full-duplex communication", "Full-duplex satisfaction", "Full-duplex power");
          });

        requests.forEach(stream::write);

        stream.end();
      });
    });
  }

  /**
   * Checks that we can make a bidirectional half-duplex gRPC.
   */
  @Test
  public void testBidirectionalHalfDuplexStreamingGRPC() {
    List<HelloRequest> requests = new ArrayList<>();
    requests.add(HelloRequest.newBuilder().setName("fun").build());
    requests.add(HelloRequest.newBuilder().setName("testing").build());
    AtomicReference<List<String>> result = new AtomicReference<>(new ArrayList<>());
    AtomicBoolean finished = new AtomicBoolean();

    ensureThat("we can make a bidirectional half-duplex gRPC.", () -> {
      stub.sayHelloHalfDuplex(stream -> {
        stream
          .exceptionHandler(Throwable::printStackTrace)
          .handler(item -> {
            assertThat(finished.get()).isTrue();
            assertThat(item).isNotNull();
            List<String> vals = result.get();
            List<String> newVals = new ArrayList<>(vals);
            newVals.add(item.getMessage());
            result.compareAndSet(vals, newVals);
          })
          .endHandler(v -> {
            assertThat(result.get()).containsOnly("Half-duplex fun", "Half-duplex testing");
          });

        requests.forEach(stream::write);

        stream.end();

        finished.set(true);
      });
    });
  }

  /**
   * Checks that making an unimplemented gRPC on server-side results in a failure.
   */
  @Test
  public void testUnimplementedCallGRPC() {
    ensureThat("making an unimplemented gRPC on server-side results in a failure.", () -> {
      stub.sayUnimplemented(Empty.newBuilder().build(), response -> {
        if (response.succeeded()) {
          fail("Should not succeed, there is no implementation");
        } else {
          assertThat(response.cause()).isNotNull();
        }
      });
    });
  }

  private ManagedChannel buildChannel() {
    Assertions.assertThat(client).deployments().pods().isPodReadyForPeriod();

    String host = securedUrlForRoute(client.routes().withName("hello").get()).getHost();

    return VertxChannelBuilder.forAddress(vertx, host, 443)
      .useSsl(options -> options
        .setSsl(true)
        .setUseAlpn(true)
        .setTrustAll(true)
      ).build();
  }
}
