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


## Healthcheck
Tests for readiness/liveness of the pod in various scenarios (pod startup, pod restart, pod kill, ..)


## HTTP2
Tests for Vert.x integration of HTTP2 features


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

These modules are located in the `db-it` parent module. On the same level, there's also `verticle-utils` module,
which contains some utility classes that are commonly used by the tests.


## MQTT
Integration tests for Vert.x MQTT module. This includes tests for connectivity, topic subscription
and message publishing. When running the tests, a MQTT broker (server) is deployed in OpenShift
and a MQTT client is created locally.


## vertx-it-utils
This module does not contain any tests, but instead provides some abstract test classes,
OpenshiftTestAssistant class and other utility classes. You need to install this module first (described at the top of this page)
in order to be able to run any tests from this test suite. 


## Running on Openshift Online
1. Login to OpenShift online with `oc`
2. Create OpenShift project
3. Use same process as described before
