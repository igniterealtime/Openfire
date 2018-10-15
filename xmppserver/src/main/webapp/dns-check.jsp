<%@ page import="java.util.*"
         errorPage="error.jsp"
%>
<%@ page import="org.jivesoftware.openfire.net.DNSUtil" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>

<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<%
    final String xmppDomain = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
    final String hostname = XMPPServer.getInstance().getServerInfo().getHostname();
    final List<DNSUtil.WeightedHostAddress> dnsSrvRecordsClient = DNSUtil.srvLookup( "xmpp-client", "tcp", xmppDomain );
    final List<DNSUtil.WeightedHostAddress> dnsSrvRecordsServer = DNSUtil.srvLookup( "xmpp-server", "tcp", xmppDomain );

    boolean detectedRecordForHostname = false;
    for ( final DNSUtil.WeightedHostAddress dnsSrvRecord : dnsSrvRecordsClient )
    {
        if ( hostname.equalsIgnoreCase( dnsSrvRecord.getHost() ) )
        {
            detectedRecordForHostname = true;
            break;
        }
    }

    for ( final DNSUtil.WeightedHostAddress dnsSrvRecord : dnsSrvRecordsServer )
    {
        if ( hostname.equalsIgnoreCase( dnsSrvRecord.getHost() ) )
        {
            detectedRecordForHostname = true;
            break;
        }
    }

    pageContext.setAttribute( "xmppDomain", xmppDomain );
    pageContext.setAttribute( "hostname", hostname );
    pageContext.setAttribute( "dnsSrvRecordsClient", dnsSrvRecordsClient );
    pageContext.setAttribute( "dnsSrvRecordsServer", dnsSrvRecordsServer );
    pageContext.setAttribute( "detectedRecordForHostname", detectedRecordForHostname );
%>

<html>
<head>
    <title><fmt:message key="system.dns.srv.check.title"/></title>
    <meta name="pageID" content="server-settings"/>
</head>
<body>

<c:choose>
    <c:when test="${detectedRecordForHostname}">
        <admin:infobox type="info">
            <fmt:message key="system.dns.srv.check.detected_matching_records.one-liner" />
        </admin:infobox>
        <fmt:message key="system.dns.srv.check.detected_matching_records.description" var="plaintextboxcontent"/>
    </c:when>
    <c:when test="${xmppDomain eq hostname}">
        <admin:infobox type="info">
            <fmt:message key="system.dns.srv.check.xmppdomain_equals_hostname.one-liner" />
        </admin:infobox>
        <fmt:message key="system.dns.srv.check.xmppdomain_equals_hostname.description" var="plaintextboxcontent"/>
    </c:when>
    <c:when test="${empty dnsSrvRecordsServer and empty dnsSrvRecordsClient}">
        <admin:infobox type="warning">
            <fmt:message key="system.dns.srv.check.no-records.one-liner" />
        </admin:infobox>
        <fmt:message key="system.dns.srv.check.no-records.description" var="plaintextboxcontent"/>
    </c:when>
    <c:otherwise>
        <admin:infobox type="warning">
            <fmt:message key="system.dns.srv.check.no-match.one-liner" />
        </admin:infobox>
        <fmt:message key="system.dns.srv.check.no-match.description" var="plaintextboxcontent"/>
    </c:otherwise>
</c:choose>

<p>
    <fmt:message key="system.dns.srv.check.info">
        <fmt:param value="${xmppDomain}" />
        <fmt:param value="${hostname}" />
        <fmt:param value="<a href=\"server-props.jsp\">" />
        <fmt:param value="</a>"/>
    </fmt:message>
</p>

<fmt:message key="system.dns.srv.check.name" var="plaintextboxtitle"/>
<admin:contentBox title="${plaintextboxtitle}">
    <c:out value="${plaintextboxcontent}"/>
</admin:contentBox>

