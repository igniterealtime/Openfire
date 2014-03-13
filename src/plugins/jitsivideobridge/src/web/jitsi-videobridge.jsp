<%--
 - Jitsi VideoBridge, OpenSource video conferencing.
 -
 - Distributable under LGPL license.
 - See terms of license at gnu.org.
--%>
<%@ page import="org.jitsi.videobridge.openfire.*" %>
<%@ page import="org.jivesoftware.openfire.*" %>
<%@ page import="org.jivesoftware.util.*" %>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<%

    boolean update = request.getParameter("update") != null;
    String errorMessage = null;

    // Get handle on the plugin
    PluginImpl plugin = (PluginImpl) XMPPServer.getInstance()
        .getPluginManager().getPlugin("jitsivideobridge");

    if (update)
    {
        String minPort = request.getParameter("minport");
        if (minPort != null) {
            minPort = minPort.trim();
            try
            {
                int port = Integer.valueOf(minPort);

                if(port >= 1 && port <= 65535)
                    JiveGlobals.setProperty(
                        PluginImpl.MIN_PORT_NUMBER_PROPERTY_NAME, minPort);
                else
                    throw new NumberFormatException("out of range port");

            }
            catch (Exception e)
            {
                errorMessage = LocaleUtils.getLocalizedString(
                    "config.page.configuration.error.minport",
                    "jitsivideobridge");
            }
        }
        String maxPort = request.getParameter("maxport");
        if (maxPort != null) {
            maxPort = maxPort.trim();
            try
            {
                int port = Integer.valueOf(maxPort);

                if(port >= 1 && port <= 65535)
                    JiveGlobals.setProperty(
                        PluginImpl.MAX_PORT_NUMBER_PROPERTY_NAME, maxPort);
                else
                    throw new NumberFormatException("out of range port");
            }
            catch (Exception e)
            {
                errorMessage = LocaleUtils.getLocalizedString(
                    "config.page.configuration.error.maxport",
                    "jitsivideobridge");
            }
        }
        
	String username = request.getParameter("username"); 
        JiveGlobals.setProperty(PluginImpl.USERNAME_PROPERTY_NAME, username);	
        
	String password = request.getParameter("password"); 	
        JiveGlobals.setProperty(PluginImpl.PASSWORD_PROPERTY_NAME, password);	
        
	String enabled = request.getParameter("enabled"); 	
        JiveGlobals.setProperty(PluginImpl.RECORD_PROPERTY_NAME, enabled);    
        
	String authusername = request.getParameter("authusername"); 
        JiveGlobals.setProperty("voicebridge.default.proxy.sipauthuser", authusername);	
        
	String sippassword = request.getParameter("sippassword"); 	
        JiveGlobals.setProperty("voicebridge.default.proxy.sippassword", sippassword);	
        
	String server = request.getParameter("server"); 	
        JiveGlobals.setProperty("voicebridge.default.proxy.sipserver", server); 
        
	String outboundproxy = request.getParameter("outboundproxy"); 	
        JiveGlobals.setProperty("voicebridge.default.proxy.outboundproxy", outboundproxy);        
    }

%>
<html>
<head>
   <title><fmt:message key="config.page.title" /></title>

   <meta name="pageID" content="jitsi-videobridge-settings"/>
</head>
<body>
<% if (errorMessage != null) { %>
<div class="error">
    <%= errorMessage%>
</div>
<br/>
<% } %>

