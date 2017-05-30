package io.vertx.it.openshift.utils;

import static org.assertj.core.api.Assertions.assertThat;

import static com.jayway.awaitility.Awaitility.await;

import com.jayway.restassured.RestAssured;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.OpenShiftClient;

/**
 *  @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class OpenShiftTestAssistant {

  private final OpenShiftClient client;

  private final String project;

  private String applicationName;

  private Map<String, List<HasMetadata>> created
    = new LinkedHashMap<>();

  public OpenShiftTestAssistant() {
    client = new DefaultKubernetesClient().adapt(OpenShiftClient.class);
    project = client.getNamespace();
  }

  public List<? extends HasMetadata> deploy(String name, File template) throws IOException {
    try (FileInputStream fis = new FileInputStream(template)) {
      List<HasMetadata> entities = client.load(fis).apply();
      created.put(name, entities);
      System.out.println(name + " deployed, " + entities.size() + " object(s) created.");

      return entities;
    }
  }

  public String deployApplication() throws IOException {
    applicationName = System.getProperty("app.name");

    List<? extends HasMetadata> entities
      = deploy("application", new File("target/classes/META-INF/fabric8/openshift.yml"));

    Optional<String> first = entities.stream()
      .filter(hm -> hm instanceof DeploymentConfig)
      .map(hm -> (DeploymentConfig) hm)
      .map(dc -> dc.getMetadata().getName()).findFirst();
    if (applicationName == null && first.isPresent()) {
      applicationName = first.get();
    }

    Route route = client.adapt(OpenShiftClient.class).routes()
      .inNamespace(project).withName(applicationName).get();
    assertThat(route).isNotNull();
    RestAssured.baseURI = "http://" + Objects.requireNonNull(route).getSpec().getHost();
    System.out.println("Route url: " + RestAssured.baseURI);

    return applicationName;
  }

  public void cleanup() {
    List<String> keys = new ArrayList<>(created.keySet());
    keys.sort(String::compareTo);
    for (String key : keys) {
      created.remove(key)
        .stream()
        .sorted(Comparator.comparing(HasMetadata::getKind))
        .forEach(metadata -> {
          System.out.println(String.format("Deleting %s : %s", key, metadata.getKind()));
          deleteWithRetries(metadata);
        });
    }
  }

  private void deleteWithRetries(HasMetadata metadata) {
    int retryCounter = 0;
    boolean deleteUnsucessful = true;
    do {
      retryCounter++;
      try {
        // returns false when successfully deleted
        deleteUnsucessful = client.resource(metadata).withGracePeriod(0).delete();
      } catch (KubernetesClientException e) {
        try {
          TimeUnit.MILLISECONDS.sleep(500);
        } catch (InterruptedException interrupted) {
          throw new RuntimeException(interrupted);
        }
        e.printStackTrace();
        System.out.println(String.format("Error deleting resource %s %s retrying #%s ", metadata.getKind(), metadata.getMetadata().getName(), retryCounter));
      }
    } while (retryCounter < 3 && deleteUnsucessful);
    if (deleteUnsucessful) {
      throw new RuntimeException("Unable to delete " + metadata);
    }
  }

  public void awaitApplicationReadinessOrFail() {
    await().atMost(5, TimeUnit.MINUTES).until(() -> {
        List<Pod> list = client.pods().inNamespace(project).list().getItems();
        return list.stream()
          .filter(pod -> pod.getMetadata().getName().startsWith(applicationName))
          .filter(this::isRunning)
          .collect(Collectors.toList()).size() >= 1;
      }
    );
  }

  private boolean isRunning(Pod pod) {
    return "running".equalsIgnoreCase(pod.getStatus().getPhase());
  }

  public OpenShiftClient client() {
    return client;
  }

  public String project() {
    return project;
  }

  public String applicationName() {
    return applicationName;
  }

  public void awaitPodReadinessOrFail(Predicate<Pod> filter) {
    await().atMost(5, TimeUnit.MINUTES).until(() -> {
        List<Pod> list = client.pods().inNamespace(project).list().getItems();
        return list.stream()
          .filter(filter)
          .filter(this::isRunning)
          .collect(Collectors.toList()).size() >= 1;
      }
    );
  }
}
