<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date: 2006-08-07 21:12:21 -0700 (Mon, 07 Aug 2006) $
--%>
<%@ page
        import="org.jivesoftware.xmpp.workgroup.RequestQueue,
                org.jivesoftware.xmpp.workgroup.Workgroup,
                org.jivesoftware.util.ParamUtils,
                org.jivesoftware.xmpp.workgroup.utils.ModelUtil,
                org.jivesoftware.xmpp.workgroup.WorkgroupManager,
                org.jivesoftware.openfire.fastpath.util.WorkgroupUtils,
                java.util.HashMap,
                java.util.Map,
                org.xmpp.packet.JID,
                org.xmpp.component.ComponentManagerFactory"
%>
<% // Get parameters //
    String wgID = ParamUtils.getParameter(request, "wgID");
    boolean createQueue = request.getParameter("createQueue") != null;
    String name = ParamUtils.getParameter(request, "name");
    String description = ParamUtils.getParameter(request, "description");
    String agents = ParamUtils.getParameter(request, "agents");

    // Load the workgroup

    final WorkgroupManager workgroupManager = WorkgroupManager.getInstance();
    Workgroup workgroup = workgroupManager.getWorkgroup(new JID(wgID));
    Map errors = new HashMap();
    if (createQueue) {
        if (name == null) {
            errors.put("name", "");
        }
        if (errors.size() == 0) {
            RequestQueue queue = workgroup.createRequestQueue(name);
            if (description != null) {
                queue.setDescription(description);
            }

            if (ModelUtil.hasLength(agents)) {
                WorkgroupUtils.addAgents(queue, agents);
            }
            response.sendRedirect("workgroup-queues.jsp?wgID=" + wgID + "&queueaddsuccess=true");
            return;
        }


    }
%>
<html>
    <head>
        <title>Workgroup Queue Creation</title>
        <meta name="subPageID" content="workgroup-queues"/>
        <meta name="extraParams" content="<%= "wgID="+wgID %>"/>
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
    <p>
        Use the form below to create a new queue in the workgroup <b><%= workgroup.getJID() %></b>.
    </p>
    <form name="f" action="workgroup-queue-create.jsp" method="post">
        <input type="hidden" name="wgID" value="<%= wgID %>">
            <div class="jive-contentBoxHeader">
        Create New Queue
        </div>
           <table width="100%"  class="jive-contentBox" cellpadding="3" cellspacing="3" border="0">

                <tr valign="top">
                    <td width="1%">
                    Workgroup
                    </td>
                    <td colspan="2">
                        <%= workgroup.getJID() %>
                    </td>
                </tr>
                <tr valign="top">
                    <td width="1%" nowrap>
                        Name Of Queue: *
<%
    if (errors.get("name") != null) {
%>
                            <span class="jive-error-text">
                            <br>
                            Please enter a valid name. </span>
<%
    }
%>
                    </td>
                    <td colspan="2">
                        <input type="text" name="name" size="30" value="<%= ((name != null) ? name : "") %>">
                        <br/><span class="jive-description">Specify a name for the queue. (ex. product1)</span>
                    </td>
                </tr>
                  <tr valign="top">
                    <td width="1%" nowrap>
                       Members:
                    </td>
                    <td width="1%">
                        <textarea name="agents" cols="30" rows="3"><%= ((description != null) ? description : "") %></textarea>
                        <span class="jive-description">
                        <br/>Comma delimited list of initial members. ex. bob,mary,suzy </span>
                    </td>
                    <% if (!ComponentManagerFactory.getComponentManager().isExternalMode()) { %>
                    <td nowrap valign="top">
                        <table>
                            <tr>
                                <td> <a href="#" onclick="openWin(document.f.agents);return false;"
                                        title="Click to browse available agents..."> <img src="images/user.gif" border="0"/></a></td>
                                <td><a href="#" onclick="openWin(document.f.agents);return false;"
                                       title="Click to browse available agents...">Browse Agents</a></td>
                            </tr>
                        </table>
                    </td>
                    <% } %>
                </tr>
                <tr valign="top">
                    <td width="1%" nowrap>
                        Description:
                    </td>
                    <td>
                        <textarea name="description" cols="30"
                                  rows="5"><%= ((description != null) ? description : "") %></textarea>
                                <br/><span class="jive-description">Specify a description for the queue.</span>
                    </td>
                </tr>
            </table>

    <p>
            * Required fields
    </p>
        <input type="submit" name="createQueue" value="Create Queue">
    </form>
  </body>
  </html>
