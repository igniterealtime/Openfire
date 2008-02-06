<%@ page import="org.jivesoftware.openfire.XMPPServer,
                 org.jivesoftware.openfire.component.InternalComponentManager,
                 org.jivesoftware.openfire.group.Group,
                 org.jivesoftware.openfire.plugin.component.ComponentList"
        %>
<%@ page import="org.jivesoftware.openfire.plugin.rules.*" %>
<%@ page import="org.jivesoftware.openfire.user.UserManager" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.xmpp.component.Component" %>
<%@ page import="org.xmpp.packet.JID" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"/>
<%
    webManager.init(request, response, session, application, out);
    Collection<Group> groups = webManager.getGroupManager().getGroups();

     ComponentList cList = ComponentList.getInstance();

    Collection<String> components = cList.getComponentDomains();

    RuleManager rm = new RuleManagerProxy();
    Rule rule = null;
    //Get Action
    boolean editSave = request.getParameter("editSave") != null;
    boolean edit = request.getParameter("edit") != null;
    boolean cancel = request.getParameter("cancel") != null;


    boolean isDestOther = false;
    boolean isDestGroup = false;
    boolean isDestUser = false;
    boolean isDestAny = false;
    boolean isDestComponent = false;

    boolean isSourceOther = false;
    boolean isSourceGroup = false;
    boolean isSourceUser = false;
    boolean isSourceAny = false;
    boolean isSourceComponent = false;

    //Get data
    String packetAction = ParamUtils.getParameter(request, "packetAction");
    String disable = ParamUtils.getParameter(request, "disable");
    String packetType = ParamUtils.getParameter(request, "packetType");
    String source = ParamUtils.getParameter(request, "source");
    String destination = ParamUtils.getParameter(request, "destination");
    String log = ParamUtils.getParameter(request, "log");
    String description = ParamUtils.getParameter(request, "description");
    String order = ParamUtils.getParameter(request, "order");


    Collection<String> userList = UserManager.getInstance().getUsernames();
    String serverName = XMPPServer.getInstance().getServerInfo().getXMPPDomain();

    Map<String, String> errors = new HashMap<String, String>();

    String sourceJID = "";
    String destJID = "";


    if (cancel) {
        response.sendRedirect("pf-main.jsp");
        return;
    }
    if (edit) {
        rule = rm.getRuleById(new Integer(request.getParameter("edit")));
        disable = rule.isDisabled().toString();
        packetType = rule.getPackeType().toString();
        source = rule.getSource();
        destination = rule.getDestination();
        log = rule.doLog().toString();
        description = rule.getDescription();

        String destType = rule.getDestType();
        String sourceType = rule.getSourceType();

        destJID = destination;
        sourceJID = source;

        if (destType.equals(Rule.SourceDestType.Any.toString())) {
            isDestAny = true;
        } else if (destType.equals(Rule.SourceDestType.Group.toString())) {
            isDestGroup = true;
        } else if (destType.equals(Rule.SourceDestType.Component.toString())) {
            isDestComponent = true;
        } else if (destType.equals(Rule.SourceDestType.User.toString())) {
            isDestUser = true;
        } else if (destType.equals(Rule.SourceDestType.Other.toString())) {
            isDestOther = true;
        }

        if (sourceType.equals(Rule.SourceDestType.Any.toString())) {
            isSourceAny = true;
        } else if (sourceType.equals(Rule.SourceDestType.Group.toString())) {
            isSourceGroup = true;
        } else if (sourceType.equals(Rule.SourceDestType.Component.toString())) {
            isSourceComponent = true;
        } else if (sourceType.equals(Rule.SourceDestType.User.toString())) {
            isSourceUser = true;
        } else if (sourceType.equals(Rule.SourceDestType.Other.toString())) {
            isSourceOther = true;
        }

    }
    if (editSave) {

        //Destination simple case any
        if (destination.equals(Rule.SourceDestType.Any.toString())) isDestAny = true;
        else if (destination.equals(Rule.SourceDestType.Group.toString())) isDestGroup = true;
        else if (destination.equals(Rule.SourceDestType.User.toString())) isDestUser = true;
        else if (destination.equals(Rule.SourceDestType.Other.toString())) isDestOther = true;
        else if (destination.equals(Rule.SourceDestType.Component.toString())) isDestComponent = true;

        //Do the same thing as above for source. I'm repeating myself a little but
        //it will make things much easier to read.
        if (source.equals(Rule.SourceDestType.Any.toString())) isSourceAny = true;
        else if (source.equals(Rule.SourceDestType.Group.toString())) isSourceGroup = true;
        else if (source.equals(Rule.SourceDestType.User.toString())) isSourceUser = true;
        else if (source.equals(Rule.SourceDestType.Other.toString())) isSourceOther = true;
        else if (source.equals(Rule.SourceDestType.Component.toString())) isSourceComponent = true;

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
                rule.setSourceType(Rule.SourceDestType.Any.toString());
            } else if (source.equals(Rule.SourceDestType.Other.toString())) {
                sourceJID = ParamUtils.getParameter(request, "sourceOtherJID");
                if (sourceJID == null || !(sourceJID.length() > 0)) {
                    sourceJID = "";
                    errors.put("sourceOther", "");
                }
                rule.setSource(sourceJID);
                rule.setSourceType(Rule.SourceDestType.Other.toString());
            } else if (source.equals(Rule.SourceDestType.User.toString())) {
                sourceJID = ParamUtils.getParameter(request, "sourceUserJID");
                rule.setSource(sourceJID);
                rule.setSourceType(Rule.SourceDestType.User.toString());
            } else if (source.equals(Rule.SourceDestType.Group.toString())) {
                sourceJID = ParamUtils.getParameter(request, "sourceGroupJID");
                rule.setSource(sourceJID);
                rule.setSourceType(Rule.SourceDestType.Group.toString());
            } else if (source.equals(Rule.SourceDestType.Component.toString())) {
                sourceJID = ParamUtils.getParameter(request, "sourceComponentJID");
                rule.setSource(sourceJID);
                rule.setSourceType(Rule.SourceDestType.Component.toString());
            }


            if (destination.equals(Rule.SourceDestType.Any.toString())) {
                rule.setDestination(destination);
                rule.setDestType(Rule.SourceDestType.Any.toString());
            } else if (destination.equals(Rule.SourceDestType.Other.toString())) {
                destJID = ParamUtils.getParameter(request, "destOtherJID");
                if (destJID == null || !(sourceJID.length() > 0)) {
                    destJID = "";
                    errors.put("destOther", "");
                }
                rule.setDestination(destJID);
                rule.setDestType(Rule.SourceDestType.Other.toString());
            } else if (destination.equals(Rule.SourceDestType.User.toString())) {
                destJID = ParamUtils.getParameter(request, "destUserJID");
                rule.setDestination(destJID);
                rule.setDestType(Rule.SourceDestType.User.toString());
            } else if (destination.equals(Rule.SourceDestType.Group.toString())) {
                destJID = ParamUtils.getParameter(request, "destGroupJID");
                rule.setDestination(destJID);
                rule.setDestType(Rule.SourceDestType.Group.toString());
            } else if (destination.equals(Rule.SourceDestType.Component.toString())) {
                destJID = ParamUtils.getParameter(request, "destComponentJID");
                rule.setDestination(destJID);
                rule.setDestType(Rule.SourceDestType.Component.toString());
            }


            rule.doLog(new Boolean(log).booleanValue());
            rule.isDisabled(new Boolean(disable).booleanValue());

            rule.setRuleId(request.getParameter("ruleId"));
            rule.setOrder(new Integer(order));
            if (errors.isEmpty()) {
                rule.setSource(rule.getSource().toLowerCase());
                rule.setDestination(rule.getDestination().toLowerCase());
                rm.updateRule(rule);
                response.sendRedirect("pf-main.jsp");
            }

        }


    }
