
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
                 org.xmpp.packet.JID,
                 org.jivesoftware.xmpp.workgroup.utils.ModelUtil,
                 org.jivesoftware.openfire.fastpath.macros.MacroGroup,
                 org.jivesoftware.openfire.fastpath.macros.WorkgroupMacros"
%>

<% // Get parameters
    String wgID = ParamUtils.getParameter(request, "wgID");
    boolean add = request.getParameter("add") != null;
    boolean delete = request.getParameter("remove") != null;
    boolean edit = request.getParameter("edit") != null;

    Map errors = new HashMap();

    WorkgroupManager workgroupManager = WorkgroupManager.getInstance();
    Workgroup workgroup = workgroupManager.getWorkgroup(new JID(wgID));
    WorkgroupMacros workgroupMacros = WorkgroupMacros.getInstance();

    String groupTitle = URLDecoder
            .decode(ParamUtils.getParameter(request, "macroGroupTitle"), "UTF-8");


    MacroGroup rootGroup = workgroupMacros.getMacroGroup(workgroup, groupTitle);

    String categoryTitle = ParamUtils.getParameter(request, "categoryTitle");
    MacroGroup g = workgroupMacros.getMacroGroup(workgroup, categoryTitle);
    if (g != null) {
        response.sendRedirect("workgroup-macros.jsp?wgID=" + wgID
                + "&categoryExists=true&failure=Category name already exists. Please choose a unique category name.");
        return;
    }
    else if (add && !edit) {
        if (ModelUtil.hasLength(categoryTitle)) {
            // Create new MacroGroup and add
            MacroGroup group = new MacroGroup();
            group.setTitle(categoryTitle);
            rootGroup.addMacroGroup(group);
            workgroupMacros.saveMacros(workgroup);
            response.sendRedirect(
                    "workgroup-macros.jsp?success=New category has been added&wgID=" + wgID);
            return;
        }
    }
    else if (edit && ModelUtil.hasLength(categoryTitle)) {
        rootGroup.setTitle(categoryTitle);
        workgroupMacros.saveMacros(workgroup);
        response.sendRedirect("workgroup-macros.jsp?wgID=" + wgID + "&success=Category edited");
        return;
    }

%>

<html>
    <head>
        <title>Workgroup Add/Edit Category</title>
        <meta name="subPageID" content="workgroup-macros"/>
        <meta name="extraParams" content="<%= "wgID="+wgID %>"/>
    </head>
    <body>
<script language="javascript">
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

        function validateForm(){
           if(!Jtrim(document.f.categoryTitle.value)){
              alert("You must specify a valid name for the new category.");
              document.f.categoryTitle.focus();
              return false;
           }
           return true;
        }
</script>

<% if(!edit){ %>
<p>
Create a new Category as a sub-category to "<b><%= groupTitle%></b>" in the Form below.
</p>
<% } else { %>
<p>
Use the form below to edit the "<b><%= groupTitle%></b>" category.
</p>
<% } %>

<form name="f" action="workgroup-add-category.jsp" method="post" onsubmit="return validateForm(); return false;">
<input type="hidden" name="wgID" value="<%= wgID%>" />
<input type="hidden" name="macroGroupTitle" value="<%= URLEncoder.encode(groupTitle, "UTF-8") %>" />
<input type="hidden" name="add" value="true" />
<% if(edit){ %>
<input type="hidden" name="edit" value="true" />
<% }%>
<table class="jive-table" cellspacing="0" cellpadding="0"  width="100%">
<th colspan="2"><%= edit ? "Edit Category" : "Add Category" %></th>
  <tr>
  <td>Category Title</td><td><input type="text" name="categoryTitle" size="40" maxlength="40"><br><span class="jive-description"><%=edit ? "Edit category" : "Adding new category to "%> <b><%= rootGroup.getTitle() %></b></span></td>
  </tr>
  <tr>
  <td colspan="2">
  <input type="submit" name="Add" value="<%= edit ? "Edit Category" : "Add Category"%>">
  </td>
  </tr>
</table>
</form>
</body>
</html>

