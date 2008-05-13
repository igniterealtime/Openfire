<%@ page errorPage="/error.jsp" import="org.jivesoftware.util.JiveGlobals" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.StringTokenizer" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.plugin.ClientControlPlugin" %>
<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>


<%!
    /**
     * Enumeration of possible clients.
     */
    enum Clients {
        Spark("Spark", "spark", "http://www.igniterealtime.org/projects/spark/index.jsp", "images/client-icon_spark.gif"),
        Adium("Adium", "libgaim", "http://www.adiumx.com/", "images/client-icon_adium.gif"),
        Exodus("Exodus", "exodus", "http://exodus.jabberstudio.org/", "images/client-icon_exodus.gif"),
        Gaim("Gaim", "gaim", "http://gaim.sourceforge.net", "images/client-icon_gaim.gif"),
        IChat("IChat", "ichat", "http://www.mac.com/1/ichat.html", "images/client-icon_ichat.gif"),
        JBother("JBother", "jbother", "http://www.jbother.org/", "images/client-icon_jbother.gif"),
        Pandion("Pandion", "pandion", "http://www.pandion.be/", "images/client-icon_pandion.gif"),
        PSI("PSI", "psi", "http://psi-im.org", "images/client-icon_psi.gif"),
        Trillian("Trillian", "trillian", "http://www.ceruleanstudios.com/", "images/client-icon_trillian.gif");

        private String name;
        private String version;
        private String url;
        private String image;

        Clients(String name, String version, String url, String image) {
            this.name = name;
            this.version = version;
            this.url = url;
            this.image = image;
        }

        public String getName() {
            return name;
        }

        public String getVersion() {
            return version;
        }

        public String getURL() {
            return url;
        }

        public String getImage(){
            return image;
        }

    }
%>
<%
    String clientsAllowed = JiveGlobals.getProperty("clients.allowed", "all");
    String otherClientsAllowed = JiveGlobals.getProperty("other.clients.allowed", "");

    final List<String> clients = new ArrayList<String>();
    final List<String> otherClients = new ArrayList<String>();


    StringTokenizer otherTokens = new StringTokenizer(otherClientsAllowed, ",");
    while (otherTokens.hasMoreTokens()) {
        otherClients.add(otherTokens.nextToken());
    }


    String other = null;


    boolean submit = request.getParameter("submit") != null;
    boolean addOther = request.getParameter("addOther") != null;
    boolean remove = request.getParameter("removeClient") != null;

    if (submit) {
        String[] cls = request.getParameterValues("client");

        int length = cls != null ? cls.length : 0;
        for (int i = 0; i < length; i++) {
            clients.add(cls[i]);
        }

        boolean all = Boolean.valueOf(request.getParameter("all"));


        final StringBuilder builder = new StringBuilder();
        // Set clients allowed
        for (String client : clients) {
            builder.append(client);
            builder.append(",");
        }

        // If all clients are allowed, delete the property to enforce the default of
        // all clients being allowed.
        if (all) {
            JiveGlobals.deleteProperty("clients.allowed");
            clients.add("all");
        }
        // Otherw, set the specific set of clients.
        else {
            JiveGlobals.setProperty("clients.allowed", builder.toString());
        }

    }
    else if (addOther) {
        other = request.getParameter("other");
        otherTokens = new StringTokenizer(other, ",");
        while (otherTokens.hasMoreTokens()) {
            otherClients.add(otherTokens.nextToken());
        }

        persistOtherClients(otherClients);
    }
    else if (remove) {
        String clientToRemove = request.getParameter("removeClient");
        otherClients.remove(clientToRemove);

        persistOtherClients(otherClients);
    }
    else {
        StringTokenizer tkn = new StringTokenizer(clientsAllowed, ",");
        while (tkn.hasMoreTokens()) {
            String token = tkn.nextToken();
            if (!"all".equals(token)) {
                other = token;
            }
            clients.add(token);
        }
    }
%>

