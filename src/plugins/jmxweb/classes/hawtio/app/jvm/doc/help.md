### Connect

The Connect tab allows you to connect to local and remote Jolokia instances so you can examine JVMs.

The "Remote" sub-tab is used to manually add connection details for a Jolokia instance.  You can store connection details and quickly recall the details of a connection and connect.

The use proxy option should often be enabled, as hawtio is running in your browser; usually due to CORS; you cannot open a different host or port from your browser (due to browse security restrictions); so we have to use a proxy servlet inside the hawtio web app to proxy all requests for a different jolokia server - so we can communicate with a different jolokia agent.
If you use the hawtio Chrome Extension this isn’t required; since Chrome Extensions are allowed to connect to any host/port.

The "Local" sub-tab lists local JVMs running on your machine and allows you to install the Jolokia JVM agent into a running JVM and connect to it.
For this to actually work you need to have your JDK's "tools.jar" in the classpath, along with Jolokia's JVM agent jar.

The "Discover" sub-tab lists all JVMs which Jolokia could discover in the network, using its built-in discovery.
