#!/bin/sh
#
#		Written by Miquel van Smoorenburg <miquels@cistron.nl>.
#		Modified for Debian 
#		by Ian Murdock <imurdock@gnu.ai.mit.edu>.
#       LSBize the script
#       by Erwan 'Labynocle' Ben Souiden <erwan@aleikoum.net>
#
# Version:	@(#)skeleton  1.9  26-Feb-2001  miquels@cistron.nl
#

### BEGIN INIT INFO
# Provides:             openfire
# Required-Start:       $local_fs $remote_fs $network $syslog
# Required-Stop:        $local_fs $remote_fs $network $syslog
# Default-Start:        2 3 4 5
# Default-Stop:         0 1 6
# Short-Description:    Start/stop openfire jabber server
# Description:          Start/stop openfire jabber server
### END INIT INFO 

# Include openfire defaults if available
if [ -f /etc/default/openfire ] ; then
	. /etc/default/openfire
fi

NAME=openfire
DESC=openfire
PIDFILE="/var/run/$NAME.pid"

#set -e

#Helper functions
start() {
        start-stop-daemon --start --quiet --background --make-pidfile \
                --pidfile "$PIDFILE" --chuid openfire:openfire \
                --exec /bin/bash -- -c "/usr/share/openfire/bin/openfire.sh ${DAEMON_OPTS}"
}

stop() {
        start-stop-daemon --stop --quiet --pidfile "$PIDFILE" \
        --retry 4
}

status(){
    start-stop-daemon -T --pidfile "$PIDFILE"
    status="$?"
    if [ "$status" = 0 ]; then
	echo "$NAME" is running with pid $(cat "$PIDFILE")
	return 1
    else
	echo "$NAME" is not running
	return 0;
    fi
    }

case "$1" in
  start)
	echo -n "Starting $DESC: "
	start
	echo "$NAME."
	;;
  stop)
	echo -n "Stopping $DESC: "
	stop
	echo "$NAME."
	;;
  status)
      status
      ;;

  restart|force-reload)
	#
	#	If the "reload" option is implemented, move the "force-reload"
	#	option to the "reload" entry above. If not, "force-reload" is
	#	just the same as "restart".
	#
	echo -n "Restarting $DESC: "
	#set +e
	stop
	#set -e
	#sleep 1
	start	
	
	echo "$NAME."
	;;
  *)
	N=/etc/init.d/$NAME
	# echo "Usage: $N {start|stop|restart|reload|force-reload}" >&2
	echo "Usage: $N {start|stop|restart|force-reload}" >&2
	exit 1
	;;
esac

exit 0
