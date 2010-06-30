<%@ page import="org.jivesoftware.openfire.sip.sipaccount.SipAccount" %>
<%@ page import="org.jivesoftware.openfire.sip.sipaccount.SipAccountDAO" %>
<%@ page import="org.jivesoftware.util.JiveGlobals" %>
<%@ page import="org.jivesoftware.util.LocaleUtils" %>
<%@ page import="org.jivesoftware.util.Log" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="java.net.InetAddress" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.sql.SQLException" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.jivesoftware.openfire.sip.tester.stack.SIPTest" %>
<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%
    boolean save = request.getParameter("save") != null;
    boolean test = request.getParameter("test") != null;
    SIPTest.Result testResult = null;
    String node = request.getParameter("node");
    String username = ParamUtils.getParameter(request, "sipusername");
    String authusername = ParamUtils.getParameter(request, "authusername");
    String displayname = ParamUtils.getParameter(request, "displayname");
    String password = ParamUtils.getParameter(request, "sippassword");
    String server = ParamUtils.getParameter(request, "server");
    boolean enabled = ParamUtils.getBooleanParameter(request, "enabled");
    String voicemail = ParamUtils.getParameter(request, "voicemail");
    String outboundproxy = ParamUtils.getParameter(request, "outboundproxy");
    boolean promptCredentials = ParamUtils.getBooleanParameter(request,"promptCredentials",false);

    // Handle a cancel
    if (request.getParameter("cancel") != null) {
        response.sendRedirect("sipark-user-summary.jsp?username=" + URLEncoder.encode(node, "UTF-8"));
        return;
    }

    SipAccount sipAccount = null;
    Map<String, String> errors = new HashMap<String, String>();
    String errorMessage = null;

    if (save || test) {
        // Validate params
        if (node == null || "".equals(node) || node.contains("@")) {
            errors.put("node", "");
            errorMessage = LocaleUtils.getLocalizedString("sipark.user.create.valid.xmpp-node", "sip");
        } else if ((username == null || "".equals(username)) && !promptCredentials) {
            errors.put("username", "");
            errorMessage = LocaleUtils.getLocalizedString("sipark.user.create.valid.sip-username", "sip");
        } else if ((authusername == null || "".equals(authusername)) && !promptCredentials) {
            errors.put("authusername", "");
            errorMessage = LocaleUtils.getLocalizedString("sipark.user.create.valid.authusername", "sip");
        } else if (displayname == null || "".equals(displayname)) {
            errors.put("displayname", "");
            errorMessage = LocaleUtils.getLocalizedString("sipark.user.create.valid.displayname", "sip");
        } else if ((password == null || "".equals(password)) && !promptCredentials) {
            errors.put("password", "");
            errorMessage = LocaleUtils.getLocalizedString("sipark.user.create.valid.password", "sip");
        } else if (server == null || "".equals(server)) {
            errors.put("server", "");
            errorMessage = LocaleUtils.getLocalizedString("sipark.user.create.valid.server", "sip");
        } else if (voicemail == null || "".equals(voicemail)) {
            errors.put("voicemail", "");
            errorMessage = LocaleUtils.getLocalizedString("sipark.user.create.valid.voicemail", "sip");
        } else if(outboundproxy == null){
            outboundproxy = "";
        }

        if (errors.isEmpty()) {
            sipAccount = new SipAccount(node);
            sipAccount.setSipUsername(username);
            sipAccount.setAuthUsername(authusername);
            sipAccount.setDisplayName(displayname);
            sipAccount.setPassword(password);
            sipAccount.setServer(server);
            sipAccount.setEnabled(enabled);
            sipAccount.setVoiceMailNumber(voicemail);
            sipAccount.setOutboundproxy(outboundproxy);
            sipAccount.setPromptCredentials(promptCredentials);

            if (test) {
                try {

                    InetAddress localAddress = InetAddress.getByName(JiveGlobals.getProperty("xmpp.domain",
                            JiveGlobals.getXMLProperty("network.interface", "localhost")));

                    SIPTest sipTest = new SIPTest(localAddress, sipAccount);

                    if (sipTest != null) {
                        sipTest.test(5000, 2);
                        testResult = sipTest.getResult();
                    } else
                        testResult = SIPTest.Result.NetworkError;

                } catch (Exception e) {
                    testResult = SIPTest.Result.NetworkError;
                    Log.error(e);
                }

                if (node != null && !"".equals(node)) {
                    sipAccount = new SipAccount(node);
                    SipAccount sp = SipAccountDAO.getAccountByUser(sipAccount.getUsername());

                    if (sp != null) {
                        sipAccount = sp;
                    }
                }
            } else {

                try {

                    if (SipAccountDAO.getAccountByUser(sipAccount.getUsername()) != null) {
                        SipAccountDAO.update(sipAccount);
                    } else {
                        SipAccountDAO.insert(sipAccount);
                    }

                    response.sendRedirect("sipark-user-summary.jsp?username=" + URLEncoder.encode(node, "UTF-8"));
                    return;

                } catch (SQLException e) {
                    errors.put("saveSettings", e.getMessage());
                    Log.error(e);
                }
            }
        }

    } else {
        if (node != null && !"".equals(node)) {
            sipAccount = new SipAccount(node);
            SipAccount sp = SipAccountDAO.getAccountByUser(sipAccount.getUsername());

            if (sp != null) {
                sipAccount = sp;
            }
        }
        if (sipAccount == null) {
            enabled = true;
            server = JiveGlobals.getProperty("phone.sipServer", "");
            voicemail = JiveGlobals.getProperty("phone.voiceMail", "");
        } else {
            username = sipAccount.getSipUsername();
            authusername = sipAccount.getAuthUsername();
            displayname = sipAccount.getDisplayName();
            password = sipAccount.getPassword();
            server = sipAccount.getServer();
            enabled = sipAccount.isEnabled();
            voicemail = sipAccount.getVoiceMailNumber();
            outboundproxy = sipAccount.getOutboundproxy();
            promptCredentials = sipAccount.isPromptCredentials();
        }
    }
