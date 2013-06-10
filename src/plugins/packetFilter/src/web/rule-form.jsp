<%@page import="org.jivesoftware.openfire.plugin.PacketFilterConstants"%>
<%@ page import="org.jivesoftware.openfire.XMPPServer,
                 org.jivesoftware.openfire.group.Group"
        %>
<%@ page import="org.jivesoftware.openfire.plugin.component.ComponentList" %>
<%@ page import="org.jivesoftware.openfire.plugin.rules.*" %>
<%@ page import="org.jivesoftware.openfire.user.UserManager" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.xmpp.component.Component" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.jivesoftware.openfire.RoutingTable" %>
<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"/>
<%
    webManager.init(request, response, session, application, out);
    Collection<Group> groups = webManager.getGroupManager().getGroups();

    ComponentList cList = ComponentList.getInstance();
    RuleManager rm = new RuleManagerProxy();
    Rule rule = null;
    //Get Action
    boolean create = request.getParameter("create") != null;
    boolean cancel = request.getParameter("cancel") != null;

    //Get data
    String packetAction = ParamUtils.getParameter(request, "packetAction");
    String disable = ParamUtils.getParameter(request, "disable");
    String packetType = ParamUtils.getParameter(request, "packetType");
    String source = ParamUtils.getParameter(request, "source");
    String destination = ParamUtils.getParameter(request, "destination");
    String log = ParamUtils.getParameter(request, "log");
    String description = ParamUtils.getParameter(request, "description");
    String order = ParamUtils.getParameter(request, "order");


    Rule.SourceDestType[] type = Rule.SourceDestType.values();

    Collection<String> userList = UserManager.getInstance().getUsernames();
    String serverName = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
    Collection<String> components = cList.getComponentDomains();
    
    Map<String, String> errors = new HashMap<String, String>();
    String sourceJID = "";
    String destJID = "";

    if (cancel) {
        response.sendRedirect("pf-main.jsp");
        return;
    }
    if (create) {
        if (packetAction.equals("Pass")) {
            rule = new Pass();
        } else if (packetAction.equals("Reject")) {
            rule = new Reject();
        } else if (packetAction.equals("Drop")) {
            rule = new Drop();
        } 

        if (rule != null) {
            rule.setDescription(description);
            rule.setPacketType(Rule.PacketType.valueOf(packetType));
            if (source.equals(Rule.SourceDestType.Any.toString())) {
                rule.setSource(source);
                rule.setSourceType(Rule.SourceDestType.Any);
            } else if (source.equals(Rule.SourceDestType.Other.toString())) {
                sourceJID = ParamUtils.getParameter(request, "sourceOtherJID");
                if (sourceJID == null || !(sourceJID.length() > 0)) {
                    sourceJID = "";
                    errors.put("sourceOther", "");
                }
                rule.setSource(sourceJID);
                rule.setSourceType(Rule.SourceDestType.Other);
            } else if (source.equals(Rule.SourceDestType.User.toString())) {
                sourceJID = ParamUtils.getParameter(request, "sourceUserJID");
                rule.setSource(sourceJID);
                rule.setSourceType(Rule.SourceDestType.User);
            } else if (source.equals(Rule.SourceDestType.Group.toString())) {
                sourceJID = ParamUtils.getParameter(request, "sourceGroupJID");
                rule.setSource(sourceJID);
                rule.setSourceType(Rule.SourceDestType.Group);
            } else if (source.equals(Rule.SourceDestType.Component.toString())) {
                sourceJID = ParamUtils.getParameter(request, "sourceComponentJID");
                rule.setSource(sourceJID);
                rule.setSourceType(Rule.SourceDestType.Component);
            }


            if (destination.equals(Rule.SourceDestType.Any.toString())) {
                rule.setDestination(destination);
                rule.setDestType(Rule.SourceDestType.Any);
            } else if (destination.equals(Rule.SourceDestType.Other.toString())) {
                destJID = ParamUtils.getParameter(request, "destOtherJID");
                if (destJID == null || !(destJID.length() > 0)) {
                    destJID = "";
                    errors.put("destOther", "");
                }
                rule.setDestination(destJID);
                rule.setDestType(Rule.SourceDestType.Other);
            } else if (destination.equals(Rule.SourceDestType.User.toString())) {
                destJID = ParamUtils.getParameter(request, "destUserJID");
                rule.setDestination(destJID);
                rule.setDestType(Rule.SourceDestType.User);
            } else if (destination.equals(Rule.SourceDestType.Group.toString())) {
                destJID = ParamUtils.getParameter(request, "destGroupJID");
                rule.setDestination(destJID);
                rule.setDestType(Rule.SourceDestType.Group);
            } else if (destination.equals(Rule.SourceDestType.Component.toString())) {
                destJID = ParamUtils.getParameter(request, "destComponentJID");
                rule.setDestination(destJID);
                rule.setDestType(Rule.SourceDestType.Component);
            }


            rule.doLog(new Boolean(log).booleanValue());
            rule.isDisabled(new Boolean(disable).booleanValue());
            if (errors.size() == 0) {
                 if (rule.getSourceType() == Rule.SourceDestType.User ||
                     rule.getDestType() == Rule.SourceDestType.Other ) {
                    rule.setSource(rule.getSource().toLowerCase());
               }
               else {
                  rule.setSource(rule.getSource());
               }

               if (rule.getDestType() == Rule.SourceDestType.User ||
                   rule.getDestType() == Rule.SourceDestType.Other) {
                rule.setDestination(rule.getDestination().toLowerCase());
               }
               else {
                  rule.setDestination(rule.getDestination());
               }
                rm.addRule(rule);
                response.sendRedirect("pf-main.jsp");
            }

        }


    }
