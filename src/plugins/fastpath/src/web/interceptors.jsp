<%--
  -	$Revision: 10204 $
  -	$Date: 2008-04-11 18:44:25 -0400 (Fri, 11 Apr 2008) $
  -
  - Copyright (C) 2004-2008 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution, or a commercial license
  - agreement with Jive.
--%>
<% try { %>
<%@ page import="java.beans.*,
                 java.util.*,
                 java.lang.reflect.Method,
                 org.jivesoftware.xmpp.workgroup.WorkgroupManager,
                 org.jivesoftware.xmpp.workgroup.Workgroup,
                 org.xmpp.packet.JID,
                 org.jivesoftware.xmpp.workgroup.interceptor.*,
                 org.jivesoftware.util.*"
    errorPage="workgroup-error.jsp"%>

<%! // Global variables/methods for this page

    private boolean isInstalledInterceptor(Workgroup workgroup,
            InterceptorManager interceptorManager, PacketInterceptor interceptor) {
        try {
            if (interceptor == null) {
                return false;
            }
            String interceptorClassname = interceptor.getClass().getName();
            List<PacketInterceptor> interceptors = workgroup == null ? interceptorManager
                    .getInterceptors()
                    : interceptorManager.getInterceptors(workgroup.getJID().toBareJID());
            for (PacketInterceptor installedInterceptor : interceptors) {
                if (interceptorClassname.equals(installedInterceptor.getClass().getName())) {
                    return true;
                }
            }
        }
        catch (Exception ignored) {
        }
        return false;
    }

    private String getHTML(PacketInterceptor interceptor, PropertyDescriptor descriptor) {
        // HTML of the customizer for this property
        StringBuffer html = new StringBuffer(50);
        // Get the name of the property (this becomes the name of the form element)
        String propName = descriptor.getName();
        // Get the current value of the property
        Object propValue = null;
        try {
            propValue = descriptor.getReadMethod().invoke(interceptor, (Object[])null);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        // Get the classname of this property
        String className = descriptor.getPropertyType().getName();

        // HTML form elements for number values (rendered as small textfields)
        if ("int".equals(className)
                || "double".equals(className)
                || "long".equals(className)) {
            html.append("<input type=\"text\" name=\"").append(propName)
                    .append("\" size=\"6\" maxlength=\"10\"");
            if (propValue != null) {
                html.append(" value=\"").append(propValue.toString()).append("\"");
            }
            html.append(">");
        }
        // HTML form elements for boolean values (rendered as Yes/No radio buttons)
        else if ("boolean".equals(className)) {
            boolean value = false;
            if ("true".equals(propValue.toString())) {
                value = true;
            }
            html.append("<input type=\"radio\" name=\"").append(propName).append("\" id=\"rb")
                    .append(propName).append("1\" ");
            html.append("value=\"true\"");
            html.append((value) ? " checked" : "");
            html.append("> <label for=\"rb").append(propName).append("1\">Yes</label> ");
            html.append("<input type=\"radio\" name=\"").append(propName).append("\" id=\"rb")
                    .append(propName).append("2\" ");
            html.append("value=\"false\"");
            html.append((!value) ? " checked" : "");
            html.append("> <label for=\"rb").append(propName).append("2\">No</label> ");
        }
        else if ("java.lang.String".equals(className)) {
            // Indicates we should print a textarea if the large text field is specified to be used
            boolean useLarge = ("true".equals(descriptor.getValue("useLargeTextField")));

            // HTML elements for a String or String[] (rendered as a single-line textarea)
            if (descriptor.getPropertyType().isArray()) {
                // Print out a customizer for a String array:
                String[] valArray = (String[])propValue;
                for (int i = 0; i < valArray.length; i++) {
                    html.append(printStringHTML(propName + i, valArray[i], useLarge));
                    html.append("<input type=\"submit\" name=\"deletePropEntry")
                            .append(i).append("\" value=\"Delete\">")
                            .append("<br>");
                }
                html.append("<br>");

                html.append(printStringHTML(propName, null, useLarge));

                html.append("<input type=\"hidden\" name=\"addNewPropName");
                html.append("\" value=\"").append(propName).append("\">");
                html.append("<input type=\"submit\" name=\"addNewProp\" ");
                html.append("value=\"Add\">");
                html.append("<input type=\"hidden\" name=\"deletePropertyName");
                html.append("\" value=\"").append(propName).append("\">");
            }
            // Else, it's just a POS (plain old String) :)
            else {
                if (propName.toLowerCase().equals("password")) {
                    html.append("<input type=\"password\"").append(" name=\"").append(propName);
                    html.append("\" size=\"30\" maxlength=\"150\"");
                    if (propValue != null) {
                        html.append(" value=\"").append(escapeHTML(propValue.toString()))
                                .append("\"");
                    }
                    html.append(">");
                }
                else {
                    String value = null;
                    if (propValue != null) {
                        value = propValue.toString();
                    }
                    html.append(printStringHTML(propName, value, useLarge));
                }
            }
        }
        if (html.length() == 0) {
            html.append("&nbsp;");
        }
        return html.toString();
    }

    // Handles printing a string text field either as a textfield or a textarea.
    private String printStringHTML(String name, String value, boolean useLarge) {
        StringBuffer buf = new StringBuffer(50);
        if (useLarge) {
            buf.append("<textarea name=\"").append(name).append("\" cols=\"40\" rows=\"3\">");
            if (value != null) {
                buf.append(escapeHTML(value));
            }
            buf.append("</textarea>");
        }
        else {
            buf.append("<input type=\"text\" name=\"").append(name)
                    .append("\" size=\"40\" maxlength=\"255\" ");
            if (value != null) {
                buf.append("value=\"").append(escapeHTML(value)).append("\"");
            }
            buf.append(">");
        }
        return buf.toString();
    }

    private Map getInterceptorPropertyValues(HttpServletRequest request,
            PacketInterceptor interceptor) {
        // Map of interceptor property name/value pairs
        Map map = new HashMap();
        try {
            // Property descriptors
            PropertyDescriptor[] descriptors = BeanUtils
                    .getPropertyDescriptors(interceptor.getClass());
            // Loop through the properties, get the value of the property as a
            // parameter from the HttpRequest object
            for (int i = 0; i < descriptors.length; i++) {
                // Don't set any array properties:
                if (!descriptors[i].getPropertyType().isArray()) {
                    String propName = descriptors[i].getName();
                    String propValue = ParamUtils.getParameter(request, propName);
                    map.put(propName, propValue);
                }
            }
        }
        catch (Exception e) {
        }
        return map;
    }

    private String escapeHTML(String html) {
        html = StringUtils.replace(html, "\"", "&quot;");
        return StringUtils.escapeHTMLTags(html);
    }
%>

<% // Get parameters
    String workgroupID = ParamUtils.getParameter(request, "wgID");
    String managerType = ParamUtils.getParameter(request, "managerType");
    String classname = ParamUtils.getParameter(request, "interceptors");
    boolean install = ParamUtils.getBooleanParameter(request, "install");
    boolean remove = ParamUtils.getBooleanParameter(request, "remove");
    int position = ParamUtils.getIntParameter(request, "pos", -1);
    boolean edit = ParamUtils.getBooleanParameter(request, "edit");
    boolean addInterceptor = ParamUtils.getBooleanParameter(request, "addInterceptor");
    String newClassname = ParamUtils.getParameter(request, "newClassname");
    boolean saveProperties = ParamUtils.getBooleanParameter(request, "saveProperties");
    int interceptorIndex = ParamUtils.getIntParameter(request, "interceptorIndex", -1);
    boolean changePosition = ParamUtils.getBooleanParameter(request, "changePos");
    boolean up = ParamUtils.getBooleanParameter(request, "up");
    boolean down = ParamUtils.getBooleanParameter(request, "down");
    String deletePropertyName = ParamUtils.getParameter(request, "deletePropertyName");
    boolean addNewProp = request.getParameter("addNewProp") != null;

    String error = "";
    String errorMessage = ParamUtils.getParameter(request, "errorMessage");

    // Determine if we need to delete a String[] property entry
    boolean deletePropEntry = false;
    int deleteIndex = -1;

    for (Enumeration e = request.getParameterNames(); e.hasMoreElements();) {
        String name = (String)e.nextElement();
        if (name.startsWith("deletePropEntry")) {
            try {
                int pos = "deletePropEntry".length();
                deleteIndex = Integer.parseInt(name.substring(pos, name.length()));
            }
            catch (Exception ignored) {
            }
            if (deleteIndex > -1) {
                deletePropEntry = true;
                break;
            }
        }
    }

    // Indicate if we're doing global interceptors
    boolean isGlobal = (workgroupID == null);

    WorkgroupManager workgroupManager = WorkgroupManager.getInstance();

    // Load the workgroup
    Workgroup workgroup = isGlobal ? null : workgroupManager.getWorkgroup(new JID(workgroupID));

    // Get the interceptor manager
    InterceptorManager interceptorManager = null;
    if (managerType == null) {
        managerType = "workgroup";
    }
    if ("workgroup".equals(managerType)) {
        interceptorManager = WorkgroupInterceptorManager.getInstance();
    }
    else if ("agent".equals(managerType)) {
        interceptorManager = AgentInterceptorManager.getInstance();
    }
    else if ("chatbot".equals(managerType)) {
        interceptorManager = ChatbotInterceptorManager.getInstance();
    }
    else if ("queue".equals(managerType)) {
        interceptorManager = QueueInterceptorManager.getInstance();
    }
    else if ("room".equals(managerType)) {
        interceptorManager = RoomInterceptorManager.getInstance();
    }
    else if ("offer".equals(managerType)) {
        interceptorManager = OfferInterceptorManager.getInstance();
    }

    // Add a new property for a String[] property type:
    if (addNewProp) {
        // Get the name of the interceptor for the new property:
        String newPropName = ParamUtils.getParameter(request, "addNewPropName");
        if (newPropName != null) {
            // Get the value of the new property:
            String newPropValue = ParamUtils.getParameter(request, "addNewProp" + newPropName);
            if (newPropValue != null) {
                // The interceptor we're working with:
                PacketInterceptor interceptor = (isGlobal ? interceptorManager
                        .getInterceptor(interceptorIndex) : interceptorManager
                        .getInterceptor(workgroup.getJID().toBareJID(), interceptorIndex));
                PropertyDescriptor[] descriptors = (Introspector.getBeanInfo(
                        interceptor.getClass())).getPropertyDescriptors();
                PropertyDescriptor propDescriptor = null;
                // Look for the property specified
                for (int i = 0; i < descriptors.length; i++) {
                    if (descriptors[i].getName().equals(newPropName)) {
                        propDescriptor = descriptors[i];
                        break;
                    }
                }
                if (propDescriptor != null) {
                    // Get both the read and write methods:
                    Method readMethod = propDescriptor.getReadMethod();
                    Method writeMethod = propDescriptor.getWriteMethod();
                    // Get the String[] via the read method:
                    String[] entries = (String[])readMethod.invoke(interceptor, (Object[])null);
                    // Make a new entry array of entries.length+1 because we're
                    // adding one more entry to the property
                    String[] newEntries = new String[entries.length + 1];
                    for (int i = 0; i < entries.length; i++) {
                        newEntries[i] = entries[i];
                    }
                    // The new prop value goes in the last spot of newEntries:
                    newEntries[newEntries.length - 1] = newPropValue;
                    // Use the write method to save the new entries:
                    writeMethod.invoke(interceptor, new Object[]{newEntries});
                    // Save interceptors
                    interceptorManager.saveInterceptors();
                    // Done, so redirect
                    StringBuffer url = new StringBuffer();
                    url.append("interceptors.jsp?edit=true&pos=").append(interceptorIndex)
                            .append("&managerType=").append(managerType);
                    if (!isGlobal) {
                        url.append("&wgID=" + workgroupID);
                    }
                    response.sendRedirect(url.toString());
                    return;
                }
            }
        }
    }

    // Remove one of the String[] prop entries:
    if (deletePropEntry) {
        if (deletePropertyName != null) {
            // The interceptor we're working with:
            PacketInterceptor interceptor = (isGlobal ? interceptorManager
                    .getInterceptor(interceptorIndex) : interceptorManager
                    .getInterceptor(workgroup.getJID().toBareJID(), interceptorIndex));
            PropertyDescriptor[] descriptors = (Introspector.getBeanInfo(interceptor.getClass()))
                    .getPropertyDescriptors();
            PropertyDescriptor propDescriptor = null;
            // Look for the property specified
            for (int i = 0; i < descriptors.length; i++) {
                if (descriptors[i].getName().equals(deletePropertyName)) {
                    propDescriptor = descriptors[i];
                    break;
                }
            }
            if (propDescriptor != null) {
                // Get both the read and write methods:
                Method readMethod = propDescriptor.getReadMethod();
                Method writeMethod = propDescriptor.getWriteMethod();
                // Get the String[] via the read method:
                String[] entries = (String[])readMethod.invoke(interceptor, (Object[])null);
                // Make a new entry array of entries.length+1 because we're
                // adding one more entry to the property
                String[] newEntries = new String[entries.length - 1];
                int offset = 0;
                for (int i = 0; i < newEntries.length; i++) {
                    // Skip the index of the item we want to delete
                    if (i == deleteIndex) {
                        offset++;
                    }
                    newEntries[i] = entries[i + offset];
                }
                // Use the write method to save the new entries:
                writeMethod.invoke(interceptor, new Object[]{newEntries});
                // Save interceptors
                interceptorManager.saveInterceptors();
                // Done, so redirect
                StringBuffer url = new StringBuffer();
                url.append("interceptors.jsp?edit=true&pos=").append(interceptorIndex)
                        .append("&managerType=").append(managerType);
                if (!isGlobal) {
                    url.append("&wgID=" + workgroupID);
                }
                response.sendRedirect(url.toString());
                return;
            }
        }
    }

    // Save interceptor properties
    if (saveProperties) {
        if (interceptorIndex >= 0) {
            // The interceptor we're working with
            PacketInterceptor interceptor = (isGlobal ? interceptorManager
                    .getInterceptor(interceptorIndex) : interceptorManager
                    .getInterceptor(workgroup.getJID().toBareJID(), interceptorIndex));
            // A map of name/value pairs. The names are the names of the bean
            // properties and the values come as parameters to this page
            Map properties = getInterceptorPropertyValues(request, interceptor);
            // Set the properties
            BeanUtils.setProperties(interceptor, properties);
            // Save the interceptors
            interceptorManager.saveInterceptors();

            // Add the new Queue
            //String queueID = request.getParameter("queues");
            //try {
            //long qID = Long.valueOf(queueID).longValue();
            //requestRouter.setRoutingQueue(qID);
            //}
            //catch (NumberFormatException e) {
            //e.printStackTrace();
            //}
            //routingUtils.saveWorkgroupRouters(workgroup);

            // Done, so redirect to this page
            String redirect = "interceptors.jsp?managerType=" + managerType;
            if (!isGlobal) {
                redirect += "&wgID=" + workgroupID;
            }
            response.sendRedirect(redirect);
            return;
        }
    }

    // Add a new interceptor to the list of installable interceptors
    if (addInterceptor) {
        try {
            if (newClassname != null) {
                // Load the specified class, make sure it's an insance of the interceptor class:
                Class c = ClassUtils.forName(newClassname.trim());
                Object obj = c.newInstance();
                if (obj instanceof PacketInterceptor) {
                    interceptorManager.addInterceptorClass(c);
                }
                else {
                    error = newClassname.trim() + " is not a Packet Interceptor";
                }
            }
            else {
                error = "You must specify a Packet Interceptorr class to load.";
            }
        }
        catch (ClassNotFoundException cnfe) {
            error = newClassname.trim() + " is not a valid classname";
        }

        catch (InstantiationException ie) {
            error = newClassname.trim() + " must have a valid constructor";
        }
        catch (Exception e) {
            Log.error(e);
            error = "Could not load class " + newClassname.trim();
        }
        String redirect = "interceptors.jsp?errorMessage=" + error + "&managerType=" + managerType;
        if (!isGlobal) {
            redirect += "&wgID=" + workgroupID;
        }
        response.sendRedirect(redirect);
        return;
    }

    // Change the position of an interceptor
    if (changePosition) {
        if (interceptorIndex >= 0) {
            // Get the interceptor at the specified interceptor position
            if (isGlobal) {
                PacketInterceptor interceptor = interceptorManager.getInterceptor(interceptorIndex);
                // Re-add it based on the "direction" we're doing. First, remove it:
                interceptorManager.removeInterceptor(interceptor);
                if (up) {
                    interceptorManager.addInterceptor(interceptorIndex - 1, interceptor);
                }
                if (down) {
                    interceptorManager.addInterceptor(interceptorIndex + 1, interceptor);
                }
            }
            else {
                PacketInterceptor interceptor = interceptorManager
                        .getInterceptor(workgroup.getJID().toBareJID(), interceptorIndex);
                // Re-add it based on the "direction" we're doing. First, remove it:
                interceptorManager.removeInterceptor(workgroup.getJID().toBareJID(), interceptor);
                if (up) {
                    interceptorManager.addInterceptor(workgroup.getJID().toBareJID(),
                            interceptorIndex - 1, interceptor);
                }
                if (down) {
                    interceptorManager.addInterceptor(workgroup.getJID().toBareJID(),
                            interceptorIndex + 1, interceptor);
                }
            }
            // done, so redirect
            String redirect = "interceptors.jsp?managerType=" + managerType;
            if (!isGlobal) {
                redirect += "&wgID=" + workgroupID;
            }
            response.sendRedirect(redirect);
            return;
        }
    }

    // Number of installed interceptors
    List<PacketInterceptor> activeInterceptors = isGlobal ? interceptorManager.getInterceptors()
            : interceptorManager.getInterceptors(workgroup.getJID().toBareJID());
    int interceptorCount = activeInterceptors.size();
    // All interceptor classes

    if (install && classname != null) {
        try {
            Class interceptorClass = ClassUtils.forName(classname);
            PacketInterceptor newInterceptor = (PacketInterceptor)interceptorClass.newInstance();
            if (isGlobal) {
                interceptorManager.addInterceptor(0, newInterceptor);
            }
            else {
                interceptorManager
                        .addInterceptor(workgroup.getJID().toBareJID(), 0, newInterceptor);
            }
            String redirect = "interceptors.jsp?managerType=" + managerType;
            if (!isGlobal) {
                redirect += "&wgID=" + workgroupID;
            }
            response.sendRedirect(redirect);
            return;
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    if (remove && position > -1) {
        if (isGlobal) {
            PacketInterceptor interceptor = interceptorManager.getInterceptor(position);
            interceptorManager.removeInterceptor(interceptor);
        }
        else {
            PacketInterceptor interceptor = interceptorManager
                    .getInterceptor(workgroup.getJID().toBareJID(), position);
            interceptorManager.removeInterceptor(workgroup.getJID().toBareJID(), interceptor);
        }
        String redirect = "interceptors.jsp?managerType=" + managerType;
        if (!isGlobal) {
            redirect += "&wgID=" + workgroupID;
        }
        response.sendRedirect(redirect);
        return;
    }

%>

<html>
    <head>
<%
    if (isGlobal) { %>
        <title>Global Packet Interceptors</title>
        <meta name="pageID" content="settings-interceptors"/>
    <%  } else { %>
        <title><%= "Packet Interceptors for " + workgroupID%></title>
        <meta name="subPageID" content="workgroup-interceptors"/>
        <meta name="extraParams" content="<%= "wgID="+workgroupID %>"/>

    <% } %>
        <!--<meta name="helpPage" content="edit_or_uninstall_global_interceptors.html"/>-->
    </head>
    <body>

<span>

<p>Interceptors examine packets before they enter the system and can modify or reject them. Use
the forms below to install and customize <%= isGlobal ? "global" : "local"%> interceptors.
<% if ("workgroup".equals(managerType)) { %>
Current interceptors will be invoked every time a packet is sent to <%= isGlobal ? "a" : "the"%>
workgroup or <%= isGlobal ? "a" : "the"%> workgroup is sending a packet to a user or an agent.
    <% }
    else if ("agent".equals(managerType)) { %>
Current interceptors will be invoked every time an agent sends a presence to
<%= isGlobal ? "a" : "the"%> workgroup.
    <% }
    else if ("chatbot".equals(managerType)) { %>
Current interceptors will be invoked every time the chatbot of <%= isGlobal ? "a" : "the"%>
workgroup receives or sends a packet.
    <% }
    else if ("queue".equals(managerType)) { %>
Current interceptors will be invoked every time a user tries to join a queue of
<%= isGlobal ? "a" : "the"%> workgroup.
    <% }
    else if ("room".equals(managerType)) { %>
Current interceptors will be invoked when sending packets for creating a room, configuring a room
or sending room invitations to an agent or a user.
    <% }
    else if ("offer".equals(managerType)) { %>
Current interceptors will be invoked when sending an offer to an agent or when an agent accepts
or rejects an offer of <%= isGlobal ? "a" : "the"%> workgroup.
    <% } %>
</p>

</span>

<p>

<script language="JavaScript" type="text/javascript">
var routerInfo = new Array(
<%	int i = 0;
    for(PacketInterceptor interceptor : interceptorManager.getAvailableInterceptors()){

        try {
            BeanDescriptor descriptor = (Introspector.getBeanInfo(interceptor.getClass())).getBeanDescriptor();
%>
    new Array(
        "<%= descriptor.getBeanClass().getName() %>",
        "<%= descriptor.getValue("version") %>",
        "<%= descriptor.getValue("author") %>",
        "<%= StringUtils.replace(descriptor.getShortDescription(), "\"", "\\\"") %>"
    )
<%          if ((interceptorManager.getAvailableInterceptors().size() - i) > 1) { %>
		,
<%	        }
        } catch (Exception e) {}
         i++;
    }
%>
);
function properties(theForm) {
    var className = theForm.interceptors.options[theForm.interceptors.selectedIndex].value;
    var selected = 0;
    for (selected=0; selected<routerInfo.length; selected++) {
        if (routerInfo[selected][0] == className) {
            var version = routerInfo[selected][1];
            var author = routerInfo[selected][2];
            var description = routerInfo[selected][3];
            theForm.version.value = ((version=="null")?"":version);
            theForm.author.value = ((author=="null")?"":author);
            theForm.description.value = ((description=="null")?"":description);
            break;
        }
    }
}
</script>

<form action="interceptors.jsp">
<% if (!isGlobal) { %>
    <input type="hidden" name="wgID" value="<%= workgroupID %>">
<% } %>
Configure interceptors of the realm:
<select name="managerType" onchange="this.form.submit();">
        <option value="workgroup" <% if ("workgroup".equals(managerType)) out.write("selected");%>>Workgroup</option>
        <option value="queue" <% if ("queue".equals(managerType)) out.write("selected");%>>Queue</option>
        <option value="offer" <% if ("offer".equals(managerType)) out.write("selected");%>>Offer</option>
        <option value="room" <% if ("room".equals(managerType)) out.write("selected");%>>Room</option>
        <option value="chatbot" <% if ("chatbot".equals(managerType)) out.write("selected");%>>Chatbot</option>
        <option value="agent" <% if ("agent".equals(managerType)) out.write("selected");%>>Agent</option>
    </select>
</form>

<%  // Print out a message if one exists
    String oneTimeMessage = errorMessage;
    if (oneTimeMessage != null) {
%>
    <font size="-1" color="#ff0000">
    <p><i><%= oneTimeMessage %></i></p>

<%  }
%>

<%  // Colors
    String red = "#ffeeee";
    String yellow = "#ffffee";
    i=0;

    if (interceptorCount > 0) {
%>
<table cellpadding="0" cellspacing="0" border="0">
<tr><td>
    <b>Current Interceptors</b>
    </td>
    <td>
    <a href="#" onclick="helpwin('interceptors','current_interceptors');return false;"
     title="Click for help"
     ><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8" alt="" /></a>
    </td>
</tr>
</table><br>
<ul>
	<table bgcolor="#aaaaaa" cellpadding="0" cellspacing="0" border="0">
    <tr><td>
    <table cellpadding="4" cellspacing="1" border="0" width="100%">
	<tr bgcolor="#eeeeee">
	<td align="center"><font size="-2" face="verdana"><b>ORDER</b></font></td>
	<td align="center"><font size="-2" face="verdana"><b>NAME</b></font></td>
	<td align="center"><font size="-2" face="verdana"><b>DESCRIPTION</b></font></td>
    <%  if (interceptorCount > 1) { %>
	<td align="center"><font size="-2" face="verdana"><b>MOVE</b></font></td>
    <%  } %>
	<td align="center"><font size="-2" face="verdana"><b>EDIT</b></font></td>
	<td align="center"><font size="-2" face="verdana"><b>DELETE</b></font></td>
    </tr>
<%  // Loop through all interceptors
    for (PacketInterceptor interceptor : activeInterceptors) {
        try {
            // Descriptor for this interceptor
            BeanDescriptor descriptor = (Introspector.getBeanInfo(interceptor.getClass())).getBeanDescriptor();
            // Properties for this interceptor
            PropertyDescriptor[] descriptors = BeanUtils.getPropertyDescriptors(interceptor.getClass());
            // Version of this interceptor
            String version = (String)descriptor.getValue("version");
            // Description of this interceptor
            String description = StringUtils.escapeHTMLTags(descriptor.getShortDescription());
%>
    <tr bgcolor="#ffffff">
        <td><%= (i+1) %></td>
        <td nowrap alt="dd"><%= descriptor.getDisplayName() %></td>
        <td><%= (description!=null)?description:"&nbsp;" %></td>
        <%  if (interceptorCount > 1) { %>
        <td nowrap>
            <%  if ((i+1)<interceptorCount) { %>
                <a href="interceptors.jsp?changePos=true&down=true&interceptorIndex=<%= i %>&managerType=<%=managerType%><%= isGlobal ? "" : "&wgID="+workgroupID %>"
                ><img src="images/arrow_down.gif" width="16" height="16" alt="Move this interceptor down." border="0"></a>
            <%  } else { %>
                <img src="images/blank.gif" width="16" height="16" border="0" alt="" />
            <%  } %>

            <%  if (i != 0) { %>
                <a href="interceptors.jsp?changePos=true&up=true&interceptorIndex=<%= i %>&managerType=<%=managerType%><%= isGlobal ? "" : "&wgID="+workgroupID %>"
                ><img src="images/arrow_up.gif" width="16" height="16" alt="Move this interceptor up." border="0"></a>
            <%  } else { %>
                <img src="images/blank.gif" width="16" height="16" border="0" alt="" />
            <%  } %>
        </td>
        <%  } %>
        <td align="center">
            <a href="interceptors.jsp?edit=true&pos=<%= i %>&managerType=<%=managerType%><%= isGlobal ? "" : "&wgID="+workgroupID %>"
            ><img src="images/edit-16x16.gif" width="16" height="16" alt="Edit the properties of this interceptor" border="0"
            ></a>
        </td>
        <td align="center">
            <a href="interceptors.jsp?remove=true&pos=<%= i %>&managerType=<%=managerType%><%= isGlobal ? "" : "&wgID="+workgroupID %>"
            ><img src="images/delete-16x16.gif" width="16" height="16" alt="Delete this interceptor" border="0"
            ></a>
        </td>
    </tr>
<%  if (position == i && edit) { %>
    <form action="interceptors.jsp" method="post">
    <% if (!isGlobal) { %>
        <input type="hidden" name="wgID" value="<%= workgroupID %>">
    <% } %>
    <input type="hidden" name="managerType" value="<%= managerType %>">
    <input type="hidden" name="saveProperties" value="true">
    <input type="hidden" name="interceptorIndex" value="<%= i %>">
    <tr bgcolor="#ffffff">
        <td>&nbsp;</td>
        <td colspan="<%= (interceptorCount > 1)?"5":"4" %>">
            <table cellpadding="2" cellspacing="0" border="0" width="100%">
            <%  int color = 1;
                for (int j=0; j<descriptors.length; j++) {
                    color ++;
                    boolean isString = "java.lang.String".equals(descriptors[j].getPropertyType().getName());
                    if (isString) {
            %>
                    <tr bgcolor=<%= (color%2==0)?"#f4f5f7":"#ffffff" %>>
                        <td colspan="3">
                            <%= descriptors[j].getDisplayName() %>
                            <br>
                            <font size="-2"><%= descriptors[j].getShortDescription() %></font>
                        </td>
                    </tr>
                    <tr bgcolor=<%= (color%2==0)?"#f4f5f7":"#ffffff" %>>
                        <td colspan="3">
                            <%= getHTML(interceptor, descriptors[j]) %>
                        </td>
                    </tr>
                <%  } else { %>
                    <tr bgcolor=<%= (color%2==0)?"#f4f5f7":"#ffffff" %>>
                        <td width="70%">
                            <%= descriptors[j].getDisplayName() %>
                            <br>
                            <font size="-2"><%= descriptors[j].getShortDescription() %></font>
                        </td>
                        <td width="10%">&nbsp;</td>
                        <td width="10%" nowrap>
                            <%= getHTML(interceptor, descriptors[j]) %>
                        </td>
                    </tr>
            <%      }
                }
            %>
            <tr>
                <td colspan="4" align="right">

                    <input type="submit" value="Save Properties">

                </td>
            </tr>
            </table>
        </td>
    </tr>
    </form>
<%  } %>
<%      } catch (Exception e) { }
         i++;
    }
%>
    </table>
    </td></tr>
    </table>
    <br>

<%  } %>
</ul>

<p>

<form action="interceptors.jsp" method="post">
<% if (!isGlobal) { %>
    <input type="hidden" name="wgID" value="<%= workgroupID %>">
<% } %>
<input type="hidden" name="managerType" value="<%= managerType %>">
<input type="hidden" name="install" value="true">

<span class="jive-install-interceptor">

<table cellpadding="0" cellspacing="0" border="0">
<tr><td>
    <b>Install Interceptor</b>
    </td>
    <td>
    <a href="#" onclick="helpwin('interceptors','install_interceptor');return false;"
     title="Click for help"
     ><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8" alt="" /></a>
    </td>
</tr>
</table><br>

<ul>
	<table bgcolor="#aaaaaa" cellpadding="0" cellspacing="0" border="0" width="1%">
    <tr><td>
        <table cellpadding="4" cellspacing="1" border="0" width="100%">
        <tr bgcolor="#eeeeee">
            <td align="center">
                <font size="-2" face="verdana"><b>AVAILABLE INTERCEPTORS</b></font>
            </td>
        </tr>
        <tr bgcolor="#ffffff">
            <td>
                <table cellpadding="1" cellspacing="0" border="0">
                <tr>
                    <td width="48%" valign="top">
                        <select size="8" name="interceptors" onchange="properties(this.form);">
                        <%  for(PacketInterceptor interceptor : interceptorManager.getAvailableInterceptors()) {
                            boolean isInstalled = isInstalledInterceptor(workgroup, interceptorManager, interceptor);
                            BeanDescriptor descriptor = (Introspector.getBeanInfo(interceptor.getClass())).getBeanDescriptor();
                        %>
                            <option value="<%= descriptor.getBeanClass().getName() %>"
                             ><%= descriptor.getDisplayName() %>

                            <%  if (isInstalled) { %>

                                *

                            <%  } %>

                        <%  } %>
                        </select>
                        <br>
                        (A * denotes the interceptor is already installed. You can install the
                        same interceptor more than once.)
                    </td>
                    <td width="2%"><img src="images/blank.gif" width="5" height="1" border="0" alt="" /></td>
                    <td width="48%" valign="top">

                        <table cellpadding="2" cellspacing="0" border="0" width="100%">
                        <tr>
                            <td><font size="-2">VERSION</font></td>
                            <td><input type="text" size="20" name="version" style="width:100%"></td>
                        </tr>
                        <tr>
                            <td><font size="-2">AUTHOR</font></td>
                            <td><input type="text" size="20" name="author" style="width:100%"></td>
                        </tr>
                        <tr>
                            <td valign="top"><font size="-2">DESCRIPTION</font></td>
                            <td><textarea name="description" cols="20" rows="5" wrap="virtual"></textarea></td>
                        </tr>
                        </table>

                    </td>
                </tr>
                <tr>
                    <td colspan="3" align="center">

                        <input type="submit" value="Install">

                    </td>
                </tr>
                </table>
            </td>
        </tr>
        </table>
    </td></tr>
    </table>
</ul>

</span>

</form>

<form action="interceptors.jsp">
<input type="hidden" name="addInterceptor" value="true">
<% if (!isGlobal) { %>
    <input type="hidden" name="wgID" value="<%= workgroupID %>">
<% } %>
<input type="hidden" name="managerType" value="<%= managerType %>">
<table cellpadding="0" cellspacing="0" border="0">
<tr><td>
    <b>Add Interceptor Class</b>
    </td>
    <td>
    <a href="#" onclick="helpwin('interceptors','add_interceptor_class');return false;"
     title="Click for help"
     ><img src="images/help-16x16.gif" width="16" height="16" border="0" hspace="8" alt="" /></a>
    </td>
</tr>
</table><br>
<ul>
    <table cellpadding="2" cellspacing="0" border="0">
    <tr>
    	<td>Class Name:</td>
    	<td><input type="text" name="newClassname" value="" size="30" maxlength="100"></td>
    	<td><input type="submit" value="Add Interceptor"></td>
    </tr>
    </table>
</ul>
</form>

<p>


</body>
</html>
<% } catch(Exception ex){ex.printStackTrace(); } %>

