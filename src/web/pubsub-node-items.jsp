<%@ page import="java.util.Arrays,
                 org.jivesoftware.openfire.pubsub.Node,
                 org.jivesoftware.openfire.pubsub.LeafNode,
                 org.jivesoftware.openfire.pubsub.PublishedItem,
                 org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.util.StringUtils,
                 org.jivesoftware.util.CookieUtils,
                 java.net.URLEncoder,
                 java.text.DateFormat"
    errorPage="error.jsp"
%>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.muc.NotAllowedException" %>
<%@ page import="org.xmpp.packet.JID" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out); %>

<%  // Get parameters
    String nodeID = ParamUtils.getParameter(request,"nodeID");
	String deleteID = ParamUtils.getParameter(request,"deleteID");

    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (deleteID != null) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
             deleteID = null;
        }
    }


    // Load the node object
    Node node = webManager.getPubSubManager().getNode(nodeID);
    if (node == null) {
        // The requested node does not exist so return to the list of the existing node
        response.sendRedirect("pubsub-node-summary.jsp");
        return;
    }

    // Delete specified subscription ID
    if (deleteID != null) {
        PublishedItem pi = node.getPublishedItem(deleteID);
        if (pi != null) {
            LeafNode lNode = (LeafNode) node;

            lNode.deleteItems(Arrays.asList(pi));

	        // Log the event
	        webManager.logEvent("Delete item ID: " + deleteID +  ", from node ID: " + nodeID, "Publisher: " + pi.getPublisher().toBareJID());
	        // Done, so redirect
	        response.sendRedirect("pubsub-node-items.jsp?nodeID=" + URLEncoder.encode(nodeID, "UTF-8") + "&deleteSuccess=true&owner=" + URLEncoder.encode(pi.getPublisher().toBareJID(), "UTF-8"));
	        return;
        }
    }


    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    pageContext.setAttribute("node", node);

%>

<html>
<head>
<title><fmt:message key="pubsub.node.items.title"/></title>
<meta name="subPageID" content="pubsub-node-items"/>
<meta name="extraParams" content="nodeID=${node.nodeID}&create=false"/>
</head>
<body>

    <p>
    <fmt:message key="pubsub.node.summary.table.info" />
    </p>

    <c:if test="${param.deleteSuccess}">

        <div class="jive-success">
        <table cellpadding="0" cellspacing="0" border="0">
        <tbody>
            <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
            <td class="jive-icon-label">
            <fmt:message key="pubsub.node.items.deleted">
                <fmt:param value="${param.owner}"/>
            </fmt:message>
            </td></tr>
        </tbody>
        </table>
        </div><br>

    </c:if>

    <div class="jive-table">
    <table cellpadding="0" cellspacing="0" border="0" width="100%">
    <thead>
        <tr>
            <th scope="col"><fmt:message key="pubsub.node.summary.id" /></th>
            <th scope="col"><fmt:message key="pubsub.node.summary.name" /></th>
            <th scope="col"><fmt:message key="pubsub.node.summary.description" /></th>
            <th scope="col"><fmt:message key="pubsub.node.summary.items" /></th>
            <th scope="col"><fmt:message key="pubsub.node.summary.subscribers" /></th>
            <th scope="col"><fmt:message key="pubsub.node.summary.created" /></th>
            <th scope="col"><fmt:message key="pubsub.node.summary.modified" /></th>
        </tr>
    </thead>
    <tbody>
        <tr>
            <td><c:out value="${node.getNodeID()}"/></td>
            <td><c:out value="${node.getName()}"/></td>
            <td><c:out value="${node.getDescription()}"/></td>
            <td><c:out value="${node.getPublishedItems().size()}"/></td>
            <td><c:out value="${node.getAllSubscriptions().size()}"/></td>
            <td><fmt:formatDate type="both" dateStyle="medium" timeStyle="short" value="${node.getCreationDate()}" /></td>
            <td><fmt:formatDate type="both" dateStyle="medium" timeStyle="short" value="${node.getModificationDate()}" /></td>
        </tr>
    </tbody>
    </table>
    </div>

    <br>
    <p>
        <fmt:message key="pubsub.node.items.table.info" />
    </p>

    <div class="jive-table">
    <table cellpadding="0" cellspacing="0" border="0" width="100%">
    <thead>
        <tr>
            <th scope="col"><fmt:message key="pubsub.node.items.id" /></th>
            <th scope="col"><fmt:message key="pubsub.node.items.publisher" /></th>
            <th scope="col"><fmt:message key="pubsub.node.items.created" /></th>
            <th scope="col"><fmt:message key="pubsub.node.items.payload" /></th>
            <th scope="col"><fmt:message key="global.delete" /></th>
        </tr>
    </thead>
    <tbody>
        <c:forEach var="item" items="${node.getPublishedItems()}">
        <tr>
            <td>
            <c:out value="${item.getID()}"/>
            </td>
            <td>
            <c:out value="${item.getPublisher().toBareJID()}"/>
            </td>
            <td>
            <fmt:formatDate type="both" dateStyle="medium" timeStyle="short" value="${item.getCreationDate()}" />
            </td>
            <td>
            <c:out value="${item.getPayloadXML()}"/>
            </td>
            <td width="1%" align="center" style="border-right:1px #ccc solid;">
               <c:url value="pubsub-node-items.jsp" var="url">
                    <c:param name="nodeID" value="${node.getNodeID()}" />
                    <c:param name="deleteID" value="${item.getID()}" />
                    <c:param name="csrf" value="${csrf}" />
                </c:url>
                <a href="${url}" title="<fmt:message key="global.click_delete" />">
                    <img src="images/delete-16x16.gif" width="16" height="16" border="0" alt="">
                </a>
	        </td>
        </tr>
        </c:forEach>
    </tbody>
    </table>
    </div>

    </body>
</html>
