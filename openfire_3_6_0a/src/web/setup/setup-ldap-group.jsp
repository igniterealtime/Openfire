<%@ page import="org.jivesoftware.openfire.XMPPServer"%>

<%
    // Redirect if we've already run setup:
	if (!XMPPServer.getInstance().isSetupMode()) {
        response.sendRedirect("setup-completed.jsp");
        return;
    }
%>

<%
    // Get parameters
    boolean initialSetup = true;
    String currentPage = "setup-ldap-group.jsp";
    String testPage = "setup-ldap-group_test.jsp";
    String nextPage =  "setup-admin-settings.jsp?ldap=true";
    Map<String, String> meta = new HashMap<String, String>();
    meta.put("currentStep", "3");
%>
<%@ include file="ldap-group.jspf" %>