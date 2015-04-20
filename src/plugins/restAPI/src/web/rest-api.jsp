<%@ page
	import="java.util.*,
                 org.jivesoftware.openfire.XMPPServer,
                 org.jivesoftware.util.*,org.jivesoftware.openfire.plugin.rest.RESTServicePlugin"
	errorPage="error.jsp"%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt"%>

<%-- Define Administration Bean --%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager" />
<c:set var="admin" value="${admin.manager}" />
<%
	admin.init(request, response, session, application, out);
%>

<%
	// Get parameters
	boolean save = request.getParameter("save") != null;
	boolean success = request.getParameter("success") != null;
	String secret = ParamUtils.getParameter(request, "secret");
	boolean enabled = ParamUtils.getBooleanParameter(request, "enabled");
	boolean httpBasicAuth = ParamUtils.getBooleanParameter(request, "authtype");
	String allowedIPs = ParamUtils.getParameter(request, "allowedIPs");

	RESTServicePlugin plugin = (RESTServicePlugin) XMPPServer.getInstance().getPluginManager()
			.getPlugin("restapi");

	// Handle a save
	Map errors = new HashMap();
	if (save) {
		if (errors.size() == 0) {
			plugin.setEnabled(enabled);
			plugin.setSecret(secret);
			plugin.setHttpBasicAuth(httpBasicAuth);
			plugin.setAllowedIPs(StringUtils.stringToCollection(allowedIPs));
			response.sendRedirect("rest-api.jsp?success=true");
			return;
		}
	}

	secret = plugin.getSecret();
	enabled = plugin.isEnabled();
	httpBasicAuth = plugin.isHttpBasicAuth();
	allowedIPs = StringUtils.collectionToString(plugin.getAllowedIPs());
%>

<html>
<head>
<title>REST API Properties</title>
<meta name="pageID" content="rest-api" />
</head>
<body>

	<p>Use the form below to enable or disable the REST API and
		configure the authentication.</p>

	<%
		if (success) {
	%>

	<div class="jive-success">
		<table cellpadding="0" cellspacing="0" border="0">
			<tbody>
				<tr>
					<td class="jive-icon"><img src="images/success-16x16.gif"
						width="16" height="16" border="0"></td>
					<td class="jive-icon-label">REST API properties edited
						successfully.</td>
				</tr>
			</tbody>
		</table>
	</div>
	<br>
	<%
		}
	%>

	<form action="rest-api.jsp?save" method="post">

		<fieldset>
			<legend>REST API</legend>
			<div>
				<p>
					The REST API can be secured with a shared secret key defined below
					or a with HTTP basic authentication.<br />Moreover, for extra
					security you can specify the list of IP addresses that are allowed
					to use this service.<br />An empty list means that the service can
					be accessed from any location. Addresses are delimited by commas.
				</p>
				<ul>
					<input type="radio" name="enabled" value="true" id="rb01"
						<%=((enabled) ? "checked" : "")%>>
					<label for="rb01"><b>Enabled</b> - REST API requests will
						be processed.</label>
					<br>
					<input type="radio" name="enabled" value="false" id="rb02"
						<%=((!enabled) ? "checked" : "")%>>
					<label for="rb02"><b>Disabled</b> - REST API requests will
						be ignored.</label>
					<br>
					<br>

					<input type="radio" name="authtype" value="true"
						id="http_basic_auth" <%=((httpBasicAuth) ? "checked" : "")%>>
					<label for="http_basic_auth">HTTP basic auth - REST API
						authentication with Openfire admin account.</label>
					<br>
					<input type="radio" name="authtype" value="false"
						id="secretKeyAuth" <%=((!httpBasicAuth) ? "checked" : "")%>>
					<label for="secretKeyAuth">Secret key auth - REST API
						authentication over specified secret key.</label>
					<br>
					<label style="padding-left: 25px" for="text_secret">Secret
						key:</label>
					<input type="text" name="secret" value="<%=secret%>"
						id="text_secret">
					<br>
					<br>

					<label for="allowedIPs">Allowed IP Addresses:</label>
					<textarea name="allowedIPs" cols="40" rows="3" wrap="virtual"><%=((allowedIPs != null) ? allowedIPs : "")%></textarea>
				</ul>

				<p>You can find here detailed documentation over the Openfire REST API: 
					<a
						href="/plugin-admin.jsp?plugin=restapi&showReadme=true&decorator=none">REST
						API Documentation</a>
				</p>
			</div>
		</fieldset>

		<br> <br> <input type="submit" value="Save Settings">
	</form>


</body>
</html>