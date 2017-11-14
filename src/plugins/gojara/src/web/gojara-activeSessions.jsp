<%@ page
    import="org.jivesoftware.openfire.plugin.gojara.sessions.TransportSessionManager"%>
<%@ page
    import="org.jivesoftware.openfire.plugin.gojara.sessions.GojaraAdminManager"%>

<%@ page
    import="org.jivesoftware.openfire.plugin.gojara.sessions.GatewaySession"%>
<%@ page
    import="org.jivesoftware.openfire.plugin.gojara.utils.JspHelper"%>
<%@ page import="java.util.Map"%>
<%@ page import="java.util.HashMap"%>
<%@ page import="java.util.Set"%>
<%@ page import="java.util.Date"%>
<%@ page import="java.util.ArrayList"%>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>

<%
    TransportSessionManager transportManager = TransportSessionManager.getInstance();
    GojaraAdminManager gojaraAdminManager = GojaraAdminManager.getInstance();
    //Helper object for generation of sorting links, column restriction is done in DatabaseManager
    Map<String, String> sortParams = new HashMap<String, String>();
    if (request.getParameter("sortby") != null && request.getParameter("sortorder") != null) {
        sortParams.put("sortby", request.getParameter("sortby"));
        sortParams.put("sortorder", request.getParameter("sortorder"));
    } else {
        sortParams.put("sortby", "transport");
        sortParams.put("sortorder", "ASC");
    }
%>
<html>
<head>
<title>Gateway Sessions</title>

<meta name="pageID" content="gojaraSessions" />
</head>
<body>
     <div align="center">
    <% if (!gojaraAdminManager.areGatewaysConfigured()) {%>
        <h2><a href="gojara-gatewayStatistics.jsp">Warning: Not all Gateways are configured for admin usage. This means session details may be inaccurate or not logged at all.<br/>
         Please configure admin_jid = gojaraadmin@<%= XMPPServer.getInstance().getServerInfo().getXMPPDomain() %> in Spectrum2 transport configuration.</a></h2><br/>
     <% } %>
    <h4>
        Current number of active Gateway Sessions: &emsp;
        <b style="font-size:150%"><%= transportManager.getNumberOfActiveSessions() %></b>
    </h4>
    <br>
    <%
        Map<String, Map<String, Long>> sessions = transportManager.getSessions();
        for (String transport : sessions.keySet()) {
    %>
    <%=transport.substring(0, 10)%>... :
    <b style="font-size:150%"><%=sessions.get(transport).size()%></b> &emsp;
    <%
        }
    %></div>
    <br>
    <br>
    <%
        //pagination logic
        //get all records, we limit these later as we have to sort them first
        ArrayList<GatewaySession> gwSessions = transportManager
                .getSessionsSorted(sortParams.get("sortby"), sortParams.get("sortorder"));
        
        int numOfSessions = gwSessions.size();
        // 100 entries is exactly 1 page, 101 entries is 2 pages
        int numOfPages = numOfSessions % 100 == 0 ? (numOfSessions / 100) : (1 + (numOfSessions / 100));
        //lets check for validity if page parameter is supplied, set it to 1 if not in valid range 
        int current_page = 1;
        if (request.getParameter("page") != null) {
            try {
                current_page = Integer.parseInt(request.getParameter("page"));
                if (current_page < 1 || current_page > numOfPages)
                    current_page = 1;
            } catch (Exception e) {
            }
        }
        // we now know current_page is in valid range from supplied parameter or standard.
        // this will be our sublist starting index, 0, 100, 200 ... 
        int current_index = (current_page -1)* 100;
        //ending index, 100, 200 etc, when next items > numOfSessions we have reached last page, set proper index so we have no out of bounds
        //ending index is excluded, so 0-100 is 0-99, e.g. item 1-100
        int next_items = current_index + 100;
        if (next_items > numOfSessions)
            next_items = numOfSessions;
    %>
    <p>
        Pages: [
        <%
        for (int i = 1; i <= numOfPages; i++) {
        %>
        <%="<a href=\"gojara-activeSessions.jsp?page=" + i + "&sortby=" + sortParams.get("sortby") + "&sortorder="
                        + sortParams.get("sortorder") + "\" class=\"" + (current_page == i ? "jive-current" : "") + "\">" + i
                        + "</a>"%>
        <%
            }
        %>
        ]
    </p>
    <div class="jive-table">
        <table cellpadding="0" cellspacing="0" border="0" width="100%">
            <thead>
                <tr>
                    <th nowrap>#</th>
                    <th nowrap><%=JspHelper.sortingHelperSessions("username", sortParams)%></th>
                    <th nowrap><%=JspHelper.sortingHelperSessions("transport", sortParams)%></th>
                    <th nowrap><%=JspHelper.sortingHelperSessions("loginTime", sortParams)%></th>
                </tr>
            </thead>
            <tbody>
                <% if (numOfSessions == 0) { %>
                <tr><td colspan="4">No active Sessions</td></tr>
                <% } else {
                    int show_number = 1 + current_index;
                    for (GatewaySession gwsession : gwSessions.subList(current_index, next_items)) {
                %>
                <tr class="jive-odd">
                    <td><%= show_number%></td>
                    <td><a
                        href="gojara-sessionDetails.jsp?username=<%=gwsession.getUsername()%>"
                        title="Session Details for <%=gwsession.getUsername()%>"><%=gwsession.getUsername()%></a></td>
                    <td><%=gwsession.getTransport()%></td>
                    <td
                        title="<%=JspHelper.dateDifferenceHelper(gwsession.getLastActivity())%>"><%=gwsession.getLastActivity()%></td>
                </tr>
                <%
                    show_number++;
                    }}
                %>
            </tbody>
        </table>
    </div>
    <br>
    <p>
        Pages: [
        <%
        for (int i = 1; i <= numOfPages; i++) {
    %>
        <%="<a href=\"gojara-activeSessions.jsp?page=" + i + "&sortby=" + sortParams.get("sortby") + "&sortorder="
                        + sortParams.get("sortorder") + "\" class=\"" + (current_page == i ? "jive-current" : "") + "\">" + i
                        + "</a>"%>
        <%
            }
        %>
        ]
    </p>
</body>
</html>