%>
<html>
<head>
    <title>
        <fmt:message key="pf.create.new.rule"/>

    </title>
    <meta name="pageID" content="packetFilter"/>
    <script language="JavaScript" type="text/javascript" src="scripts/packetfilter.js"></script>
</head>
<body>

<% if (!errors.isEmpty()) { %>

<div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
        <tbody>
            <tr>
                <td class="jive-icon"><img src="/images/error-16x16.gif" width="16" height="16" border="0"/></td>
                <td class="jive-icon-label">

                    <% if (errors.get("sourceOther") != null) { %>
                    <fmt:message key="pf.error.sourceOther"/>
                    <% } else if (errors.get("destOther") != null) { %>
                    <fmt:message key="pf.error.destOther"/>
                    <% } %>
                </td>
            </tr>
        </tbody>
    </table>
</div>
<br>

<% } %>

<form action="rule-form.jsp" method="get">
<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<tbody>
<tr class="jive-even">
    <td>Action</td>
    <td>
        <select id="packetAction" name="packetAction" onChange="ShowExtraOptions('packetAction')">
            <% Rule.Action[] actions = Rule.Action.values();
                for (int i = 0; i < actions.length; i++) {
                    String action = actions[i].toString();
            %>
            <option value="<%=action%>" <%if (packetAction != null && packetAction.equals(action)) {%>
                    selected<%}%>
                    >
                <%=action%>
            </option>
            <%}%>

        </select>
    </td>
</tr>
<tr class="jive-odd">
    <td>Disable</td>
    <td><input type="checkbox" name="disable" value="true"
    <%if (disable != null && disable.equals("true")){%>
               checked <%}%>
            ></td>

</tr>
<tr class="jive-even">
    <td>Packet Type</td>
    <td>
        <select label="packetType" name="packetType">
            <%
                Rule.PacketType[] packetTypes = Rule.PacketType.values();
                for (int i = 0; i < packetTypes.length; i++) {

            %>

            <option value="<%=packetTypes[i].toString()%>"
                    <%if (packetType != null && packetType.equals(packetTypes[i].toString())) {%>
                    selected <%}%>
                    >
                <%=packetTypes[i].getDisplayName()%>
            </option>
            <% } %>

        </select>
    </td>

</tr>
<tr class="jive-odd">
    <td>From</td>
    <td>
        <select id="source" name="source" onChange="ShowSourceField('source')">
            <%
                for (int i = 0; i < type.length; i++) {
                    String option = type[i].toString();%>
            <option value="<%=option%>" <%
                if (source != null &&
                        source.equals(option)) {
            %>
                    selected <%}%>
                    ><%=option%>
            </option>
            <% } %>
        </select>
    </td>
</tr>
<tr class="jive-odd" name="SourceOther"
    id="SourceOther" <%if (source == null || !source.equals(Rule.SourceDestType.Other.toString())) {%>
    style="display:none;"<%}%>
        >
    <td>
        Other JID
    </td>
    <td>
        <input type="text" name="sourceOtherJID" id="sourceOtherJID"
                <%if (source != null && source.equals(Rule.SourceDestType.Other.toString())) {%>
               value="<%=sourceJID%>"
                <%}%>
                ></input>
    </td>
</tr>

<tr class="jive-odd" name="SourceGroup"
    id="SourceGroup" <%if (source == null || !source.equals(Rule.SourceDestType.Group.toString())) {%>
    style="display:none;"<%}%>
        >
    <td>
        Source Group
    </td>
    <td>
        <select id="sourceGroupJID" name="sourceGroupJID">
            <% for (Group group : groups) {%>

            <option value="<%=group.getName()%>" <%if (sourceJID != null && sourceJID.equals(group.getName())) {%>
                    selected<%}%>
                    ><%=group.getName()%>
            </option>
            <%}%>
            <option value="<%=PacketFilterConstants.ANY_GROUP%>" <%if (sourceJID != null && sourceJID.equals(PacketFilterConstants.ANY_GROUP)) {%>
                    selected<%}%>
                    ><fmt:message key="pf.anygroup" />
            </option>
        </select>
    </td>
</tr>

<tr class="jive-odd" name="SourceUser"
    id="SourceUser"  <%if (source == null || !source.equals(Rule.SourceDestType.User.toString())) {%>
    style="display:none;"><%}%>
    <td>
        Source User
    </td>
    <td>
        <select id="sourceUserJID" name="sourceUserJID">
            <% for (String userName : userList) {%>
            <option value="<%=userName+"@"+serverName%>"
                    <%if (sourceJID != null && sourceJID.equals(userName + "@" + serverName)) {%>
                    selected<%}%>
                    ><%=userName + "@" + serverName%>
            </option>
            <%}%>
        </select>
    </td>
