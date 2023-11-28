#!/usr/bin/env bash
# Starts an Openfire server (used in Continuous integration testing)
set -euo pipefail

LOCAL_RUN=false

HOST='example.org'

usage()
{
	echo "Usage: $0 [-d] [-l] [-i IPADDRESS] [-h HOST]"
	echo "    -d: Enable debug mode. Prints commands, and preserves temp directories if used (default: off)"
	echo "    -l: Launch a local Openfire. (default: off)"
	echo "    -i: Set a hosts file for the given IP and host (or for example.com if running locally). Reverted at exit."
	echo "    -h: The hostname for the Openfire under test (default: example.org)"
	exit 2
}

while getopts dlh:i: OPTION "$@"; do
	case $OPTION in
		d)
			set -x
			;;
		l)
			LOCAL_RUN=true
			;;
		h)
			HOST="${OPTARG}"
			;;
		i)
			IPADDRESS="${OPTARG}"
			;;
		\? ) usage;;
        :  ) usage;;
        *  ) usage;;
	esac
done

if [[ $LOCAL_RUN == true ]] && [[ $HOST != "example.org" ]]; then
	echo "Host is fixed if launching a local instance. If you have an already-running instance to test against, omit the -l flag (and provide -i 127.0.0.1 if necessary)."
	exit 1
fi

function setBaseDirectory {
	# Pretty fancy method to get reliable the absolute path of a shell
	# script, *even if it is sourced*. Credits go to GreenFox on
	# stackoverflow: http://stackoverflow.com/a/12197518/194894
	pushd . > /dev/null
	SCRIPTDIR="${BASH_SOURCE[0]}";
	while [ -h "${SCRIPTDIR}" ]; do
		cd "$(dirname "${SCRIPTDIR}")"
		SCRIPTDIR="$(readlink "$(basename "${SCRIPTDIR}")")";
	done
	cd "$(dirname "${SCRIPTDIR}")" > /dev/null
	SCRIPTDIR="$(pwd)";
	popd  > /dev/null
	BASEDIR="${SCRIPTDIR}"
	cd "${BASEDIR}"
}

function setHostsFile {
	if [[ -n "${IPADDRESS-}" ]]; then
		echo "Setting hosts file for local running. This may prompt for sudo."
		sudo /bin/sh -c "echo \"$IPADDRESS $HOST\" >> /etc/hosts"
	fi
}

function launchOpenfire {
	declare -r OPENFIRE_SHELL_SCRIPT="${BASEDIR}/distribution/target/distribution-base/bin/openfire.sh"

	if [[ ! -f "${OPENFIRE_SHELL_SCRIPT}" ]]; then
		./mvnw verify -P ci
	fi

	rm -f distribution/target/distribution-base/conf/openfire.xml
	cp distribution/target/distribution-base/conf/openfire-demoboot.xml \
		distribution/target/distribution-base/conf/openfire.xml

	echo "Starting Openfire…"
	"${OPENFIRE_SHELL_SCRIPT}" &

	# Wait 120 seconds for Openfire to open up the web interface and
	# assume Openfire is fully operational once that happens (not sure if
	# this assumption is correct).
	echo "Waiting for Openfire…"
	timeout 120 bash -c 'until printf "" 2>>/dev/null >>/dev/tcp/$0/$1; do sleep 0.3; done' localhost 7070
}

setBaseDirectory
if [[ -n "${IPADDRESS-}" ]]; then
	setHostsFile
fi
if [[ $LOCAL_RUN == true ]]; then
	launchOpenfire
fi
