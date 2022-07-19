<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer,
                 org.jivesoftware.openfire.pep.PEPServiceInfo,
                 org.jivesoftware.openfire.pubsub.Node,
                 org.jivesoftware.openfire.pubsub.PubSubServiceInfo,
                 org.jivesoftware.util.ListPager,
                 org.jivesoftware.util.ParamUtils,
                 org.xmpp.packet.JID,
                 java.util.List"
    errorPage="error.jsp"
%>
<%@ page import="java.util.ArrayList" %>
<%@ page import="org.jivesoftware.openfire.pubsub.PubsubNodeResultFilter" %>
<%@ page import="java.util.Arrays" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="admin" prefix="admin" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters
    int sortOrder = ParamUtils.getIntParameter(request, "sortOrder", webManager.getPageProperty("pubsub-node-summary", "console.sortOrder", PubsubNodeResultFilter.ASCENDING));
    int sortColumnNumber = ParamUtils.getIntParameter(request, "sortColumnNumber", webManager.getPageProperty("pubsub-node-summary", "console.sortColumnNumber", PubsubNodeResultFilter.SORT_NODE_IDENTIFIER));

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

    if (request.getParameter("sortOrder") != null) {
        webManager.setPageProperty("pubsub-node-summary", "console.sortOrder", sortOrder);
    }
    if (request.getParameter("sortColumnNumber") != null) {
        webManager.setPageProperty("pubsub-node-summary", "console.sortColumnNumber", sortColumnNumber);
    }

    boolean PEPMode = false;
    PubSubServiceInfo pubSubServiceInfo;
    if ( owner == null )
    {
        pubSubServiceInfo = webManager.getPubSubInfo();
    }
    else if ( XMPPServer.getInstance().getIQPEPHandler().getServiceManager().getPEPService( owner.asBareJID(), true ) != null )
    {
        PEPMode = true;
        pubSubServiceInfo = new PEPServiceInfo( owner );
    }
    else
    {
        pubSubServiceInfo = null;
    }

    final PubsubNodeResultFilter pubsubNodeResultFilter = PubsubNodeResultFilter.createDefaultSessionFilter();
    pubsubNodeResultFilter.setSortOrder(sortOrder);
    pubsubNodeResultFilter.setSortColumnNumber(sortColumnNumber);

    for (final String filterParam : pubsubNodeResultFilter.getFilterParams()) {
        final String filterValue = ParamUtils.getStringParameter(request, filterParam, "").trim();
        if (!filterValue.isEmpty()) {
            pubsubNodeResultFilter.addFilter(filterParam, filterValue);
        }
        pageContext.setAttribute(filterParam, filterValue);
    }

    List<Node> nodes;
    if (pubSubServiceInfo != null){
        nodes = pubSubServiceInfo.getLeafNodes();
    } else {
        nodes = new ArrayList<>();
    }
    nodes.sort(pubsubNodeResultFilter.getSortComparator());

    final List<String> additionalParams = new ArrayList<>(Arrays.asList(pubsubNodeResultFilter.getFilterParams()));
    additionalParams.add("username");

    final ListPager<Node> listPager = new ListPager<>(request, response, nodes, pubsubNodeResultFilter.getFilter(), pubsubNodeResultFilter.getSortColumnNumber(), pubsubNodeResultFilter.getSortOrder() == PubsubNodeResultFilter.DESCENDING, additionalParams.toArray(new String[0]));
    pageContext.setAttribute("listPager", listPager);
    pageContext.setAttribute("owner", owner);
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

<c:if test="${param.deleteSuccess}">
    <admin:infoBox type="success">
        <fmt:message key="pubsub.node.summary.deleted" />
    </admin:infoBox>
</c:if>

<p>
<fmt:message key="pubsub.node.summary.info" />
</p>

<p>
<fmt:message key="pubsub.node.summary.total_nodes" />: <c:out value="${listPager.totalItemCount}"/>
<c:if test="${listPager.filtered}">
    <fmt:message key="pubsub.node.summary.filtered_node_count" />: <c:out value="${listPager.filteredItemCount}"/>
</c:if>

<c:if test="${listPager.totalPages > 1}">
    <fmt:message key="global.showing" /> <c:out value="${listPager.firstItemNumberOnPage}"/>-<c:out value="${listPager.lastItemNumberOnPage}"/>
</c:if>
-- <fmt:message key="pubsub.node.summary.nodes_per_page" />: ${listPager.pageSizeSelection} <fmt:message key="pubsub.node.summary.sorted_id" />

</p>

<p><fmt:message key="global.pages" />: [ ${listPager.pageLinks} ]</p>

