<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.messenger.SessionManager,
                 java.util.*,
                 org.jivesoftware.messenger.Session" %>

<%@ include file="global.jsp" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<%  // Check to see if the server is down.
    if (xmppServer == null) {
        response.sendRedirect("error-serverdown.jsp");
        return;
    }
%>

<%  // Get parameters
    boolean doFilter = ParamUtils.getBooleanParameter(request, "doFilter");
    boolean choose = ParamUtils.getBooleanParameter(request,"choose");
    boolean remove = ParamUtils.getBooleanParameter(request, "remove");
    String username = ParamUtils.getParameter(request, "username");
    String usernameTF = ParamUtils.getParameter(request, "usernameTF");
    String usernameSEL = ParamUtils.getParameter(request, "usernameSEL");
    if (username == null) {
        if (choose) {
            username = usernameSEL;
        }
        else {
            username = usernameTF;
        }
    }

    boolean errors = false;
    if (doFilter) {
        if (username == null) {
            errors = true;
        }
        else {
            // Set the username in the session, return to the session summary page:
            session.setAttribute("messenger.admin.session-summary.username",username);
            response.sendRedirect("session-summary.jsp");
            return;
        }
    }

    if (remove) {
        // Remove the filtered username from the session:
        session.removeAttribute("messenger.admin.session-summary.username");
        response.sendRedirect("session-summary.jsp");
        return;
    }
%>

<jsp:include page="header.jsp" flush="true" />

<%  // Title of this page and breadcrumbs
    String title = LocaleUtils.getLocalizedString("session.filter.title");
    String[][] breadcrumbs = {
        {"Home", "index.jsp"},
        {"Session Summary", "session-summary.jsp"},
        {title, "session-filter.jsp"}
    };
%>
<jsp:include page="title.jsp" flush="true" />

<p>
<fmt:message key="session.filter.info" />
</p>

<%  if (errors) { %>

    <p class="jive-error-text">
    <fmt:message key="session.filter.enter_username" />
    </p>

<%  } %>

<form action="session-filter.jsp" name="filterform">
<input type="hidden" name="doFilter" value="true">

<table cellpadding="3" cellspacing="0" border="0" width="100%">
<tr valign="top">
    <td width="1%" nowrap align="center">
        <input type="radio" name="choose" value="false">
    </td>
    <td width="1%" nowrap>
        <fmt:message key="session.filter.specify_username" />
    </td>
    <td width="98%">
        <input type="text" name="usernameTF" size="30" maxlength="100"
         value="<%= ((username != null) ? username : "") %>"
         onfocus="this.form.choose[0].checked=true;">
    </td>
</tr>
<tr valign="top">
    <td width="1%" nowrap align="center">
        <input type="radio" name="choose" value="true">
    </td>
    <td width="1%" nowrap>
        <fmt:message key="session.filter.choose_user" />
    </td>
    <td width="98%">
        <select size="1" name="usernameSEL" onfocus="this.form.choose[1].checked=true;">
            <%
                Collection<User> users = userManager.getUsers();
                for (User user: users) {
            %>
                <option value="<%= user.getUsername() %>"><%= user.getUsername() %></option>

            <%  } %>
        </select>
    </td>
</tr>
</table>

<br>

<input type="submit" value="Save and Return">
<input type="submit" value="<fmt:message key="global.cancel" />" onclick="location.href='session-summary.jsp';">

</form>

<script language="JavaScript" type="text/javascript">
<%  if (!doFilter) { %>

    document.filterform.usernameTF.focus();

<%  } else { %>

    <%  if (usernameSEL == null) { %>

        document.filterform.usernameTF.focus();

    <%  } %>

<%  } %>
</script>

<jsp:include page="footer.jsp" flush="true" />