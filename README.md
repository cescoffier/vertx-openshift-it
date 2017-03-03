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
cd jdbc-it
mvn clean install -Popenshift
```

## Configuration

**Need the `oc` command line in the `PATH`**

```bash
cd vertx-configuration-it
mvn clean install -Popenshift
```

## Running on Openshift Online

1. Login to Openshift online with `oc`
2. Ensure you don't have any project created
3. Run `scripts/create-project.sh`
4. Execute `export NAMESPACE_USE_EXISTING=vertx-it`
5. Execute `export USE_OPENSHIFT_ONLINE=true`
6. Go to the project you want to test
7. `mvn clean install -Popenshift`
8. Go grab a coffee
9. If the S2I was not yet provisioned, you may have to retry a couple of times because of timeout
10. Once done, `script/delete-project.sh`
