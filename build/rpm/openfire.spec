Summary: Openfire XMPP Server
Name: openfire
Version: 3.3.0
Release: alpha
BuildRoot: %{_builddir}/%{name}-root
Source0: %{name}_src_3_3_0_alpha.tar.gz
Group: Applications/Communications
Vendor: Jive Software
License: Commercial
Requires: jdk >= 1.5.0
URL: http://www.igniterealtime.org/

%description
Openfire is a leading Open Source, cross-platform IM server based on the
XMPP (Jabber) protocol. It has great performance, is easy to setup and use,
and delivers an innovative feature set.

%prep

%build

%install
# There's no need to package this.
rm $RPM_BUILD_ROOT/opt/openfire/logs/stderr.out

%files
%defattr(-,root,root)
/opt/openfire/bin
%dir /opt/openfire/conf
%config(noreplace) /opt/openfire/conf/openfire.xml
/opt/openfire/lib
%dir /opt/openfire/logs
/opt/openfire/plugins
/opt/openfire/resources
%dir /opt/openfire/resources
/opt/openfire/resources/database
/opt/openfire/resources/i18n
/opt/openfire/resources/nativeAuth
/opt/openfire/resources/spank
%dir /opt/openfire/resources/security
%config(noreplace) /opt/openfire/resources/security/keystore
%config(noreplace) /opt/openfire/resources/security/truststore
%doc /opt/openfire/documentation
%doc /opt/openfire/LICENSE.html
%doc /opt/openfire/README.html
%doc /opt/openfire/changelog.html
