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
                   java.util.*,
                 java.io.File,
                 org.xmpp.component.ComponentManagerFactory,
                 org.xmpp.packet.JID,
                 java.util.LinkedList,
                 org.jivesoftware.messenger.user.UserNotFoundException"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = LocaleUtils.getLocalizedString("audit.policy.title");
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(LocaleUtils.getLocalizedString("global.main"), "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "audit-policy.jsp"));
    pageinfo.setPageID("server-audit-policy");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />
<script language="JavaScript" type="text/javascript">
function openWin(el) {
    var win = window.open('user-browser.jsp?formName=f&elName=ignore','newWin','width=500,height=550,menubar=yes,location=no,personalbar=no,scrollbars=yes,resize=yes');
}
</script>

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
    String logTimeout = ParamUtils.getParameter(request,"logTimeout");
    String logDir = ParamUtils.getParameter(request,"logDir");
    String ignore = ParamUtils.getParameter(request,"ignore");


    // Get an audit manager:
    AuditManager auditManager = admin.getXMPPServer().getAuditManager();

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
        try {
            auditManager.setLogTimeout(Integer.parseInt(logTimeout) * 1000);
        } catch (Exception e){
            errors.put("logTimeout","logTimeout");
        }
        if (logDir == null || logDir.trim().length() == 0) {
            errors.put("logDir","logDir");
        }
        else {
            if (new File(logDir).isDirectory()) {
                auditManager.setLogDir(logDir);
            }
            else {
                errors.put("logDir","logDir");
            }
        }
        if (errors.size() == 0){
            if (ignore == null){
                // remove all ignored users
                auditManager.setIgnoreList(new ArrayList<String>());
            }
            else {
                // Set the new ignore list
                Collection<String> newIgnoreList = new HashSet<String>(ignore.length());
                StringTokenizer tokenizer = new StringTokenizer(ignore, ", \t\n\r\f");
                while (tokenizer.hasMoreTokens()) {
                    String tok = tokenizer.nextToken();
                    String username = tok;
                    if (tok.contains("@")) {
                        if (tok.contains("@" + admin.getServerInfo().getName())) {
                           username = new JID(tok).getNode();
                        }
                        else {
                            // Skip this JID since it belongs to a remote server
                            continue;
                        }
                    }
                    try {
                        admin.getUserManager().getUser(username);
                        newIgnoreList.add(username);
                    }
                    catch (UserNotFoundException e){
                    }
                }
                auditManager.setIgnoreList(newIgnoreList);
            }
        // All done, redirect
        %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        <fmt:message key="audit.policy.settings.saved_successfully" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

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
        logTimeout = Integer.toString(auditManager.getLogTimeout() / 1000);
        logDir = auditManager.getLogDir();
        StringBuilder ignoreList = new StringBuilder();
        for (String username : auditManager.getIgnoreList()) {
            if (ignoreList.length() == 0) {
                ignoreList.append(username);
            }
            else {
                ignoreList.append(", ").append(username);
            }
        }
        ignore = ignoreList.toString();
    }
%>

<p>
<fmt:message key="title" />
<fmt:message key="audit.policy.title_info" />
</p>

<form action="audit-policy.jsp" name="f">

<fieldset>
    <legend><fmt:message key="audit.policy.policytitle" /></legend>
    <div>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr valign="middle">
            <td width="1%" nowrap>
                <input type="radio" name="auditEnabled" value="false" id="rb01"
                 <%= (!auditEnabled ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb01">
                <b><fmt:message key="audit.policy.label_disable_auditing" /></b> <fmt:message key="audit.policy.label_disable_auditing_info" />
                </label>
            </td>
        </tr>
        <tr valign="middle">
            <td width="1%" nowrap>
                <input type="radio" name="auditEnabled" value="true" id="rb02"
                 <%= (auditEnabled ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb02">
                <b><fmt:message key="audit.policy.label_enable_auditing" /></b> <fmt:message key="audit.policy.label_enable_auditing_info" />
                </label>
            </td>
        </tr>
        <tr valign="top">
            <td width="1%" nowrap>
                &nbsp;
            </td>
            <td width="99%">
                <table cellpadding="3" cellspacing="0" border="0" width="100%">
                <tr valign="top">
                    <td width="1%" nowrap class="c1">
                        <fmt:message key="audit.policy.log_directory" />
                    </td>
                    <td width="99%">
                        <input type="text" size="30" maxlength="150" name="logDir"
                         value="<%= ((logDir != null) ? logDir : "") %>">

                    <%  if (errors.get("logDir") != null) { %>

                        <span class="jive-error-text">
                        <fmt:message key="audit.policy.valid_log_directory" />
                        </span>

                    <%  } %>

                    </td>
                </tr>
                <tr valign="top">
                    <td width="1%" nowrap class="c1">
                        <fmt:message key="audit.policy.maxfile_size" />
                    </td>
                    <td width="99%">
                        <input type="text" size="15" maxlength="50" name="maxSize"
                         value="<%= ((maxSize != null) ? maxSize : "") %>">

                    <%  if (errors.get("maxSize") != null) { %>

                        <span class="jive-error-text">
                        <fmt:message key="audit.policy.validnumber" />
                        </span>

                    <%  } %>

                    </td>
                </tr>
                <tr valign="top">
                    <td width="1%" nowrap class="c1">
                        <fmt:message key="audit.policy.maxfile_number" />
                    </td>
                    <td width="99%">
                        <input type="text" size="15" maxlength="50" name="maxCount"
                         value="<%= ((maxCount != null) ? maxCount : "") %>">

                        <%  if (errors.get("maxCount") != null) { %>

                            <span class="jive-error-text">
                            <fmt:message key="audit.policy.validnumber" />
                            </span>

                        <%  } %>

                    </td>
                </tr>
                <tr valign="top">
                    <td width="1%" nowrap class="c1">
                        <fmt:message key="audit.policy.flush_interval" />
                    </td>
                    <td width="99%">
                        <input type="text" size="15" maxlength="50" name="logTimeout"
                         value="<%= ((logTimeout != null) ? logTimeout : "") %>">

                    <%  if (errors.get("logTimeout") != null) { %>

                        <span class="jive-error-text">
                        <fmt:message key="audit.policy.validnumber" />
                        </span>

                    <%  } %>

                    </td>
                </tr>
                <tr valign="top">
                    <td width="1%" nowrap class="c1">
                        <fmt:message key="audit.policy.packet_audit" />
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
                                <b><fmt:message key="audit.policy.label_audit_messenge_packets" /></b>
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
                                <b><fmt:message key="audit.policy.label_audit_presence_packets" /></b>
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
                                <b><fmt:message key="audit.policy.label_audit_iq_packets" /></b>
                                </label>
                            </td>
                        </tr>
                        </table>
                    </td>
                </tr>
                <tr valign="top">
                    <td width="1%" nowrap class="c1">
                        <fmt:message key="audit.policy.ignore" />
                    </td>
                    <td width="99%">
                        <table>
                            <td>
                            <textarea name="ignore" cols="40" rows="3" wrap="virtual"><%= ((ignore != null) ? ignore : "") %></textarea>
                            <%  if (errors.get("ignore") != null) { %>

                                <span class="jive-error-text">
                                <fmt:message key="audit.policy.validignore" />
                                </span>

                            <%  } %>
                            </td>
                             <td nowrap valign="top">
                                <a href="#" onclick="openWin(document.f.ignore);return false;"
                                 title="<fmt:message key="user.browser.browse_users_desc" />"
                                 ><img src="images/user.gif" border="0"/> <fmt:message key="user.browser.browse_users" /></a>
                            </td>
                        </table>
                    </td>
                </tr>
                </table>
            </td>
        </tr>
    </tbody>
    </table>
    <table border="0">
        <tr valign="top">
            <td width="1%" nowrap class="c1">
                <fmt:message key="audit.policy.queued_packets" />
            </td>
            <td width="99%">
                 <%= auditManager.getAuditor().getQueuedPacketsNumber() %>
            </td>
        </tr>
    </table>
    </div>
</fieldset>

<br><br>

<input type="submit" name="update" value="<fmt:message key="global.save_settings" />">

</form>

<jsp:include page="bottom.jsp" flush="true" />
