<%
    boolean initialSetup = false;
    String currentPage = "clearspace-integration.jsp";
    String testPage = "setup/setup-clearspace-integration_test.jsp";
    String nextPage = "profile-settings.jsp";
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

<%@ include file="setup/clearspace-integration.jspf" %>