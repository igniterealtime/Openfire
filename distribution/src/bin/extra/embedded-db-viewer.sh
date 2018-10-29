#!/bin/sh

# Script to start the HSQLDB database viewer. The embedded-db.rc file
# contains connection settings. Visit http://hsqldb.org for documentation
# on using the tool. The classpath includes JDBC drivers shipped with Openfire
# to work with the Transfer tool. You will need to add any other JDBC driver
# that you'd like to use with the transfer tool to the classpath manually.

CLASSPATH="$(dirname "$0")/../../lib/*"
export CLASSPATH

echo Starting embedded database viewer...

java -cp "$CLASSPATH" org.hsqldb.util.DatabaseManagerSwing --rcfile embedded-db.rc --urlid embedded-db
