#! /bin/sh
#
#		Written by Miquel van Smoorenburg <miquels@cistron.nl>.
#		Modified for Debian 
#		by Ian Murdock <imurdock@gnu.ai.mit.edu>.
#
# Version:	@(#)skeleton  1.9  26-Feb-2001  miquels@cistron.nl
#

PATH=/usr/local/sbin:/usr/local/bin:/sbin:/bin:/usr/sbin:/usr/bin
DAEMON=/usr/bin/java
NAME=openfire
DESC=openfire
DAEMON_DIR=/opt/openfire
DAEMON_LIB=${DAEMON_DIR}/lib

test -x $DAEMON || exit 0

# Include openfire defaults if available
if [ -f /etc/default/openfire ] ; then
	. /etc/default/openfire
fi

DAEMON_OPTS="-server -DopenfireHome=${DAEMON_DIR} \
 -Dopenfire.lib.dir=${DAEMON_LIB} -classpath ${DAEMON_LIB}/startup.jar\
 -jar ${DAEMON_LIB}/startup.jar $DAEMON_OPTS"

set -e

case "$1" in
  start)
	echo -n "Starting $DESC: "
	start-stop-daemon --start --quiet --oknodo --background \
		--pidfile /var/run/$NAME.pid --make-pidfile \
		--exec $DAEMON -- $DAEMON_OPTS
	echo "$NAME."
	;;
  stop)
	echo -n "Stopping $DESC: "
	start-stop-daemon --stop --quiet --oknodo --pidfile /var/run/$NAME.pid \
		--exec $DAEMON
	echo "$NAME."
	;;
  restart|force-reload)
	#
	#	If the "reload" option is implemented, move the "force-reload"
	#	option to the "reload" entry above. If not, "force-reload" is
	#	just the same as "restart".
	#
	echo -n "Restarting $DESC: "
	start-stop-daemon --stop --quiet --pidfile \
		/var/run/$NAME.pid --exec $DAEMON
	sleep 1
	start-stop-daemon --start --quiet --pidfile \
		/var/run/$NAME.pid --exec $DAEMON -- $DAEMON_OPTS
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
