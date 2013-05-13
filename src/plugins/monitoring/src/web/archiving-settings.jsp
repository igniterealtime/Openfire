<%@ page errorPage="/error.jsp" import="org.jivesoftware.openfire.plugin.MonitoringPlugin"
    %>
<%@ page import="org.jivesoftware.openfire.archive.ArchiveIndexer" %>
<%@ page import="org.jivesoftware.openfire.archive.ConversationManager, org.jivesoftware.util.ByteFormat, org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%
    // Get handle on the Monitoring plugin
    MonitoringPlugin plugin = (MonitoringPlugin) XMPPServer.getInstance().getPluginManager().getPlugin("monitoring");
    ConversationManager conversationManager = (ConversationManager) plugin.getModule(
            ConversationManager.class);

    ArchiveIndexer archiveIndexer = (ArchiveIndexer) plugin.getModule(ArchiveIndexer.class);
    ByteFormat byteFormatter = new ByteFormat();
    String indexSize = byteFormatter.format(archiveIndexer.getIndexSize());
%>

<html>
<head>
<title><fmt:message key="archive.settings.title"/></title>
<meta name="pageID" content="archiving-settings"/>
<link rel="stylesheet" type="text/css" href="style/global.css">
<script src="dwr/engine.js" type="text/javascript"></script>
<script src="dwr/util.js" type="text/javascript"></script>
<script src="dwr/interface/conversations.js" type="text/javascript"></script>
<link rel="stylesheet" type="text/css" href="style/style.css">
<script type="text/javascript">
    // Calls a getBuildProgress
    function getBuildProgress() {
        conversations.getBuildProgress(showBuildProgress);
    }

    function showBuildProgress(progress) {
        var rebuildElement = document.getElementById("rebuildElement");
        if (progress != null && progress != -1){
            // Update progress item.
            rebuildElement.style.display = '';
            var rebuildProgress = document.getElementById('rebuildProgress');
            rebuildProgress.innerHTML = progress;
            setTimeout("getBuildProgress()", 1000);
        }
        else {
            var rebuildProgress = document.getElementById('rebuildProgress');
            rebuildProgress.innerHTML = "100";
            Effect.Fade('rebuildElement');
        }
    }
</script>
<style type="text/css">
    .small-label {
        font-size: 11px;
        font-weight: bold;
        font-family: verdana;
    }

    .small-text {
        font-size: 11px;
        font-family: verdana;
    }

    .stat {
        border: 1px;
        border-color: #ccc;
        border-style: dotted;
    }

    .conversation-body {
        color: black;
        font-size: 11px;
        font-family: verdana;
    }

    .conversation-label1 {
        color: blue;
        font-size: 11px;
        font-family: verdana;
    }

    .conversation-label2 {
        color: red;
        font-size: 11px;
        font-family: verdana;
    }

    .conversation-table {
        font-family: verdana;
        font-size: 12px;
    }

    .light-gray-border {
        border-color: #bbb;
        border-style: solid;
        border-width: 1px 1px 1px 1px;
    }

    .light-gray-border-bottom {
        border-color: #bbb;
        border-style: solid;
        border-width: 0px 0px 1px 0px;
    }

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

