package io.vertx.starter;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;

public class MainVerticle extends AbstractVerticle {

  @Override
  public void start() {
    vertx.createHttpServer()
      .requestHandler(req -> {
        String path = req.path();
        HttpServerResponse response = req.response();
        switch (path) {
          case "/":
            response.end("Hello Vert.x!");
            break;
          case "/file":
            response.sendFile("/app/config/application.properties");
            break;
          case "/tmp":
            response.sendFile("/tmp/hello.txt");
            break;
          case "/headers":
            json(ok(response)).end(headersToJson(req.headers()).encode());
            break;
          case "/host":
            ok(response).end(System.getenv("HOSTNAME"));
          case "/form":
            response.setChunked(true);
            req.setExpectMultipart(true);
            req.endHandler((v) -> {
              JsonObject json = new JsonObject();
              for (String attr : req.formAttributes().names()) {
                json.put(attr, req.formAttributes().get(attr));
              }
              ok(json(response)).end(json.encode());
            });
            break;
          case "/write":
            writeSomeFile(response);
            break;
        }
      })
      .websocketHandler(socket -> {
        if (!socket.path().equals("/ws")) {
          socket.reject();
        } else {
          socket.handler(socket::writeBinaryMessage);
        }
      })
      .listen(8080);
  }

  private void writeSomeFile(HttpServerResponse response) {
    vertx.fileSystem().writeFile("/tmp/hello.txt", Buffer.buffer("hello-" + System.currentTimeMillis()), v -> {
      if (v.succeeded()) {
        response.end("/tmp/hello.txt");
      } else {
        response.end(v.cause().getMessage());
      }
    });
  }


  public HttpServerResponse ok(HttpServerResponse response) {
    return response.setStatusCode(200);
  }

  public HttpServerResponse json(HttpServerResponse response) {
    return response.putHeader("content-type", "application/json;charset=UTF-8");
  }

  public JsonObject headersToJson(MultiMap headers) {
    JsonObject json = new JsonObject();
    headers.forEach(entry -> json.put(entry.getKey(), entry.getValue()));
    return json;
  }

}
