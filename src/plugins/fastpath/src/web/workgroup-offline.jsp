<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<% try { %>
<%@ page import=" org.jivesoftware.xmpp.workgroup.utils.ModelUtil,
                 org.jivesoftware.xmpp.workgroup.WorkgroupManager,
                 org.jivesoftware.xmpp.workgroup.Workgroup,
                 org.xmpp.packet.JID,
                 org.jivesoftware.openfire.fastpath.settings.offline.OfflineSettingsManager,
                 org.jivesoftware.openfire.fastpath.settings.offline.OfflineSettings,
                 org.jivesoftware.openfire.fastpath.settings.offline.OfflineSettingsNotFound,
                 org.jivesoftware.openfire.fastpath.util.WorkgroupUtils"
%><%@ page import="org.jivesoftware.util.JiveGlobals" %>
 <%
     String wgID = request.getParameter("wgID");
     final WorkgroupManager workgroupManager = WorkgroupManager.getInstance();
     Workgroup workgroup = workgroupManager.getWorkgroup(new JID(wgID));

     OfflineSettingsManager offlineManager = new OfflineSettingsManager();
     String redirectValue = request.getParameter("redirectToPage");
     String statusMessage = "";

     OfflineSettings offlineSettings = null;

     boolean emailConfigured = false;

     String property = JiveGlobals.getProperty("mail.configured");
     if (ModelUtil.hasLength(property)) {
         emailConfigured = true;
     }

     boolean delete = request.getParameter("delete") != null;
     boolean save = request.getParameter("save") != null && !delete;

     if (save){
         String emailAddress = request.getParameter("email");
         String subject = request.getParameter("subject");
         String header = request.getParameter("headerField");
         offlineSettings = offlineManager.saveOfflineSettings(workgroup, redirectValue, emailAddress, subject, header);
         if (offlineSettings != null) {
             statusMessage = "Offline settings have been saved.";
         }
     }
     else if(delete){
        statusMessage = "Offline settings have been deleted.";
        offlineSettings = offlineManager.saveOfflineSettings(workgroup, redirectValue, "", "", "");
     }
     else {
         try {
             offlineSettings = offlineManager.getOfflineSettings(workgroup);
         }
         catch (OfflineSettingsNotFound offlineSettingsNotFound) {
             offlineSettings = new OfflineSettings();
         }

     }
 %>
<html>
    <head>
        <title><%= "Offline Settings for "+wgID%></title>
        <meta name="subPageID" content="workgroup-offline"/>
        <meta name="extraParams" content="<%= "wgID="+wgID %>"/>
        <!--<meta name="helpPage" content="set_an_offline_policy_for_a_workgroup.html"/>-->

        <script>
        function saveOfflineSettings(){
             var todo = document.offline.todo;
             if(todo[0].checked){
                 var url = document.offline.redirectToPage.value;
                 if(!Jtrim(url)){
                   alert("Please specify the URL to forward to.");
                   document.offline.redirectToPage.focus();
                   return;
                 }
                 document.offline.email.value = "";
                 document.offline.subject.value = "";
                 document.offline.headerField.value = "";

                 document.offline.submit();
             }
             else if(todo[1].checked){
               var email = document.offline.email.value;
               var subject = document.offline.subject.value;
               var message = document.offline.headerField.value;
               document.offline.redirectToPage.value = '';

               if(!Jtrim(email) || !Jtrim(subject) || !Jtrim(message)){
                 alert("All fields are required.");
                 return;
               }
                document.offline.submit();
             }
        }



         function Jtrim(st) {
             var len = st.length;
             var begin = 0, end = len - 1;
             while (st.charAt(begin) == " " && begin < len) {
                 begin++;
             }
             while (st.charAt(end) == " " && end > begin) {
                 end--;
             }
             return st.substring(begin, end + 1);
         }
        </script>
    </head>
    <body>
    Specify action to take when this workgroup has no available agents to take incoming chat requests.
    <% if(statusMessage != null && !statusMessage.equals("")) { %>
    <div class="success">
        <%= statusMessage %>
    </div>
    <% } %>

      <% if(!emailConfigured){ %>
            <div class="error">
                Email form will not be displayed until you configure your <a href="../../system-email.jsp">email settings</a>.
            </div>
    <% } %>

    <div id="offline_message"><%= statusMessage %></div>
    <p/>
    <form action="workgroup-offline.jsp" method="get" name="offline">
    <input type="hidden" name="wgID" value="<%= wgID %>" />
    <div>
        <div class="jive-contentBoxHeader">
        Offline Workgroup Action
        </div>
        <table width="100%" cellpadding="3" cellspacing="0" border="0" class="jive-contentBox">
                <tr valign="top">
                <% String checked = offlineSettings.redirects() ? "checked" : ""; %>
                            <td width="1%">
                                <input type="radio" name="todo" value="redirectToPage" <%= checked %> />
                            </td>
                            <td nowrap><b>Redirect To Web Page</b>
                               </td>
                            <td class="c2">
                                <input type="text" name="redirectToPage" size="40" value="<%= offlineSettings.getRedirectURL() %>" /><br/>
                                 <span class="jive-description">e.g. http://www.jivesoftware.com/contact.html</span>
                            </td>
                </tr>
                <tr>
                    <td nowrap width="1%">
                         <input type="radio" name="todo" value="showEmailPage" <%=!offlineSettings.redirects() ? "checked" :"" %>/>
                         <td><b>Display Email Form</b></td>
                     </td>
                     <td>&nbsp;</td>
                </tr>
                <!-- Email Address -->
                <tr valign="top">
                    <td>&nbsp;</td>
                    <td>Email Address:</td>
                    <td>
                        <input type="text" size="40" name="email" value="<%= offlineSettings.getEmailAddress() %>" /><br/>
                        <span class="jive-description">Email address to send all offline messages to.</span>
                    </td>
                </tr>
                <!-- End of Email Address -->
                <!-- Subject Line -->
                 <tr valign="top">
                    <td>&nbsp;</td>
                    <td>Subject:</td>
                    <td>
                        <input type="text" size="40" name="subject" value="<%= offlineSettings.getSubject() %>"/><br/>
                        <span class="jive-description">The subject of all offline email messages.</span>
                    </td>
                </tr>
                <!--  End Of Subject Line -->
                <tr valign="top">
                     <td>&nbsp;</td>
                    <td>Offline Text:</td>
                    <td>
                        <textarea name="headerField" cols="40" rows="5"><%= offlineSettings.getOfflineText()  %></textarea><br/>
                        <span class="jive-description">Text to display to the user in the email form.</span>
                    </td>
                </tr>
                    <input type="hidden" name="save" value="save">
                 <tr>
                </tr>
            <%-- spacer --%>
            </table>
            <table><tr>
                 <td colspan="1"> <input type="button" name="save" value="Save Changes" onclick="return saveOfflineSettings();" /></td>
                <td colspan="1"> <input type="submit" name="delete" value="Delete Changes" /></td>
            </tr></table>
       </div>
    </form>

</body>
</html>

<% } catch(Exception ex){ex.printStackTrace();} %>