<c:if test="${not empty dnsSrvRecordsClient or not empty dnsSrvRecordsServer}">

    <fmt:message key="system.dns.srv.check.recordbox.title" var="plaintextboxtitle"/>
    <admin:contentBox title="${plaintextboxtitle}">

        <p><fmt:message key="system.dns.srv.check.recordbox.description"/></p>

        <div class="jive-table">
            <table cellpadding="0" cellspacing="0" border="0" width="100%">
                <thead>
                <tr>
                    <th>&nbsp;</th>
                    <th nowrap><fmt:message key="system.dns.srv.check.label.client-host" /></th>
                    <th nowrap><fmt:message key="system.dns.srv.check.label.port" /></th>
                    <th nowrap><fmt:message key="system.dns.srv.check.label.priority" /></th>
                    <th nowrap><fmt:message key="system.dns.srv.check.label.weight" /></th>
                </tr>
                </thead>
                <tbody>
                <c:forEach var="dnsSrvRecord" items="${dnsSrvRecordsClient}" varStatus="varStatus">
                    <c:choose>
                        <c:when test="${dnsSrvRecord.host.toLowerCase() eq hostname}">
                            <c:set var="cssClass" value="jive-highlight"/>
                        </c:when>
                        <c:otherwise>
                            <c:set var="cssClass" value="${varStatus.count % 2 eq 0 ? 'jive-even' : 'jive-odd' }"/>
                        </c:otherwise>
                    </c:choose>
                    <tr class="${cssClass}">
                        <td width="1%" nowrap><c:out value="${varStatus.count}"/></td>
                        <td nowrap><c:out value="${dnsSrvRecord.host}"/></td>
                        <td nowrap><c:out value="${dnsSrvRecord.port}"/></td>
                        <td nowrap><c:out value="${dnsSrvRecord.priority}"/></td>
                        <td nowrap><c:out value="${dnsSrvRecord.weight}"/></td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </div>

        <br/>

        <div class="jive-table">
            <table cellpadding="0" cellspacing="0" border="0" width="100%">
                <thead>
                <tr>
                    <th>&nbsp;</th>
                    <th nowrap><fmt:message key="system.dns.srv.check.label.server-host" /></th>
                    <th nowrap><fmt:message key="system.dns.srv.check.label.port" /></th>
                    <th nowrap><fmt:message key="system.dns.srv.check.label.priority" /></th>
                    <th nowrap><fmt:message key="system.dns.srv.check.label.weight" /></th>
                </tr>
                </thead>
                <tbody>
                <c:forEach var="dnsSrvRecord" items="${dnsSrvRecordsServer}" varStatus="varStatus">
                    <c:choose>
                        <c:when test="${dnsSrvRecord.host.toLowerCase() eq hostname}">
                            <c:set var="cssClass" value="jive-highlight"/>
                        </c:when>
                        <c:otherwise>
                            <c:set var="cssClass" value="${varStatus.count % 2 eq 0 ? 'jive-even' : 'jive-odd' }"/>
                        </c:otherwise>
                    </c:choose>
                    <tr class="${cssClass}">
                        <td width="1%" nowrap><c:out value="${varStatus.count}"/></td>
                        <td nowrap><c:out value="${dnsSrvRecord.host}"/></td>
                        <td nowrap><c:out value="${dnsSrvRecord.port}"/></td>
                        <td nowrap><c:out value="${dnsSrvRecord.priority}"/></td>
                        <td nowrap><c:out value="${dnsSrvRecord.weight}"/></td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </div>

    </admin:contentBox>
</c:if>

<br/>

<p>
    <fmt:message key="system.dns.srv.check.rationale" />
</p>
<p>
    <fmt:message key="system.dns.srv.check.example" />
    <!-- TODO verify the port value! -->
<ul>
    <li><tt>_xmpp-client._tcp.<c:out value="${xmppDomain}"/>. 86400 IN SRV 0 5 5222 <c:out value="${hostname}"/>.</tt></li>
    <li><tt>_xmpp-server._tcp.<c:out value="${xmppDomain}"/>. 86400 IN SRV 0 5 5269 <c:out value="${hostname}"/>.</tt></li>
</ul>
</p>
<p>
    <fmt:message key="system.dns.srv.check.disclaimer" />
</p>

</body>
</html>
