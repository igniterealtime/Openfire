<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.messenger.user.*,
                 java.util.HashMap,
                 org.jivesoftware.admin.*,
                 java.util.Map,
                 java.net.URLEncoder"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<%-- Define Administration Bean --%>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"/>
<%   webManager.init(request, response, session, application, out ); %>
<%  
    // Get parameters
    boolean search = ParamUtils.getBooleanParameter(request,"search");
    String username = ParamUtils.getParameter(request,"username");

    // Handle a cancel
    if (request.getParameter("cancel") != null) {
        response.sendRedirect("user-summary.jsp");
        return;
    }

    // Handle a search execute:
    Map errors = new HashMap();
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
<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean"/>
<%    // Title of this page and breadcrumbs
    String title = LocaleUtils.getLocalizedString("user.search.title");
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "user-search.jsp"));
    pageinfo.setPageID("user-search");
%>
<jsp:include page="top.jsp" flush="true"/>
<jsp:include page="title.jsp" flush="true"/>
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
          <input type="text" name="username" value="<%= ((username!=null) ? username : "") %>" size="30" maxlength="75"/>
        </td>
      </tr>
     <tr><td colspan="2" nowrap><input type="submit" name="search" value="Search!"/><input type="submit" name="cancel" value="Cancel"/></td>
     </tr>
    </table>
  </fieldset>
</form>
<script language="JavaScript" type="text/javascript">
document.f.username.focus();
</script>
<jsp:include page="bottom.jsp" flush="true"/>
