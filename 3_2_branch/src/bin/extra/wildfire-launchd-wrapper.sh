#!/bin/bash
export WILDFIRE_HOME=/usr/local/wildfire
export JAVA_HOME=/Library/Java/Home

function shutdown() 
{
	date
	echo "Shutting down Wildfire"
    kill -s TERM `ps auxww | grep -v wrapper | awk '/wildfire/ && !/awk/ {print $2}'`
}

date
echo "Starting Wildfire"

/usr/bin/java -server -jar "$WILDFIRE_HOME/lib/startup.jar" -Dwildfire.lib.dir=/usr/local/wildfire/lib&

WILDFIRE_PID=`ps auxww | grep -v wrapper | awk '/wildfire/ && !/awk/ {print $2}'`

# allow any signal which would kill a process to stop Wildfire
trap shutdown HUP INT QUIT ABRT KILL ALRM TERM TSTP

echo "Waiting for `cat $WILDFIRE_PID`"
wait `cat $WILDFIRE_PID`
