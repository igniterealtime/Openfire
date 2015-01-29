### Camel

Click [Camel](#/jmx/attributes?tab=camel) in the top navigation bar to view all the running Camel Contexts in the current JVM. (The selection will not appear on the navigation bar if there is no Camel running).

The Camel plugin allows you to view all the running Camel applications in the current JVM.
You can among others see the following details:

* Lists of all running Camel applications
* Detailed information of each Camel Context such as Camel version number, runtime statics
* Lists of all routes in each Camel applications and their runtime statistics
* Manage the lifecycle of all Camel applications and their routes, so you can restart / stop / pause / resume, etc.
* Graphical representation of the running routes along with real time metrics
* Live tracing and debugging of running routes
* Profile the running routes with real time runtime statics; detailed specified per processor
* Browsing and sending messages to Camel endpoint


#### Camel Tree ####

On the left hand side is the Camel Tree which lists all the running Camel applications in the JVM.

![Camel Tree](app/camel/doc/img/camel-tree.png "Camel Tree")

And on the main view area is a table that lists runtime metrics of each Camel application, as shown below:

![Camel Attributes](app/camel/doc/img/context-attributes.png "Camel Attributes")

Clicking on a Camel application in the tree, selects it, and allows you to see more details about that particular Camel application.


#### Camel Application ####

In the Camel tree expand the selected Camel application to access more information such as the running routes.
When clicking on the routes node in the tree, the main page lists a table of all the routes and runtime metrics as shown below:

![Route Attributes](app/camel/doc/img/route-attributes.png "Route Attributes")

In the table you can select route(s) and use the buttons to control their lifecycle, such as stopping and starting routes.


#### Camel Route ####

By selecting a route in the Camel Tree

![Select Route](app/camel/doc/img/route-select.png "Select Route")

... gives access to a full range of *hawt* functionality. For example the diagram tab, shows a real time visual representation of the route, as shown below:

##### Diagram #####

![Route Diagram](app/camel/doc/img/route-diagram.png "Route Diagram")

If you hover the mouse over the nodes in the diagram, a tool tip is presented with additional details.

##### Source #####

The Source tab shows a XML representation of the route.
Camel is able to output any route as XML even if the route was originally developed using Java, Groovy, or Scala code.

![Route Source](app/camel/doc/img/route-source.png "Route Source")

You can even edit the source code, for example we could add a predicate for checking if the message is from Paris in France,
such as adding the following to source code, and clicking the Update button.

    <when customId="true" id="isCityParis">
      <xpath>/person/city = 'Paris'</xpath>
      <to uri="file:target/messages/fr" customId="true" id="messagesFR"/>
    </when>

And the route would be updated at runtime, which we can see in the Diagram tab as shown below:

![Route Updated](app/camel/doc/img/route-updated.png "Route Updated")


##### Debug #####

The debug tab is for real time debugging of the selected route.
To activate the debugger, click the **Start Debugging** button. And to stop debugging click the **Close** button.

When the debugger is started, the center screen presents the selected route,

![Route Debug](app/camel/doc/img/route-debug-0.png "Route Debug")

... and on the left hand side is buttons to control the debugging.

![Route Debug Control Panel](app/camel/doc/img/route-debug-1.png "Route Debug Control Panel")

To set a breakpoint in the route, double click on a node (or select a node, and click the **+** button),
which inserts the breakpoint using a yellow ball as marker, as shown in the screen shot above, at the *Choice* node.
To remove a breakpoint double click the node again (or select the node, and click the **x** button).

The breakpoint is active, and when the first message arrives at the node, the color turns from yellow to blue, as shown below

![Route Debug Breakpoint Suspended](app/camel/doc/img/route-debug-2.png "Route Debug Breakpoint Suspended")

... and the message is suspended at the node. Below the route we can expand the message to see the message body and headers.
In this example we can see its a message from Jonathan Anstey whom lives in St. Johns in Canada.

Clicking on the ![Step Button](app/camel/doc/img/debug/step.gif "Step Button") will advance the message to the next node, which
in this example is the *messageOthers* node as shown below:

![Route Debug Others](app/camel/doc/img/route-debug-3.png "Route Debug at messageOthers")

By clicking on the ![Resume Button](app/camel/doc/img/debug/resume.gif "Step Resume") would continue routing the message, until
a message arrives at an active breakpoint.

You can have multiple breakpoints in a route, and use the step or resume buttons to advance routing the message(s).

Notice you can only *work with* one message at a time with the debugger; meaning that its the first message that arrives
at an active breakpoint that is *only in use* in the debugger, until that message has completed its routing. This means
if you have concurrent messages in the route, the other messages will continue routing without being suspended at breakpoints.


##### Trace #####

The trace tab is for real time tracing of messages as they flows through the route.
The tracing works similar to how the debugger works, expect that all messages is traced *as they* are being routed,
and no messages is suspended at breakpoints.

When the tracer is started, the route is displayed, and the traced messages is listed below the route as shown below:

![Route Trace](app/camel/doc/img/route-trace-0.png "Route Trace")

The ID column groups the traced message by their exchangeID. In the screen shot above, we have two groups traced messages (ID ending with -0-5 and -0-7).
Clicking a message in the list highlights the selected message in the route, by marking the node as blue, and shows the content of the message body as well,
as shown below:

![Route Trace Message](app/camel/doc/img/route-trace-1.png "Route Trace Message")

Using the control buttons we can navigate forward and backward the traced message at the actual path the message went.

![Route Trace Control Buttons](app/camel/doc/img/route-trace-control.png "Route Trace Control Buttons")

So if we click the next button we can see the message advanced to the next node which is the *Log* node, and the
message body is changed from *null* to *Hello from Camel route*

![Route Trace Message 2](app/camel/doc/img/route-trace-2.png "Route Trace Message 2")


##### Profile #####

The profile tab shows a real time profile of the selected route.

The top shows the accumulated profile for the route. Each of the following rows is a break down per processor.

In the screen shot below, we can see the route has processed 3 messages, with a mean processing time of 56 ms per message,
and a total of 169 ms. We can also see from the self time of the last 2 rows, that they only use 1 and 12 ms, so the
bottleneck is at the *choice1* processor which has a self time of 146 ms. The *choice1* processor is a Content Based Router EIP that
uses an XPath expression which means the majority of processing time is spent evaluating XPath expressions.

![Route Profile](app/camel/doc/img/route-profile.png "Route Profile")

The metrics shown in the table are as follows:

* Count = Total number of messages processed
* Last = Time in ms. processing the last message.
* Delta = Difference in +/- ms. processing the second-last and last message.
* Mean = Time in ms. for the average processing time.
* Min = Time in ms. for the lowest processing time.
* Max = Time in ms. for the highest processing time.
* Total = Accumulated self time in ms. for processing messages.
* Self = Total time in ms. for processing message in this processor only.


#### Endpoint Tree ####

On the left hand side is the Camel Tree which lists all the running Camel applications in the JVM.

When having selected a CamelContext you can list all the endpoint in use, as shown below:

![Endpoint Tree](app/camel/doc/img/endpoint-tree.png "Endpoint Tree")

And on the main view area is a table that lists the endpoints in tabular format as well.

![Endpoint Table](app/camel/doc/img/endpoint-table.png "Endpoint Table")


##### Browsable Endpoints #####

Some endpoints supports browsing, meaning you can view messages they have received or sent.
For example file and activemq/jms endpoints are browsable.

In the screen shot below we have selected the 'file:target/messages/uk' endpoint which contains one message that can be browsed

![Endpoint Browsable](app/camel/doc/img/endpoint-file-browse-1.png "Endpoint Browsable")

.. by clicking the message id, brings up the message, and a control panel on the right hand side.

![Endpoint Browse](app/camel/doc/img/endpoint-file-browse-2.png "Endpoint Browse")

If there is 2 or more messages that were browsable, you can use the control panel to navigate forward and backward among the messages.


##### Sending Message to Endpoint ######

It is possible to send message(s) to any Camel endpoint, by selecting the endpoint from the Camel Tree, as shown below,
and selecting the Send sub-tab, as shown in the screen shot below:

![Endpoint Send](app/camel/doc/img/endpoint-file-send-1.png "Endpoint Send")

The Compose sub-tab which is shown, allows to compose a new message, using the message editor shown above. In this example
we compose a new XML message for 'Claus Ibsen' to be sent as a file to the endpoint 'file://src/data'.

![Endpoint Send](app/camel/doc/img/endpoint-file-send-2.png "Endpoint Send")

To tell Camel what file name to use, we need to add a header, using the name 'CamelFileName'. As we start typing the header
name a list of known header names is listed, which allows us to easily pick the header needed, which is 'CamelFileName'

![Endpoint Send](app/camel/doc/img/endpoint-file-send-3.png "Endpoint Send")

And then we are ready to send the message, by clicking the Send button.



