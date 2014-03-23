<%@ page import="org.jitsi.videobridge.openfire.*" %>
<%@ page import="org.jivesoftware.openfire.*" %>
<%@ page import="org.jivesoftware.openfire.container.*" %>
<%@ page import="org.jivesoftware.util.*" %>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<%

	String hostname = XMPPServer.getInstance().getServerInfo().getHostname();
	String domain = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
	boolean websockets = XMPPServer.getInstance().getPluginManager().getPlugin("websockets") != null;
	
	response.setHeader("Content-Type", "application/javascript");
%>
var config = {
    hosts: {
        domain: <%=domain%>,
        muc: 'conference.<%=domain%>',
        bridge: 'jitsi-videobridge.<%=domain%>',
    },
    useIPv6: false,
    useNicks: false,
    useWebsockets: <%= websockets ? "true" : "false" %>,
    resolution: "360",
    bosh: window.location.protocol + "//" + window.location.host + '/http-bind/'
};
