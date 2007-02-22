Summary: Wildfire XMPP Server
Name: wildfire
Version: 3.2.2
Release: 1
BuildRoot: %{_builddir}/%{name}-root
Source0: %{name}_src_3_2_2.tar.gz
Group: Applications/Communications
Vendor: Jive Software
License: Commercial
URL: www.igniterealtime.org
%description
Wildfire XMPP Server
%prep
pwd
%build
%install
%files
%defattr(-,root,root)
/opt/wildfire/bin
/opt/wildfire/lib
/opt/wildfire/logs/stderr.out
/opt/wildfire/plugins
/opt/wildfire/resources
/opt/wildfire/documentation
%config /opt/wildfire/conf/wildfire.xml
%docdir /opt/wildfire/documentation
%doc /opt/wildfire/LICENSE.html 
%doc /opt/wildfire/README.html 
%doc /opt/wildfire/changelog.html
