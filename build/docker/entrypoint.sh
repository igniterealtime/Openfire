#!/bin/bash
set -e

rewire_openfire() {
  rm -rf ${OPENFIRE_DIR}/{conf,resources/security}
  ln -sf ${OPENFIRE_DATA_DIR}/conf ${OPENFIRE_DIR}/
  ln -sf ${OPENFIRE_DATA_DIR}/plugins ${OPENFIRE_DIR}/
  ln -sf ${OPENFIRE_DATA_DIR}/conf/security ${OPENFIRE_DIR}/resources/
}

initialize_data_dir() {
  echo "Initializing ${OPENFIRE_DATA_DIR}..."

  mkdir -p ${OPENFIRE_DATA_DIR}
  chmod -R 0750 ${OPENFIRE_DATA_DIR}
  chown -R ${OPENFIRE_USER}:${OPENFIRE_USER} ${OPENFIRE_DATA_DIR}

  # initialize the data volume
  if [[ ! -d ${OPENFIRE_DATA_DIR}/conf ]]; then
    sudo -HEu ${OPENFIRE_USER} cp -a ${OPENFIRE_DIR}/conf_org ${OPENFIRE_DATA_DIR}/conf
    sudo -HEu ${OPENFIRE_USER} cp -a ${OPENFIRE_DIR}/plugins_org ${OPENFIRE_DATA_DIR}/plugins
    sudo -HEu ${OPENFIRE_USER} cp -a ${OPENFIRE_DIR}/resources/security_org ${OPENFIRE_DATA_DIR}/conf/security
  fi
  sudo -HEu ${OPENFIRE_USER} mkdir -p ${OPENFIRE_DATA_DIR}/{plugins,embedded-db}
  sudo -HEu ${OPENFIRE_USER} rm -rf ${OPENFIRE_DATA_DIR}/plugins/admin
  sudo -HEu ${OPENFIRE_USER} ln -sf ${OPENFIRE_DIR}/plugins_org/admin ${OPENFIRE_DATA_DIR}/plugins/admin

  # create version file
  CURRENT_VERSION=
  [[ -f ${OPENFIRE_DATA_DIR}/VERSION ]] && CURRENT_VERSION=$(cat ${OPENFIRE_DATA_DIR}/VERSION)
  if [[ ${OPENFIRE_VERSION} != ${CURRENT_VERSION} ]]; then
    echo -n "${OPENFIRE_VERSION}" | sudo -HEu ${OPENFIRE_USER} tee ${OPENFIRE_DATA_DIR}/VERSION >/dev/null
  fi
}

initialize_log_dir() {
  echo "Initializing ${OPENFIRE_LOG_DIR}..."
  mkdir -p ${OPENFIRE_LOG_DIR}
  chmod -R 0755 ${OPENFIRE_LOG_DIR}
  chown -R ${OPENFIRE_USER}:${OPENFIRE_USER} ${OPENFIRE_LOG_DIR}
  rm -rf ${OPENFIRE_DIR}/logs
  ln -sf ${OPENFIRE_LOG_DIR} ${OPENFIRE_DIR}/logs
}

# allow arguments to be passed to openfire launch
if [[ ${1:0:1} = '-' ]]; then
  EXTRA_ARGS="$@"
  set --
fi

rewire_openfire
initialize_data_dir
initialize_log_dir

JAVACMD=`which java 2> /dev/null `
# default behaviour is to launch openfire
if [[ -z ${1} ]]; then
  exec start-stop-daemon --start --chuid ${OPENFIRE_USER}:${OPENFIRE_USER} --exec $JAVACMD -- \
    -server \
    -DopenfireHome="${OPENFIRE_DIR}" \
    -Dopenfire.lib.dir=${OPENFIRE_DIR}/lib \
    -Dlog4j.configurationFile=${OPENFIRE_DIR}/lib/log4j2.xml \
    -classpath ${OPENFIRE_DIR}/lib/startup.jar \
    -jar ${OPENFIRE_DIR}/lib/startup.jar ${EXTRA_ARGS}
else
  exec "$@"
fi
