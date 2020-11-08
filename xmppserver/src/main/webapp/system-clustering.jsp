<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  -
  - Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ page import="org.jivesoftware.database.DbConnectionManager" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.cluster.ClusterManager" errorPage="error.jsp" %>
<%@ page import="org.jivesoftware.openfire.cluster.ClusterNodeInfo" %>
<%@ page import="org.jivesoftware.openfire.cluster.GetBasicStatistics" %>
<%@ page import="org.jivesoftware.util.Base64" %>
<%@ page import="org.jivesoftware.util.CookieUtils" %>
<%@ page import="org.jivesoftware.util.JiveGlobals" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="org.jivesoftware.util.cache.CacheFactory" %>
<%@ page import="org.slf4j.Logger" %>
<%@ page import="org.slf4j.LoggerFactory" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="java.text.DecimalFormat" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.jivesoftware.openfire.cluster.ClusterEventListener" %>
<%@ page import="java.util.concurrent.Semaphore" %>
<%@ page import="java.util.concurrent.TimeUnit" %>
<%@ page import="org.jivesoftware.openfire.cluster.GetClusteredVersions" %>
<%@ page import="org.jivesoftware.openfire.cluster.NodeID" %>
<%@ page import="com.google.common.collect.Table" %>
<%@ page import="com.google.common.collect.HashBasedTable" %>
<%@ page import="java.util.Set" %>
<%@ page import="java.util.TreeSet" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<html>
<head>
<title><fmt:message key="system.clustering.title"/></title>
<meta name="pageID" content="system-clustering"/>
<style type="text/css">
.jive-contentBox .local {
    background-color: #ffc;
    }
</style>
</head>
<body>

