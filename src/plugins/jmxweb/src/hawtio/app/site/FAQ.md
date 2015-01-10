### General Questions

General questions on all things hawtio.

#### What is the license?

hawtio uses the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.txt).

#### What does hawtio do?

It's a [pluggable](http://hawt.io/plugins/index.html) management console for Java stuff which supports any kind of JVM, any kind of container (Tomcat, Jetty, Karaf, JBoss, Fuse Fabric, etc), and any kind of Java technology and middleware.

#### How do I install hawtio?

See the [Getting Started Guide](http://hawt.io/getstarted/index.html) and the [Configuration Guide](http://hawt.io/configuration/index.html)

#### How do I configure hawtio?

Mostly hawtio just works. However, please check out the [Configuration Guide](http://hawt.io/configuration/index.html) to see what kinds of things you can configure via system properties, environment variables, web.xml context-params or dependency injection.

#### How do I disable security?

Since 1.2-M2 of hawtio we enable security by default using the underlying application container's security mechanism.

Here's how to [disable security](https://github.com/hawtio/hawtio/blob/master/docs/Configuration.md#configuring-or-disabling-security-in-karaf-servicemix-fuse) if you wish to remove the need to login to hawtio.

#### How do I enable hawtio inside my Java Application / Spring Boot / DropWizard / Micro Service

The easiest thing to do is add jolokia as a java agent via a java agent command line:
```
java -javaagent:jolokia-agent.jar=host=0.0.0.0 -jar foo.jar
```

Then by default you can connect on http;//localhost:8778/jolokia to access the jolokia REST API.

Now you can use hawtio (e.g. the Google Chrome Extension or the stand alone hawtio application) to connect to it - and it then minimises the effect of hawtio/jolokia on your app (e.g. you don't need to mess about with whats inside your application or even change the classpath)

#### How do I connect to my remote JVM?

All thats required for hawtio to connect to any remote JVM is that a [jolokia agent](http://jolokia.org/agent.html) can be added to it. This can be done in various ways.

Firstly if you are using [Fuse](http://www.jboss.org/products/fuse) or [Apache ActiveMQ 5.9.x or later](http://activemq.apache.org/) then you already have jolokia enabled by default.

If a JVM has no jolokia agent, you can use the **Local** tab of the **Connect** menu (in 1.2.x or later of **hawtio-default.war**). The Local tab lists all local Java processes on the same machine (just like JConsole does).

For JVMs not running a jolokia agent already, there's a start button (on the right) which will dynamically add the [jolokia JVM agent](http://jolokia.org/agent/jvm.html) into the selected JVM process. You can then click on the Agent URL link to connect into it.

Note that the Local plugin only works when the JVM running hawtio has the **hawtio-local-jvm-mbean** plugin installed (which depends on the JVM finding the com.sun.tools.attach.VirtualMachine API that jconsole uses and is included in the hawtio-default.war). BTW if you don't see a **Local** tab inside the **Conect** menu in your hawtio application; check the log of your hawtio JVM; there might be a warning around com.sun.tools.attach.VirtualMachine not being available on the classpath. Or you could just try using the [exectuable jar](http://hawt.io/getstarted/index.html) to run hawtio which seems to work on most platforms.

Note also that the **Local** tab only works when the process is on the same machine as the JVM running hawtio. So a safer option is just to make sure there's a jolokia agent running in each JVM you want to manage with hawtio.

There are a [few different agents you can use](http://jolokia.org/agent.html):

* [WAR agent](http://jolokia.org/agent/war.html) if you are using a servlet / EE container
* [OSGi agent](http://jolokia.org/agent/osgi.html) if you are using OSGi (note that Jolokia is enabled by default in [Fuse](http://www.jboss.org/products/fuse) so you don't have to worry)
* [JVM agent](http://jolokia.org/agent/jvm.html) if you are using a stand alone Java process

So once you've got a jolokia agent in your JVM you can test it by accessing http://host:port/jolokia in a browser to see if you can view the JSON returned for the version information of the jolokia agent.

Assuming you have jolokia working in your JVM, then you can use the **Remote** tab on the **Connect** menu in hawtio to connect; just enter the host, port, jolokia path and user/password.

After trying the above if you have problems connecting to your JVM, please [let us know](http://hawt.io/community/index.html) by [raising an issue](https://github.com/hawtio/hawtio/issues?state=open) and we'll try to help.

#### How do I install a plugin?

Each hawtio distro has these [browser based plugins](http://hawt.io/plugins/index.html) inside already; plus hawtio can discover any other external plugins deployed in the same JVM too.

Then the hawtio UI updates itself in real time based on what it can find in the server side JVM it connects to. So, for example, if you connect to an empty tomcat/jetty you'll just see things like JMX and tomcat/jetty (and maybe wiki / dashboard / maven if you're using hawtio-default.war which has a few more server side plugins inside).

Then if you deploy a WAR which has ActiveMQ or Camel inside it, you should see an ActiveMQ or Camel tab appear as you deploy code which registers mbeans for ActiveMQ or Camel.

So usually, if you are interested in a particular plugin and its not visible in the hawtio UI (after checking your preferences in case you disabled it), usually you just need to deploy or add a server side plugin; which is usually a case of deploying some Java code (e.g. ActiveMQ, Camel, Infinispan etc).

#### What has changed lately?

Try have a look at the [change log](http://hawt.io/changelog.html) to see the latest changes in hawtio!

#### Where can I find more information?

Try have a look at the [articles and demos](http://hawt.io/articles/index.html) to see what other folks are saying about hawtio.

#### Why does hawtio log a bunch of 404s to the javascript console at startup?

The hawtio help registry tries to automatically discover help data for each registered plugin even if plugins haven't specifically registered a help file.

#### Why does hawtio have its own wiki?

At first a git-based wiki might not seem terribly relevant to hawtio. A wiki can be useful to document running systems and link to the various consoles, operational tools and views. Though in addition to being used for documentation, hawtio's wiki also lets you view and edit any text file; such as Camel routes, Fuse Fabric profiles, Spring XML files, Drools rulebases, etc.

From a hawtio perspective though its wiki pages can be HTML or Markdown and then be an AngularJS HTML partial. So it can include JavaScript widgets; or it can include [AngularJS directives](http://docs.angularjs.org/guide/directive).

This lets us use HTML and Markdown files to define custom views using HTML directives (custom tags) from any any [hawtio plugins](http://hawt.io/plugins/index.html). Hopefully over time we can build a large library of HTML directives that folks can use inside HTML or Markdown files to show attribute values or charts from MBeans in real time, to show a panel from a dashboard, etc. Then folks can make their own mashups and happy pages showing just the information they want.

So another way to think of hawtio wiki pages is as a kind of plugin or a custom format of a dashboard page. Right now each dashboard page assumes a grid layout of rectangular widgets which you can add to and then move around. However with a wiki page you can choose to render whatever information & widgets you like in whatever layout you like. You have full control over the content and layout of the page!

Here are some [sample](https://github.com/hawtio/hawtio/issues/103) [issues](https://github.com/hawtio/hawtio/issues/62) on this if you want to help!

So whether the hawtio wiki is used for documentation, to link to various hawtio and external resources, to create custom mashups or happy pages or to provide new plugin views--all the content of the wiki is audited, versioned and stored in git so it's easy to see who changed what, when and to roll back changes, etc.

### Problems/General Questions about using hawtio

Questions relating to errors you get while using hawtio or other general questions:

#### How can I hide or move tabs to different perspectives?

An easy way is to use a plugin to reconfigure the default perspective definition.  Have a look at the [custom-perspective](https://github.com/hawtio/hawtio/tree/master/hawtio-plugin-examples/custom-perspective) for a plugin-based solution.

From **hawtio 1.2.2** onwards you can reorder and hide plugins from the preference.


#### Provider sun.tools.attach.WindowsAttachProvider could not be instantiated: java.lang.UnsatisfiedLinkError: no attach in java.library.path

If you see an error like this:
```
java.util.ServiceConfigurationError: com.sun.tools.attach.spi.AttachProvider: Provider sun.tools.attach.WindowsAttachProvider could not be instantiated: java.lang.UnsatisfiedLinkError: no attach in java.library.path
```
when starting up or trying the **Connect/Local** tab then its probably related to [this issue](http://stackoverflow.com/questions/14027164/fix-the-java-lang-unsatisfiedlinkerror-no-attach-in-java-library-path) as was found on [#718](https://github.com/hawtio/hawtio/issues/718#issuecomment-27677738).

Basically you need to make sure that you have JAVA_HOME/bin on your path. e.g. try this first before starting hawtio:
```
set path=%path%;%JAVA_HOME%\jre\bin
```

### Plugin Questions

Questions on using the available plugins:

#### What plugins are available?

See the list of [hawtio plugins](http://hawt.io/plugins/index.html)

#### What is a plugin?

See [How Plugins Work](http://hawt.io/plugins/howPluginsWork.html)


#### Why does the OSGi tab not appear on GlassFish?

This is a [reported issue](https://github.com/hawtio/hawtio/issues/158). It turns out that the standard OSGi MBeans (in the osgi.core domain) are not installed by default on GlassFish.

The workaround is to install the [Gemini Management bundle](http://www.eclipse.org/gemini/management/) then you should see the MBeans in the osgi.core domain in the JMX tree; then the OSGi tab should appear!


### Camel Questions

Questions on using [Apache Camel](http://camel.apache.org/) and hawtio.

#### Why does the Debug or Trace tab not appear for my Camel route?

The Debug and Trace tabs depend on the JMX MBeans provided by the Camel release you use.

* the Debug tab requires at least version 2.12.x or later of your Camel library to be running
* the Trace tab requires either a 2.12.x or later distro of Camel or a Fuse distro of Camel from about 2.8 or later

### Developer Questions

Questions on writing new plugins or hacking on existing ones:

#### How do I build the project?

If you just want to run hawtio in a JVM then please see the [Getting Started](http://hawt.io/getstarted/index.html) section.

If you want to hack the source code then check out [how to build hawtio](http://hawt.io/building/index.html).

#### What code conventions do you have?

Check out the [Coding Conventions](https://github.com/hawtio/hawtio/blob/master/docs/CodingConventions.md) for our recommended approach.

#### What can my new plugin do?

Anything you like :). So long as it runs on a web browser, you're good to go. Please start [contributing](http://hawt.io/contributing/index.html)!

#### Do I have to use TypeScript?

You can write hawtio plugins in anything that runs in a browser and ideally compiles to JavaScript. So use pure JavaScript,  CoffeeScript, EcmaScript6-transpiler, TypeScript, GWT, Kotlin, Ceylon, ClojureScript, ScalaJS and [any language that compiles to JavaScript](http://altjs.org/).

So take your pick; the person who creates a plugin can use whatever language they prefer, so please contribute a [new plugin](http://hawt.io/contributing/index.html) :).

The only real APIs a plugin needs to worry about are AngularJS (if you want to work in the core layout rather than just be an iframe), JSON (for some pretty trivial extension points such as adding new tabs), HTML and CSS.

#### How can I add my new plugin?

Check out [how plugins work](http://hawt.io/plugins/index.html). You can then either:

* Fork this project and submit your plugin by [creating a Github pull request](https://help.github.com/articles/creating-a-pull-request) then we'll include your plugin by default in the hawtio distribution.
* Make your own WAR with your plugin added (by depending on the hawtio-web.war in your pom.xml)
* Host your plugin at some canonical website (e.g. with Github pages) then [submit an issue](https://github.com/hawtio/hawtio/issues?state=open) to tell us about it and we can add it to the plugin registry JSON file.

#### How can I reuse those awesome AngularJS directives in my application?

We hope that folks can just write plugins for hawtio to be able to reuse all the various [plugins](http://hawt.io/plugins/index.html) in hawtio.

However if you are writing your own stand alone web application using AngularJS then please check out the [Hawtio Directives](http://hawt.io/directives/) which you should be able to reuse in any AngularJS application
