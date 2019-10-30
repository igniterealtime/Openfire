<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%
    // Redirect if we've already run setup:
    if (!XMPPServer.getInstance().isSetupMode()) {
        response.sendRedirect("setup-completed.jsp");
        return;
    }
%>

<%
    String serverType = "";
    if (request.getParameter("save") != null || request.getParameter("test") != null) {
        serverType = ParamUtils.getStringParameter(request, "serverType", "");
        // Sanitise the serverType
        switch (serverType) {
            case "activedirectory":
            case "openldap":
            case "other":
                break;
            default:
                serverType = "";
        }
    }

    boolean initialSetup = true;
    String currentPage = "setup-ldap-server.jsp";
    String testPage = "setup-ldap-server_test.jsp?serverType="+ serverType;
    String nextPage = "setup-ldap-user.jsp?serverType=" + serverType;
    Map<String, String> meta = new HashMap<String, String>();
    meta.put("currentStep", "3");

    pageContext.setAttribute( "serverType", serverType );
    pageContext.setAttribute( "initialSetup", initialSetup );
    pageContext.setAttribute( "currentPage", currentPage );
    pageContext.setAttribute( "testPage", testPage );
    pageContext.setAttribute( "nextPage", nextPage );
    pageContext.setAttribute( "meta", meta );
%>
<%@ include file="ldap-server.jspf" %>