<html>
<head>
    <title><fmt:message key="permitted.client.title"/></title>
    <meta name="pageID" content="client-version"/>
    <script type="text/javascript" language="javascript" src="scripts/tooltips/domLib.js"></script>
    <script type="text/javascript" language="javascript" src="scripts/tooltips/domTT.js"></script>
    <link rel="stylesheet" type="text/css" href="style/style.css">

    <script type="text/javascript">
        function disableAll() {
            var all_value;
            for (i = 0; i < document.f.all.length; i++) {
                if (document.f.all[i].checked) {
                    all_value = document.f.all[i].value;
                }
            }

            for (i = 0; i < document.f.client.length; i++) {
                if (all_value == "true") {
                    document.f.client[i].disabled = true;
                    document.f.other.disabled = true;
                        var boxall = document.getElementById('boxall');
                        var boxspecify = document.getElementById('boxspecify');
                        boxall.style.background = "#fffbe2"
					    boxspecify.style.background = "#F4F4F4"
                }
                else {
                    document.f.client[i].disabled = false;
                    document.f.other.disabled = false;
                        var boxall = document.getElementById('boxall');
                        var boxspecify = document.getElementById('boxspecify');
                        boxall.style.background = "#F4F4F4"
					    boxspecify.style.background = "#fffbe2"
                }
            }
        }

        function selectCheckbox(boxName) {
            for (i = 0; i < document.f.client.length; i++) {
                var box = document.f.client[i];
                if (box.value == boxName) {
                    box.checked = !box.checked;
                }
            }
        }
    </script>

    <style type="text/css">
    .content {
        border-color: #bbb;
        border-style: solid;
        border-width: 0px 0px 1px 0px;
    }

    /* Default DOM Tooltip Style */
    div.domTT {
        border: 1px solid #bbb;
        background-color: #F9F5D5;
        font-family: arial;
        font-size: 9px;
        padding: 5px;
    }

    div.domTT .caption {
        font-family: serif;
        font-size: 12px;
        font-weight: bold;
        padding: 1px 2px;
        color: #FFFFFF;
    }

    div.domTT .contents {
        font-size: 12px;
        font-family: sans-serif;
        padding: 3px 2px;
    }

    .textfield {
        font-size: 11px;
        font-family: verdana;
        padding: 3px 2px;
        background: #efefef;
    }

    .keyword-field {
        font-size: 11px;
        font-family: verdana;
        padding: 3px 2px;
    }
    </style>
    <style type="text/css">
        @import "style/style.css";
    </style>
</head>

<body>

<% if (submit || remove) { %>

<div class="success">
  <fmt:message key="permitted.client.success"/>
</div>
<br>
<% }%>



<form name="f" action="permitted-clients.jsp" method="post">

    <fieldset style="display: block;width:620px;">
    <legend><fmt:message key="permitted.client.legend"/></legend>

    <div class="clientscontent">

		<div class="permitclientbox permitclientActive" id="boxall">
		<input type="radio" name="all" value="true" onclick="disableAll();" <%= clients.contains("all") ? "checked" : ""%> /><strong><fmt:message key="permitted.client.all.clients"/></strong> - <fmt:message key="permitted.client.all.clients.description"/>
		</div>

		<div class="permitclientbox" id="boxspecify">
		<input type="radio" name="all" value="false" onclick="disableAll();" <%= !clients.contains("all") ? "checked" : ""%> /><strong><fmt:message key="permitted.client.specific.clients"/></strong><br>
			<div class="specifyclients">
                <table border="0">
                    <tr>
				        <td valign="top" nowrap>
                            <div style="display: block; width: 260px;">
                               <%
                                int count = 0;
                                for (Clients client : Clients.values()) {
                                count++;
                                    if (count == 6) {
                                %>
                            </div>
                        </td>
                        <td valign="top" nowrap>
                            <div style="display: block; width: 205px;">
                                    <% } %>
                            <label for="<%= client.getName() %>"><input type="checkbox" name="client" value="<%= client.getName() %>" id="<%= client.getName() %>" <%= clients.contains(client.getName()) ? "checked" : ""%> /> <img src="<%= client.getImage() %>" width="16" height="16" border="0" alt=""> <strong><%= client.getName() %></strong></label><span>(<a href="<%= client.getURL() %>" target="_blank"><fmt:message key="permitted.client.website"/></a>)</span><br>
                                <% } %>
                            </div>
                        </td>
				    </tr>
				</table>

            <span class="horizontalrule" style="height:1px;"></span>

            <strong><fmt:message key="permitted.client.add.other.client" />:</strong>
            <a onmouseover="domTT_activate(this, event, 'content', '<fmt:message key="permitted.client.tooltip" />', 'trail', true, 'direction', 'northeast', 'width', '220');"><img src="images/icon_help_14x14.gif" align="texttop" /></a><br>
			<input type="text" name="other" style="width: 160px;">&nbsp;<input type="submit" name="addOther" value="<fmt:message key="permitted.client.add" />"/><br>
            <% for (String otherClient : otherClients) { %>
                <%= otherClient%>&nbsp(<a href="permitted-clients.jsp?removeClient=<%=otherClient%>" name="removeClient" id="<%= otherClient %>"><fmt:message key="permitted.client.remove" /></a>)<br>
            <% } %>

            </div>

        </div>

	</div>

    </fieldset>



    <input type="submit" name="submit" value="<fmt:message key="permitted.client.save.settings" />" style="clear: both; margin-top: 15px;"/>

</form>

<script type="text/javascript">
    disableAll();
</script>

</body>
</html>

<%!
    void persistOtherClients(List otherClients) {
        // Build other string.
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < otherClients.size(); i++) {
            builder.append(otherClients.get(i));
            if (i < otherClients.size()) {
                builder.append(",");
            }
        }

        JiveGlobals.setProperty("other.clients.allowed", builder.toString());
    }
%>