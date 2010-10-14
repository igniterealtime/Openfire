<%@ page import="org.jivesoftware.util.ParamUtils"
%>
<%@ page import="org.jivesoftware.openfire.plugin.DebuggerPlugin" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<html>
    <head>
        <title>XML Debugger Properties</title>
        <meta name="pageID" content="debugger-conf"/>
    </head>
    <body>

<%
    boolean update = request.getParameter("update") != null;
    boolean c2s = ParamUtils.getBooleanParameter(request,"c2s");
    boolean ssl = ParamUtils.getBooleanParameter(request,"ssl");
    boolean extcomp = ParamUtils.getBooleanParameter(request,"extcomp");
    boolean cm = ParamUtils.getBooleanParameter(request,"cm");

    DebuggerPlugin plugin = (DebuggerPlugin) XMPPServer.getInstance().getPluginManager().getPlugin("xmldebugger");
    if (update) {
        // Save new settings
        plugin.getDefaultPortFilter().setEnabled(c2s);
        plugin.getOldPortFilter().setEnabled(ssl);
        plugin.getComponentPortFilter().setEnabled(extcomp);
        plugin.getMultiplexerPortFilter().setEnabled(cm);
    }
    else {
        // Set current values
        c2s = plugin.getDefaultPortFilter().isEnabled();
        ssl = plugin.getOldPortFilter().isEnabled();
        extcomp = plugin.getComponentPortFilter().isEnabled();
        cm = plugin.getMultiplexerPortFilter().isEnabled();
    }
%>


<form name="f" action="debugger-conf.jsp">
	<div class="jive-contentBoxHeader">
		Debug connections
	</div>
	<div class="jive-contentBox">
		<table cellpadding="3" cellspacing="0" border="0">
		<tbody>
		<tr valign="middle">
			<td width="1%" nowrap>
				<input id="rb01" type="checkbox" name="c2s" <%= (c2s ? "checked" : "") %>/>
			</td>
			<td width="99%">
				<label for="rb01">
					Client (default port)
				</label>
			</td>
		</tr>
		<tr valign="middle">
			<td width="1%" nowrap>
				<input id="rb02" type="checkbox" name="ssl" <%= (ssl ? "checked" : "") %>/>
			</td>
			<td width="99%">
				<label for="rb02">
					Client (old SSL port)
				</label>
			</td>
		</tr>
		<tr valign="middle">
			<td width="1%" nowrap>
				<input id="rb03" type="checkbox" name="extcomp" <%= (extcomp ? "checked" : "") %>/>
			</td>
			<td width="99%">
				<label for="rb03">
					External Component
				</label>
			</td>
		</tr>
		<tr valign="middle">
			<td width="1%" nowrap>
				<input id="rb04" type="checkbox" name="cm" <%= (cm ? "checked" : "") %>/>
			</td>
			<td width="99%">
				<label for="rb04">
					Connection Manager
				</label>
			</td>
		</tr>
		</tbody>
		</table>
	</div>
    <input type="submit" name="update" value="<fmt:message key="global.save_settings" />">
</form>

</body>
</html>