<%@ page errorPage="error.jsp" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="org.apache.log4j.Logger" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="org.dom4j.Element" %>
<%@ page import="org.dom4j.Attribute" %>
<%@ page import="org.jivesoftware.util.JiveGlobals" %>
<%@ page import="org.jivesoftware.util.LocaleUtils" %>
<%@ page import="org.dom4j.Document" %>
<%@ page import="net.sf.kraken.KrakenPlugin" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page contentType="text/html;charset=UTF-8" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%
    final KrakenPlugin plugin =
            (KrakenPlugin) XMPPServer.getInstance().getPluginManager().getPlugin("kraken");

    final ArrayList<String> optionTypes = new ArrayList<String>();

    class GatewaySettings {

        Logger Log = Logger.getLogger(GatewaySettings.class);

        JspWriter out = null;
        Integer jsID = 0; // Javascript incrementable id

        GatewaySettings(JspWriter out) {
            this.out = out;
            pollConfigOptions();
        }

        public String join(Collection s, String delimiter) {
            // Borrowed from http://www.bigbold.com/snippets/posts/show/91
            StringBuffer buffer = new StringBuffer();
            Iterator iter = s.iterator();
            while (iter.hasNext()) {
                buffer.append(iter.next());
                if (iter.hasNext()) {
                    buffer.append(delimiter);
                }
            }
            return buffer.toString();
        }

        void printConfigNode(Element node) {
            try {
                Attribute type = node.attribute("type");
                if (type.getText().equals("text")) {
                    // Required fields
                    Attribute desckey = node.attribute("desckey");
                    Attribute var = node.attribute("var");
                    Attribute sysprop = node.attribute("sysprop");

                    // Optional fields
                    Attribute def = node.attribute("default");
                    Attribute size = node.attribute("size");
                    Attribute maxlen = node.attribute("maxlength");

                    if (desckey == null || var == null || sysprop == null) {
                        Log.error("Missing variable from options config.");
                        return;
                    }

                    String defStr = "";
                    if (def != null) {
                        defStr = def.getText();
                    }

                    String descStr = LocaleUtils.getLocalizedString(desckey.getText(), "kraken");
                    String setting = JiveGlobals.getProperty(sysprop.getText(), defStr);

                    String inputId = var.getText();
                    out.println("<tr valign='middle'>");
                    out.println("<td align='right' width='20%'><label for='" + inputId + "'>" +
                            descStr + "</label>:</td>");
                    out.print("<td><input type='text' id='" + inputId + "' name='" + inputId + "'" +
                            (size != null ? " size='" + size.getText() + "'" : "") +
                            (size != null ? " maxlength='" + maxlen.getText() + "'" : "") +
                            " value='" + setting + "'");
                    out.println(" /></td>");
                    out.println("</tr>");
                } else if (type.getText().equals("toggle")) {
                    // Required fields
                    Attribute desckey = node.attribute("desckey");
                    Attribute var = node.attribute("var");
                    Attribute sysprop = node.attribute("sysprop");

                    // Optional fields
                    Attribute def = node.attribute("default");
                    Attribute alert = node.attribute("alert");

                    if (desckey == null || var == null || sysprop == null) {
                        Log.error("Missing variable from options config.");
                        return;
                    }

                    boolean defBool = false;
                    if (def != null && (def.getText().equals("1") || def.getText().equals("true") ||
                            def.getText().equals("enabled") || def.getText().equals("yes"))) {
                        defBool = true;
                    }

                    String descStr = LocaleUtils.getLocalizedString(desckey.getText(), "kraken");
                    String alertStr = null;
                    if (alert != null && alert.getText() != null && alert.getText().length() > 0) {
                        alertStr = LocaleUtils.getLocalizedString(alert.getText(), "kraken");
                    }
                    boolean setting = JiveGlobals.getBooleanProperty(sysprop.getText(), defBool);

                    String jsStr = (++jsID).toString();
                    String checkId = var.getText();
                    boolean hasChildren = node.elements("item").size() > 0;
                    out.println("<tr valign='top'>");
                    out.print("<td align='right' width='20%'><input type='checkbox' id='" +
                            checkId + "' name='" + checkId + "' value='true' " +
                            (setting ? " checked='checked'" : ""));
                    if (hasChildren) {
                        out.print(" onClick='elem = document.getElementById(\"" + jsStr +
                                "\"); if (elem) { if (this.checked) { elem.style.display=\"table\"} else { elem.style.display=\"none\"} }'");
                    }
                    if (alertStr != null) {
                        out.print(" onClick='elem = document.getElementById(\"" + checkId +
                                "\"); if (elem) { if (this.checked) { return confirm(\""+alertStr+"\") } else { return true; } } else { return true; }'");
                    }
                    out.println("/></td>");
                    out.print("<td><label for='" + checkId + "'>" + descStr + "</label>");
                    if (hasChildren) {
                        out.println("<table id='" + jsStr + "' width='100%' style='display: " +
                                (setting ? "table" : "none") + "'>");
                        for (Object itemObj : node.elements("item")) {
                            Element item = (Element) itemObj;
                            printConfigNode(item);
                        }
                        out.println("</table>");
                    }
                    out.println("</td>");
                    out.println("</tr>");
                }
            }
            catch (Exception e) {
                // Uhm, yeah, that sucks.
                Log.error("Error printing config node:", e);
            }
        }

        void pollConfigOptions() {
            Document optConfig = plugin.getOptionsConfig();
            Element mainPanel = optConfig.getRootElement().element("mainpanel");
            if (mainPanel != null && mainPanel.nodeCount() > 0) {
                for (Object nodeObj : mainPanel.elements("item")) {
                    Element node = (Element) nodeObj;
                    getConfigOptions(node);
                }
            }
        }

        void getConfigOptions(Element node) {
            try {
                Attribute type = node.attribute("type");
                if (type.getText().equals("text")) {
                    // Required fields
                    Attribute desckey = node.attribute("desckey");
                    Attribute var = node.attribute("var");
                    Attribute sysprop = node.attribute("sysprop");

                    if (desckey == null || var == null || sysprop == null) {
                        Log.error("Missing variable from options config.");
                        return;
                    }

                    // Store a copy of the node variable for later use.
                    if (!optionTypes.contains(var.getText())) {
                        optionTypes.add(var.getText());
                    }
                } else if (type.getText().equals("toggle")) {
                    // Required fields
                    Attribute desckey = node.attribute("desckey");
                    Attribute var = node.attribute("var");
                    Attribute sysprop = node.attribute("sysprop");

                    if (desckey == null || var == null || sysprop == null) {
                        Log.error("Missing variable from options config.");
                        return;
                    }

                    // Store a copy of the node variable for later use.
                    if (!optionTypes.contains(var.getText())) {
                        optionTypes.add(var.getText());
                    }
                    for (Object itemObj : node.elements("item")) {
                        Element item = (Element) itemObj;
                        getConfigOptions(item);
                    }
                }
            }
            catch (Exception e) {
                // Uhm, yeah, that sucks.
                Log.error("Error reading config node:", e);
            }
        }

        void printSettingsDialog() {
            try {
                Document optConfig = plugin.getOptionsConfig();
                Element mainPanel = optConfig.getRootElement().element("mainpanel");
%>
    <!-- Options Window -->
        <div>
            <form id="jiveoptionsform" action="" onSubmit="return false">
                <table border="0" cellpadding="0" cellspacing="0" width="100%">
                    <tr>
                        <td align="left">
<%
                if (mainPanel != null && mainPanel.nodeCount() > 0) {
                    out.println("<table border='0' cellpadding='1' cellspacing='2'>");
                    for (Object nodeObj : mainPanel.elements("item")) {
                        Element node = (Element)nodeObj;
                        printConfigNode(node);
                    }
                    out.println("</table>");
                }
                else {
                    out.println("&nbsp;");
                }
%>
                        </td>
                    </tr>
                </table>

                <span id="optionsresults" class="saveResultsMsg"></span>
                <input type="submit" name="submit" value="<%= LocaleUtils.getLocalizedString("gateway.web.settings.saveoptions", "kraken") %>" onclick="saveOptions(); return false" class="jive-formButton">
                <input type="reset" name="cancel" value="<%= LocaleUtils.getLocalizedString("gateway.web.settings.cancelchanges", "kraken") %>" onclick="cancelOptions(); return true" class="jive-formButton">
            </form>
        </div>


<%
            }
            catch (Exception e) {
                // Uhm, yeah, that sucks.
                Log.error("Error printing settings section:", e);
            }
        }
    }

    GatewaySettings settings =  new GatewaySettings(out);
