<%@ taglib uri="core" prefix="c"%>
<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.messenger.PresenceManager,
                 org.jivesoftware.messenger.user.*"
    
%>
<%-- Define Administration Bean --%>
<jsp:useBean id="ad" class="org.jivesoftware.util.WebManager"  />
<% ad.init(request, response, session, application, out ); %>


<c:set var="userID" value="${param.userID}" />
<c:set var="tabName" value="${pageScope.tab}" />
<jsp:useBean id="tabName" type="java.lang.String" />


<%  // Get params
    long ui = ParamUtils.getLongParameter(request,"userID",-1L);

    // Load the user
    User foundUser = ad.getUserManager().getUser(ui);

    // Get a presence manager
    PresenceManager presenceManager = ad.getPresenceManager();
%>

<table class="jive-tabs" cellpadding="0" cellspacing="0" border="0" width="100%">
<tr>
<c:set var="tabCount" value="1" />

    <td class="jive-<%= (("props".equals(tabName)) ? "selected-" : "") %>tab" width="1%" nowrap>
        <a href="user-properties.jsp?userID=<c:out value="${userID}"/>">User Properties</a>
    </td>
    <td class="jive-tab-spacer" width="1%"><img src="images/blank.gif" width="5" height="1" border="0"></td>

<c:set var="tabCount" value="${tabCount + 1}" />

    <td class="jive-<%= (("edit".equals(tabName)) ? "selected-" : "") %>tab" width="1%" nowrap>
        <a href="user-edit-form.jsp?userID=<c:out value="${userID}"/>">Edit User</a>
    </td>
    <td class="jive-tab-spacer" width="1%"><img src="images/blank.gif" width="5" height="1" border="0"></td>

<c:set var="tabCount" value="${tabCount + 1}" />

    <%  // Only show the message tab if the user is online
        if (presenceManager.isAvailable(foundUser)) {
    %>

        <td class="jive-<%= (("message".equals(tabName)) ? "selected-" : "") %>tab" width="1%" nowrap>
            <a href="user-message.jsp?userID=<c:out value="${userID}"/>">Send Message</a>
        </td>
        <td class="jive-tab-spacer" width="1%"><img src="images/blank.gif" width="5" height="1" border="0"></td>

        <c:set var="tabCount" value="${tabCount + 1}" />


    <%  } %>

    <td class="jive-<%= (("pass".equals(tabName)) ? "selected-" : "") %>tab" width="1%" nowrap>
        <a href="user-password.jsp?userID=<c:out value="${userID}"/>">Change Password</a>
    </td>
    <td class="jive-tab-spacer" width="1%"><img src="images/blank.gif" width="5" height="1" border="0"></td>

<c:set var="tabCount" value="${tabCount + 1}" />

    <td class="jive-<%= (("delete".equals(tabName)) ? "selected-" : "") %>tab" width="1%" nowrap>
        <a href="user-delete.jsp?userID=<c:out value="${userID}"/>">Delete User</a>
    </td>
    <td class="jive-tab-spacer" width="1%"><img src="images/blank.gif" width="5" height="1" border="0"></td>
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
<tr><td width="1%"><img src="images/blank.gif" width="1" height="1" border="0"></td></tr>
</table>
<table bgcolor="#eeeeee" cellpadding="0" cellspacing="0" border="0" width="100%">
<tr><td width="1%"><img src="images/blank.gif" width="1" height="1" border="0"></td></tr>
</table>