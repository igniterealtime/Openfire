<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="java.util.*"
         errorPage="error.jsp"
%>
<%@ page import="org.jivesoftware.openfire.net.DNSUtil" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionConfiguration" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionManagerImpl" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionType" %>

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
    final List<DNSUtil.WeightedHostAddress> dnsSrvRecordsClientTLS = DNSUtil.srvLookup( "xmpps-client", "tcp", xmppDomain );
    final List<DNSUtil.WeightedHostAddress> dnsSrvRecordsServerTLS = DNSUtil.srvLookup( "xmpps-server", "tcp", xmppDomain );

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

    final ConnectionManagerImpl manager = (ConnectionManagerImpl) XMPPServer.getInstance().getConnectionManager();

    final ConnectionConfiguration plaintextClientConfiguration  = manager.getListener( ConnectionType.SOCKET_C2S, false ).generateConnectionConfiguration();
    final ConnectionConfiguration legacymodeClientConfiguration = manager.getListener( ConnectionType.SOCKET_C2S, true  ).generateConnectionConfiguration();
    final ConnectionConfiguration plaintextServerConfiguration  = manager.getListener( ConnectionType.SOCKET_S2S, false ).generateConnectionConfiguration();
    final ConnectionConfiguration legacymodeServerConfiguration = manager.getListener( ConnectionType.SOCKET_S2S, true  ).generateConnectionConfiguration();

    pageContext.setAttribute( "xmppDomain", xmppDomain );
    pageContext.setAttribute( "hostname", hostname );
    pageContext.setAttribute( "dnsSrvRecordsClient", dnsSrvRecordsClient );
    pageContext.setAttribute( "dnsSrvRecordsServer", dnsSrvRecordsServer );
    pageContext.setAttribute( "dnsSrvRecordsClientTLS", dnsSrvRecordsClientTLS );
    pageContext.setAttribute( "dnsSrvRecordsServerTLS", dnsSrvRecordsServerTLS );
    pageContext.setAttribute( "detectedRecordForHostname", detectedRecordForHostname );
    pageContext.setAttribute( "plaintextClientConfiguration", plaintextClientConfiguration );
    pageContext.setAttribute( "legacymodeClientConfiguration", legacymodeClientConfiguration );
    pageContext.setAttribute( "plaintextServerConfiguration", plaintextServerConfiguration );
    pageContext.setAttribute( "legacymodeServerConfiguration", legacymodeServerConfiguration );
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
    <c:when test="${empty dnsSrvRecordsServer and empty dnsSrvRecordsClient and empty dnsSrvRecordsClientTLS and empty dnsSrvRecordsServerTLS}">
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

<c:if test="${not empty dnsSrvRecordsClient or not empty dnsSrvRecordsServer or not empty dnsSrvRecordsClientTLS or not empty dnsSrvRecordsServerTLS}">

    <fmt:message key="system.dns.srv.check.recordbox.title" var="plaintextboxtitle"/>
    <admin:contentBox title="${plaintextboxtitle}">

        <p><fmt:message key="system.dns.srv.check.recordbox.description"/></p>

        <c:if test="${not empty dnsSrvRecordsClient}">
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
        </c:if>

        <c:if test="${not empty dnsSrvRecordsClientTLS}">
            <div class="jive-table">
                <table cellpadding="0" cellspacing="0" border="0" width="100%">
                    <thead>
                    <tr>
                        <th>&nbsp;</th>
                        <th nowrap><fmt:message key="system.dns.srv.check.label.client-host-tls" /></th>
                        <th nowrap><fmt:message key="system.dns.srv.check.label.port" /></th>
                        <th nowrap><fmt:message key="system.dns.srv.check.label.priority" /></th>
                        <th nowrap><fmt:message key="system.dns.srv.check.label.weight" /></th>
                    </tr>
                    </thead>
                    <tbody>
                    <c:forEach var="dnsSrvRecord" items="${dnsSrvRecordsClientTLS}" varStatus="varStatus">
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
        </c:if>

        <c:if test="${not empty dnsSrvRecordsServer}">
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

            <br/>
        </c:if>

        <c:if test="${not empty dnsSrvRecordsServerTLS}">
            <div class="jive-table">
                <table cellpadding="0" cellspacing="0" border="0" width="100%">
                    <thead>
                    <tr>
                        <th>&nbsp;</th>
                        <th nowrap><fmt:message key="system.dns.srv.check.label.server-host-tls" /></th>
                        <th nowrap><fmt:message key="system.dns.srv.check.label.port" /></th>
                        <th nowrap><fmt:message key="system.dns.srv.check.label.priority" /></th>
                        <th nowrap><fmt:message key="system.dns.srv.check.label.weight" /></th>
                    </tr>
                    </thead>
                    <tbody>
                    <c:forEach var="dnsSrvRecord" items="${dnsSrvRecordsServerTLS}" varStatus="varStatus">
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
        </c:if>

    </admin:contentBox>
</c:if>

<br/>

<p>
    <fmt:message key="system.dns.srv.check.rationale" />
</p>
<p>
    <fmt:message key="system.dns.srv.check.example" />
<ul>
    <c:if test="${plaintextClientConfiguration.enabled}">
        <li><tt>_xmpp-client._tcp.<c:out value="${xmppDomain}"/>. 86400 IN SRV 0 5 ${plaintextClientConfiguration.port} <c:out value="${hostname}"/>.</tt></li>
    </c:if>
    <c:if test="${legacymodeClientConfiguration.enabled}">
        <li><tt>_xmpps-client._tcp.<c:out value="${xmppDomain}"/>. 86400 IN SRV 0 5 ${legacymodeClientConfiguration.port} <c:out value="${hostname}"/>.</tt></li>
    </c:if>
    <c:if test="${plaintextServerConfiguration.enabled}">
        <li><tt>_xmpp-server._tcp.<c:out value="${xmppDomain}"/>. 86400 IN SRV 0 5 ${plaintextServerConfiguration.port} <c:out value="${hostname}"/>.</tt></li>
    </c:if>
    <c:if test="${legacymodeServerConfiguration.enabled}">
        <li><tt>_xmpps-server._tcp.<c:out value="${xmppDomain}"/>. 86400 IN SRV 0 5 ${legacymodeServerConfiguration.port} <c:out value="${hostname}"/>.</tt></li>
    </c:if>
</ul>
</p>
<p>
    <fmt:message key="system.dns.srv.check.disclaimer" />
</p>

</body>
</html>
