<%@ page import="org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.xmpp.workgroup.WorkgroupManager,
                 org.jivesoftware.xmpp.workgroup.Workgroup,
                 org.xmpp.packet.JID,
                 java.util.StringTokenizer,
                 java.util.List,
                 java.util.Iterator,
                 org.jivesoftware.xmpp.workgroup.utils.ModelUtil, org.jivesoftware.openfire.fastpath.dataforms.FormManager, org.jivesoftware.openfire.fastpath.dataforms.WorkgroupForm, org.jivesoftware.openfire.fastpath.dataforms.FormElement"%>
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

    boolean createElement = ParamUtils.getParameter(request, "createElement") != null;
    boolean edit = ParamUtils.getBooleanParameter(request, "edit", false);

    String label = ParamUtils.getParameter(request, "label");
    String variable = ParamUtils.getParameter(request, "variable");
    String answerType = ParamUtils.getParameter(request, "answer");
    boolean required = ParamUtils.getBooleanParameter(request, "required");
    String listItems = ParamUtils.getParameter(request, "items");
    String description = ParamUtils.getParameter(request, "description");

    boolean saveEdit = ParamUtils.getBooleanParameter(request, "saveEdit");
    int index = ParamUtils.getIntParameter(request, "index", -1);

    boolean hasCookie = false;

    if (createElement) {
        // Create Element
        FormElement formElement = new FormElement();
        if (saveEdit) {
            int saveIndex = ParamUtils.getIntParameter(request, "saveIndex", -1);
            formElement = workgroupForm.getFormElementAt(saveIndex);
            formElement.getAnswers().removeAll(formElement.getAnswers());
        }
        formElement.setLabel(label);
        formElement.setAnswerType(answerType);
        formElement.setRequired(required);
        formElement.setVisible(true);
        formElement.setVariable(variable);
        formElement.setDescription(description);

        if (listItems != null) {
            StringTokenizer tkn = new StringTokenizer(listItems, "\n");
            while (tkn.hasMoreTokens()) {
                String value = tkn.nextToken();
                value = value.replace('\r', ' ');
                formElement.getAnswers().add(value.trim());
            }
        }

        boolean prepopulate = ParamUtils.getBooleanParameter(request, "prepopulate");
        if (prepopulate) {
            String tag = "setCookie_" + variable;
            boolean containsTag = workgroupForm.containsHiddenTag(tag);
            if (!containsTag) {
                // Add new tag
                FormElement el = new FormElement();
                el.setAnswerType(WorkgroupForm.FormEnum.hidden);
                el.setVariable(tag);
                workgroupForm.addHiddenVar(el);
            }
        }
        else {
            String tag = "setCookie_" + variable;
            workgroupForm.removeHiddenVar(tag);
        }

        if (!saveEdit) {
            workgroupForm.addFormElement(formElement);
        }

        workgroup = workgroupManager.getWorkgroup(new JID(wgID));

        response.sendRedirect("workgroup-dataform.jsp?wgID=" + wgID);
        return;
    }

    String title = "Create Form Element";

    if (edit) {
        if (index != -1) {
            FormElement elem = workgroupForm.getFormElementAt(index);
            label = elem.getLabel();
            variable = elem.getVariable();
            description = elem.getDescription();
            answerType = elem.getAnswerType().toString();
            required = elem.isRequired();

            String tag = "setCookie_" + variable;
            hasCookie = workgroupForm.containsHiddenTag(tag);

            StringBuffer buf = new StringBuffer();
            List answers = elem.getAnswers();
            Iterator iter = answers.iterator();
            while (iter.hasNext()) {
                buf.append((String)iter.next());
                buf.append("\n");
            }
            listItems = buf.toString();
        }
        title = "Edit Form Element";
    }

    if (label == null) {
        label = "";
    }

    if (variable == null) {
        variable = "";
    }

    if (description == null) {
        description = "";
    }

    if (answerType == null) {
        answerType = "";
    }

    if (listItems == null) {
        listItems = "";
    }
