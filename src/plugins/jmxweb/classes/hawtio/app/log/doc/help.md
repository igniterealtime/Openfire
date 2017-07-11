### Logs

When we run middleware we spend an awful lot of time looking at and searching logs. With [hawtio](http://hawt.io/) we don't just do logs, we do _hawt logs_.

Sure logs are nicely coloured and easily filtered as you'd expect. But with hawtio we try to link all log statements and exception stack traces to the actual lines of code which generated them; so if a log statement or line of exception doesn't make sense - just click the link and view the source code! Thats _hawt_!

We can't guarantee to always be able to do download the source code; we need to find the maven coordinates (group ID, artifact ID, version) of each log statement or line of stack trace to be able to do this. So awesomeness is not guaranteed, but it should work for projects which have published their source code to either the global or your local maven repository.

We should try encourage all projects to publish their source code to maven repositories (even if only internally to an internal maven repo for code which is not open source).

##### How to enable hawtio logs

Hawtio uses an MBean usually called LogQuery which implements the [LogQuerySupportMBean interface](https://github.com/fusesource/fuse/blob/master/insight/insight-log-core/src/main/java/org/fusesource/insight/log/support/LogQuerySupportMBean.java#L26) from either the [insight-log](https://github.com/fusesource/fuse/tree/master/insight/insight-log) or [insight-log4j](https://github.com/fusesource/fuse/tree/master/insight/insight-log4j) bundles depending on if you are working inside or outside of OSGi respectively.

If you are using OSGi and use [Fuse Fabric](http://fuse.fusesource.org/fabric/) or [Fuse ESB](http://fusesource.com/products/fuse-esb-enterprise/) you already have [insight-log](https://github.com/fusesource/fuse/tree/master/insight/insight-log) included. If not, or you are just using Apache Karaf just add the **insight-log** bundle.

If you are not using OSGi then you just need to ensure you have [insight-log4j](https://github.com/fusesource/fuse/tree/master/insight/insight-log4j) in your WAR when you deploy hawtio; which is already included in the [hawtio sample war](https://github.com/hawtio/hawtio/tree/master/sample).

Then you need to ensure that the LogQuery bean is instantiated in whatever dependency injection framework you choose. For example this is [how we initialise LogQuery](https://github.com/hawtio/hawtio/blob/master/sample/src/main/webapp/WEB-INF/applicationContext.xml#L12) in the [sample war](https://github.com/hawtio/hawtio/tree/master/sample) using spring XML:

    <bean id="logQuery" class="org.fusesource.insight.log.log4j.Log4jLogQuery"
          lazy-init="false" scope="singleton"
          init-method="start" destroy-method="stop"/>

##### Things that could go wrong

* If you have no Logs tab in hawtio, then
     * if you are in OSGi it means you are not running either the [insight-log bundle](https://github.com/fusesource/fuse/tree/master/insight/insight-log)
     * if you are outside of OSGi it means you have not added the [insight-log4j jar](https://github.com/fusesource/fuse/tree/master/insight/insight-log4j) to your hawtio web app or you have not properly initialised the insight-log4j jar to then initialise the LogQuery mbean
* If links don't appear in the Logger column on the logs tab of your hawtio then the maven coordinates cannot be resolved for some reason
* If links are there but clicking on them cannot find any source code it generally means the maven repository resolver is not working. You maybe need to configure a local maven repository proxy so hawtio can access the source jars? Or maybe you need to start publishing the source jars?

##### How hawtio logs work

To be able to link to the source we need the maven coordinates (group ID, artifact ID, version), the relative file name and line number.

Most logging frameworks generate the className and/or file name along with the line number; the maven coordinates are unfortunately not yet common.

There's been [an idea to add better stack traces to log4j](http://macstrac.blogspot.co.uk/2008/09/better-stack-traces-in-java-with-log4j.html) along with a [patch](https://issues.apache.org/bugzilla/show_bug.cgi?id=45721) which is now included in log4j. Then a similar patch has been added to [logback](http://jira.qos.ch/browse/LOGBACK-690)

The missing part is to also add maven coordinates to non-stack traces; so all log statements have maven coordinates too. This is then implemented by either the [insight-log](https://github.com/fusesource/fuse/tree/master/insight/insight-log) or [insight-log4j](https://github.com/fusesource/fuse/tree/master/insight/insight-log4j) bundles depending on if you are working inside or outside of OSGi respectively.

##### The hawtio source plugin

Once we've figured out the maven coordinates, class & file name and line number we need to link to the source code from the [log plugin](https://github.com/hawtio/hawtio/tree/master/hawtio-web/src/main/webapp/app/log). This is where the [source plugin](https://github.com/hawtio/hawtio/tree/master/hawtio-web/src/main/webapp/app/source) comes in.

If you wish to use links to source code from any other [hawtio plugin](http://hawt.io/plugins/index.html) just use the following link syntax for your hypertext link:

    #/source/view/:mavenCoords/:className/:fileName

e.g. to link to a line of the camel source code you could use the following in your HTML:

    <a href="#/source/view/org.apache.camel:camel-core:2.10.0/org.apache.camel.impl.DefaultCamelContext/DefaultCamelContext.java?line=1435">
    org.apache.camel.impl.DefaultCamelContext.java line 1435</a>

Note that the className argument is optional; its usually there as often logging frameworks have the fully qualified class name, but just a local file name (like _DefaultCamelContext.java_ above).

You can also specify a list of space separated maven coordinates in the mavenCoords parameter; the server will scan each maven coordinate in turn looking for the file. This helps deal with some uberjars which really combine multiple jars together and so require looking into the source of each of their jars.


