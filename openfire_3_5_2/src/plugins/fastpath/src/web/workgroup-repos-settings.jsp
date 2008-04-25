<%@ page
    import="org.jivesoftware.xmpp.workgroup.WorkgroupManager,
                 org.jivesoftware.xmpp.workgroup.Workgroup,
                 org.xmpp.packet.JID,

                 org.jivesoftware.xmpp.workgroup.utils.ModelUtil,
                 java.net.URL,
                 java.net.MalformedURLException"%>

<%!
      boolean success;
%>
<%
    final String wgID = request.getParameter("wgID");
    final WorkgroupManager workgroupManager = WorkgroupManager.getInstance();
    Workgroup workgroup = workgroupManager.getWorkgroup(new JID(wgID));

    String submit = request.getParameter("save");
    boolean hasSubmitted = ModelUtil.hasLength(submit);

    String forum = request.getParameter("forum");
    String kb = request.getParameter("kb");

    if(ModelUtil.hasLength(forum) && hasSubmitted){
        try {
            URL url = new URL(forum);
            workgroup.getProperties().setProperty("forums", forum);
            success = true;
        }
        catch (MalformedURLException e) {
            // Bad protocol
        }

    }
    else if(hasSubmitted) {
        workgroup.getProperties().deleteProperty("forums");
        success = true;
    }

    if(ModelUtil.hasLength(kb) && hasSubmitted){
        try {
            URL url = new URL(kb);
            workgroup.getProperties().setProperty("kb", kb);
            success = true;
        }
        catch (MalformedURLException e) {
           // Bad protocol
        }
    }
    else if(hasSubmitted){
        workgroup.getProperties().deleteProperty("kb");
        success = true;
    }

%>



<html>
    <head>
        <title><%= "Search Settings for "+ wgID%></title>
        <meta name="subPageID" content="workgroup-repos-settings"/>
        <meta name="extraParams" content="wgID=<%= wgID %>"/>
        <!--<meta name="helpPage" content="specify_search_settings_for_workgroup.html"/>-->
    </head>
    <body>
<%

    String kbSetting = workgroup.getProperties().getProperty("kb");
    if(kbSetting == null){
        kbSetting = "";
    }

    String forumSetting = workgroup.getProperties().getProperty("forums");
    if(forumSetting == null){
        forumSetting = "";
    }
%>
    Use the form below to set the Jive Knowledge Base and/or Jive Forums you are using for this workgroup.
    <br/><br/>
<% if (hasSubmitted && success){ %>
            <div class="jive-success">
                <table cellpadding="0" cellspacing="0" border="0">
                    <tbody>
                        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16"
                                                       border="0"></td>
                            <td class="jive-icon-label">
                                Repository Settings have been updated.
                            </td></tr>
                    </tbody>
                </table>
            </div>
 <% } else if(hasSubmitted && !success) { %>

        <p class="jive-error-text">
            An error occured. Please verify that you have filled out all required fields correctly and try again.
        </p>
<% } %>
    <p>
    <form action="workgroup-repos-settings.jsp" method="post" name="f">
        <fieldset><legend>Jive Repositories</legend>
            <div>
                <table class="box" cellpadding="3" cellspacing="0" border="0">
                    <tr>
                        <td width="30%" >
                            <b>Jive Forum Document Root:</b><br/><span class="jive-description">The document root of your Jive Forums installation (ex. http://www.jivesoftware.com/forums). </span>
                        </td>
                        <td width="70%">
                            <input type="text" name="forum" value="<%= forumSetting%>" size="40" maxlength="150">
                        </td>
                    </tr>
                    <tr>
                        <td width="30%">
                            <b>Jive Knowledge Base Document Root:</b><br/><span class="jive-description">The document root of your Jive Knowledge Base installation (ex. http://www.jivesoftware.com/kb)</span>
                        </td>
                        <td width="70%">
                            <input type="text" name="kb" value="<%= kbSetting %>" size="40" maxlength="150">
                        </td>
                    </tr>
                    <input type="hidden" name="wgID" value="<%= wgID%>"/>
                    <%-- spacer --%>
                    <tr>
                        <td colspan="2">
                            <input type="submit" name="save" value="Save Changes">
                        </td>
                    </tr>
                </table>
            </div>
        </fieldset>
    </form>
    </body>
</html>