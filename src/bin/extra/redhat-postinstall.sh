#!/bin/sh

# redhat-poinstall.sh
#
# This script sets permissions on the Jive Messenger installtion
# and install the init script.
#
# Run this script as root after installation of jive messenger
# It is expected that you are executing this script from the bin directory

# If you used an non standard directory name of location
# Please specify it here
# MESSENGER_HOME=
 
MESSENGER_USER="jive"
MESSENGER_GROUP="jive"

if [ ! $MESSENGER_HOME ]; then
	if [ -d "/opt/jive_messenger" ]; then
		MESSENGER_HOME="/opt/jive_messsenger"
	elif [ -d "/usr/local/jive_messenger" ]; then
		MESSENGER_HOME="/usr/local/jive_messenger"
	fi
fi

# Install the init script
cp $MESSENGER_HOME/bin/extra/jive-messengerd /etc/init.d
/sbin/chkconfig --add jive-messengerd
/sbin/chkconfig jive-messengerd on

# Create the jive user and group
/usr/sbin/groupadd $MESSENGER_GROUP
/usr/sbin/useradd $MESSENGER_USER -g $MESSENGER_GROUP

# Change the permissions on the installtion directory
/bin/chown -R $MESSENGER_USER:$MESSENGER_GROUP $MESSENGER_HOME 
