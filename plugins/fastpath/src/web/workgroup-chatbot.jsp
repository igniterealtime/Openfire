<%--
  -	$RCSfile$
  -	$Revision: 25955 $
  -	$Date: 2006-01-18 16:24:51 -0800 (Wed, 18 Jan 2006) $
--%>
<%@ page import="org.jivesoftware.util.*,
                 java.util.*,
                 org.jivesoftware.xmpp.workgroup.WorkgroupManager,
                 org.jivesoftware.xmpp.workgroup.Workgroup,
                 org.jivesoftware.xmpp.workgroup.WorkgroupAdminManager,
                 org.xmpp.packet.JID,
                 org.jivesoftware.xmpp.workgroup.utils.ModelUtil, org.jivesoftware.openfire.fastpath.settings.chat.ChatSettings, org.jivesoftware.openfire.fastpath.settings.chat.ChatSettingsManager, org.jivesoftware.openfire.fastpath.settings.chat.ChatSetting"%>
<%
    // Get parameters
    String wgID = ParamUtils.getParameter(request, "wgID");
    WorkgroupAdminManager workgroupAdminManager = new WorkgroupAdminManager();
    workgroupAdminManager.init(pageContext);
    boolean updated = ParamUtils.getBooleanParameter(request, "updated", false);
    boolean enabled = ParamUtils.getBooleanParameter(request, "enabled", false);
    long timeout = ParamUtils.getIntParameter(request, "timeout", 30);

    JID workgroupJID = new JID(wgID);
    String restore = request.getParameter("restore");

    final ChatSettingsManager chatSettingsManager = ChatSettingsManager.getInstance();
    Workgroup workgroup = WorkgroupManager.getInstance().getWorkgroup(workgroupJID);
    ChatSettings chatSettings = chatSettingsManager.getChatSettings(workgroup);

    String saveText = request.getParameter("saveText");
    if (ModelUtil.hasLength(saveText)) {
        Enumeration en = request.getParameterNames();
        while (en.hasMoreElements()) {
            String key = (String)en.nextElement();
            String value = request.getParameter(key);
            ChatSetting setting = chatSettings.getChatSetting(key);
            if (setting != null && ModelUtil.hasLength(value)) {
                setting.setValue(value);
                chatSettingsManager.updateChatSetting(setting);
            }
        }
    }

    String keys = request.getParameter("_key");
    if (ModelUtil.hasLength(keys)) {
        ChatSetting setting = chatSettings.getChatSetting(keys);
        String defaultValue = setting.getDefaultValue();
        setting.setValue(defaultValue);
        chatSettingsManager.updateChatSetting(setting);
    }

    String enabledText = request.getParameter("enabled");
    String timeoutText = request.getParameter("timeout");
    if (ModelUtil.hasLength(enabledText)) {
        workgroup.chatbotEnabled(enabled);
    }
    else if (ModelUtil.hasLength(timeoutText)) {
        if (workgroup.isChatbotEnabled()) {
            workgroup.getChatBot().setIdleTimeout(timeout * 60 * 1000);
        }
    }
    else {
        enabled = workgroup.isChatbotEnabled();
        if (enabled) {
            timeout = workgroup.getChatBot().getIdleTimeout() / (60 * 1000);
        }
    }