%>

<html>

<head>
<title><fmt:message key="gateway.web.settings.title" /></title>
<meta name="pageID" content="kraken-settings">
<style type="text/css">
<!--	@import url("style/kraken.css");    -->
</style>
<script src="dwr/engine.js" type="text/javascript"></script>
<script src="dwr/util.js" type="text/javascript"></script>
<script src="dwr/interface/ConfigManager.js" type="text/javascript"></script>
<script type="text/javascript" >
    DWREngine.setErrorHandler(handleError);
    window.onerror = handleError;

    function handleError(error) {
        // swallow errors
    }

    var optionTypes = new Array(
<%
        Boolean first = true;
        for (String var : optionTypes) {
            if (!first) {
                out.println(",");
            }
            out.print("      \""+var+"\"");
            if (first) {
                first = false;
            }
        }
%>
    );

    function saveOptions(transportID) {
        var globalSettings = new Object();
        for (var x in optionTypes) {
            var optType = optionTypes[x];
            var optionId = transportID+optType;
            var testoption = document.getElementById(optionId);
            if (testoption != null) {
                transportSettings[optType] = DWRUtil.getValue(optionId);
            }
        }
        ConfigManager.saveSettings(globalSettings);
        document.getElementById("setStatusMsg").style.display = "";
        document.getElementById("setStatusMsg").innerHTML = "<span class='successresults'><img src='images/success-16x16.gif' align='absmiddle' /><fmt:message key='gateway.web.settings.settingssaved' /></span>";
        setTimeout("to_saveOptions()", 5000);
    }

    function cancelOptions(transportID) {
        document.getElementById("setStatusMsg").style.display = "";
        document.getElementById("setStatusMsg").innerHTML = "<span class='warningresults'><img src='images/warning-16x16.gif' align='absmiddle' /><fmt:message key='gateway.web.settings.cancelledchanges' /></span>";
        setTimeout("to_saveOptions()", 5000);
    }

    function to_saveOptions() {
        Effect.Fade("setStatusMsg");
    }

    function pingSession() {
        ConnectionTester.pingSession();
        setTimeout("pingSession()", 60000); // Every minute
    }

    setTimeout("pingSession()", 60000); // One minute after first load
</script>
</head>

<body>
<p><fmt:message key="gateway.web.settings.instructions" /></p>

<div id="setStatusMsg" style="display: none"></div>

<form action="" name="gatewayForm">

<% settings.printSettingsDialog(); %>

</form>

<br clear="all" />
</body>

</html>
