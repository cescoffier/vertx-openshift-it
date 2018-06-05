package io.vertx.openshift.proxy;

import io.vertx.core.AbstractVerticle;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.openshift.proxy.impl.MyServiceImpl;
import io.vertx.serviceproxy.ServiceBinder;

/**
 * @author Martin Spisiak (mspisiak@redhat.com) on 21/05/18.
 */
public class ServerVerticle extends AbstractVerticle {
  @Override
  public void start() throws Exception {
    MyService myService = new MyServiceImpl();

    ServiceBinder binder = new ServiceBinder(vertx);
    binder.setAddress("my-service-address").register(MyService.class, myService);

    Router router = Router.router(vertx);
    BridgeOptions options = new BridgeOptions()
      .addInboundPermitted(new PermittedOptions().setAddress("my-service-address"))
      .addOutboundPermitted(new PermittedOptions().setAddress("my-service-address"));

    SockJSHandler sockJSHandler = SockJSHandler.create(vertx).bridge(options);
    router.route("/eventbus/*").handler(sockJSHandler);
    router.route().handler(StaticHandler.create());

    vertx.createHttpServer().requestHandler(router::accept).listen(8080);
  }
}
