### Dashboard

The dashboard plugin lets you view, create and modify dashboards of widgets for displaying real time metrics, diagrams or metrics.

<ul class="thumbnails">
  <li class="span4">
    <div class="thumbnail">
      <img src="https://raw.github.com/hawtio/hawtio/master/website/src/images/screenshots/dashboard.png" alt="screenshot">
      <h5>Sample dashboard</h5>
    </div>
  </li>
</ul>

* you can drag the windows around on a dashboard to align them however you wish
* click the edit icon next to a dashboard or window name to edit the title
* click the Edit link (top right of the dashboard page) to be able to copy/delete dashboards
* to add new content to a dashboard click the share icon <i class="icon-share"></i> icon on the top right of a view (such as when viewing the JMX tab to show attributes or charts).

<ul class="thumbnails">
  <li class="span4">
    <div class="thumbnail">
      <img src="https://raw.github.com/hawtio/hawtio/master/website/src/images/screenshots/jmx.png" alt="screenshot">
      <h5>JMX page</h5>
    </div>
  </li>
</ul>


##### Adding your plugin views to the dashboard

Any partial HTML page loaded via the hawtio [plugin mechanism](http://hawt.io/plugins/howPluginsWork.html) should be usable as a widget inside a dashboard.

Here are some developer guides on how to make nicely behaving views for inclusion into a dashboard rectangle:

* when adding your view to the angularjs [routing mechanism](http://docs.angularjs.org/api/ng.$route) (e.g like in this use of [$routeProvider.when() in the log plugin](https://github.com/hawtio/hawtio/blob/master/hawtio-web/src/main/webapp/app/log/js/logPlugin.ts#L5) then only add the templateUri parameter and put the ng-controller (or directives) inside the HTML partial. This makes it easy for the dashboard to just perform an [ngInclude](http://docs.angularjs.org/api/ng.directive:ngInclude) for your page.
* if you use any jQuery navigation, good behaving pages use relative locations to the angularjs controller (e.g. using **$element** in the controller / directive) rather than global lookups by ID with jQuery. If you use global ID lookups, then your page won't work if its included into 2 rectangles on a dashboard at the same time :). e.g. [here is how the JMX chart controller uses $element to find where to draw the charts](https://github.com/hawtio/hawtio/blob/master/hawtio-web/src/main/webapp/app/jmx/js/charts.ts#L2)
* until 1.1, we don't yet support **$routeParams** injection though we will hopefully be [fixing that soon](https://github.com/hawtio/hawtio/issues/146)!
* any questions on how to allow your view to be added to the dashboard or to get it working on a dashboard, [join the community](http://hawt.io/community/index.html) - we'll be glad to help!


##### How the configuration works

The default configuration repository for the dashboards is [hawtio-config on github](https://github.com/hawtio/hawtio-config) but you can [configure hawtio](http://hawt.io/configuration/index.html) to use whatever configuration directory or remote git repository you wish.

We'd love it if you [contribute](http://localhost:8000/contributing/index.html) any nice cool dashboards you may have created so we can share them with others! Just fork the  [hawtio-config repository on github](https://github.com/hawtio/hawtio-config) and submit a pull request; or maintain your own public repo!
