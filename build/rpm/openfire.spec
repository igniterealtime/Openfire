Summary: Openfire XMPP Server
Name: openfire
Version: %{OPENFIRE_VERSION}
Release: 1
BuildRoot: %{_builddir}/%{name}-root
Source0: %{OPENFIRE_SOURCE}
%ifnarch noarch
Source1: jre-dist.tar.gz
%endif
Group: Applications/Communications
Vendor: Jive Software
Packager: Jive Software
License: Apache license v2.0
AutoReqProv: no
URL: http://www.igniterealtime.org/

%define prefix /opt
%define homedir %{prefix}/openfire
# couldn't find another way to disable the brp-java-repack-jars which was called in __os_install_post
%define __os_install_post %{nil}
%define debug_package %{nil}

%description
Openfire is a leading Open Source, cross-platform IM server based on the
XMPP (Jabber) protocol. It has great performance, is easy to setup and use,
and delivers an innovative feature set.

This particular release includes a bundled JRE.

%prep
%setup -q -n openfire_src

%build
cd build
ant openfire
ant -Dplugin=search plugin
cd ..

%install
# Prep the install location.
rm -rf $RPM_BUILD_ROOT
mkdir -p $RPM_BUILD_ROOT%{prefix}
# Copy over the main install tree.
cp -R target/openfire $RPM_BUILD_ROOT%{homedir}
%ifnarch noarch
# Set up distributed JRE
pushd $RPM_BUILD_ROOT%{homedir}
gzip -cd %{SOURCE1} | tar xvf -
popd
%endif
# Set up the init script.
mkdir -p $RPM_BUILD_ROOT/etc/init.d
cp $RPM_BUILD_ROOT%{homedir}/bin/extra/redhat/openfire $RPM_BUILD_ROOT/etc/init.d/openfire
chmod 755 $RPM_BUILD_ROOT/etc/init.d/openfire
# Make the startup script executable.
chmod 755 $RPM_BUILD_ROOT%{homedir}/bin/openfire.sh
# Set up the sysconfig file.
mkdir -p $RPM_BUILD_ROOT/etc/sysconfig
cp $RPM_BUILD_ROOT%{homedir}/bin/extra/redhat/openfire-sysconfig $RPM_BUILD_ROOT/etc/sysconfig/openfire
# Copy over the documentation
cp -R documentation $RPM_BUILD_ROOT%{homedir}/documentation
cp changelog.html $RPM_BUILD_ROOT%{homedir}/
cp LICENSE.html $RPM_BUILD_ROOT%{homedir}/
cp README.html $RPM_BUILD_ROOT%{homedir}/
# Copy over the i18n files
cp -R resources/i18n $RPM_BUILD_ROOT%{homedir}/resources/i18n
# Make sure scripts are executable
chmod 755 $RPM_BUILD_ROOT%{homedir}/bin/extra/openfired
chmod 755 $RPM_BUILD_ROOT%{homedir}/bin/extra/redhat-postinstall.sh
# Move over the embedded db viewer pieces
mv $RPM_BUILD_ROOT%{homedir}/bin/extra/embedded-db.rc $RPM_BUILD_ROOT%{homedir}/bin
mv $RPM_BUILD_ROOT%{homedir}/bin/extra/embedded-db-viewer.sh $RPM_BUILD_ROOT%{homedir}/bin
# We don't really need any of these things.
rm -rf $RPM_BUILD_ROOT%{homedir}/bin/extra
rm -f $RPM_BUILD_ROOT%{homedir}/bin/*.bat
rm -rf $RPM_BUILD_ROOT%{homedir}/resources/nativeAuth/osx-ppc
rm -rf $RPM_BUILD_ROOT%{homedir}/resources/nativeAuth/solaris-sparc
rm -rf $RPM_BUILD_ROOT%{homedir}/resources/nativeAuth/win32-x86
rm -f $RPM_BUILD_ROOT%{homedir}/lib/*.dll

%clean
rm -rf $RPM_BUILD_ROOT

%preun
if [ "$1" == "0" ]; then
	# This is an uninstall, instead of an upgrade.
	/sbin/chkconfig --del openfire
	[ -x "/etc/init.d/openfire" ] && /etc/init.d/openfire stop
fi
# Force a happy exit even if openfire shutdown script didn't exit cleanly.
exit 0

%post
chown -R daemon:daemon %{homedir}
if [ "$1" == "1" ]; then
	# This is a fresh install, instead of an upgrade.
	/sbin/chkconfig --add openfire
fi

# Trigger a restart.
[ -x "/etc/init.d/openfire" ] && /etc/init.d/openfire condrestart

# Force a happy exit even if openfire condrestart script didn't exit cleanly.
exit 0

%files
%defattr(-,daemon,daemon)
%attr(750, daemon, daemon) %dir %{homedir}
%dir %{homedir}/bin
%{homedir}/bin/openfire.sh
%{homedir}/bin/openfirectl
%config(noreplace) %{homedir}/bin/embedded-db.rc
%{homedir}/bin/embedded-db-viewer.sh
%dir %{homedir}/conf
%config(noreplace) %{homedir}/conf/openfire.xml
%dir %{homedir}/lib
%{homedir}/lib/*.jar
%{homedir}/lib/log4j.xml
%dir %{homedir}/logs
%dir %{homedir}/plugins
%{homedir}/plugins/search.jar
%dir %{homedir}/plugins/admin
%{homedir}/plugins/admin/*
%dir %{homedir}/resources
%dir %{homedir}/resources/database
%{homedir}/resources/database/*.sql
%dir %{homedir}/resources/database/upgrade
%dir %{homedir}/resources/database/upgrade/*
%{homedir}/resources/database/upgrade/*/*
%dir %{homedir}/resources/i18n
%{homedir}/resources/i18n/*
%dir %{homedir}/resources/nativeAuth
%dir %{homedir}/resources/nativeAuth/linux-i386
%{homedir}/resources/nativeAuth/linux-i386/*
%dir %{homedir}/resources/security
%dir %{homedir}/resources/spank
%{homedir}/resources/spank/index.html
%dir %{homedir}/resources/spank/WEB-INF
%{homedir}/resources/spank/WEB-INF/web.xml
%config(noreplace) %{homedir}/resources/security/keystore
%config(noreplace) %{homedir}/resources/security/truststore
%config(noreplace) %{homedir}/resources/security/client.truststore
%doc %{homedir}/documentation
%doc %{homedir}/LICENSE.html 
%doc %{homedir}/README.html 
%doc %{homedir}/changelog.html
%{_sysconfdir}/init.d/openfire
%config(noreplace) %{_sysconfdir}/sysconfig/openfire
%ifnarch noarch
%{homedir}/jre
%endif

%changelog
* %{OPENFIRE_BUILDDATE} Jive Software <webmaster@jivesoftware.com> %{OPENFIRE_VERSION}-1
- Automatic RPM build.
