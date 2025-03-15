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

# Attempt to locate JAVA_HOME
if [ -z $JAVA_HOME ]; then
 JAVA_HOME=$(LC_ALL=C update-alternatives --display java \
    | grep best \
    | grep -oe \/.*\/bin\/java \
    | sed 's/\/bin\/java//g')
    echo "best java alternative in: "$JAVA_HOME
fi

PATH=/sbin:/bin:/usr/sbin:/usr/bin:${JAVA_HOME}/bin
JAVA=${JAVA_HOME}/bin/java
NAME=openfire
DESC=openfire
DAEMON_DIR=/usr/share/openfire
DAEMON_LIB=${DAEMON_DIR}/lib
PIDFILE="/var/run/$NAME.pid"


test -x $JAVA || exit 1

DAEMON_OPTS="$DAEMON_OPTS -server -DopenfireHome=${DAEMON_DIR} \
 -Dlog4j.configurationFile=${DAEMON_LIB}/log4j2.xml \
 -Dlog4j2.formatMsgNoLookups=true \
 -Djdk.tls.ephemeralDHKeySize=matched -Djsse.SSLEngine.acceptLargeFragments=true -Djava.net.preferIPv6Addresses=system \
 -Dopenfire.lib.dir=${DAEMON_LIB} -classpath ${DAEMON_LIB}/startup.jar\
 -jar ${DAEMON_LIB}/startup.jar"

#set -e

#Helper functions
start() {
        start-stop-daemon --start --quiet --background --make-pidfile \
                --pidfile "$PIDFILE" --chuid openfire:openfire \
                --exec $JAVA -- $DAEMON_OPTS
}

stop() {
        start-stop-daemon --stop --quiet --pidfile "$PIDFILE" \
		--exec $JAVA --retry 4
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
