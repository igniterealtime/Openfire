Openfire ![alt tag](https://raw.githubusercontent.com/igniterealtime/IgniteRealtime-Website/master/src/main/webapp/images/logo_openfire.gif)
========
[![Openfire CI](https://github.com/igniterealtime/Openfire/workflows/Openfire%20CI/badge.svg?branch=master)](https://github.com/igniterealtime/Openfire/actions?query=workflow%3A%22Openfire+CI%22+branch%3Amaster)  [![Project Stats](https://www.openhub.net/p/Openfire/widgets/project_thin_badge.gif)](https://www.openhub.net/p/Openfire)

About
-----
[Openfire] is a real time collaboration (RTC) server licensed under the Open Source Apache License. It uses the only widely adopted open protocol for instant messaging, XMPP (also called Jabber). Openfire is incredibly easy to setup and administer, but offers rock-solid security and performance.

[Openfire] is a XMPP server licensed under the Open Source Apache License.

[Openfire] - an [Ignite Realtime] community project.

Bug Reporting
-------------

Only a few users have access for filling bugs in the tracker. New
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
- Bug Tracker: https://igniterealtime.atlassian.net/browse/OF
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
The directory structure is fairly straightforward. The main code is contained in:

* `Openfire/xmppserver` - a Maven module representing the core code for Openfire itself

Other folders are:  
* `Openfire/build` - various files use to create installers for different platforms
* `Openfire/distribution` - a Maven module used to bring all the parts together
* `Openfire/documentation` - the documentation hosted at [igniterealtime.org](https://www.igniterealtime.org/projects/openfire/documentation.jsp)
* `Openfire/i18n` - files used for internationalisation of the admin interface
* `Openfire/plugins` - Maven configuration files to allow the various [plugins](https://www.igniterealtime.org/projects/openfire/plugins.jsp) available to be built
* `Openfire/starter` - a small module that allows Openfire to start in a consistent manner on different platforms

To build the complete project including plugins, run the command
```
./mvnw verify
```  

However much of the time it is only necessary to make changes to the core XMPP server itself in which case the command
```
./mvnw verify -pl distribution -am 
```  
will compile the core server and any dependencies, and then assemble it in to something that can be run. 

Testing your changes
--------------------

#### IntelliJ IDEA:

1. Run -> Edit Configurations... -> Add Application
2. fill in following values
    1. Name: Openfire
    2. Use classpath of module: starter
    3. Main class: org.jivesoftware.openfire.starter.ServerStarter
    4. VM options (adapt accordingly):
        ````
        -DopenfireHome="-absolute path to your project folder-\distribution\target\distribution-base" 
        -Xverify:none
        -server
        -Dlog4j.configurationFile="-absolute path to your project folder-\distribution\target\distribution-base\lib\log4j2.xml"
        -Dopenfire.lib.dir="-absolute path to your project folder-\distribution\target\distribution-base\lib"
        -Dfile.encoding=UTF-8
       ````
   5. Working directory: -absolute path to your project folder-
3. apply

You need to execute `mvnw verify` before you can launch openfire.

#### Other IDE's:

Although your IDE will happily compile the project, unfortunately it's not possible to run Openfire from within the 
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
