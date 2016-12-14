# Vert.x Integration Test for OpenShift

If you want to use Minishift for testing, run:

```bash
minishift start --cpus=4 --memory=8192 \
  --deploy-registry=true --deploy-router=true \
  --vm-driver=virtualbox

eval $(minishift docker-env)
```

Somes tests require the `oc` command line in the `PATH`.
Don't forget to login as well (user `admin`, password `admin`):

```bash
oc login
```

## Embedded HTTP

Check the embedded case. An embedded HTTP server is started is started and tested.

```bash
cd embedded-http-it
mvn clean verify -Popenshift
```

## Simple HTTP

Test various HTTP features (replicas, web sockets...)

```bash
cd simple-http-it
mvn clean verify -Popenshift
```

## Service Discovery

**Need the `oc` command line in the `PATH`**
**These tests use a specific project (`vertx-discovery-it`) 

Test the Vert.x service discovery.

```bash
cd vertx-service-discovery-it
./create-project.sh
mvn clean install -Popenshift
./delete-project.sh
```

## JDBC

Test the Async JDBC Client against a PosgreSQL database running in OpenShift.

**Need the `oc` command line in the `PATH`**

```bash
cd vertx-service-discovery-it
mvn clean install -Popenshift
```
