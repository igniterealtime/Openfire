<%@ page import="java.io.*,
                 org.jivesoftware.util.JiveGlobals,
                 org.jivesoftware.openfire.auth.UnauthorizedException"
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

<%  } %>

<%  if (exception != null) {
        StringWriter sout = new StringWriter();
        PrintWriter pout = new PrintWriter(sout);
        exception.printStackTrace(pout);
%>
    <fmt:message key="error.exception" />
    <pre>
<%=     sout.toString() %>
    </pre>

<%  } %>