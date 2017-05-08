We love [contributions](http://hawt.io/contributing/index.html)! You may also want to know [how to hack on the hawtio code](http://hawt.io/developers/index.html)

[hawtio](http://hawt.io/) can now be built **without** having to install node.js or anything first thanks to the [typescript-maven-plugin](https://github.com/hawtio/typescript-maven-plugin).  However [hawtio](http://hawt.io/) will build faster if typescript is installed, so when possible it's recommended to install it.

## Installing npm and TypeScript for faster builds

To install all of the required dependencies you first need to install [npm](https://npmjs.org/) e.g. by [installing nodejs](http://nodejs.org/). If you're on OS X we recommend just installing [npm](https://npmjs.org/) directly rather than via things like homebrew to get the latest npm crack.

In order to make use of [TypeScript](http://typescriptlang.org/) you will need to install the compiler globally. Installing a dependency globally allows you to access the the dependency directly from your shell.

You can do this by running:

    npm install -g typescript

Note, if you are using Ubuntu then you may need to use the `sudo` command:

    sudo npm install -g typescript

To run the tests you'll also need to install phantomjs:

    sudo npm install -g phantomjs

If you want to be able to generate the JavaScript documentation reference docs then also do:

    npm -g install yuidocjs

## Building

After you've cloned hawtio's git repo the first thing you should do is build the whole project.  First ```cd``` into the root directory of the hawtio project and run:

    mvn install

This will ensure all dependencies within the hawtio repo are built and any dependencies are downloaded and in your local repo.

To run the sample web application for development, type:

    cd hawtio-web
    mvn compile
    mvn test-compile exec:java

On OS X and linux the _mvn compile_ command above is unnecessary but folks have found on windows there can be timing issues with grunt and maven that make this extra step a requirement (see [issue #203 for more details](https://github.com/hawtio/hawtio/issues/203#issuecomment-15808516))

Or if you want to just run an empty hawtio and connect in hawtio to a remote container (e.g. connect to a Fuse Fabric or something via the Connect tab) just run

    cd hawtio-web
    mvn clean jetty:run

### Trying Different Containers

The above uses Jetty but you can try running hawtio in different containers via any of the following commands. Each of them runs the hawtio-web in a different container (with an empty JVM so no beans or camel by default).

    mvn tomcat7:run
    mvn tomcat6:run
    mvn jboss-as:run
    mvn jetty:run

## Incrementally compiling TypeScript

For a more rapid development workflow its good to use incremental compiling of TypeScript and to use LiveReload (or LiveEdit) below too.

So in a **separate shell** (while keeping the above shell running!) run the following commands:

    cd hawtio-web
    mvn compile -Pwatch

This will incrementally watch all the *.ts files in the src/main/webapp/app directory and recompile them into src/main/webapp/app/app.js whenever there's a change.

## Incrementally compiling TypeScript inside IntelliJ (IDEA)

The easiest way we've figured out how to use [IDEA](http://www.jetbrains.com/idea/) and TypeScript together is to setup an External Tool to run watchTsc; then you get (relatively) fast recompile of all the TypeScript files to a single app.js file; so you can just keep hacking code in IDEA and letting LiveReload reload your web page.

* open the **Preferences** dialog
* select **External Tools**
* add a new one called **watchTsc**
* select path to **mvn** as the program and **compile -Pwatch** as the program arguments
* select **hawtio-web** as the working directory
* click on Output Filters...
* add a new Output Filter
* use this regular expression

```
$FILE_PATH$\($LINE$,$COLUMN$\)\:
```

Now when you do **Tools** -> **watchTsc** you should get a output in the Run tab. If you get a compile error when TypeScript gets recompiled you should get a nice link to the line and column of the error.

**Note** when you do this you probably want the Run window to just show the latest compile errors (which is usually the last couple of lines).

I spotted a handy tip on [this issue](http://youtrack.jetbrains.com/issue/IDEA-74931), if you move the cursor to the end of the Run window after some compiler output has been generated - pressing keys _META_ + _end_ (which on OS X is the _fn_ and the _option/splat_ and right cursor keys) then IDEA keeps scrolling to the end of the output automatically; you don't have to then keep pressing the "Scroll to end" button ;)

## Using LiveReload

The LiveReload support allows you to edit the code and for the browser to automatically reload once things are compiled. This makes for a much more fun and RAD development environment!!

Here's how to do it:

Install the [LiveReload](https://chrome.google.com/webstore/detail/livereload/jnihajbhpnppcggbcgedagnkighmdlei) plugin for Chrome and then enable it for the website (click the live reload icon on the right of the address bar)

When you run "mvn test-compile exec:java" the sample server runs an embedded Live Reload server that's already configured to look at src/main/webapp for file changes.  The Live Reload server implementation is provided by [livereload-jvm](https://github.com/davidB/livereload-jvm).  When using other methods run run hawtio like "mvn jetty:run" or "mvn tomcat:run" you can run [livereload-jvm](https://github.com/davidB/livereload-jvm) directly, for example from the hawtio-web directory:

    java -jar livereload-jvm-0.2.0-SNAPSHOT-onejar.jar -d src/main/webapp/ -e .*\.ts$

In another shell (as mentioned above in the "Incrementally compile TypeScript" section you probably want to auto-recompile all the TypeScript files into app.js in *another shell* via this command:

    cd hawtio-web
    ./watchTsc

Enable Live Reload in your browser (open [http://localhost:8080/hawtio/](http://localhost:8080/hawtio/) then click on the Live Reload icon to the right of the location bar).

Now if you change any source (HTML, CSS, TypeScript, JS library) the browser will auto reload on the fly. No more context-switching between your IDE and your browser! :)

To specify a different port to run on, just override the `jettyPort` property

    mvn test-compile exec:java -DjettyPort=8181

### Using your build & LiveReload inside other web containers

TODO - this needs updating still...

The easiest way to use other containers and still get the benefits of LiveReload is to create a symbolic link to the generated hawtio-web war in expanded form, in the deploy directory in your web server.

e.g. to use Tomcat7 in LiveReload mode try the following to create a symbolic link in the tomcat/webapps directory to the **hawtio-web/target/hawtio-web-1.3-SNAPSHOT** directory:

    cd tomcat/webapps
    ln -s ~/hawtio/hawtio-web/target/hawtio-web-1.3-SNAPSHOT hawtio

Then use [livereload-jvm](https://github.com/davidB/livereload-jvm) manually as shown above.

Now just run Tomcat as normal. You should have full LiveReload support and should not have to stop/start Tomcat or recreate the WAR etc!

#### Using your build from inside Jetty

For jetty you need to name the symlink directory **hawtio.war** for [Jetty to recognise it](http://www.eclipse.org/jetty/documentation/current/automatic-webapp-deployment.html).

    cd jetty-distribution/webapps
    ln -s ~/hawtio/hawtio-web/target/hawtio-web-1.3-SNAPSHOT hawtio.war

Another thing is for symlinks jetty uses the real directory name rather than the symlink name for the context path.

So to open the application in Jetty open [http://localhost:8080/hawtio-web-1.3-SNAPSHOT/](http://localhost:8080/hawtio-web-1.3-SNAPSHOT/)


## Running Unit Tests

You can run the unit tests via maven:

    cd hawtio-web
    mvn test


If you have a local build (or ideally are using the _mvn -Pwatch_ command to do incremental compiles as you edit the source), you can open the unit test runner via the following:

    cd hawtio-web
    open src/test/specs/SpecRunner.html

This then runs the [unit test specifications](https://github.com/hawtio/hawtio/tree/master/hawtio-web/src/test/specs/spec) using [Jasmine](http://pivotal.github.com/jasmine/) in your browser. From this web page you can use the browser's debugger and console to debug and introspect unit test cases as required.

If you are using the [LiveReload plugin for Chrome](https://chrome.google.com/webstore/detail/livereload/jnihajbhpnppcggbcgedagnkighmdlei) you can then hit the LiveReload icon to the right of the address bar and if you are running the watch profile, the tests are re-run every time there is a compile:

    mvn -Pwatch

Now the unit tests are all re-run whenever you edit the source.


## Running the End-to-End Integration Tests

Install [testacular](http://vojtajina.github.com/testacular/):

    npm -g install testacular

To get the latest greatest testacular crack (e.g. so console.log() statements output to the command shell, etc.) you need 0.5.x or later use this command:

    npm install -g testacular@"~0.5.7"


### Running Tests With Testacular

In a shell in the `hawtio-web` directory run:

    mvn test-compile exec:java

In another in the same directory run the following:

    testacular start src/test/config/e2e-config.js


## How to Get Started Hacking the Code

Check out the [hawtio technologies, tools and code walkthroughs](http://hawt.io/developers/index.html)

## Trying hawtio with Fuse Fabric

As of writing hawtio depends on the latest snapshot of [Fuse Fabric](http://fuse.fusesource.org/fabric/). To try out hawtio with it try these steps:

  1. Grab the latest [Fuse Fabric source code](http://fuse.fusesource.org/source.html) and do a build in the fabric directory...

    git clone git://github.com/fusesource/fuse.git
    cd fuse
    cd fabric
    mvn -Dtest=false -DfailIfNoTests=false clean install

  2. Now create a Fuse Fabric instance

    cd fuse-fabric\target
    tar xf fuse-fabric-99-master-SNAPSHOT.tar.gz
    cd fuse-fabric-99-master-SNAPSHOT
    bin/fusefabric

  3. When the Fabric starts up run the command

    fabric:create

  to properly test things out you might want to create a new version and maybe some child containers.

### Running hawtio with Fuse Fabric in development mode

    cd hawtio-web
    mvn test-compile exec:java -Psnapshot,fabric

