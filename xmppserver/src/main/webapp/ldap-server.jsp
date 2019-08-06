<%@ page contentType="text/html; charset=UTF-8" %>
<%
    String serverType = null;
    boolean initialSetup = false;
    String currentPage = "ldap-server.jsp";
    String testPage = "setup/setup-ldap-server_test.jsp";
    String nextPage = "ldap-user.jsp";
    Map<String, String> meta = new HashMap<String, String>();
    meta.put("pageID", "profile-settings");

    pageContext.setAttribute( "serverType", serverType );
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
                        
<%@ include file="setup/ldap-server.jspf" %>
