<%@ page import="org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.xmpp.workgroup.WorkgroupManager,
                 org.jivesoftware.xmpp.workgroup.Workgroup,
                 org.xmpp.packet.JID,
                 org.jivesoftware.xmpp.workgroup.utils.ModelUtil, org.jivesoftware.openfire.fastpath.dataforms.FormManager, org.jivesoftware.openfire.fastpath.dataforms.WorkgroupForm, org.jivesoftware.openfire.fastpath.dataforms.FormElement, org.jivesoftware.openfire.fastpath.dataforms.FormUtils"%>
<%
    String wgID = ParamUtils.getParameter(request, "wgID");
    WorkgroupManager workgroupManager = WorkgroupManager.getInstance();
    Workgroup workgroup = workgroupManager.getWorkgroup(new JID(wgID));


    FormManager formManager = FormManager.getInstance();

    WorkgroupForm workgroupForm = formManager.getWebForm(workgroup);
    if (workgroupForm == null) {
        workgroupForm = new WorkgroupForm();
        formManager.addWorkgroupForm(workgroup, workgroupForm);
    }

    boolean changePosition = ParamUtils.getBooleanParameter(request, "changePos");
    boolean up = ParamUtils.getBooleanParameter(request, "up");
    boolean down = ParamUtils.getBooleanParameter(request, "down");

    boolean delete = ParamUtils.getBooleanParameter(request, "delete");

    // Change the position of a interceptor
    if (changePosition) {
        int index = ParamUtils.getIntParameter(request, "index", -1);

        if (index >= 0) {
            // Get the Form element at index
            FormElement element = workgroupForm.getFormElementAt(index);

            // Re-add it based on the "direction" we're doing. First, remove it:
            workgroupForm.removeFormElement(index);
            if (up) {
                workgroupForm.addFormElement(element, index - 1);
            }
            if (down) {
                workgroupForm.addFormElement(element, index + 1);
            }

            // done, so redirect
            response.sendRedirect("workgroup-dataform.jsp?wgID=" + wgID);
            return;
        }
    }

    String changeRequired = ParamUtils.getParameter(request, "notRequired");
    if (ModelUtil.hasLength(changeRequired)) {
        boolean notRequired = ParamUtils.getBooleanParameter(request, "notRequired");
        int index = ParamUtils.getIntParameter(request, "index", -1);
        FormElement elem = workgroupForm.getFormElementAt(index);
        elem.setRequired(notRequired);
    }


    if (delete) {
        int index = ParamUtils.getIntParameter(request, "index", -1);

        if (index >= 0) {
            // Re-add it based on the "direction" we're doing. First, remove it:
            workgroupForm.removeFormElement(index);

            // done, so redirect
            response.sendRedirect("workgroup-dataform.jsp?wgID=" + wgID);
            return;
        }
    }

    boolean save = ParamUtils.getParameter(request, "save") != null;
    String message = "";
    if (save) {
        String title = ParamUtils.getParameter(request, "title");
        String description = ParamUtils.getParameter(request, "description");
        if (ModelUtil.hasLength(title)) {
            workgroupForm.setTitle(title);
        }

        if (ModelUtil.hasLength(description)) {
            workgroupForm.setDescription(description);
        }

        formManager.saveWorkgroupForm(workgroup);
        message = "Web Form has been saved.";
    }

    String formTitle = workgroupForm.getTitle();
    String description = workgroupForm.getDescription();
    if (!ModelUtil.hasLength(formTitle)) {
        formTitle = "";
    }

    if (!ModelUtil.hasLength(description)) {
        description = "";
    }
%>


<html>
    <head>
        <title><%= "Workgroup Web Form for "+wgID%></title>
        <meta name="subPageID" content="workgroup-forms"/>
        <meta name="extraParams" content="wgID=<%= wgID %>"/>
        <!--<meta name="helpPage" content="create_a_form.html"/>-->
    </head>
    <body>

<p>
 Create your own customized HTML Form to collect information from the user.
</p>

