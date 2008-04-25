<%@ page
   import="org.jivesoftware.openfire.XMPPServer,
           org.jivesoftware.openfire.plugin.MotDPlugin,
           org.jivesoftware.util.ParamUtils,
           java.util.HashMap,
           java.util.Map"
   errorPage="error.jsp"%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt"%>

<%
	boolean save = request.getParameter("save") != null;	
	boolean motdEnabled = ParamUtils.getBooleanParameter(request, "motdenabled", false);
	String motdSubject = ParamUtils.getParameter(request, "motdSubject");
	String motdMessage = ParamUtils.getParameter(request, "motdMessage");
    
	MotDPlugin plugin = (MotDPlugin) XMPPServer.getInstance().getPluginManager().getPlugin("motd");

	Map<String, String> errors = new HashMap<String, String>();	
	if (save) {
	  if (motdSubject == null || motdSubject.trim().length() < 1) {
	     errors.put("missingMotdSubject", "missingMotdSubject");
	  }
       
	  if (motdMessage == null || motdMessage.trim().length() < 1) {
	     errors.put("missingMotdMessage", "missingMotdMessage");
	  }
       
	  if (errors.size() == 0) {
	     plugin.setEnabled(motdEnabled);
	     plugin.setSubject(motdSubject);
	     plugin.setMessage(motdMessage);
           
	     response.sendRedirect("motd-form.jsp?settingsSaved=true");
	     return;
	  }		
	}
    
	motdEnabled = plugin.isEnabled();
	motdSubject = plugin.getSubject();
	motdMessage = plugin.getMessage();
%>

<html>
	<head>
	  <title><fmt:message key="motd.title" /></title>
	  <meta name="pageID" content="motd-form"/>
	</head>
	<body>

<form action="motd-form.jsp?save" method="post">

<div class="jive-contentBoxHeader"><fmt:message key="motd.options" /></div>
<div class="jive-contentBox">
   
	<% if (ParamUtils.getBooleanParameter(request, "settingsSaved")) { %>
   
	<div class="jive-success">
	<table cellpadding="0" cellspacing="0" border="0">
	<tbody>
	  <tr>
	     <td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
	     <td class="jive-icon-label"><fmt:message key="motd.saved.success" /></td>
	  </tr>
	</tbody>
	</table>
	</div>
   
	<% } %>
   
	<table cellpadding="3" cellspacing="0" border="0" width="100%">
	<tbody>
	  <tr>
	     <td width="1%" align="center" nowrap><input type="checkbox" name="motdenabled" <%=motdEnabled ? "checked" : "" %>></td>
	     <td width="99%" align="left"><fmt:message key="motd.enable" /></td>
	  </tr>
	</tbody>
	</table>
   
   <br><br>
	<p><fmt:message key="motd.directions" /></p>
   
	<table cellpadding="3" cellspacing="0" border="0" width="100%">
	<tbody>
	  <tr>
	     <td width="5%" valign="top"><fmt:message key="motd.subject" />:&nbsp;</td>
	     <td width="95%"><input type="text" name="motdSubject" value="<%= motdSubject %>"></td>
	     <% if (errors.containsKey("missingMotdSubject")) { %>
	        <span class="jive-error-text"><fmt:message key="motd.subject.missing" /></span>
	     <% } %> 
	  </tr>
	  <tr>
	     <td width="5%" valign="top"><fmt:message key="motd.message" />:&nbsp;</td>
	     <td width="95%"><textarea cols="45" rows="5" wrap="virtual" name="motdMessage"><%= motdMessage %></textarea></td>
	     <% if (errors.containsKey("missingMotdMessage")) { %>
	        <span class="jive-error-text"><fmt:message key="motd.message.missing" /></span>
	     <% } %>            
	  </tr>
	</tbody>
	</table>
</div>
<input type="submit" value="<fmt:message key="motd.button.save" />"/>
</form>

</body>
</html>
