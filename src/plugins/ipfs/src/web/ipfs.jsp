<%@ page import="java.util.*" %>
<%@ page import="org.ifsoft.ipfs.openfire.*" %>
<%@ page import="org.jivesoftware.openfire.*" %>
<%@ page import="org.jivesoftware.util.*" %>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%

    boolean update = request.getParameter("update") != null;
    String errorMessage = null;

    // Get handle on the plugin
    PluginImpl plugin = (PluginImpl) XMPPServer.getInstance().getPluginManager().getPlugin("ipfs");

    if (update)
    {                        
        String path = request.getParameter("path");     
        JiveGlobals.setProperty("ipfs.path", path);                 

        String ipfsEnabled = request.getParameter("ipfsEnabled");
        JiveGlobals.setProperty("ipfs.enabled", (ipfsEnabled != null && ipfsEnabled.equals("on")) ? "true": "false");        
    }

%>
<html>
<head>
   <title><fmt:message key="config.page.title" /></title>

   <meta name="pageID" content="ipfs-settings"/>
</head>
<body>
<% if (errorMessage != null) { %>
<div class="error">
    <%= errorMessage%>
</div>
<br/>
<% } %>

<div class="jive-table">
<form action="ipfs.jsp" method="post">
    <p>
        <table class="jive-table" cellpadding="0" cellspacing="0" border="0" width="100%">
            <thead> 
            <tr>
                <th colspan="2"><fmt:message key="config.page.title"/></th>
            </tr>
            </thead>
            <tbody>  
            <tr>
                <td nowrap  colspan="2">
                    <input type="checkbox" name="ipfsEnabled"<%= (JiveGlobals.getProperty("ipfs.enabled", "true").equals("true")) ? " checked" : "" %>>
                    <fmt:message key="config.page.configuration.enabled" />       
                </td>  
            </tr>
            <tr>
                <td align="left" width="150">
                    <fmt:message key="config.page.configuration.path"/>
                </td>
                <td><input type="text" size="50" maxlength="100" name="path"
                       value="<%= JiveGlobals.getProperty("ipfs.path", plugin.getPath()) %>">
                </td>
            </tr>            
            </tbody>
        </table>
    </p>
   <p>
        <table class="jive-table" cellpadding="0" cellspacing="0" border="0" width="100%">
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
