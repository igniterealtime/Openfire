<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  -
  - Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

<%@ page import="org.jivesoftware.openfire.SessionManager,
                 org.jivesoftware.openfire.session.IncomingServerSession,
                 org.jivesoftware.openfire.session.OutgoingServerSession,
                 org.jivesoftware.util.JiveGlobals,
                 org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.util.StringUtils,
                 java.text.NumberFormat"
    errorPage="error.jsp"
%>
<%@ page import="org.slf4j.Logger" %>
<%@ page import="org.slf4j.LoggerFactory" %>
<%@ page import="org.jivesoftware.openfire.cluster.ClusterManager" %>
<%@ page import="org.jivesoftware.openfire.session.LocalSession" %>
<%@ page import="java.util.stream.Collectors" %>
<%@ page import="java.util.*" %>
<%@ page import="java.net.InetAddress" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<% // Get parameters
    String domainname = ParamUtils.getParameter(request, "hostname");

    // Handle a "go back" click:
    if (request.getParameter("back") != null) {
        response.sendRedirect("server-session-summary.jsp");
        return;
    }

    // Get the session & address objects
    SessionManager sessionManager = webManager.getSessionManager();
    List<IncomingServerSession> inSessions = sessionManager.getIncomingServerSessions(domainname);
    List<OutgoingServerSession> outSessions = sessionManager.getOutgoingServerSessions(domainname);

    // Sort them by remote peer.
    final Map<String, Set<IncomingServerSession>> inByHost = inSessions.stream().collect(Collectors.groupingBy(e -> {
        try {
            return e.getHostAddress();
        } catch (Exception t) {
            return "Invalid session/connection";
        }
    }, Collectors.mapping(e -> e, Collectors.toSet())));

    final Map<String, Set<OutgoingServerSession>> outByHost = outSessions.stream().collect(Collectors.groupingBy(e -> {
        try {
            return e.getHostAddress();
        } catch (Exception t) {
            return "Invalid session/connection";
        }
    }, Collectors.mapping(e -> e, Collectors.toSet())));

    Map<String, String> allHosts = new HashMap<>();

    Set<String> hosts = new HashSet<>();
    hosts.addAll(inByHost.keySet());
    hosts.addAll(outByHost.keySet());

    for (String host : hosts) {
        try {
            allHosts.put(host, InetAddress.getByName(host).getCanonicalHostName());
        } catch (Exception e) {
            allHosts.put(host, null);
        }
    }

    final boolean clusteringEnabled = ClusterManager.isClusteringStarted() || ClusterManager.isClusteringStarting();

    pageContext.setAttribute("domainname", domainname);
    pageContext.setAttribute("allHosts", allHosts);
    pageContext.setAttribute("inByHost", inByHost);
    pageContext.setAttribute("outByHost", outByHost);
    pageContext.setAttribute("clusteringEnabled", clusteringEnabled);
%>

