<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%
    // Redirect if we've already run setup:
    if (!XMPPServer.getInstance().isSetupMode()) {
        response.sendRedirect("setup-completed.jsp");
        return;
    }
%>

<%
    String serverType = null;
    if (request.getParameter("save") != null || request.getParameter("test") != null) {
        int serverTypeInt = ParamUtils.getIntParameter(request, "servertype", 1);
        switch (serverTypeInt) {
            case 1:
                serverType = "other";
                break;
            case 2:
                serverType = "activedirectory";
                break;
            case 3:
                serverType = "openldap";
                break;
            default:
                serverType = "other";
        }
    }

    boolean initialSetup = true;
    String currentPage = "setup-ldap-server.jsp";
    String testPage = "setup-ldap-server_test.jsp?serverType="+ serverType;
    String nextPage = "setup-ldap-user.jsp?serverType=" + serverType;
    Map<String, String> meta = new HashMap<String, String>();
    meta.put("currentStep", "3");
%>
<%@ include file="ldap-server.jspf" %>
