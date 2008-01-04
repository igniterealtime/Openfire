<%@ page import="java.util.Map" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="org.jivesoftware.util.JiveGlobals" %>
<%@ page import="org.jivesoftware.util.LocaleUtils" %>
<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<html>
<head>
<title><fmt:message key="sipark.settings.title"/></title>
<meta name="pageID" content="sipark-settings"/>
<link rel="stylesheet" type="text/css" href="style/style.css">
<style type="text/css">
    .small-label {
        font-size: 11px;
        font-weight: bold;
        font-family: verdana;
    }

    .small-text {
        font-size: 11px;
        font-family: verdana;
    }

    .stat {
        border: 1px;
        border-color: #ccc;
        border-style: dotted;
    }

    .conversation-body {
        color: black;
        font-size: 11px;
        font-family: verdana;
    }

    .conversation-label1 {
        color: blue;
        font-size: 11px;
        font-family: verdana;
    }

    .conversation-label2 {
        color: red;
        font-size: 11px;
        font-family: verdana;
    }

    .conversation-table {
        font-family: verdana;
        font-size: 12px;
    }

    .light-gray-border {
        border-color: #bbb;
        border-style: solid;
        border-width: 1px 1px 1px 1px;
    }

    .light-gray-border-bottom {
        border-color: #bbb;
        border-style: solid;
        border-width: 0px 0px 1px 0px;
    }

    .content {
        border-color: #bbb;
        border-style: solid;
        border-width: 0px 0px 1px 0px;
    }

    /* Default DOM Tooltip Style */
    div.domTT {
        border: 1px solid #bbb;
        background-color: #F9F5D5;
        font-family: arial;
        font-size: 9px;
        padding: 5px;
    }

    div.domTT .caption {
        font-family: serif;
        font-size: 12px;
        font-weight: bold;
        padding: 1px 2px;
        color: #FFFFFF;
    }

    div.domTT .contents {
        font-size: 12px;
        font-family: sans-serif;
        padding: 3px 2px;
    }

    .textfield {
        font-size: 11px;
        font-family: verdana;
        padding: 3px 2px;
        background: #efefef;
    }

    .keyword-field {
        font-size: 11px;
        font-family: verdana;
        padding: 3px 2px;
    }


</style>

</head>

<body>

<% // Get parameters
    boolean update = request.getParameter("update") != null;
    String sipServer = request.getParameter("sipServer");
    String voiceMail = request.getParameter("voiceMail");
    boolean stunEnabled = request.getParameter("stunEnabled") != null;
    String stunServer = request.getParameter("stunServer");
    String stunPort = request.getParameter("stunPort");

    if (request.getParameter("cancel") != null) {
        response.sendRedirect("sipark-user-summary.jsp");
        return;
    }

    // Update the session kick policy if requested
    Map errors = new HashMap();
    String errorMessage = "";
    if (update) {
        // Validate params
        if (sipServer == null || "".equals(sipServer)) {
            errors.put("sipServer", "");
            errorMessage = LocaleUtils.getLocalizedString("sipark.settings.valid.sipserver", "sip");
        }
        else if (voiceMail == null || "".equals(voiceMail)) {
            errors.put("voiceMail", "");
            errorMessage = LocaleUtils.getLocalizedString("sipark.settings.valid.voiceMail", "sip");
        }
        else if (stunEnabled && (stunServer == null || "".equals(stunServer))) {
            errors.put("stunServer", "");
            errorMessage = LocaleUtils.getLocalizedString("sipark.settings.valid.stunServer", "sip");
        }
        else if (stunEnabled && (stunPort == null || "".equals(stunPort))) {
            errors.put("stunPort", "");
            errorMessage = LocaleUtils.getLocalizedString("sipark.settings.valid.stunPort", "sip");
        }
        // If no errors, continue:
        if (errors.size() == 0) {
            JiveGlobals.setProperty("phone.sipServer", sipServer);
            JiveGlobals.setProperty("phone.voiceMail", voiceMail);
            if (stunEnabled) {
                JiveGlobals.setProperty("phone.stunEnabled", "true");
                JiveGlobals.setProperty("phone.stunServer", stunServer);
                JiveGlobals.setProperty("phone.stunPort", stunPort);
            } else {
                JiveGlobals.setProperty("phone.stunEnabled", "false");
                JiveGlobals.deleteProperty("phone.stunServer");
                JiveGlobals.deleteProperty("phone.stunPort");
            }
%>
<div class="success">
    <fmt:message key="sipark.settings.success"/>
</div><br>
<%
        }
    }
    else {
            sipServer = JiveGlobals.getProperty("phone.sipServer", "");
            voiceMail = JiveGlobals.getProperty("phone.voiceMail", "");
            stunEnabled = JiveGlobals.getBooleanProperty("phone.stunEnabled", false);
            stunServer = JiveGlobals.getProperty("phone.stunServer", "");
            stunPort = JiveGlobals.getProperty("phone.stunPort", "");
        }
%>

<% if (errors.size() > 0) { %>
<div class="error">
    <%= errorMessage%>
</div>
<br/>
<% } %>

<p>
    <fmt:message key="sipark.settings.description"/>
</p>

<form action="sipark-settings.jsp" method="post">
    <table class="settingsTable" cellpadding="3" cellspacing="0" border="0" width="90%">
        <thead>
            <tr>
                <th colspan="3"><fmt:message key="sipark.settings.table.title" /></th>
            </tr>
        </thead>
        <tbody>
            <tr>
                <td width="98%"><label class="jive-label"><fmt:message key="sipark.settings.sipServer"/>:</label><br>
                <fmt:message key="sipark.settings.sipServer.description"/></td>
                <td><input type="text" name="sipServer" size="20" maxlength="100" value="<%= sipServer == null ? "" : sipServer%>" /></td>
                <td></td>
            </tr>
            <tr>
                <td><label class="jive-label"><fmt:message key="sipark.settings.voiceMail"/>:</label><br>
                <fmt:message key="sipark.settings.voiceMail.description"/></td>
                <td><input type="text" name="voiceMail" size="20" maxlength="100" value="<%= voiceMail == null ? "" : voiceMail %>" /></td>
                <td></td>
            </tr>
            <tr>
                <td><label class="jive-label"><fmt:message key="sipark.settings.enable.stun"/>:</label><br>
                <fmt:message key="sipark.settings.enable.stun.description"/></td>
                <td><input type="checkbox" name="stunEnabled" <%= stunEnabled ? "checked" : ""%> /></td>
                <td></td>
            </tr>
            <tr>
                <td><label class="jive-label"><fmt:message key="sipark.settings.stunServer"/>:</label><br>
                <fmt:message key="sipark.settings.stunServer.description"/></td>
                <td><input type="text" name="stunServer" size="20" maxlength="100" value="<%= stunServer == null ? "" : stunServer %>" /></td>
                <td></td>
            </tr>

            <tr>
                <td><label class="jive-label"><fmt:message key="sipark.settings.stunServer.port"/>:</label><br>
                <fmt:message key="sipark.settings.stunServer.port.description"/></td>
                <td><input type="text" name="stunPort" size="10" maxlength="10" value="<%= stunPort == null ? "" : stunPort %>" /></td>
                <td></td>
            </tr>
        </tbody>
    </table>


    <input type="submit" name="update" value="<fmt:message key="sipark.settings.update.settings" />">
    <input type="submit" name="cancel" value="<fmt:message key="sipark.settings.cancel" />">
</form>

</body>
</html>
