<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<% try { %>
<%@ page import="org.jivesoftware.xmpp.workgroup.utils.ModelUtil,
                 org.jivesoftware.xmpp.workgroup.WorkgroupManager,
                 org.jivesoftware.xmpp.workgroup.Workgroup,
                 org.xmpp.packet.JID,
                 org.jivesoftware.xmpp.workgroup.DbProperties"
                 errorPage="workgroup-error.jsp"%><%@ page import="org.jivesoftware.util.JiveGlobals" %><%@ page import="org.jivesoftware.util.Log" %>
 <%
     String wgID = request.getParameter("wgID");
     final WorkgroupManager workgroupManager = WorkgroupManager.getInstance();
     Workgroup workgroup = workgroupManager.getWorkgroup(new JID(wgID));

     boolean delete = request.getParameter("removeChanges") != null;
     boolean save = request.getParameter("save") != null && !delete;

     boolean emailConfigured = false;

     String property = JiveGlobals.getProperty("mail.configured");
     if (ModelUtil.hasLength(property)) {
        emailConfigured = true;
     }

     DbProperties props = workgroup.getProperties();
     String context = "jive.transcript";
     String from = "";
     String subject = "";
     String message = "";
     String fromEmail = "";
     if (save) {
         from = request.getParameter("from");
         fromEmail = request.getParameter("fromEmail");
         subject = request.getParameter("subject");
         message = request.getParameter("message");
         if (ModelUtil.hasLength(from) && ModelUtil.hasLength(subject) && ModelUtil.hasLength(message) && ModelUtil.hasLength(fromEmail)) {
             props.setProperty(context + ".from", from);
             props.setProperty(context + ".fromEmail", fromEmail);
             props.setProperty(context + ".subject", subject);
             props.setProperty(context + ".message", message);
         }
     }
     else if(delete){
        props.deleteProperty(context + ".from");
        props.deleteProperty(context + ".fromEmail");
        props.deleteProperty(context + ".subject");
        props.deleteProperty(context + ".message");
         System.out.println("DELTED");
     }
     else {
         from = props.getProperty(context + ".from");
         subject = props.getProperty(context + ".subject");
         message = props.getProperty(context + ".message");
         fromEmail = props.getProperty(context + ".fromEmail");
         if (from == null) {
             from = "";
         }

         if (subject == null) {
             subject = "";
         }

         if (message == null) {
             message = "";
         }

         if (fromEmail == null) {
             fromEmail = "";
         }
     }
 %>
<html>
    <head>
        <title><%= "Transcript Settings for "+wgID%></title>
        <meta name="subPageID" content="workgroup-transcript-config"/>
        <meta name="extraParams" content="wgID=<%= wgID %>"/>
        <!--<meta name="helpPage" content="set_header_and_message_text_for_transcript_emails.html"/>-->
    </head>
    <body>

    Specify specific configuration when a user requests a transcript from the system.
    <br/><br/>

    <% if(save) { %>
   <div class="success">
    Transcript Settings have been updated
    </div>
    <%}%>


       <% if(!emailConfigured){ %>
            <div class="error">
                Transcripts cannot be sent until you configure your <a href="../../system-email.jsp">email settings</a>.
            </div>
    <% } %>

    <p/>
    <form action="workgroup-transcript-config.jsp" method="get" name="offline">
    <input type="hidden" name="wgID" value="<%= wgID %>" />
    <div>
        <div class="jive-contentBoxHeader">
        Conversation Transcript Configuration
        </div>
        <table width="100%" cellpadding="3" cellspacing="3" border="0" class="jive-contentBox">
                <tr valign="top">
                    <td>From:</td>
                    <td>
                        <input type="text" size="40" name="from" value="<%= from %>" /><br/><span class="jive-description">Specify who the transcript is from, such as ACME Company.</span>
                    </td>
                </tr>

                  <tr valign="top">
                    <td>Email Address:</td>
                    <td>
                        <input type="text" size="40" name="fromEmail" value="<%= fromEmail %>" /><br/><span class="jive-description">Specify the email address the message will be from. Ex. support@acme.com</span>
                    </td>
                </tr>

               <tr valign="top">

                    <td>Subject:</td>
                    <td>
                        <input type="text" size="40" name="subject" value="<%= subject %>"/><br/><span class="jive-description">The subject that will appear to the user.</span>
                    </td>
                </tr>
                <!--  End Of Subject Line -->
                 <tr valign="top">

                    <td>Message:</td>
                    <td>
                        <textarea name="message" cols="40" rows="3"><%= message %></textarea><br/><span class="jive-description">Text to prepend to the transcript being sent.</span>
                    </td>
                </tr>
                    <input type="hidden" name="save" value="save">
                 <tr>
                <td colspan="1"> <input type="button" name="save" value="Save Changes" onclick="return saveSettings();" /></td>
                <td colspan="1"> <input type="submit" name="removeChanges" value="Remove Changes"  /></td>
                <td>&nbsp;</td>
                </tr>
            <%-- spacer --%>
            </table>
       </div>
       </form>
       <script>
       function saveSettings(){
            var from = document.offline.from.value;
            var subject = document.offline.subject.value;
            var message = document.offline.message.value;

            if(!Jtrim(from) || !Jtrim(subject) || !Jtrim(message)){
                alert("All fields are required.");
                document.offline.from.focus();
                return;
            }
               document.offline.submit();
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

</body>
</html>

<% } catch(Exception ex){ex.printStackTrace();} %>