Openfire ![alt tag](https://raw.githubusercontent.com/igniterealtime/IgniteRealtime-Website/master/src/main/webapp/images/logo_openfire.gif)
========
[![Build Status](https://travis-ci.org/igniterealtime/Openfire.svg?branch=master)](https://travis-ci.org/igniterealtime/Openfire)  [![Project Stats](https://www.openhub.net/p/Openfire/widgets/project_thin_badge.gif)](https://www.openhub.net/p/Openfire)

About
-----
[Openfire] is a real time collaboration (RTC) server licensed under the Open Source Apache License. It uses the only widely adopted open protocol for instant messaging, XMPP (also called Jabber). Openfire is incredibly easy to setup and administer, but offers rock-solid security and performance.

[Openfire] is a XMPP server licensed under the Open Source Apache License.

[Openfire] - an [Ignite Realtime] community project.

Bug Reporting
-------------

Only a few users have access for for filling bugs in the tracker. New
users should:

1. Create a Discourse account
2. Login to a Discourse account
3. Click on the New Topic button
4. Choose the [Openfire Dev category](https://discourse.igniterealtime.org/c/openfire/openfire-dev) and provide a detailed description of the bug.

Please search for your issues in the bug tracker before reporting.

Resources
---------

- Documentation: http://www.igniterealtime.org/projects/openfire/documentation.jsp
- Community: https://discourse.igniterealtime.org/c/openfire
- Bug Tracker: http://issues.igniterealtime.org/browse/OF
- Nightly Builds: http://www.igniterealtime.org/downloads/nightly_openfire.jsp

Ignite Realtime
===============

[Ignite Realtime] is an Open Source community composed of end-users and developers around the world who 
are interested in applying innovative, open-standards-based Real Time Collaboration to their businesses and organizations. 
We're aimed at disrupting proprietary, non-open standards-based systems and invite you to participate in what's already one 
of the biggest and most active Open Source communities.

[Openfire]: http://www.igniterealtime.org/projects/openfire/index.jsp
[Ignite Realtime]: http://www.igniterealtime.org
[XMPP (Jabber)]: http://xmpp.org/

Making changes
==============
The project uses [Maven](https://maven.apache.org/) and as such should import straight in to your favourite Java IDE.
The directory structure is fairly straightforward. The code is contained in two key folders:

* `Openfire/xmppserver` - a Maven module representing the core code for Openfire itself
* `Openfire/plugins` - a number of modules for the various [plugins](https://www.igniterealtime.org/projects/openfire/plugins.jsp) available

Other folders are:  
* `Openfire/build` - various files use to create installers for different platforms
* `Openfire/distribution` - a Maven module used to bring all the parts together
* `Openfire/documentation` - the documentation hosted at [igniterealtime.org](https://www.igniterealtime.org/projects/openfire/documentation.jsp)
* `Openfire/i18n` - files used for internationalisation of the admin interface
* `Openfire/starter` - a small module that allows Openfire to start in a consistent manner on different platforms

To build the complete project including plugins, run the command
```
mvn verify
```  

However much of the time it is only necessary to make changes to the core XMPP server itself in which case the command
```
mvn verify -pl distribution -am 
```  
will compile the core server and any dependencies, and then assemble it in to something that can be run. 

Testing your changes
--------------------
Although your IDE will happily compile the project unfortunately, it's not possible to run Openfire from within an 
IDE - it must be done at the command line. After building the project using Maven, simply run the shell script or 
batch file to start Openfire;
```
./distribution/target/distribution-base/bin/openfire.sh
```
or
```
.\distribution\target\distribution-base\bin\openfire.bat
```

Adding `-debug` as the first parameter to the script will start the server in debug mode, and your IDE should be able
to attach a remote debugger if necessary.

Compiling a plugin
------------------
Compiling the complete project will build all the plugins - however to test changes to a plugin it's often quicker to 
compile an individual plugin by specifing the `pom.xml` for that plugin, for example;
```
mvn verify -f plugins/broadcast/pom.xml
```
