<%@ page import="org.jivesoftware.messenger.container.Container,
                 org.jivesoftware.messenger.container.ServiceLookup,
                 org.jivesoftware.messenger.container.ServiceLookupFactory,
                 org.jivesoftware.util.Log,
                 org.jivesoftware.messenger.auth.UnauthorizedException"%>
<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
  <head><title>Simple jsp page</title></head>
  <body>Place your content here</body>
  <%  // Getting to this page means everything is setup, so restart the container.
    // flush the page first
    out.flush();

    // Let them have a chance to get other page assets
    try {
        Thread.sleep(3000L);
    }
    catch (Exception ignored) {}

    // then restart the server
    Container container = null;
    try {
        ServiceLookup lookup = ServiceLookupFactory.getLookup();
        container = (Container)lookup.lookup(Container.class);
    }
    catch (Exception e) {
        Log.error(e);
    }
    // do the restart if the container is not null
    if (container != null) {
        try {
            container.restart();
        }
        catch (UnauthorizedException e) {
            Log.error(e);
        }
    }
%>
</html>