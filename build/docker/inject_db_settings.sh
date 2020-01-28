#!/bin/bash
set -e

TEMPLATE_FILE=${1}

sed -i -e "s/\_DB_URL_/${DB_URL}/" -e "s/\_DB_NAME_/${DB_NAME}/" -e "s/\_DB_USER_/${DB_USER}/" -e "s/\_DB_PASS_/${DB_PASS}/" ${TEMPLATE_FILE}
