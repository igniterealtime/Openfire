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


# Attempt to locate JAVA_HOME
if [ -z $JAVA_HOME ]; then
    JAVA_HOMES="/usr/lib/jvm/default-java \
		/usr/lib/jvm/java-7-sun \
		/usr/lib/jvm/java-6-sun \
		/usr/lib/jvm/java-1.5.0-sun \
		/usr/lib/jvm/java-7-openjdk-amd64 \
		/usr/lib/jvm/java-7-openjdk \
		/usr/lib/jvm/java-6-openjdk-amd64 \
		/usr/lib/jvm/java-6-openjdk"
    for t in $JAVA_HOMES ; do
	if [ -d $t ] ; then
	    JAVA_HOME=$t
	    break;
	fi
    done
fi

PATH=/sbin:/bin:/usr/sbin:/usr/bin:${JAVA_HOME}/bin
JAVA=${JAVA_HOME}/bin/java
NAME=openfire
DESC=openfire
DAEMON_DIR=/usr/share/openfire
DAEMON_LIB=${DAEMON_DIR}/lib

test -x $JAVA || exit 0

# Include openfire defaults if available
if [ -f /etc/default/openfire ] ; then
	. /etc/default/openfire
fi

DAEMON_OPTS="$DAEMON_OPTS -server -DopenfireHome=${DAEMON_DIR} \
 -Dopenfire.lib.dir=${DAEMON_LIB} -classpath ${DAEMON_LIB}/startup.jar\
 -jar ${DAEMON_LIB}/startup.jar"

#set -e

#Helper functions
start() {
        start-stop-daemon --start --quiet --background --make-pidfile \
                --pidfile /var/run/$NAME.pid --chuid openfire:openfire \
                --exec $JAVA -- $DAEMON_OPTS
}

stop() {
        start-stop-daemon --stop --quiet --pidfile /var/run/$NAME.pid \
		--exec $JAVA --retry 4
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
