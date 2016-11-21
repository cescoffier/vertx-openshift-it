package io.vertx.openshift.it;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Closeables;
import io.fabric8.arquillian.utils.SecretKeys;
import io.fabric8.arquillian.utils.Secrets;
import io.fabric8.kubernetes.api.Controller;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.Template;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.utils.Strings;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import static com.jayway.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class AbstractKubernetesIT {

  static KubernetesClient CLIENT;
  static OpenShiftClient OC;
  static String NAMESPACE;

  KubernetesClient client;
  OpenShiftClient oc;
  String namespace;

  Controller controller;

  @BeforeClass
  public static void initialize() throws InterruptedException {
    NAMESPACE = "it-" + System.currentTimeMillis();

    Config config = new ConfigBuilder()
        .withTrustCerts(true)
        .withNamespace("default")
        .build();

    DefaultKubernetesClient kube = new DefaultKubernetesClient(config);
    OpenShiftClient oc = kube.adapt(OpenShiftClient.class);

    System.out.println("Kubernetes Master URL: " + kube.getMasterUrl());
    System.out.println("Current namespace: " + kube.getNamespace());

    System.out.println("Creating project: " + NAMESPACE);
//    kube.namespaces().createNew().withNewMetadata().withName(NAMESPACE).endMetadata().done();
    oc.projects().createNew().withNewMetadata().withName(NAMESPACE)
        .withLabels(ImmutableMap.of("project", NAMESPACE))

        .endMetadata().done();

    CLIENT = kube.inNamespace(NAMESPACE);
    System.out.println("Switching to namespace: " + CLIENT.getNamespace());

    OC = CLIENT.adapt(OpenShiftClient.class);

    // Wait until all service account get a token.
    await().until(() -> {
      List<ServiceAccount> accounts = CLIENT.serviceAccounts().list().getItems();
      if (accounts.size() < 3) {
        // builder, default, deployer
        return false;
      }

      for (ServiceAccount account : accounts) {
        if (account.getSecrets() == null  || account.getSecrets().isEmpty()) {
          return false;
        }
      }
      return true;
    });


    System.out.println("System Accounts ready");

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      if (NAMESPACE != null && OC != null) {
        System.out.println("Cleanup...");
        cleanup();
      }
    }));
  }

  @Before
  public void inject() {
    System.out.println("Preparing test execution environment");
    this.client = CLIENT;
    this.oc = OC;
    this.namespace = NAMESPACE;
    this.controller = new Controller(this.client);
    this.controller.setNamespace(this.namespace);
    this.controller.setThrowExceptionOnError(true);
    this.controller.setRecreateMode(true);
    this.controller.setIgnoreRunningOAuthClients(true);
  }

  @AfterClass
  public static void cleanup() {
    if (NAMESPACE != null  && OC != null) {
      System.out.println("Deleting namespace " + NAMESPACE);
      OC.projects().withName(NAMESPACE).delete();
    }

    CLIENT = null;
    OC = null;
    NAMESPACE = null;
  }


  public URL url(Route route) {
    try {
      return new URL("http://" + route.getSpec().getHost());
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  public URL url(Route route, String path) {
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

  public static void cleanAllItNamespaces(KubernetesClient kube) {
    List<Namespace> items = kube.namespaces().list().getItems();
    for (Namespace namespace : items) {
      if (namespace.getMetadata().getName().startsWith("it-")) {
        System.out.println("Deep cleanup ... - Deleting " + namespace.getMetadata().getName());
        kube.namespaces().withName(namespace.getMetadata().getName()).delete();
      }
    }
  }

//  private static void generateServiceAccount(KubernetesClient client, String namespace, Set<Secret> secrets, String
//      serviceAccountName) {
//    List<ObjectReference> secretRefs = new ArrayList<>();
//    for (Secret secret : secrets) {
//      secretRefs.add(
//          new ObjectReferenceBuilder()
//              .withNamespace(namespace)
//              .withName(KubernetesHelper.getName(secret))
//              .build()
//      );
//    }
//
//
//    SecurityContextConstraints securityContextConstraints = client.securityContextConstraints().withName(namespace).get();
//    if (securityContextConstraints == null) {
//      client.securityContextConstraints().createNew()
//          .withNewMetadata()
//          .withName(namespace)
//          .endMetadata()
//          .withAllowHostDirVolumePlugin(true)
//          .withAllowPrivilegedContainer(true)
//          .withNewRunAsUser()
//          .withType("RunAsAny")
//          .endRunAsUser()
//          .withNewSeLinuxContext()
//          .withType("RunAsAny")
//          .endSeLinuxContext()
//          .withUsers("system:serviceaccount:" + namespace + ":" + serviceAccountName)
//          .done();
//    }
//
//    ServiceAccount serviceAccount = client.serviceAccounts()
//        .inNamespace(namespace)
//        .withName(serviceAccountName)
//        .get();
//
//    System.out.println("Service Account: " + serviceAccount);
//
//    if (serviceAccount == null) {
//      client.serviceAccounts().inNamespace(namespace).createNew()
//          .withNewMetadata()
//          .withName(serviceAccountName)
//          .endMetadata()
//          .withSecrets(secretRefs)
//          .done();
//    } else {
//      client.serviceAccounts().inNamespace(namespace)
//          .withName(serviceAccountName)
//          .replace(new ServiceAccountBuilder(serviceAccount)
//              .withNewMetadata()
//              .withName(serviceAccountName)
//              .endMetadata()
//              .addToSecrets(secretRefs.toArray(new ObjectReference[secretRefs.size()]))
//              .build());
//    }
//  }
//
//
//  private Set<Secret> generateSecrets(KubernetesClient client, String namespace, ObjectMeta meta) {
//    Set<Secret> secrets = new HashSet<>();
//    Map<String, String> annotations = meta.getAnnotations();
//    if (annotations != null && !annotations.isEmpty()) {
//      for (Map.Entry<String, String> entry : annotations.entrySet()) {
//        String key = entry.getKey();
//        String value = entry.getValue();
//        if (SecretKeys.isSecretKey(key)) {
//          SecretKeys keyType = SecretKeys.fromValue(key);
//          for (String name : Secrets.getNames(value)) {
//            Map<String, String> data = new HashMap<>();
//
//            Secret secret = null;
//            try {
//              secret = client.secrets().inNamespace(namespace).withName(name).get();
//            } catch (Exception e) {
//              // ignore - probably doesn't exist
//            }
//
//            if (secret == null) {
//              for (String c : Secrets.getContents(value, name)) {
//                data.put(c, keyType.generate());
//              }
//
//              secret = client.secrets().inNamespace(namespace).createNew()
//                  .withNewMetadata()
//                  .withName(name)
//                  .endMetadata()
//                  .withData(data)
//                  .done();
//              secrets.add(secret);
//            }
//          }
//        }
//      }
//    }
//    return secrets;
//  }


  private Set<Secret> generateSecrets(KubernetesClient client, String namespace, ObjectMeta meta) {
    Set<Secret> secrets = new HashSet<>();
    Map<String, String> annotations = meta.getAnnotations();
    if (annotations != null && !annotations.isEmpty()) {
      for (Map.Entry<String, String> entry : annotations.entrySet()) {
        String key = entry.getKey();
        String value = entry.getValue();
        if (SecretKeys.isSecretKey(key)) {
          SecretKeys keyType = SecretKeys.fromValue(key);
          for (String name : Secrets.getNames(value)) {
            Map<String, String> data = new HashMap<>();

            Secret secret = null;
            try {
              secret = client.secrets().inNamespace(namespace).withName(name).get();
            } catch (Exception e) {
              // ignore - probably doesn't exist
            }

            if (secret == null) {
              for (String c : Secrets.getContents(value, name)) {
                data.put(c, keyType.generate());
              }

              secret = client.secrets().inNamespace(namespace).createNew()
                  .withNewMetadata()
                  .withName(name)
                  .endMetadata()
                  .withData(data)
                  .done();
              secrets.add(secret);
            }
          }
        }
      }
    }
    return secrets;
  }

  public static void executeCommand(String... command) {
    String commandText = Strings.join(Arrays.asList(command), " ");
    System.out.println("Invoking command: " + commandText);
    try {
      Process process = Runtime.getRuntime().exec(command);
      processOutput(process.getInputStream(), true);
      processOutput(process.getErrorStream(), false);
      int status = process.waitFor();
      assertEquals("status code of: " + commandText, 0, status);
    } catch (Exception e) {
      throw new AssertionError("Failed to invoke: " + commandText + "\n" + e, e);
    }
  }

  protected static void processOutput(InputStream inputStream, boolean error) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
    try {
      while (true) {
        String line = reader.readLine();
        if (line == null) break;

        if (error) {
          System.err.println(line);
        } else {
          System.out.println(line);
        }
      }
    } catch (Exception e) {
      System.err.println("Failed to process " + (error ? "stderr" : "stdout") + ": " + e.getMessage());
      throw e;
    } finally {
      Closeables.closeQuietly(reader);
    }
  }

  protected Object expandTemplate(Controller controller, String namespace, String sourceName, Object dto) {
    if (dto instanceof Template) {
      Template template = (Template) dto;
      KubernetesHelper.setNamespace(template, namespace);
      System.out.println("Applying template in namespace " + namespace);
      controller.installTemplate(template, sourceName);
      dto = controller.processTemplate(template, sourceName);
      if (dto == null) {
        throw new IllegalArgumentException("Failed to process Template!");
      }
    }
    return dto;
  }


}
