<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%--
--%>

<%@ page import="org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.openfire.PresenceManager,
                 org.jivesoftware.openfire.user.*"
    
%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%-- Define Administration Bean --%>
<jsp:useBean id="ad" class="org.jivesoftware.util.WebManager"  />
<% ad.init(request, response, session, application, out ); %>


<c:set var="username" value="${param.username}" />
<c:set var="tabName" value="${pageScope.tab}" />
<jsp:useBean id="tabName" type="java.lang.String" />


<%  // Get params
    String uname = ParamUtils.getParameter(request,"username");

    // Load the user
    User foundUser = ad.getUserManager().getUser(uname);

    // Get a presence manager
    PresenceManager presenceManager = ad.getPresenceManager();
%>

<table class="jive-tabs" cellpadding="0" cellspacing="0" border="0" width="100%">
<tr>
<c:set var="tabCount" value="1" />

    <td class="jive-<%= (("props".equals(tabName)) ? "selected-" : "") %>tab" width="1%" nowrap>
        <a href="user-properties.jsp?username=<c:out value="${username}"/>"><fmt:message key="user.tabs.properties" /></a>
    </td>
    <td class="jive-tab-spacer" width="1%"><img src="images/blank.gif" width="5" height="1" border="0" alt=""></td>

<c:set var="tabCount" value="${tabCount + 1}" />

    <td class="jive-<%= (("edit".equals(tabName)) ? "selected-" : "") %>tab" width="1%" nowrap>
        <a href="user-edit-form.jsp?username=<c:out value="${username}"/>"><fmt:message key="user.tabs.edit" /></a>
    </td>
    <td class="jive-tab-spacer" width="1%"><img src="images/blank.gif" width="5" height="1" border="0" alt=""></td>

<c:set var="tabCount" value="${tabCount + 1}" />

    <%  // Only show the message tab if the user is online
        if (presenceManager.isAvailable(foundUser)) {
    %>

        <td class="jive-<%= (("message".equals(tabName)) ? "selected-" : "") %>tab" width="1%" nowrap>
            <a href="user-message.jsp?username=<c:out value="${username}"/>"><fmt:message key="user.tabs.send" /></a>
        </td>
        <td class="jive-tab-spacer" width="1%"><img src="images/blank.gif" width="5" height="1" border="0" alt=""></td>

        <c:set var="tabCount" value="${tabCount + 1}" />


    <%  } %>

    <td class="jive-<%= (("pass".equals(tabName)) ? "selected-" : "") %>tab" width="1%" nowrap>
        <a href="user-password.jsp?username=<c:out value="${username}"/>"><fmt:message key="user.tabs.change_pwd" /></a>
    </td>
    <td class="jive-tab-spacer" width="1%"><img src="images/blank.gif" width="5" height="1" border="0" alt=""></td>

<c:set var="tabCount" value="${tabCount + 1}" />

    <td class="jive-<%= (("delete".equals(tabName)) ? "selected-" : "") %>tab" width="1%" nowrap>
        <a href="user-delete.jsp?username=<c:out value="${username}"/>"><fmt:message key="user.tabs.delete_user" /></a>
    </td>
    <td class="jive-tab-spacer" width="1%"><img src="images/blank.gif" width="5" height="1" border="0" alt=""></td>
<c:set var="width" value="${100-(tabCount*2)}" />
    <td class="jive-tab-spring" width="<c:out value="${width}" />%" align="right" nowrap>
        &nbsp;
    </td>
</tr>
<tr>
    <td class="jive-tab-bar" colspan="99">
        &nbsp;
    </td>
</tr>
</table>
<table bgcolor="#dddddd" cellpadding="0" cellspacing="0" border="0" width="100%">
<tr><td width="1%"><img src="images/blank.gif" width="1" height="1" border="0" alt=""></td></tr>
</table>
<table bgcolor="#eeeeee" cellpadding="0" cellspacing="0" border="0" width="100%">
<tr><td width="1%"><img src="images/blank.gif" width="1" height="1" border="0" alt=""></td></tr>
</table>
