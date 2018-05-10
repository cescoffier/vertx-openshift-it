package io.vertx.openshift.auth;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.oauth2.*;
import io.vertx.ext.auth.oauth2.providers.KeycloakAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.openshift.auth.service.Greeting;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

public class RestApplication extends AbstractVerticle {

  private long counter;

  @Override
  public void start(Future<Void> done) {
    Router router = Router.router(vertx);
    router.get("/health").handler(rc -> rc.response().end("OK"));

    JsonObject keycloakJson = new JsonObject()
      .put("realm", System.getenv("REALM"))
      .put("realm-public-key", System.getenv("REALM_PUBLIC_KEY"))
      .put("auth-server-url", System.getenv("SSO_AUTH_SERVER_URL"))
      .put("ssl-required", "external")
      .put("resource", System.getenv("CLIENT_ID"))
      .put("credentials", new JsonObject()
        .put("secret", System.getenv("SECRET")));

    OAuth2Auth oauth2 = KeycloakAuth.create(
      vertx,
      OAuth2FlowType.PASSWORD,
      keycloakJson,
      new HttpClientOptions()
        .setProtocolVersion(HttpVersion.HTTP_2)
        .setSsl(true)
        .setUseAlpn(true)
        .setTrustAll(true)
    );

    router.route("/oauth2-greeting").handler(BodyHandler.create());
    router.post("/oauth2-greeting").handler(ctx -> {
      oauth2.authenticate(
        new JsonObject()
          .put("username", ctx.request().getParam("username"))
          .put("password", ctx.request().getParam("password")), res -> {
          if (res.failed()) {
            res.cause().printStackTrace();
            ctx.fail(401);
          } else {
            AccessToken token = (AccessToken) res.result();
            token.isAuthorized("realm:booster-admin", authz -> {
              if (authz.succeeded() && authz.result()) {
                ctx.next();
              } else {
                System.err.println("AuthZ failed!");
                ctx.fail(403);
              }
            });
          }
        });
//      oauth2.decodeToken(ctx.get("token"), h -> {
//        if (h.succeeded()) {
//          h.result().isAuthorized("booster-admin", authz -> {
//              if (authz.succeeded() && authz.result()) {
//                ctx.next();
//              } else {
//                System.err.println("AuthZ failed!");
//                ctx.fail(403);
//              }
//            });
//        } else {
//          h.cause().printStackTrace();
//        }
//      });
    });

    router.post("/oauth2-greeting").handler(ctx -> {
      String name = ctx.request().getParam("name");
      if (name == null) {
        name = "World";
      }
      ctx.response()
        .putHeader(CONTENT_TYPE, "application/json; charset=utf-8")
        .end(new Greeting(++counter, name).encode());
    });

    router.route("/jwt-greeting").handler(JWTAuthHandler.create(
      JWTAuth.create(vertx, new JWTAuthOptions()
        .addPubSecKey(new PubSecKeyOptions()
          .setAlgorithm("RS256")
          .setPublicKey(System.getenv("REALM_PUBLIC_KEY")))
        .setPermissionsClaimKey("realm_access/roles"))));

    router.get("/jwt-greeting").handler(ctx ->
      ctx.user().isAuthorized("booster-admin", authz -> {
        if (authz.succeeded() && authz.result()) {
          ctx.next();
        } else {
          System.err.println("AuthZ failed!");
          ctx.fail(403);
        }
      }));

    router.get("/jwt-greeting").handler(ctx -> {
      String name = ctx.request().getParam("name");
      if (name == null) {
        name = "World";
      }
      ctx.response()
        .putHeader(CONTENT_TYPE, "application/json; charset=utf-8")
        .end(new Greeting(++counter, name).encode());
    });

    router.get("/keycloak.json").handler(ctx ->
      ctx.response()
        .putHeader(CONTENT_TYPE, "application/json; charset=utf-8")
        .end(keycloakJson.encode()));

    router.get().handler(StaticHandler.create());

    vertx
      .createHttpServer()
      .requestHandler(router::accept)
      .listen(config().getInteger("http.port", 8080), ar -> done.handle(ar.mapEmpty()));
  }
}
