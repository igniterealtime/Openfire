<%--
 - Jitsi VideoBridge, OpenSource video conferencing.
 -
 - Distributable under LGPL license.
 - See terms of license at gnu.org.
--%>
<%@ page import="org.jivesoftware.openfire.plugin.ofmeet.*" %>
<%@ page import="org.jitsi.videobridge.openfire.*" %>
<%@ page import="org.jivesoftware.openfire.*" %>
<%@ page import="org.jivesoftware.util.*" %>
<%@ page import="java.io.File" %>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<%

    boolean update = request.getParameter("update") != null;
    String errorMessage = null;

    // Get handle on the plugin
    OfMeetPlugin container = (OfMeetPlugin) XMPPServer.getInstance().getPluginManager().getPlugin("ofmeet");
    PluginImpl plugin = container.getPlugin();

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
                    "ofmeet");
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
                    "ofmeet");
            }
        }
        
	String checkReplay = request.getParameter("checkreplay"); 
        JiveGlobals.setProperty(PluginImpl.CHECKREPLAY_PROPERTY_NAME, checkReplay);
        
	String localAddress = request.getParameter("localaddress"); 
        JiveGlobals.setProperty(PluginImpl.NAT_HARVESTER_LOCAL_ADDRESS, localAddress);

	String publicAddress = request.getParameter("publicaddress"); 
        JiveGlobals.setProperty(PluginImpl.NAT_HARVESTER_PUBLIC_ADDRESS, publicAddress);
        
	String securityenabled = request.getParameter("securityenabled"); 
        JiveGlobals.setProperty("ofmeet.security.enabled", securityenabled);	
        
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

	String iceServers = request.getParameter("iceservers"); 	
        JiveGlobals.setProperty("org.jitsi.videobridge.ofmeet.iceservers", iceServers);         
        
	String useIPv6 = request.getParameter("useipv6"); 	
        JiveGlobals.setProperty("org.jitsi.videobridge.ofmeet.useipv6", useIPv6);         
        
	String useNicks = request.getParameter("usenicks"); 	
        JiveGlobals.setProperty("org.jitsi.videobridge.ofmeet.usenicks", useNicks);         
        
	String resolution = request.getParameter("resolution"); 	
        JiveGlobals.setProperty("org.jitsi.videobridge.ofmeet.resolution", resolution);                 

	String audiobandwidth = request.getParameter("audiobandwidth"); 	
        JiveGlobals.setProperty("org.jitsi.videobridge.ofmeet.audio.bandwidth", audiobandwidth);   
        
	String videobandwidth = request.getParameter("videobandwidth"); 	
        JiveGlobals.setProperty("org.jitsi.videobridge.ofmeet.video.bandwidth", videobandwidth);     
        
	String audiomixer = request.getParameter("audiomixer"); 	
        JiveGlobals.setProperty("org.jitsi.videobridge.ofmeet.audio.mixer", audiomixer);   
        
	String clientusername = request.getParameter("clientusername"); 	
        JiveGlobals.setProperty("org.jitsi.videobridge.ofmeet.sip.username", clientusername);    
        
	String clientpassword = request.getParameter("clientpassword"); 	
        JiveGlobals.setProperty("org.jitsi.videobridge.ofmeet.sip.password", clientpassword);          

	String allowdirectsip = request.getParameter("allowdirectsip"); 
        JiveGlobals.setProperty("org.jitsi.videobridge.ofmeet.allow.direct.sip", allowdirectsip);   
        
	String recordsecret = request.getParameter("recordsecret"); 	
        JiveGlobals.setProperty("org.jitsi.videobridge.ofmeet.recording.secret", recordsecret);          

	String recordpath = request.getParameter("recordpath"); 
        JiveGlobals.setProperty("org.jitsi.videobridge.ofmeet.recording.path", recordpath);  
        
	String adaptivelastn = request.getParameter("adaptivelastn"); 
        JiveGlobals.setProperty("org.jitsi.videobridge.ofmeet.adaptive.lastn", adaptivelastn); 

	String adaptivesimulcast = request.getParameter("adaptivesimulcast"); 
        JiveGlobals.setProperty("org.jitsi.videobridge.ofmeet.adaptive.simulcast", adaptivesimulcast);         

	String enablesimulcast = request.getParameter("enablesimulcast"); 
        JiveGlobals.setProperty("org.jitsi.videobridge.ofmeet.enable.simulcast", enablesimulcast); 

	String focusjid = request.getParameter("focusjid"); 
        JiveGlobals.setProperty("org.jitsi.videobridge.ofmeet.focus.user.jid", focusjid); 
        
	String focuspassword = request.getParameter("focuspassword"); 
        JiveGlobals.setProperty("org.jitsi.videobridge.ofmeet.focus.user.password", focuspassword);         
    }

