## Configuring Security

hawtio enables security out of the box depending on the container it is running within. Basically there is two types of containers:

- Karaf based containers
- Web containers

#### Default Security Settings for Karaf containers

By default the security in hawtio uses these system properties when running in Apache Karaf containers (Karaf, ServiceMix, JBoss Fuse) which you can override:

<table class="buttonTable table table-striped">
  <thead>
  <tr>
    <th>Name</th>
    <th>Default</th>
    <th>Description</th>
  </tr>
  </thead>
  <tbody>
  <tr>
    <td>
      hawtio.authenticationEnabled
    </td>
    <td>
      true
    </td>
    <td>
      Whether or not security is enabled
    </td>
  </tr>
  <tr>
    <td>
      hawtio.realm
    </td>
    <td>
      karaf
    </td>
    <td>
      The security realm used to login
    </td>
  </tr>
  <tr>
    <td>
      hawtio.role
    </td>
    <td>
      admin
    </td>
    <td>
      The user role required to be able to login to the console
    </td>
  </tr>
  <tr>
    <td>
      hawtio.rolePrincipalClasses
    </td>
    <td>
      
    </td>
    <td>
      Principal fully qualified classname(s). Multiple classes can be separated by comma.
    </td>
  </tr>
  <tr>
    <td>
      hawtio.noCredentials401
    </td>
    <td>
      false
    </td>
    <td>
      Whether to return HTTP status 401 when authentication is enabled, but no credentials has been provided. Returning 401 will cause the browser popup window to prompt for credentails. By default this option is false, returning HTTP status 403 instead.
    </td>
  </tr>
  </tbody>
</table>

Changing these values is often application server specific. Usually the easiest way to get hawtio working in your container is to just ensure you have a new user with the required role (by default its the 'admin' role).

#### Default Security Settings for web containers

By default the security in hawtio uses these system properties when running in any other container which you can override:

<table class="buttonTable table table-striped">
  <thead>
  <tr>
    <th>Name</th>
    <th>Default</th>
    <th>Description</th>
  </tr>
  </thead>
  <tbody>
  <tr>
    <td>
      hawtio.authenticationEnabled
    </td>
    <td>
      false
    </td>
    <td>
      Whether or not security is enabled
    </td>
  </tr>
  <tr>
    <td>
      hawtio.realm
    </td>
    <td>
      *
    </td>
    <td>
      The security realm used to login
    </td>
  </tr>
  <tr>
    <td>
      hawtio.role
    </td>
    <td>
    </td>
    <td>
      The user role required to be able to login to the console
    </td>
  </tr>
  <tr>
    <td>
      hawtio.rolePrincipalClasses
    </td>
    <td>
    </td>
    <td>
      Principal fully qualified classname(s). Multiple classes can be separated by comma.
    </td>
  </tr>
  <tr>
    <td>
      hawtio.noCredentials401
    </td>
    <td>
    </td>
    <td>
       Whether to return HTTP status 401 when authentication is enabled, but no credentials has been provided. Returning 401 will cause the browser popup window to prompt for credentails. By default this option is false, returning HTTP status 403 instead.
    </td>
  </tr>
  </tbody>
</table>



#### Configuring or disabling security in web containers

Set the following JVM system property to enable security:

    hawtio.authenticationEnabled=true

Or adjust the web.xml file and configure the &lt;env-entry&gt; element, accordingly.

##### Configuring security in Apache Tomcat

From **hawt 1.2.2** onwards we made it much easier to use Apache Tomcats userdata file (conf/tomcat-users.xml) for security.
All you have to do is to set the following **CATALINA_OPTS** environment variable:

    export CATALINA_OPTS=-Dhawtio.authenticationEnabled=true

Then **hawtio** will auto detect that its running in Apache Tomcat, and use its userdata file (conf/tomcat-users.xml) for security.

For example to setup a new user named scott with password tiger, then edit the file '''conf/tomcat-users.xml''' to include:

    <user username="scott" password="tiger" roles="tomcat"/>

Then you can login to hawtio with the username scott and password tiger.

If you only want users of a special role to be able to login **hawtio** then you can set the role name in the **CATALINA_OPTS** environment variable as shown:

    export CATALINA_OPTS='-Dhawtio.authenticationEnabled=true -Dhawtio.role=manager'

Now the user must be in the manager role to be able to login, which we can setup in the '''conf/tomcat-users.xml''' file:

    <role rolename="manager"/>
    <user username="scott" password="tiger" roles="tomcat,manager"/>


## Configuration Properties

The following table contains the various configuration settings for the various hawtio plugins.

<table class="table table-striped">
  <thead>
    <tr>
      <th>System Property</th>
      <th>Description</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>hawtio.offline</td>
      <td>Whether to run hawtio in offline mode (default false). When in offline mode, then some plugins is not enabled such as <a href="http://hawt.io/plugins/maven/">Maven</a> and <a href="http://hawt.io/plugins/git/">Git</a>.</td>
    </tr>
    <tr> 
      <td>hawtio.dirname</td>
      <td>The directory name for the hawtio home. Is by default <tt>/.hawtio</tt>. This complete home directory for hawtio is the <tt>hawtio.config.dir</tt><tt>hawtio.dirname</tt>, so remember to have leading / in this option. The out of the box options translates as the: <tt>user.home/.hawtio</tt> directory.</td>
    </tr>
    <tr> 
      <td>hawtio.config.dir</td>
      <td>The directory on the file system used to keep a copy of the configuration for hawtio; for all user settings, the dashboard configurations, the wiki etc. Typically you will push this configuration to some remote git server (maybe even github itself) so if not specified this directory will be a temporary created directory. However if you are only running one hawtio server then set this somewhere safe and you probably want to back this up!. See also the hawtio.dirname option.</td>
    </tr>
    <tr>
      <td>hawtio.config.repo</td>
      <td>The URL of the remote git repository used to clone for the dashboard and wiki configuration. This defaults to <b>git@github.com:hawtio/hawtio-config.git</b> but if you forked the hawtio-config repository then you would use your own user name; e.g. <b>git@github.com:myUserName/hawtio-config.git</b></td>
    </tr>
    <tr>
      <td>hawtio.config.cloneOnStartup</td>
      <td>If set to the value of <b>false</b> then there will be no attempt to clone the remote repo</td>
    </tr>
    <tr>
      <td>hawtio.config.pullOnStartup</td>
      <td>If set to the value of <b>false</b> then there will be no attempt to pull from the remote config repo on startup</td>
    </tr>
    <tr>
      <td>hawtio.maven.index.dir</td>
      <td>The directory where the maven indexer will use to store its cache and index files</td>
    </tr>
    <tr>
      <td>hawtio.sessionTimeout</td>
      <td><strong>hawtio 1.2.2</strong> The maximum time interval, in seconds, that the servlet container will keep this session open between client accesses. If this option is not configured, then hawtio uses the default session timeout of the servlet container.</td>
    </tr>
  </tbody>
</table>

## Web Application configuration

If you are using a web container, the easiest way to change the web app configuration values is:

* Create your own WAR which depends on the **hawtio-default.war** like the [sample project's pom.xml](https://github.com/hawtio/hawtio/blob/master/sample/pom.xml#L17)
* Create your own [blueprint.properties](https://github.com/hawtio/hawtio/blob/master/sample/src/main/resources/blueprint.properties#L7) file that then can override whatever properties you require

#### OSGi configuration

Just update the blueprint configuration values in OSGi config admim as you would any OSGi blueprint bundles. On OSGi all the hawtio Java modules use OSGi blueprint.


