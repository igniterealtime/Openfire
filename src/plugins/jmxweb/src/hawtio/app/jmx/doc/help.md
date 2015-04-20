### JMX

The [JMX](#/jmx/attributes) plugin in [hawtio](http://hawt.io "hawtio") gives a raw view of the underlying JMX metric data, allowing access to the entire JMX domain tree of MBeans.

##### General Navigation #####


###### The JMX Tree ######

The topmost level of the JMX tree view lists JMX domains.

![JMX Tree Top Level](app/jmx/doc/img/jmx-tree-domains.png "JMX Tree Top Level")

Click the <i class='icon-chevron-right'></i> icon to expand a tree item and further navigate into a domain.

![JMX Tree Expanded](app/jmx/doc/img/jmx-tree-expanded.png "JMX Tree Expanded")

MBeans are generally indicated with <i class='icon-cog'></i> while domains and MBeans that have descendants are indicated using <i class='icon-folder-close'></i>.


###### The JMX Toolbar ######

The JMX toolbar is used to interact with an MBean.

![JMX Toolbar](app/jmx/doc/img/jmx-toolbar.png "JMX Toolbar")

The next few sections describe the options provided by the JMX plugin.

###### Attributes ######

The Attributes view shows a table of attributes for the selected MBean

![Attributes View](app/jmx/doc/img/attributes-table.png "Attributes Table")

Data is organized in columns generally following a name/value format.


###### Operations ######

The Operations view shows a list of available JMX operations that can be invoked on the selected MBean.

![Operations List](app/jmx/doc/img/operations-list.png "Operations List")

Click the <i class='icon-chevron-right'></i> icon to expand the operation invocation form which is dynamically generated from JMX operation metadata.

![Operation Invocation Form](app/jmx/doc/img/operation-invoke.png "Operation Invocation Form")

Fill in the fields with the operation parameters and click the "<i class='icon-ok'></i> Execute" button to invoke the JMX operation.  Clicking the "<i class='icon-refresh'></i> Reset" button will reset the form, clearing all of the fields.  Clicking the "<i class='icon-remove'></i> Cancel" button will reset and close the form.  A successful invocation of the JMX operation will be reported in the body of the form.

![Operation Success](app/jmx/doc/img/operation-executed.png "Operation Success")

###### Chart ######

The Chart view displays charts of numeric metric values updated in realtime using [Cubism.js](http://square.github.com/cubism/).  As metrics are periodically fetched from the server the charts will scroll from right to left.  Hover the mouse over a portion of the chart data to view values from that time period.

![Example Chart View](app/jmx/doc/img/chart.png "Example Chart")

When the Chart view is selected from the JMX toolbar an additional "<i class='icon-cog'></i> Edit Chart" option is enabled allowing customization of which JMX metrics are displayed on the chart.

![Edit Chart View](app/jmx/doc/img/edit-chart.png "Edit Chart")

###### <i class='icon-share'></i> (Add this view to a dashboard) ######

Click this icon to add the current view to a [dashboard](#/help/dashboard)

###### <i class='icon-fullscreen'></i> (Show this view in fullscreen) ######

Hides the JMX tree so only the current view is visible on the page.


##### Other Functions #####

Plugins can extend the JMX toolbar and add additional functions.  Descriptions of these functions can be found in the plugins documentation such as [ActiveMQ](#/help/activemq) or [Camel](#/help/camel).
