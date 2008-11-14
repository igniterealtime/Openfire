<%--
  -	$RCSfile$
  -	$Revision: 22565 $
  -	$Date: 2005-10-10 17:20:21 -0700 (Mon, 10 Oct 2005) $
--%>
<%@ page import="org.jivesoftware.xmpp.workgroup.Workgroup,
                 org.jivesoftware.xmpp.workgroup.WorkgroupAdminManager,
                 org.jivesoftware.xmpp.workgroup.WorkgroupManager,
                 org.jivesoftware.util.ParamUtils,
                 org.xmpp.packet.JID"
        %>
<%@ page import="org.jivesoftware.xmpp.workgroup.utils.ModelUtil, org.jivesoftware.openfire.fastpath.settings.chat.ChatSettingsManager"%>
<%
    // Get parameters
    String wgID = ParamUtils.getParameter(request, "wgID");
    WorkgroupAdminManager workgroupAdminManager = new WorkgroupAdminManager();
    workgroupAdminManager.init(pageContext);

    boolean updated = ParamUtils.getBooleanParameter(request, "updated", false);

    JID workgroupJID = new JID(wgID);

    final ChatSettingsManager chatSettingsManager = ChatSettingsManager.getInstance();
    Workgroup workgroup = WorkgroupManager.getInstance().getWorkgroup(workgroupJID);
    String incomingMessage = workgroup.getProperties().getProperty("incomingSound");
    String outgoingMessage = workgroup.getProperties().getProperty("outgoingSound");

%>
<html>
    <head>
        <title><%= "Sound Settings For " + wgID%></title>
        <meta name="subPageID" content="workgroup-sound-settings"/>
        <meta name="extraParams" content="<%= "wgID="+wgID %>"/>
        <!--<meta name="helpPage" content="index.html"/>-->

        <link rel="stylesheet" type="text/css" href="/style/global.css">
    </head>
    <body>

      <%
          if (updated) {
      %>
       <div class="jive-success">
            <table cellpadding="0" cellspacing="0" border="0">
                <tbody>
                    <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16"
                                                   border="0"></td>
                        <td class="jive-icon-label">
                           Sounds have been updated successfully
                        </td></tr>
                </tbody>
            </table>
        </div><br/>
      <% } %>

        <br/><br/>

  <form name="f" action="uploadSounds.jsp" enctype="multipart/form-data" method="post">
        <input type="hidden" name="wgID" value="<%= wgID %>"/>
        <!-- Create Image Table -->
        <table width="600" class="jive-table" cellpadding="3" cellspacing="0" border="0">
        <tr>
            <th colspan="3">Sound Configuration</th>
        </tr>

        <tr>
          <td>Incoming Message Sound:</td>
             <td width="1%" nowrap>
                  <input type="file" name="incomingSound" size="40"/>
                </td>
              <% if(ModelUtil.hasLength(incomingMessage)){ %>
              <td><a href="getsound?workgroup=<%= wgID %>&action=incoming">Play Sound</a></td>
              <% } %>
        </tr>
        <tr>
            <td>
              Message Sent Sound:
            </td>
                <td width="1%" nowrap>
                    <input type="file" name="outgoingSound" size="40"/>
                </td>
            <% if(ModelUtil.hasLength(outgoingMessage)){ %>
              <td><a href="getsound?workgroup=<%= wgID %>&action=outgoing">Play Sound</a></td>
              <% } %>
         </tr>
                    <tr>
                        <td colspan="2" align="left">
                            <input type="submit" value="Update Sounds">
                        </td><td>&nbsp;</td>
                    </tr>
                </table>
    </form>
    </body>
</html>
<%
    session.setAttribute("workgroup", wgID);
%>