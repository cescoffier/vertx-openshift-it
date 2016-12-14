package io.vertx.it.openshift.utils;

import java.util.concurrent.Callable;

import static org.assertj.core.api.Fail.fail;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class Ensure {

  public static  <T> T ensureThat(String msg, Callable<T> callable) {
    System.out.println("Ensuring that " + msg);
    try {
      return callable.call();
    } catch (Throwable t) {
      fail("Fail ensuring '" + msg + "'", t);
      return null;
    }
  }

  public static void ensureThat(String msg, Runnable runnable) {
    System.out.println("Ensuring that " + msg);
    try {
      runnable.run();
    } catch (Throwable t) {
      fail("Fail ensuring '" + msg + "'", t);
    }
  }
}
