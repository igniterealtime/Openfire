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
                 org.jivesoftware.messenger.auth.GroupNotFoundException"
    isErrorPage="true"
%>

<%  boolean debug = "true".equals(JiveGlobals.getProperty("skin.default.debug"));
    if (debug) {
        exception.printStackTrace();
    }
%>

<%@ include file="header.jsp" %>

<%  // Title of this page and breadcrumbs
    String title = "Jive Messenger Admin Error";
    String[][] breadcrumbs = {
        { "Home", "main.jsp" },
        { title, "error.jsp" }
    };
%>
<%@ include file="title.jsp" %>



<%  if (exception instanceof UnauthorizedException) { %>

    <p>
    You don't have admin privileges to perform this operation.
    </p>

<%  } else if (exception instanceof UserNotFoundException) {
        String userID = ParamUtils.getParameter(request,"userID");
        String username = ParamUtils.getParameter(request,"username");
        if (userID == null) {
            userID = username;
        }
%>
        <p>
        The requested user
        <%  if (userID != null) { %>
            (id: <%= userID %>)
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
    <p style="color:#999;">
    Exception:
    <pre style="color:#999;">
<%= sout.toString() %>
    </pre>
    </p>

<%  } %>

<%@ include file="footer.jsp" %>