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

<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.openfire.*,
                 java.util.HashMap,
                 java.util.Map,
                 java.text.DecimalFormat"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" scope="page" />
<% webManager.init(request, response, session, application, out ); %>

<html>
<head>
<title><fmt:message key="offline.messages.title"/></title>
<meta name="pageID" content="server-offline-messages"/>
<meta name="helpPage" content="manage_offline_messages.html"/>
</head>
<body>

<c:set var="success" />

<%! // Global vars and methods:

    // Strategy definitions:
    static final int BOUNCE = 1;
    static final int DROP = 2;
    static final int STORE = 3;
    static final int ALWAYS_STORE = 4;
    static final int STORE_AND_BOUNCE = 5;
    static final int STORE_AND_DROP = 6;
%>

<%  // Get parameters
	OfflineMessageStrategy manager = webManager.getXMPPServer().getOfflineMessageStrategy();
    boolean update = request.getParameter("update") != null;
    int strategy = ParamUtils.getIntParameter(request,"strategy",-1);
    int storeStrategy = ParamUtils.getIntParameter(request,"storeStrategy",-1);
    double quota = ParamUtils.getDoubleParameter(request,"quota", manager.getQuota()/1024);
    DecimalFormat format = new DecimalFormat("#0.00");

    // Update the session kick policy if requested
    Map<String, String> errors = new HashMap<String, String>();
    if (update) {
        // Validate params
        if (strategy != BOUNCE && strategy != DROP && strategy != STORE) {
            errors.put("general","Please choose one of the 3 strategies below.");
        }
        else {
            // Validate the storage policy of store strat is chosen:
            if (strategy == STORE) {
                if (storeStrategy != ALWAYS_STORE && storeStrategy != STORE_AND_BOUNCE
                        && storeStrategy != STORE_AND_DROP)
                {
                    errors.put("general", LocaleUtils.getLocalizedString("offline.messages.choose_policy"));
                }
                else {
                    // Validate the store size limit:
                    if (quota <= 0) {
                        errors.put("quota", LocaleUtils.getLocalizedString("offline.messages.enter_store_size"));
                    }
                }
            }
        }
        // If no errors, continue:
        if (errors.size() == 0) {

            if (strategy == STORE) {
                manager.setType(OfflineMessageStrategy.Type.store);

                if (storeStrategy == STORE_AND_BOUNCE) {
                    manager.setType(OfflineMessageStrategy.Type.store_and_bounce);
                }
                else if (storeStrategy == STORE_AND_DROP) {
                    manager.setType(OfflineMessageStrategy.Type.store_and_drop);
                }
                else /* (storeStrategy == ALWAYS_STORE) */ {
                    manager.setType(OfflineMessageStrategy.Type.store);
                }
            }
            else {
                if (strategy == BOUNCE) {
                    manager.setType(OfflineMessageStrategy.Type.bounce);
                }
                else if (strategy == DROP) {
                    manager.setType(OfflineMessageStrategy.Type.drop);
                }
            }

            manager.setQuota((int)(quota*1024));

            // Log the event
            webManager.logEvent("edited offline message settings", "quote = "+quota+"\ntype = "+manager.getType());
%>
<c:set var="success" value="true" />
<%
        }
    }

    // Update variable values

    if (errors.size() == 0) {
        if (manager.getType() == OfflineMessageStrategy.Type.store
                || manager.getType() == OfflineMessageStrategy.Type.store_and_bounce
                || manager.getType() == OfflineMessageStrategy.Type.store_and_drop)
        {
            strategy = STORE;

            if (manager.getType() == OfflineMessageStrategy.Type.store_and_bounce) {
                storeStrategy = STORE_AND_BOUNCE;
            }
            else if (manager.getType() == OfflineMessageStrategy.Type.store_and_drop) {
                storeStrategy = STORE_AND_DROP;
            }
            else /*(manager.getType() == OfflineMessageStrategy.STORE)*/ {
                storeStrategy = ALWAYS_STORE;
            }
        }
        else {
            if (manager.getType() == OfflineMessageStrategy.Type.bounce) {
                strategy = BOUNCE;
            }
            else if (manager.getType() == OfflineMessageStrategy.Type.drop) {
                strategy = DROP;
            }
        }

        quota = ((double)manager.getQuota()) / (1024);
        if (quota < 0) {
            quota = 0;
        }
    }
%>


<c:if test="${success}" >
    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="offline.messages.update" />
        </td></tr>
    </tbody>
    </table>
    </div><br>
