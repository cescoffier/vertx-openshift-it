#!/usr/bin/env bash
PROJECT=$(oc project -q)
echo "Cleaning up current project: ${PROJECT}"
oc delete routes --all
oc delete services --all
oc delete rc --all
oc delete dc --all
oc delete pods --all
oc delete bc --all
oc delete is --all
oc delete configmap --all
oc delete template --all
