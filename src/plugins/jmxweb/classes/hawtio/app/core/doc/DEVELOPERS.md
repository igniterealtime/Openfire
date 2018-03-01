We love [contributions](http://hawt.io/contributing/index.html). This page is intended to help you get started hacking on the code, it contains various information on technology and tools together with code walk-throughs.

Welcome and enjoy! Its hawt, but stay cool! :)

## Building the code

Check out <a class="btn btn-primary btn-large" href="http://hawt.io/building/index.html">How To Build The Code</a> if you want to start hacking on the source.

## Architecture

**hawtio** is a single page application which is highly modular and capable of dynamically loading [plugins](http://hawt.io/plugins/index.html) based on the capability of the server.

You may want to check out:

* [Current hawtio plugins](http://hawt.io/plugins/index.html)
* [Hawtio AngularJS Directives](http://hawt.io/directives/)
* [External AngularJS Directives](http://hawt.io/developers/directives.html)
* [How plugins work](http://hawt.io/plugins/howPluginsWork.html)

## Developer Tools

The following are recommended if you want to contribute to the code

* [IntelliJ IDEA EAP 12 or later](http://confluence.jetbrains.net/display/IDEADEV/IDEA+12+EAP) as this has TypeScript support and is the daddy of IDEs!
* [There are other TypeScript plugins](http://blogs.msdn.com/b/interoperability/archive/2012/10/01/sublime-text-vi-emacs-typescript-enabled.aspx) if you prefer Sublime, Emacs or VIM. (Unfortunately we're not aware of an eclipse plugin yet).
* [AngularJS plugin for IDEA](http://plugins.jetbrains.com/plugin/?id=6971) if you use [IDEA](http://www.jetbrains.com/idea/) then this plugin really helps work with Angular JS
* [AngularJS plugin for Chrome](https://chrome.google.com/webstore/detail/angularjs-batarang/ighdmehidhipcmcojjgiloacoafjmpfk) which is handy for visualising scopes and performance testing etc.
* [JSONView plugin for Chrome](https://chrome.google.com/webstore/detail/jsonview/chklaanhfefbnpoihckbnefhakgolnmc) makes it easy to visualise JSON returned by the [REST API of Jolokia](http://jolokia.org/reference/html/protocol.html)
* [Apache Maven 3.0.3 or later](http://maven.apache.org/)
* [gruntjs](http://gruntjs.com/) a build tool for JavaScript. See nearly the beginning of this document for details of how to install and use.
* [Dash.app](http://kapeli.com/) is a handy tool for browsing API documentation for JavaScript, HTML, CSS, jquery, AngularJS etc.

## JavaScript Libraries

For those interested in [contributing](http://hawt.io/contributing/index.html) or just learning how to build single page applications, here is a list of the various open source libraries used to build hawtio:

* [AngularJS](http://angularjs.org/) is the web framework for performing real time two-way binding of HTML to the model of the UI using simple declarative attributes in the HTML.
* [jolokia](http://jolokia.org/) is the server side / JVM plugin for exposing JMX as JSON over HTTP. It's awesome and is currently the only server side component of hawtio.
* [TypeScript](http://typescriptlang.org/) is the language used to implement the console; it compiles to JavaScript and adds classes, modules, type inference & type checking. We recommend [IntelliJ IDEA EAP 12 or later](http://confluence.jetbrains.net/display/IDEADEV/IDEA+12+EAP) for editing TypeScript--especially if you don't use Windows or Visual Studio (though there is a Sublime Text plugin too).
* [angular ui](http://angular-ui.github.com/) is a library of AngularJS additions and directives
* [d3](http://d3js.org/) is the visualization library used to do the force layout graphs (for example the diagram view for ActiveMQ)
* [cubism](http://square.github.com/cubism/) implements the real-time horizon charts
* [dagre](https://github.com/cpettitt/dagre) for graphviz style layouts of d3 diagrams (e.g. the Camel diagram view).
* [ng-grid](http://angular-ui.github.com/ng-grid/) an AngualrJS based table/grid component for sorting/filtering/resizing tables
* [marked](https://github.com/chjj/marked) for rendering Github-flavoured Markdown as HTML
* [DataTables](http://datatables.net/) for sorted/filtered tables (though we are migrating to ng-grid as its a bit more natural for AngularJS)
* [DynaTree](http://wwwendt.de/tech/dynatree/doc/dynatree-doc.html) for tree widget
* [jQuery](http://jquery.com/) small bits of general DOM stuff, usually when working with third-party libraries which don't use AngularJS
* [Twitter Bootstrap](http://twitter.github.com/bootstrap/) for CSS
* [Toastr](https://github.com/CodeSeven/toastr) for notifications

We're not yet using it but these look handy too:

* [jquery-mobile-angular-adapter](https://github.com/tigbro/jquery-mobile-angular-adapter) for easy interop of jQuery Mobile and AngularJS
* [breezejs](http://www.breezejs.com/) for easy LINQ / ActiveRecord style database access and synchronization

## API documentation

If you are interested in working on the code the following references and articles have been really useful so far:

* [available AngularJS Directives](http://hawt.io/developers/directives.html)
* [angularjs API](http://docs.angularjs.org/api/)
* [bootstrap API](http://twitter.github.com/bootstrap/base-css.html)
* [cubism API](https://github.com/square/cubism/wiki/API-Reference)
* [d3 API](https://github.com/mbostock/d3/wiki/API-Reference)
* [ng-grid API](http://angular-ui.github.com/ng-grid/#/api)
* [datatables API](http://www.datatables.net/api)
* [javascript API](http://www.w3schools.com/jsref/default.asp)
* [sugarjs API](http://sugarjs.com/api/Array/sortBy)
* [icons from Font Awesome](http://fortawesome.github.com/Font-Awesome/)

### Developer Articles, Forums and Resources

* [Fun with AngularJS](http://devgirl.org/2013/03/21/fun-with-angularjs/) is great overview on AngularJS!
* [egghead.io various short angularjs videos](http://egghead.io/)
* [great angularjs talk](http://www.youtube.com/angularjs)
* [AngularJS plugins](http://ngmodules.org/)
* [AngularJS tips and tricks](http://deansofer.com/posts/view/14/AngularJs-Tips-and-Tricks-UPDATED)
* [more AngularJS magic to supercharge your webapp](http://www.yearofmoo.com/2012/10/more-angularjs-magic-to-supercharge-your-webapp.html#)
* [AngularJS questions on stackoverflow](http://stackoverflow.com/questions/tagged/angularjs)
* [Modeling Data and State in Your AngularJS Application](http://joelhooks.com/blog/2013/04/24/modeling-data-and-state-in-your-angularjs-application/)
* [6 Common Pitfalls Using Scopes](http://thenittygritty.co/angularjs-pitfalls-using-scopes)

## Code Walkthrough

If you fancy contributing--and [we love contributions!](http://hawt.io/contributing/index.html)--the following should give you an overview of how the code hangs together:

* hawtio is a single page web application, from [this single page of HTML](https://github.com/hawtio/hawtio/blob/master/hawtio-web/src/main/webapp/index.html)
* We use [AngularJS routing](http://docs.angularjs.org/api/ng.directive:ngView) to display different [partial pages](https://github.com/hawtio/hawtio/tree/master/hawtio-web/src/main/webapp/app/core/html) depending on which tab/view you choose. You'll notice that the partials are simple HTML fragments which use [AngularJS](http://angularjs.org/) attributes (starting with **ng-**) along with some {{expressions}} in the markup.
* Other than the JavaScript libraries listed above which live in [webapp/lib](https://github.com/hawtio/hawtio/tree/master/hawtio-web/src/main/webapp/lib) and are [included in the index.html](https://github.com/hawtio/hawtio/blob/master/hawtio-web/src/main/webapp/index.html), we then implement [AngularJS](http://angularjs.org/) controllers using [TypeScript](http://typescriptlang.org/). All the typescript source is in the [in files in webapp/app/pluginName/js/ directory](https://github.com/hawtio/hawtio/tree/master/hawtio-web/src/main/webapp/app) which is then compiled into the [webapp/app/app.js file](https://github.com/hawtio/hawtio/blob/master/hawtio-web/src/main/webapp/app/app.js)
* To be able to compile with TypeScript's static type checking we use the various [TypeScript definition files](https://github.com/hawtio/hawtio/tree/master/hawtio-web/src/main/d.ts) to define the optional statically typed APIs for the various APIs we use
* The controllers use the [Jolokia JavaScript API](http://jolokia.org/reference/html/clients.html#client-javascript) to interact with the server side JMX MBeans

### How the Tabs Work

Tabs can dynamically become visible or disappear based on the following:

* the contents of the JVM
* the [plugins](plugins.html),
* and the current UI selection(s).

[Plugins](plugins.html) can register new top-level tabs by adding to the [topLevelTabs](https://github.com/hawtio/hawtio/blob/master/hawtio-web/src/main/webapp/app/log/js/logPlugin.ts#L9) on the workspace which can be dependency injected into your plugin via [AngularJS Dependency Injection](http://docs.angularjs.org/guide/di).

The [isValid()](https://github.com/hawtio/hawtio/blob/master/hawtio-web/src/main/webapp/app/log/js/logPlugin.ts#L12) function is then used to specify when this top-level tab should be visible.

You can register [subLevelTabs](https://github.com/hawtio/hawtio/blob/master/hawtio-web/src/main/webapp/app/log/js/logPlugin.ts#L16) which are then visible when the [right kind of MBean is selected](https://github.com/hawtio/hawtio/blob/master/hawtio-web/src/main/webapp/app/log/js/logPlugin.ts#L19).

For more detail check out the [plugin documentation](plugins.html).

#### Enable Source Maps for Easier Debugging

We recommend you enable [Source Maps](https://docs.google.com/document/d/1U1RGAehQwRypUTovF1KRlpiOFze0b-_2gc6fAH0KY0k/edit?pli=1) in your browser (e.g. in Chrome) for easier debugging by clicking on the bottom-right Settings icon in the *JavaScript Console* and [enabling Source Maps support such as in this video](http://www.youtube.com/watch?v=-xJl22Kvgjg).

#### Notes on Using IDEA

To help IDEA navigate to functions in your source and to avoid noise, you may want to ignore some JavaScript files in IDEA so that they are not included in the navigation. Go to `Settings/Preferences -> File Types -> Ignore Files` then add these patterns to the end:

    *.min.js;*-min.js

Ignoring these files will let IDEA ignore the minified versions of the JavaScript libraries.

Then select the generated [webapp/app/app.js file](https://github.com/hawtio/hawtio/blob/master/hawtio-web/src/main/webapp/app/app.js) in the Project Explorer, right-click and select _Mark as Plain Text_ so that it is ignored as being JavaScript source. This hint came from [this forum thread](http://devnet.jetbrains.net/message/5472690#5472690), hopefully there will be a nicer way to do all this one day!

#### Handy AngularJS debugging tip

Open the JavaScript Console and select the _Console_ tab so you can type expressions into the shell.
Select part of the DOM of the scope you want to investigate
Right click and select _Inspect Element_
In the console type the following

    s = angular.element($0).scope()

You have now defined a variable called _s_ which contains all the values in the active AngularJS scope so you can navigate into the scope and inspect values or invoke functions in the REPL, etc.

### Handy AngularJS programming tips

#### Use a nested scope object to own state for 2 way binding

When binding models to HTML templates; its always a good idea to use a level of indirection between the $scope and the property. So rather than binding to $scope.name, bind to $scope.entity.name then have code like

    $scope.entity = { "name": "James" };

This means to that to reset the form you can just do

    $scope.entity = {};

And you can then refer to the entire entity via **$scope.entity**. Another reason for doing this is that when you have lots of nested scopes; for example when using nested directives, or using includes or layouts, you don't get inheritence issues; since you can acess $scope.entity from child scopes fine.

#### When working with $routeParams use the $scope.$on

Its often useful to use $routeParams to get the URI template parameters. However due to [the rules on who gets the $routeParams injected](http://deansofer.com/posts/view/14/AngularJs-Tips-and-Tricks-UPDATED#routing) when using layouts and so forth, the $routeParams can be empty.

So put all code that uses $routeParams inside the $routeChangeSuccess callback:

    $scope.$on('$routeChangeSuccess', function(event, routeData){
      // process $routeParams now...
    });

### Local Storage

hawtio uses local storage to store preferences and preferred views for different kinds of MBean type and so forth.

You can view the current Local Storage in the Chrome developer tools console in the Resources / Local Storage tab.

If you ever want to clear it out in Chrome on OS X you'll find this located at `~/Library/Application Support/Google/Chrome/Default/Local Storage`.
