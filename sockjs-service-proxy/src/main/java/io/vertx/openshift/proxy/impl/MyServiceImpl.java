package io.vertx.openshift.proxy.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.openshift.proxy.MyService;

/**
 * @author Martin Spisiak (mspisiak@redhat.com) on 21/05/18.
 */
public class MyServiceImpl implements MyService {
  @Override
  public MyService sayHello(String name, Handler<AsyncResult<String>> handler) {
    handler.handle(Future.succeededFuture("Hello " + name + " from Vert.x running on OpenShift!"));
    return this;
  }
}
