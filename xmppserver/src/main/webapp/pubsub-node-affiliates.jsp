<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer,
                 org.jivesoftware.openfire.pep.PEPServiceInfo,
                 org.jivesoftware.openfire.pubsub.Node,
                 org.jivesoftware.openfire.pubsub.NodeAffiliate,
                 org.jivesoftware.openfire.pubsub.PubSubServiceInfo,
                 org.jivesoftware.util.ParamUtils,
                 org.xmpp.packet.JID,
                 java.net.URLEncoder,
                 java.util.ArrayList,
                 java.util.List"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn"%>
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

    PubSubServiceInfo pubSubServiceInfo;
    if ( owner == null )
    {
        pubSubServiceInfo = webManager.getPubSubInfo();
    }
    else
    {
        pubSubServiceInfo = new PEPServiceInfo( owner );
    }
    
    // Load the node object
    Node node = pubSubServiceInfo.getNode(nodeID);
    if (node == null) {
        // The requested node does not exist so return to the list of the existing node
        response.sendRedirect("pubsub-node-summary.jsp" + (owner != null ? "?owner=" + URLEncoder.encode(owner.toBareJID(), "UTF-8") : ""));
        return;
    }

    List<NodeAffiliate> affiliates = new ArrayList<>(node.getAllAffiliates());

    affiliates.sort( ( affiliate1, affiliate2 ) -> {

            // Sort by Emum ordinal which gives order: owner, publisher, none, outcast
            int affiliateComp = affiliate1.getAffiliation().compareTo(affiliate2.getAffiliation());

            //if affiliations match then sort by full JID
            if(affiliateComp != 0) {
                return affiliateComp;
            } else {
                return affiliate1.getJID().compareTo(affiliate2.getJID());
            }

        }
    );

    pageContext.setAttribute("node", node);
    pageContext.setAttribute("owner", owner );
    pageContext.setAttribute("affiliates", affiliates);
%>

<html>
<head>
    <title><fmt:message key="pubsub.node.affiliates.title"/></title>
    <c:choose>
        <c:when test="${not empty owner and owner.domain eq webManager.serverInfo.XMPPDomain}">
            <meta name="subPageID" content="user-pep-node-summary"/>
            <meta name="extraParams" content="username=${admin:urlEncode(owner.node)}&nodeID=${admin:urlEncode(node.nodeID)}" />
        </c:when>
        <c:otherwise>
            <meta name="subPageID" content="pubsub-node-affiliates"/>
            <meta name="extraParams" content="nodeID=${admin:urlEncode(node.nodeID)}"/>
        </c:otherwise>
    </c:choose>
</head>
<body>

    <c:if test="${param.deleteSuccess}">
        <admin:infoBox type="success">
            <fmt:message key="pubsub.node.affiliates.deleted">
                <fmt:param value="${fn:escapeXml(param.affiliateJID)}"/>
            </fmt:message>
        </admin:infoBox>
    </c:if>

    <c:if test="${param.updateSuccess}">
        <admin:infoBox type="success">
            <fmt:message key="pubsub.node.affiliates.updated">
                <fmt:param value="${fn:escapeXml(param.affiliateJID)}"/>
            </fmt:message>
        </admin:infoBox>
    </c:if>

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
        <fmt:message key="pubsub.node.affiliates.table.info" />
    </p>

    <div class="jive-table">
    <table cellpadding="0" cellspacing="0" border="0" width="100%">
    <thead>
        <tr>
            <th scope="col"><fmt:message key="pubsub.node.affiliates.jid" /></th>
            <th scope="col"><fmt:message key="pubsub.node.affiliates.affiliation" /></th>
            <th scope="col"><fmt:message key="pubsub.node.affiliates.subscriptions" /></th>
            <th scope="col"><fmt:message key="global.edit" /></th>
            <th scope="col"><fmt:message key="global.delete" /></th>
        </tr>
    </thead>
    <tbody>
        <c:if test="${empty affiliates}">
        <tr>
            <td align="center" colspan="4">
                <fmt:message key="pubsub.node.affiliates.table.no_affiliates" />
            </td>
        </tr>
        </c:if>
        <c:forEach var="affiliate" items="${affiliates}">
        <tr>
            <td>
            <c:out value="${affiliate.JID.toBareJID()}"/>
            </td>
            <td>
            <c:out value="${affiliate.affiliation.name()}"/>
            </td>
            <td width="1%" align="center">
                <c:url value="pubsub-node-subscribers.jsp" var="url">
                    <c:param name="nodeID" value="${node.nodeID}" />
                    <c:param name="owner" value="${owner}" />
                </c:url>
                <a href="${url}">
                     <c:out value="${affiliate.subscriptions.size()}"/>
                </a>
            </td>
            <td width="1%" align="center">
                <c:url value="pubsub-node-affiliates-edit.jsp" var="url">
                    <c:param name="nodeID" value="${node.nodeID}" />
                    <c:param name="owner" value="${owner}" />
                    <c:param name="affiliateJID" value="${affiliate.JID.toBareJID()}" />
                </c:url>
                <a href="${url}" title="<fmt:message key="global.click_edit" />">
                    <img src="images/edit-16x16.gif" width="16" height="16" border="0" alt="">
                </a>
            </td>
            <td width="1%" align="center" style="border-right:1px #ccc solid;">
                <c:url value="pubsub-node-affiliates-delete.jsp" var="url">
                    <c:param name="nodeID" value="${node.nodeID}" />
                    <c:param name="owner" value="${owner}" />
                    <c:param name="affiliateJID" value="${affiliate.JID.toBareJID()}" />
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
