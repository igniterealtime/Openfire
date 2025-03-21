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
    cp -a ${OPENFIRE_DIR}/conf_org ${OPENFIRE_DATA_DIR}/conf
    cp -a ${OPENFIRE_DIR}/plugins_org ${OPENFIRE_DATA_DIR}/plugins
    cp -a ${OPENFIRE_DIR}/resources/security_org ${OPENFIRE_DATA_DIR}/conf/security
  fi
  mkdir -p ${OPENFIRE_DATA_DIR}/{plugins,embedded-db}
  rm -rf ${OPENFIRE_DATA_DIR}/plugins/admin
  ln -sf ${OPENFIRE_DIR}/plugins_org/admin ${OPENFIRE_DATA_DIR}/plugins/admin
  chown -R ${OPENFIRE_USER}:${OPENFIRE_USER} ${OPENFIRE_DATA_DIR}

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

copy_provided_plugins() {
  for file in /opt/plugins/*.jar; do # Workaround for https://github.com/koalaman/shellcheck/wiki/SC2144 that doesn't involve redirecting terminal output
    if [ -f "$file" ]; then
      echo "Copying user-provided plugin: $file"
      cp -n "$file" "${OPENFIRE_DIR}/plugins/"
    fi
  done
}

# allow arguments to be passed to openfire launch
if [[ ${1:0:1} = '-' ]]; then
  EXTRA_ARGS="$@"
  set --
fi

rewire_openfire
initialize_data_dir
initialize_log_dir
copy_provided_plugins

export OPENFIRE_HOME="${OPENFIRE_DIR}"

# default behaviour is to launch openfire
if [[ -z ${1} ]]; then
  exec start-stop-daemon --start --chuid ${OPENFIRE_USER}:${OPENFIRE_USER} \
    --exec "${OPENFIRE_DIR}/bin/openfire.sh" -- ${EXTRA_ARGS}
else
  exec "$@"
fi
