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

<%@ page import="org.jivesoftware.messenger.audit.AuditManager,
                   org.jivesoftware.admin.*,
                   org.jivesoftware.util.*,
                   java.util.*"
    errorPage="error.jsp"
%>

<%@ taglib uri="core" prefix="c"%>

  <%-- Define Administration Bean --%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "Audit Policy";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "main.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "audit-policy.jsp"));
    pageinfo.setPageID("server-audit-policy");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

  <%   // Get parameters:
    boolean update = request.getParameter("update") != null;
    boolean auditEnabled = ParamUtils.getBooleanParameter(request,"auditEnabled");
    boolean auditMessages = ParamUtils.getBooleanParameter(request,"auditMessages");
    boolean auditPresence = ParamUtils.getBooleanParameter(request,"auditPresence");
    boolean auditIQ = ParamUtils.getBooleanParameter(request,"auditIQ");
    boolean auditXPath = ParamUtils.getBooleanParameter(request,"auditXPath");
    String newXpathQuery = ParamUtils.getParameter(request,"newXpathQuery");
    String[] xpathQuery = ParamUtils.getParameters(request,"xpathQuery");
    String maxCount = ParamUtils.getParameter(request,"maxCount");
    String maxSize = ParamUtils.getParameter(request,"maxSize");

    // Get an audit manager:
    AuditManager auditManager = (AuditManager)admin.getServiceLookup().lookup(AuditManager.class);

    Map errors = new HashMap();
    if (update) {
        auditManager.setEnabled(auditEnabled);
        auditManager.setAuditMessage(auditMessages);
        auditManager.setAuditPresence(auditPresence);
        auditManager.setAuditIQ(auditIQ);
        /*
        auditManager.setAuditXPath(auditXPath);
        if (newXpathQuery != null) {
            auditManager.addXPath(newXpathQuery);
        }
        for (int i=0; i<xpathQuery.length; i++) {
            auditManager.removeXPath(xpathQuery[i]);
        }
        */
        try {
            auditManager.setMaxFileCount(Integer.parseInt(maxCount));
        } catch (Exception e){
            errors.put("maxCount","maxCount");
        }
        try {
            auditManager.setMaxFileSize(Integer.parseInt(maxSize));
        } catch (Exception e){
            errors.put("maxSize","maxSize");
        }
        // All done, redirect
        if (errors.size() == 0){
        %>
         <p class="jive-success-text">
           Settings Updated.
         </p>
        <%
        }
    }

    // Set page vars
    if (errors.size() == 0) {
        auditEnabled = auditManager.isEnabled();
        auditMessages = auditManager.isAuditMessage();
        auditPresence = auditManager.isAuditPresence();
        auditIQ = auditManager.isAuditIQ();
        auditXPath = auditManager.isAuditXPath();
        maxCount = Integer.toString(auditManager.getMaxFileCount());
        maxSize = Integer.toString(auditManager.getMaxFileSize());
    }
%>

<table cellpadding="4" cellspacing="0" border="0" width="600">
<form action="audit-policy.jsp">
<tr><td colspan="2" class="text">
Jive Messenger can audit XMPP traffic on the server and save the data to
XML data files. The amount of data sent via an XMPP server can be substantial.
Messenger provides several settings to control whether to audit packets, how
audit files are created, and the types of packets to save. In most cases, logging
Message packets will provide all of the data an enterprise requires. Presence
and IQ packets are primarily useful for tracing and troubleshooting XMPP deployments.
</td></tr>



<tr valign="top">
    <td width="1%" nowrap>
        <input type="radio" name="auditEnabled" value="false" id="rb01"
         <%= (!auditEnabled ? "checked" : "") %>>
    </td>
    <td width="99%">
        <label for="rb01">
        <b>Disable Message Auditing</b> - Packets are not logged.
        </label>
    </td>
</tr>
<tr valign="top">
    <td width="1%" nowrap>
        <input type="radio" name="auditEnabled" value="true" id="rb02"
         <%= (auditEnabled ? "checked" : "") %>>
    </td>
    <td width="99%">
        <label for="rb02">
        <b>Enable Message Auditing</b> - Packets are logged with the following options:
        </label>
    </td>
