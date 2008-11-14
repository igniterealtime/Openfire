<%--
  -	$RCSfile$
  -	$Revision: 28108 $
  -	$Date: 2006-03-06 13:49:44 -0800 (Mon, 06 Mar 2006) $
--%>
<%@ page import="org.jivesoftware.util.*,
                 java.util.*,
                 org.jivesoftware.xmpp.workgroup.WorkgroupManager,
                 org.jivesoftware.xmpp.workgroup.Workgroup,
                 org.jivesoftware.xmpp.workgroup.WorkgroupAdminManager,
                 org.xmpp.packet.JID,
                 org.jivesoftware.xmpp.workgroup.utils.ModelUtil, org.jivesoftware.openfire.fastpath.settings.chat.ChatSettingsManager, org.jivesoftware.openfire.fastpath.settings.chat.ChatSettings, org.jivesoftware.openfire.fastpath.settings.chat.ChatSetting, org.jivesoftware.openfire.fastpath.settings.chat.KeyEnum"%>
<%!
   List<InternalModel> pageList = new ArrayList<InternalModel>();
%>
<%
    pageList = new ArrayList<InternalModel>();

    // Get parameters
    String wgID = ParamUtils.getParameter(request, "wgID");
    WorkgroupAdminManager workgroupAdminManager = new WorkgroupAdminManager();
    workgroupAdminManager.init(pageContext);
    boolean updated = ParamUtils.getBooleanParameter(request, "updated", false);

    JID workgroupJID = new JID(wgID);
    String restore = request.getParameter("restore");

    final ChatSettingsManager chatSettingsManager = ChatSettingsManager.getInstance();
    Workgroup workgroup = WorkgroupManager.getInstance().getWorkgroup(workgroupJID);

    ChatSettings chatSettings = chatSettingsManager.getChatSettings(workgroup);

    // Notify the workgroup
    workgroup.imagesChanged();

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
        updated = true;
    }

    String keys = request.getParameter("_key");
    if (ModelUtil.hasLength(keys)) {
        ChatSetting setting = chatSettings.getChatSetting(keys);
        String defaultValue = setting.getDefaultValue();
        setting.setValue(defaultValue);
        chatSettingsManager.updateChatSetting(setting);
        updated = true;
    }
%>
<html>
    <head>
        <title><%= "Web Chat Text Settings for "+wgID%></title>
        <meta name="subPageID" content="workgroup-text-settings"/>
        <meta name="extraParams" content="wgID=<%= wgID %>"/>
        <!--<meta name="helpPage" content="edit_form_text.html"/>-->

        <link rel="stylesheet" type="text/css" href="/style/global.css">
        <script>
        function restoreKey(name){
            document.text._key.value = name;
            document.text.submit();
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
                           Web UI Text Settings have been updated successfully
                        </td></tr>
                </tbody>
            </table>
        </div><br/>
      <% } %>

      <!-- Create HTML Code Snippet Table -->
        <table width="100%" class="jive-table" cellpadding="3" cellspacing="0" border="0">
        <tr valign="top">
        <th colspan="3">HTML Code Snippet</th>
        </tr>
        <tr valign="middle">
        <td><b>HTML Code</b><br/>
        <span class="jive-description">Copy this HTML Code wherever you would like to place a "Chat with me" button. This code will
        display the correct presence information for this workgroup by either being online or offline. <i>(You must replace the url to the actual jivelive.jsp page)</i>
    </span>
        </td>
        <td width="60%" style="font-size:11px;">
       <font color="green">&lt;!-- Insert this snippet where you would like the Chat button image to show up --></font><br/>
        &lt;script language="JavaScript" type="text/javascript" src="url to jivelive.jsp">&lt;/script><br/>
        &lt;script>showChatButton('<%= wgID %>');&lt;/script><br/>
        <font color="green">&lt;!-- End Of Spark Fastpath Snippet --></font>
        </td>
        </tr>
        </table>
        <br/><br/>
