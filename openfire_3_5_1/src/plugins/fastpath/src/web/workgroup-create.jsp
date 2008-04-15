<%--

  -	$RCSfile$

  -	$Revision$

  -	$Date: 2006-08-07 21:12:21 -0700 (Mon, 07 Aug 2006) $

--%>
<%@ page
import="java.util.HashMap,
        java.util.Map,
        org.jivesoftware.xmpp.workgroup.WorkgroupManager,
        org.jivesoftware.xmpp.workgroup.Workgroup,
        org.jivesoftware.util.ParamUtils,
        org.jivesoftware.openfire.XMPPServer,
        org.xmpp.component.ComponentManagerFactory,
        org.jivesoftware.openfire.fastpath.util.WorkgroupUtils"%>

<!-- Define Administration Bean -->
<%
    final XMPPServer xmppServer = XMPPServer.getInstance();
    Map errors = new HashMap();
    // Get a workgroup manager
    WorkgroupManager workgroupManager = WorkgroupManager.getInstance();
    // If the workgroup manager is null, service is down so redirect:
    if (workgroupManager == null) {
        response.sendRedirect("error-serverdown.jsp");
        return;
    }
%>

<% // Get parameters //
    boolean create = request.getParameter("create") != null;
    boolean cancel = request.getParameter("cancel") != null;
    String wgName = ParamUtils.getParameter(request, "wgName");
    String description = ParamUtils.getParameter(request, "description");
    String queueName = ParamUtils.getParameter(request, "queueName");
    String agents = ParamUtils.getParameter(request, "agents");
    int maxChats = ParamUtils.getIntParameter(request, "maxChats", 0);
    int minChats = ParamUtils.getIntParameter(request, "minChats", 0);

    // Handle a cancel
    if (cancel) {
        response.sendRedirect("workgroup-summary.jsp");
        return;
    }

    if (create) {
        errors = WorkgroupUtils.createWorkgroup(wgName, description, agents);
        if (errors.size() == 0) {
            Workgroup workgroup = workgroupManager.getWorkgroup(wgName);
            response.sendRedirect(
                    "workgroup-create-success.jsp?wgID=" + workgroup.getJID().toString());
            return;
        }
    }
%>
<html>
    <head>
        <title>Create Workgroup</title>
        <meta name="pageID" content="workgroup-create"/>
        <!--<meta name="helpPage" content="create_a_workgroup.html"/>-->
        <script>
            function openWin(el) {
                var win = window.open(
                              'user-browser.jsp?formName=f&elName=agents', 'newWin',
                              'width=500,height=550,menubar=yes,location=no,personalbar=no,scrollbars=yes,resize=yes');
            }
        </script>
    </head>
    <body>
    <style type="text/css">
        @import "style/style.css";
    </style>
        <p>
        Use the form below to create a new workgroup in the system.</p>
<%
    if (!errors.isEmpty()) {
%>
        <div class="jive-error">
            <table cellpadding="0" cellspacing="0" border="0">
                <tbody>
                    <tr>
                        <td class="jive-icon">
                            <img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""/>
                        </td>
                        <td class="jive-icon-label">
<%
                            if (errors.get("general") != null) {
%>
                                    Unable to create the workgroup.
<%
                            }
                            else if (errors.get("exists") != null) {
%>
                               The workgroup name is already in use. Please try another.
<%
                            }
                            else if (errors.get("wgName") != null) {
%>
                                Supply a valid name for the workgroup.
<%
                            }
                            else if (errors.get("maxChats") != null) {
%>
                                Invalid maximum number of chat sessions.
<%
                            }
                            else if (errors.get("minChats") != null) {
%>
                                Invalid minimum number of chat sessions.
<%
                            }
                            else if (errors.get("minChatsGreaterThanMax") != null) {
%>
                                Minimum chat sessions can not be greater than maximum.
<%
                            }
%>
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>
        <br>
<%
    }
%>
    <form name="f" action="workgroup-create.jsp" method="post">
        <div>
             <div class="jive-contentBoxHeader">
        Create New Workgroup
        </div>
            <table cellpadding="3" cellspacing="3" border="0"  class="jive-contentBox">

                <tr valign="top">
                    <td width="30%">
                       Workgroup Name: *
                        <br/>
                    </td>
                    <td colspan="2" style="border-right:1px #ccc solid;">
                        <input type="text" name="wgName" size="40" maxlength="45"
                               value="<%= ((wgName != null) ? wgName : "") %>"/>
                        @workgroup.<%= xmppServer.getServerInfo().getXMPPDomain() %><br/><span class="jive-description">e.g. sales, marketing, bizdev, support.</span>
                    </td>
                </tr>
                <tr valign="top">
                    <td>
                        Members:
                    </td>
                    <td width="1%">
                        <textarea name="agents" cols="30" rows="3"><%= ((description != null) ? description : "") %></textarea><br/>
                        <span class="jive-description">Comma delimited list of initial members of the workgroup.</span>
                    </td>
                    <% if (!ComponentManagerFactory.getComponentManager().isExternalMode()) { %>
                    <td nowrap valign="top" style="border-right:1px #ccc solid;">
                        <table>
                            <tr>
                                <td> <a href="#" onclick="openWin(document.f.agents);return false;"
                           title="Click to browse available agents..."> <img src="images/user.gif" border="0" alt=""/></a></td>
                                <td><a href="#" onclick="openWin(document.f.agents);return false;"
                           title="Click to browse available agents...">Browse...</a></td>
                            </tr>
                        </table>
                    </td>
                    <% } %>
                </tr>
                <tr  valign="top">
                    <td>
                       Description:

                    </td>
                    <td colspan="2" width="1%" style="border-right:1px #ccc solid;">
                        <textarea name="description" cols="30"
                                  rows="3"><%= ((description != null) ? description : "") %></textarea> <br/>
                        <span class="jive-description">General description of the workgroup.</span>
                    </td>
                </tr>
            </table>
        </div>

   <span class="jive-description">
    * Required fields
    </span><br/><br/>
        <input type="submit" name="create" value="Create Workgroup"/>
        <input type="submit" name="cancel" value="Cancel"/>
        <input type="hidden" name="queueName" size="40" maxlength="75"
               value="<%= ((queueName != null) ? queueName : "") %>"/>
        <input type="hidden" name="maxChats" size="5" maxlength="10"
               value="<%= ((maxChats > 0) ? "" + maxChats : "") %>"/>
        <input type="hidden" name="minChats" size="5" maxlength="10"
               value="<%= ((minChats > 0) ? "" + minChats : "") %>"/>
    </form>


    <script language="JavaScript" type="text/javascript">
        document.f.wgName.focus();
    </script>
</body>
</html>
