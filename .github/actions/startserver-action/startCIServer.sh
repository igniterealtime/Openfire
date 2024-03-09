#!/usr/bin/env bash
# Starts an Openfire server (used in Continuous integration testing)
set -euo pipefail

HOST='example.org'

usage()
{
	echo "Usage: $0 [-i IPADDRESS] [-h HOST] [-b BASEDIR]"
	echo "    -i: Set a hosts file for the given IP and host (or for example.com if running locally). Reverted at exit."
	echo "    -h: The network name for the Openfire under test, which will be used for both the hostname as the XMPP domain name (default: example.org)"
	echo "    -b: The base directory of the distribution that is to be started"
	exit 2
}

while getopts h:i:b: OPTION "$@"; do
	case $OPTION in
		h)
			HOST="${OPTARG}"
			;;
		i)
			IPADDRESS="${OPTARG}"
			;;
		b)
			BASEDIR="${OPTARG}"
			;;
		\? ) usage;;
        :  ) usage;;
        *  ) usage;;
	esac
done

function setHostsFile {
	if [[ -n "${IPADDRESS-}" ]]; then
		echo "Setting hosts file for local running. This may prompt for sudo."
		sudo /bin/sh -c "echo \"$IPADDRESS $HOST\" >> /etc/hosts"
	fi
}

function launchOpenfire {
	declare -r OPENFIRE_SHELL_SCRIPT="${BASEDIR}/bin/openfire.sh"

	if [[ ! -f "${OPENFIRE_SHELL_SCRIPT}" ]]; then
		echo "Unable to find Openfire distribution in ${BASEDIR} (this file did not exist: ${OPENFIRE_SHELL_SCRIPT} )"
		exit 1
	fi

	# Ensure script is executable
	chmod +x "${OPENFIRE_SHELL_SCRIPT}"

	# Replace standard config with demoboot config
	rm -f ${BASEDIR}/conf/openfire.xml
	cp ${BASEDIR}/conf/openfire-demoboot.xml \
		${BASEDIR}/conf/openfire.xml

	# Replace the default XMPP domain name ('example.org') that's in the demoboot config with the configured domain name.
	sed -i -e 's/example.org/'"${HOST}"'/g' ${BASEDIR}/conf/openfire.xml

	echo "Starting Openfire…"
	"${OPENFIRE_SHELL_SCRIPT}" &

	# Wait 120 seconds for Openfire to open up the web interface and
	# assume Openfire is fully operational once that happens (not sure if
	# this assumption is correct).
	echo "Waiting for Openfire…"
	timeout 120 bash -c 'until printf "" 2>>/dev/null >>/dev/tcp/$0/$1; do sleep 0.3; done' localhost 7070
}

if [[ -n "${IPADDRESS-}" ]]; then
	setHostsFile
fi

launchOpenfire