%>
<html>
    <head>
        <title><%= "Chatbot Configuration for "+wgID%></title>
        <meta name="subPageID" content="workgroup-chatbot"/>
        <meta name="extraParams" content="<%= "wgID="+wgID %>"/>
        <!--<meta name="helpPage" content="configure_chatbot_settings.html"/>-->

        <link rel="stylesheet" type="text/css" href="/style/global.css">
        <script>
        function restoreKey(name){
            document.text3._key.value = name;
            document.text3.submit();
        }
        </script>
        <script language="javascript">
            function changeImage(image, img) {
                img.src = image;
            }
        </script>
    </head>
    <body>

      <%
          if(updated){
      %>
       <div class="jive-success">
            <table cellpadding="0" cellspacing="0" border="0">
                <tbody>
                    <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16"
                    border="0"></td>
                        <td class="jive-icon-label">
                           Web Chat images have been updated successfully
                        </td></tr>
                </tbody>
            </table>
        </div><br/>
      <% } %>

      <!-- Create HTML Code Snippet Table -->
    <p>Use the form below to configure the messages that the chatbot will send to users using standard XMPP clients.</p>

        <form name="text" action="workgroup-chatbot.jsp" method="post">
        <fieldset>
            <legend>Chatbot activation</legend>
            <div>
            <p>
            Enable or disable the chatbot for this workgroup.
            </p>
            <table cellpadding="3" cellspacing="0" border="0" width="100%">
            <tbody>
                <tr>
                    <td width="1%">
                        <input type="radio" name="enabled" value="true" id="rb01" <%= ((enabled) ? "checked" : "") %>>
                    </td>
                    <td width="99%">
                        <label for="rb01"><b>Enabled</b></label> - Users will be attended by the chatbot.
                    </td>
                </tr>
                <tr>
                    <td width="1%">
                        <input type="radio" name="enabled" value="false" id="rb02" <%= ((!enabled) ? "checked" : "") %>>
                    </td>
                    <td width="99%">
                        <label for="rb02"><b>Disabled</b></label> - Messages sent to the workgroup will be ignored.
                    </td>
                </tr>
            </tbody>
            </table>
            </div>
        </fieldset>
          <input type="hidden" name="wgID" value="<%= wgID %>" />
          <br>
          <input type="submit" value="Save Settings" />
        </form>
        <br>

        <form name="text2" action="workgroup-chatbot.jsp" method="post">
        <fieldset>
            <legend>Idle Session Settings</legend>
            <div>
            <p>
            Sessions that haven't been used for a while will be removed.
            </p>
            <table cellpadding="3" cellspacing="0" border="0" width="100%">
            <tbody>
                <tr>
                    <td width="30%" nowrap>
                        Remove sessions after they have been idle for
                    </td>
                    <td width="70%">
                        <input type="text" name="timeout" size="15" maxlength="50" value="<%= timeout %>"> minutes
                    </td>
                </tr>
            </tbody>
            </table>
            </div>
        </fieldset>
          <input type="hidden" name="wgID" value="<%= wgID %>" />
          <br>
          <input type="submit" value="Save Settings" />
        </form>
        <br>

        <!-- Text Settings -->
        <form name="text3" action="workgroup-chatbot.jsp" method="post">
        <fieldset>
            <legend>Chatbot Text Settings</legend>
            <div>
            <table  class="jive-table"  width="100%" cellpadding="3" cellspacing="0" border="0">
            <tr>
            <th>Event</th><th>Current Message</th><th colspan="2">Default Message</th>
            </tr>
            <%
                Iterator iter = chatSettings.getChatSettingsByType(ChatSettings.SettingType.bot_settings).iterator();
                while(iter.hasNext()){
                    ChatSetting setting = (ChatSetting)iter.next();
            %>
            <tr valign="top">
                 <td width="25%"><%= setting.getLabel() %></td>
                 <td><textarea cols="25" rows="5" name="<%= setting.getKey() %>"><%= setting.getValue() %></textarea></td>
                 <td><%= setting.getDefaultValue() %></td>
                 <td><input type="submit" name="restore" value="Restore Defaults" onClick="restoreKey('<%=setting.getKey()%>');"></td>
                 <input type="hidden" name="key" value="<%= setting.getKey() %>" />
            </tr>
            <% } %>
            </table>
            </div>
        </fieldset>
            <br>
          <input type="hidden" name="_key" />
          <input type="hidden" name="wgID" value="<%= wgID %>" />
          <input type="submit" name="saveText" value="Update Text Settings" />
        </form>

    </body>
</html>
<%
    session.setAttribute("workgroup", wgID);
%>