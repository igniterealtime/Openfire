<%@ page
    import="org.jivesoftware.openfire.plugin.gojara.sessions.TransportSessionManager"%>
<%@ page
    import="org.jivesoftware.openfire.plugin.gojara.sessions.GojaraAdminManager"%>
<%@ page
    import="org.jivesoftware.openfire.plugin.gojara.database.SessionEntry"%>
<%@ page
    import="org.jivesoftware.openfire.plugin.gojara.utils.JspHelper"%>
<%@ page import="java.util.Map"%>
<%@ page import="java.util.HashMap"%>
<%@ page import="java.util.Set"%>
<%@ page import="java.util.Date"%>
<%@ page import="java.util.ArrayList"%>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>

<%
    TransportSessionManager transportManager = TransportSessionManager.getInstance();
    GojaraAdminManager gojaraAdminManager = GojaraAdminManager.getInstance();

    //Helper object for generation of sorting links, column restriction is done in DatabaseManager
    // we need this object so we cann pass the parameters around to our functions
    Map<String, String> sortParams = new HashMap<String, String>();
    if (request.getParameter("sortby") != null && request.getParameter("sortorder") != null) {
        sortParams.put("sortby", request.getParameter("sortby"));
        sortParams.put("sortorder", request.getParameter("sortorder"));
    } else {
        sortParams.put("sortby", "username");
        sortParams.put("sortorder", "ASC");
    }
%>

<html>
<head>
<title>Overview of existing Registrations</title>
<meta name="pageID" content="gojaraRegistrationAdministration" />
</head>
<body>
    <div align="center">
    <ul style="list-style: none;padding:0;margin:0;">
    <%
        //do unregisters if supplied, we do them here because we generate output that should be displayed
        if (request.getParameterMap() != null) {
            String uninteresting_params = "sortorder sortby page";
            for (Object key : request.getParameterMap().keySet()) {
                if (uninteresting_params.contains(key.toString())) {
                    continue;
                }
                String[] uservalues = request.getParameterValues(key.toString());
                for (String transport : uservalues) {
    %>
    <li><%=gojaraAdminManager.unregisterUserFrom(transport, key.toString())%></li>
    <%
        }
            }
        }
    %>
    </ul>
    </div>


    <div align="center">
    <% if (!gojaraAdminManager.areGatewaysConfigured()) {%>
        <h2><a href="gojara-gatewayStatistics.jsp">Warning: Not all Gateways are configured for admin usage. This means unregistrations will not be properly executed.<br/>
         Please configure admin_jid = gojaraadmin@<%= XMPPServer.getInstance().getServerInfo().getXMPPDomain() %>  in Spectrum2 transport configuration.</a></h2>
     <% } %>
        <h5>Logintime 1970 means User did register but never logged in,
            propably because of invalid credentials.</h5>
            <br>
            <br>
        Registrations total: <b style="font-size:150%"><%=transportManager.getNumberOfRegistrations()%></b><br>
    </div>
    <br>
    <%
        //pagination logic
        //get all records, we limit these later. Not all databes support limiting queries so we need to do it the bad way
        ArrayList<SessionEntry> registrations = transportManager.getAllRegistrations(sortParams.get("sortby"),
                sortParams.get("sortorder"));
        
        int numOfSessions = registrations.size();
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
        <br> Pages: [
        <%
            for (int i = 1; i <= numOfPages; i++) {
        %>
        <%="<a href=\"gojara-RegistrationsOverview.jsp?page=" + i + "&sortby=" + sortParams.get("sortby") + "&sortorder="
                        + sortParams.get("sortorder") + "\" class=\"" + (current_page  == i ? "jive-current" : "") + "\">" + i
                        + "</a>" %>
        <%
            }
        %>
        ]
    </p>
    <form name="unregister-form" id="gojara-RegOverviewUnregister"
        method="POST">
        <div class="jive-table">
            <table cellpadding="0" cellspacing="0" border="0" width="100%">
                <thead>
                    <tr>
                        <th nowrap>#</th>
                        <th nowrap><%=JspHelper.sortingHelperRegistrations("username", sortParams)%></th>
                        <th nowrap><%=JspHelper.sortingHelperRegistrations("transport", sortParams)%></th>
                        <th nowrap>Active?</th>
                        <th nowrap>Admin Configured?</th>
                        <th nowrap><%=JspHelper.sortingHelperRegistrations("lastActivity", sortParams)%></th>
                        <th nowrap>Unregister?</th>
                    </tr>
                </thead>
                <tbody>
                    <%	
                        int show_number = 1 + current_index;
                        for (SessionEntry registration : registrations.subList(current_index, next_items)) {
                    %>
                    <tr class="jive-odd">
                        <td><%= show_number%></td>
                        <td><a
                            href="gojara-sessionDetails.jsp?username=<%=registration.getUsername()%>"
                            title="Session Details for <%=registration.getUsername()%>"><%=registration.getUsername()%></a></td>
                        <td><%=registration.getTransport()%></td>
                        <td>
                            <%
                                if (transportManager.isTransportActive(registration.getTransport())) {
                            %> <img alt="Yes" src="/images/success-16x16.gif"> <%
                                } else {
                             %> <img alt="No" src="/images/error-16x16.gif" title="Sending unregister to inactive transport will result in NOT UNREGISTERING the registration."> <%
                                }
                             %>
                        </td>
                        <td>
                        <% if (gojaraAdminManager.isGatewayConfigured(registration.getTransport())) { %>
                        <img alt="Yes" src="/images/success-16x16.gif"> 
                        <% 	} else { %>
                         <img alt="No" src="/images/error-16x16.gif" title="Sending unregister to unconfigured transport will result in NOT UNREGISTERING the registration.">
                          <% }%>
                        </td>
                        <td
                            title="<%=JspHelper.dateDifferenceHelper(registration.getLast_activityAsDate())%>"><%=registration.getLast_activityAsDate()%></td>
                        <td><input type="checkbox"
                            name="<%=registration.getUsername()%>"
                            value="<%=registration.getTransport()%>"></td>
                    </tr>
                    <%
                        show_number++;
                        }
                    %>
                </tbody>
            </table>
        </div>
        <p>
            Pages: [
            <%
            for (int i = 1; i <= numOfPages; i++) {
        %>
            <%="<a href=\"gojara-RegistrationsOverview.jsp?page=" + i + "&sortby=" + sortParams.get("sortby") + "&sortorder="
                        + sortParams.get("sortorder") + "\" class=\"" + (current_page == i ? "jive-current" : "") + "\">" + i
                        + "</a>"%>
            <%
                }
            %>
            ]
        </p>
        <br>
        <div align="center">
            <input type="submit" value="Unregister">
        </div>
    </form>
</body>
</html>
