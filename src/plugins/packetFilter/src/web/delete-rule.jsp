
<%@page import="org.jivesoftware.openfire.plugin.PacketFilterUtil"%>
<%@ page import="org.jivesoftware.util.*"%>
<%@ page import="org.jivesoftware.openfire.plugin.rules.RuleManagerProxy" %>
<%@ page import="org.jivesoftware.openfire.plugin.rules.Rule" %>
<%@ page import="org.jivesoftware.openfire.plugin.rules.RuleManager" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<% // Get parameters //
    boolean cancel = request.getParameter("cancel") != null;
    boolean delete = request.getParameter("delete") != null;
    String ruleId = ParamUtils.getParameter(request, "ruleId");

    RuleManager rm = new RuleManagerProxy();
    Rule rule = rm.getRuleById(new Integer(ruleId));

    // Handle a cancel
    if (cancel) {
        response.sendRedirect("pf-main.jsp");
        return;
    }
    if (delete) {
        rm.deleteRule(Integer.parseInt(ruleId));
        response.sendRedirect("pf-main.jsp");
    }
%>

<html>
    <head>
        <title><fmt:message key="pf.delete.title"/></title>
        <meta name="pageID" content="packetFilter"/>
    </head>
    <body>

    You have choosen to delete the rule form <%=PacketFilterUtil.formatRuleSourceDest(rule.getSource())%> to <%=rule.getDestination()%>. Are you sure?

    <br>
    <br>

<form action="delete-rule.jsp">
<input type="hidden" name="ruleId" value="<%=ruleId%>">
<input type="submit" name="delete" value="<fmt:message key="pf.delete.delete" />">
<input type="submit" name="cancel" value="<fmt:message key="pf.global.cancel" />">
</form>
</body>
</html>