%>


<html>
<head>
    <title><% if (sipAccount == null) { %>
        <fmt:message key="sipark.user.create.title"/>
        <% } else { %>
        <fmt:message key="sipark.user.update.title"/>
        <% } %></title>
    <link rel="stylesheet" type="text/css" href="/style/global.css">
    <meta name="pageID" content="sipark-user-summary"/>
    <script src="/js/prototype.js" type="text/javascript"></script>
    <script src="/js/scriptaculous.js" type="text/javascript"></script>
    <style type="text/css">
        .div-border {
            border: 1px;
            border-color: #ccc;
            border-style: dotted;
        }
    </style>
</head>

<body>

<% if (errors.size() > 0) { %>
<div class="error">
    <%= errorMessage %>
</div>
<br/>
<% } %>


<%
    if (testResult != null) {
        if (SIPTest.Result.Successfully.equals(testResult)) { %>
<div class="success">
    SIP Account Successfully Tested.
</div>
<br>
<% } else { %>
<div class="error">
    SIP Account problem: <%=testResult.toString()%>
    <i>
        <%
            if (testResult.equals(SIPTest.Result.Forbidden)) {
        %>
        <fmt:message key="sip.test.error.forbidden"/>
        <%
        } else if (testResult.equals(SIPTest.Result.NetworkError)) {
        %>
        <fmt:message key="sip.test.error.networkerror"/>
        <%
        } else if (testResult.equals(SIPTest.Result.Timeout)) {
        %>
        <fmt:message key="sip.test.error.timeout"/>
        <%

        } else if (testResult.equals(SIPTest.Result.WrongUser)) {
        %>
        <fmt:message key="sip.test.error.wronguser"/>
        <%

        } else if (testResult.equals(SIPTest.Result.WrongAuthUser)) {
        %>
        <fmt:message key="sip.test.error.wrongauthuser"/>
        <%

        } else if (testResult.equals(SIPTest.Result.WrongPass)) {
        %>
        <fmt:message key="sip.test.error.wronpass"/>
        <%
            }
        %>
    </i>

</div>
<br>
<% }
} %>

