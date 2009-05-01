<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c"%>
<%--
  -	$RCSfile$
  -	$Revision: 32833 $
  -	$Date: 2006-08-02 15:52:36 -0700 (Wed, 02 Aug 2006) $
--%>
<%@ page import="org.jivesoftware.xmpp.workgroup.Workgroup,
           org.jivesoftware.xmpp.workgroup.WorkgroupManager,
           org.jivesoftware.xmpp.workgroup.utils.ModelUtil"
errorPage="workgroup-error.jsp"%>
<%@ page import="org.jivesoftware.util.ParamUtils"%>
<%@ page import="org.xmpp.packet.JID"%>
<%@ page import="java.util.HashMap"%>
<%@ page import="java.util.Map, org.jivesoftware.openfire.fastpath.util.WorkgroupUtils, org.jivesoftware.openfire.fastpath.dataforms.FormManager, org.jivesoftware.openfire.fastpath.dataforms.WorkgroupForm, org.jivesoftware.openfire.fastpath.dataforms.FormElement"%>
<%
    // Get parameters //
    String wgID = ParamUtils.getParameter(request, "wgID");
    boolean created = ParamUtils.getParameter(request, "created") != null;
%>

<html>
    <head>
        <title>Workgroup Settings For <%=wgID%></title>
        <meta name="subPageID" content="workgroup-properties"/>
        <meta name="extraParams" content="wgID=<%= wgID %>"/>
        <!--<meta name="helpPage" content="edit_workgroup_properties.html"/>-->
    </head>
    <body>

    <% if(created) { %>
        <div class="jive-success">
            <table cellpadding="0" cellspacing="0" border="0">
                <tbody>
                    <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16"
                    border="0"></td>
                        <td class="jive-icon-label">
                            Workgroup has been created. To add members to the workgroup, click on the Queues link in the sidebar.
                        </td></tr>
                </tbody>
            </table>
        </div><br>
    <% } %>
<%
    // Get a workgroup manager
    WorkgroupManager wgManager = WorkgroupManager.getInstance();
    // If the workgroup manager is null, service is down so redirect:
    if (wgManager == null) {
        response.sendRedirect("error-serverdown.jsp");
        return;
    }
