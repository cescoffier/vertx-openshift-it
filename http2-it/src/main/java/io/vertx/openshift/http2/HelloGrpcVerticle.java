package io.vertx.openshift.http2;

import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.net.JksOptions;
import io.vertx.grpc.VertxServer;
import io.vertx.grpc.VertxServerBuilder;

/**
 * Very simple gRPC service.
 */
public class HelloGrpcVerticle extends AbstractVerticle {

  @Override
  public void start() throws Exception {
    VertxServer server = VertxServerBuilder.forPort(vertx, 8082)
      .useSsl(options -> options
        .setSsl(true)
        .setUseAlpn(true)
        .setKeyStoreOptions(new JksOptions()
          .setPath("tls/server-keystore.jks")
          .setPassword("wibble"))
      )
      .addService(new GreeterGrpc.GreeterVertxImplBase() {
        @Override
        public void sayHello(HelloRequest request, Future<HelloReply> future) {
          System.out.println("Hello " + request.getName());
          future.complete(HelloReply.newBuilder().setMessage("Hello " + request.getName()).build());
        }
      }).build();

    server.start(ar -> {
      if (ar.succeeded()) {
        System.out.println("gRPC service started");
      } else {
        System.out.println("Could not start server " + ar.cause().getMessage());
      }
    });
  }
}
