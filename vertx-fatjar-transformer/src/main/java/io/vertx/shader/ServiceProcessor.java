package io.vertx.shader;

import org.apache.maven.plugins.shade.relocation.Relocator;
import org.apache.maven.plugins.shade.resource.ResourceTransformer;
import org.codehaus.plexus.util.IOUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class ServiceProcessor implements ResourceTransformer {

  private List<String> resources;

  private Map<String, ByteArrayOutputStream> data = new LinkedHashMap<>();


  public ServiceProcessor(List<String> resource) {
    Objects.requireNonNull(resource);
    this.resources = resource;
  }

  public boolean canTransformResource(String res) {
    for (String resource : resources) {
      if (resource.equalsIgnoreCase(res)) {
        return true;
      }
    }
    return false;
  }

  public void processResource(String resource, InputStream is, List<Relocator> relocators)
      throws IOException {
    ByteArrayOutputStream stream = data.get(resource);
    if (stream == null) {
      stream = new ByteArrayOutputStream();
    }

    IOUtil.copy(is, stream);
    stream.write('\n');

    data.put(resource, stream);
  }

  public boolean hasTransformedResource() {
    return !data.values().isEmpty();
  }

  public void modifyOutputStream(JarOutputStream jos)
      throws IOException {
    for (Map.Entry<String, ByteArrayOutputStream> entry : data.entrySet()) {
      jos.putNextEntry(new JarEntry(entry.getKey()));
      IOUtil.copy(new ByteArrayInputStream(entry.getValue().toByteArray()), jos);
      entry.getValue().reset();
    }
  }
}