<% // Get parameters
    boolean update = request.getParameter("update") != null;
    boolean clusteringEnabled = ParamUtils.getBooleanParameter(request, "clusteringEnabled");
    boolean updateSuccess = false;
    final Logger LOGGER = LoggerFactory.getLogger("system-clustering.jsp");

    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (update) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            update = false;
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);
    if (update) {
        if (!clusteringEnabled) {
            LOGGER.info("Disabling clustering");
            // Log the event
            webManager.logEvent("disabled clustering", null);
            final Semaphore leftClusterSemaphore = new Semaphore(0);
            final ClusterEventListener listener = new ClusterEventListener() {
                @Override
                public void joinedCluster() {
                }

                @Override
                public void joinedCluster(byte[] nodeID) {
                }

                @Override
                public void leftCluster() {
                    leftClusterSemaphore.release();
                }

                @Override
                public void leftCluster(byte[] nodeID) {
                }

                @Override
                public void markedAsSeniorClusterMember() {
                }
            };
            ClusterManager.addListener(listener);
            ClusterManager.setClusteringEnabled(false);
            try {
                updateSuccess = leftClusterSemaphore.tryAcquire(30, TimeUnit.SECONDS);
            } finally {
                ClusterManager.removeListener(listener);
            }
            LOGGER.info("Clustering disabled");
        } else {
            if (ClusterManager.isClusteringAvailable()) {
                LOGGER.info("Enabling clustering");
                // Log the event
                webManager.logEvent("enabled clustering", null);
                final Semaphore joinedClusterSemaphore = new Semaphore(0);
                final ClusterEventListener listener = new ClusterEventListener() {
                    @Override
                    public void joinedCluster() {
                        joinedClusterSemaphore.release();
                    }

                    @Override
                    public void joinedCluster(byte[] nodeID) {
                    }

                    @Override
                    public void leftCluster() {
                    }

                    @Override
                    public void leftCluster(byte[] nodeID) {
                    }

                    @Override
                    public void markedAsSeniorClusterMember() {
                    }
                };
                ClusterManager.addListener(listener);
                ClusterManager.setClusteringEnabled(true);
                try {
                    updateSuccess = joinedClusterSemaphore.tryAcquire(30, TimeUnit.SECONDS);
                } finally {
                    ClusterManager.removeListener(listener);
                }
                LOGGER.info("Clustering enabled");
            } else {
                LOGGER.error("Failed to enable clustering. Clustering is not available.");
            }
        }
    }

    boolean usingEmbeddedDB = DbConnectionManager.isEmbeddedDB();
    boolean clusteringAvailable = !usingEmbeddedDB && ClusterManager.isClusteringAvailable();
    int maxClusterNodes = ClusterManager.getMaxClusterNodes();
    clusteringEnabled = ClusterManager.isClusteringStarted() || ClusterManager.isClusteringStarting();

    final List<ClusterNodeInfo> clusterNodesInfo = new ArrayList<>(ClusterManager.getNodesInfo());
    // Sort them so they are always consistent in order
    clusterNodesInfo.sort((o1, o2) -> o1.getHostName().compareTo(o2.getHostName()));
    // Get some basic statistics from the cluster nodes
    // TODO Set a timeout so the page can load fast even if a node is taking too long to answer
    Collection<Map<String, Object>> statistics =
            CacheFactory.doSynchronousClusterTask(new GetBasicStatistics(), true);
    // Calculate percentages
    int clients = 0;
    int incoming = 0;
    int outgoing = 0;
    for (Map<String, Object> statsMap : statistics) {
        if (statsMap == null) {
            continue;
        }
        clients += (Integer) statsMap.get(GetBasicStatistics.CLIENT);
        incoming += (Integer) statsMap.get(GetBasicStatistics.INCOMING);
        outgoing += (Integer) statsMap.get(GetBasicStatistics.OUTGOING);
    }
    for (Map<String, Object> statsMap : statistics) {
        if (statsMap == null) {
            continue;
        }
        int current = (Integer) statsMap.get(GetBasicStatistics.CLIENT);
        int percentage = clients == 0 ? 0 : current * 100 / clients;
        statsMap.put(GetBasicStatistics.CLIENT, current + " (" + Math.round(percentage) + "%)");
        current = (Integer) statsMap.get(GetBasicStatistics.INCOMING);
        percentage = incoming == 0 ? 0 : current * 100 / incoming;
        statsMap.put(GetBasicStatistics.INCOMING, current + " (" + Math.round(percentage) + "%)");
        current = (Integer) statsMap.get(GetBasicStatistics.OUTGOING);
        percentage = outgoing == 0 ? 0 : current * 100 / outgoing;
        statsMap.put(GetBasicStatistics.OUTGOING, current + " (" + Math.round(percentage) + "%)");
    }
    // Note; if any one node in the cluster does not have the GetClusterVersions task, running
    // CacheFactory.doSynchronousClusterTask() on all nodes will return an empty collection. For
    // that reason, run the task on each node individually.
    final Table<String, NodeID, String> pluginVersions = HashBasedTable.create();
    final Set<String> plugins = new TreeSet<>();
    clusterNodesInfo.forEach(clusterNodeInfo -> {
        final NodeID nodeID = clusterNodeInfo.getNodeID();
        final GetClusteredVersions clusteredVersions = CacheFactory.doSynchronousClusterTask(
            new GetClusteredVersions(), nodeID.toByteArray());
        if (clusteredVersions != null) {
            pluginVersions.put("Openfire", nodeID, clusteredVersions.getOpenfireVersion());
            clusteredVersions.getPluginVersions().forEach((pluginName, pluginVersion) -> {
                plugins.add(pluginName);
                pluginVersions.put(pluginName, nodeID, pluginVersion);
            });
        }
    });
    pageContext.setAttribute("localNodeID", XMPPServer.getInstance().getNodeID());
    pageContext.setAttribute("pluginVersions", pluginVersions);
    pageContext.setAttribute("plugins", plugins);
    pageContext.setAttribute("clusteringStarted", CacheFactory.isClusteringStarted());
    pageContext.setAttribute("clusterNodesInfo", clusterNodesInfo);
%>

<p>
<fmt:message key="system.clustering.info"/>
</p>

