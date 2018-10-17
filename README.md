# Vert.x Integration Test for OpenShift

Before you want to run any tests from this test suite for the first time,
you need to first install the `vertx-it-utils` module to your local maven repo:
```bash
cd vertx-it-utils

mvn clean install 
```
If you don't do this, the modules here won't compile because of missing dependencies
(for example missing OpenshiftTestAssistant class and so on.)

If you want to use Minishift for testing, run:

```bash
minishift start --cpus=4 --memory=8192 \
  --vm-driver=virtualbox

eval $(minishift docker-env)
```

Some tests require the `oc` command line in the `PATH`.
Don't forget to login as well (user `developer`, password `developer`):

```bash
oc login
```

Test in module can be executed switching to `$PROJECT` directory and running 
 ```bash
 cd $PROJECT
 mvn clean verify -Popenshift
 ```

Note that the productized Vert.x 3.5.1.redhat-004 uses Agroal as a default
JDBC connection pool as opposed to c3p0 used in upstream 3.5.1 release.
In order to use the c3p0 connection pool, you need to modify the JDBC client
configuration as the c3p0 library uses different configuration keys than Agroal:
```
// Agroal example
JsonObject agroalConfig = new JsonObject()
  .put("jdbcUrl", JDBC_URL)
  .put("driverClassName", "org.postgresql.Driver")
  .put("principal", JDBC_USER)
  .put("credential", JDBC_PASSWORD)
  .put("castUUID", true);

// c3p0 example
JsonObject c3p0Config = new JsonObject()
  .put("url", JDBC_URL)
  .put("driver_class", "org.postgresql.Driver")
  .put("user", JDBC_USER)
  .put("password", JDBC_PASSWORD)
  .put("castUUID", true)
  .put("provider_class", "io.vertx.ext.jdbc.spi.impl.C3P0DataSourceProvider");
```


## Embedded HTTP
Check the embedded case. An embedded HTTP server is started and tested.


## Simple HTTP
Test various HTTP features (replicas, web sockets...)


## Service Discovery
Test the Vert.x service discovery.


## JDBC
Test the Async JDBC Client against a PostgreSQL database running in OpenShift.


## Configuration
Showcases different configuration stores and retrieving configuration with different options.


## SockJS 
You need to set up maven properties, which are used for openshift route generation:
* `openshift.namespace`  namespace which is used for testing
* `openshift.route.suffix` suffix which is used for route generation


## SockJS service proxy
Integration tests for sockjs-service-proxy module (RPC-like application).

If tests fail on `SockJSProxyIT.initialize:34` try to change [selenium/standalone-chrome](https://hub.docker.com/r/selenium/standalone-chrome/) version by maven property: 
* `chrome.selenium.image.version`. 
  * The default value this property is set to is `latest`
  * Latest known working version on OpenShift is `3.14.0-europium`


## SSO Auth
Tests for authentication using Vert.x JWTAuth & OAuth2 modules. JWTAuth integration tests taken from [Vert.x Secured Booster](https://github.com/openshiftio-vertx-boosters/vertx-secured-http-booster). 
The tests use Red Hat SSO image v7.1


## Healthcheck
Tests for readiness/liveness of the pod in various scenarios (pod startup, pod restart, pod kill, ..)


## HTTP2
Contains tests for Vert.x integration of HTTP2 features + tests for Vert.x integration of [gRPC](https://grpc.io/).


## Circuit Breaker
Integration tests for Vert.x Circuit Breaker module. The tests are taken from [Vert.x Circuit Breaker Booster](https://github.com/openshiftio-vertx-boosters/vertx-circuit-breaker-booster)


## Cluster Manager
Tests for Vert.x clustering - integration with Infinispan, usage of clustered event bus, ..


## Various Database Engines
These tests serve as a verification that a Vert.x application running on OpenShift Online
is able to communicate with an existing on-premise database as well as an internal database
also running in the same OpenShift instance. The tests are divided into modules, each of which is named
after the database engine used in the Vert.x application example:
* `mysql-it` for Vert.x and MySQL integration tests
* `postgresql-it` for Vert.x and PostgreSQL integration tests
* `oracle-it` for Vert.x and OracleDB integration tests - do note that due to the OracleDB being proprietary,
the tests only cover communication with an existing on-premise database use case.
* `mongodb-it` for Vert.x and MongoDB integration tests

These modules are located in the `db-it` parent module. On the same level, there's also `verticle-utils` module,
which contains some utility classes that are commonly used by the tests.


## MQTT
Integration tests for Vert.x MQTT module. This includes tests for connectivity, topic subscription
and message publishing. When running the tests, a MQTT broker (server) is deployed in OpenShift
and a MQTT client is created locally.


## Proton
Integration tests for Vert.x Proton module. The application leverages the 
[amq63-basic OpenShift Application template](https://github.com/jboss-openshift/application-templates/blob/master/docs/amq/amq63-basic.adoc)
and is derived from the [RHOAR AMQP messaging booster](https://github.com/openshiftio-vertx-boosters/vertx-messaging-work-queue-booster).

## Micrometer Metrics
This module contains integration tests for Vert.x Micrometer Metrics. The test run should start a Prometheus server
along with a simple Vert.x HTTP server for providing some metrics, then execute some basic tests to verify that
the metrics are correct.



## vertx-it-utils
This module does not contain any tests, but instead provides some abstract test classes,
`OpenshiftTestAssistant` class and other utility classes. You need to install this module first (described at the top of this page)
in order to be able to run any tests from this test suite. 


## Running on Openshift Online
1. Login to OpenShift online with `oc`
2. Create OpenShift project
3. Use same process as described before
