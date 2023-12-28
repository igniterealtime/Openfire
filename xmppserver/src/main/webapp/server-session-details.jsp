<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  -
  - Copyright (C) 2004-2008 Jive Software, 2017-2023 Ignite Realtime Foundation. All rights reserved.
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
                 org.jivesoftware.util.ParamUtils"
    errorPage="error.jsp"
%>
<%@ page import="org.jivesoftware.openfire.cluster.ClusterManager" %>
<%@ page import="java.util.stream.Collectors" %>
<%@ page import="java.util.*" %>
<%@ page import="java.net.InetAddress" %>
<%@ page import="org.jivesoftware.util.CookieUtils" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="org.jivesoftware.openfire.session.Session" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<% // Get parameters
    String domainname = ParamUtils.getParameter(request, "hostname");
    boolean close = request.getParameter("close") != null;

    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (close) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            close = false;
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    // Get the session manager
    SessionManager sessionManager = webManager.getSessionManager();

    // Close all connections related to the specified domain name
    if (close) {
        try {
            final List<IncomingServerSession> incomingServerSessions = sessionManager.getIncomingServerSessions(domainname);
            for (Session incomingServerSession : incomingServerSessions) {
                incomingServerSession.close();
            }

            Collection<OutgoingServerSession> outgoingServerSessions = sessionManager.getOutgoingServerSessions(domainname);
            for (OutgoingServerSession outgoingServerSession : outgoingServerSessions) {
                if (outgoingServerSession != null) {
                    outgoingServerSession.close();
                }
            }
            // Log the event
            webManager.logEvent("closed server sessions for "+ domainname, "Closed " + incomingServerSessions.size() + " incoming and " + outgoingServerSessions + " outgoing session(s).");
            // wait one second
            Thread.sleep(250L);
        }
        catch (Exception ignored) {
            // Session might have disappeared on its own
        }
        // redirect back to this page
        response.sendRedirect("server-session-summary.jsp?close=success");
        return;
    }

    // Handle a "go back" click:
    if (request.getParameter("back") != null) {
        response.sendRedirect("server-session-summary.jsp");
        return;
    }

    // Get the session & address objects
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
            <table>
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
                                <img src="images/incoming_32x16.gif" width="32" height="16" title="<fmt:message key='server.session.connection.incoming' />" alt="<fmt:message key='server.session.connection.incoming' />">
                                <fmt:message key="server.session.connection.incoming" />
                            </c:when>
                            <c:when test="${empty inByHost and not empty outByHost}">
                                <img src="images/outgoing_32x16.gif" width="32" height="16" title="<fmt:message key='server.session.connection.outgoing' />" alt="<fmt:message key='server.session.connection.outgoing' />">
                                <fmt:message key="server.session.connection.outgoing" />
                            </c:when>
                            <c:otherwise>
                                <img src="images/both_32x16.gif" width="32" height="16" title="<fmt:message key='server.session.connection.both' />" alt="<fmt:message key='server.session.connection.both' />">
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
                <table>
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
                                    <img src="images/incoming_32x16.gif" width="32" height="16" title="<fmt:message key='server.session.connection.incoming' />" alt="<fmt:message key='server.session.connection.incoming' />">
                                    <fmt:message key="server.session.connection.incoming" />
                                </c:when>
                                <c:when test="${empty inSessions and not empty outSessions}">
                                    <img src="images/outgoing_32x16.gif" width="32" height="16" title="<fmt:message key='server.session.connection.outgoing' />" alt="<fmt:message key='server.session.connection.outgoing' />">
                                    <fmt:message key="server.session.connection.outgoing" />
                                </c:when>
                                <c:otherwise>
                                    <img src="images/both_32x16.gif" width="32" height="16" title="<fmt:message key='server.session.connection.both' />" alt="<fmt:message key='server.session.connection.both' />">
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
                    <table style="width: 100%">
                        <tr>
                            <th style="width: 20%;" colspan="2"><fmt:message key="server.session.details.incoming_session" /> <fmt:message key="server.session.details.streamid" /></th>
                            <c:if test="${clusteringEnabled}">
                                <th style="width: 1%; "><fmt:message key="server.session.details.node"/></th>
                            </c:if>
                            <th style="width: 10%;"><fmt:message key="server.session.details.authentication"/></th>
                            <th style="width: 10%;"><fmt:message key="server.session.details.tls_version"/></th>
                            <th style="width: 10%;"><fmt:message key="server.session.details.cipher"/></th>
                            <th style="width: 10%;"><fmt:message key="server.session.label.creation" /></th>
                            <th style="width: 10%;"><fmt:message key="server.session.label.last_active" /></th>
                            <th style="width: 1%;"><fmt:message key="server.session.details.incoming_statistics" /></th>
                            <th style="width: 1%;"><fmt:message key="server.session.details.outgoing_statistics" /></th>
                        </tr>

                        <c:forEach items="${inSessions}" var="session">
                            <tr>
                                <td style="width: 1%">
                                    <c:choose>
                                        <c:when test="${session.secure}">
                                            <img src="images/lock.gif" alt="A secure connection">
                                        </c:when>
                                        <c:otherwise>
                                            <img src="images/blank.gif" width="1" height="1" alt="Not a secure connection">
                                        </c:otherwise>
                                    </c:choose>
                                </td>
                                <td><c:out value="${session.streamID}"/></td>
                                <c:if test="${clusteringEnabled}">
                                    <td >
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
                                <td >
                                    <c:choose>
                                        <c:when test="${session.isUsingServerDialback()}">
                                            <fmt:message key="server.session.details.dialback"/>
                                        </c:when>
                                        <c:when test="${session.isUsingSaslExternal()}">
                                            <fmt:message key="server.session.details.tlsauth"/>
                                        </c:when>
                                        <c:otherwise>
                                            <fmt:message key="server.session.details.unknown"/>
                                        </c:otherwise>
                                    </c:choose>
                                <td><c:out value="${session.TLSProtocolName}"/></td>
                                <td><c:out value="${session.cipherSuiteName}"/></td>
                                <td ><fmt:formatDate type="both" value="${session.creationDate}"/></td>
                                <td ><fmt:formatDate type="both" value="${session.lastActiveDate}"/></td>
                                <td style="text-align: center" ><fmt:formatNumber type="number" value="${session.numClientPackets}"/></td>
                                <td style="text-align: center" ><fmt:formatNumber type="number" value="${session.numServerPackets}"/></td>
                            </tr>
                        </c:forEach>
                    </table>
                </c:if>

                <!-- Show details of the outgoing sessions for this host -->
                <c:if test="${not empty outSessions}">
                    <table style="width: 100%">
                        <tr>
                            <th style="width: 20%;" colspan="2"><fmt:message key="server.session.details.outgoing_session" /> <fmt:message key="server.session.details.streamid" /></th>
                            <c:if test="${clusteringEnabled}">
                                <th style="width: 1%; "><fmt:message key="server.session.details.node"/></th>
                            </c:if>
                            <th style="width: 10%; "><fmt:message key="server.session.details.authentication"/></th>
                            <th style="width: 10%; "><fmt:message key="server.session.details.tls_version"/></th>
                            <th style="width: 10%; "><fmt:message key="server.session.details.cipher"/></th>
                            <th style="width: 10%; "><fmt:message key="server.session.label.creation" /></th>
                            <th style="width: 10%; "><fmt:message key="server.session.label.last_active" /></th>
                            <th style="width: 1%; "><fmt:message key="server.session.details.incoming_statistics" /></th>
                            <th style="width: 1%; "><fmt:message key="server.session.details.outgoing_statistics" /></th>
                        </tr>

                        <c:forEach items="${outSessions}" var="session">
                            <tr>
                                <td style="width: 1%">
                                    <c:choose>
                                        <c:when test="${session.secure}">
                                            <img src="images/lock.gif" alt="A secure connection">
                                        </c:when>
                                        <c:otherwise>
                                            <img src="images/blank.gif" width="1" height="1" alt="Not a secure connection">
                                        </c:otherwise>
                                    </c:choose>
                                </td>
                                <td><c:out value="${session.streamID}"/></td>
                                <c:if test="${clusteringEnabled}">
                                    <td >
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
                                <td >
                                    <c:choose>
                                        <c:when test="${session.isUsingServerDialback()}">
                                            <fmt:message key="server.session.details.dialback"/>
                                        </c:when>
                                        <c:when test="${session.isUsingSaslExternal()}">
                                            <fmt:message key="server.session.details.tlsauth"/>
                                        </c:when>
                                        <c:otherwise>
                                            <fmt:message key="server.session.details.unknown"/>
                                        </c:otherwise>
                                    </c:choose>
                                <td><c:out value="${session.TLSProtocolName}"/></td>
                                <td><c:out value="${session.cipherSuiteName}"/></td>
                                <td ><fmt:formatDate type="both" value="${session.creationDate}"/></td>
                                <td ><fmt:formatDate type="both" value="${session.lastActiveDate}"/></td>
                                <td style="text-align: center" ><fmt:formatNumber type="number" value="${session.numClientPackets}"/></td>
                                <td style="text-align: center" ><fmt:formatNumber type="number" value="${session.numServerPackets}"/></td>
                            </tr>
                        </c:forEach>
                     </table>
                </c:if>

            </div>

            <br/>

        </c:forEach>

        <form action="server-session-details.jsp">
            <input type="hidden" name="hostname" value="<%= StringUtils.escapeForXML(domainname) %>">
            <input type="hidden" name="csrf" value="<%=csrfParam%>"/>
            <div style="text-align: center;">
                <input type="submit" name="back" value="<fmt:message key="session.details.back_button" />">
                <button name="close" style="padding-left: 24px;background-repeat: no-repeat;background-image: url(images/delete-16x16.gif);background-position-y: center; background-color: lightyellow;border-width: 1px;" onclick="return confirm('<fmt:message key="session.row.confirm_close" />');"><fmt:message key="session.row.click_kill_session" /></button>
            </div>
        </form>

    </body>
</html>