<%  if (update) {
        if (updateSuccess) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <% if (ClusterManager.isClusteringStarted()) { %>
            <fmt:message key="system.clustering.enabled" />
        <% } else { %>
            <fmt:message key="system.clustering.disabled" />
        <%
            }
        %>
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } else { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
            <td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""/></td>
            <td class="jive-icon-label">
                <fmt:message key="system.clustering.failed-start" />
            </td>
        </tr>
    </tbody>
    </table>
    </div>
    <br>
<%  }
} else if (!clusteringAvailable) {
%>
    <div class="warning">
    <table cellpadding="0" cellspacing="0" border="0" >
    <tbody>
        <tr>
            <td class="jive-icon-label">
            <b><fmt:message key="system.clustering.not-available" /></b><br/><br/>
            </td>
        </tr>
        <tr>
        <td valign="top" align="left" colspan="2">
            <% if (usingEmbeddedDB) { %>
                <span><fmt:message key="system.clustering.using-embedded-db"/></span>
            <% } else if (maxClusterNodes == 0) { %>
                <span><fmt:message key="system.clustering.not-installed"/></span>
            <% } else { %>
                <span><fmt:message key="system.clustering.not-valid-license"/></span>
            <% } %>
        </td>
        </tr>
    </tbody>
    </table>
    </div>
    <br>
<% } %> 

<!-- BEGIN 'Clustering Enabled' -->
<form action="system-clustering.jsp" method="post">
        <input type="hidden" name="csrf" value="${csrf}">
    <div class="jive-contentBoxHeader">
        <fmt:message key="system.clustering.enabled.legend" />
    </div>
    <div class="jive-contentBox">
        <table cellpadding="3" cellspacing="0" border="0">
        <tbody>
            <tr>
                <td width="1%" valign="top" nowrap>
                    <input type="radio" name="clusteringEnabled" value="false" id="rb01"
                     <%= (!clusteringEnabled ? "checked" : "") %> <%= clusteringAvailable ? "" : "disabled" %>>
                </td>
                <td width="99%">
                    <label for="rb01">
                    <b><fmt:message key="system.clustering.label_disable" /></b> - <fmt:message key="system.clustering.label_disable_info" />
                    </label>
                </td>
            </tr>
            <tr>
                <td width="1%" valign="top" nowrap>
                    <input type="radio" name="clusteringEnabled" value="true" id="rb02"
                     <%= (clusteringEnabled ? "checked" : "") %> <%= clusteringAvailable ? "" : "disabled" %>>
                </td>
                <td width="99%">
                    <label for="rb02">
                    <b><fmt:message key="system.clustering.label_enable" /></b> - <fmt:message key="system.clustering.label_enable_info" /> <b><fmt:message key="system.clustering.label_enable_info2" /></b> 
                    </label>
                </td>
            </tr>
        </tbody>
        </table>
        <br/>
        <% if (clusteringAvailable) { %>
        <input type="submit" name="update" value="<fmt:message key="global.save_settings" />">
        <% } %>
    </div>
</form>
<!-- END 'Clustering Enabled' -->
<br>
<div class="jive-contentBoxHeader">
    <fmt:message key="system.clustering.overview.label"/>
