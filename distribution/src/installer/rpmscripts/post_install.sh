
chown -R daemon:daemon $I4J_INSTALL_LOCATION
if [ "$1" == "1" ]; then
	# This is a fresh install, instead of an upgrade.
	/sbin/chkconfig --add openfire
fi

# Trigger a restart.
[ -x "/etc/init.d/openfire" ] && /etc/init.d/openfire condrestart

# copy a default sysconfig setting
if [ ! -f "/etc/sysconfig/openfire" ]; then
	if [ ! -f "/etc/sysconfig/openfire.rpmsave" ]; then
		cp /etc/sysconfig/openfire.rpmsave /etc/sysconfig/openfire
	else
		cp bin/extra/redhat/openfire-sysconfig /etc/sysconfig/openfire
	fi
fi