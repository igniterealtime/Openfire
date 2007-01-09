<%--
  -	$Revision: 5321 $
  -	$Date: 2006-09-11 01:22:53 -0300 (seg, 11 set 2006) $
  -
  - Copyright (C) 2004-2005 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>
<%@ page import="org.jivesoftware.util.JiveGlobals" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.wildfire.XMPPServer" %>
<%@ page import="org.jivesoftware.wildfire.stun.STUNService" %>
<%@ page import="java.net.InetAddress" %>
<%@ page import="java.util.List" %>
<%

    STUNService stunService = XMPPServer.getInstance().getSTUNService();

    boolean save = request.getParameter("set") != null;
    boolean success = false;

    boolean enabled = false;

    String primaryAddress;
    String secondaryAddress;
    int primaryPort = 3478;
    int secondaryPort = 3576;

    if (save) {
        primaryPort = ParamUtils.getIntParameter(request, "primaryPort", primaryPort);
        JiveGlobals.setProperty("stun.port.primary", String.valueOf(primaryPort));

        secondaryPort = ParamUtils.getIntParameter(request, "secondaryPort", secondaryPort);
        JiveGlobals.setProperty("stun.port.secondary", String.valueOf(secondaryPort));

        primaryAddress = ParamUtils.getParameter(request, "primaryAddress", true);
        JiveGlobals.setProperty("stun.address.primary", primaryAddress);

        secondaryAddress = ParamUtils.getParameter(request, "secondaryAddress", true);
        JiveGlobals.setProperty("stun.address.secondary", secondaryAddress);

        enabled = ParamUtils.getBooleanParameter(request, "enabled", enabled);
        JiveGlobals.setProperty("stun.enabled", String.valueOf(enabled));

        stunService.stop();
        stunService.initialize(XMPPServer.getInstance());
        stunService.setEnabled(enabled);

        success = stunService.isEnabled() == enabled;
    }

%>
<html>
<head>
    <title>STUN Server Settings</title>
    <meta name="pageID" content="stun-settings"/>
</head>
<body>

<p>
    Use the form below to manage STUN Server settings.<br>
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

<% } else { %>

<div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
        <tbody>
            <tr>
                <td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16"
                                           border="0"></td>
                <td class="jive-icon-label">Settings not updated successfully.<br>Server not started.</td>
            </tr>
        </tbody>
    </table>
</div>
<br>

<% } %>


<form action="stun-settings.jsp" method="post">
    <fieldset>
        <legend>STUN Server Settings</legend>
        <div>

            <p>
                The settings will just take effects after savings settings.
            </p>

            <table cellpadding="3" cellspacing="0" border="0" width="100%">
                <tbody>
                    <tr>
                        <td align="left">Primary Address:&nbsp<select size="1"
                                                                      maxlength="100"
                                                                      name="primaryAddress"
                                                                      align="left">
                            <option value="<%=stunService.getPrimaryAddress()%>"><%=stunService.getPrimaryAddress()%>
                                <%


                              List<InetAddress> addresses = stunService.getAddresses();

                              for(InetAddress iaddress:addresses){


                                %>
                            <option value="<%=iaddress.getHostAddress()%>"><%=iaddress.getHostAddress()%>
                            </option>
                            <%

                            }

                                String sname = JiveGlobals.getProperty("xmpp.domain", JiveGlobals.getProperty("network.interface", "localhost"));


                            %>
                            <option value="<%=sname%>"><%=sname%>
                            </option>
                        </td>
                    </tr>
                    <tr>
                        <td align="left"> Secondary
                            Address:&nbsp<select size="1"
                                                 maxlength="100"
                                                 name="secondaryAddress"
                                                 align="left">
                            <option value="<%=stunService.getSecondaryAddress()%>"><%=stunService.getSecondaryAddress()%>
                            </option>
                            <%

                                for (InetAddress iaddress : addresses) {

                            %>
                            <option value="<%=iaddress.getHostAddress()%>"><%=iaddress.getHostAddress()%>
                            </option>
                            <% } %>
                            <option value="127.0.0.1">127.0.0.1</option>
                        </select>
                        </td>
                    </tr>
                    <tr>
                        <td align="left">Primary Port Value:&nbsp<input type="text" size="20"
                                                                        maxlength="100"
                                                                        name="primaryPort"
                                                                        value="<%=stunService.getPrimaryPort()%>"
                                                                        align="left">
                        </td>
                    </tr>
                    <tr>
                        <td align="left">Secondary Port Value:&nbsp<input type="text" size="20"
                                                                          maxlength="100"
                                                                          name="secondaryPort"
                                                                          value="<%=stunService.getSecondaryPort()%>"
                                                                          align="left">
                        </td>
                    </tr>
                    <tr>
                        <td align="left">Enabled:&nbsp<input type="checkbox"
                                                             name="enabled"
                        <%=stunService.isEnabled()?"checked":""%>
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