</div>
<div class="jive-contentBox">
    <p>
        <fmt:message key="system.clustering.overview.info">
            <fmt:param value="<%= clusterNodesInfo.size() %>" />
            <fmt:param value="<%= maxClusterNodes %>" />
            <fmt:param value="<span style='background-color:#ffc;'>" />
            <fmt:param value="</span>" />
        </fmt:message>
    </p>

      <table cellpadding="3" cellspacing="2" border="0">
          <thead>
              <tr>
                  <th colspan="2">
                      <fmt:message key="system.clustering.overview.node"/>
                  </th>
                  <th>
                      <fmt:message key="system.clustering.overview.joined"/>
                  </th>
                  <th style="text-align:center;">
                      <fmt:message key="system.clustering.overview.clients"/>
                  </th>
                  <th style="text-align:center;">
                      <fmt:message key="system.clustering.overview.incoming_servers"/>
                  </th>
                  <th style="text-align:center;">
                      <fmt:message key="system.clustering.overview.outgoing_servers"/>
                  </th>
                  <th style="text-align:center;">
                      <fmt:message key="system.clustering.overview.memory"/>
                  </th>
                  <th width="90%" class="last">&nbsp;</th>
              </tr>
          </thead>
          <tbody>
            <% if (!clusterNodesInfo.isEmpty()) {
                for (ClusterNodeInfo nodeInfo : clusterNodesInfo) {
                    boolean isLocalMember =
                            XMPPServer.getInstance().getNodeID().equals(nodeInfo.getNodeID());
                    String nodeID = Base64.encodeBytes(nodeInfo.getNodeID().toByteArray(), Base64.URL_SAFE);
                    Map<String, Object> nodeStats = null;
                    for (Object stat : statistics) {
                        Map<String, Object> statsMap = (Map<String, Object>) stat;
                        if (statsMap == null) {
                            continue;
                        }
                        if (Arrays.equals((byte[]) statsMap.get(GetBasicStatistics.NODE),
                                nodeInfo.getNodeID().toByteArray())) {
                            nodeStats = statsMap;
                            break;
                        }
                    }
            %>
              <tr class="<%= (isLocalMember ? "local" : "") %>" valign="middle">
                  <td align="center" width="1%">
                      <a href="plugins/<%= CacheFactory.getPluginName() %>/system-clustering-node.jsp?UID=<%= URLEncoder.encode(nodeID, StandardCharsets.UTF_8.name()) %>"
                       title="Click for more details"
                       ><img src="images/server-network-24x24.gif" width="24" height="24" border="0" alt=""></a>
                  </td>
                  <td class="jive-description" nowrap width="1%" valign="middle">
                      <a href="plugins/<%= CacheFactory.getPluginName() %>/system-clustering-node.jsp?UID=<%= URLEncoder.encode(nodeID, StandardCharsets.UTF_8.name()) %>">
                      <%  if (isLocalMember) { %>
                          <b><%= nodeInfo.getHostName() %></b>
                      <%  } else { %>
                          <%= nodeInfo.getHostName() %>
                      <%  } %></a>
                      <br />
                      <%= nodeInfo.getNodeID() %>
                  </td>
                  <td class="jive-description" nowrap width="1%" valign="middle">
                      <%= JiveGlobals.formatDateTime(new Date(nodeInfo.getJoinedTime())) %>
                  </td>
                  <td class="jive-description" nowrap width="1%" valign="middle">
                      <%= nodeStats != null ? nodeStats.get(GetBasicStatistics.CLIENT) : "N/A" %>
                  </td>
                  <td class="jive-description" nowrap width="1%" valign="middle">
                      <%= nodeStats != null ? nodeStats.get(GetBasicStatistics.INCOMING) : "N/A" %>
                  </td>
                  <td class="jive-description" nowrap width="1%" valign="middle">
                      <%= nodeStats != null ? nodeStats.get(GetBasicStatistics.OUTGOING) : "N/A" %>
                  </td>
                  <td class="jive-description" nowrap width="75%" valign="middle">
                  <table width="100%">
                    <tr>
                      <%
                          int percent = 0;
                          String memory = "N/A";
                          if (nodeStats != null) {
                              double usedMemory = (Double) nodeStats.get(GetBasicStatistics.MEMORY_CURRENT);
                              double maxMemory = (Double) nodeStats.get(GetBasicStatistics.MEMORY_MAX);
                              double percentFree = ((maxMemory - usedMemory) / maxMemory) * 100.0;
                              percent = 100-(int)Math.round(percentFree);
                                DecimalFormat mbFormat = new DecimalFormat("#0.00");
                                memory = mbFormat.format(usedMemory) + " MB of " + mbFormat.format(maxMemory) + " MB used";
                          }
                      %>
                        <td width="30%">
                          <div class="bar">
                          <table cellpadding="0" cellspacing="0" border="0" width="100%" style="border:1px #666 solid;">
                          <tr>
                              <%  if (percent == 0) { %>

                                  <td width="100%"><img src="images/percent-bar-left.gif" width="30" height="4" border="0" alt=""></td>

                              <%  } else { %>

                                  <%  if (percent >= 90) { %>

                                      <td width="<%= percent %>%" background="images/percent-bar-used-high.gif"
                                          ><img src="images/blank.gif" width="1" height="4" border="0" alt=""></td>

                                  <%  } else { %>

                                      <td width="<%= percent %>%" background="images/percent-bar-used-low.gif"
                                          ><img src="images/blank.gif" width="1" height="4" border="0" alt=""></td>

                                  <%  } %>
                                  <td width="<%= (100-percent) %>%" background="images/percent-bar-left.gif"
                                      ><img src="images/blank.gif" width="1" height="4" border="0" alt=""></td>
                              <%  } %>
                          </tr>
                          </table>
                          </div>
                        </td>
                        <td class="jive-description">
                          <%= memory %>
                        </td>
                      </tr>
                    </table>
                  </td>
                  <td width="20%">&nbsp;</td>
              </tr>
              <% }
              } else if (ClusterManager.isClusteringStarting()) { %>
              <tr valign="middle" align="middle" class="local">
                  <td colspan=8>
                      <fmt:message key="system.clustering.starting">
                          <fmt:param value="<a href=\"system-clustering.jsp\">"/>
                          <fmt:param value="</a>"/>
                      </fmt:message>
                  </td>
              </tr>
              <% } %>
        </tbody>
        </table>
