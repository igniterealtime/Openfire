#!/bin/bash
export OPENFIRE_HOME=/usr/local/openfire
export JAVA_HOME=/Library/Java/Home

function shutdown() 
{
	date
	echo "Shutting down Openfire"
    kill -s TERM `ps auxww | grep -v wrapper | awk '/openfire/ && !/awk/ {print $2}'`
}

date
echo "Starting Openfire"

/usr/bin/java -server -jar "$OPENFIRE_HOME/lib/startup.jar" -Dlog4j.configurationFile=$$OPENFIRE_HOME/lib/log4j2.xml -Dopenfire.lib.dir=/usr/local/openfire/lib&

OPENFIRE_PID=`ps auxww | grep -v wrapper | awk '/openfire/ && !/awk/ {print $2}'`

# allow any signal which would kill a process to stop Openfire
trap shutdown HUP INT QUIT ABRT KILL ALRM TERM TSTP

echo "Waiting for `cat $OPENFIRE_PID`"
wait `cat $OPENFIRE_PID`
