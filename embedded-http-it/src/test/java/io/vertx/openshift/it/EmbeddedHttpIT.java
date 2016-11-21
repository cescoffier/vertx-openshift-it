package io.vertx.openshift.it;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.jayway.restassured.RestAssured;
import io.fabric8.kubernetes.api.Controller;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.Template;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import static com.jayway.awaitility.Awaitility.await;
import static io.fabric8.kubernetes.assertions.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;


/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class EmbeddedHttpIT extends AbstractKubernetesIT {

  private Route route;
  private Service service;
  private Deployment deployment;

  @After
  public void tearDown() {
    if (route != null) {
      oc.routes().withName(route.getMetadata().getName()).delete();
    }
    if (service != null) {
      oc.services().withName(service.getMetadata().getName()).delete();
    }

    if (deployment != null) {
      client.extensions().deployments().delete(deployment);
    }
  }


  @Test
  public void testEmbeddedHttpUsingDeployment() throws Exception {
    File file = new File("src/test/resources/openshift/embedded-http-deployment.yml");
    deployDeployment(file);
    service = createDefaultService("embedded-http");
    route = expose(oc, service);
    assertThat(client).deployments().pods().isPodReadyForPeriod();

    System.out.println(url(route));

    await().until(() -> RestAssured.get(url(route)).getStatusCode() == 200);
    RestAssured.get(url(route)).then().assertThat().statusCode(200).body(containsString("Hello World!"));
  }

  @Test
  public void testEmbeddedHttpWithReplicas() throws Exception {
    File file = new File("src/test/resources/openshift/embedded-http-deployment.yml");
    deployment = deployDeployment(file);
    service = createDefaultService("embedded-http");
    route = expose(oc, service);

    client.extensions().deployments().withName(deployment.getMetadata().getName()).edit().editSpec().withReplicas(2)
        .endSpec().done();

    assertThat(client).deployments().pods().isPodReadyForPeriod();

    System.out.println(url(route));

    await().until(() -> RestAssured.get(url(route)).getStatusCode() == 200);

    RestAssured.get(url(route)).then().assertThat().statusCode(200).body(containsString("Hello World!"));
    RestAssured.get(url(route)).then().assertThat().statusCode(200).body(containsString("Hello World!"));
    RestAssured.get(url(route)).then().assertThat().statusCode(200).body(containsString("Hello World!"));
    RestAssured.get(url(route)).then().assertThat().statusCode(200).body(containsString("Hello World!"));
  }

  private Deployment deployDeployment(File file) throws IOException {
    assertThat(file).isFile();
    FileInputStream fis = null;
    try {
      fis = new FileInputStream(file);
      Deployment deployment = client.extensions().deployments().load(fis).get();
      client.extensions().deployments().create(deployment);
      return deployment;
    } finally {
      if (fis != null) {
        fis.close();
      }
    }
  }

  private DeploymentConfig deployDeploymentConfig(File file) throws IOException {
    assertThat(file).isFile();
    FileInputStream fis = null;
    try {
      String content = Files.toString(file, Charsets.UTF_8);
      KubernetesResource resource = KubernetesHelper.loadYaml(content, KubernetesResource.class);
      Object dto = expandTemplate(controller, namespace, resource);
      controller.apply(dto, "session-id");
      return null;
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      if (fis != null) {
        fis.close();
      }
    }
    return null;
  }

  private Object expandTemplate(Controller controller, String namespace, KubernetesResource resource) {
    if (resource instanceof Template) {
      Template template = (Template) resource;
      KubernetesHelper.setNamespace(template, namespace);
      String parameterNamePrefix = "";
      //overrideTemplateParameters(template, configuration.getProperties(), parameterNamePrefix);
      System.out.println("Applying template in namespace " + namespace);
      controller.installTemplate(template, "some-file");
      Object dto = controller.processTemplate(template, "some-file");
      if (dto == null) {
        throw new IllegalArgumentException("Failed to process Template!");
      }
      return dto;
    }
    return resource;
  }


}
