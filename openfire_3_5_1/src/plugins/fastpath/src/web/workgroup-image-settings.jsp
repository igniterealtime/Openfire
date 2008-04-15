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
                 org.jivesoftware.openfire.fastpath.settings.chat.ChatSettings,
                 org.jivesoftware.openfire.fastpath.settings.chat.ChatSettingsManager,
                 org.jivesoftware.openfire.fastpath.settings.chat.ChatSetting"
%>
<%
    // Get parameters
    String wgID = ParamUtils.getParameter(request, "wgID");
    WorkgroupAdminManager workgroupAdminManager = new WorkgroupAdminManager();
    workgroupAdminManager.init(pageContext);
    boolean updated = ParamUtils.getBooleanParameter(request, "updated", false);

    JID workgroupJID = new JID(wgID);

    final ChatSettingsManager chatSettingsManager = ChatSettingsManager.getInstance();
    Workgroup workgroup = WorkgroupManager.getInstance().getWorkgroup(workgroupJID);
    ChatSettings chatSettings = chatSettingsManager.getChatSettings(workgroup);

%>
<html>
    <head>
        <title><%= "WebChat Images for "+wgID%></title>
        <meta name="subPageID" content="workgroup-image-settings"/>
        <meta name="extraParams" content="<%= "wgID="+wgID %>"/>
        <!--<meta name="helpPage" content="add_or_change_form_images.html"/>-->

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
                           Web UI images have been updated successfully
                        </td></tr>
                </tbody>
            </table>
        </div><br/>
      <% } %>



        <br/><br/>

  <form name="f" action="upload.jsp" enctype="multipart/form-data" method="post">
        <input type="hidden" name="wgID" value="<%= wgID %>" />
        <!-- Create Image Table -->
        <table width="75%" class="jive-table" cellpadding="3" cellspacing="0" border="0">
        <tr>
            <th colspan="3">Web UI Image Configuration</th>
        </tr>
                    <jsp:useBean id="images" class="java.util.LinkedHashMap"/>
                    <%
                        Iterator imagesIter = chatSettings
                                .getChatSettingsByType(ChatSettings.SettingType.image_settings)
                                .iterator();
                        while (imagesIter.hasNext()) {
                            ChatSetting setting = (ChatSetting)imagesIter.next();
                            String key = setting.getKey().toString();
                            String label = setting.getLabel();
                            String description = setting.getDescription();
                            if (description == null) {
                                continue;
                            }
                    %>

      <tr valign="top">
        <td bgcolor="#FFFFFF" width="40%"><b><%= label %>:</b><br/><span class="jive-description"><%= description %></span></td>
        <td bgcolor="#FFFFFF">
            <table cellspacing="3" cellpadding="2" border="0" width="100%">
              <tr>
                <td width="1%" nowrap>
                  <input type="file" name="<%= key %>" onchange="changeImage(document.f.<%=key%>.value, document.f.<%= key %>image);" size="40"/>
                </td>

              </tr>
              <tr>

              <td>
                  <img name="<%= key %>image" src="getimage?imageName=<%= key %>"/>
                </td>

              </tr>
            </table>
        </td>
      </tr>

     <% } %>
                    <tr>
                        <td colspan="2" align="left">
                            <input type="submit" value="Update Images">
                        </td>
                    </tr>
                </table>
    </form>
    </body>
</html>
<%
    session.setAttribute("workgroup", wgID);
%>