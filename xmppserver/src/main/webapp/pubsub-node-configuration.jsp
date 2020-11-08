<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="org.jivesoftware.openfire.pep.PEPServiceInfo,
                 org.jivesoftware.openfire.pubsub.Node,
                 org.jivesoftware.openfire.pubsub.PubSubServiceInfo,
                 org.jivesoftware.openfire.XMPPServer,
                 org.jivesoftware.util.ParamUtils,
                 org.xmpp.packet.JID,
                 java.net.URLEncoder"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="admin" prefix="admin" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out); %>

<%  // Get parameters
    String nodeID = ParamUtils.getParameter(request,"nodeID");
    String ownerString = ParamUtils.getParameter( request, "owner" );
    if ( ownerString == null )
    {
        ownerString = ParamUtils.getParameter( request, "username" );
    }

    JID owner = null;
    if (ownerString != null)
    {
        if ( ownerString.contains( "@" ) )
        {
            owner = new JID( ownerString ).asBareJID();
        }
        else
        {
            owner = XMPPServer.getInstance().createJID( ownerString, null );
        }
    }

    // Load the node object
    PubSubServiceInfo pubSubServiceInfo;
    if ( owner == null )
    {
        pubSubServiceInfo = webManager.getPubSubInfo();
    }
    else
    {
        pubSubServiceInfo = new PEPServiceInfo( owner );
    }

    Node node = pubSubServiceInfo.getNode( nodeID );
    if (node == null) {
        // The requested node does not exist so return to the list of the existing node
        response.sendRedirect("pubsub-node-summary.jsp" + (owner != null ? "?owner=" + URLEncoder.encode(owner.toBareJID(), "UTF-8") : ""));
        return;
    }

    pageContext.setAttribute("node", node);
    pageContext.setAttribute("owner", owner );
%>

<html>
<head>
<title><fmt:message key="pubsub.node.configuration.title"/></title>
<c:choose>
    <c:when test="${not empty owner and owner.domain eq webManager.serverInfo.XMPPDomain}">
        <meta name="subPageID" content="user-pep-node-summary"/>
        <meta name="extraParams" content="username=${admin:urlEncode(owner.node)}&nodeID=${admin:urlEncode(node.nodeID)}" />
    </c:when>
    <c:otherwise>
        <meta name="subPageID" content="pubsub-node-details"/>
        <meta name="extraParams" content="nodeID=${admin:urlEncode(node.nodeID)}"/>
    </c:otherwise>
</c:choose>
</head>
<body>

    <p>
    <fmt:message key="pubsub.node.summary.table.info" />
    </p>

    <div class="jive-table">
    <table cellpadding="0" cellspacing="0" border="0" width="100%">
    <thead>
        <tr>
            <th scope="col"><fmt:message key="pubsub.node.summary.id" /></th>
            <th scope="col"><fmt:message key="pubsub.node.summary.name" /></th>
            <th scope="col"><fmt:message key="pubsub.node.summary.description" /></th>
            <th scope="col"><fmt:message key="pubsub.node.summary.items" /></th>
            <th scope="col"><fmt:message key="pubsub.node.summary.affiliates" /></th>
            <th scope="col"><fmt:message key="pubsub.node.summary.subscribers" /></th>
            <th scope="col"><fmt:message key="pubsub.node.summary.created" /></th>
            <th scope="col"><fmt:message key="pubsub.node.summary.modified" /></th>
        </tr>
    </thead>
    <tbody>
        <tr>
            <td><c:out value="${node.nodeID}"/></td>
            <td><c:out value="${node.name}"/></td>
            <td><c:out value="${node.description}"/></td>
            <td><c:out value="${node.publishedItems.size()}"/></td>
            <td><c:out value="${node.allAffiliates.size()}"/></td>
            <td><c:out value="${node.allSubscriptions.size()}"/></td>
            <td><fmt:formatDate type="both" dateStyle="medium" timeStyle="short" value="${node.creationDate}" /></td>
            <td><fmt:formatDate type="both" dateStyle="medium" timeStyle="short" value="${node.modificationDate}" /></td>
        </tr>
    </tbody>
    </table>
    </div>

    <br>
    <p>
        <fmt:message key="pubsub.node.configuration.table.info" />
    </p>

    <div class="jive-table">
    <table cellpadding="0" cellspacing="0" border="0" width="100%">
    <thead>
        <tr>
            <th scope="col" colspan="2"><fmt:message key="pubsub.node.configuration.details" /></th>
        </tr>
    </thead>
    <tbody>
        <c:forEach var="field" items="${node.configurationForm.fields}">
        <tr>
            <td>
            <c:out value="${field.label}"/>
            </td>
            <td>
                <c:forEach var="value" items="${field.values}">
                    <c:out value="${value}"/>
                </c:forEach>
            </td>
        </tr>
        </c:forEach>
    </tbody>
    </table>
    </div>

    </body>
</html>
