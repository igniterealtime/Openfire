<%@ page import="java.util.Map" %>
<%@ page import="java.util.HashMap" %>
<%
    boolean initialSetup = false;
    String currentPage = "ldap-user.jsp";
    String testPage = "setup/setup-ldap-user_test.jsp";
    String nextPage = "ldap-group.jsp";
    Map<String, String> meta = new HashMap<String, String>();
    meta.put("pageID", "profile-settings");
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

<%@ include file="setup/ldap-user.jspf" %>