<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th>&nbsp;</th>
        <th nowrap>
            <a href="pubsub-node-summary.jsp" onclick='return toggleColumnOrder(${PubsubNodeResultFilter.SORT_NODE_IDENTIFIER})'>
                <fmt:message key="pubsub.node.summary.id" />
                <c:if test="${listPager.sortColumnNumber == PubsubNodeResultFilter.SORT_NODE_IDENTIFIER}">
                    <img src="images/sort_${listPager.sortDescending ? "descending" : "ascending"}.gif" alt="The sort order in this column is ${listPager.sortDescending ? "descending" : "ascending"} (click to toggle).">
                </c:if>
            </a>
        </th>
        <th nowrap>
            <a href="pubsub-node-summary.jsp" onclick='return toggleColumnOrder(${PubsubNodeResultFilter.SORT_NODE_NAME})'>
                <fmt:message key="pubsub.node.summary.name" />
                <c:if test="${listPager.sortColumnNumber == PubsubNodeResultFilter.SORT_NODE_NAME}">
                    <img src="images/sort_${listPager.sortDescending ? "descending" : "ascending"}.gif" alt="The sort order in this column is ${listPager.sortDescending ? "descending" : "ascending"} (click to toggle).">
                </c:if>
            </a>
        </th>
        <th nowrap>
            <a href="pubsub-node-summary.jsp" onclick='return toggleColumnOrder(${PubsubNodeResultFilter.SORT_NODE_DESCRIPTION})'>
                <fmt:message key="pubsub.node.summary.description" />
                <c:if test="${listPager.sortColumnNumber == PubsubNodeResultFilter.SORT_NODE_DESCRIPTION}">
                    <img src="images/sort_${listPager.sortDescending ? "descending" : "ascending"}.gif" alt="The sort order in this column is ${listPager.sortDescending ? "descending" : "ascending"} (click to toggle).">
                </c:if>
            </a>
        </th>
        <th nowrap>
            <a href="pubsub-node-summary.jsp" onclick='return toggleColumnOrder(${PubsubNodeResultFilter.SORT_ITEM_COUNT})'>
                <fmt:message key="pubsub.node.summary.items" />
                <c:if test="${listPager.sortColumnNumber == PubsubNodeResultFilter.SORT_ITEM_COUNT}">
                    <img src="images/sort_${listPager.sortDescending ? "descending" : "ascending"}.gif" alt="The sort order in this column is ${listPager.sortDescending ? "descending" : "ascending"} (click to toggle).">
                </c:if>
            </a>
        </th>
        <th nowrap>
            <a href="pubsub-node-summary.jsp" onclick='return toggleColumnOrder(${PubsubNodeResultFilter.SORT_AFFILIATE_COUNT})'>
                <fmt:message key="pubsub.node.summary.affiliates" />
                <c:if test="${listPager.sortColumnNumber == PubsubNodeResultFilter.SORT_AFFILIATE_COUNT}">
                    <img src="images/sort_${listPager.sortDescending ? "descending" : "ascending"}.gif" alt="The sort order in this column is ${listPager.sortDescending ? "descending" : "ascending"} (click to toggle).">
                </c:if>
            </a>
        </th>
        <th nowrap>
            <a href="pubsub-node-summary.jsp" onclick='return toggleColumnOrder(${PubsubNodeResultFilter.SORT_SUBSCRIBER_COUNT})'>
                <fmt:message key="pubsub.node.summary.subscribers" />
                <c:if test="${listPager.sortColumnNumber == PubsubNodeResultFilter.SORT_SUBSCRIBER_COUNT}">
                    <img src="images/sort_${listPager.sortDescending ? "descending" : "ascending"}.gif" alt="The sort order in this column is ${listPager.sortDescending ? "descending" : "ascending"} (click to toggle).">
                </c:if>
            </a>
        </th>
        <c:if test="${not PEPMode}" >
            <th nowrap><fmt:message key="global.edit" /></th>
        </c:if>
        <th nowrap><fmt:message key="pubsub.node.summary.configuration" /></th>
        <th nowrap><fmt:message key="global.delete" /></th>
    </tr>
    <tr>
        <td></td>
        <td nowrap>
            <input type="search" id="${fn:escapeXml(PubsubNodeResultFilter.FILTER_NODE_IDENTIFIER)}" size="20" value="${fn:escapeXml(searchNodeId)}"/>
            <img src="images/search-16x16.png" width="16" height="16" alt="search" title="search" style="vertical-align: middle;" onclick="submitForm();">
        </td>
        <td nowrap>
            <input type="search" id="${fn:escapeXml(PubsubNodeResultFilter.FILTER_NODE_NAME)}" size="20" value="${fn:escapeXml(searchNodeName)}"/>
            <img src="images/search-16x16.png" width="16" height="16" alt="search" title="search" style="vertical-align: middle;" onclick="submitForm();">
        </td>
        <td nowrap>
            <input type="search" id="${fn:escapeXml(PubsubNodeResultFilter.FILTER_NODE_DESCRIPTION)}" size="20" value="${fn:escapeXml(searchNodeDescription)}"/>
            <img src="images/search-16x16.png" width="16" height="16" alt="search" title="search" style="vertical-align: middle;" onclick="submitForm();">
        </td>
        <td nowrap>
            <input type="search" id="${fn:escapeXml(PubsubNodeResultFilter.FILTER_ITEM_COUNT)}" size="20" value="${fn:escapeXml(searchItemCount)}"/>
            <img src="images/search-16x16.png" width="16" height="16" alt="search" title="search" style="vertical-align: middle;" onclick="submitForm();">
        </td>
        <td nowrap>
            <input type="search" id="${fn:escapeXml(PubsubNodeResultFilter.FILTER_AFFILIATE_COUNT)}" size="20" value="${fn:escapeXml(searchAffiliateCount)}"/>
            <img src="images/search-16x16.png" width="16" height="16" alt="search" title="search" style="vertical-align: middle;" onclick="submitForm();">
        </td>
        <td nowrap>
            <input type="search" id="${fn:escapeXml(PubsubNodeResultFilter.FILTER_SUBSCRIBER_COUNT)}" size="20" value="${fn:escapeXml(searchSubscriberCount)}"/>
            <img src="images/search-16x16.png" width="16" height="16" alt="search" title="search" style="vertical-align: middle;" onclick="submitForm();">
        </td>
        <c:if test="${not PEPMode}">
            <td></td>
        </c:if>
        <td></td>
        <td></td>
    </tr>
