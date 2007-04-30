<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="java.io.*,
                 org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.util.JiveGlobals,
                 org.jivesoftware.openfire.auth.UnauthorizedException,
                 org.jivesoftware.openfire.user.UserNotFoundException,
                 org.jivesoftware.openfire.group.GroupNotFoundException"
    isErrorPage="true"
%>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<%  boolean debug = "true".equals(JiveGlobals.getProperty("skin.default.debug"));
    if (debug) {
        exception.printStackTrace();
    }
%>

<%  if (exception instanceof UnauthorizedException) { %>

    <p>
    <fmt:message key="error.admin_privileges" />
    </p>

<%  } else if (exception instanceof UserNotFoundException) {
        String username = ParamUtils.getParameter(request,"username");
%>
        <p>
        <%  if (username == null) { %>
            <fmt:message key="error.requested_user_not_found" />
        <%  } else { %>
            <fmt:message key="error.specific_user_not_found">
                <fmt:param value="${username}" />
            </fmt:message>
        <%  } %>
        </p>

<%  } else if (exception instanceof GroupNotFoundException) { %>

    <p>
    <fmt:message key="error.not_found_group" />
    </p>
    
<%  } %>

<%  if (exception != null) {
        StringWriter sout = new StringWriter();
        PrintWriter pout = new PrintWriter(sout);
        exception.printStackTrace(pout);
%>
    <fmt:message key="error.exception" />
    <pre>
<%= sout.toString() %>
    </pre>

<%  } %>