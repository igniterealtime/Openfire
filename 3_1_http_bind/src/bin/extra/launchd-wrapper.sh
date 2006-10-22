#!/bin/bash

export WILDFIRE_HOME=/usr/local/wildfire
function shutdown() 
{
	date
	echo "Shutting down Wildfire"
	$WILDFIRE_HOME/bin/wildfire.sh stop
}

date 
echo "Starting Wildfire"
export CATALINA_PID=/tmp/$$

# uncomment to increase Wildfire's maximum heap allocation
# export JAVA_OPTS="-Xmx512M $JAVA_OPTS"

$WILDFIRE_HOME/bin/wildfire.sh start

# allow any signal which would kill a process to stop Wildfire
trap shutdown HUP INT QUIT ABRT KILL ALRM TERM TSTP

echo "Waiting for `cat $WILDFIRE_PID`"
wait `cat $WILDFIRE_PID`