<p>
    <fmt:message key="sipark.user.create.description"/>
</p>


<form id="urlForm" name="urlForm" action="create-sipark-mapping.jsp" method="post">
<table class="div-border" cellpadding="3">

    <% if (sipAccount == null) { %>
    <tr>
        <td align="left" width="150">
            <fmt:message key="sip.account.xmpp.username"/>
            :&nbsp
        </td>
        <td><input type="text" size="20" maxlength="100" name="node" value="<%= (node != null ? node : "") %>">
        </td>
    </tr>
    <% } else {%>
    <input type="hidden" name="node" value="<%= node %>">
    <% } %>

    <tr>
        <td align="left" width="150">
            <fmt:message key="sip.account.sip.username"/>
            :&nbsp
        </td>
        <td><input type="text" size="20" maxlength="100" name="sipusername"
                   value="<%= (username != null ? username : "") %>">
        </td>
    </tr>

    <tr>
        <td align="left" width="150">
            <fmt:message key="sip.account.authusername"/>
            :&nbsp
        </td>
        <td><input type="text" size="20" maxlength="100" name="authusername"
                   value="<%= (authusername != null ? authusername : "") %>">
        </td>
    </tr>

    <tr>
        <td align="left" width="150">
            <fmt:message key="sip.account.displayname"/>
            :&nbsp
        </td>
        <td><input type="text" size="20" maxlength="100" name="displayname"
                   value="<%= (displayname != null ? displayname : "") %>">
        </td>
    </tr>

    <tr>
        <td align="left" width="150">
            <fmt:message key="sip.account.password"/>
            :&nbsp
        </td>
        <td><input type="password" size="20" maxlength="100" name="sippassword"
                   value="<%= (password != null ? password : "") %>">
        </td>
    </tr>

    <tr>
        <td align="left" width="150">
            <fmt:message key="sip.account.server"/>
            :&nbsp
        </td>
        <td><input type="text" size="20" maxlength="100" name="server"
                   value="<%= (server != null ? server : "") %>">
        </td>
    </tr>

    <tr>
        <td align="left" width="150">
            <fmt:message key="sip.account.outboundproxy"/>
            :&nbsp
        </td>
        <td><input type="text" size="20" maxlength="100" name="outboundproxy"
                   value="<%= (outboundproxy != null ? outboundproxy : "") %>">
        </td>
    </tr>

    <tr>
        <td align="left" width="150">
            <fmt:message key="sip.account.voicemail"/>
            :&nbsp
        </td>
        <td><input type="text" size="20" maxlength="100" name="voicemail"
                   value="<%= (voicemail != null ? voicemail : "") %>">
        </td>
    </tr>

    <% if (sipAccount != null) { %>
    <tr>
        <td align="left" width="150">
            <fmt:message key="sip.account.promptCredentials"/>
            :&nbsp
        </td>
        <td><input type="checkbox" size="20" maxlength="100" name="promptCredentials" <%= promptCredentials ? "checked" : "" %>">
        </td>
    </tr>
    
    <tr>
        <td align="left" width="150">
            <fmt:message key="sip.account.enabled"/>
            :&nbsp
        </td>
        <td><input type="checkbox" size="20" maxlength="100" name="enabled" <%= enabled ? "checked" : "" %>">
        </td>
    </tr>
    <% } else {%>
    <input type="hidden" name="enabled" value="<%= enabled %>">
    <% } %>

    <tr>
        <td></td>
        <td><input type="submit" name="save"
                   value="<%= sipAccount != null ? LocaleUtils.getLocalizedString("sipark.user.create.save.changes", "sip") : LocaleUtils.getLocalizedString("create", "sip")  %>"/>
            &nbsp;<input type="submit" name="cancel" value="<fmt:message key="cancel" />">
            <% if (sipAccount != null || test) { %>
            &nbsp;<input type="submit" name="test" value="<fmt:message key="test" />">
            <%}%>
        </td>
    </tr>

</table>

</form>

</body>
</html>
