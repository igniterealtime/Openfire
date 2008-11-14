#!/bin/sh

# redhat-poinstall.sh
#
# This script sets permissions on the Openfire installtion
# and install the init script.
#
# Run this script as root after installation of openfire
# It is expected that you are executing this script from the bin directory

# If you used an non standard directory name of location
# Please specify it here
# OPENFIRE_HOME=
 
OPENFIRE_USER="jive"
OPENFIRE_GROUP="jive"

if [ ! $OPENFIRE_HOME ]; then
	if [ -d "/opt/openfire" ]; then
		OPENFIRE_HOME="/opt/openfire"
	elif [ -d "/usr/local/openfire" ]; then
		OPENFIRE_HOME="/usr/local/openfire"
	fi
fi

# Grant execution permissions 
chmod +x $OPENFIRE_HOME/bin/extra/openfired

# Install the init script
cp $OPENFIRE_HOME/bin/extra/openfired /etc/init.d
/sbin/chkconfig --add openfired
/sbin/chkconfig openfired on

# Create the jive user and group
/usr/sbin/groupadd $OPENFIRE_GROUP
/usr/sbin/useradd $OPENFIRE_USER -g $OPENFIRE_GROUP -s /bin/bash

# Change the permissions on the installtion directory
/bin/chown -R $OPENFIRE_USER:$OPENFIRE_GROUP $OPENFIRE_HOME
