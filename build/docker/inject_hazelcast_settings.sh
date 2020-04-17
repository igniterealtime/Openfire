#!/bin/bash
set -e

TEMPLATE_FILE=${1}

sed -i -e "s/\_SERVICE_NAME_/${SERVICE_NAME}/" -e "s/\_CLUSTER_NAME_/${CLUSTER_NAME}/" -e "s/\_SUBNET_/${SUBNET}/" ${TEMPLATE_FILE}
