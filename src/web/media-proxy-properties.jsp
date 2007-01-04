<%@ page import="org.jivesoftware.util.JiveGlobals" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.wildfire.XMPPServer" %>
<%@ page import="org.jivesoftware.wildfire.mediaproxy.MediaProxyService" %>
<%

    MediaProxyService mediaProxyService = XMPPServer.getInstance().getMediaProxyService();

    boolean save = request.getParameter("set") != null;
    boolean success = false;

    long keepAliveDelay = 0;
    int minPort = 10000;
    int maxPort = 20000;
    boolean enabled = false;

    if (save) {
        keepAliveDelay = ParamUtils.getLongParameter(request, "keepalivedelay", keepAliveDelay);
        if (keepAliveDelay > 50) {
            mediaProxyService.setKeepAliveDelay(keepAliveDelay);
            JiveGlobals
                    .setProperty("plugin.rtpbridge.keepalivedelay", String.valueOf(keepAliveDelay));
        }

        minPort = ParamUtils.getIntParameter(request, "minport", minPort);
        maxPort = ParamUtils.getIntParameter(request, "maxport", maxPort);
        enabled = ParamUtils.getBooleanParameter(request, "enabled", enabled);

        JiveGlobals.setProperty("plugin.rtpbridge.enabled", String.valueOf(enabled));

        mediaProxyService.setEnabled(enabled);

        if (minPort > 0 && maxPort > 0) {
            if (maxPort - minPort > 1000) {
                mediaProxyService.setMinPort(minPort);
                mediaProxyService.setMaxPort(maxPort);
                JiveGlobals.setProperty("plugin.rtpbridge.minport", String.valueOf(minPort));
                JiveGlobals.setProperty("plugin.rtpbridge.maxport", String.valueOf(maxPort));
            }
        }

        success = true;
    }

%>
<html>
<head>
    <title>Media Proxy Settings</title>
    <meta name="pageID" content="media-proxy-service"/>
</head>
<body>

<p>
    Use the form below to manage Media Proxy settings.<br>
</p>

<% if (success) { %>

<div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
        <tbody>
            <tr>
                <td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16"
                                           border="0"></td>
                <td class="jive-icon-label">Settings updated successfully.</td>
            </tr>
        </tbody>
    </table>
</div>
<br>

<% } %>

<form action="media-proxy-properties.jsp" method="post">
    <fieldset>
        <legend>Media Proxy Settings</legend>
        <div>

            <p>
                The settings will just take effects for new created agents.
            </p>

            <table cellpadding="3" cellspacing="0" border="0" width="100%">
                <tbody>
                    <tr>
                        <td align="left">Keep Alive Delay:&nbsp<input type="text" size="20"
                                                                      maxlength="100"
                                                                      name="keepalivedelay"
                                                                      value="<%=mediaProxyService.getKeepAliveDelay()%>"
                                                                      align="left">
                        </td>
                    </tr>
                    <tr>
                        <td align="left">Minimal Port Value:&nbsp<input type="text" size="20"
                                                                        maxlength="100"
                                                                        name="minport"
                                                                        value="<%=mediaProxyService.getMinPort()%>"
                                                                        align="left">
                        </td>
                    </tr>
                    <tr>
                        <td align="left">Maximum Port Value:&nbsp<input type="text" size="20"
                                                                        maxlength="100"
                                                                        name="maxport"
                                                                        value="<%=mediaProxyService.getMaxPort()%>"
                                                                        align="left">
                        </td>
                    </tr>
                    <tr>
                        <td align="left">Enabled:&nbsp<input type="checkbox"
                                                             name="enabled"
                        <%=mediaProxyService.isEnabled()?"checked":""%>
                                                             align="left">
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>
        <input type="submit" name="set" value="Change">

    </fieldset>
</form>

</body>
</html>