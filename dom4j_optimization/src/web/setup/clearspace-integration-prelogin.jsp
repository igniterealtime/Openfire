<%
    boolean initialSetup = false;
    boolean forceTest = true;
    String currentPage = "clearspace-integration-prelogin.jsp";
    String testPage = "setup-clearspace-integration_test.jsp";
    String nextPage = "../login.jsp";
    Map<String, String> meta = new HashMap<String, String>();
    meta.put("pageID", "profile-settings");
%>

<style type="text/css" title="setupStyle" media="screen">
	@import "../style/lightbox.css";
	@import "../style/ldap.css";
</style>

<script language="JavaScript" type="text/javascript" src="../js/prototype.js/prototype.js"></script>
<script language="JavaScript" type="text/javascript" src="../js/scriptaculous.js/scriptaculous.js"></script>
<script language="JavaScript" type="text/javascript" src="../js/lightbox.js/lightbox.js"></script>
<script language="javascript" type="text/javascript" src="../js/tooltips/domLib.js/tooltips/domLib.js"></script>
<script language="javascript" type="text/javascript" src="../js/tooltips/domTT.js/tooltips/domTT.js"></script>
<script language="javascript" type="text/javascript" src="../js/setup.js/setup.js"></script>

<%@ include file="clearspace-integration.jspf" %>