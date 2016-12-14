#!/usr/bin/env bash
NAME=vertx-it
oc new-project ${NAME}
oc project ${NAME}
USERNAME=$(oc whoami)
oc policy add-role-to-user admin ${USERNAME} -n ${NAME}