<% // Get parameters
    boolean update = request.getParameter("update") != null;
    boolean messageArchiving = conversationManager.isMessageArchivingEnabled();
    boolean roomArchiving = conversationManager.isRoomArchivingEnabled();
    int idleTime = ParamUtils.getIntParameter(request, "idleTime", conversationManager.getIdleTime());
    int maxTime = ParamUtils.getIntParameter(request, "maxTime", conversationManager.getMaxTime());
    
    int maxAge = ParamUtils.getIntParameter(request, "maxAge", conversationManager.getMaxAge());
    int maxRetrievable = ParamUtils.getIntParameter(request, "maxRetrievable", conversationManager.getMaxRetrievable());
    
    boolean rebuildIndex = request.getParameter("rebuild") != null;

    if (request.getParameter("cancel") != null) {
        response.sendRedirect("archiving-settings.jsp");
        return;
    }

    if (rebuildIndex) {
        archiveIndexer.rebuildIndex();
    }

    // Update the session kick policy if requested
    Map errors = new HashMap();
    String errorMessage = "";
    if (update) {
        // New settings for message archiving.
        boolean metadataArchiving = request.getParameter("metadataArchiving") != null;
        messageArchiving = request.getParameter("messageArchiving") != null;
        roomArchiving = request.getParameter("roomArchiving") != null;
        String roomsArchived = request.getParameter("roomsArchived");

        // Validate params
        if (idleTime < 1) {
            errors.put("idleTime", "");
            errorMessage = "Idle Time must be greater than 0.";
        }
        if (maxTime < 1) {
            errors.put("maxTime", "");
            errorMessage = "Max Time must be greater than 0.";
        }
        if (roomsArchived != null && roomsArchived.contains("@")) {
            errors.put("roomsArchived", "");
            errorMessage = "Only name of local rooms should be specified.";
        }
        if (maxAge < 0) {
            errors.put("maxAge", "");
            errorMessage = "Max Age must be greater than or equal to 0.";
        }
        if (maxRetrievable < 1) {
            errors.put("maxRetrievable", "");
            errorMessage = "Max Retrievable must be greater than or equal to 0.";
        }
        // If no errors, continue:
        if (errors.size() == 0) {
            conversationManager.setMetadataArchivingEnabled(metadataArchiving);
            conversationManager.setMessageArchivingEnabled(messageArchiving);
            conversationManager.setRoomArchivingEnabled(roomArchiving);
            conversationManager.setRoomsArchived(StringUtils.stringToCollection(roomsArchived));
            conversationManager.setIdleTime(idleTime);
            conversationManager.setMaxTime(maxTime);
            
            conversationManager.setMaxAge(maxAge);
            conversationManager.setMaxRetrievable(maxRetrievable);

%>
<div class="success">
    <fmt:message key="archive.settings.success"/>
</div><br>
<%
        }
    }
%>

<%
    if (rebuildIndex) {
%>
<div class="success">
    <fmt:message key="archive.settings.rebuild.success"/>
</div><br/>

<script type="text/javascript">
    getBuildProgress();
</script>
<% } %>

<% if (errors.size() > 0) { %>
<div class="error">
    <%= errorMessage%>
</div>
<br/>
<% } %>

<p>
    <fmt:message key="archive.settings.description"/>
</p>