</div>

<c:if test="${clusteringStarted}">
    <div class="jive-contentBoxHeader">
        <fmt:message key="system.clustering.versions.label"/>
    </div>
    <div class="jive-contentBox">
        <table style="white-space: nowrap; padding: 3px; border-spacing: 2px; border-collapse: collapse">
            <thead>
            <tr>
                <th style="width: 1%"></th>
                <%--@elvariable id="clusterNodeInfo" type="org.jivesoftware.openfire.cluster.ClusterNodeInfo>"--%>
                <c:forEach items="${clusterNodesInfo}" var="clusterNodeInfo">
                    <th style="width: 1%"><c:out value="${clusterNodeInfo.hostName}"/></th>
                </c:forEach>
            </tr>
            </thead>
            <tbody>
            <tr>
                <th style="width: 1%">
                    <fmt:message key="short.title"/>
                </th>
                <c:forEach items="${clusterNodesInfo}" var="clusterNodeInfo">
                    <td class="jive-description <c:if test="${localNodeID == clusterNodeInfo.nodeID}">local</c:if>"
                        style="width: 1%">
                        <c:out value="${pluginVersions.get('Openfire', clusterNodeInfo.nodeID)}"/>
                        <c:if test="${pluginVersions.get('Openfire', localNodeID) != pluginVersions.get('Openfire', clusterNodeInfo.nodeID)}">
                            <img src="images/warning-16x16.gif" width="16" height="16" alt="Warning">
                        </c:if>
                    </td>
                </c:forEach>
            </tr>
            <c:forEach items="${plugins}" var="plugin">
                <tr style="vertical-align:middle">
                    <th style="width: 1%">
                        <c:out value="${plugin}"/>
                    </th>
                    <c:forEach items="${clusterNodesInfo}" var="clusterNodeInfo">
                        <td class="jive-description <c:if test="${localNodeID == clusterNodeInfo.nodeID}">local</c:if>"
                            style="width: 1%">
                            <c:out value="${pluginVersions.get(plugin, clusterNodeInfo.nodeID)}"/>
                            <c:if test="${pluginVersions.get(plugin, localNodeID) != pluginVersions.get(plugin, clusterNodeInfo.nodeID)}">
                                <img src="images/warning-16x16.gif" width="16" height="16" alt="Warning">
                            </c:if>
                        </td>
                    </c:forEach>
                </tr>
            </c:forEach>
            </tbody>
        </table>
    </div>
</c:if>
</body>
</html>
