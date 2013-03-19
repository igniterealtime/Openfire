<%--
  -	$Revision: 10204 $
  -	$Date: 2008-04-11 18:44:25 -0400 (Fri, 11 Apr 2008) $
  -
  - Copyright (C) 2004-2008 Jive Software. All rights reserved.
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     http://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
--%>
<% try { %>
<%@ page import="java.beans.*,
                 org.jivesoftware.xmpp.workgroup.dispatcher.AgentSelector,
                 org.jivesoftware.util.ParamUtils,
                 java.util.List,
                 org.jivesoftware.util.StringUtils,
                 org.jivesoftware.openfire.fastpath.util.WorkgroupUtils,
                 org.jivesoftware.util.ClassUtils,
                 org.jivesoftware.util.Log"
    errorPage="workgroup-error.jsp"
%>
<% // Get parameters
    boolean addAlgorithm = ParamUtils.getBooleanParameter(request, "addAlgorithm");
    String newClassname = ParamUtils.getParameter(request, "newClassname");

    String error = "";
    String errorMessage = ParamUtils.getParameter(request, "errorMessage");

    // Add a new interceptor to the list of installable algorithms
    if (addAlgorithm) {
        try {
            if (newClassname != null) {
                // Load the specified class, make sure it's an insance of the interceptor class:
                Class c = ClassUtils.forName(newClassname.trim());
                Object obj = c.newInstance();
                if (obj instanceof AgentSelector) {
                    WorkgroupUtils.addAgentSelectorClass(c);
                }
                else {
                    error = newClassname.trim() + " is not an AgentSelector";
                }
            }
            else {
                error = "You must specify an AgentSelector class to load.";
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
        String redirect = "agent-selectors.jsp?errorMessage=" + error;
        response.sendRedirect(redirect);
        return;
    }

%>

<html>
    <head>
        <title>Dispatcher Settings</title>
        <meta name="pageID" content="member-selectors"/>
        <!--<meta name="helpPage" content="configure_global_dispatcher_settings.html"/>-->

        <script language="JavaScript" type="text/javascript">
        var algorithmInfo = new Array(
<%	    int i = 0;
        List<AgentSelector> availableAgentSelectors = WorkgroupUtils.getAvailableAgentSelectors();
        for(AgentSelector agentSelector : availableAgentSelectors){

            try {
                BeanDescriptor descriptor = (Introspector.getBeanInfo(agentSelector.getClass())).getBeanDescriptor();
%>
            new Array(
                "<%= descriptor.getBeanClass().getName() %>",
                "<%= descriptor.getValue("version") %>",
                "<%= descriptor.getValue("author") %>",
                "<%= StringUtils.replace(descriptor.getShortDescription(), "\"", "\\\"") %>"
            )
<%          if ((availableAgentSelectors.size() - i) > 1) { %>
                ,
<%	        }
                } catch (Exception e) {}
                 i++;
            }
%>
        );
        function properties(theForm) {
            var className = theForm.algorithms.options[theForm.algorithms.selectedIndex].value;
            var selected = 0;
            for (selected=0; selected<algorithmInfo.length; selected++) {
                if (algorithmInfo[selected][0] == className) {
                    var version = algorithmInfo[selected][1];
                    var author = algorithmInfo[selected][2];
                    var description = algorithmInfo[selected][3];
                    theForm.version.value = ((version=="null")?"":version);
                    theForm.author.value = ((author=="null")?"":author);
                    theForm.description.value = ((description=="null")?"":description);
                    break;
                }
            }
        }
        </script>
    </head>
    <body>

<span>

<p>Below is a list of available algorithms for choosing the best agent in a queue that may
receive an offer. Use the form below to install new algorithms.
</p>

</span>

<p>

<%  // Print out a message if one exists
    String oneTimeMessage = errorMessage;
    if (oneTimeMessage != null) {
%>
    <font size="-1" color="#ff0000">
    <p><i><%= oneTimeMessage %></i></p>

<%  }
%>

<p>

<form action="agent-selectors.jsp" method="post">

<span class="jive-install-interceptor">

<table cellpadding="0" cellspacing="0" border="0">
<tr><td>
    <b>Available Algorithms</b>
    </td>
    <td>
    <a href="#" onclick="helpwin('algorithms','install_interceptor');return false;"
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
                <font size="-2" face="verdana"><b>AVAILABLE ALGORITHMS</b></font>
            </td>
        </tr>
        <tr bgcolor="#ffffff">
            <td>
                <table cellpadding="1" cellspacing="0" border="0">
                <tr>
                    <td width="48%" valign="top">
                        <select size="8" name="algorithms" onchange="properties(this.form);">
                        <%  for(AgentSelector agentSelector : WorkgroupUtils.getAvailableAgentSelectors()) {
                            BeanDescriptor descriptor = (Introspector.getBeanInfo(agentSelector.getClass())).getBeanDescriptor();
                        %>
                            <option value="<%= descriptor.getBeanClass().getName() %>"
                             ><%= descriptor.getDisplayName() %>

                        <%  } %>
                        </select>
                        <br>
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
                </table>
            </td>
        </tr>
        </table>
    </td></tr>
    </table>
</ul>

</span>

</form>

<form action="agent-selectors.jsp">
<input type="hidden" name="addAlgorithm" value="true">
<table cellpadding="0" cellspacing="0" border="0">
<tr><td>
    <b>Add Algorithm Class</b>
    </td>
    <td>
    <a href="#" onclick="helpwin('algorithms','add_algorithm_class');return false;"
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
    	<td><input type="submit" value="Add Algorithm"></td>
    </tr>
    </table>
</ul>
</form>

<p>


</body>
</html>
<% } catch(Exception ex){ex.printStackTrace(); } %>

