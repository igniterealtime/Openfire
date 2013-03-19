<%--
  -	$RCSfile$
  -	$Revision: 28108 $
  -	$Date: 2006-03-06 13:49:44 -0800 (Mon, 06 Mar 2006) $
--%>

<%@ page
import     ="java.io.*,
             org.jivesoftware.util.ParamUtils,
             org.jivesoftware.xmpp.workgroup.UnauthorizedException,
             org.jivesoftware.openfire.user.UserNotFoundException"
isErrorPage="true"%>

<%
    if (exception instanceof UnauthorizedException) {
%>
                  <p>
          You don't have admin privileges to perform this operation.
                  </p>

<%
}
else if (exception instanceof UserNotFoundException) {
    String username = ParamUtils.getParameter(request, "username");
%>
                              <p>
          The requested user

<%
          if (username != null) {
%>

            (username: <%= username %>)

<%
          }
%>

          was not found.
                              </p>

<%
    }
%>

<%
    if (exception != null) {
      StringWriter sout = new StringWriter();
      PrintWriter  pout = new PrintWriter(sout);
      exception.printStackTrace(pout);
%>

      Spark Fastpath Exception:

      <pre> <%= sout.toString() %> </pre>

<%
    }
%>
