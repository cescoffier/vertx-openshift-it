package io.vertx.openshift.it;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.OpenShiftClient;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.Before;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class AbstractKubernetesIT {

  @ArquillianResource
  DefaultKubernetesClient client;

  OpenShiftClient oc;


  @Before
  public void injectOpenShiftClient() {
    oc = client.adapt(OpenShiftClient.class);
  }


  public URL url(Route route)  {
    try {
      return new URL("http://" + route.getSpec().getHost());
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  public URL url(Route route, String path)  {
    try {
      return new URL("http://" + route.getSpec().getHost() + path);
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }


  public Route expose(OpenShiftClient oc, Service service) {
    String name = service.getMetadata().getName();

    return oc.routes().createNew()
        .withNewMetadata().withName(name).endMetadata()
        .withNewSpec()
        .withNewTo().withName(name).withKind("Service").endTo()
        .endSpec()
        .done();
  }

  public Service createDefaultService(String project, String type) {
    Objects.requireNonNull(project);


    Map<String, String> labels = new HashMap<>();
    if (type != null) {
      labels.put("service-type", type);
    }

    labels.put("project", project);
    labels.put("name", project);

    return client.services().createNew()
        .withNewMetadata()
        .withName(project)
        .withLabels(labels)
        .endMetadata()
        .withNewSpec()
        .addNewPort()
        .withProtocol("TCP")
        .withPort(80)
        .withNewTargetPort(8080)
        .endPort()
        .addToSelector("project", project)
        .withType("ClusterIP")
        .withSessionAffinity("None")
        .endSpec()
        .done();
  }

  public String name(HasMetadata object) {
    return object.getMetadata().getName();
  }



}
