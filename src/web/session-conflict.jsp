<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ taglib uri="core" prefix="c" %>
<%@ taglib uri="fmt" prefix="fmt" %>

<%@ page import="org.jivesoftware.util.*,
                 java.util.Iterator,
                 org.jivesoftware.messenger.*,
                 java.util.Date,
                 java.text.DateFormat,
                 java.util.HashMap,
                 java.util.Map,
                 org.jivesoftware.admin.AdminPageBean"
    errorPage="error.jsp"
%>

<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "Conflict Policy";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Session Conflict", "session-conflict.jsp"));
    pageinfo.setPageID("server-session-conflict");
%>

<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<%  // Get parameters
    boolean update = request.getParameter("update") != null;
    int kickPolicy = ParamUtils.getIntParameter(request,"kickPolicy",Integer.MAX_VALUE);
    int kickValue = ParamUtils.getIntParameter(request,"kickValue",-2);

    if (kickPolicy == -2) {
        kickPolicy = admin.getSessionManager().getConflictKickLimit();
    }

    // Update the session kick policy if requested
    Map errors = new HashMap();
    if (update) {
        // Validate params
        if (kickPolicy != 0 && kickPolicy != 1 && kickPolicy != SessionManager.NEVER_KICK) {
            if (kickValue <= 1) {
                errors.put("kickValue","");
            }
        }
        // If no errors, continue:
        if (errors.size() == 0) {
            if (kickPolicy != 0 && kickPolicy != 1 && kickPolicy != SessionManager.NEVER_KICK) {
                admin.getSessionManager().setConflictKickLimit(kickValue);
            }
            else {
                admin.getSessionManager().setConflictKickLimit(kickPolicy);
            }
            %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        Settings updated successfully.
        </td></tr>
    </tbody>
    </table>
    </div><br>

            <%
          
          
        }
    }

    // Update variable values
    kickPolicy = admin.getSessionManager().getConflictKickLimit();
%>

<p>
<fmt:message key="title" bundle="${lang}" /> allows multiple logins to the same user account by assigning a unique "resource name"
to each connection. If a connection requests a resource name that is already in use, the server must
decide how to handle the conflict. The options on this page allow you to determine if the server
always kicks off existing connections, never kicks off existing connections, or sets the number of
login attempts that should be rejected before kicking off an
existing connection. The last option allows users to receive an error when logging in that
allows them to request a different resource name.
</p>

<form action="session-conflict.jsp" method="post">

<fieldset>
    <legend>Set Conflict Policy</legend>
    <div>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr valign="top">
            <td width="1%">
                <input type="radio" name="kickPolicy" value="0" id="rb01"
                 <%= ((kickPolicy==0) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb01"><b>Always kick</b></label> - If there is a resource conflict,
                immediately kick the other resource.
            </td>
        </tr>
        <tr valign="top">
            <td width="1%">
                <input type="radio" name="kickPolicy" value="<%= SessionManager.NEVER_KICK %>" id="rb02"
                 <%= ((kickPolicy==SessionManager.NEVER_KICK) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb02"><b>Never kick</b></label> - If there is a resource conflict, don't
                allow the new resource to log in.
            </td>
        </tr>
        <tr valign="top">
            <td width="1%">
                <input type="radio" name="kickPolicy" value="1" id="rb04"
                 <%= ((kickPolicy==1) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb04"><b>Allow one login attempt</b></label> - If there is a resource conflict,
                report an error one time but don't kick the existing connection.
            </td>
        </tr>
<%  // Figure out if the kick policy is neither 0 nor SessionManager.NEVER_KICK:
    boolean assignedKickPolicy = false;
    if (kickPolicy != 0 && kickPolicy != 1 && kickPolicy != SessionManager.NEVER_KICK) {
       assignedKickPolicy = true;
    }
%>
        <tr valign="top">
            <td width="1%">
                <input type="radio" name="kickPolicy" value="<%= Integer.MAX_VALUE %>" id="rb03"
                 onfocus="this.form.kickValue.focus();"
                 <%= ((assignedKickPolicy) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb03"><b>Assign kick value</b></label> - Specify the number of login
                attempts allowed before conflicting resources are kicked. You must specify a
                number greater than one.
            </td>
        </tr>
        <tr valign="top">
            <td width="1%">
                &nbsp;
            </td>
            <td width="99%">
                <%  if (errors.get("kickValue") != null) { %>
                    <span class="jive-error-text">
                    Please enter a value greater than 1.
                    </span><br>
                <%  } %>
                <input type="text" name="kickValue" value="<%= ((assignedKickPolicy) ? ""+kickPolicy : "") %>"
                 size="5" maxlength="10"
                 onclick="this.form.kickPolicy[3].checked=true;">
            </td>
        </tr>
    </tbody>
    </table>
    </div>
</fieldset>

<br><br>

<input type="submit" name="update" value="Save Settings">

</form>

<jsp:include page="bottom.jsp" flush="true" />
