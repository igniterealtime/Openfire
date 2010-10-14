<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%--
  -	$RCSfile$
  -	$Revision: 25955 $
  -	$Date: 2006-01-18 16:24:51 -0800 (Wed, 18 Jan 2006) $
--%>

<%@ page import="org.jivesoftware.xmpp.workgroup.WorkgroupManager,
                 org.jivesoftware.util.*,
                 java.util.*,
                 java.net.URLEncoder,
                 java.io.UnsupportedEncodingException,
                 org.jivesoftware.xmpp.workgroup.Workgroup,
                 org.xmpp.packet.JID,
                 org.jivesoftware.xmpp.workgroup.utils.ModelUtil, org.jivesoftware.openfire.fastpath.macros.MacroGroup, org.jivesoftware.openfire.fastpath.macros.WorkgroupMacros"
%>


<% // Get parameters
    String wgID = ParamUtils.getParameter(request, "wgID");
    boolean delete = request.getParameter("delete") != null;

    String success = request.getParameter("success");
    String failure = request.getParameter("failure");


    Map errors = new HashMap();

    WorkgroupManager workgroupManager = WorkgroupManager.getInstance();
    Workgroup workgroup = workgroupManager.getWorkgroup(new JID(wgID));
    WorkgroupMacros workgroupMacros = WorkgroupMacros.getInstance();
    MacroGroup rootGroup = workgroupMacros.getMacroGroup(workgroup);


    if (delete) {
        String macroGroup = request.getParameter("macroGroupTitle");
        String parentGroup = request.getParameter("parentGroupTitle");
        MacroGroup parent = workgroupMacros.getMacroGroup(workgroup, parentGroup);
        MacroGroup group = workgroupMacros.getMacroGroup(workgroup, macroGroup);
        if (group != null && parent != null) {
            parent.removeMacroGroup(group);
        }
        success = macroGroup + " has been deleted.";
        workgroupMacros.saveMacros(workgroup);
    }
%>

<html>
    <head>
        <title><%= "Canned Responses for "+wgID%></title>
        <meta name="subPageID" content="workgroup-macros"/>
        <meta name="extraParams" content="<%= "wgID="+wgID %>"/>
        <!--<meta name="helpPage" content="add_canned_responses_to_a_workgroup.html"/>-->
    </head>
    <body>

<p>
Below are the Canned Response Categories to create your own global canned responses. These global canned responses
will be available to all agents within the "<%=wgID%>" workgroup to use in their chat sessions.
</p>

<%  if (ModelUtil.hasLength(failure)) { %>

    <p class="jive-error-text">
    <%= failure%>
    </p>

<%  } else if (ModelUtil.hasLength(success)) { %>

    <div class="jive-success">
            <table cellpadding="0" cellspacing="0" border="0">

            <tbody>
                <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
                <td class="jive-icon-label">
                <%= success%>
                </td></tr>
            </tbody>
            </table>
            </div><br>
<% } %>

<table class="jive-table" cellspacing="0" cellpadding="0"  width="100%">
<th colspan="2">Manage Canned Categories</th>
  <tr>
    <td width="1%">
        <table cellspacing="0" cellpadding="0">
        <tr>
        <td width="1%"><img src="images/folder-16x16.gif"></td><td nowrap><a href="workgroup-view-responses.jsp?macroGroupTitle=<%=URLEncoder.encode(rootGroup.getTitle(), "UTF-8") %>&wgID=<%=wgID%>"><%= rootGroup.getTitle()%></a></td>
        </tr>
        </table>
    </td>
    <td>[<a href="workgroup-add-category.jsp?macroGroupTitle=<%= URLEncoder.encode(rootGroup.getTitle(), "UTF-8")%>&wgID=<%= wgID%>">Add Category</a> | <a href="workgroup-add-response.jsp?macroGroupTitle=<%= URLEncoder.encode(rootGroup.getTitle(), "UTF-8")%>&wgID=<%=wgID%>">Add Response</a>]</td>
    </tr>
<%
    StringBuilder builder = new StringBuilder();

    List<MacroGroup> macroGroups = rootGroup.getMacroGroups();
    for(MacroGroup groups : macroGroups){
        writeMacroGroup(groups, rootGroup, builder, 6, wgID);
    }
%>
<%= builder.toString() %>
</table>
</body>
</html>

<%!
    private void writeMacroGroup(MacroGroup group, MacroGroup parent, StringBuilder builder, int space, String wgID){
        builder.append("<tr>");

        builder.append("<td width=\"1%\"><table cellspacing=\"0\" cellpadding=\"0\"><tr><td width=\"1%\" nowrap>");

        String spaceString = "";
        for(int i=0; i<space; i++){
            spaceString += "&nbsp;";
        }

        builder.append(spaceString);
        try {
            builder.append("<img src=\"images/folder-16x16.gif\"></td><td nowrap><a href=\"workgroup-view-responses.jsp?macroGroupTitle="+URLEncoder.encode(group.getTitle(), "UTF-8")+"&wgID="+wgID+"\">"+group.getTitle()+"</a></td>");
            builder.append("</tr></table>");

            builder.append("</td> <td>[<a href=\"workgroup-add-category.jsp?macroGroupTitle="+URLEncoder.encode(group.getTitle(), "UTF-8")+"&wgID="+wgID+"\">Add Category</a> | <a href=\"workgroup-add-response.jsp?macroGroupTitle="+URLEncoder.encode(group.getTitle(), "UTF-8")+"&wgID="+wgID+"\">Add Response</a> | <a href=\"workgroup-add-category.jsp?edit=true&macroGroupTitle="+URLEncoder.encode(group.getTitle(), "UTF-8")+"&wgID="+wgID+"\">Edit Category</a> | <a href=\"workgroup-macros.jsp?parentGroupTitle="+URLEncoder.encode(parent.getTitle(), "UTF-8")+"&macroGroupTitle="+URLEncoder.encode(group.getTitle(), "UTF-8")+"&wgID="+wgID+"&delete=true\">Delete</a>]</td></tr>");
        }
        catch (UnsupportedEncodingException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        space += 6;
        for(MacroGroup g : group.getMacroGroups()){
            writeMacroGroup(g, group, builder, space, wgID);
        }


    }
%>
