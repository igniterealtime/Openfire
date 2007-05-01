Summary: Openfire XMPP Server
Name: openfire
Version: 3.3.1
Release: 1
BuildRoot: %{_builddir}/%{name}-root
Source0: %{name}_src_3_3_1.tar.gz
Group: Applications/Communications
Vendor: Jive Software
Packager: Jive Software
License: GPL
# May reevaluate this at some point.
#Requires: jdk >= 1.5.0
URL: http://www.igniterealtime.org/

%description
Openfire is a leading Open Source, cross-platform IM server based on the
XMPP (Jabber) protocol. It has great performance, is easy to setup and use,
and delivers an innovative feature set.

%prep

%build

%install
# Need to evaluate this.  Required, but need to figure out logistics.
#rm -rf $RPM_BUILD_ROOT
# We prefer /usr/local to /opt.
mkdir -p $RPM_BUILD_ROOT/usr/local
mv $RPM_BUILD_ROOT/opt/openfire $RPM_BUILD_ROOT/usr/local/openfire
rmdir $RPM_BUILD_ROOT/opt
# There's no need to package this.
rm $RPM_BUILD_ROOT/usr/local/openfire/logs/stderr.out
# Set up the init script.
mkdir -p $RPM_BUILD_ROOT/etc/init.d
cp $RPM_BUILD_ROOT/usr/local/openfire/bin/extra/redhat/openfire $RPM_BUILD_ROOT/etc/init.d/openfire
chmod 755 $RPM_BUILD_ROOT/etc/init.d/openfire
# Make the startup script executable.
chmod 755 $RPM_BUILD_ROOT/usr/local/openfire/bin/openfire.sh
# Set up the sysconfig file.
mkdir -p $RPM_BUILD_ROOT/etc/sysconfig
cp $RPM_BUILD_ROOT/usr/local/openfire/bin/extra/redhat/openfire-sysconfig $RPM_BUILD_ROOT/etc/sysconfig/openfire
# Create /etc based config directory.
mkdir -p $RPM_BUILD_ROOT/etc/openfire

%clean
rm -rf $RPM_BUILD_ROOT

%preun
[ -x "/etc/init.d/openfire" ] && /etc/init.d/openfire stop
/sbin/chkconfig --del openfire

%post
/sbin/chkconfig --add openfire

%files
%defattr(-,daemon,daemon)
%dir /usr/local/openfire
/usr/local/openfire/bin
%dir /usr/local/openfire/conf
%config(noreplace) /usr/local/openfire/conf/openfire.xml
/usr/local/openfire/lib
%dir /usr/local/openfire/logs
/usr/local/openfire/plugins
%dir /usr/local/openfire/resources
/usr/local/openfire/resources/database
/usr/local/openfire/resources/i18n
/usr/local/openfire/resources/nativeAuth
/usr/local/openfire/resources/spank
%dir /usr/local/openfire/resources/security
%config(noreplace) /usr/local/openfire/resources/security/keystore
%config(noreplace) /usr/local/openfire/resources/security/truststore
%doc /usr/local/openfire/documentation
%doc /usr/local/openfire/LICENSE.html 
%doc /usr/local/openfire/README.html 
%doc /usr/local/openfire/changelog.html
/etc/init.d/openfire
%config(noreplace) /etc/sysconfig/openfire

%changelog
* Mon Apr 30 2007 Daniel Henninger <jadestorm@nc.rr.com> 3.3.1
- Initial RPM creation.