<p>
<b>Important:</b>&nbsp;Saving the form makes the form visible to customers.
</p>
<% if(save){ %>
 <div class="success">
        	<%= message %>
</div>
<% } %>

  <table class="jive-table" cellpadding="3" cellspacing="0" >
     <form action="workgroup-dataform.jsp" method="post">
    <tr>
        <th>Order</th><th>Label</th><th>Name</th><th>Type</th><th>Required</th><th>Edit</th><th>Delete</th>
    </tr>
    <!-- Build table -->
    <%
        int counter = 0;
        int size = workgroupForm.getFormElements().size();
        for(FormElement element : workgroupForm.getFormElements()){
            boolean isHidden = (element.getAnswerType() == WorkgroupForm.FormEnum.hidden);
    %>


        <tr valign="top" <%= isHidden ? "bgcolor=\"#fffff\"" : ""%>>
        <td>
        <% if(counter == 0 && size > 1) { %>
          <a href="workgroup-dataform.jsp?wgID=<%=wgID%>&changePos=true&down=true&index=<%=counter%>">
          <img src="images/arrow_down.gif" width="16" height="16" border="0"></a>

          <img src="images/blank.gif" width="16" height="16">
        <% } %>


        <% if(counter > 0 && counter < size - 1){ %>
         <a href="workgroup-dataform.jsp?wgID=<%=wgID%>&changePos=true&down=true&index=<%=counter%>"><img src="images/arrow_down.gif" width="16" height="16" border="0"></a>
         <a href="workgroup-dataform.jsp?wgID=<%=wgID%>&changePos=true&up=true&index=<%=counter%>">
          <img src="images/arrow_up.gif" width="16" height="16" border="0"></a>
        <%}%>

        <% if(counter > 0 && counter ==  size - 1){ %>
         <img src="images/blank.gif" width="16" height="16">
          <a href="workgroup-dataform.jsp?wgID=<%=wgID%>&changePos=true&up=true&index=<%=counter%>">
            <img src="images/arrow_up.gif" width="16" height="16" border="0"></a>
        <%}%>

        <% if(size == 1){%>
        &nbsp;
        <%}%>


        </td>
           <% if(!isHidden){ %>
            <td><b><%= element.getLabel()%></b></td>
            <td><%= element.getVariable()%></td>
            <td><%= FormUtils.createAnswers(element) %><br><span class="jive-description"><%= element.getDescription() != null ? element.getDescription() : "" %></span></td>

            <%
                if(element.isRequired()){
            %>
            <td><a href="workgroup-dataform.jsp?wgID=<%=wgID %>&notRequired=false&index=<%=counter %>">Required</a></td>
            <% } else { %>
            <td><a href="workgroup-dataform.jsp?wgID=<%=wgID %>&notRequired=true&index=<%=counter %>">Not Required</a></td>
            <% }%>

            <td>
                <a href="create-element.jsp?wgID=<%= wgID%>&edit=true&index=<%= counter%>"><img src="images/edit-16x16.gif" border="0" /></a></td>
           <% } else {
            if(element.getAnswerType() == WorkgroupForm.FormEnum.hidden){
            String variableName = element.getVariable();
            String type = "";
            if(variableName.startsWith("cookie_")){
                type = "Cookie";
            }
            else if(variableName.startsWith("header_")){
                type = "HTTP Header";
            }
            else if(variableName.startsWith("session_")){
                type = "Session Attribute";
            }

            int indexOf = variableName.indexOf("_");
            String varName = variableName.substring(indexOf + 1);
            %>
              <td colspan="5" align="center">{Hidden variable - Type: <%=type%>. Checks for variable:<%= varName%>}</td>

           <% }}  %>

            <td> <a href="workgroup-dataform.jsp?wgID=<%=wgID%>&delete=true&index=<%=counter%>"><img src="images/delete-16x16.gif" border="0"></a></td>
        </tr>

        <% counter++; }%>

    <tr>

    <td colspan="1"><input type="button" name="create" value="Add Field" onclick="window.location.href='create-element.jsp?wgID=<%= wgID%>'"></td>
        <td colspan="6"><input type="submit" name="save" value="Save Changes"></td>

        <input type="hidden" name="wgID" value="<%= wgID %>">
    </tr>
          </form>
   </table>
    <br/><br/>
    <div style="width:600px">
    <p>Spark Fastpath has assigned certain functionality to certain form element names. Please review before building your list.
    For a more customized approach via code, you may want to implement your own MetadataProvider. Please see the Web Chat Client api
    for more information.
    </p>
</div>
    <table class="jive-table" cellpadding="3" cellspacing="0" width="600">
    <tr>
    <th nowrap>Form Name</th><th>Description</th>
    </tr>
    <tr>
    <td>username</td><td>Use this variable to allow a user to specify their own user name to use for this chat session.</td>
    </tr>
    <tr>
    <td>password</td><td>Use this variable in conjunction with 'username' to allow a user to login to their account.</td>
    </tr>
    <tr>
    <td>email</td><td>Use this form element name to allow a user to specify their email address.</td>
    </tr>
     <tr>
    <td>question</td><td>Use this form element name to set the question a user asks before entering a queue.</td>
    </tr>
     <tr>
    <td>agent</td><td>Use this form element name to specify a particular agent to initially route to in the workgroup. If the agent is not available, the failover is to route to others in the workgroup.</td>
    </tr>
    </table>

</body>
</html>