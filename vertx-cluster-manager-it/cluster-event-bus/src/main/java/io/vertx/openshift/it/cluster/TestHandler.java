package io.vertx.openshift.it.cluster;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * @author Thomas Segismont
 */
public interface TestHandler extends Handler<RoutingContext> {
  String path();
}