<html>
    <head>
        <title><fmt:message key="server.session.details.title"/></title>
        <meta name="pageID" content="server-session-summary"/>
    </head>
    <body>

        <p>
            <fmt:message key="server.session.details.info">
                <fmt:param value="<b>${fn:escapeXml(domainname)}</b>" />
            </fmt:message>
        </p>

        <div class="jive-table">
            <table cellpadding="0" cellspacing="0" border="0" width="100%">
                <tr>
                    <th colspan="2">
                        <fmt:message key="server.session.details.domain.title" />
                    </th>
                </tr>
                <tr>
                    <td class="c1">
                        <fmt:message key="server.session.details.domainname" />
                    </td>
                    <td>
                        <c:out value="${domainname}"/>
                    </td>
                </tr>
                <tr>
                    <td class="c1">
                        <fmt:message key="server.session.label.connection" />
                    </td>
                    <td>
                        <c:choose>
                            <c:when test="${not empty inByHost and empty outByHost}">
                                <img src="images/incoming_32x16.gif" width="32" height="16" border="0" title="<fmt:message key='server.session.connection.incoming' />" alt="<fmt:message key='server.session.connection.incoming' />">
                                <fmt:message key="server.session.connection.incoming" />
                            </c:when>
                            <c:when test="${empty inByHost and not empty outByHost}">
                                <img src="images/outgoing_32x16.gif" width="32" height="16" border="0" title="<fmt:message key='server.session.connection.outgoing' />" alt="<fmt:message key='server.session.connection.outgoing' />">
                                <fmt:message key="server.session.connection.outgoing" />
                            </c:when>
                            <c:otherwise>
                                <img src="images/both_32x16.gif" width="32" height="16" border="0" title="<fmt:message key='server.session.connection.both' />" alt="<fmt:message key='server.session.connection.both' />">
                                <fmt:message key="server.session.connection.both" />
                            </c:otherwise>
                        </c:choose>
                    </td>
                </tr>
            </table>

        </div>

        <br/>

        <c:forEach items="${allHosts}" var="host">

            <c:set var="inSessions" value="${inByHost[host.key]}"/>
            <c:set var="outSessions" value="${outByHost[host.key]}"/>

            <div class="jive-table">
                <table cellpadding="0" cellspacing="0" border="0" width="100%">
                    <tr>
                        <th colspan="2">
                            <fmt:message key="server.session.details.title" />
                        </th>
                    </tr>
                    <tr>
                        <td class="c1">
                            <fmt:message key="server.session.details.hostname" />
                        </td>
                        <td>
                            <c:out value="${host.key}"/>
                            <c:if test="${not empty host.value}">/ <c:out value="${host.value}"/></c:if>
                        </td>
                    </tr>
                    <tr>
                        <td class="c1">
                            <fmt:message key="server.session.label.connection" />
                        </td>
                        <td>
                            <c:choose>
                                <c:when test="${not empty inSessions and empty outSessions}">
                                    <img src="images/incoming_32x16.gif" width="32" height="16" border="0" title="<fmt:message key='server.session.connection.incoming' />" alt="<fmt:message key='server.session.connection.incoming' />">
                                    <fmt:message key="server.session.connection.incoming" />
                                </c:when>
                                <c:when test="${empty inSessions and not empty outSessions}">
                                    <img src="images/outgoing_32x16.gif" width="32" height="16" border="0" title="<fmt:message key='server.session.connection.outgoing' />" alt="<fmt:message key='server.session.connection.outgoing' />">
                                    <fmt:message key="server.session.connection.outgoing" />
                                </c:when>
                                <c:otherwise>
                                    <img src="images/both_32x16.gif" width="32" height="16" border="0" title="<fmt:message key='server.session.connection.both' />" alt="<fmt:message key='server.session.connection.both' />">
                                    <fmt:message key="server.session.connection.both" />
                                </c:otherwise>
                            </c:choose>
                        </td>
                    </tr>

                    <c:choose>
                        <c:when test="${not empty inSessions and not empty inSessions.toArray()[0].softwareVersion}">
                            <c:set var="softwareVersion" value="${inSessions.toArray()[0].softwareVersion}"/>
                        </c:when>
                        <c:when test="${not empty outSessions and not empty outSessions.toArray()[0].softwareVersion}">
                            <c:set var="softwareVersion" value="${outSessions.toArray()[0].softwareVersion}"/>
                        </c:when>
                        <c:otherwise>
                            <c:set var="softwareVersion" value=""/>
                        </c:otherwise>
                    </c:choose>

                    <!-- Show Software Version if there is any reported. -->
                    <c:if test="${not empty softwareVersion}">
                        <c:forEach items="${softwareVersion}" var="entry">
                            <tr>
                                <td class="c1" style="text-transform: capitalize">
                                    <c:out value="${entry.key}"/>
                                </td>
                                <td>
                                    <c:out value="${entry.value}"/>
                                </td>
                            </tr>
                        </c:forEach>
                    </c:if>
                </table>

                <!-- Show details of the incoming sessions for this host -->
                <c:if test="${not empty inSessions}">
                    <table cellpadding="3" cellspacing="1" border="0" width="100%">
                        <tr>
                            <th width="35%" colspan="2" nowrap><fmt:message key="server.session.details.incoming_session" /> <fmt:message key="server.session.details.streamid" /></th>
                            <c:if test="${clusteringEnabled}">
                                <th width="1%" nowrap><fmt:message key="server.session.details.node"/></th>
                            </c:if>
                            <th width="10%" nowrap><fmt:message key="server.session.details.authentication"/></th>
                            <th width="10%" nowrap><fmt:message key="server.session.details.cipher"/></th>
                            <th width="1%" nowrap><fmt:message key="server.session.label.creation" /></th>
                            <th width="1%" nowrap><fmt:message key="server.session.label.last_active" /></th>
                            <th width="1%" nowrap><fmt:message key="server.session.details.incoming_statistics" /></th>
                            <th width="1%" nowrap><fmt:message key="server.session.details.outgoing_statistics" /></th>
                        </tr>

                        <c:forEach items="${inSessions}" var="session">
                            <tr>
                                <td width="1%">
                                    <c:choose>
                                        <c:when test="${session.secure}">
                                            <img src="images/lock.gif" width="16" height="16" border="0" alt="A secure connection">
                                        </c:when>
                                        <c:otherwise>
                                            <img src="images/blank.gif" width="1" height="1" alt="Not a secure connection">
                                        </c:otherwise>
                                    </c:choose>
                                </td>
                                <td><c:out value="${session.streamID}"/></td>
                                <c:if test="${clusteringEnabled}">
                                    <td nowrap>
                                        <c:choose>
                                            <c:when test="${session['class'].simpleName eq 'LocalIncomingServerSession'}">
                                                <fmt:message key="server.session.details.local"/>
                                            </c:when>
                                            <c:otherwise>
                                                <fmt:message key="server.session.details.remote"/>
                                            </c:otherwise>
                                        </c:choose>
                                    </td>
                                </c:if>
                                <td nowrap>
                                    <c:choose>
                                        <c:when test="${session.usingServerDialback}">
                                            <fmt:message key="server.session.details.dialback"/>
                                        </c:when>
                                        <c:otherwise>
                                            <fmt:message key="server.session.details.tlsauth"/>
                                        </c:otherwise>
                                    </c:choose>
                                <td><c:out value="${session.cipherSuiteName}"/></td>
                                <td nowrap><fmt:formatDate type="both" value="${session.creationDate}"/></td>
                                <td nowrap><fmt:formatDate type="both" value="${session.lastActiveDate}"/></td>
                                <td align="center" nowrap><fmt:formatNumber type="number" value="${session.numClientPackets}"/></td>
                                <td align="center" nowrap><fmt:formatNumber type="number" value="${session.numServerPackets}"/></td>
                            </tr>
                        </c:forEach>
                    </table>
                </c:if>

                <!-- Show details of the outgoing sessions for this host -->
                <c:if test="${not empty outSessions}">
                    <table cellpadding="3" cellspacing="1" border="0" width="100%">
                        <tr>
                            <th width="35%" colspan="2" nowrap><fmt:message key="server.session.details.outgoing_session" /> <fmt:message key="server.session.details.streamid" /></th>
                            <c:if test="${clusteringEnabled}">
                                <th width="1%" nowrap><fmt:message key="server.session.details.node"/></th>
                            </c:if>
                            <th width="10%" nowrap><fmt:message key="server.session.details.authentication"/></th>
                            <th width="10%" nowrap><fmt:message key="server.session.details.cipher"/></th>
                            <th width="1%" nowrap><fmt:message key="server.session.label.creation" /></th>
                            <th width="1%" nowrap><fmt:message key="server.session.label.last_active" /></th>
                            <th width="1%" nowrap><fmt:message key="server.session.details.incoming_statistics" /></th>
                            <th width="1%" nowrap><fmt:message key="server.session.details.outgoing_statistics" /></th>
                        </tr>

                        <c:forEach items="${outSessions}" var="session">
                            <tr>
                                <td width="1%">
                                    <c:choose>
                                        <c:when test="${session.secure}">
                                            <img src="images/lock.gif" width="16" height="16" border="0" alt="A secure connection">
                                        </c:when>
                                        <c:otherwise>
                                            <img src="images/blank.gif" width="1" height="1" alt="Not a secure connection">
                                        </c:otherwise>
                                    </c:choose>
                                </td>
                                <td><c:out value="${session.streamID}"/></td>
                                <c:if test="${clusteringEnabled}">
                                    <td nowrap>
                                        <c:choose>
                                            <c:when test="${session['class'].simpleName eq 'LocalOutgoingServerSession'}">
                                                <fmt:message key="server.session.details.local"/>
                                            </c:when>
                                            <c:otherwise>
                                                <fmt:message key="server.session.details.remote"/>
                                            </c:otherwise>
                                        </c:choose>
                                    </td>
                                </c:if>
                                <td nowrap>
                                    <c:choose>
                                    <c:when test="${session.usingServerDialback}">
                                        <fmt:message key="server.session.details.dialback"/>
                                    </c:when>
                                    <c:otherwise>
                                        <fmt:message key="server.session.details.tlsauth"/>
                                    </c:otherwise>
                                    </c:choose>
                                <td><c:out value="${session.cipherSuiteName}"/></td>
                                <td nowrap><fmt:formatDate type="both" value="${session.creationDate}"/></td>
                                <td nowrap><fmt:formatDate type="both" value="${session.lastActiveDate}"/></td>
                                <td align="center" nowrap><fmt:formatNumber type="number" value="${session.numClientPackets}"/></td>
                                <td align="center" nowrap><fmt:formatNumber type="number" value="${session.numServerPackets}"/></td>
                            </tr>
                        </c:forEach>
                     </table>
                </c:if>

            </div>

            <br/>

        </c:forEach>

        <form action="server-session-details.jsp">
            <center>
                <input type="submit" name="back" value="<fmt:message key="session.details.back_button" />">
            </center>
        </form>

    </body>
</html>
