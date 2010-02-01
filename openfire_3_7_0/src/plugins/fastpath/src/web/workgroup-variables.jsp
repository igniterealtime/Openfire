<%@ page import="org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.xmpp.workgroup.WorkgroupManager,
                 org.jivesoftware.xmpp.workgroup.Workgroup,
                 org.xmpp.packet.JID,
                 org.jivesoftware.xmpp.workgroup.utils.ModelUtil,
                 java.util.List, org.jivesoftware.openfire.fastpath.dataforms.FormManager, org.jivesoftware.openfire.fastpath.dataforms.WorkgroupForm, org.jivesoftware.openfire.fastpath.dataforms.FormElement" errorPage="workgroup-error.jsp" %>
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

    boolean delete = ParamUtils.getBooleanParameter(request, "delete");
    boolean addVariable = ParamUtils.getParameter(request, "addVariable") != null;

    if (addVariable) {
        String variableType = ParamUtils.getParameter(request, "variableType");
        String variableName = ParamUtils.getParameter(request, "variableName");
        String variableValue = ParamUtils.getParameter(request, "variableValue");
        String variableDescription = ParamUtils.getParameter(request, "variableDescription");

        FormElement formElement = new FormElement();
        formElement.setAnswerType(WorkgroupForm.FormEnum.hidden);
        formElement.getAnswers().add(variableType + "_" + variableValue);
        formElement.setVariable(variableName);
        if (variableDescription != null) {
            formElement.setDescription(variableDescription);
        }

        workgroupForm.addHiddenVar(formElement);
    }

    if (delete) {
        int index = ParamUtils.getIntParameter(request, "index", -1);

        if (index >= 0) {
            // Re-add it based on the "direction" we're doing. First, remove it:
            workgroupForm.removeHiddenVarAt(index);

            // done, so redirect
            response.sendRedirect("workgroup-variables.jsp?wgID=" + wgID);
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
        message = "Web Variables has been saved.";
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
        <title><%= "Workgroup Variables for "+wgID%></title>
        <meta name="subPageID" content="workgroup-variables"/>
        <meta name="extraParams" content="wgID=<%= wgID %>"/>
        <!--<meta name="helpPage" content="create_a_form_variable.html"/>-->

        <script type="text/javascript">
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
            if(!Jtrim(document.f.variableValue.value)){
                alert("Please specify the name of the variable.");
                document.f.variableValue.focus();
                return false;
            }

            if(!Jtrim(document.f.variableName.value)){
               alert("Please specify the the new name of the variable.");
                document.f.variableName.focus();
                return false;
            }
            return true;
          }
        </script>
    </head>
    <body>

<p>
 Add all variables you wish to have collected during a Chat Request.
</p>

<p>
<b>Important:</b> Save the form to have your changes take affect.
</p>
<% if(save){ %>
 <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
        	<td class="jive-icon"><img alt="success image" src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        	<td class="jive-icon-label"><%= message %></td>
        </tr>
    </tbody>
    </table>
    </div>
    <br/>
<% } %>

  <form name="f" action="workgroup-variables.jsp" method="post" onsubmit="return validateForm(); return false;">
  <input type="hidden" name="wgID" value="<%= wgID %>">
  <table class="jive-table" cellpadding="3" cellspacing="0" >
    <tr>
        <th colspan="7">Create New Variable From Chat Request</th>
    </tr>
    <tr valign="top">
    <td width="25%">HTTP Type:</td>
    <td colspan="7">
        <select name="variableType">
        <option value="getRequest">Request Parameter</option>
        <option value="getCookie">Cookie</option>
        <option value="getHeader">HTTP Header</option>
        <option value="getSession">Session Attribute</option>
        </select>
        <br/><span class="jive-description">The type of variable to be retrieved.</span>
        </td>
    </tr>
    <tr valign="top">
    <td width="25%">Name:*</td><td colspan="6"><input type="text" name="variableValue" size="40"><br/><span class="jive-description">The name of the variable to retrieve.</span></td>
    </tr>

    <tr valign="top">
    <td width="25%">Assign New Name:*</td><td colspan="6"><input type="text" name="variableName" size="40"><br/><span class="jive-description">Specify the name you wish to assign to the variable value upon retrieving the information.</span></td>
    </tr>

    <tr valign="top">
    <td width="25%">Description:</td><td colspan="6"><input type="text" name="variableDescription" size="40"><br/><span class="jive-description">Specify a general description of the variable being retrieved.</span></td>
    </tr>
    <tr>
    <td colspan="7"><input type="submit" name="addVariable" value="Add Variable"></td>
    </tr>
   </form>
     <form action="workgroup-variables.jsp" method="post">

    <tr>
        <th>Type</th><th>Name</th><th>Returned Name</th><th>Description</th><th>Delete</th>
    </tr>
    <!-- Build table -->
    <%
        int counter = 0;
        for(FormElement element : workgroupForm.getHiddenVars()){
            if(element.getAnswerType() != WorkgroupForm.FormEnum.hidden){
                counter++;
                continue;
            }

            String variableValue = "";
            List answers = element.getAnswers();
            if(answers.size() > 0){
              variableValue = (String)element.getAnswers().get(0);
            }

            String type = null;
            if(variableValue.startsWith("getRequest_")){
                type = "Request Parameter";
            }
            if(variableValue.startsWith("getCookie_")){
                type = "Cookie";
            }
            else if(variableValue.startsWith("getHeader_")){
                type = "HTTP Header";
            }
            else if(variableValue.startsWith("getSession_")){
                type = "Session Attribute";
            }

            int indexOf = variableValue.indexOf("_");
            String varValue = variableValue.substring(indexOf + 1);
            if(type != null){%>
        <tr valign="top">
            <td><b><%= type %></b></td>
            <td><%= element.getVariable() %></td>
            <td><%= varValue %></td>
            <td><span class="jive-description"><%= element.getDescription() != null ? element.getDescription() : "&nbsp;" %></span></td>
            <td> <a href="workgroup-variables.jsp?wgID=<%=wgID%>&delete=true&index=<%=counter%>"><img src="images/delete-16x16.gif" border="0"></a></td>
        </tr>
        <% } %>


        <% counter++; }%>

    <tr>

    <td colspan="7"><input type="submit" name="save" value="Save Form"></td>
    <input type="hidden" name="wgID" value="<%= wgID %>">
    </tr>
   </table>
   </form>

</body>
</html>