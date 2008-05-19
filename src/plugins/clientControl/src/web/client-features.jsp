<%@ page import="org.jivesoftware.openfire.plugin.spark.manager.FileTransferFilterManager" %>
<%@ page import="org.jivesoftware.util.JiveGlobals"%>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.plugin.ClientControlPlugin" %>
<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%
    String broadcastEnabledString = JiveGlobals.getProperty("broadcast.enabled", "true");
    String fileTransferString = JiveGlobals.getProperty("transfer.enabled", "true");

    String vcardEnabledString = JiveGlobals.getProperty("vcard.enabled", "true");


    boolean submit = request.getParameter("submit") != null;
    if (submit) {
        broadcastEnabledString = request.getParameter("broadcastEnabled");
        fileTransferString = request.getParameter("transferEnabled");
        vcardEnabledString = request.getParameter("vcardEnabled");

        JiveGlobals.setProperty("broadcast.enabled", broadcastEnabledString);
        JiveGlobals.setProperty("transfer.enabled", fileTransferString);

        JiveGlobals.setProperty("vcard.enabled", vcardEnabledString);

    }
    boolean broadcastEnabled = Boolean.parseBoolean(broadcastEnabledString);
    boolean transferEnabled = Boolean.parseBoolean(fileTransferString);
    boolean vcardEnabled = Boolean.parseBoolean(vcardEnabledString);

    // Enable File Transfer in the system.
    ClientControlPlugin plugin = (ClientControlPlugin) XMPPServer.getInstance()
            .getPluginManager().getPlugin("clientcontrol");
    FileTransferFilterManager manager = plugin.getFileTransferFilterManager();
    manager.enableFileTransfer(transferEnabled);
%>

<html>
<head>
    <title><fmt:message key="client.features.title"/></title>
    <meta name="pageID" content="client-features"/>
    <style type="text/css">
        @import "style/style.css";
    </style>
</head>

<body>


<% if (submit) { %>

<div class="success">
  <fmt:message key="client.features.update.features"/>
</div>
<br>
<% }%>
<p>
    <fmt:message key="client.features.info"/>
</p>

<form name="f" action="client-features.jsp" method="post">
    <table class="jive-table" cellspacing="0" width="600">
        <th><fmt:message key="client.feature"/></th>
        <th><fmt:message key="client.features.enabled"/></th>
        <th><fmt:message key="client.features.disabled"/></th>
        <tr>
            <td><b><fmt:message key="client.features.broadcasting" /></b> - <fmt:message key="client.features.spark.only" /><br/><span class="jive-description">
              <fmt:message key="client.features.broadcasting.description" />
           </span></td>
            <td width="1%" nowrap>
                <input type="radio" name="broadcastEnabled" value="true" <%= broadcastEnabled ? "checked" : "" %> />
            </td>
            <td width="1%" nowrap>
                <input type="radio" name="broadcastEnabled" value="false" <%= !broadcastEnabled ? "checked" : "" %> />
            </td>
        </tr>
        <tr>
            <td><b><fmt:message key="client.features.filetransfer" /></b><br/><span class="jive-description">
               <fmt:message key="client.features.filetransfer.description" />
           </span></td>
            <td width="1%" nowrap>
                <input type="radio" name="transferEnabled" value="true" <%= transferEnabled ? "checked" : "" %> />
            </td>
            <td width="1%" nowrap>
                <input type="radio" name="transferEnabled" value="false" <%= !transferEnabled ? "checked" : "" %> />
            </td>
        </tr>
        <tr>
            <td><b><fmt:message key="client.features.vcard" /></b> - <fmt:message key="client.features.spark.only" /><br/><span class="jive-description">
                     <fmt:message key="client.features.vcard.description" />
                  </span></td>
            <td width="1%" nowrap>
                <input type="radio" name="vcardEnabled" value="true" <%= vcardEnabled ? "checked" : "" %> />
            </td>
            <td width="1%" nowrap>
                <input type="radio" name="vcardEnabled" value="false" <%= !vcardEnabled ? "checked" : "" %> />
            </td>
        </tr>

    </table>

    <br/>
    <input type="submit" name="submit" value="<fmt:message key="client.features.save.settings" />" />
</form>
</body>
</html>