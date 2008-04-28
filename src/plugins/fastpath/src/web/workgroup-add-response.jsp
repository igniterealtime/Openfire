
<%--
  -	$RCSfile$
  -	$Revision: 22565 $
  -	$Date: 2005-10-10 17:20:21 -0700 (Mon, 10 Oct 2005) $
--%>

<%@ page import="org.jivesoftware.xmpp.workgroup.WorkgroupManager,
                 org.jivesoftware.util.*,
                 java.net.URLEncoder,
                 java.net.URLDecoder,
                 org.jivesoftware.xmpp.workgroup.Workgroup,
                 org.xmpp.packet.JID,
                 org.jivesoftware.xmpp.workgroup.utils.ModelUtil,
                 org.jivesoftware.openfire.fastpath.macros.MacroGroup,
                 org.jivesoftware.openfire.fastpath.macros.WorkgroupMacros, org.jivesoftware.openfire.fastpath.macros.Macro"
%>

<% // Get parameters
    String wgID = ParamUtils.getParameter(request, "wgID");
    boolean add = request.getParameter("add") != null;
    boolean delete = request.getParameter("delete") != null;
    boolean edit = request.getParameter("edit") != null;

    String success = request.getParameter("success");
    String failure = request.getParameter("failure");

    WorkgroupManager workgroupManager = WorkgroupManager.getInstance();
    Workgroup workgroup = workgroupManager.getWorkgroup(new JID(wgID));
    WorkgroupMacros workgroupMacros = WorkgroupMacros.getInstance();

    String groupTitle = URLDecoder
            .decode(ParamUtils.getParameter(request, "macroGroupTitle"), "UTF-8");


    MacroGroup rootGroup = workgroupMacros.getMacroGroup(workgroup, groupTitle);


    String responseTitle = ParamUtils.getParameter(request, "responseTitle");
    String responseBody = ParamUtils.getParameter(request, "responseBody");
    int responseType = ParamUtils.getIntParameter(request, "responseType", -1);

    int entry = ParamUtils.getIntParameter(request, "entry", -1);

    if (delete) {
        if (entry != -1) {
            Macro macro = rootGroup.getMacro(entry);
            if (macro != null) {
                rootGroup.removeMacro(macro);
                success = "Response has been deleted.";
            }
        }
    }


    boolean commitEdit = request.getParameter("editAdd") != null;
    if (commitEdit) {
        Macro macro = rootGroup.getMacro(entry);
        macro.setTitle(responseTitle);
        macro.setResponse(responseBody);
        macro.setType(responseType);
        workgroupMacros.saveMacros(workgroup);
        response.sendRedirect("workgroup-add-response.jsp?macroGroupTitle="
                + URLEncoder.encode(groupTitle, "UTF-8") + "&wgID=" + wgID
                + "&success=Response has been edited successfully.");
        return;
    }


    if (add && !edit) {
        if (ModelUtil.hasLength(responseTitle) && ModelUtil.hasLength(responseBody)) {
            // Create new MacroGroup and add
            Macro macro = new Macro();
            macro.setTitle(responseTitle);
            macro.setResponse(responseBody);
            macro.setType(responseType);
            rootGroup.addMacro(macro);
            workgroupMacros.saveMacros(workgroup);
            response.sendRedirect("workgroup-add-response.jsp?macroGroupTitle="
                    + URLEncoder.encode(groupTitle, "UTF-8") + "&wgID=" + wgID
                    + "&success=New response has been added.");
            return;
        }
    }


    boolean inEditMode = false;

    String title = "Add New Canned Response To Category";


    String macroTitle = "";
    String macroBody = "";
    int type = 0;
    if (edit && !ModelUtil.hasLength(responseTitle)) {
        if (entry != -1) {
            Macro macro = rootGroup.getMacro(entry);
            macroTitle = macro.getTitle();
            macroBody = macro.getResponse();
            inEditMode = true;
            title = "Edit Canned Response";
        }
    }


%>

<html>
    <head>
        <title><%= title %></title>
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
           if(!Jtrim(document.f.responseTitle.value)){
              alert("You must specify a valid title for this response.");
              document.f.responseTitle.focus();
              return false;
           }

           if(!Jtrim(document.f.responseBody.value)){
                alert("You must specify a valid response body for this response.");
                document.f.responseBody.focus();
                return false;
            }
           return true;
        }


</script>

<% if(!edit){ %>
<p>
Create a new response to add to the "<b><%= groupTitle%></b>" using the form below.
</p>
<% } else { %>
<p>
Edit the response using the form below.
</p>
<% } %>

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

<form name="f" action="workgroup-add-response.jsp" method="post" onsubmit="return validateForm(); return false;">
<input type="hidden" name="wgID" value="<%= wgID%>" />
<input type="hidden" name="macroGroupTitle" value="<%= URLEncoder.encode(groupTitle, "UTF-8") %>" />
<input type="hidden" name="add" value="true" />
<%if(inEditMode){ %>
<input type="hidden" name="editAdd" value="true" />
<input type="hidden" name="entry" value="<%=entry%>" />
<% } %>
<table class="jive-table" cellspacing="0" cellpadding="0"  width="100%">
<th colspan="2"><%= inEditMode ? "Edit Response" : "Add New Response"%></th>
  <tr valign="top">
  <td>Response Title<br/><span class="jive-description">Please enter the title for this response.</span></td><td><input type="text" name="responseTitle" value="<%= macroTitle %>" size="40" maxlength="40"><br><span class="jive-description">Adding new response to <b><%= rootGroup.getTitle() %></b></span></td>
  </tr>
  <tr valign="top">
  <td>Response Type<br/><span class="jive-description">Please select the response type.</span></td><td>
  <select name="responseType">
  <option value="0">Text</option>
  <option value="1">URL</option>
  <option value="2">Image</option>
  </select>
  </td>
  </tr>
  <tr valign="top">
  <td width="40%">Response Body<br/><span class="jive-description">Enter your response body. If you selected a url or image, just enter the URL to either.</span></td><td><textarea name="responseBody" cols="40" rows="4"><%= macroBody%></textarea></td>
  </tr>
  <tr>
  <td colspan="2">
  <input type="submit" name="Add" value="<%= inEditMode ? "Save Changes" : "Add Response" %>">
  </td>
  </tr>
</table>
</form>

<br><br>
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
    for(Macro macro : rootGroup.getMacros()){%>
        <tr>
        <td><%= macro.getTitle()%></td>
        <td><%= "Text"%></td>
        <td align="center"><a href="workgroup-add-response.jsp?edit=true&wgID=<%=wgID%>&macroGroupTitle=<%= URLEncoder.encode(groupTitle, "UTF-8")%>&entry=<%=count%>"><img src="images/edit-16x16.gif" border="0"></a>&nbsp;<a href="workgroup-add-response.jsp?delete=true&wgID=<%=wgID%>&macroGroupTitle=<%= URLEncoder.encode(groupTitle, "UTF-8")%>&entry=<%=count%>"><img src="images/delete-16x16.gif" border="0"></a></td>
        </tr>

<%
        count++;
    }
%>
</table>
</form>
</body>
</html>