<form action="archiving-settings.jsp" method="post">
    <table class="settingsTable" cellpadding="3" cellspacing="0" border="0" width="90%">
        <thead>
            <tr>
                <th colspan="3"><fmt:message key="archive.settings.message.metadata.title" /></th>
            </tr>
        </thead>
        <tbody>
            <tr>
                <td colspan="3"><p><fmt:message key="archive.settings.message.metadata.description" /></p></td>
            </tr>
            <tr>
                <td colspan="2" width="90%"><label class="jive-label" for="metadata"><fmt:message key="archive.settings.enable.metadata"/>:</label><br>
                <fmt:message key="archive.settings.enable.metadata.description"/></td>
                <td><input type="checkbox" id="metadata" name="metadataArchiving" <%= conversationManager.isMetadataArchivingEnabled() ? "checked" : "" %> /></td>
            </tr>
            <tr>
                <td colspan="3"><label class="jive-label"><fmt:message key="archive.settings.enable.message"/>:</label><br>
                <fmt:message key="archive.settings.enable.message.description"/><br>
                <table width=70% align=right border="0" cellpadding="3" cellspacing="0">
                    <tr>
                        <td><fmt:message key="archive.settings.one_to_one"/></td>
                        <td><input type="checkbox" name="messageArchiving" <%= conversationManager.isMessageArchivingEnabled() ? "checked" : ""%> /></td>
                    </tr>
                    <tr>
                        <td><fmt:message key="archive.settings.group_chats"/></td>
                        <td><input type="checkbox" name="roomArchiving" <%= conversationManager.isRoomArchivingEnabled() ? "checked" : ""%> /></td>
                    </tr>
                    <tr>
                        <td><fmt:message key="archive.settings.certain_rooms"/></td>
                        <td><textarea name="roomsArchived" cols="30" rows="2" wrap="virtual"><%= StringUtils.collectionToString(conversationManager.getRoomsArchived()) %></textarea></td>
                    </tr>
                </table>
                </td>
            </tr>
            <tr>
                <td><label class="jive-label"><fmt:message key="archive.settings.idle.time"/>:</label><br>
                <fmt:message key="archive.settings.idle.time.description"/></td>
                <td><input type="text" name="idleTime" size="10" maxlength="10" value="<%= conversationManager.getIdleTime()%>" /></td>
                <td></td>
            </tr>
            <tr>
                <td><label class="jive-label"><fmt:message key="archive.settings.max.time"/>:</label><br>
                <fmt:message key="archive.settings.max.time.description"/><br><br></td>
                <td><input type="text" name="maxTime" size="10" maxlength="10" value="<%= conversationManager.getMaxTime()%>" /></td>
                <td></td>
            </tr>
            
            <tr>
                <td><label class="jive-label"><fmt:message key="archive.settings.max.age"/>:</label><br>
                <fmt:message key="archive.settings.max.age.description"/><br><br></td>
                <td><input type="text" name="maxAge" size="10" maxlength="10" value="<%= conversationManager.getMaxAge()%>" /></td>
                <td></td>
            </tr>
            
            <tr>
                <td><label class="jive-label"><fmt:message key="archive.settings.max.retrievable"/>:</label><br>
                <fmt:message key="archive.settings.max.retrievable.description"/><br><br></td>
                <td><input type="text" name="maxRetrievable" size="10" maxlength="10" value="<%= conversationManager.getMaxRetrievable()%>" /></td>
                <td></td>
            </tr>
            
        </tbody>
    </table>


    <input type="submit" name="update" value="<fmt:message key="archive.settings.update.settings" />">
    <input type="submit" name="cancel" value="<fmt:message key="archive.settings.cancel" />">

    <br>
    <br>
    <% if (messageArchiving || roomArchiving) { %>
    <br>

    <table class="settingsTable" cellpadding="3" cellspacing="0" border="0" width="90%">
        <thead>
            <tr>
               <th colspan="3" width="100%"><fmt:message key="archive.settings.index.settings"/></th>
            </tr>
        </thead>
        <tbody>
           <tr>
               <td colspan="3" width="100%"><p><fmt:message key="archive.settings.index.settings.description"/></p></td>
           </tr>
           <tr valign="top">
               <td width="80%"><b><fmt:message key="archive.settings.current.index"/></b> - <fmt:message key="archive.settings.current.index.description"/></td>
               <td><%= indexSize %></td>
               <td></td>
           </tr>
           <tr valign="top">
               <td><b><fmt:message key="archive.settings.message.count"/></b> - <fmt:message key="archive.settings.message.count.description"/></td>
               <td><%= conversationManager.getArchivedMessageCount()%></td>
               <td></td>
           </tr>
           <tr valign="top">
               <td><b><fmt:message key="archive.settings.conversation.count"/></b> - <fmt:message key="archive.settings.conversation.count.description"/><br><br></td>
               <td><%= conversationManager.getArchivedConversationCount()%></td>
               <td></td>
           </tr>
        </tbody>
    </table>

    <input type="submit" name="rebuild" value="<fmt:message key="archive.settings.rebuild" />"/>
    <span id="rebuildElement" style="display:none;" class="jive-description">Rebuilding is <span id="rebuildProgress"></span>% complete.</span>

    <%} %>
</form>


</body>
</html>
