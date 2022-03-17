<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.HashMap" %>
<%
    boolean initialSetup = false;
    String currentPage = "ldap-group.jsp";
    String testPage = "setup/setup-ldap-group_test.jsp";
    String nextPage = "profile-settings.jsp";
    Map<String, String> meta = new HashMap<>();
    meta.put("pageID", "profile-settings");

    pageContext.setAttribute( "initialSetup", initialSetup );
    pageContext.setAttribute( "currentPage", currentPage );
    pageContext.setAttribute( "testPage", testPage );
    pageContext.setAttribute( "nextPage", nextPage );
    pageContext.setAttribute( "meta", meta );
%>

<style title="setupStyle" media="screen">
    @import "style/ldap.css";
</style>

<script src="js/setup.js"></script>

<%@ include file="setup/ldap-group.jspf" %>
