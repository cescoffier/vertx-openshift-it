# Vert.x Integration Test for OpenShift

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

Check the embedded case. An embedded HTTP server is started is started and tested.



## Simple HTTP

Test various HTTP features (replicas, web sockets...)


## Service Discovery


Test the Vert.x service discovery.
## JDBC

Test the Async JDBC Client against a PosgreSQL database running in OpenShift.

## Configuration
Showcase different configuration stores and retrieving configuration with different options.
## SockJS 
You need to set up maven properties, which are used for openshift route generation:
* `openshift.namespace`  namespace which is used for testing
* `openshift.route.suffix` suffix which is used for route generation


## Running on Openshift Online

1. Login to OpenShift online with `oc`
2. Create OpenShift project
3. Use same process as described before
