<%@page import="org.jivesoftware.openfire.plugin.PacketFilterUtil"%>
<%@ page import="org.jivesoftware.openfire.component.InternalComponentManager,
                 org.jivesoftware.openfire.plugin.component.ComponentList,
                 org.jivesoftware.openfire.plugin.rules.Rule,
                 org.jivesoftware.openfire.plugin.rules.RuleManager"
        %>
<%@ page import="org.jivesoftware.openfire.plugin.rules.RuleManagerProxy" %>
<%@ page import="org.xmpp.packet.JID" %>
<%@ page import="java.util.List" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"/>
<%
    webManager.init(request, response, session, application, out);
    RuleManager rm = new RuleManagerProxy();
    ComponentList componentManager = ComponentList.getInstance();
    int i = 0;

    boolean moveOne = request.getParameter("moveOne") != null;

    if (moveOne) {
        int destId = new Integer(request.getParameter("moveOne")).intValue();
        int srcId = new Integer(request.getParameter("ruleId")).intValue();
        rm.moveOne(srcId, destId);
        response.sendRedirect("pf-main.jsp");
    }

    List<Rule> rules = rm.getRules();
    int lastOrder = rm.getLastOrder();
%>

<html>
<head>
    <style type="text/css">
        <!--
        @import url( "style/packetfilter.css" );
        -->
    </style>
    <title>
        <fmt:message key="pf.summary.title"/>
    </title>
    <meta name="pageID" content="packetFilter"/>
    <meta name="helpPage" content=""/>
    <script language="JavaScript" type="text/javascript" src="scripts/packetfilter.js"></script>
    

</head>
<body>


<div class="jive-table">
    <table cellpadding="0" cellspacing="0" border="0" width="100%">
        <thead>
            <tr>
                <th nowrap>Rule Type</th>
                <th nowrap>From</th>
                <th nowrap>To</th>
                <th nowrap>Packet Type</th>
                <th nowrap>Log</th>
                <th>&nbsp</th>
            </tr>
        </thead>

        <tbody>
            <%
                for (Rule rule : rules) {


            %>

            <tr class="jive-<%= (((i%2)==0) ? "even" : "odd") %>">

                <% if (rule.isDisabled()) { %>
                       <td><strike><%=rule.getDisplayName()%></strike></td>
                    <%} else {%>
                        <td><%=rule.getDisplayName()%></td>
                    <%}%>
                <% if (rule.isDisabled()) { %>
                      <td><strike><%=rule.getSource()%></strike></td>
                    <%} else if (rule.getSourceType().equals(Rule.SourceDestType.Component.toString())
                            && componentManager.getComponentName(new JID(rule.getSource()))!= null) { %>
                        <td><%=componentManager.getComponentName(new JID(rule.getSource()))%></td>
                    <% } else {%>
                       <td><%=PacketFilterUtil.formatRuleSourceDest(rule.getSource())%></td>
                    <%}%>
                <% if (rule.isDisabled()) { %>
                         <td><strike><%=rule.getDestination()%></strike></td>
                    <%} else if (rule.getDestType().equals(Rule.SourceDestType.Component.toString())
                            && componentManager.getComponentName(new JID(rule.getDestination()))!= null) {%>
                          <td><%=componentManager.getComponentName(new JID(rule.getDestination()))%></td>
                   <% }else {%>
                          <td><%=PacketFilterUtil.formatRuleSourceDest(rule.getDestination())%></td>
                    <%}%>
                <% if (rule.isDisabled()) { %>
                         <td><strike><%=rule.getPackeType()%></strike></td>
                    <%} else {%>
                        <td><%=rule.getPackeType().getDisplayName()%></td>
                    <%}%>
                <% if (rule.isDisabled()) { %>
                       <td><strike><%=rule.doLog()%></strike></td>
                    <%} else {%>
                      <td><%=rule.doLog()%></td>
                    <%}%>

                <td><a href="delete-rule.jsp?ruleId=<%=rule.getRuleId()%>"><img src="/images/delete-16x16.gif"
                                                                                width="16" height="16"
                                                                                border="0"
                                                                                alt="<fmt:message key="global.click_delete" />"></a>
                    <!--<a href="rule-form.jsp?beforeId=<%=rule.getRuleId()%>">Before</a>
                    <a href="rule-form.jsp?afterId=<%=rule.getRuleId()%>">After</a>-->
                    <%if (rule.getOrder() > 1 && i>0) {%>
                    <a href="pf-main.jsp?moveOne=<%=rules.get(i-1).getRuleId()%>&ruleId=<%=rule.getRuleId()%>"><img src="arrow_up.png" width="16" height="16" border="0" alt="<fmt:message key="pf.click.up"/>"></a>
                    <%}%>
                     <%if (rule.getOrder() != lastOrder) {%>
                    <a href="pf-main.jsp?moveOne=<%=rules.get(i+1).getRuleId()%>&ruleId=<%=rule.getRuleId()%>"><img src="arrow_down.png" width="16" height="16" border="0" alt="<fmt:message key="pf.click.down"/>"></a>
                    <%}%>
                    <a href="rule-edit-form.jsp?edit=<%=rule.getRuleId()%>"><img src="/images/edit-16x16.gif" width="16" height="16" border="0" alt="<fmt:message key="pf.click.edit"/>"></a>
                </td>
            </tr>
            <%
                i++;
                } %>
        </tbody>
    </table>
    </div>
    <input type="button" ONCLICK="window.location.href='rule-form.jsp'" name="create" value="<fmt:message key="pf.create.new.rule"/>">

</body>
</html>
