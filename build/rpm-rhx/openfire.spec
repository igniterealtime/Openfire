Summary: Openfire XMPP Server
Name: openfire
Version: 3.4.1
Release: 1
BuildRoot: %{_builddir}/%{name}-root
Source0: %{name}_src_3_4_1.tar.gz
Group: Applications/Communications
Vendor: Jive Software
Packager: Jive Software
License: GPL
#Requires: java-1.5.0
#BuildRequires: java-1.5.0-devel
URL: http://www.igniterealtime.org/

%define confdir %{_sysconfdir}/%{name}
%define homedir %{_datadir}/%{name}
%define logdir %{_localstatedir}/log/%{name}

%description
Openfire is a leading Open Source, cross-platform IM server based on the
XMPP (Jabber) protocol. It has great performance, is easy to setup and use,
and delivers an innovative feature set.

%prep
%setup -q -n openfire_src

%build
cd build
ant openfire
ant -Dplugin=search plugin
cd ..

%install
# Prep the install location
rm -rf $RPM_BUILD_ROOT
mkdir -p $RPM_BUILD_ROOT%{_datadir}
# Copy over the main install
cp -R target/openfire $RPM_BUILD_ROOT%{homedir}
# Set up the init script.
mkdir -p $RPM_BUILD_ROOT%{_sysconfdir}/init.d
cp $RPM_BUILD_ROOT%{homedir}/bin/extra/redhat/openfire $RPM_BUILD_ROOT%{_sysconfdir}/init.d/openfire
chmod 755 $RPM_BUILD_ROOT%{_sysconfdir}/init.d/openfire
# Make the startup script executable.
chmod 755 $RPM_BUILD_ROOT%{homedir}/bin/openfire.sh
# Set up the sysconfig file.
mkdir -p $RPM_BUILD_ROOT%{_sysconfdir}/sysconfig
cp $RPM_BUILD_ROOT%{homedir}/bin/extra/redhat/openfire-sysconfig $RPM_BUILD_ROOT%{_sysconfdir}/sysconfig/openfire
# Create /etc based config directory.
mv $RPM_BUILD_ROOT%{homedir}/conf $RPM_BUILD_ROOT%{confdir}
ln -s %{confdir} $RPM_BUILD_ROOT%{homedir}/conf
mv $RPM_BUILD_ROOT%{homedir}/resources/security $RPM_BUILD_ROOT%{confdir}/security
ln -s %{confdir}/security $RPM_BUILD_ROOT%{homedir}/resources/security
# Create proper log path
mkdir -p $RPM_BUILD_ROOT%{logdir}
rmdir $RPM_BUILD_ROOT%{homedir}/logs
ln -s %{logdir} $RPM_BUILD_ROOT%{homedir}/logs
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
[ -x "/etc/init.d/openfire" ] && /etc/init.d/openfire stop
/sbin/chkconfig --del openfire

%post
/sbin/chkconfig --add openfire

%files
%defattr(-,daemon,daemon)
%dir %{homedir}
%dir %{homedir}/bin
%{homedir}/bin/openfire.sh
%{homedir}/conf
%config(noreplace) %{confdir}/openfire.xml
%config(noreplace) %{confdir}/security/keystore
%config(noreplace) %{confdir}/security/truststore
%config(noreplace) %{confdir}/security/client.truststore
%dir %{homedir}/lib
%{homedir}/lib/*.jar
%{homedir}/logs
%dir %{logdir}
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
%dir %{homedir}/resources/spank
%{homedir}/resources/spank/*.swf
%{homedir}/resources/spank/*.html
%dir %{homedir}/resources/spank/scripts
%{homedir}/resources/spank/scripts/*
%dir %{homedir}/resources/spank/WEB-INF
%{homedir}/resources/spank/WEB-INF/*
%{homedir}/resources/security
%doc %{homedir}/documentation
%doc %{homedir}/LICENSE.html 
%doc %{homedir}/README.html 
%doc %{homedir}/changelog.html
%{_sysconfdir}/init.d/openfire
%config(noreplace) %{_sysconfdir}/sysconfig/openfire

%changelog
* Thu Nov 08 2007 Daniel Henninger <jadestorm@nc.rr.com> 3.4.1-1
- Updated to version 3.4.1.

* Mon Apr 30 2007 Daniel Henninger <jadestorm@nc.rr.com> 3.3.1-1
- Initial RPM creation.
