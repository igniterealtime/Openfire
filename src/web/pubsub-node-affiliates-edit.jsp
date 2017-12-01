<%@page import="org.jivesoftware.openfire.pep.PEPServiceInfo,
                org.jivesoftware.openfire.pubsub.NodeAffiliate,
                org.jivesoftware.openfire.pubsub.NodeAffiliate.Affiliation,
                org.jivesoftware.openfire.pubsub.NodeSubscription,
                org.jivesoftware.openfire.pubsub.PubSubServiceInfo,
                org.jivesoftware.util.*,
                org.jivesoftware.openfire.pubsub.Node,
                org.jivesoftware.openfire.XMPPServer,
                org.xmpp.packet.JID,
                java.net.URLEncoder"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="admin" prefix="admin" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters //
    boolean cancel = ParamUtils.getParameter(request,"cancel") != null;
    boolean update = ParamUtils.getParameter(request,"update") != null;
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");
    String nodeID = ParamUtils.getParameter(request,"nodeID");
    String affiliateJID = ParamUtils.getParameter(request,"affiliateJID");
    String affiliation = ParamUtils.getParameter(request,"affiliation");

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

    if (update) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            update = false;
        }
    }

    // Handle a cancel
    if (cancel) {
        response.sendRedirect("pubsub-node-affiliates.jsp?nodeID="+nodeID
                + (owner != null ? "&owner=" + URLEncoder.encode(owner.toBareJID(), "UTF-8") : ""));
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

    // Load the affiliate object
    Node node = pubSubServiceInfo.getNode(nodeID);
    NodeAffiliate affiliate = node.getAffiliate(new JID(affiliateJID));

    // Handle a affiliation update:
    if (update) {
        if (affiliate != null) {
            JID jid = new JID(affiliateJID);

            String oldAffiliation = affiliate.getAffiliation().name();

            switch(NodeAffiliate.Affiliation.valueOf(affiliation)) {
                case outcast:
                    node.addOutcast(jid);
                    break;
                case publisher:
                    node.addPublisher(jid);
                    break;
                case owner:
                    node.addOwner(jid);
                    break;
                case none:
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
                        break;
                    }
            }

            // Log the event
            webManager.logEvent("Changed affiliation between Node: " + nodeID + ", and JID: " + affiliateJID, "Changed from " + oldAffiliation +" to " + affiliation);
        }
        // Done, so redirect
        response.sendRedirect("pubsub-node-affiliates.jsp?nodeID="+nodeID+"&updateSuccess=true&affiliateJID="+affiliateJID);
        return;
    }

    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    pageContext.setAttribute("node", node);
    pageContext.setAttribute("affiliate", affiliate);
    pageContext.setAttribute("affiliations", Affiliation.values());
    pageContext.setAttribute("owner", owner);

%>

<html>
    <head>
        <title><fmt:message key="pubsub.node.affiliates.edit.title"/></title>
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

    <p>
        <fmt:message key="pubsub.node.affiliates.edit.info" />
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
                    <td><c:out value="${node.getNodeID()}"/></td>
                    <td><c:out value="${affiliate.getJID().toBareJID()}"/></td>
                    <td><c:out value="${affiliate.getAffiliation().name()}"/></td>
                </tr>
            </tbody>
        </table>
    </div>
    <br>
    <br>
    <p>
        <fmt:message key="pubsub.node.affiliates.edit.info2" />
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
                <c:if test="${empty affiliate.getSubscriptions()}">
                    <tr>
                        <td align="center" colspan="4">
                            <fmt:message key="pubsub.node.affiliates.delete.table.no_subscriptions" />
                        </td>
                    </tr>
                </c:if>
                <c:forEach var="subscription" items="${affiliate.getSubscriptions()}">
                    <tr>
                        <td>
                        <c:out value="${subscription.getOwner().toBareJID()}"/>
                        </td>
                        <td>
                        <c:out value="${subscription.getJID().getResource()}"/>
                        </td>
                        <td>
                        <c:out value="${subscription.getState().name()}"/>
                        </td>
                        <td>
                        <fmt:formatDate type="both" dateStyle="medium" timeStyle="short" value="${subscription.getExpire()}" />
                        </td>
                    </tr>
                </c:forEach>
            </tbody>
        </table>
    </div>
    <br>
    <br>
    <p>
        <fmt:message key="pubsub.node.affiliates.edit.info3" />
    </p>

    <form action="pubsub-node-affiliates-edit.jsp">
        <input type="hidden" name="csrf" value="${csrf}">
        <input type="hidden" name="nodeID" value="${node.nodeID}">
        <input type="hidden" name="owner" value="${owner}">
        <input type="hidden" name="affiliateJID" value="${affiliate.getJID().toBareJID()}">

        <fieldset>

        <select name="affiliation">
        <c:forEach var="value" items="${affiliations}">
            <c:choose>
            <c:when test="${value eq affiliate.getAffiliation()}">
                <option value="${value.name()}" selected>${value.name()}</option>
            </c:when>
            <c:otherwise>
                <option value="${value.name()}">${value.name()}</option>
            </c:otherwise>
            </c:choose>
        </c:forEach>
        </select>

        </fieldset>
        <br>
        <br>

        <input type="submit" name="update" value="<fmt:message key="global.update" />">
        <input type="submit" name="cancel" value="<fmt:message key="global.cancel" />">
    </form>

    </body>
</html>
