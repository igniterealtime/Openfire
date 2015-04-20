Currently hawtio uses JMX to discover which MBeans are present and then dynamically updates the navigation bars and tabs based on what it finds. The UI is updated whenever hawtio reloads the mbeans JSON; which it does periodically or a plugin can trigger explicitly.

So you can deploy the standard [hawtio-web.war](https://oss.sonatype.org/content/repositories/public/io/hawt/hawtio-web/1.4.2/hawtio-web-1.4.2.war); then as you deploy more services to your container, hawtio will update itself to reflect the suitable plugins in the UI.

Relying on JMX for discovery doesn't mean though that plugins can only interact with JMX; they can do anything at all that a browser can. e.g. a plugin could use REST to discover UI capabilities and other plugins.

A hawtio plugin is anything that will run inside a browser. We've tried to keep hawtio plugins as technology agnostic as possible; so a plugin is just some combination of JS / HTML / CSS / markup / images and other content that can be loaded in a browser.


## What is a Plugin?

From a plugin developer's perspective a plugin is just a set of resources; usually at least one JavaScript file.

For [all the plugins](http://hawt.io/plugins/index.html) we've done so far we've picked [AngularJS](http://angularjs.org/) as the UI framework, which has nice a two-way binding between the HTML markup and the JS data model along with modularisation, web directives and dependency injection.

We're using TypeScript to generate the JS code to get syntax for modules, classes, interfaces, type inference and static type checking; but folks can use anything that compiles to JS (e.g. vanilla JS or JSLint / Google Closure, CoffeeScript or any of the JVM language -> JS translators like GWT, Kotlin, Ceylon etc)

In terms of JS code, we're using JavaScript modules to keep things separated, so plugins can't conflict but they can work together if required. From an AngularJS perspective we're using AngularJS's modules and dependency injection; which makes it easy for plugins to interact with each other & share services between them. e.g. plugins which want to interact with or listen to changes in the MBean tree can be injected with the Workspace service etc.

### Example Plugin

If you want so see some example code, here's a [log plugin](https://github.com/hawtio/hawtio/blob/master/hawtio-web/src/main/webapp/app/log/js/logPlugin.ts) designed to work with an MBean which queries the log statements from SLF4J/log4j, etc.

* We can [map single page URIs templates](https://github.com/hawtio/hawtio/blob/master/hawtio-web/src/main/webapp/app/log/js/logPlugin.ts#L5) to HTML templates (partials) and controllers. This will add the view at http://localhost:8080/hawtio/#/logs if you are running hawtio locally.
* These AngularJS modules can be added and removed at runtime inside the same single page application without requiring a reload.
* [Here's where we register a top-level navigation bar item](https://github.com/hawtio/hawtio/blob/master/hawtio-web/src/main/webapp/app/log/js/logPlugin.ts#L12) for this the new log tab.
* Here's a [sub tab in the JMX plugin](https://github.com/hawtio/hawtio/blob/master/hawtio-web/src/main/webapp/app/log/js/logPlugin.ts#L19) which is only visible if you select a node in the JMX tree.

Thanks to the dependency injection of [AngularJS](http://angularjs.org/) different plugins can expose services and perform various kinds of integration and wiring together.

## Adding Your Own Plugins

There are various ways of adding your own plugins to hawtio:

### Static Linking

The simplest way to make plugins available is to statically link them inside the WAR hosting the hawtio web application.

e.g. if you create a maven WAR project and [add the hawtio-web WAR dependency and use the maven war plugin](https://github.com/hawtio/hawtio/blob/master/sample/pom.xml#L17) you can then add your own plugins into the **src/main/webapp/app** directory.

### Separate Deployment Unit

Plugins can be packaged up as a separate deployment unit (WAR, OSGi bundle, EAR, etc) and then deployed like any other deployment unit.

The plugin then needs to expose a hawtio plugin MBean instance which describes how to load the plugin artifacts (e.g. local URLs inside the container or public URLs to some website). See the [plugin examples](https://github.com/hawtio/hawtio/tree/master/hawtio-plugin-examples) for more details.

So plugins can be deployed into the JVM via whatever container you prefer (web container, OSGi, JEE).

To see how this works check out the [plugin examples and detailed description](https://github.com/hawtio/hawtio/blob/master/hawtio-plugin-examples/readme.md).

### Using a Registry

We've not fully implemented this yet--but we can have a simple JSON registry inside the hawtio application which maps known MBean object names to external plugins. We can then auto-install plugins using trusted JSON repositories of plugins.

This has the benefit of not requiring any changes to the JVM being managed (other than Jolokia being inside).

Here is a [sample JSON file](https://github.com/hawtio/hawtio/blob/master/hawtio-web/src/main/webapp/test.json) to show the kind of thing we mean.

### Plugin Manager (plugin)

We could add a "plugin manager" plugin to allow users to add new plugins either into the JVM, some registry or purely on the client side with local storage. So rather like with [jenkins](http://jenkins-ci.org/) you can install new plugins from a repository of well known plugins, we could add the same capability to hawtio.

