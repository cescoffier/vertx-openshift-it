package io.vertx.shader;

import org.apache.maven.plugins.shade.relocation.Relocator;
import org.apache.maven.plugins.shade.resource.ResourceTransformer;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarOutputStream;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class VertxFatJarTransformer implements ResourceTransformer {

  // Configuration parameter
  String mainClass = "io.vertx.core.Launcher";
  String verticle;

  Map<String, Object> manifestEntries;

  List<String> services = new ArrayList<>();

  private List<ResourceTransformer> processors = new ArrayList<>();



  public VertxFatJarTransformer() {
    System.out.println("Creating the transformer");
    services.add("META-INF/services/io.vertx.core.spi.VerticleFactory");
    services.add("META-INF/services/io.vertx.ext.configuration.spi.ConfigurationStoreFactory");

  }

  @Override
  public boolean canTransformResource(String s) {
    init();
    for (ResourceTransformer transformer : processors) {
      if (transformer.canTransformResource(s)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void processResource(String s, InputStream inputStream, List<Relocator> list) throws IOException {
    init();
    for (ResourceTransformer transformer : processors) {
      if (transformer.canTransformResource(s)) {
        transformer.processResource(s, inputStream, list);
      }
    }
  }

  @Override
  public boolean hasTransformedResource() {
    init();
    for (ResourceTransformer transformer : processors) {
      if (transformer.hasTransformedResource()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void modifyOutputStream(JarOutputStream jarOutputStream) throws IOException {
    for (ResourceTransformer transformer : processors) {
      transformer.modifyOutputStream(jarOutputStream);
    }
  }

  private void init() {
    if (processors.isEmpty()) {
      processors.add(new ManifestProcessor(mainClass, verticle, manifestEntries));
      processors.add(new ServiceProcessor(services));
    }
  }
}
