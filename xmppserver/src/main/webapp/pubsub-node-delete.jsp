<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="org.jivesoftware.openfire.pep.PEPServiceInfo,
                 org.jivesoftware.openfire.pubsub.Node,
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
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters //
    boolean cancel = ParamUtils.getParameter(request,"cancel") != null;
    boolean delete = ParamUtils.getParameter(request,"delete") != null;
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

    if (delete) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            delete = false;
            errors.put("csrf", "CSRF Failure!");
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    String nodeID = ParamUtils.getParameter(request,"nodeID");
    String reason = ParamUtils.getParameter(request,"reason");

    // Handle a cancel
    if (cancel) {
        response.sendRedirect("pubsub-node-summary.jsp"+ (owner != null ? "?owner=" + URLEncoder.encode(owner.toBareJID(), "UTF-8") : ""));
        return;
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

    // Handle a node delete:
    if (errors.isEmpty() && delete) {
        // Delete the node
        if (node != null) {
            // If the node still exists then destroy it
            node.delete();
            // Log the event
            webManager.logEvent("destroyed PubSub Node " + nodeID, "reason = " + reason );
        }
        // Done, so redirect
        response.sendRedirect("pubsub-node-summary.jsp?deleteSuccess=true"
            + (owner != null ? "&owner=" + URLEncoder.encode( owner.toBareJID(), "UTF-8") : "") );
        return;
    }

    pageContext.setAttribute("node", node);
    pageContext.setAttribute("owner", owner);
    pageContext.setAttribute("errors", errors);
%>

<html>
    <head>
        <title><fmt:message key="pubsub.node.delete.title"/></title>
        <c:choose>
            <c:when test="${not empty owner and owner.domain eq webManager.serverInfo.XMPPDomain}">
                <meta name="subPageID" content="user-pep-node-summary"/>
                <meta name="extraParams" content="username=${admin:urlEncode(owner.node)}&nodeID=${admin:urlEncode(node.nodeID)}" />
            </c:when>
            <c:otherwise>
                <meta name="subPageID" content="pubsub-node-delete"/>
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
    <fmt:message key="pubsub.node.delete.info" />
        <b>
            <c:out value="${node.nodeID}"/>
        </b>
    <fmt:message key="pubsub.node.delete.detail" />
</p>

<form action="pubsub-node-delete.jsp">
    <input type="hidden" name="csrf" value="${csrf}">
    <input type="hidden" name="nodeID" value="${fn:escapeXml(node.nodeID)}">
    <input type="hidden" name="owner" value="${fn:escapeXml(owner)}">

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
                <label for="reason"><fmt:message key="pubsub.node.delete.reason" /></label>
            </td>
            <td>
                <input type="text" size="50" maxlength="150" name="reason" id="reason">
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
