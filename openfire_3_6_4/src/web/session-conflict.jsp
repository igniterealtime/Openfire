<%--
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004-2008 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution, or a commercial license
  - agreement with Jive.
--%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.openfire.*,
                 java.util.HashMap,
                 java.util.Map"
    errorPage="error.jsp"
%>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<html>
<head>
<title><fmt:message key="session.conflict.title"/></title>
<meta name="pageID" content="server-session-conflict"/>
<meta name="helpPage" content="set_the_server_resource_conflict_policy.html"/>
</head>
 <body>

<%  // Get parameters
    boolean update = request.getParameter("update") != null;
    int kickPolicy = ParamUtils.getIntParameter(request,"kickPolicy",Integer.MAX_VALUE);
    int kickValue = ParamUtils.getIntParameter(request,"kickValue",-2);

    if (kickPolicy == -2) {
        kickPolicy = webManager.getSessionManager().getConflictKickLimit();
    }

    // Update the session kick policy if requested
    Map<String,String> errors = new HashMap<String,String>();
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
                webManager.getSessionManager().setConflictKickLimit(kickValue);
            }
            else {
                webManager.getSessionManager().setConflictKickLimit(kickPolicy);
            }
            %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="session.conflict.update" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

            <%
          
          
        }
    }

    // Update variable values
    kickPolicy = webManager.getSessionManager().getConflictKickLimit();
%>

<p>
<fmt:message key="session.conflict.info" />
</p>

<!-- BEGIN 'Set Conflict Policy' -->
<form action="session-conflict.jsp" method="post">
	<div class="jive-contentBoxHeader">
		<fmt:message key="session.conflict.policy" />
	</div>
	<div class="jive-contentBox">
		<table cellpadding="3" cellspacing="0" border="0">
		<tbody>
			<tr valign="middle">
				<td valign="top" width="1%">
					<input type="radio" name="kickPolicy" value="0" id="rb01"
					 <%= ((kickPolicy==0) ? "checked" : "") %>>
				</td>
				<td width="99%">
					<label for="rb01"><b><fmt:message key="session.conflict.always_kick" /></b></label> -
					<fmt:message key="session.conflict.always_kick_info" />
				</td>
			</tr>
			<tr valign="middle">
				<td valign="top" width="1%">
					<input type="radio" name="kickPolicy" value="<%= SessionManager.NEVER_KICK %>" id="rb02"
					 <%= ((kickPolicy==SessionManager.NEVER_KICK) ? "checked" : "") %>>
				</td>
				<td width="99%">
					<label for="rb02"><b><fmt:message key="session.conflict.never_kick" /></b></label> -
					<fmt:message key="session.conflict.never_kick_info" />
				</td>
			</tr>
			<tr valign="middle">
				<td valign="top" width="1%">
					<input type="radio" name="kickPolicy" value="1" id="rb04"
					 <%= ((kickPolicy==1) ? "checked" : "") %>>
				</td>
				<td width="99%">
					<label for="rb04"><b><fmt:message key="session.conflict.allow_one" /></b></label> -
					<fmt:message key="session.conflict.resource_conflict" />
				</td>
			</tr>
	<%  // Figure out if the kick policy is neither 0 nor SessionManager.NEVER_KICK:
		boolean assignedKickPolicy = false;
		if (kickPolicy != 0 && kickPolicy != 1 && kickPolicy != SessionManager.NEVER_KICK) {
		   assignedKickPolicy = true;
		}
	%>
			<tr valign="middle">
				<td valign="top" width="1%">
					<input type="radio" name="kickPolicy" value="<%= Integer.MAX_VALUE %>" id="rb03"
					 onfocus="this.form.kickValue.focus();"
					 <%= ((assignedKickPolicy) ? "checked" : "") %>>
				</td>
				<td width="99%">
					<label for="rb03"><b><fmt:message key="session.conflict.kick_value" /></b></label> -
					<fmt:message key="session.conflict.kick_value_info" />

				</td>
			</tr>
			<tr valign="middle">
				<td width="1%">
					&nbsp;
				</td>
				<td width="99%">
					<%  if (errors.get("kickValue") != null) { %>
						<span class="jive-error-text">
						<fmt:message key="session.conflict.enter_value" />
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
<input type="submit" name="update" value="<fmt:message key="global.save_settings" />">
</form>
<!-- END 'Set Conflict Policy' -->


</body>
</html>