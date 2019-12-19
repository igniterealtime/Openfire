<%@ page contentType="text/html; charset=UTF-8" %>
<%@page import="org.jivesoftware.openfire.pep.PEPServiceInfo,
                org.jivesoftware.openfire.pubsub.NodeAffiliate,
                org.jivesoftware.openfire.pubsub.NodeSubscription,
                org.jivesoftware.openfire.pubsub.PubSubServiceInfo,
                org.jivesoftware.util.*,
                org.jivesoftware.openfire.pubsub.Node,
                org.jivesoftware.openfire.XMPPServer,
                org.xmpp.packet.JID,
                java.net.URLEncoder,
                java.util.HashMap,
                java.util.Map"
        errorPage="error.jsp"
%>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="admin" prefix="admin" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters //
    boolean cancel = ParamUtils.getParameter(request,"cancel") != null;
    boolean delete = ParamUtils.getParameter(request,"delete") != null;
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");
    String nodeID = ParamUtils.getParameter(request,"nodeID");
    String affiliateJID = ParamUtils.getParameter(request,"affiliateJID");

    final Map<String, String> errors = new HashMap<>();

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

    if (delete) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            delete = false;
            errors.put("csrf", "CSRF Failure!");
        }
    }

    // Handle a cancel
    if (cancel) {
        response.sendRedirect("pubsub-node-affiliates.jsp?nodeID="+URLEncoder.encode(nodeID, "UTF-8") + (owner != null ? "&owner=" + URLEncoder.encode(owner.toBareJID(), "UTF-8") : ""));
        return;
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
    NodeAffiliate affiliate = node.getAffiliate(new JID(affiliateJID));

    // Handle a affiliate delete:
    if (errors.isEmpty() && delete) {
        if (affiliate != null) {
            JID jid = new JID(affiliateJID);

            for (NodeSubscription subscription: affiliate.getSubscriptions()) {
                node.cancelSubscription(subscription);
            }

            switch(affiliate.getAffiliation()) {
                case outcast:
                    node.removeOutcast(jid);
                    break;
                case publisher:
                    node.removePublisher(jid);
                    break;
                case owner:
                    node.removeOwner(jid);
                    break;
                case none:
                    //None affiliation will have been removed as a result of removing the subscriptions.
                    break;
            }

            // Log the event
            webManager.logEvent("Deleted Affiliation for : " + affiliate + ", from Node " + nodeID, null);
        }
        // Done, so redirect
        response.sendRedirect("pubsub-node-affiliates.jsp?nodeID="+URLEncoder.encode( nodeID, "UTF-8" )
                + (owner != null ? "&owner=" + URLEncoder.encode(owner.toBareJID(), "UTF-8") : "")
                +"&deleteSuccess=true&affiliateJID="+URLEncoder.encode( affiliateJID, "UTF-8" ));
        return;
    }

    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    pageContext.setAttribute("node", node);
    pageContext.setAttribute("affiliate", affiliate);
    pageContext.setAttribute("owner", owner);
    pageContext.setAttribute("errors", errors);
%>

<html>
    <head>
        <title><fmt:message key="pubsub.node.affiliates.delete.title"/></title>
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

    <p>
        <fmt:message key="pubsub.node.affiliates.delete.info" />
    </p>

    <div class="jive-table">
        <table cellpadding="3" cellspacing="0" border="0" width="100%">
            <thead>
                <tr>
                    <th scope="col"><fmt:message key="pubsub.node.summary.id" /></th>
                    <th scope="col"><fmt:message key="pubsub.node.affiliates.jid" /></th>
                    <th scope="col"><fmt:message key="pubsub.node.affiliates.affiliation" /></th>
                </tr>
            </thead>
            <tbody>
                <tr>
                    <td><c:out value="${node.nodeID}"/></td>
                    <td><c:out value="${affiliate.JID.toBareJID()}"/></td>
                    <td><c:out value="${affiliate.affiliation.name()}"/></td>
                </tr>
            </tbody>
        </table>
    </div>
    <br>
    <br>
    <p>
        <fmt:message key="pubsub.node.affiliates.delete.info2" />
    </p>

    <div class="jive-table">
        <table cellpadding="3" cellspacing="0" border="0" width="100%">
            <thead>
                <tr>
                    <th scope="col"><fmt:message key="pubsub.node.subscribers.owner" /></th>
                    <th scope="col"><fmt:message key="pubsub.node.subscribers.resource" /></th>
                    <th scope="col"><fmt:message key="pubsub.node.subscribers.status" /></th>
                    <th scope="col"><fmt:message key="pubsub.node.subscribers.expires" /></th>
                </tr>
            </thead>
            <tbody>
                <c:if test="${empty affiliate.subscriptions}">
                    <tr>
                        <td align="center" colspan="4">
                            <fmt:message key="pubsub.node.affiliates.delete.table.no_subscriptions" />
                        </td>
                    </tr>
                </c:if>
                <c:forEach var="subscription" items="${affiliate.subscriptions}">
                    <tr>
                        <td>
                        <c:out value="${subscription.owner.toBareJID()}"/>
                        </td>
                        <td>
                        <c:out value="${subscription.JID.resource}"/>
                        </td>
                        <td>
                        <c:out value="${subscription.state.name()}"/>
                        </td>
                        <td>
                        <fmt:formatDate type="both" dateStyle="medium" timeStyle="short" value="${subscription.expire}" />
                        </td>
                    </tr>
                </c:forEach>
            </tbody>
        </table>
    </div>
    <br>
    <br>
    <p>
        <fmt:message key="pubsub.node.affiliates.delete.info3" />
    </p>

    <form action="pubsub-node-affiliates-delete.jsp">
        <input type="hidden" name="csrf" value="${csrf}">
        <input type="hidden" name="nodeID" value="${fn:escapeXml(node.nodeID)}">
        <input type="hidden" name="owner" value="${fn:escapeXml(owner)}">
        <input type="hidden" name="affiliateJID" value="${fn:escapeXml(affiliate.JID.toBareJID())}">

        <input type="submit" name="delete" value="<fmt:message key="pubsub.node.affiliates.delete.delete_affiliate" />">
        <input type="submit" name="cancel" value="<fmt:message key="global.cancel" />">
    </form>

    </body>
</html>
