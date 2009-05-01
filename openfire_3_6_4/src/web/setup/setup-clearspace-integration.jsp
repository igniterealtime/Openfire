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
    boolean forceTest = false;
    String currentPage = "setup-clearspace-integration.jsp";
    String testPage = "setup-clearspace-integration_test.jsp";
    String nextPage = "setup-finished.jsp";
    Map<String, String> meta = new HashMap<String, String>();
    meta.put("currentStep", "3");
%>
<%@ include file="clearspace-integration.jspf" %>
