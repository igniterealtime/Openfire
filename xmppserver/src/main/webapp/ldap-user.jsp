<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.HashMap" %>
<%
    boolean initialSetup = false;
    String currentPage = "ldap-user.jsp";
    String testPage = "setup/setup-ldap-user_test.jsp";
    String nextPage = "ldap-group.jsp";
    Map<String, String> meta = new HashMap<>();
    meta.put("pageID", "profile-settings");

    pageContext.setAttribute( "initialSetup", initialSetup );
    pageContext.setAttribute( "currentPage", currentPage );
    pageContext.setAttribute( "testPage", testPage );
    pageContext.setAttribute( "nextPage", nextPage );
    pageContext.setAttribute( "meta", meta );
%>

<style title="setupStyle" media="screen">
    @import "style/lightbox.css";
    @import "style/ldap.css";
</style>

<script src="js/lightbox.js"></script>
<script src="js/setup.js"></script>

<%@ include file="setup/ldap-user.jspf" %>
