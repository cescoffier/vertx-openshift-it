package io.vertx.it.openshift.utils;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class OC {

  public static void initializeServiceAccount(String namespace) {
    // oc policy add-role-to-user view -z default -n CURRENT_NAMESPACE
    OC.execute("policy", "add-role-to-user", "view", "-z", "default", "-n", namespace);
  }

  public static void initializeSystemServiceAccount(String namespace) {
    OC.execute("policy", "add-role-to-group", "view", "system:serviceaccounts", "-n", namespace);
  }

  public static void removeServiceAccount(String namespace) {
    // oc policy remove-role-from-user view -z default -n CURRENT_NAMESPACE
    OC.execute("policy", "remove-role-from-user", "view", "-z", "default", "-n", namespace);
  }

  public static void removeSystemServiceAccount(String namespace) {
    OC.execute("policy", "remove-role-from-group", "view", "system:serviceaccounts", "-n", namespace);
  }

  private static File find() {
    String path = System.getenv().get("PATH");
    String[] segments = path.split(File.pathSeparator);
    for (String s : segments) {
      File maybe = new File(s, "oc");
      if (maybe.isFile()) {
        return maybe;
      }
    }
    throw new IllegalArgumentException("Cannot find `oc` in PATH: " + path);
  }

  public static String execute(String... args) {
    return executeWithQuotes(true, args);
  }

  public static String execute(boolean printCommandResult, String... args) {
    return executeWithQuotes(printCommandResult, true, args);
  }

  public static String executeWithQuotes(boolean handleQuoting, String... args) {
    return executeWithQuotes(true, handleQuoting, args);
  }

  /**
   * This method will look for the OC exec first, then execute a command constructed from <b>args</b> param.
   * @param handleQuoting specifies whether to wrap arguments in quotation marks or not.
   * @param args args from which the command will be constructed
   * @return output of the executed command, wrapped in single quotation marks.
   */
  public static String executeWithQuotes(boolean printCommand, boolean handleQuoting, String... args) {
    File oc_executable = find();

    CommandLine commandLine = new CommandLine(oc_executable);
    commandLine.addArguments(args, handleQuoting);

    DefaultExecutor executor = new DefaultExecutor();

    try {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      PumpStreamHandler handler = new PumpStreamHandler(output);
      executor.setStreamHandler(handler);
      executor.setExitValues(new int[] {0, 1, 2});
      executor.execute(commandLine);

      if (! output.toString().isEmpty() && printCommand) {
        System.out.println("====");
        System.out.println(output);
        System.out.println("====");
      }

      return output.toString();
    } catch (IOException e) {
      throw new RuntimeException("Unable to execute " + commandLine.toString(), e);
    }
  }
}
