<%@ page import="com.jivesoftware.util.cluster.NodeRuntimeStats,
                 org.jivesoftware.util.cache.CacheFactory,
                 com.hazelcast.core.Hazelcast,
                 com.hazelcast.core.Cluster,
                 com.hazelcast.core.Member,
                 com.hazelcast.config.ClasspathXmlConfig,
                 com.hazelcast.config.Config,
                 org.jivesoftware.openfire.cluster.ClusterManager,
                 org.jivesoftware.openfire.cluster.NodeID,
                 org.jivesoftware.openfire.cluster.ClusterNodeInfo"
%>
<%@ page import="org.jivesoftware.util.*" %>
<%@ page import="org.jivesoftware.util.cache.Cache" %>
<%@ page import="java.text.DecimalFormat" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="java.util.*" %>
<%@ page import="java.util.LinkedList" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>


<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<html>
<head>
    <title>Cluster Node Information</title>
    <meta name="pageID" content="system-clustering"/>
    <meta http-equiv="refresh" content="10" >
    <style type="text/css">
    .warning {
        color : #f00;
        font-weight : bold;
    }
    .jive-stats .jive-table THEAD TH, .jive-stats .jive-table TBODY TD {
        border-right : 1px #ccc solid;
        text-align : center;
    }
    .jive-stats .jive-table .c6c7c8, .jive-stats .jive-table .c8, .jive-stats .jive-table TBODY .c8 {
        border-right : 0px;
    }
    .jive-stats .jive-table TBODY TD TABLE TD {
        border : 0px;
    }

    .jive-info .c1 {
        width : 30%;
    }
    .jive-info .c2 {
        width : 25%;
    }
    .jive-info .c3 {
        width : 15%;
        text-align : center;
    }
    .jive-info .c4 {
        width : 30%;
    }
    </style>
</head>

<body>

<% // Is clustering enabled? If not, redirect back to the cache page
    if (!ClusterManager.isClusteringStarted()) {
        response.sendRedirect("../../system-clustering.jsp");
        return;
    }

    // get parameters
    boolean clear = request.getParameter("clear") != null;
    String uid = ParamUtils.getParameter(request, "UID");

    // Clear the cache stats if requested
    if (clear) {
        NodeRuntimeStats.clearCacheStats();
        response.sendRedirect("system-clustering-node.jsp?UID=" + uid);
        return;
    }

	List<ClusterNodeInfo> members = (List<ClusterNodeInfo>) CacheFactory.getClusterNodesInfo();
    Map<NodeID, NodeRuntimeStats.NodeInfo> nodeInfoMap = NodeRuntimeStats.getNodeInfo();

    // Sort it according to name
    Collections.sort(members, new Comparator<ClusterNodeInfo>() {
        public int compare(ClusterNodeInfo member1, ClusterNodeInfo member2) {
            String name1 = member1.getHostName() + " (" + member1.getNodeID() + ")";
            String name2 = member2.getHostName() + " (" + member2.getNodeID() + ")";
            return name1.toLowerCase().compareTo(name2.toLowerCase().toLowerCase());
        }
    });

    // If no UID was used, use the UID from the first member in the member list
    byte[] byteArray;
    if (uid == null) {
        byteArray = members.get(0).getNodeID().toByteArray();
    } else {
        byteArray = Base64.decode(uid, Base64.URL_SAFE);
    }

    // Get the specific member requested
    ClusterNodeInfo member = null;
    for (int i = 0; i < members.size(); i++) {
        ClusterNodeInfo m = members.get(i);
        if (Arrays.equals(byteArray, m.getNodeID().toByteArray())) {
            member = m;
            break;
        }
    }

    if (member == null) {
        Log.warn("Node not found: " + uid);
        for (int i = 0; i < members.size(); i++) {
            ClusterNodeInfo m = members.get(i);
            Log.warn("Available members: " + m.getNodeID().toString());
        }

        response.sendRedirect("../../system-clustering.jsp");
        return;
    }

    // Get the cache stats object:
    Map cacheStats = Hazelcast.getHazelcastInstanceByName("openfire").getMap("opt-$cacheStats");

    // Decimal formatter for numbers
    DecimalFormat decFormat = new DecimalFormat("#,##0.0");
    NumberFormat numFormat = NumberFormat.getInstance();
    DecimalFormat mbFormat = new DecimalFormat("#0.00");
    DecimalFormat percentFormat = new DecimalFormat("#0.0");

    // Get the list of existing caches
    Cache[] caches = webManager.getCaches();
    String[] cacheNames = new String[caches.length];
    for (int i = 0; i < caches.length; i++) {
        cacheNames[i] = caches[i].getName();
    }
