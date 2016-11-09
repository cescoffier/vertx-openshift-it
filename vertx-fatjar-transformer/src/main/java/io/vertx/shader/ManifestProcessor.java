package io.vertx.shader;

import org.apache.maven.plugins.shade.relocation.Relocator;
import org.apache.maven.plugins.shade.resource.ResourceTransformer;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.jar.*;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class ManifestProcessor implements ResourceTransformer {

  private final String verticle;
  private final String mainClass;
  private final Map<String, Object> entries;

  private boolean processed;
  private Manifest manifest;

  public ManifestProcessor(String mainClass, String verticle, Map<String, Object> entries) {
    this.mainClass = mainClass;
    this.verticle = verticle;
    this.entries = entries;
  }

  public boolean canTransformResource(String resource) {
    return JarFile.MANIFEST_NAME.equalsIgnoreCase(resource);

  }

  public void processResource(String resource, InputStream is, List<Relocator> relocators)
      throws IOException {
    // Take the first one.
    if (!processed) {
      manifest = new Manifest(is);
      processed = true;
    }
  }

  public boolean hasTransformedResource() {
    return processed;
  }

  public void modifyOutputStream(JarOutputStream jos)
      throws IOException {
    // If we didn't find a manifest, create one.
    if (manifest == null) {
      manifest = new Manifest();
      processed = true;
    }

    Attributes attributes = manifest.getMainAttributes();

    if (mainClass != null) {
      attributes.put(Attributes.Name.MAIN_CLASS, mainClass);
    }

    if (verticle != null) {
      attributes.put(new Attributes.Name("Main-Verticle"), verticle);
    }

    if (entries != null) {
      for (Map.Entry<String, Object> entry : entries.entrySet()) {
        attributes.put(new Attributes.Name(entry.getKey()), entry.getValue());
      }
    }

    jos.putNextEntry(new JarEntry(JarFile.MANIFEST_NAME));
    manifest.write(jos);
  }
}
