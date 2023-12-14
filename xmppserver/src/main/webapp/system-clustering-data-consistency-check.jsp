<%@ page contentType="text/html; charset=UTF-8" %>

<%--
  -
  - Copyright (C) 2021-2022 Ignite Realtime Foundation. All rights reserved.
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

<%@ page import="org.jivesoftware.openfire.spi.RoutingTableImpl" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<!-- Define Administration Bean -->
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"/>

<%
    webManager.init(pageContext);

    // Calculations for RoutingTableImpl
    pageContext.setAttribute("clusteringStateConsistencyReportForServerRoutes", ((RoutingTableImpl) XMPPServer.getInstance().getRoutingTable()).clusteringStateConsistencyReportForServerRoutes());
    pageContext.setAttribute("clusteringStateConsistencyReportForComponentRoutes", ((RoutingTableImpl) XMPPServer.getInstance().getRoutingTable()).clusteringStateConsistencyReportForComponentRoutes());
    pageContext.setAttribute("clusteringStateConsistencyReportForClientRoutes", ((RoutingTableImpl) XMPPServer.getInstance().getRoutingTable()).clusteringStateConsistencyReportForClientRoutes());
    pageContext.setAttribute("clusteringStateConsistencyReportForIncomingServerSessions", XMPPServer.getInstance().getSessionManager().clusteringStateConsistencyReportForIncomingServerSessionInfos());
    pageContext.setAttribute("clusteringStateConsistencyReportForSessionInfos", XMPPServer.getInstance().getSessionManager().clusteringStateConsistencyReportForSessionInfos());
    pageContext.setAttribute("clusteringStateConsistencyReportForUsersSession", ((RoutingTableImpl) XMPPServer.getInstance().getRoutingTable()).clusteringStateConsistencyReportForUsersSessions());
    pageContext.setAttribute("clusteringStateConsistencyReportForMucRoomsAndOccupant", XMPPServer.getInstance().getMultiUserChatManager().clusteringStateConsistencyReportForMucRoomsAndOccupant());

    pageContext.setAttribute("newLineChar", "\n");
%>

<html>
<head>
    <title>Clustering - data consistency</title>
    <meta name="pageID" content="system-clustering-data-consistency-check"/>