<%
    // User Input Page
    addToPage("User Input Page", chatSettings.getChatSetting(KeyEnum.user_input_page_title));
    addToPage("User Input Page", chatSettings.getChatSetting(KeyEnum.start_chat_button));

    // Queue
    addToPage("Queue", chatSettings.getChatSetting(KeyEnum.queue_title_text));
    addToPage("Queue", chatSettings.getChatSetting(KeyEnum.queue_description_text));
    addToPage("Queue", chatSettings.getChatSetting(
            org.jivesoftware.openfire.fastpath.settings.chat.KeyEnum.queue_footer_text));
    addToPage("Queue", chatSettings.getChatSetting(KeyEnum.no_agent_text));

    // Chat Room
    addToPage("Chat Room", chatSettings.getChatSetting(KeyEnum.accepted_chat_text));
    addToPage("Chat Room", chatSettings.getChatSetting(KeyEnum.transferred_chat_text));
    addToPage("Chat Room", chatSettings.getChatSetting(KeyEnum.agent_invite_text));
    addToPage("Chat Room", chatSettings.getChatSetting(KeyEnum.agent_ends_chat_text));

    // Transcript Page
    addToPage("Email Transcript Page", chatSettings.getChatSetting(KeyEnum.transcript_text));
    addToPage("Email Transcript Page", chatSettings.getChatSetting(KeyEnum.transcript_sent_text));
    addToPage("Email Transcript Page",
            chatSettings.getChatSetting(KeyEnum.transcript_not_sent_text));

    // Offline Page
    addToPage("No Help Page", chatSettings.getChatSetting(KeyEnum.no_help));
%>

        <!-- Text Settings -->
        <form name="text" action="workgroup-text-settings.jsp" method="post">
        <%
            for(InternalModel model : pageList){
        %>
          <table  class="jive-table"  cellpadding="3" cellspacing="0" border="0" width="100%">
               <tr>
               <th colspan="2"><%= model.getPageName()%></th>
               </tr>

               <%
                  List list = model.getChatSettings();
                  Iterator iter = list.iterator();
                  while(iter.hasNext()){
                      ChatSetting chatSetting = (ChatSetting)iter.next();
               %>
               <tr valign="top">
                    <td  style="border:0px;" width="25%"><b><%= chatSetting.getLabel() %></b><br/>
                    <span class="jive-description"><%= chatSetting.getDescription() %></span>
                    </td>
                    <td rowspan="2" align="left"><textarea cols="60" rows="6" name="<%= chatSetting.getKey() %>"><%= chatSetting.getValue() %></textarea></td>
                    <input type="hidden" name="key" value="<%= chatSetting.getKey() %>" />
               </tr>
               <tr valign="bottom">
                 <td ><input type="submit" name="restore" value="Restore Defaults" onClick="restoreKey('<%=chatSetting.getKey()%>');"></td>
                </tr>
              <% } %>
          </table><br/>
        <% } %>




              <table>
               <tr><td colspan="4">
                <input type="hidden" name="_key" />
                <input type="hidden" name="wgID" value="<%= wgID %>" />
                <input type="submit" name="saveText" value="Update Text Settings" />
            </td>
           </tr>
        </table>
        </form>
    </body>
</html>

<%!
    private void addToPage(String page, ChatSetting chatSetting){
        List list = null;
        for(InternalModel model : pageList){
            if(model.getPageName().equals(page)){
                list = model.getChatSettings();
                list.add(chatSetting);
                return;
            }
        }

        if(list == null){
            InternalModel model = new InternalModel();
            model.setPageName(page);
            model.addChatSetting(chatSetting);
            pageList.add(model);
            return;
        }
    }

    private class InternalModel {
        private String pageName;
        private List<ChatSetting> list = new ArrayList<ChatSetting>();

        public void setPageName(String pageName){
            this.pageName = pageName;
        }

        public String getPageName(){
            return pageName;
        }

        public void addChatSetting(ChatSetting chatSetting){
            list.add(chatSetting);
        }

        public List<ChatSetting> getChatSettings(){
            return list;
        }
    }
%>