<div class="jive-table">
    <form action="jitsi-videobridge.jsp" method="post">
        <table class="jive-table" cellpadding="0" cellspacing="0" border="0" width="50%">
            <thead>
            <tr>
                <th colspan="2"><fmt:message key="config.page.configuration.media.title"/></th>
            </tr>
            </thead>
            <tbody>
            <tr>
                <td><fmt:message key="config.page.configuration.min.port"/><br>
                </td>
                <td align="left">
                    <input name="minport" type="text" maxlength="5" size="5"
                           value="<%=plugin.getMinPort()%>"/>
                </td>
            </tr>
            <tr>
                <td><fmt:message key="config.page.configuration.max.port"/><br>
                </td>
                <td align="left">
                    <input name="maxport" type="text" maxlength="5" size="5"
                           value="<%=plugin.getMaxPort()%>"/>
                </td>
            </tr>
            </tbody>
        </table> 
        <p/>        
        <table class="jive-table" cellpadding="0" cellspacing="0" border="0" width="50%">
            <thead>
            <tr>
                <th colspan="2"><fmt:message key="config.page.configuration.security.title"/></th>
            </tr>
            </thead>
            <tbody>            
            <tr>
                <td><fmt:message key="config.page.configuration.username"/><br>
                </td>
                <td align="left">
                    <input name="username" type="text" maxlength="16" size="16"
                           value="<%=JiveGlobals.getProperty(PluginImpl.USERNAME_PROPERTY_NAME, "jitsi")%>"/>
                </td>
            </tr>     
            <tr>
                <td><fmt:message key="config.page.configuration.password"/><br>
                </td>
                <td align="left">
                    <input name="password" type="password" maxlength="16" size="16"
                           value="<%=JiveGlobals.getProperty(PluginImpl.PASSWORD_PROPERTY_NAME, "jitsi")%>"/>
                </td>
            </tr> 
            </tbody>
        </table> 
        <p/>        
        <table class="jive-table" cellpadding="0" cellspacing="0" border="0" width="50%">
            <thead> 
            <tr>
                <th colspan="2"><fmt:message key="config.page.configuration.recording.title"/></th>
            </tr>
            </thead>
            <tbody>             
	    <tr>
		    <td  nowrap colspan="2">
			<input type="radio" value="false" name="enabled" <%= ("false".equals(JiveGlobals.getProperty(PluginImpl.RECORD_PROPERTY_NAME, "false")) ? "checked" : "") %>>
			<b><fmt:message key="config.page.configuration.record.disabled" /></b> - <fmt:message key="config.page.configuration.record.disabled_description" />
		    </td>
	    </tr>   
	    <tr>
		    <td  nowrap colspan="2">
			<input type="radio" value="true" name="enabled" <%= ("true".equals(JiveGlobals.getProperty(PluginImpl.RECORD_PROPERTY_NAME, "false")) ? "checked" : "") %>>
			<b><fmt:message key="config.page.configuration.record.enabled" /></b> - <fmt:message key="config.page.configuration.record.enabled_description" />
		    </td>
	    </tr> 	    
            </tbody>
        </table>
        <p/>        
        <table class="jive-table" cellpadding="0" cellspacing="0" border="0" width="50%">
            <thead> 
            <tr>
                <th><fmt:message key="config.page.configuration.telephone.title"/></th>
                <th><%= plugin.sipRegisterStatus %></th>
            </tr>
            </thead>
            <tbody> 
	    <tr>
		<td align="left" width="150">
		    <fmt:message key="config.page.configuration.authusername"/>
		</td>
		<td><input type="text" size="20" maxlength="100" name="authusername"
			   value="<%= JiveGlobals.getProperty("voicebridge.default.proxy.sipauthuser", "") %>">
		</td>
	    </tr>

	    <tr>
		<td align="left" width="150">
		    <fmt:message key="config.page.configuration.sippassword"/>
		</td>
		<td><input type="password" size="20" maxlength="100" name="sippassword"
			   value="<%= JiveGlobals.getProperty("voicebridge.default.proxy.sippassword", "") %>">
		</td>
	    </tr>

	    <tr>
		<td align="left" width="150">
		    <fmt:message key="config.page.configuration.server"/>
		</td>
		<td><input type="text" size="20" maxlength="100" name="server"
			   value="<%= JiveGlobals.getProperty("voicebridge.default.proxy.sipserver", "") %>">
		</td>
	    </tr>

	    <tr>
		<td align="left" width="150">
		    <fmt:message key="config.page.configuration.outboundproxy"/>
		</td>
		<td><input type="text" size="20" maxlength="100" name="outboundproxy"
			   value="<%= JiveGlobals.getProperty("voicebridge.default.proxy.outboundproxy", "") %>">
		</td>
	    </tr> 
        </table>
        <p/>
        <table class="jive-table" cellpadding="0" cellspacing="0" border="0" width="50%">
            <thead> 
            <tr>
                <th colspan="2"><fmt:message key="config.page.configuration.save.title"/></th>
            </tr>
            </thead>
            <tbody> 	    
            <tr>
                <th colspan="2"><input type="submit" name="update" value="<fmt:message key="config.page.configuration.submit" />"><fmt:message key="config.page.configuration.restart.warning"/></th>
            </tr>	    
            </tbody>            
        </table>            
    </form>
</div>

</body>
</html>