</head>
<body>

    <p>When Openfire is running in a cluster, most of its caches are backed by distributed data structures to share state across the different cluster nodes. There are several challenges in working with these distributed data structures, in particular around cluster events that relate to servers joining or leaving the cluster.</p>

    <p>As described below, for each cache, there can be up to two supporting data structures on each node. To an extend, this means that there is data duplication. It is important that all three structures (the clustered cache, as well as the two supporting structures on each node) are kept in a consistent state. This page shows diagnostics that help identify consistency problems. Note that this page only verifies data that is maintained on the <em>local cluster node</em>. This page will have different results on each server in the cluster (you should check them all).</p>

    <h2>Restoring 'local' data after cache switch-over</h2>

    <p>As a result of how clustering functionality is built in to Openfire, most caches in Openfire will switch between a default implementation to one that is backed by a distributed data structure. These switches happen when a server joins or leaves the cluster (at boot/shutdown time, or when the clustering functionality is enabled/disabled by configuration, or as a way to resolve a 'split-brain' scenario). After a switch, any data that was previously available in the cache is lost, from the perspective of the local cluster node: when switching <em>to</em> a cluster, the cache will at that point only contain data that was added by all other servers in the cluster, when switching <em>from</em> a cluster (back to being non-clustered, stand-alone XMPP domain), the cache will be completely empty. After each cache switch, the local node will therefore need to repopulate the cache with entries that it previously added to the cache.</p>

    <p>As an aside: when a clustered node looses network connectivity, it is still considered to be in a cluster. It will see all other nodes leave that cluster (and obviously, each of those nodes will see the disconnected node leave). In this scenario, a cache switch-over will not occur. When it rejoins, a split-brain scenario will have been established (as both 'clusters' will each have a 'senior' node). As a result of the existing split-brain resolution, caches <em>will</em> switch-over twice. This is a result of one of the cluster partitions being briefly removed from the cluster, after which it rejoins.</p>

    <h2>Knowing what data is no longer available</h2>

    <p>When a server in the cluster (a 'cluster node') unexpectedly leaves the cluster (eg: a crash, or network interruption), the data that's available in these caches on both the split off cluster as well as the remaining cluster nodes need to be cleaned up. There might, for example, be data in the cache that is no longer 'valid', as it is tightly related to a now unavailable cluster node. On top of that, because of how the clustered data structure is implemented, there's no guarantee that the caches on each individual node contain a consistent data set after an unscheduled cluster interruption.</p>

    <p>To guard against data inconsistency as a result of cluster outages, as well as for the local node to know what data was 'lost' (which might need to be operate on, for example to let local users know that certain other users are now no longer reachable), each cluster node maintains a partial copy for each cache entry, that contains a minimal amount of data.</p>

    <admin:contentBox title="Routing Table Caches">
        <h4>Servers Cache</h4>
        <p>The cache describes what <em>outgoing</em> S2S connections (identified by DomainPair) are physically connected to which cluster node(s). Like client connections, an (outgoing) S2S connection is uniquely established on one cluster node (multiple concurrent outgoing connections cannot exist).</p>
        <c:forEach items="${clusteringStateConsistencyReportForServerRoutes.asMap()}" var="messageGroup">
            <ul>
                <c:forEach items="${messageGroup.value}" var="line">
                    <li>
                        <c:choose>
                            <c:when test="${messageGroup.key eq 'info'}"><img src="images/info-16x16.gif" alt="informational"></c:when>
                            <c:when test="${messageGroup.key eq 'pass'}"><img src="images/check.gif" alt="consistent"></c:when>
                            <c:when test="${messageGroup.key eq 'fail'}"><img src="images/x.gif" alt="inconsistency"></c:when>
                        </c:choose>
                        <c:choose>
                            <c:when test='${fn:contains(line, newLineChar)}'>
                                <c:forTokens items="${line}" delims="${newLineChar}" var="descr" begin="0" end="0">
                                    <c:out value="${descr}"/>
                                </c:forTokens>
                                <ul>
                                    <c:forTokens items="${line}" delims="${newLineChar}" var="item" begin="1">
                                        <li><c:out value="${item}"/></li>
                                    </c:forTokens>
                                </ul>
                            </c:when>
                            <c:otherwise>
                                <c:out value="${line}"/>
                            </c:otherwise>
                        </c:choose>
                    </li>
                </c:forEach>
            </ul>
        </c:forEach>

        <h4>Component Cache</h4>
        <p>The cache describes what component are physically connected to which cluster node(s), which includes both externally connected components as well as internal components. UnLike server and client connections, a component connection is <em>not</em> uniquely established on one cluster node (each cluster node can have a local route to the same component address).</p>
        <c:forEach items="${clusteringStateConsistencyReportForComponentRoutes.asMap()}" var="messageGroup">
            <ul>
                <c:forEach items="${messageGroup.value}" var="line">
                    <li>
                        <c:choose>
                            <c:when test="${messageGroup.key eq 'info'}"><img src="images/info-16x16.gif" alt="informational"></c:when>
                            <c:when test="${messageGroup.key eq 'pass'}"><img src="images/check.gif" alt="consistent"></c:when>
                            <c:when test="${messageGroup.key eq 'fail'}"><img src="images/x.gif" alt="inconsistency"></c:when>
                        </c:choose>
                        <c:choose>
                            <c:when test='${fn:contains(line, newLineChar)}'>
                                <c:forTokens items="${line}" delims="${newLineChar}" var="descr" begin="0" end="0">
                                    <c:out value="${descr}"/>
                                </c:forTokens>
                                <ul>
                                    <c:forTokens items="${line}" delims="${newLineChar}" var="item" begin="1">
                                        <li><c:out value="${item}"/></li>
                                    </c:forTokens>
                                </ul>
                            </c:when>
                            <c:otherwise>
                                <c:out value="${line}"/>
                            </c:otherwise>
                        </c:choose>
                    </li>
                </c:forEach>
            </ul>
        </c:forEach>

        <h4>Routing Users Cache & Routing AnonymousUsers Cache</h4>
        <p>The caches describes what C2S connections (identified by full JID) are physically connected to what cluster node. Unlike server-to-server connection, a C2S connection is uniquely established to one cluster node (multiple concurrent connections <em>for the same full JID</em> cannot exist).</p>

        <c:forEach items="${clusteringStateConsistencyReportForClientRoutes.asMap()}" var="messageGroup">
            <ul>
                <c:forEach items="${messageGroup.value}" var="line">
                    <li>
                        <c:choose>
                            <c:when test="${messageGroup.key eq 'info'}"><img src="images/info-16x16.gif" alt="informational"></c:when>
                            <c:when test="${messageGroup.key eq 'pass'}"><img src="images/check.gif" alt="consistent"></c:when>
                            <c:when test="${messageGroup.key eq 'fail'}"><img src="images/x.gif" alt="inconsistency"></c:when>
                        </c:choose>
                        <c:choose>
                            <c:when test='${fn:contains(line, newLineChar)}'>
                                <c:forTokens items="${line}" delims="${newLineChar}" var="descr" begin="0" end="0">
                                    <c:out value="${descr}"/>
                                </c:forTokens>
                                <ul>
                                    <c:forTokens items="${line}" delims="${newLineChar}" var="item" begin="1">
                                        <li><c:out value="${item}"/></li>
                                    </c:forTokens>
                                </ul>
                            </c:when>
                            <c:otherwise>
                                <c:out value="${line}"/>
                            </c:otherwise>
                        </c:choose>
                    </li>
                </c:forEach>
            </ul>
        </c:forEach>

        <h4>Incoming Server Sessions Cache</h4>

        <c:forEach items="${clusteringStateConsistencyReportForIncomingServerSessions.asMap()}" var="messageGroup">
            <ul>
                <c:forEach items="${messageGroup.value}" var="line">
                    <li>
                        <c:choose>
                            <c:when test="${messageGroup.key eq 'info'}"><img src="images/info-16x16.gif" alt="informational"></c:when>
                            <c:when test="${messageGroup.key eq 'pass'}"><img src="images/check.gif" alt="consistent"></c:when>
                            <c:when test="${messageGroup.key eq 'fail'}"><img src="images/x.gif" alt="inconsistency"></c:when>
                        </c:choose>
                        <c:choose>
                            <c:when test='${fn:contains(line, newLineChar)}'>
                                <c:forTokens items="${line}" delims="${newLineChar}" var="descr" begin="0" end="0">
                                    <c:out value="${descr}"/>
                                </c:forTokens>
                                <ul>
                                    <c:forTokens items="${line}" delims="${newLineChar}" var="item" begin="1">
                                        <li><c:out value="${item}"/></li>
                                    </c:forTokens>
                                </ul>
                            </c:when>
                            <c:otherwise>
                                <c:out value="${line}"/>
                            </c:otherwise>
                        </c:choose>
                    </li>
                </c:forEach>
            </ul>
        </c:forEach>

        <h4>Tracked sessions Cache</h4>

        <c:forEach items="${clusteringStateConsistencyReportForSessionInfos.asMap()}" var="messageGroup">
            <ul>
                <c:forEach items="${messageGroup.value}" var="line">
                    <li>
                        <c:choose>
                            <c:when test="${messageGroup.key eq 'info'}"><img src="images/info-16x16.gif" alt="informational"></c:when>
                            <c:when test="${messageGroup.key eq 'pass'}"><img src="images/check.gif" alt="consistent"></c:when>
                            <c:when test="${messageGroup.key eq 'fail'}"><img src="images/x.gif" alt="inconsistency"></c:when>
                        </c:choose>
                        <c:choose>
                            <c:when test='${fn:contains(line, newLineChar)}'>
                                <c:forTokens items="${line}" delims="${newLineChar}" var="descr" begin="0" end="0">
                                    <c:out value="${descr}"/>
                                </c:forTokens>
                                <ul>
                                    <c:forTokens items="${line}" delims="${newLineChar}" var="item" begin="1">
                                        <li><c:out value="${item}"/></li>
                                    </c:forTokens>
                                </ul>
                            </c:when>
                            <c:otherwise>
                                <c:out value="${line}"/>
                            </c:otherwise>
                        </c:choose>
                    </li>
                </c:forEach>
            </ul>
        </c:forEach>

        <h4>Users sessions Cache</h4>

        <c:forEach items="${clusteringStateConsistencyReportForUsersSession.asMap()}" var="messageGroup">
            <ul>
                <c:forEach items="${messageGroup.value}" var="line">
                    <li>
                        <c:choose>
                            <c:when test="${messageGroup.key eq 'info'}"><img src="images/info-16x16.gif" alt="informational"></c:when>
                            <c:when test="${messageGroup.key eq 'pass'}"><img src="images/check.gif" alt="consistent"></c:when>
                            <c:when test="${messageGroup.key eq 'fail'}"><img src="images/x.gif" alt="inconsistency"></c:when>
                        </c:choose>
                        <c:choose>
                            <c:when test='${fn:contains(line, newLineChar)}'>
                                <c:forTokens items="${line}" delims="${newLineChar}" var="descr" begin="0" end="0">
                                    <c:out value="${descr}"/>
                                </c:forTokens>
                                <ul>
                                    <c:forTokens items="${line}" delims="${newLineChar}" var="item" begin="1">
                                        <li><c:out value="${item}"/></li>
                                    </c:forTokens>
                                </ul>
                            </c:when>
                            <c:otherwise>
                                <c:out value="${line}"/>
                            </c:otherwise>
                        </c:choose>
                    </li>
                </c:forEach>
            </ul>
        </c:forEach>

        <c:forEach items="${clusteringStateConsistencyReportForMucRoomsAndOccupant}" var="mucServiceReport">

            <h4>MUC and occupants Cache</h4>
            <c:if test="${mucServiceReport.containsKey(\"intro\")}">
                <ul>
                    <c:forEach items="${mucServiceReport.asMap().get(\"intro\")}" var="introItem">
                        <li>
                            <em><c:out value="${introItem}"/></em>
                        </li>
                    </c:forEach>
                </ul>
            </c:if>

            <c:forEach items="${mucServiceReport.asMap()}" var="messageGroup">
                <c:if test="${messageGroup.key ne 'intro'}">
                    <ul>
                        <c:forEach items="${messageGroup.value}" var="line">
                            <li>
                                <c:choose>
                                    <c:when test="${messageGroup.key eq 'info'}"><img src="images/info-16x16.gif" alt="informational"></c:when>
                                    <c:when test="${messageGroup.key eq 'pass'}"><img src="images/check.gif" alt="consistent"></c:when>
                                    <c:when test="${messageGroup.key eq 'fail'}"><img src="images/x.gif" alt="inconsistency"></c:when>
                                </c:choose>
                                <c:choose>
                                    <c:when test='${fn:contains(line, newLineChar)}'>
                                        <c:forTokens items="${line}" delims="${newLineChar}" var="descr" begin="0" end="0">
                                            <c:out value="${descr}"/>
                                        </c:forTokens>
                                        <ul>
                                            <c:forTokens items="${line}" delims="${newLineChar}" var="item" begin="1">
                                                <li><c:out value="${item}"/></li>
                                            </c:forTokens>
                                        </ul>
                                    </c:when>
                                    <c:otherwise>
                                        <c:out value="${line}"/>
                                    </c:otherwise>
                                </c:choose>
                            </li>
                        </c:forEach>
                    </ul>
                </c:if>
            </c:forEach>
        </c:forEach>
    </admin:contentBox>
</body>
</html>
