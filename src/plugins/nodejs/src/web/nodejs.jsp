<%@ page import="java.util.*" %>
<%@ page import="org.ifsoft.nodejs.openfire.*" %>
<%@ page import="org.jivesoftware.openfire.*" %>
<%@ page import="org.jivesoftware.util.*" %>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%

    boolean update = request.getParameter("update") != null;
    String errorMessage = null;

    // Get handle on the plugin
    PluginImpl plugin = (PluginImpl) XMPPServer.getInstance().getPluginManager().getPlugin("nodejs");

    if (update)
    {                        
    String path = request.getParameter("path"); 	
        JiveGlobals.setProperty("org.ifsoft.nodejs.openfire.path", path);                 
        
    }

%>
<html>
<head>
   <title><fmt:message key="config.page.title" /></title>

   <meta name="pageID" content="nodejs-settings"/>
</head>
<body>
<% if (errorMessage != null) { %>
<div class="error">
    <%= errorMessage%>
</div>
<br/>
<% } %>

<div class="jive-table">
<form action="nodejs.jsp" method="post">
    <p>
        <table class="jive-table" cellpadding="0" cellspacing="0" border="0" width="100%">
            <thead> 
            <tr>
                <th colspan="2"><fmt:message key="config.page.title"/></th>
            </tr>
            </thead>
            <tbody>  
        <tr>
        <td align="left" width="150">
            <fmt:message key="config.page.configuration.path"/>
        </td>
        <td><input type="text" size="50" maxlength="100" name="path"
               value="<%= JiveGlobals.getProperty("org.ifsoft.nodejs.openfire.path", plugin.getPath()) %>">
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

<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
    <th nowrap><fmt:message key="config.page.configuration.node.process" /></th>
    <th nowrap><fmt:message key="config.page.configuration.node.path" /></th>	
    <th nowrap><fmt:message key="config.page.configuration.node.home" /></th>
    </tr>
</thead>
<tbody>    

    <%
        List<String> properties = JiveGlobals.getPropertyNames();    
        
        for (String n : properties) 
        {        	
        if (n.indexOf("js.") == 0 && n.indexOf(".path") != n.length() - 5) 
        {
                String v = JiveGlobals.getProperty(n);
                String p = StringUtils.replace(StringUtils.escapeHTMLTags(v), "\n", "");
            String h = JiveGlobals.getProperty(n + ".path", JiveGlobals.getProperty("org.ifsoft.nodejs.openfire.path", plugin.getPath()));		
    %>
    
    <tr>

        <td>
            <div class="hidebox" style="width:200px;">
                <span title="<%= StringUtils.escapeForXML(n) %>">
                <%= StringUtils.escapeHTMLTags(n) %>
                </span>
            </div>
        </td>
        <td>
            <div class="hidebox" style="width:300px;">
                <% if (JiveGlobals.isPropertyEncrypted(n) || 
                       JiveGlobals.isPropertySensitive(n)) { %>
                <span style="color:#999;"><i>hidden</i></span>
                <% } else { %>
                <span title="<%= ("".equals(p) ? "&nbsp;" : p) %>"><%= ("".equals(p) ? "&nbsp;" : p) %></span>
                <% } %>
            </div>
        </td>
        <td>
            <div class="hidebox" style="width:300px;">
                <% if (JiveGlobals.isPropertyEncrypted(n) || 
                       JiveGlobals.isPropertySensitive(n)) { %>
                <span style="color:#999;"><i>hidden</i></span>
                <% } else { %>
                <span title="<%= ("".equals(h) ? "&nbsp;" : h) %>"><%= ("".equals(h) ? "&nbsp;" : h) %></span>
                <% } %>
            </div>
        </td>                
    </tr>

    <% 
            }   
        }
    %>

</tbody>
</table>    
</div>

</body>
</html>