%>
<%
    // Load the workgroup object
    JID workgroupJID = new JID(wgID);
    Workgroup workgroup = wgManager.getWorkgroup(workgroupJID);
    int maxChats = workgroup.getMaxChats();
    int minChats = workgroup.getMinChats();
    long requestTimeout = workgroup.getRequestTimeout() / 1000;
    long offerTimeout = workgroup.getOfferTimeout() / 1000;
    String description = workgroup.getDescription();
    String displayName = workgroup.getDisplayName();
    boolean authRequired = Boolean.valueOf(workgroup.getProperties().getProperty("authRequired"));
    boolean doEnable = ParamUtils.getBooleanParameter(request, "doEnable");
    boolean enableWorkgroup = ParamUtils.getBooleanParameter(request, "enableWorkgroup");

    boolean update = ModelUtil.hasLength(request.getParameter("update"));

    if (doEnable && ModelUtil.hasLength(request.getParameter("enableWorkgroup"))) {
        if (enableWorkgroup) {
            workgroup.setStatus(Workgroup.Status.READY);
        }
        else {
            workgroup.setStatus(Workgroup.Status.CLOSED);
        }
    }

    String statusMessage = "";
    Map errors = new HashMap();
    if (update) {
        displayName = request.getParameter("displayName");
        if (displayName == null && displayName.length() == 0) {
            errors.put("displayName", "");
        }

        maxChats = ParamUtils.getIntParameter(request, "maxChats", wgManager.getDefaultMaxChats());


        minChats = ParamUtils.getIntParameter(request, "minChats", wgManager.getDefaultMinChats());


        requestTimeout = ParamUtils.getLongParameter(request, "requestTimeout",
                wgManager.getDefaultRequestTimeout() / 1000) * 1000;

        offerTimeout = ParamUtils.getLongParameter(request, "offerTimeout",
                wgManager.getDefaultOfferTimeout() / 1000) * 1000;

        authRequired = ParamUtils.getBooleanParameter(request, "authRequired", false);


        if (minChats <= 0) {
            errors.put("minChats", "");
        }
        if (minChats > maxChats) {
            errors.put("minChatsGreater", "");
        }
        if (requestTimeout <= 0) {
            errors.put("requestTimeout", "");
        }
        if (offerTimeout <= 0) {
            errors.put("offerTimeout", "");
        }
        if (offerTimeout > requestTimeout) {
            errors.put("offerGreater", "");
        }
        if (errors.size() == 0) {
            description = request.getParameter("description");
            statusMessage = WorkgroupUtils.updateWorkgroup(wgID, displayName, description, maxChats,
                    minChats, requestTimeout, offerTimeout);
            requestTimeout = workgroup.getRequestTimeout() / 1000;
            offerTimeout = workgroup.getOfferTimeout() / 1000;
            workgroup.getProperties().setProperty("authRequired", String.valueOf(authRequired));

            FormManager formManager = FormManager.getInstance();

            WorkgroupForm workgroupForm = formManager.getWebForm(workgroup);
            if (workgroupForm == null) {
                workgroupForm = new WorkgroupForm();
                formManager.addWorkgroupForm(workgroup, workgroupForm);
            }

            // check if password field exists and get its index if it does exist.
            int index = -1;
            int counter = 0;
            for (FormElement element : workgroupForm.getFormElements()) {
                if ("password".equals(element.getVariable())) {
                    index = counter;
                    break;
                }
                counter++;
            }

            if (authRequired && index == -1) {
                // Create Element
                FormElement formElement = new FormElement();
                formElement.setLabel("Password");
                formElement.setAnswerType(WorkgroupForm.FormEnum.password);
                formElement.setRequired(true);
                formElement.setVisible(true);
                formElement.setVariable("password");
                formElement.setDescription("Authentication Required");
                workgroupForm.addFormElement(formElement);
                formManager.saveWorkgroupForm(workgroup);
            }
            else if (!authRequired && index != -1) {
                // Remove Element
                workgroupForm.removeFormElement(index);
                formManager.saveWorkgroupForm(workgroup);
            }
        }
    }

%>
    <p>Below are the general settings for the <b><%= workgroupJID.getNode() %></b> workgroup.</p>
    <script langauge="JavaScript" type="text/javascript">
        function wgEnable(enable) {
            if (enable) {
                document.overview.enableWorkgroup.value = 'true';
            }
            else{
                document.overview.enableWorkgroup.value = 'false';
            }
            document.overview.submit();
        }
    </script>

<%  if (!errors.isEmpty()) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
            <td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0"/></td>
            <td class="jive-icon-label">

            <% if (errors.get("displayName") != null) { %>
                Please enter a valid display name.
            <% } else if (errors.get("maxChats") != null) { %>
                Please enter a valid max number of chats value.
            <% } else if (errors.get("minChats") != null) { %>
                Please enter a valid min number of chats value.
            <% } else if (errors.get("minChatsGreater") != null) { %>
                Min chats must be less than max chats.
            <% } else if (errors.get("requestTimeout") != null) { %>
                Please enter a valid request timeout value.
            <% } else if (errors.get("offerTimeout") != null) { %>
                Please enter a valid offer timeout value.
            <% } else if (errors.get("offerGreater") != null) { %>
                Offer timeout must be less than request timeout.
            <% } %>
            </td>
        </tr>
    </tbody>
    </table>
    </div>
    <br>

<%  } else { %>

      <%= statusMessage %>

<%  } %>

    <form action="workgroup-properties.jsp" name="overview">
    <table width="100%" class="jive-table" cellpadding="3" cellspacing="0" border="0">
        <tr>
            <th colspan="2">Workgroup Details</th>
        </tr>

        <tr>
            <td class="c1"><b>Current Status</b></td>
            <td>
                <table cellpadding="0" cellspacing="0" border="0" style="border-width : 0px !important;">
                    <tr>
