<%@ page import="org.jivesoftware.wildfire.XMPPServer,
                 org.jivesoftware.util.*,
                 org.jivesoftware.wildfire.gateway.GatewayPlugin"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%  // Get parameters
    String gwType = ParamUtils.getParameter(request, "gwType");
    boolean gwEnabled = ParamUtils.getBooleanParameter(request, "gwEnabled");

    GatewayPlugin plugin = (GatewayPlugin)XMPPServer.getInstance().getPluginManager().getPlugin("gateway");

    if (gwType != null) {
        if (gwEnabled) {
            plugin.enableService(gwType);
        }
        else {
            plugin.disableService(gwType);
        }
    }
%>