</tr>

<tr class="jive-odd" name="SourceComponent"
    id="SourceComponent" <%if (source == null || !source.equals(Rule.SourceDestType.Component.toString())) {%>
    style="display:none;"<%}%>
        >
    <td>
        Component
    </td>
    <td>
        <select id="sourceComponentJID" name="sourceComponentJID">
            <% if (components != null && components.size() > 0) {
                for (String component : components) {
             if (component != null && cList.getComponentName(component) != null) { %>
            <option value="<%=component%>"
                    <%if (sourceJID != null && sourceJID.equals(component)) {%>
                    selected<%}%>>
                <%=cList.getComponentName(component)%>
            </option>
            <% }
            }
            }else {%>
            <option value="">
                None Installed
            </option>
            <%}%>
        </select>
    </td>
</tr>

<tr class="jive-even">
    <td>To</td>
    <td>
        <select name="destination" id="destination" onChange="ShowDestinationField('destination')">
            <% for (int i = 0; i < type.length; i++) {
                String option = type[i].toString();%>
            <option value="<%=option%>" <%
                if (destination != null &&
                        destination.equals(option)) {
            %>
                    selected <%}%>
                    ><%=option%>
            </option>
            <% } %>
        </select>
    </td>

</tr>

<tr class="jive-even" name="DestComponent"
    id="DestComponent" <%if (destination == null || !destination.equals(Rule.SourceDestType.Component.toString())) {%>
    style="display:none;"<%}%>
        >
    <td>
        Component
    </td>
    <td>
        <select id="destComponentJID" name="destComponentJID">
            <% if (components != null && components.size() > 0) {
                for (String component : components) {
            if (component != null && cList.getComponentName(component) != null) {  %>
            <option value="<%=component%>"
                    <%if (destJID != null && destJID.equals(component)) {%>
                    selected<%}%>>
                <%=cList.getComponentName(component)%>
            </option>
            <% }
            }
            } else {%>
            <option value="">
                None Installed
            </option>
            <%}%>
        </select>
    </td>
</tr>

<tr class="jive-even" name="DestOther"
    id="DestOther"  <%if (destination == null || !destination.equals(Rule.SourceDestType.Other.toString())) {%>
    style="display:none;"<%}%>
        >
    <td>
        Other JID
    </td>
    <td>
        <input type="text" name="destOtherJID" id="destOtherJID"
                <%if (destination != null && destination.equals(Rule.SourceDestType.Other.toString())) {%>
               value="<%=destJID%>"
                <%}%>
                ></input>
    </td>

</tr>

<tr class="jive-odd" name="DestGroup" id="DestGroup" <%
    if (destination == null ||
            !destination.equals(Rule.SourceDestType.Group.toString())) {
%>
    style="display:none;"<%}%>
        >
    <td>
        Destination Group
    </td>
    <td>
        <select id="destGroupJID" name="destGroupJID">
            <% for (Group group : groups) {%>

            <option value="<%=group.getName()%>" <%if (destJID != null && destJID.equals(group.getName())) {%>
                    selected<%}%>
                    ><%=group.getName()%>
            </option>
            <%}%>
            <option value="<%=PacketFilterConstants.ANY_GROUP%>" <%if (sourceJID != null && sourceJID.equals(PacketFilterConstants.ANY_GROUP)) {%>
                    selected<%}%>
                    ><fmt:message key="pf.anygroup" />
            </option>
        </select>
    </td>
</tr>

<tr class="jive-odd" name="DestUser" id="DestUser" <%
    if (destination == null
            || !destination.equals(Rule.SourceDestType.User.toString())) {
%>
    style="display:none;"<%}%>>
    <td>
        Destination User
    </td>
    <td>
        <select id="destUserJID" name="destUserJID">
            <% for (String userName : userList) {%>
            <option value="<%=userName+"@"+serverName%>" <%if (destJID != null && destJID.equals(userName + "@" + serverName)) {%>
                    selected<%}%>
                    ><%=userName + "@" + serverName%>
            </option>
            <%}%>
        </select>
    </td>
</tr>


<tr class="jive-odd">
    <td>Log</td>
    <td><input type="checkbox" name="log" value="true"
    <% if (log != null && log.equals("true")) {%>
               checked <%}%>
            ></td>
</tr>
<tr class="jive-even">
    <td>Description</td>
    <td><input type="text" size="40" name="description"
            <%if (description != null) {%>
               value="<%=description%>"<%}%>
            ></input></td>
</tr>

<tr>
    <td>
        <input type="submit" name="create" value="<fmt:message key="pf.create.rule" />">
        <input type="submit" name="cancel" value="<fmt:message key="pf.global.cancel" />">
    </td>
    <td>&nbsp;</td>
</tr>
</tbody>
</table>

</div>
</form>

</body>
</html>

