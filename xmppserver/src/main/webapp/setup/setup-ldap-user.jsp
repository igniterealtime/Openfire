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
    boolean initialSetup = true;
    String currentPage = "setup-ldap-user.jsp";
    String testPage = "setup-ldap-user_test.jsp";
    String nextPage =  "setup-ldap-group.jsp";
    Map<String, String> meta = new HashMap<>();
    meta.put("currentStep", "3");

    pageContext.setAttribute( "initialSetup", initialSetup );
    pageContext.setAttribute( "currentPage", currentPage );
    pageContext.setAttribute( "testPage", testPage );
    pageContext.setAttribute( "nextPage", nextPage );
    pageContext.setAttribute( "meta", meta );
%>
<%@ include file="ldap-user.jspf" %>
