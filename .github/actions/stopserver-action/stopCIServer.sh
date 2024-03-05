#!/usr/bin/env bash
# Stops an Openfire server (used in Continuous integration testing)
set -euo pipefail

HOST='example.org'

usage()
{
	echo "Usage: $0 [-i IPADDRESS] [-h HOST]"
	echo "    -i: Set a hosts file for the given IP and host (or for example.com if running locally). Reverted at exit."
	echo "    -h: The network name for the Openfire under test, which will be used for both the hostname as the XMPP domain name."

	exit 2
}

while getopts h:i: OPTION "$@"; do
	case $OPTION in
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

echo "Stopping Openfire"
pkill -f openfire.lib #TODO: Can this be made more future-proof against changes in the start script?
