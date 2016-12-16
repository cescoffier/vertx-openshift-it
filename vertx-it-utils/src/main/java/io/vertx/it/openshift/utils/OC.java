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

  public static String execute(String... args) {
    File oc_executable = find();

    CommandLine commandLine = new CommandLine(oc_executable);
    commandLine.addArguments(args);

    DefaultExecutor executor = new DefaultExecutor();

    try {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      PumpStreamHandler handler = new PumpStreamHandler(output);
      executor.setStreamHandler(handler);
      executor.setExitValues(new int[] {0, 1, 2});
      executor.execute(commandLine);

      if (! output.toString().isEmpty()) {
        System.out.println("====");
        System.out.println(output);
        System.out.println("====");
      }

      return output.toString();
    } catch (IOException e) {
      throw new RuntimeException("Unable to execute " + commandLine.toString(), e);
    }

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
}