</c:if>

<%  if (errors.containsKey("general") || errors.containsKey("quota")) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <%  if (errors.containsKey("general")) { %>
            <%= errors.get("general") %>
        <%  } else if (errors.containsKey("quota")) { %>
            <%= errors.get("quota") %>
        <%  } %>
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<p>
<fmt:message key="offline.messages.info" />
</p>

<p>
<fmt:message key="offline.messages.size" />
<b><%= format.format(OfflineMessageStore.getInstance().getSize()/1024.0/1024.0) %> MB</b>
</p>



<!-- BEGIN 'Offline Message Policy' -->
<form action="offline-messages.jsp">
	<div class="jive-contentBoxHeader">
		<fmt:message key="offline.messages.policy" />
	</div>
	<div class="jive-contentBox">
		<table cellpadding="3" cellspacing="0" border="0">
		<tbody>
			<tr valign="top" class="">
				<td width="1%" nowrap>
					<input type="radio" name="strategy" value="<%= STORE %>" id="rb03"
					 <%= ((strategy==STORE) ? "checked" : "") %>>
				</td>
				<td width="99%">
					<label for="rb03"><b><fmt:message key="offline.messages.store_option" /></b></label> - <fmt:message key="offline.messages.storage_openfire" />
				</td>
			</tr>
			<tr valign="top">
				<td width="1%" nowrap>
					&nbsp;
				</td>
				<td width="99%">

					<table cellpadding="4" cellspacing="0" border="0">
					<tr valign="top">
						<td width="1%" nowrap>
							<input type="radio" name="storeStrategy" value="<%= STORE_AND_BOUNCE%>" id="rb06"
							 onclick="this.form.strategy[2].checked=true;"
							 <%= ((storeStrategy==STORE_AND_BOUNCE) ? "checked" : "") %>>
						</td>
						<td width="99%">
							<label for="rb06"><b><fmt:message key="offline.messages.bounce" /></b></label> - <fmt:message key="offline.messages.bounce_info" />
						</td>
					</tr>
                    <tr valign="top">
						<td width="1%" nowrap>
							<input type="radio" name="storeStrategy" value="<%= ALWAYS_STORE %>" id="rb05"
							 onclick="this.form.strategy[2].checked=true;"
							 <%= ((storeStrategy==ALWAYS_STORE) ? "checked" : "") %>>
						</td>
						<td width="99%">
							<label for="rb05"><b><fmt:message key="offline.messages.always_store" /></b></label> - <fmt:message key="offline.messages.always_store_info" />
						</td>
					</tr>
					<tr valign="top">
						<td width="1%" nowrap>
							<input type="radio" name="storeStrategy" value="<%= STORE_AND_DROP %>" id="rb07"
							 onclick="this.form.strategy[2].checked=true;"
							 <%= ((storeStrategy==STORE_AND_DROP) ? "checked" : "") %>>
						</td>
						<td width="99%">
							<label for="rb07"><b><fmt:message key="offline.messages.drop" /></b></label> - <fmt:message key="offline.messages.drop_info" />
						</td>
					</tr>
					<tr>
						<td colspan="2">
							<fmt:message key="offline.messages.storage_limit" />
							<input type="text" size="5" maxlength="12" name="quota"
							 value="<%= (quota>0 ? ""+format.format(quota) : "") %>"
							 onclick="this.form.strategy[2].checked=true;">
							KB
						</td>
					</tr>
					</table>
				</td>
			</tr>
            <tr valign="top">
				<td width="1%" nowrap>
					<input type="radio" name="strategy" value="<%= BOUNCE %>" id="rb01"
					 <%= ((strategy==BOUNCE) ? "checked" : "") %>>
				</td>
				<td width="99%">
					<label for="rb01"><b><fmt:message key="offline.messages.bounce_option" /></b></label> - <fmt:message key="offline.messages.never_back" />
				</td>
			</tr>
			<tr valign="top">
				<td width="1%" nowrap>
					<input type="radio" name="strategy" value="<%= DROP %>" id="rb02"
					 <%= ((strategy==DROP) ? "checked" : "") %>>
				</td>
				<td width="99%">
					<label for="rb02"><b><fmt:message key="offline.messages.drop_option" /></b></label> - <fmt:message key="offline.messages.never_store" />
				</td>
			</tr>
        </tbody>
		</table>
	</div>
    <input type="submit" name="update" value="<fmt:message key="global.save_settings" />">
</form>
<!-- END 'Offline Message Policy' -->


</body>
</html>