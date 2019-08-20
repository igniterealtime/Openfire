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

<style type="text/css" title="setupStyle" media="screen">
    @import "style/lightbox.css";
    @import "style/ldap.css";
</style>

<script language="JavaScript" type="text/javascript" src="js/prototype.js"></script>
<script language="JavaScript" type="text/javascript" src="js/scriptaculous.js"></script>
<script language="JavaScript" type="text/javascript" src="js/lightbox.js"></script>
<script language="javascript" type="text/javascript" src="js/tooltips/domLib.js"></script>
<script language="javascript" type="text/javascript" src="js/tooltips/domTT.js"></script>
<script language="javascript" type="text/javascript" src="js/setup.js"></script>

<%@ include file="setup/ldap-group.jspf" %>
