#!/bin/sh

# redhat-poinstall.sh
#
# This script sets permissions on the Wildfire installtion
# and install the init script.
#
# Run this script as root after installation of wildfire
# It is expected that you are executing this script from the bin directory

# If you used an non standard directory name of location
# Please specify it here
# WILDFIRE_HOME=
 
WILDFIRE_USER="jive"
WILDFIRE_GROUP="jive"

if [ ! $WILDFIRE_HOME ]; then
	if [ -d "/opt/wildfire" ]; then
		WILDFIRE_HOME="/opt/wildfire"
	elif [ -d "/usr/local/wildfire" ]; then
		WILDFIRE_HOME="/usr/local/wildfire"
	fi
fi

# Grant execution permissions
chmod +x $WILDFIRE_HOME/bin/extra/wildfired

# Install the init script
cp $WILDFIRE_HOME/bin/extra/wildfired /etc/init.d
/sbin/chkconfig --add wildfired
/sbin/chkconfig wildfired on

# Create the jive user and group
/usr/sbin/groupadd $WILDFIRE_GROUP
/usr/sbin/useradd $WILDFIRE_USER -g $WILDFIRE_GROUP -s /bin/bash

# Change the permissions on the installtion directory
/bin/chown -R $WILDFIRE_USER:$WILDFIRE_GROUP $WILDFIRE_HOME
