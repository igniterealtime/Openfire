<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.openfire.pubsub.Node,
                 java.net.URLEncoder"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters //
    boolean cancel = ParamUtils.getParameter(request,"cancel") != null;
    boolean delete = ParamUtils.getParameter(request,"delete") != null;
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (delete) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            delete = false;
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    String nodeID = ParamUtils.getParameter(request,"nodeID");
    String reason = ParamUtils.getParameter(request,"reason");

    // Handle a cancel
    if (cancel) {
        response.sendRedirect("pubsub-node-summary.jsp");
        return;
    }

    // Load the node object
	Node node = webManager.getPubSubManager().getNode(nodeID);

    // Handle a node delete:
    if (delete) {
        // Delete the node
        if (node != null) {
            // If the node still exists then destroy it
            node.delete();
            // Log the event
            webManager.logEvent("destroyed PubSub Node " + nodeID, "reason = " + reason );
        }
        // Done, so redirect
        response.sendRedirect("pubsub-node-summary.jsp?deletesuccess=true");
        return;
    }

    pageContext.setAttribute("node", node);

%>

<html>
    <head>
        <title><fmt:message key="pubsub.node.delete.title"/></title>
        <meta name="subPageID" content="pubsub-node-delete"/>
        <meta name="extraParams" content="nodeID=${node.nodeID}"/>
    </head>
    <body>

<p>
    <fmt:message key="pubsub.node.delete.info" />
        <b>
            <c:out value="${node.nodeID}"/>
        </b>
    <fmt:message key="pubsub.node.delete.detail" />
</p>

<form action="pubsub-node-delete.jsp">
    <input type="hidden" name="csrf" value="${csrf}">
	<input type="hidden" name="nodeID" value="${node.nodeID}">

<fieldset>
    <legend><fmt:message key="pubsub.node.delete.details_title" /></legend>
    <div>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td class="c1">
                <fmt:message key="pubsub.node.delete.node_id" />
            </td>
            <td>
                <c:out value="${node.nodeID}"/>
            </td>
        </tr>
        <tr>
            <td class="c1">
                <fmt:message key="pubsub.node.delete.reason" />
            </td>
            <td>
                <input type="text" size="50" maxlength="150" name="reason">
            </td>
        </tr>
    </tbody>
    </table>
    </div>
</fieldset>

<br><br>

<input type="submit" name="delete" value="<fmt:message key="pubsub.node.delete.delete_node" />">
<input type="submit" name="cancel" value="<fmt:message key="global.cancel" />">
</form>

    </body>
</html>