%>

<p>
Below you will find statistic information for the selected node. This page will be automatically
refreshed every 10 seconds.
</p>

<table cellpadding="3" cellspacing="0" border="0" width="100%">
<tr>
    <td width="99%">
        &nbsp;
    </td>
    <td width="1%" nowrap="nowrap">
        <a href="../../system-clustering.jsp">&laquo; Back to cluster summary</a>
    </td>
</tr>
</table>

<br />

<div class="jive-stats">
<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th rowspan="2" class="c1">Node</th>
        <th rowspan="2" class="c2">Memory Usage</th>
        <th colspan="3" class="c3c4c5">Incoming Packets</th>
        <th colspan="3" class="c6c7c8">Outgoing Packets</th>
    </tr>
    <tr>
        <th class="c3" colspan="2">Packets Received</th>
        <th class="c5">Success</th>
        <th class="c6">CPU</th>
        <th class="c7">Throughput</th>
        <th class="c8">Success</th>
    </tr>
</thead>

<tbody>

<%  for (int i=0; i<members.size(); i++) {
        ClusterNodeInfo m = members.get(i);
        if (member != m) {
            continue;
        }
        NodeRuntimeStats.NodeInfo nodeInfo = nodeInfoMap.get(m.getNodeID());
%>
    <tr bgcolor="#ffffcc">

        <td nowrap class="c1">
            <%= m.getHostName() %><br/><%= m.getNodeID() %>
        </td>

        <td class="c2" valign="middle">
            <%  double freeMem = (double)nodeInfo.getFreeMem()/(1024.0*1024.0);
                double maxMem = (double)nodeInfo.getMaxMem()/(1024.0*1024.0);
                double totalMem = (double)nodeInfo.getTotalMem()/(1024.0*1024.0);
                double usedMem = totalMem - freeMem;
                double percentFree = ((maxMem - usedMem)/maxMem)*100.0;
                double percentUsed = 100.0 - percentFree;
                int percent = 100-(int)Math.round(percentFree);
            %>

            <table cellpadding="0" cellspacing="0" border="0" width="250">
            <tr>
                <td width="99%">
                    <table cellpadding="0" cellspacing="0" border="0" width="100%" style="border:1px #666 solid;">
                    <tr>
                        <%  if (percent == 0) { %>

                            <td width="100%" style="padding:0px;"><img src="../../images/percent-bar-left.gif" width="100%" height="4" border="0" alt=""></td>

                        <%  } else { %>

                            <%  if (percent >= 90) { %>

                                <td width="<%= percent %>%" background="../../images/percent-bar-used-high.gif" style="padding:0px;"
                                    ><img src="images/blank.gif" width="1" height="4" border="0" alt=""></td>

                            <%  } else { %>

                                <td width="<%= percent %>%" background="../../images/percent-bar-used-low.gif" style="padding:0px;"
                                    ><img src="images/blank.gif" width="1" height="4" border="0" alt=""></td>

                            <%  } %>

                            <td width="<%= (100-percent) %>%" background="../../images/percent-bar-left.gif" style="padding:0px;"
                                ><img src="images/blank.gif" width="1" height="4" border="0" alt=""></td>

                        <%  } %>
                    </tr>
                    </table>
                </td>
                <td width="1%" nowrap="nowrap">
                    <%= mbFormat.format(totalMem) %> MB, <%= decFormat.format(percentUsed) %>% used
                </td>
           </tr>
           </table>

        </td>
        <td class="c3" colspan="2">
            
        </td>

        <td class="c5">
            
        </td>
        <td class="c6">
           
        </td>
        <td class="c7">
           
        </td>
        <td class="c8">
            
        </td>
    </tr>

<%  } %>

