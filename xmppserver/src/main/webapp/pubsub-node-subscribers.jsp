<%--
  -
  - Copyright (C) 2017-2022 Ignite Realtime Foundation. All rights reserved.
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     http://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
--%>
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="org.jivesoftware.openfire.pep.PEPServiceInfo,
                 org.jivesoftware.openfire.pubsub.Node,
                 org.jivesoftware.openfire.pubsub.NodeSubscription,
                 org.jivesoftware.openfire.pubsub.PubSubServiceInfo,
                 org.jivesoftware.openfire.XMPPServer,
                 org.jivesoftware.util.CookieUtils,
                 org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.util.StringUtils,
                 org.xmpp.packet.JID,
                 java.net.URLEncoder,
                 java.util.HashMap,
                 java.util.Map"
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
    String deleteID = ParamUtils.getParameter(request,"deleteID");
    String ownerString = ParamUtils.getParameter( request, "owner" );
    if ( ownerString == null )
    {
        ownerString = ParamUtils.getParameter( request, "username" );
    }

    final Map<String, String> errors = new HashMap<>();

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

    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (deleteID != null) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            deleteID = null;
            errors.put("csrf", "CSRF Failure!");
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

    // Delete specified subscription ID
    if (errors.isEmpty() && deleteID != null) {
        NodeSubscription subscription = node.getSubscription(deleteID);
        if (subscription != null) {

            node.cancelSubscription(subscription);
            // Log the event
            webManager.logEvent("Cancelled subscription ID: " + deleteID +  ", from node ID: " + nodeID, "Owner: " + subscription.getOwner().toBareJID());
            // Done, so redirect
            response.sendRedirect("pubsub-node-subscribers.jsp?nodeID=" + URLEncoder.encode(nodeID, "UTF-8")
                + "&deleteSuccess=true"
                + (owner != null ? "&owner=" + URLEncoder.encode(owner.toBareJID(), "UTF-8") : "")
                + "&ownerOfDeleted=" + URLEncoder.encode(subscription.getOwner().toBareJID(), "UTF-8"));
            return;
        }
    }

    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    pageContext.setAttribute("node", node);
    pageContext.setAttribute("owner", owner );
    pageContext.setAttribute("errors", errors);
%>

<html>
<head>
<title><fmt:message key="pubsub.node.subscribers.title"/></title>
<c:choose>
    <c:when test="${not empty owner and owner.domain eq webManager.serverInfo.XMPPDomain}">
        <meta name="subPageID" content="user-pep-node-summary"/>
        <meta name="extraParams" content="username=${admin:urlEncode(owner.node)}&nodeID=${admin:urlEncode(node.nodeID)}" />
    </c:when>
    <c:otherwise>
        <meta name="subPageID" content="pubsub-node-subscribers"/>
        <meta name="extraParams" content="nodeID=${admin:urlEncode(node.nodeID)}"/>
    </c:otherwise>
</c:choose>
</head>
<body>

    <c:choose>
        <c:when test="${empty errors and param.deleteSuccess}">
            <admin:infobox type="success">
                <fmt:message key="pubsub.node.subscribers.deleted">
                    <fmt:param value="${fn:escapeXml(param.ownerOfDeleted)}"/>
                </fmt:message>
            </admin:infobox>
        </c:when>
        <c:otherwise>
            <c:forEach var="err" items="${errors}">
                <admin:infobox type="error">
                    <c:choose>
                        <c:when test="${err.key eq 'csrf'}"><fmt:message key="global.csrf.failed" /></c:when>
                        <c:otherwise>
                            <c:if test="${not empty err.value}">
                                <fmt:message key="admin.error"/>: <c:out value="${err.value}"/>
                            </c:if>
                            (<c:out value="${err.key}"/>)
                        </c:otherwise>
                    </c:choose>
                </admin:infobox>
            </c:forEach>
        </c:otherwise>
    </c:choose>

    <p>
    <fmt:message key="pubsub.node.summary.table.info" />
    </p>

    <div class="jive-table">
    <table>
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
        <fmt:message key="pubsub.node.subscribers.table.info" />
    </p>

    <div class="jive-table">
    <table>
    <thead>
        <tr>
            <th scope="col"><fmt:message key="pubsub.node.subscribers.owner" /></th>
            <th scope="col"><fmt:message key="pubsub.node.subscribers.resource" /></th>
            <th scope="col"><fmt:message key="pubsub.node.subscribers.affiliation" /></th>
            <th scope="col"><fmt:message key="pubsub.node.subscribers.status" /></th>
            <th scope="col"><fmt:message key="pubsub.node.subscribers.expires" /></th>
            <th scope="col"><fmt:message key="global.delete" /></th>
        </tr>
    </thead>
    <tbody>
        <c:if test="${empty node.allSubscriptions}">
        <tr>
            <td style="text-align: center" colspan="6">
                <fmt:message key="pubsub.node.subscribers.table.no_subscribers" />
            </td>
        </tr>
        </c:if>

        <c:forEach var="subscription" items="${node.allSubscriptions}">
        <tr>
            <td>
            <c:out value="${subscription.owner.toBareJID()}"/>
            </td>
            <td>
            <c:out value="${subscription.JID.resource}"/>
            </td>
            <td>
            <c:out value="${subscription.affiliate.affiliation.name()}"/>
            </td>
            <td>
            <c:out value="${subscription.state.name()}"/>
            </td>
            <td>
            <fmt:formatDate type="both" dateStyle="medium" timeStyle="short" value="${subscription.expire}" />
            </td>
            <td style="width: 1%; text-align: center; border-right:1px #ccc solid;">
                <c:url value="pubsub-node-subscribers.jsp" var="url">
                    <c:param name="nodeID" value="${node.nodeID}" />
                    <c:param name="deleteID" value="${subscription.ID}" />
                    <c:param name="csrf" value="${csrf}" />
                    <c:param name="owner" value="${owner}"/>
                </c:url>
                <a href="${url}" title="<fmt:message key="global.click_delete" />">
                    <img src="images/delete-16x16.gif" alt="">
                </a>
            </td>
        </tr>
        </c:forEach>
    </tbody>
    </table>
    </div>

    </body>
</html>
