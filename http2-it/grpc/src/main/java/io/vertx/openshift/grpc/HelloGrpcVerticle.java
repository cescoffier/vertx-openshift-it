package io.vertx.openshift.grpc;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.web.Router;
import io.vertx.grpc.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Very simple gRPC service.
 */
public class HelloGrpcVerticle extends AbstractVerticle {

  @Override
  public void start() {
    Router router = Router.router(vertx);
    router.get("/health").handler(rc -> rc.response().end("OK"));
    vertx.createHttpServer().requestHandler(router::accept).listen(8080);

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

        @Override
        public void sayHelloStreamReply(StreamRequest request, GrpcWriteStream<HelloReply> responses) {
          for (String s : request.getNamesList()) {
            System.out.println("Streaming " + s);
            responses.write(HelloReply.newBuilder().setMessage("Streaming " + s).build());
          }
          responses.end();
        }

        @Override
        public void sayStreamHello(GrpcReadStream<HelloRequest> requests, Future<StreamReply> replyFuture) {
          StreamReply.Builder builder = StreamReply.newBuilder();
          requests
            .exceptionHandler(Throwable::printStackTrace)
            .handler(res -> {
              System.out.println("Streamed " + res.getName());
              builder.addMessages("Streamed " + res.getName());
            })
            .endHandler(h -> replyFuture.complete(builder.build()));
        }

        @Override
        public void sayHelloFullDuplex(GrpcBidiExchange<HelloRequest, HelloReply> exchange) {
          exchange
            .exceptionHandler(Throwable::printStackTrace)
            .handler(item -> {
              System.out.println("Full-duplex " + item.getName());
              exchange.write(HelloReply.newBuilder().setMessage("Full-duplex " + item.getName()).build());
            })
            .endHandler(h -> exchange.end());
        }

        @Override
        public void sayHelloHalfDuplex(GrpcBidiExchange<HelloRequest, HelloReply> exchange) {
          List<HelloRequest> buffer = new ArrayList<>();
          exchange
            .exceptionHandler(Throwable::printStackTrace)
            .handler(item -> {
              System.out.println("Half-duplex " + item.getName());
              buffer.add(item);
            })
            .endHandler(h -> {
              buffer.forEach(v -> exchange.write(HelloReply.newBuilder().setMessage("Half-duplex " + v.getName()).build()));
              exchange.end();
            });
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
