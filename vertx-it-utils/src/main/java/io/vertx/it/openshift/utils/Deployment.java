package io.vertx.it.openshift.utils;

import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.utils.Files;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.vertx.it.openshift.utils.Ensure.ensureThat;
import static io.vertx.it.openshift.utils.Kube.oc;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class Deployment {

  public static File filter(String path, Map<String, String> variables) {
    File input = new File(Objects.requireNonNull(path));
    assertThat(input).isFile();
    assertThat(variables).isNotNull();

    try {
      File tmp = File.createTempFile("openshift-it", ".json");

      String content = Files.toString(input);

      for (String key : variables.keySet()) {
        content = content.replace("${" + key + "}", variables.get(key));
      }

      Files.writeToFile(tmp, content.getBytes());

      return tmp;
    } catch (Exception e) {
      throw new RuntimeException("Unable to filter " + path, e);
    }
  }

  public static ImageStream findImageStream(KubernetesClient client, String name) {
    List<ImageStream> items = oc(client).imageStreams().list().getItems();
    for (ImageStream item : items) {
      if (item.getMetadata().getName().equalsIgnoreCase(name)) {
        return item;
      }
    }

    // Try in the "default" namespace
    items = oc(client).imageStreams().inNamespace("default").list().getItems();
    for (ImageStream item : items) {
      if (item.getMetadata().getName().equalsIgnoreCase(name)) {
        return item;
      }
    }

    return null;
  }

  public static String deployIfNeeded(KubernetesClient client, String name, String path) {

    if (oc(client).deploymentConfigs().withName(name).get() != null) {
      System.out.println("Skipping the creation of dc/" + name);
      return name;
    }

    ImageStream stream = findImageStream(client, name);
    ensureThat("the image stream " + name + " exists in the namespace " + client.getNamespace(),
      () -> assertThat(stream).isNotNull());

    File file = filter(Objects.requireNonNull(path),
      ImmutableMap.of("image", stream.getStatus().getDockerImageRepository()));

    String name2 = deployIfNeeded(client, file);
    assertThat(name).isEqualTo(name2);

    return name;
  }

  public static String deployIfNeeded(KubernetesClient client, File input) {
    OpenShiftClient oc = oc(client);
    assertThat(input).isFile();

    try {
      byte[] bytes = Files.readBytes(input);
      try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes)) {
        DeploymentConfig deploymentConfig = oc.deploymentConfigs().load(bis).get();
        assertThat(deploymentConfig).isNotNull();

        if (oc.deploymentConfigs().withName(deploymentConfig.getMetadata().getName()).get() != null) {
          System.out.println("Skipping the creation of dc/" + deploymentConfig.getMetadata().getName());
          return deploymentConfig.getMetadata().getName();
        }

        oc.deploymentConfigs().create(deploymentConfig);
        OC.execute("deploy", deploymentConfig.getMetadata().getName(), "--latest", "-n", oc.getNamespace());
        return deploymentConfig.getMetadata().getName();
      }
    } catch (Exception e) {
      throw new RuntimeException("Unable to deploy deployment config " + input.getAbsolutePath(), e);
    }
  }
}
