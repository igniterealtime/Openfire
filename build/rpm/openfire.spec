Summary: Openfire XMPP Server
Name: openfire
Version: 3.3.1
Release: 1
BuildRoot: %{_builddir}/%{name}-root
Source0: %{name}_src_3_3_1.tar.gz
Source1: jre-dist.tar.gz
Group: Applications/Communications
Vendor: Jive Software
Packager: Jive Software
License: GPL
AutoReqProv: no
URL: http://www.igniterealtime.org/

%define prefix /opt
%define homedir %{prefix}/openfire

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
# Set up distributed JRE
pushd $RPM_BUILD_ROOT%{homedir}
gzip -cd %{SOURCE1} | tar xvf -
popd
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
rm -rf $RPM_BUILD_ROOT%{homedir}/resources/spank

%clean
rm -rf $RPM_BUILD_ROOT

%preun
[ -x "/etc/init.d/openfire" ] && /etc/init.d/openfire stop
/sbin/chkconfig --del openfire

%post
/sbin/chkconfig --add openfire

%files
%defattr(-,daemon,daemon)
%dir %{homedir}
%dir %{homedir}/bin
%{homedir}/bin/openfire.sh
%config(noreplace) %{homedir}/bin/embedded-db.rc
%{homedir}/bin/embedded-db-viewer.sh
%dir %{homedir}/conf
%config(noreplace) %{homedir}/conf/openfire.xml
%dir %{homedir}/lib
%{homedir}/lib/*.jar
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
%{homedir}/resources/security
%doc %{homedir}/documentation
%doc %{homedir}/LICENSE.html 
%doc %{homedir}/README.html 
%doc %{homedir}/changelog.html
%{_sysconfdir}/init.d/openfire
%config(noreplace) %{_sysconfdir}/sysconfig/openfire
%{homedir}/jre

%changelog
* Mon Apr 30 2007 Daniel Henninger <jadestorm@nc.rr.com> 3.3.1-1
- Initial RPM creation.
