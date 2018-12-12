#!/usr/bin/env bash
cd ..
rm test-results.yaml
mvn clean install -f vertx-it-utils/pom.xml
mvn clean install -N
for module in configuration-it embedded-http-it healthcheck-it http2-it jdbc-it micrometer-metrics-it simple-http-it sockjs-it sockjs-service-proxy sso-auth various-db-engines-it vertx-circuit-breaker vertx-cluster-manager-it vertx-mqtt-it vertx-proton vertx-service-discovery-it ; do
  if mvn clean verify -f ${module}/pom.xml -Popenshift -Dfabric8.generator.from=registry.access.redhat.com/redhat-openjdk-18/openjdk18-openshift:latest -Dexternal -DdbAllocator.url=http://dballocator.mw.lab.eng.bos.redhat.com:8080/Allocator/AllocatorServlet -Dnexus.repo=http://10.37.132.77/oracle-jdbc/ ; then
    echo "${module}: pass\n" >> test-results.yaml
  else
    echo "${module}: fail\n" >> test-results.yaml
  fi
done
