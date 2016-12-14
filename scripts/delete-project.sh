#!/usr/bin/env bash

NAME=vertx-it
oc delete project ${NAME}

while true; do
  projects=$(oc projects -q)
  if grep -qw "${NAME}" <<< "$projects" ; then
    echo "Waiting for complete deletion"
    sleep 1
  else
    echo "Project ${NAME} deleted"
    exit 0
  fi
done

