<%--
  -	$RCSfile$
  -	$Revision: $
  -	$Date: 2007-09-21 $
  -
  - Copyright (C) 2007 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%@ page import="org.jivesoftware.database.DbConnectionManager"
    errorPage="error.jsp"
%>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.cluster.ClusterManager" %>
<%@ page import="org.jivesoftware.openfire.cluster.ClusterNodeInfo" %>
<%@ page import="org.jivesoftware.openfire.cluster.GetBasicStatistics" %>
<%@ page import="org.jivesoftware.util.JiveGlobals" %>
<%@ page import="org.jivesoftware.util.Log" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.util.cache.CacheFactory" %>
<%@ page import="java.text.DecimalFormat" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.jivesoftware.util.Base64" %>

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
    boolean updateSucess = false;

    if (update) {
        if (!clusteringEnabled) {
            ClusterManager.setClusteringEnabled(false);
            updateSucess = true;
        }
        else {
            if (ClusterManager.isClusteringAvailable()) {
                ClusterManager.setClusteringEnabled(true);
                updateSucess = ClusterManager.isClusteringStarted();
            }
            else {
                Log.error("Failed to enable clustering. Clustering is not installed.");
            }
        }
    }

    boolean usingEmbeddedDB = DbConnectionManager.getDatabaseType() == DbConnectionManager.DatabaseType.hsqldb;
    boolean clusteringAvailable = !usingEmbeddedDB && ClusterManager.isClusteringAvailable();
    boolean clusteringStarting = ClusterManager.isClusteringStarting();
    int maxClusterNodes = ClusterManager.getMaxClusterNodes();
    clusteringEnabled = ClusterManager.isClusteringStarted() || ClusterManager.isClusteringStarting();

    Collection<ClusterNodeInfo> clusterNodesInfo = ClusterManager.getNodesInfo();
    // Get some basic statistics from the cluster nodes
    // TODO Set a timeout so the page can load fast even if a node is taking too long to answer
    Collection<Object> statistics =
            CacheFactory.doSynchronousClusterTask(new GetBasicStatistics(), true);
    // Calculate percentages
    int clients = 0;
    int incoming = 0;
    int outgoing = 0;
    for (Object stat : statistics) {
        Map<String, Object> statsMap = (Map<String, Object>) stat;
        if (statsMap == null) {
            continue;
        }
        clients += (Integer) statsMap.get(GetBasicStatistics.CLIENT);
        incoming += (Integer) statsMap.get(GetBasicStatistics.INCOMING);
        outgoing += (Integer) statsMap.get(GetBasicStatistics.OUTGOING);
    }
    for (Object stat : statistics) {
        Map<String, Object> statsMap = (Map<String, Object>) stat;
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
%>

<p>
<fmt:message key="system.clustering.info"/>
</p>

<%  if (update) {
        if (updateSucess) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
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
            <td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0"/></td>
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
        <td valign="top" align="left" colspan="2">
            <% if (usingEmbeddedDB) { %>
                <span><fmt:message key="system.clustering.using-embedded-db"/></span>
            <% } else if (maxClusterNodes == 0) { %>
                <span><fmt:message key="system.clustering.not-installed"/></span>
            <% } else { %>
                <span><fmt:message key="system.clustering.not-valid-license"/></span>
            <% } %>
        </td>
    </tbody>
    </table>
    </div>
    <br>
<% } %> 

<!-- BEGIN 'Clustering Enabled' -->
<form action="system-clustering.jsp" method="post">
	<div class="jive-contentBoxHeader">
		<fmt:message key="system.clustering.enabled.legend" />
	</div>
	<div class="jive-contentBox">
		<table cellpadding="3" cellspacing="0" border="0">
		<tbody>
			<tr>
				<td width="1%" valign="top" nowrap>
					<input type="radio" name="clusteringEnabled" value="false" id="rb01"
					 <%= (!clusteringEnabled ? "checked" : "") %> <%= (!clusteringAvailable || clusteringStarting ? "disabled" : "") %>>
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
					 <%= (clusteringEnabled ? "checked" : "") %> <%= (!clusteringAvailable || clusteringStarting ? "disabled" : "") %>>
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
        <% if (clusteringAvailable  && !clusteringStarting) { %>
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
            <fmt:param value="<%= "<span style='background-color:#ffc;'>" %>" />
            <fmt:param value="<%= "</span>" %>" />
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
                      <a href="plugins/enterprise/system-clustering-node.jsp?UID=<%= nodeID %>"
                       title="Click for more details"
                       ><img src="images/server-network-24x24.gif" width="24" height="24" border="0" alt=""></a>
                  </td>
                  <td class="jive-description" nowrap width="1%" valign="middle">
                      <a href="plugins/enterprise/system-clustering-node.jsp?UID=<%= nodeID %>">
                      <%  if (isLocalMember) { %>
                          <b><%= nodeInfo.getHostName() %></b>
                      <%  } else { %>
                          <%= nodeInfo.getHostName() %>
                      <%  } %></a>
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

                                  <td width="100%"><img src="images/percent-bar-left.gif" width="100%" height="4" border="0" alt=""></td>

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
              } else if (clusteringStarting) { %>
              <tr valign="middle" align="middle" class="local">
                  <td colspan=8>
                      <fmt:message key="system.clustering.starting">
                          <fmt:param value="<%= "<a href='system-clustering.jsp'>" %>" />
                          <fmt:param value="<%= "</a>" %>" />
                      </fmt:message>
                  </td>
              </tr>
              <% } %>
        </tbody>
        </table>
</div>


</body>
</html>
