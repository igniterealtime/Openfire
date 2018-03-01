<%@ page import="org.jivesoftware.openfire.pep.PEPService,
                 org.jivesoftware.openfire.pep.PEPServiceInfo,
                 org.jivesoftware.openfire.pep.PEPServiceManager,
                 org.jivesoftware.openfire.pubsub.Node,
                 org.jivesoftware.openfire.pubsub.PubSubServiceInfo,
                 org.jivesoftware.openfire.XMPPServer,
                 org.jivesoftware.util.ParamUtils,
                 org.xmpp.packet.JID,
                 java.util.Collections,
                 java.util.Comparator,
                 java.util.List"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="admin" prefix="admin" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters
    int start = ParamUtils.getIntParameter(request,"start",0);
    int range = ParamUtils.getIntParameter(request,"range",webManager.getRowsPerPage("pubsub-node-summary", 15));
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

    if (request.getParameter("range") != null) {
        webManager.setRowsPerPage("pubsub-node-summary", range);
    }

    boolean PEPMode = false;
    PubSubServiceInfo pubSubServiceInfo;
    if ( owner == null )
    {
        pubSubServiceInfo = webManager.getPubSubInfo();
    }
    else if ( new PEPServiceManager().getPEPService( owner.toBareJID() ) != null )
    {
        PEPMode = true;
        pubSubServiceInfo = new PEPServiceInfo( owner );
    }
    else
    {
        pubSubServiceInfo = null;
    }

    List<Node> nodes;
    if ( pubSubServiceInfo != null )
    {
        nodes = pubSubServiceInfo.getLeafNodes();
    }
    else
    {
        nodes = Collections.emptyList();
    }

    Collections.sort(nodes, new Comparator<Node>() {
        public int compare(Node node1, Node node2) {
            return node1.getNodeID().toLowerCase().compareTo(node2.getNodeID().toLowerCase());
        }
    });

    int nodeCount = nodes.size();

    // paginator vars
    int numPages = (int)Math.ceil((double)nodeCount/(double)range);

    if(start > nodeCount) {
        start=nodeCount;
    }

    int curPage = (start/range) + 1;
    int maxNodeIndex = (start+range <= nodeCount ? start+range : nodeCount);

    pageContext.setAttribute("nodeCount", nodeCount);
    pageContext.setAttribute("numPages", numPages);
    pageContext.setAttribute("start", start);
    pageContext.setAttribute("range", range);
    pageContext.setAttribute("curPage", curPage);
    pageContext.setAttribute("maxNodeIndex", maxNodeIndex);
    pageContext.setAttribute("nodes", nodes.subList(start, maxNodeIndex));
    pageContext.setAttribute("owner", owner );
    pageContext.setAttribute("PEPMode", PEPMode);

%>
<html>
    <head>
        <title><fmt:message key="pubsub.node.summary.title"/></title>
        <c:choose>
            <c:when test="${not empty owner and owner.domain eq webManager.serverInfo.XMPPDomain}">
                <meta name="subPageID" content="user-pep-node-summary"/>
                <meta name="extraParams" content="username=${admin:urlEncode(owner.node)}" />
            </c:when>
            <c:otherwise>
                <meta name="pageID" content="pubsub-node-summary"/>
            </c:otherwise>
        </c:choose>
    </head>
    <body>

<p>
<fmt:message key="pubsub.node.summary.info" />
</p>

<c:if test="${param.deleteSuccess}">
    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="pubsub.node.summary.deleted" />
        </td></tr>
    </tbody>
    </table>
    </div><br>
</c:if>

<p>
<fmt:message key="pubsub.node.summary.total_nodes" />: <c:out value="${nodeCount}"/>
<c:if test="${numPages gt 1}">

    <fmt:message key="global.showing" /> <c:out value="${start+1}"/>-<c:out value="${maxNodeIndex}"/>

</c:if>

<fmt:message key="pubsub.node.summary.sorted_id" />

</p>

<c:if test="${numPages gt 1}">
    <p>
    <fmt:message key="global.pages" />:
    [
    <c:forEach begin="1" end="${numPages}" varStatus="loop">
        <c:url value="pubsub-node-summary.jsp" var="url">
            <c:param name="start" value="${(loop.index-1)*range}" />
            <c:param name="owner" value="${owner}"/>
        </c:url>
        <a href="${url}" class="${ loop.index == curPage ? 'jive-current' : ''}">
            <c:out value="${loop.index}"/>
        </a>
        <c:if test="${loop.index < numPages}">
            &nbsp;
        </c:if>
    </c:forEach>
    ]
    </p>