</tr>
<tr valign="top">
    <td width="1%" nowrap>
        &nbsp;
    </td>
    <td width="99%">
        <div class="jive-table">
        <table cellpadding="3" cellspacing="1" border="0" width="100%">
        <tr valign="top">
            <td width="1%" nowrap class="c1">
                Maximum file size (MB):
            </td>
            <td width="99%">
                <input type="text" size="15" maxlength="50" name="maxSize"
                 value="<%= ((maxSize != null) ? maxSize : "") %>">

            <%  if (errors.get("maxSize") != null) { %>

                <span class="jive-error-text">
                Please enter a valid number.
                </span>

            <%  } %>

            </td>
        </tr>
        <tr valign="top">
            <td width="1%" nowrap class="c1">
                Maximum number of files:
            </td>
            <td width="99%">
                <input type="text" size="15" maxlength="50" name="maxCount"
                 value="<%= ((maxCount != null) ? maxCount : "") %>">

                <%  if (errors.get("maxCount") != null) { %>

                    <span class="jive-error-text">
                    Please enter a valid number.
                    </span>

                <%  } %>

            </td>
        </tr>
        <tr valign="top">
            <td width="1%" nowrap class="c1">
                Packets to audit:
            </td>
            <td width="99%">

        <table cellpadding="4" cellspacing="0" border="0" width="100%">
        <tr valign="top">
            <td width="1%" nowrap>
                <input type="checkbox" name="auditMessages" id="cb01"
                 onclick="this.form.auditEnabled[1].checked=true;"
                 <%= (auditMessages ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="cb01">
                <b>Audit Message Packets</b>
                </label>
            </td>
        </tr>
        <tr valign="top">
            <td width="1%" nowrap>
                <input type="checkbox" name="auditPresence" id="cb02"
                 onclick="this.form.auditEnabled[1].checked=true;"
                 <%= (auditPresence ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="cb02">
                <b>Audit Presence Packets</b>
                </label>
            </td>
        </tr>
        <tr valign="top">
            <td width="1%" nowrap>
                <input type="checkbox" name="auditIQ" id="cb03"
                 onclick="this.form.auditEnabled[1].checked=true;"
                 <%= (auditIQ ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="cb03">
                <b>Audit IQ Packets</b>
                </label>
            </td>
        </tr>
        <!--
        <% if (false){ %>
        <tr valign="top">
            <td width="1%" nowrap>
                <input type="checkbox" name="auditXPath" id="cb04"
                 onclick="this.form.auditEnabled[1].checked=true;"
                 <%= (auditXPath ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="cb04">
                <b>Audit Packets matching XPath Queries:</b>
                </label>
            </td>
        </tr>
        <tr valign="top">
            <td width="1%" nowrap>
                &nbsp;
            </td>
            <td width="99%">

                <table cellpadding="4" cellspacing="0" border="0" width="100%">
                <tr>
                    <td>
                        Enter new query:
                        <input type="text" size="30" maxlength="100" name="newXpathQuery"
                         onclick="this.form.auditEnabled[1].checked=true;this.form.auditXPath.checked=true;"
                         value="<%= ((newXpathQuery != null) ? newXpathQuery : "") %>">
                    </td>
                </tr>

                <%  Iterator queries = auditManager.getXPathFilters();
                    if (queries.hasNext()) {
                %>
                    <tr>
                        <td>
                            Current queries audited:
                        </td>
                    </tr>
                    <tr>
                        <td>

                            <div class="jive-table">
                            <table cellpadding="3" cellspacing="1" border="0" width="400">
                            <tr>
                                <th nowrap>XPath Query</th>
                                <th align="center">Delete</th>
                            </tr>

                            <%  while (queries.hasNext()) {
                                    String query = (String)queries.next();
                            %>

                            <tr>
                                <td class="jive-label" width="99%">
                                    <%= query %>
                                </td>
                                <td align="center" width="1%">
                                    <input type="checkbox" name="xpathQuery"
                                     value="<%= StringUtils.replace(query, "\"", "&quot;") %>">
                                </td>
                            </tr>

                            <%  } %>

                            </table>
                            </div>

                        </td>
                    </tr>

                <%  } %>

                </table>

            </td>
        </tr>
        <% } // xquery comment block %>
-->
                </table>
            </td>
        </tr>
        </table>
        </div>
    </td>
</tr>
</table>

<br>

<input type="submit" name="update" value="Save Settings">

</form>

<jsp:include page="bottom.jsp" flush="true" />
