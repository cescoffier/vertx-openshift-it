package io.vertx.openshift.it;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class OpenshiftHelper {


  public static void oc_execute(String... command) {
    File oc_executable = find();

    ProcessBuilder builder = new ProcessBuilder();
    List<String> cmd = new ArrayList<>();
    cmd.add(oc_executable.getAbsolutePath());
    cmd.addAll(Arrays.asList(command));
    builder.command(cmd);
    builder.redirectError(ProcessBuilder.Redirect.INHERIT);
    builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
    try {
      Process process = builder.start();
      process.waitFor();
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
    }

  }

  private static File find() {
    String path = System.getenv().get("PATH");
    String[] segments = path.split(":");
    for (String s : segments) {
      File maybe = new File(s, "oc");
      if (maybe.isFile()) {
        return maybe;
      }
    }
    throw new IllegalArgumentException("Cannot find `oc` in PATH: " + path);
  }
}
