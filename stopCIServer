#!/usr/bin/env bash
# Stops an Openfire server (used in Continuous integration testing)
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

if [[ -n "${IPADDRESS-}" ]]; then
    echo "Resetting hosts file after local running. This may prompt for sudo."
    sudo sed -i'.original' "/$IPADDRESS $HOST/d" /etc/hosts
fi
if [[ $LOCAL_RUN == true ]]; then
    echo "Stopping Openfire"
    pkill -f openfire.lib #TODO: Can this be made more future-proof against changes in the start script?
fi
