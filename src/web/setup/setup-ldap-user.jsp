<%@ page import="org.jivesoftware.openfire.XMPPServer" %>

<%
    // Redirect if we've already run setup:
    if (!XMPPServer.getInstance().isSetupMode()) {
        response.sendRedirect("setup-completed.jsp");
        return;
    }
%>

<%
    boolean initialSetup = true;
    String currentPage = "setup-ldap-user.jsp";
    String testPage = "setup-ldap-user_test.jsp";
    String nextPage =  "setup-ldap-group.jsp";
    Map<String, String> meta = new HashMap<String, String>();
    meta.put("currentStep", "3");
%>
<%@ include file="ldap-user.jspf" %>