%>
<html>
    <head>
        <title><%= title %></title>
        <meta name="subPageID" content="workgroup-forms"/>
        <meta name="extraParams" content="<%= "wgID="+wgID %>"/>
        <!--<meta name="helpPage" content="create_a_custom_form_field.html"/>-->

        <script>
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
             if(!Jtrim(document.f.label.value)){
               alert("Please supply a label for this form element.");
               document.f.label.focus();
               return false;
             }

             if(!Jtrim(document.f.variable.value)){
               alert("Please supply a variable for this form element.");
               document.f.variable.focus();
               return false;
             }

              if(document.f.variable.value.indexOf(" ") != -1){
               alert("Please supply a valid variable name for this form element.");
               document.f.variable.focus();
               return false;
             }

             var v = document.f.answer.value;
             if(v == '<%= WorkgroupForm.FormEnum.dropdown_box%>' || v == '<%= WorkgroupForm.FormEnum.radio_button%>' || v == '<%= WorkgroupForm.FormEnum.checkbox%>'){
                if(!Jtrim(document.f.items.value)){
                  alert("Please supply at least one item for a multi choice  element.");
                  return false;
                }
             }

             return true;
         }
        </script>
    </head>
    <body>

    <form name="f" action="create-element.jsp" method="post" onsubmit="return validateForm(); return false;"  >
        <table class="jive-table" cellpadding="3" cellspacing="0" width="600">
        <tr>
            <th colspan="2">New Form Element</th>
        </tr>
        <tr valign="top">
            <td>Variable Label:*</td><td><input type="text" size="60" name="label" value="<%= label %>">
            <br/><span class="jive-description">The text to display on the HTML Form. e.g. Product:</span>
            </td>
        </tr>
       <tr valign="top">
            <td>Variable Name:*</td><td><input type="text" size="60" name="variable" value="<%= variable%>">
            <br/><span class="jive-description">The name of the html form element. e.g. product_name</span>
            </td>
        </tr>
       <tr valign="top">
            <td>Description:</td><td><input type="text" size="60" name="description" value="<%= description %>">
             <br/><span class="jive-description">A description of this form element.</span>
            </td>
        </tr>
       <tr valign="top">
        <td>Answer Type:*</td>
        <td>
            <select name="answer">
                <%= getOption(WorkgroupForm.FormEnum.dropdown_box, "Dropdown Box", answerType) %>
                <%= getOption(WorkgroupForm.FormEnum.checkbox, "Checkbox", answerType) %>
                <%= getOption(WorkgroupForm.FormEnum.radio_button, "Radio Button", answerType) %>
                <%= getOption(WorkgroupForm.FormEnum.textfield, "TextField", answerType) %>
                <%= getOption(WorkgroupForm.FormEnum.textarea, "TextArea", answerType) %>
                <%= getOption(WorkgroupForm.FormEnum.password, "Password", answerType) %>
            </select>
        </td>
        </tr>
        <tr>
            <td>&nbsp;</td>
            <td><input type="checkbox" name="required" <%= required ? "checked" : ""%>>&nbsp;<b>Required</b></td>
        </tr>
        <tr>
        <td colspan="2"><input type="checkbox" name="prepopulate" <%= hasCookie ? "checked" : ""%>>Populate with user's previous choice.</td>
        </tr>
        </table>

        <table class="jive-table" cellpadding="3" cellspacing="0" width="600">
        <tr>
            <th colspan="2">Add List Items</th>
        </tr>
        <tr>
            <td colspan="2"><i>Hit return after each list item.</i></td>
        </tr>
        <tr>
        <td colspan="2">
            <textarea name="items" cols="40" rows="3"><%= listItems %></textarea>
        </td>
        </tr>
        <tr>
           <td><input type="submit" name="createElement" value="Update">&nbsp;
           <input type="button" name="cancel" value="Cancel" onclick="javascript:window.location.href='workgroup-dataform.jsp?wgID=<%=wgID%>'"></td>
        </tr>
        </table>
        <input type="hidden" name="wgID" value="<%= wgID%>">
        <% if(edit) { %>
        <input type="hidden" name="saveEdit" value="true" />
        <input type="hidden" name="saveIndex" value="<%= index %>" />
        <% } %>
    </form>
</body>
</html>

<%!
  private String getOption(WorkgroupForm.FormEnum form, String label, String answerType){
     String selected = form.toString().equals(answerType) ? "selected" : "";
     if(!ModelUtil.hasLength(answerType)){
         if(form == WorkgroupForm.FormEnum.textfield){
             selected = "selected";
         }
     }
     String returnStr = "<option value=\""+form.toString()+"\" "+selected+">"+label+"</option>";
     return returnStr;
  }
%>