<%
                        if (workgroup.getStatus() == Workgroup.Status.OPEN) {
%>
                            <td class="c2">
                                <img src="images/bullet-green-14x14.gif" width="14" height="14" border="0"/>
                            </td>
                            <td class="c2">Workgroup is currently active and accepting requests.</td>
                            <td>&nbsp;
                                <input type="button" value="Close" onclick="wgEnable(false);return false;"/>
                            </td>
<%
                        }
                        else if (workgroup.getStatus() == Workgroup.Status.READY) {
%>
                            <td class="c2">
                                <img src="images/bullet-yellow-14x14.gif" width="14" height="14" border="0"/>
                            </td>
                            <td class="c2">Waiting for member.</td>
                            <td>&nbsp;
                                <input type="button" value="Close" onclick="wgEnable(false);return false;"/>
                            </td>
<%
                        }
                        else{
%>
                            <td class="c2">
                                <img src="images/bullet-red-14x14.gif" width="14" height="14" border="0"/>
                            </td>
                            <td class="c2">&nbsp; Workgroup is currently closed.</td>
                            <td>&nbsp;
                                <input type="button" value="Enable" onclick="wgEnable(true);return false;"/>
                            </td>
<%
                        }
%>
                    </tr>
                </table>
            </td>
        </tr>
       
         <tr>
            <td class="c1">
                <b>Display Name</b>
            </td>
            <td class="c2">
                <input type="text" name="displayName" size="30" maxlength="50" value="<%= ((displayName != null) ? displayName : "") %>">
            </td>
        </tr>
        <tr>
           <td class="c1">
               <b>Description</b>
           </td>
           <td class="c2">
               <textarea id="description" name="description" cols="30" rows="3"><%= ((description != null) ? description : "") %></textarea>
           </td>
       </tr>
        </table>
    <br/>
     <table width="100%" class="jive-table" cellpadding="3" cellspacing="0" border="0">
        <tr>
            <th colspan="2">Chat Request Settings</th>
        </tr>
         <tr>
            <td class="c1">
                <b>Max Sessions</b><br/><span class="jive-description">Specify the maximum number of chats for a workgroup member.</span>
            </td>
            <td class="c2">
                            <input type="text" name="maxChats" value="<%= maxChats %>"
                             size="5" maxlength="5"
                            >
                        </td>
                    </tr>
        <tr>
            <td class="c1">
              <b>Min Sessions</b><br/><span class="jive-description">Specify the minimum number of chats for a workgroup member.</span>
            </td>
                  <td class="c2">
                            <input type="text" name="minChats" value="<%= minChats %>"
                             size="5" maxlength="5">
                        </td>
                    </tr>

        <tr>
            <td class="c1">
                <b>Request timeout</b><br/><span class="jive-description">Total time a user will be in a queue before timing out.</span>
            </td>
  <td class="c2">
                            <input type="text" name="requestTimeout" value="<%=requestTimeout%>"
                             size="5" maxlength="10"> seconds
      </td>

        </tr>
        <tr>
            <td class="c1">
                <b>Offer Timeout</b><br/><span class="jive-description">Amount of time each member has to answer an incoming request.</span>
            </td>
            <td class="c2">

                            <input type="text" name="offerTimeout" value="<%= offerTimeout %>"
                             size="5" maxlength="10"> seconds
                        </td>
                    </tr>


        <tr>
            <td class="c1">
                <b>Web authentication</b><br/><span class="jive-description">If checked, requires user to have a valid Openfire account.</span>
            </td>
            <td class="c2">
                <table cellpadding="3" cellspacing="0" border="0" width="100%">
                <tbody>
                    <input type="checkbox" name="authRequired" <%= (authRequired ? "checked" : "") %>>
                </tbody>
                </table>
            </td>
        </tr>
    </table>
    <br/>
     <input type="hidden" name="wgID" value="<%= wgID %>"/>
        <input type="hidden" name="enableWorkgroup" value=""/>
        <input type="hidden" name="doEnable" value="true"/>
        <input type="submit" name="update" value="Update Workgroup" />
     </form>


    </body>
</html>




