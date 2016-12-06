# Vert.x Integration Test for OpenShift

**Important**: You need the latest (DEV) version of the vertx-maven-plugin from: https://github.com/fabric8io/vertx-maven-plugin

## Embedded HTTP

Check the embedded case. An embedded HTTP server is started is started and tested.

```
cd embedded-http-it
mvn clean verify -Popenshift
```

## Simple HTTP

Test various HTTP features (replicas, web sockets...)

```
cd simple-http-it
mvn clean verify -Popenshift
```

## Service Discovery

**Need the `oc` command line in the `PATH`**

Test the Vert.x service discovery.

```
cd vertx-service-discovery-it
mvn clean install -Popenshift
```

## JDBC

Test the Async JDBC Client against a PosgreSQL database running in OpenShift.

**Need the `oc` command line in the `PATH`**

```
cd vertx-service-discovery-it
mvn clean install -Popenshift
```
