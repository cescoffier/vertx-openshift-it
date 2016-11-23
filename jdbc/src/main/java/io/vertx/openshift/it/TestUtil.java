package io.vertx.openshift.it;

import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * @author Thomas Segismont
 */
public class TestUtil {

  public static void fail(RoutingContext rc, String msg) {
    fail(rc, new AssertionError(msg));
  }

  public static void fail(RoutingContext rc, Throwable t) {
    rc.response().setStatusCode(500).putHeader(HttpHeaders.CONTENT_TYPE, "text/plain").end(throwableToString(t));
  }

  public static String throwableToString(Throwable t) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    t.printStackTrace(pw);
    pw.flush();
    pw.close();
    return sw.toString();
  }
}
