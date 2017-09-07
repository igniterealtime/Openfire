<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.openfire.pubsub.Node,
                 java.util.*,
                 java.net.URLEncoder"
    errorPage="error.jsp"
%>
<%@ page import="org.jivesoftware.openfire.pubsub.PubSubManager" %>
<%@ page import="org.xmpp.packet.JID" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters
    int start = ParamUtils.getIntParameter(request,"start",0);
    int range = ParamUtils.getIntParameter(request,"range",webManager.getRowsPerPage("pubsub-node-summary", 15));

    if (request.getParameter("range") != null) {
        webManager.setRowsPerPage("pubsub-node-summary", range);
    }

    PubSubManager pubSubManager = webManager.getPubSubManager();
    List<Node> nodes = pubSubManager.getLeafNodes();

    Collections.sort(nodes, new Comparator<Node>() {
        public int compare(Node node1, Node node2) {
            return node1.getNodeID().toLowerCase().compareTo(node2.getNodeID().toLowerCase());
        }
    });

    int nodeCount = nodes.size();

    // paginator vars
    int numPages = (int)Math.ceil((double)nodeCount/(double)range);
    int curPage = (start/range) + 1;
    int maxNodeIndex = (start+range <= nodeCount ? start+range : nodeCount);
%>
<html>
    <head>
        <title><fmt:message key="pubsub.node.summary.title"/></title>
        <meta name="pageID" content="pubsub-node-summary"/>
    </head>
    <body>

<p>
<fmt:message key="pubsub.node.summary.info" />
</p>

<%  if (request.getParameter("deletesuccess") != null) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="pubsub.node.summary.deleted" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<p>
<fmt:message key="pubsub.node.summary.total_nodes" />: <%= nodeCount %>,
<%  if (numPages > 1) { %>

    <fmt:message key="global.showing" /> <%= (start+1) %>-<%= (maxNodeIndex) %>,

<%  } %>

<fmt:message key="pubsub.node.summary.sorted_id" />

</p>

<%  if (numPages > 1) { %>

    <p>
    <fmt:message key="global.pages" />:
    [
    <%  for (int i=0; i<numPages; i++) {
            String sep = ((i+1)<numPages) ? " " : "";
            boolean isCurrent = (i+1) == curPage;
    %>
        <a href="pubsub-node-summary.jsp?start=<%= (i*range) %>"
         class="<%= ((isCurrent) ? "jive-current" : "") %>"
         ><%= (i+1) %></a><%= sep %>

    <%  } %>
    ]
    </p>

<%  } %>

<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th>&nbsp;</th>
        <th nowrap><fmt:message key="pubsub.node.summary.id" /></th>
        <th nowrap><fmt:message key="pubsub.node.summary.name" /></th>
        <th nowrap><fmt:message key="pubsub.node.summary.description" /></th>
        <th nowrap><fmt:message key="pubsub.node.summary.items" /></th>
        <th nowrap><fmt:message key="pubsub.node.summary.subscribers" /></th>
        <th nowrap><fmt:message key="global.delete" /></th>
    </tr>
</thead>
<tbody>

<%  // Print the list of rooms
    Iterator<Node> nodesPage = nodes.subList(start, maxNodeIndex).iterator();
    if (!nodesPage.hasNext()) {
%>
    <tr>
        <td align="center" colspan="7">
            <fmt:message key="pubsub.node.summary.no_nodes" />
        </td>
    </tr>

<%
    }
    int i = start;
    while (nodesPage.hasNext()) {
        Node node = nodesPage.next();
        i++;
%>
    <tr class="jive-<%= (((i%2)==0) ? "even" : "odd") %>">
        <td width="1%">
            <%= i %>
        </td>
        <td width="1%" valign="middle">
            <%=  StringUtils.escapeHTMLTags(node.getNodeID()) %>
        </td>
        <td width="1%" valign="middle">
            <%=  StringUtils.escapeHTMLTags(node.getName()) %>
        </td>
        <td valign="middle">
            <%= StringUtils.escapeHTMLTags(node.getDescription()) %>
        </td>
        <td width="1%" align="center">
            <%= node.getPublishedItems().size() %>
        </td>
        <td width="1%" align="center">
            <a href="pubsub-node-subscribers.jsp?nodeID=<%= URLEncoder.encode(node.getNodeID(), "UTF-8") %>">
                <%= node.getAllSubscriptions().size() %>
            </a>
        </td>
        <td width="1%" align="center" style="border-right:1px #ccc solid;">
            <a href="pubsub-node-delete.jsp?nodeID=<%= URLEncoder.encode(node.getNodeID(), "UTF-8") %>"
             title="<fmt:message key="global.click_delete" />"
             ><img src="images/delete-16x16.gif" width="16" height="16" border="0" alt=""></a>
        </td>
    </tr>

<%
    }
%>
</tbody>
</table>
</div>

<%  if (numPages > 1) { %>

    <p>
    <fmt:message key="global.pages" />:
    [
    <%  for (i=0; i<numPages; i++) {
            String sep = ((i+1)<numPages) ? " " : "";
            boolean isCurrent = (i+1) == curPage;
    %>
        <a href="pubsub-node-summary.jsp?start=<%= (i*range) %>"
         class="<%= ((isCurrent) ? "jive-current" : "") %>"
         ><%= (i+1) %></a><%= sep %>

    <%  } %>
    ]
    </p>

<%  } %>

    </body>
</html>
