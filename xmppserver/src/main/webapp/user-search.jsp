<%@ page contentType="text/html; charset=UTF-8" %>
<%--
--%>

<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.openfire.user.*,
                 java.util.HashMap,
                 java.util.Map,
                 java.net.URLEncoder"
%><%@ page import="org.xmpp.packet.JID"%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%-- Define Administration Bean --%>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"/>
<%   webManager.init(request, response, session, application, out ); %>
<%  
    // Get parameters
    boolean search = ParamUtils.getBooleanParameter(request,"search");
    String username = ParamUtils.getParameter(request,"username");
    username = JID.escapeNode(username);

    // Handle a cancel
    if (request.getParameter("cancel") != null) {
        response.sendRedirect("user-summary.jsp");
        return;
    }

    // Handle a search execute:
    Map<String,String> errors = new HashMap<String,String>();
    if (search) {
        User user = null;
        try {
            user = webManager.getUserManager().getUser(username);
        }
        catch (Exception e2) {
            errors.put("username","username");
        }
        if (user != null) {
            // found the user, so redirect to the user properties page:
            response.sendRedirect("user-properties.jsp?username=" +
                    URLEncoder.encode(user.getUsername(), "UTF-8"));
            return;
        }
    }
%>

<html>
    <head>
        <title><fmt:message key="user.search.title"/></title>
        <meta name="pageID" content="user-search"/>
        <meta name="helpPage" content="search_for_a_user.html"/>
    </head>
    <body>

<%    if (errors.size() > 0) { %>
<p class="jive-error-text"><fmt:message key="user.search.not_found" /></p>
<%    } %>
<form name="f" action="user-search.jsp">
  <input type="hidden" name="search" value="true"/>
  <fieldset>
    <legend><fmt:message key="user.search.search_user" /></legend>
    <table cellpadding="3" cellspacing="1" border="0" width="600">
      <tr class="c1">
        <td width="1%" nowrap><fmt:message key="user.create.username" />:</td>
        <td class="c2">
          <input type="text" name="username" value="<%= ((username!=null) ? StringUtils.escapeForXML(username) : "") %>" size="30" maxlength="75"/>
        </td>
      </tr>
     <tr><td colspan="2" nowrap><input type="submit" name="search" value="<fmt:message key="user.search.search" />"/><input type="submit" name="cancel" value="<fmt:message key="global.cancel" />"/></td>
     </tr>
    </table>
  </fieldset>
</form>
<script language="JavaScript" type="text/javascript">
document.f.username.focus();
</script>

    </body>
</html>