</tbody>

</table>
</div>
</div>


<br/>

[<a href="system-clustering-node.jsp?clear=true&UID=<%=uid%>">Clear Cache Stats</a>]

<br /><br />

<table cellpadding="3" cellspacing="0" border="0" width="100%">
<tr>
    <td width="1%"><img src="images/server-network-48x48.gif" width="48" height="48" border="0" alt="" hspace="10"></td>
    <td width="99%">
        <span style="font-size:1.1em;"><b>Node Details: <%= member.getHostName() %> (<%= member.getNodeID() %>)</b></span>
        <br />
        <span style="font-size:0.9em;">
        Joined: <%= JiveGlobals.formatDateTime(new Date(member.getJoinedTime())) %>
        </span>
    </td>
</tr>
</table>

<p>
Cache statistics for this cluster node appear below.
</p>

<div class="jive-info">
<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th class="c1">Cache Type</th>
        <th class="c2">Size</th>
        <th class="c3">Objects</th>
        <th class="c4">Effectiveness</th>
    </tr>
</thead>

<tbody>

<% Map cNames = (Map) cacheStats.get(member.getNodeID().toString());
    if (cNames == null) {
%>
    <tr>
    <td align="center" colspan="4"><i>No stats available</i></td>
    </tr>

<% } else {
    // Iterate through the cache names,
    for (String cacheName : cacheNames) {
        long[] theStats = (long[]) cNames.get(cacheName);
        // Skip caches that are in this JVM but not in other nodes
        if (theStats == null) {
            continue;
        }
        long size = theStats[0];
        long maxSize = theStats[1];
        long numObjects = theStats[2];

        double memUsed = (double) size / (1024 * 1024);
        double totalMem = (double) maxSize / (1024 * 1024);
        double freeMem = 100 - 100 * memUsed / Math.max(1, totalMem);
        double usedMem = 100 * memUsed / Math.max(1, totalMem);
        long hits = theStats[3];
        long misses = theStats[4];
        double hitPercent = 0.0;
        if (hits + misses == 0) {
            hitPercent = 0.0;
        } else {
            hitPercent = 100 * (double) hits / (hits + misses);
        }
        boolean lowEffec = (hits > 500 && hitPercent < 85.0 && freeMem < 20.0);
%>
    <tr>
        <td class="c1">
            <%= cacheName %>
        </td>
        <td class="c2">

            <% if (maxSize != -1 && maxSize != Integer.MAX_VALUE) { %>
            <%= mbFormat.format(totalMem) %> MB,
            <%= percentFormat.format(usedMem)%>% used
            <% } else { %>
            Unlimited
            <% } %>

        </td>
        <td class="c3">

            <%= LocaleUtils.getLocalizedNumber(numObjects) %>

        </td>
        <td class="c4">

            <% if (lowEffec) { %>
            <font color="#ff0000"><b><%= percentFormat.format(hitPercent)%>%</b>
                <%  } else { %>
                <b><%= percentFormat.format(hitPercent)%>%</b>
                <%  } %>
                (<%= LocaleUtils.getLocalizedNumber(hits) %>
                hits, <%= LocaleUtils.getLocalizedNumber(misses) %> misses)

        </td>
    </tr>
    <%
        }
    }
    %>
</tbody>

</table>
</div>
</div>

<br /><br />

<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th colspan="2">
            Openfire Cluster Details
        </th>
    </tr>
</thead>
<tbody>
    <tr>
        <td width="100%">
            Hazelcast Version <%= NodeRuntimeStats.getProviderConfig("hazelcast.version") %> 
            Build <%= NodeRuntimeStats.getProviderConfig("hazelcast.build") %>
        </td>
    </tr>
</tbody>
</table>
</div>

<br/>

</body>
</html>