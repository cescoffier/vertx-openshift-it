package io.vertx.openshift.embedded;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.OpenShiftClient;
import io.restassured.RestAssured;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import static com.jayway.awaitility.Awaitility.await;
import static io.fabric8.kubernetes.assertions.Assertions.assertThat;
import static io.restassured.RestAssured.get;
import static org.hamcrest.Matchers.containsString;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
@RunWith(Arquillian.class)
@RunAsClient
public class EmbeddedServerIT {

    private static final String NAME = "embedded-http";

    @ArquillianResource
    private KubernetesClient client;

    private Route route;
    private OpenShiftClient oc;

    @Before
    public void initialize() {
        oc = client.adapt(OpenShiftClient.class);
        // The route is exposed using .vagrant.f8 suffix, delegate to openshift to
        // get a public URL
        oc.routes()
            .withName(NAME).delete();

        Route route = oc.routes().createNew()
            .withNewMetadata().withName(NAME).endMetadata()
            .withNewSpec()
            .withNewTo().withName(NAME).withKind("Service").endTo()
            .endSpec()
            .done();

        assertThat(route).isNotNull();
        this.route = route;
    }

    @Test
    @Ignore
    public void testAppProvisionsRunningPods() throws Exception {
        assertThat(client).deployments().pods().isPodReadyForPeriod();
    }

    @Test
    public void testInvokingTheService() {
        await().atMost(1, TimeUnit.MINUTES).until(this::isServed);

        get(url()).then().assertThat()
            .statusCode(200)
            .body(containsString("Hello World!"));
    }

    @Test
    public void testWithTwoReplicas() {
        oc.deploymentConfigs().withName(NAME)
            .edit().editSpec().withReplicas(2).endSpec().done();

        await().atMost(1, TimeUnit.MINUTES).until(this::isServed);

        get(url()).then().assertThat().statusCode(200)
            .body(containsString("Hello World!"));
        get(url()).then().assertThat().statusCode(200)
            .body(containsString("Hello World!"));
        get(url()).then().assertThat().statusCode(200)
            .body(containsString("Hello World!"));
        get(url()).then().assertThat().statusCode(200)
            .body(containsString("Hello World!"));

        oc.deploymentConfigs().withName(NAME)
            .edit().editSpec().withReplicas(1).endSpec().done();
    }


    public URL url() {
        try {
            return new URL("http://" + route.getSpec().getHost());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isServed() {
        try {
            return get(url()).getStatusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }


}