</c:if>

<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th>&nbsp;</th>
        <th nowrap><fmt:message key="pubsub.node.summary.id" /></th>
        <th nowrap><fmt:message key="pubsub.node.summary.name" /></th>
        <th nowrap><fmt:message key="pubsub.node.summary.description" /></th>
        <th nowrap><fmt:message key="pubsub.node.summary.items" /></th>
        <th nowrap><fmt:message key="pubsub.node.summary.affiliates" /></th>
        <th nowrap><fmt:message key="pubsub.node.summary.subscribers" /></th>
        <c:if test="${not PEPMode}" >
            <th nowrap><fmt:message key="global.edit" /></th>
        </c:if>
        <th nowrap><fmt:message key="global.delete" /></th>
    </tr>
</thead>
<tbody>

<c:if test="${nodeCount lt 1}">
    <tr>
        <td align="center" colspan="${PEPMode ? 8 : 9}">
            <fmt:message key="pubsub.node.summary.table.no_nodes" />
        </td>
    </tr>
</c:if>

<c:forEach var="node" items="${nodes}" varStatus="loop">

    <tr class="${ (loop.index%2)==0 ? 'jive-even' : 'jive-odd'}">
        <td width="1%">
            <c:out value="${start + 1 + loop.index}"/>
        </td>
        <td width="1%" valign="middle">
            <c:out value="${node.getNodeID()}"/>
        </td>
        <td nowrap width="1%" valign="middle">
            <c:out value="${node.getName()}"/>
        </td>
        <td valign="middle">
            <c:out value="${node.getDescription()}"/>
        </td>
        <td width="1%" align="center">
            <c:url value="pubsub-node-items.jsp" var="url">
                <c:param name="nodeID" value="${node.getNodeID()}" />
                <c:param name="owner" value="${owner}" />
            </c:url>
            <a href="${url}">
                <c:out value="${node.getPublishedItems().size()}" />
            </a>
        </td>
        <td width="1%" align="center">
            <c:url value="pubsub-node-affiliates.jsp" var="url">
                <c:param name="nodeID" value="${node.getNodeID()}" />
                <c:param name="owner" value="${owner}" />
            </c:url>
            <a href="${url}">
                <c:out value="${node.getAllAffiliates().size()}" />
            </a>
        </td>
        <td width="1%" align="center">
            <c:url value="pubsub-node-subscribers.jsp" var="url">
                <c:param name="nodeID" value="${node.getNodeID()}" />
                <c:param name="owner" value="${owner}" />
            </c:url>
            <a href="${url}">
                <c:out value="${node.getAllSubscriptions().size()}" />
            </a>
        </td>
        <c:if test="${not PEPMode}" >
            <td width="1%" align="center">
                <c:url value="pubsub-node-edit.jsp" var="url">
                    <c:param name="nodeID" value="${node.getNodeID()}" />
                </c:url>
                <a href="${url}" title="<fmt:message key="global.click_edit" />">
                    <img src="images/edit-16x16.gif" width="16" height="16" border="0" alt="">
                </a>
            </td>
        </c:if>
        <td width="1%" align="center" style="border-right:1px #ccc solid;">
            <c:url value="pubsub-node-delete.jsp" var="url">
                <c:param name="nodeID" value="${node.getNodeID()}" />
                <c:param name="owner" value="${owner}" />
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

<c:if test="${numPages gt 1}">
    <br>
    <p>
    <fmt:message key="global.pages" />:
    [
    <c:forEach begin="1" end="${numPages}" varStatus="loop">
        <c:url value="pubsub-node-summary.jsp" var="url">
            <c:param name="start" value="${(loop.index-1)*range}" />
            <c:param name="owner" value="${owner}" />
        </c:url>
        <a href="${url}" class="${ loop.index == curPage ? 'jive-current' : ''}">
            <c:out value="${loop.index}"/>
        </a>
        <c:if test="${loop.index < numPages}">
            &nbsp;
        </c:if>
    </c:forEach>
    ]
    </p>
</c:if>

    </body>
</html>