</thead>
<tbody>

<c:if test="${listPager.filteredItemCount lt 1}">
    <tr>
        <td align="center" colspan="${PEPMode ? 8 : 9}">
            <c:choose>
                <c:when test="${listPager.filtered}"><fmt:message key="pubsub.node.summary.table.no_nodes_matching" /></c:when>
                <c:otherwise><fmt:message key="pubsub.node.summary.table.no_nodes" /></c:otherwise>
            </c:choose>
        </td>
    </tr>
</c:if>

<%--@elvariable id="node" type="org.jivesoftware.openfire.pubsub.Node"--%>
<c:forEach var="node" items="${listPager.itemsOnCurrentPage}" varStatus="loop">

    <tr class="${ (loop.index%2)==0 ? 'jive-even' : 'jive-odd'}">
        <td width="1%">
            <c:out value="${listPager.firstItemNumberOnPage + loop.index}"/>
        </td>
        <td width="1%" valign="middle">
            <c:out value="${node.nodeID}"/>
        </td>
        <td nowrap width="1%" valign="middle">
            <c:out value="${node.name}"/>
        </td>
        <td valign="middle">
            <c:out value="${node.description}"/>
        </td>
        <td width="1%" align="center">
            <c:url value="pubsub-node-items.jsp" var="url">
                <c:param name="nodeID" value="${node.nodeID}" />
                <c:param name="owner" value="${owner}" />
            </c:url>
            <a href="${url}">
                <c:out value="${node.publishedItems.size()}" />
            </a>
        </td>
        <td width="1%" align="center">
            <c:url value="pubsub-node-affiliates.jsp" var="url">
                <c:param name="nodeID" value="${node.nodeID}" />
                <c:param name="owner" value="${owner}" />
            </c:url>
            <a href="${url}">
                <c:out value="${node.allAffiliates.size()}" />
            </a>
        </td>
        <td width="1%" align="center">
            <c:url value="pubsub-node-subscribers.jsp" var="url">
                <c:param name="nodeID" value="${node.nodeID}" />
                <c:param name="owner" value="${owner}" />
            </c:url>
            <a href="${url}">
                <c:out value="${node.allSubscriptions.size()}" />
            </a>
        </td>
        <c:if test="${not PEPMode}" >
            <td width="1%" align="center">
                <c:url value="pubsub-node-edit.jsp" var="url">
                    <c:param name="nodeID" value="${node.nodeID}" />
                </c:url>
                <a href="${url}" title="<fmt:message key="global.click_edit" />">
                    <img src="images/edit-16x16.gif" width="16" height="16" border="0" alt="">
                </a>
            </td>
        </c:if>
        <td width="1%" align="center">
            <c:url value="pubsub-node-configuration.jsp" var="url">
                <c:param name="nodeID" value="${node.nodeID}" />
                <c:param name="owner" value="${owner}" />
            </c:url>
            <a href="${url}" title="<fmt:message key="pubsub.node.summary.click_config" />">
                <img src="images/info-16x16.gif" width="16" height="16" border="0" alt="">
            </a>
        </td>
        <td width="1%" align="center" style="border-right:1px #ccc solid;">
            <c:url value="pubsub-node-delete.jsp" var="url">
                <c:param name="nodeID" value="${node.nodeID}" />
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

<p><fmt:message key="global.pages" />: [ ${listPager.pageLinks} ]</p>

${listPager.jumpToPageForm}

<script type="text/javascript">
${listPager.pageFunctions}
</script>
    </body>
</html>
