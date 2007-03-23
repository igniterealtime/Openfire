Summary: Openfire XMPP Server
Name: openfire
Version: 3.2.2
Release: 1
BuildRoot: %{_builddir}/%{name}-root
Source0: %{name}_src_3_2_2.tar.gz
Group: Applications/Communications
Vendor: Jive Software
License: Commercial
URL: www.igniterealtime.org
%description
Openfire XMPP Server
%prep
pwd
%build
%install
%files
%defattr(-,root,root)
/opt/openfire/bin
/opt/openfire/lib
/opt/openfire/logs/stderr.out
/opt/openfire/plugins
/opt/openfire/resources
/opt/openfire/documentation
%config /opt/openfire/conf/wildfire.xml
%docdir /opt/openfire/documentation
%doc /opt/openfire/LICENSE.html 
%doc /opt/openfire/README.html 
%doc /opt/openfire/changelog.html