%>
<html>
<head>
    <title>
        <fmt:message key="pf.save.edit"/>
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
<form action="rule-edit-form.jsp?editSave" method="get">
<input type="hidden" name="ruleId" value="<%=rule.getRuleId()%>">
<input type="hidden" name="order" value="<%=rule.getOrder()%>">

<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<tbody>
<tr class="jive-even">
    <td>Action</td>
    <td>
        <select label="packetAction" name="packetAction">

            <% Rule.Action[] actions = Rule.Action.values();
                            for (int i = 0; i < actions.length; i++) {
                                String action = actions[i].toString();
                        %>
                        <option value="<%=action%>" <%if ((packetAction != null && packetAction.equals(action))||rule.getDisplayName().equals(action) ) {%>
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
    <%if (rule.isDisabled()) {%>
               checked
    <%}%>
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
            <option value="Any" <%if (isSourceAny) {%> selected <%}%>>Any</option>
            <option value="User" <%if (isSourceUser) {%> selected <%}%>>User</option>
            <option value="Group" <%if (isSourceGroup) {%> selected <%}%>>Group</option>
            <option value="Other" <%if (isSourceOther) {%> selected <%}%>>Other</option>
            <option value="Component" <%if (isSourceComponent) {%> selected <%}%>>Component</option>
        </select>
    </td>
</tr>
<tr class="jive-odd" name="SourceOther" id="SourceOther" <%if (!isSourceOther) {%> style="display:none;"<%}%>>
    <td>
        Other JID
    </td>
    <td>
        <input type="text" name="sourceOtherJID" id="sourceOtherJID" <%if (isSourceOther) {%>
               value="<%=sourceJID%>"<%}%>></input>
    </td>
</tr>

<tr class="jive-odd" name="SourceGroup" id="SourceGroup" <% if (!isSourceGroup) {%> style="display:none;"<%}%>>
    <td>
        Source Group
    </td>
    <td>
        <select id="sourceGroupJID" name="sourceGroupJID">
            <% for (Group group : groups) {%>

            <option value="<%=group.getName()%>"
                    <%if (isSourceGroup && source.equals(group.getName())) {%> selected<%}%>
                    ><%=group.getName()%>
            </option>
            <%}%>
        </select>
    </td>
</tr>

<tr class="jive-odd" name="SourceUser" id="SourceUser" <% if (!isSourceUser) {%> style="display:none;"<%}%>>
    <td>
        Source User
    </td>
    <td>
        <select id="sourceUserJID" name="sourceUserJID">
            <% for (String userName : userList) {%>
            <option value="<%=userName+"@"+serverName%>"
                    <% if (isSourceUser && source.equals(userName + "@" + serverName)) {%>
                    selected
                    <%}%>
                    ><%=userName + "@" + serverName%>
            </option>
            <%}%>
        </select>
    </td>
</tr>

<tr class="jive-odd" name="SourceComponent" id="SourceComponent" <%if (!isSourceComponent) {%>
    style="display:none;"<%}%>
        >
    <td>
        Component
    </td>
    <td>
        <select id="sourceComponentJID" name="sourceComponentJID">
            <% for (String component : components) {
            if (component != null && cList.getComponentName(component) != null) {%>
            <option value="<%=component%>"
                    <%if (sourceJID != null && sourceJID.equals(component)) {%>
                    selected<%}%>>
                <%=cList.getComponentName(component)%>
            </option>
            <% }
            }%>
        </select>
    </td>
</tr>

<tr class="jive-even">
    <td>To</td>
    <td>
        <select name="destination" id="destination" onChange="ShowDestinationField('destination')">
            <option value="Any" <%if (isDestAny) {%> selected <%}%>>Any</option>
            <option value="User" <%if (isDestUser) {%> selected <%}%>>User</option>
            <option value="Group" <%if (isDestGroup) {%> selected <%}%>>Group</option>
            <option value="Other" <%if (isDestOther) {%> selected <%}%>>Other</option>
            <option value="Component" <% if (isDestComponent) { %> selected <%}%>>Component</option>
        </select>
    </td>

</tr>

<tr class="jive-even" name="DestOther" id="DestOther" <%if (!isDestOther) {%> style="display:none;"<%}%>>
    <td>
        Other JID
    </td>
    <td>
        <input type="text" name="destOtherJID" id="destOtherJID"
                <% if (isDestOther) {%> value="<%=destJID%>"<%}%>
                ></input>
    </td>

</tr>

<tr class="jive-odd" name="DestGroup" id="DestGroup" <%if (!isDestGroup) {%> style="display:none;"<%}%>>
    <td>
        Destination Group
    </td>
    <td>
        <select id="destGroupJID" name="destGroupJID">
            <% for (Group group : groups) {%>

            <option value="<%=group.getName()%>"
                    <%if (isDestGroup && destination.equals(group.getName())) {%> selected<%}%>
                    ><%=group.getName()%>
            </option>
            <%}%>
        </select>
    </td>
</tr>

<tr class="jive-odd" name="DestUser" id="DestUser" <%if (!isDestUser) {%> style="display:none;"<%}%>>
    <td>
        Destination User
    </td>
    <td>
        <select id="destUserJID" name="destUserJID">
            <% for (String userName : userList) {%>
            <option value="<%=userName+"@"+serverName%>"
                    <% if (isDestUser && destination.equals(userName + "@" + serverName)) {%>
                    selected
                    <%}%>
                    ><%=userName + "@" + serverName%>
            </option>
            <%}%>
        </select>
    </td>
</tr>

<tr class="jive-even" name="DestComponent" id="DestComponent" <%if (!isDestComponent) {%>
    style="display:none;"<%}%>
        >
    <td>
        Component
    </td>
    <td>
        <select id="destComponentJID" name="destComponentJID">
            <% for (String component : components) {
            if (component != null && cList.getComponentName(component) != null) {%>
            <option value="<%=component%>"
                    <%if (destJID != null && destJID.equals(component)) {%>
                    selected<%}%>>
                <%=cList.getComponentName(component)%>
            </option>
            <% }
            }%>
        </select>
    </td>
</tr>


<tr class="jive-odd">
    <td>Log</td>
    <td><input type="checkbox" name="log" value="true"
    <%if(rule.doLog()) {%> checked<%}%>></td>
</tr>
<tr class="jive-even">
    <td>Description</td>
    <td><input type="text" size="40" name="description"
            <%if (rule.getDescription() != null) {%>
               value="<%=rule.getDescription()%>"
            <%}%>
            ></input></td>
</tr>
<tr>
    <td>
        <input type="submit" name="editSave" value="<fmt:message key="pf.save.edit" />">
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

