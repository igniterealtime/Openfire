<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="java.io.*,
                 org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.util.Version,
                 org.jivesoftware.messenger.JiveGlobals,
                 org.jivesoftware.messenger.auth.UnauthorizedException,
                 org.jivesoftware.messenger.user.UserNotFoundException,
                 org.jivesoftware.messenger.group.GroupNotFoundException"
    isErrorPage="true"
%>

<%  boolean debug = "true".equals(JiveGlobals.getProperty("skin.default.debug"));
    if (debug) {
        exception.printStackTrace();
    }
%>

<%  if (exception instanceof UnauthorizedException) { %>

    <p>
    You don't have admin privileges to perform this operation.
    </p>

<%  } else if (exception instanceof UserNotFoundException) {
        String username = ParamUtils.getParameter(request,"username");
%>
        <p>
        The requested user
        <%  if (username != null) { %>
            (username: <%= username %>)
        <%  } %>
        was not found.
        </p>

<%  } else if (exception instanceof GroupNotFoundException) { %>

    <p>
    The requested group was not found.
    </p>
    
<%  } %>

<%  if (exception != null) {
        StringWriter sout = new StringWriter();
        PrintWriter pout = new PrintWriter(sout);
        exception.printStackTrace(pout);
%>
    Exception:
    <pre>
<%= sout.toString() %>
    </pre>

<%  } %>