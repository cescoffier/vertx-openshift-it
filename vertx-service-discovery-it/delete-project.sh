#!/usr/bin/env bash

NAME=vertx-service-discovery-it
oc delete project ${NAME}

while true; do
  projects=$(oc projects -q)
  if grep -qw "${NAME}" <<< "$projects" ; then
    echo "Waiting for deletion"
    sleep 1
  else
    echo "Project ${NAME} deleted"
    exit 0
  fi
done