%>
<html>
<head>
   <title><fmt:message key="config.page.settings.title" /></title>

   <meta name="pageID" content="ofmeet-settings"/>
</head>
<body>
<% if (errorMessage != null) { %>
<div class="error">
    <%= errorMessage%>
</div>
<br/>
<% } %>

<div class="jive-table">
    <form action="ofmeet-settings.jsp" method="post">
    <p>
        <table class="jive-table" cellpadding="0" cellspacing="0" border="0" width="75%">
            <thead> 
            <tr>
                <th colspan="2"><fmt:message key="config.page.configuration.ofmeet.title"/></th>
            </tr>
            </thead>
            <tbody>             
	    <tr>
		    <td  nowrap colspan="2">
			<input type="radio" value="false" name="useipv6" <%= ("false".equals(JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.useipv6", "false")) ? "checked" : "") %>>
			<b><fmt:message key="config.page.configuration.ofmeet.useipv6.disabled" /></b> - <fmt:message key="config.page.configuration.ofmeet.useipv6.disabled_desc" />
		    </td>
	    </tr>   
	    <tr>
		    <td  nowrap colspan="2">
			<input type="radio" value="true" name="useipv6" <%= ("true".equals(JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.useipv6", "false")) ? "checked" : "") %>>
			<b><fmt:message key="config.page.configuration.ofmeet.useipv6.enabled" /></b> - <fmt:message key="config.page.configuration.ofmeet.useipv6.enabled_desc" />
		    </td>
	    </tr> 
	    <tr>
		    <td  nowrap colspan="2">
			<input type="radio" value="false" name="usenicks" <%= ("false".equals(JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.usenicks", "false")) ? "checked" : "") %>>
			<b><fmt:message key="config.page.configuration.ofmeet.usenicks.disabled" /></b> - <fmt:message key="config.page.configuration.ofmeet.usenicks.disabled_desc" />
		    </td>
	    </tr>   
	    <tr>
		    <td  nowrap colspan="2">
			<input type="radio" value="true" name="usenicks" <%= ("true".equals(JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.usenicks", "false")) ? "checked" : "") %>>
			<b><fmt:message key="config.page.configuration.ofmeet.usenicks.enabled" /></b> - <fmt:message key="config.page.configuration.ofmeet.usenicks.enabled_desc" />
		    </td>
	    </tr>
	    <tr>
		<td colspan="2" align="left" width="150">
		    <fmt:message key="config.page.configuration.ofmeet.iceservers"/>
		</td>
	    </tr>	
	    <tr>		
		<td colspan="2"><input type="text" size="100" maxlength="256" name="iceservers"
			   value="<%= JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.iceservers", "") %>" placeholder="{ 'iceServers': [{ 'url': 'stun:stun.l.google.com:19302' }] }">
		</td>
	    </tr>	
	    <tr>
		<td align="left" width="150">
		    <fmt:message key="config.page.configuration.ofmeet.resolution"/>
		</td>
		<td><input type="text" size="10" maxlength="100" name="resolution"
			   value="<%= JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.resolution", "360") %>">
		</td>
	    </tr>
	    <tr>
		<td align="left" width="150">
		    <fmt:message key="config.page.configuration.ofmeet.audio.bandwidth"/>
		</td>
		<td><input type="text" size="10" maxlength="100" name="audiobandwidth"
			   value="<%= JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.audio.bandwidth", "64") %>">
		</td>
	    </tr>
	    <tr>
		<td align="left" width="150">
		    <fmt:message key="config.page.configuration.ofmeet.video.bandwidth"/>
		</td>
		<td><input type="text" size="10" maxlength="100" name="videobandwidth"
			   value="<%= JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.video.bandwidth", "512") %>">
		</td>
	    </tr>	    
            </tbody>
        </table>
    </p>
    <p>
        <table class="jive-table" cellpadding="0" cellspacing="0" border="0" width="75%">
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
            <tr>
                <td><fmt:message key="config.page.configuration.local.ip.address"/><br>
                </td>
                <td align="left">
                    <input name="localaddress" type="text" maxlength="20" size="15"
                           value="<%=JiveGlobals.getProperty(PluginImpl.NAT_HARVESTER_LOCAL_ADDRESS, XMPPServer.getInstance().getServerInfo().getHostname())%>"/>
                </td>
            </tr>
            <tr>
                <td><fmt:message key="config.page.configuration.public.ip.address"/><br>
                </td>
                <td align="left">
                    <input name="publicaddress" type="text" maxlength="20" size="15"
                           value="<%=JiveGlobals.getProperty(PluginImpl.NAT_HARVESTER_PUBLIC_ADDRESS, XMPPServer.getInstance().getServerInfo().getHostname())%>"/>
                </td>
            </tr>            
	    <tr>
		    <td  nowrap colspan="2">
			<input type="radio" value="false" name="checkreplay" <%= ("false".equals(JiveGlobals.getProperty(PluginImpl.CHECKREPLAY_PROPERTY_NAME, "false")) ? "checked" : "") %>>
			<b><fmt:message key="config.page.configuration.checkreplay.disabled" /></b> - <fmt:message key="config.page.configuration.checkreplay.disabled_description" />
		    </td>
	    </tr>   
	    <tr>
		    <td  nowrap colspan="2">
			<input type="radio" value="true" name="checkreplay" <%= ("true".equals(JiveGlobals.getProperty(PluginImpl.CHECKREPLAY_PROPERTY_NAME, "false")) ? "checked" : "") %>>
			<b><fmt:message key="config.page.configuration.checkreplay.enabled" /></b> - <fmt:message key="config.page.configuration.checkreplay.enabled_description" />
		    </td>
	    </tr>   
	    <tr>
		    <td  nowrap colspan="2">
			<input type="radio" value="false" name="audiomixer" <%= ("false".equals(JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.audio.mixer", "false")) ? "checked" : "") %>>
			<b><fmt:message key="config.page.configuration.audiomixer.disabled" /></b> - <fmt:message key="config.page.configuration.audiomixer.disabled_description" />
		    </td>
	    </tr>   
	    <tr>
		    <td  nowrap colspan="2">
			<input type="radio" value="true" name="audiomixer" <%= ("true".equals(JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.audio.mixer", "false")) ? "checked" : "") %>>
			<b><fmt:message key="config.page.configuration.audiomixer.enabled" /></b> - <fmt:message key="config.page.configuration.audiomixer.enabled_description" />
		    </td>
	    </tr> 	    
            </tbody>
        </table> 
     </p>   
     <p>
        <table class="jive-table" cellpadding="0" cellspacing="0" border="0" width="75%">
            <thead>
            <tr>
                <th colspan="2"><fmt:message key="config.page.configuration.security.title"/></th>
            </tr>
            </thead>
            <tbody>  
	    <tr>
		<td align="left" width="150">
		    <fmt:message key="config.page.configuration.focus.jid"/>
		</td>
		<td><input type="text" size="20" maxlength="100" name="focusjid"
			   value="<%= JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.focus.user.jid", "focus@" + XMPPServer.getInstance().getServerInfo().getXMPPDomain()) %>">
		</td>
	    </tr>

	    <tr>
		<td align="left" width="150">
		    <fmt:message key="config.page.configuration.focus.password"/>
		</td>
		<td><input type="password" size="20" maxlength="100" name="focuspassword"
			   value="<%= JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.focus.user.password", "focus-password-" + System.currentTimeMillis()) %>">
		</td>
	    </tr>            
	    <tr>
		    <td  nowrap colspan="2">
			<input type="radio" value="false" name="securityenabled" <%= ("false".equals(JiveGlobals.getProperty("ofmeet.security.enabled", "true")) ? "checked" : "") %>>
			<b><fmt:message key="config.page.configuration.security.disabled" /></b> - <fmt:message key="config.page.configuration.security.disabled_description" />
		    </td>
	    </tr>   
	    <tr>
		    <td  nowrap colspan="2">
			<input type="radio" value="true" name="securityenabled" <%= ("true".equals(JiveGlobals.getProperty("ofmeet.security.enabled", "true")) ? "checked" : "") %>>
			<b><fmt:message key="config.page.configuration.security.enabled" /></b> - <fmt:message key="config.page.configuration.security.enabled_description" />
		    </td>
	    </tr> 
            </tbody>
        </table> 
    </p>
    <p>        
        <table class="jive-table" cellpadding="0" cellspacing="0" border="0" width="75%">
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
	    <tr>
		<td align="left" width="150">
		    <fmt:message key="config.page.configuration.record.path"/>
		</td>
		<td><input type="text" size="60" maxlength="100" name="recordpath"
			   value="<%= JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.recording.path", container.pluginDirectory.getAbsolutePath() + File.separator + "recordings") %>">
		</td>
	    </tr>

	    <tr>
		<td align="left" width="150">
		    <fmt:message key="config.page.configuration.record.secret"/>
		</td>
		<td><input type="password" size="60" maxlength="100" name="recordsecret"
			   value="<%= JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.recording.secret", "secret") %>">
		</td>
	    </tr>	    
            </tbody>
        </table>
    </p> 
    <p>
        <table class="jive-table" cellpadding="0" cellspacing="0" border="0" width="75%">
            <thead> 
            <tr>
                <th><fmt:message key="config.page.configuration.telephone.client.title"/></th>
                <th><%= container.sipRegisterStatus %></th>
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
		<td><input type="text" size="40" maxlength="100" name="server"
			   value="<%= JiveGlobals.getProperty("voicebridge.default.proxy.sipserver", "") %>">
		</td>
	    </tr>

	    <tr>
		<td align="left" width="150">
		    <fmt:message key="config.page.configuration.outboundproxy"/>
		</td>
		<td><input type="text" size="40" maxlength="100" name="outboundproxy"
			   value="<%= JiveGlobals.getProperty("voicebridge.default.proxy.outboundproxy", "") %>">
		</td>
	    </tr> 
        </table>
   </p>
    <p>
        <table class="jive-table" cellpadding="0" cellspacing="0" border="0" width="75%">
            <thead> 
            <tr>
                <th><fmt:message key="config.page.configuration.telephone.server.title"/></th>
            </tr>
            </thead>
            <tbody> 
	    <tr>
		<td align="left" width="150">
		    <fmt:message key="config.page.configuration.authusername"/>
		</td>
		<td><input type="text" size="20" maxlength="100" name="clientusername"
			   value="<%= JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.sip.username", "ofmeet") %>">
		</td>
	    </tr>

	    <tr>
		<td align="left" width="150">
		    <fmt:message key="config.page.configuration.sippassword"/>
		</td>
		<td><input type="password" size="20" maxlength="100" name="clientpassword"
			   value="<%= JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.sip.password", "ofmeet") %>">
		</td>
	    </tr>
	    <tr>
		    <td  nowrap colspan="2">
			<input type="radio" value="false" name="allowdirectsip" <%= ("false".equals(JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.allow.direct.sip", "false")) ? "checked" : "") %>>
			<b><fmt:message key="config.page.configuration.allowdirectsip.disabled" /></b> - <fmt:message key="config.page.configuration.allowdirectsip.disabled_description" />
		    </td>
	    </tr>   
	    <tr>
		    <td  nowrap colspan="2">
			<input type="radio" value="true" name="allowdirectsip" <%= ("true".equals(JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.allow.direct.sip", "false")) ? "checked" : "") %>>
			<b><fmt:message key="config.page.configuration.allowdirectsip.enabled" /></b> - <fmt:message key="config.page.configuration.allowdirectsip.enabled_description" />
		    </td>
	    </tr> 	     	    
        </table>
   </p>   
    <p>
        <table class="jive-table" cellpadding="0" cellspacing="0" border="0" width="75%">
            <thead> 
            <tr>
                <th><fmt:message key="config.page.configuration.advanced.features.title"/></th>
            </tr>
            </thead>
            <tbody> 
	    <tr>
		    <td  nowrap colspan="2">
			<input type="radio" value="false" name="adaptivelastn" <%= ("false".equals(JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.adaptive.lastn", "false")) ? "checked" : "") %>>
			<b><fmt:message key="config.page.configuration.adaptivelastn.disabled" /></b> - <fmt:message key="config.page.configuration.adaptivelastn.disabled_description" />
		    </td>
	    </tr>   
	    <tr>
		    <td  nowrap colspan="2">
			<input type="radio" value="true" name="adaptivelastn" <%= ("true".equals(JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.adaptive.lastn", "false")) ? "checked" : "") %>>
			<b><fmt:message key="config.page.configuration.adaptivelastn.enabled" /></b> - <fmt:message key="config.page.configuration.adaptivelastn.enabled_description" />
		    </td>
	    </tr> 
	    <tr>
		    <td  nowrap colspan="2">
			<input type="radio" value="false" name="adaptivesimulcast" <%= ("false".equals(JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.adaptive.simulcast", "false")) ? "checked" : "") %>>
			<b><fmt:message key="config.page.configuration.adaptivesimulcast.disabled" /></b> - <fmt:message key="config.page.configuration.adaptivesimulcast.disabled_description" />
		    </td>
	    </tr>   
	    <tr>
		    <td  nowrap colspan="2">
			<input type="radio" value="true" name="adaptivesimulcast" <%= ("true".equals(JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.adaptive.simulcast", "false")) ? "checked" : "") %>>
			<b><fmt:message key="config.page.configuration.adaptivesimulcast.enabled" /></b> - <fmt:message key="config.page.configuration.adaptivesimulcast.enabled_description" />
		    </td>
	    </tr>

	    <tr>
		    <td  nowrap colspan="2">
			<input type="radio" value="false" name="enablesimulcast" <%= ("false".equals(JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.enable.simulcast", "false")) ? "checked" : "") %>>
			<b><fmt:message key="config.page.configuration.enablesimulcast.disabled" /></b> - <fmt:message key="config.page.configuration.enablesimulcast.disabled_description" />
		    </td>
	    </tr>   
	    <tr>
		    <td  nowrap colspan="2">
			<input type="radio" value="true" name="enablesimulcast" <%= ("true".equals(JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.enable.simulcast", "false")) ? "checked" : "") %>>
			<b><fmt:message key="config.page.configuration.enablesimulcast.enabled" /></b> - <fmt:message key="config.page.configuration.enablesimulcast.enabled_description" />
		    </td>
	    </tr>	    
        </table>
   </p>    
   <p>
        <table class="jive-table" cellpadding="0" cellspacing="0" border="0" width="75%">
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
    </p>
    </form>
</div>

</body>
</html>