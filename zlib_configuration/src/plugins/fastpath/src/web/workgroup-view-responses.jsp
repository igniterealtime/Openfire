
<%--
  -	$RCSfile$
  -	$Revision: 22565 $
  -	$Date: 2005-10-10 17:20:21 -0700 (Mon, 10 Oct 2005) $
--%>

<%@ page import="org.jivesoftware.xmpp.workgroup.WorkgroupManager,
                 org.jivesoftware.util.*,
                 java.util.*,
                 java.net.URLEncoder,
                 java.net.URLDecoder,
                 org.jivesoftware.xmpp.workgroup.Workgroup,
                 org.xmpp.packet.JID, org.jivesoftware.openfire.fastpath.macros.WorkgroupMacros, org.jivesoftware.openfire.fastpath.macros.MacroGroup, org.jivesoftware.openfire.fastpath.macros.Macro"
%>



<% // Get parameters
    String wgID = ParamUtils.getParameter(request, "wgID");
    boolean add = request.getParameter("add") != null;
    boolean delete = request.getParameter("delete") != null;

    Map errors = new HashMap();

    WorkgroupManager workgroupManager = WorkgroupManager.getInstance();
    Workgroup workgroup = workgroupManager.getWorkgroup(new JID(wgID));
    WorkgroupMacros workgroupMacros = WorkgroupMacros.getInstance();

    String groupTitle = URLDecoder
            .decode(ParamUtils.getParameter(request, "macroGroupTitle"), "UTF-8");


    MacroGroup group = workgroupMacros.getMacroGroup(workgroup, groupTitle);
    if (delete) {
        int entry = ParamUtils.getIntParameter(request, "entry", -1);
        if (entry != -1) {
            Macro macro = group.getMacro(entry);
            if (macro != null) {
                group.removeMacro(macro);
            }
        }
    }


%>



<html>
    <head>
        <title>Canned Response List</title>
        <meta name="subPageID" content="workgroup-macros"/>
        <meta name="extraParams" content="wgID=<%= wgID %>"/>
    </head>
    <body>

<p>
Viewing all responses in <b>"<%= groupTitle %>"</b>
</p>

<form action="workgroup-add-response.jsp" method="post">
<input type="hidden" name="wgID" value="<%= wgID%>" />
<input type="hidden" name="macroGroupTitle" value="<%= URLEncoder.encode(groupTitle, "UTF-8") %>" />
<input type="hidden" name="add" value="true" />
<table class="jive-table" cellspacing="0" cellpadding="0"  width="100%">
<th colspan="1">Title</th><th>Type</th><th>Options</th>
<%
    int count = 0;
    for(Macro macro : group.getMacros()){%>
        <tr>
        <td><%= macro.getTitle()%></td>
        <td><%= "Text"%></td>
        <td align="center"><a href="workgroup-add-response.jsp?edit=true&wgID=<%=wgID%>&macroGroupTitle=<%= URLEncoder.encode(groupTitle, "UTF-8")%>&entry=<%=count%>"><img src="images/edit-16x16.gif" border="0"></a>&nbsp;<a href="workgroup-view-responses.jsp?delete=true&wgID=<%=wgID%>&macroGroupTitle=<%= URLEncoder.encode(groupTitle, "UTF-8")%>&entry=<%=count%>"><img src="images/delete-16x16.gif" border="0"></a></td>
        </tr>

<%
        count++;
    }
%>
</table>
</form>
</body>
</html>

