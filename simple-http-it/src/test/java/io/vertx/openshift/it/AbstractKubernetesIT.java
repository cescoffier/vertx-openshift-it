package io.vertx.openshift.it;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.Before;

import java.net.MalformedURLException;
import java.net.URL;
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


  public URL url(Route route) throws MalformedURLException {
    return new URL("http://" + route.getSpec().getHost());
  }

  public URL url(Route route, String path) throws MalformedURLException {
    return new URL("http://" + route.getSpec().getHost() + path);
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

  public Service createDefaultService(String project) {
    Objects.requireNonNull(project);

    return client.services().createNew()
        .withNewMetadata().withName(project).endMetadata()
        .withNewSpec()
        .addNewPort()
        .withProtocol("TCP")
        .withPort(80)
        .withNewTargetPort(8080)
        .endPort()
        .addToSelector("project", project)
        .withType("ClusterIP")
        .endSpec()
        .done();
  }


}
