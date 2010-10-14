<%--
  -	$RCSfile$
  -	$Revision: 32111 $
  -	$Date: 2006-07-12 22:19:23 -0700 (Wed, 12 Jul 2006) $
--%>
<%@ page
    import="org.jivesoftware.openfire.fastpath.history.AgentChatSession,
            org.jivesoftware.openfire.fastpath.history.ChatSession,
            org.jivesoftware.openfire.fastpath.history.ChatTranscriptManager,
            org.jivesoftware.xmpp.workgroup.Workgroup,
            org.jivesoftware.xmpp.workgroup.WorkgroupManager,
            org.jivesoftware.xmpp.workgroup.utils.ModelUtil" %>
<%@ page import="org.jivesoftware.database.DbConnectionManager" %>
<%@ page import="org.jivesoftware.util.Log" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.xmpp.packet.JID" %>
<%@ page import="java.sql.Connection" %>
<%@ page import="java.sql.PreparedStatement" %>
<%@ page import="java.sql.SQLException"%>
<%@ page import="java.text.DateFormat"%>
<%@ page import="java.text.SimpleDateFormat, java.util.Collection, java.util.Date, java.util.Iterator"%>
<%@ page import="org.jivesoftware.openfire.user.UserManager" %>
<%@ page import="org.jivesoftware.openfire.user.User" %>
<html>
<head>
    <title>Chat Summary</title>
    <meta name="pageID" content="chat-summary"/>
    <style type="text/css">@import url( /js/jscalendar/calendar-win2k-cold-1.css );</style>
    <script type="text/javascript" src="/js/jscalendar/calendar.js"></script>
    <script type="text/javascript" src="/js/jscalendar/i18n.jsp"></script>
    <script type="text/javascript" src="/js/jscalendar/calendar-setup.js"></script>

    <style type="text/css">
        .textfield {
            font-size: 11px;
            font-family: verdana;
            padding: 3px 2px;
            background: #efefef;
        }

        .text {
            font-size: 11px;
            font-family: verdana;
        }
    </style>
    <!--<meta name="helpPage" content="view_chat_transcript_reports_for_a_workgroup.html"/>-->
</head>

<body>
<style type="text/css">
    @import "style/style.css";
</style>
<%
    // Get a workgroup manager
    WorkgroupManager wgManager = WorkgroupManager.getInstance();
%>
<% // Get parameters //
    boolean cancel = request.getParameter("cancel") != null;
    String queueName = ParamUtils.getParameter(request, "queueName");
    if (queueName == null) {
        queueName = "Default Queue";
    }
    // Handle a cancel
    if (cancel) {
        response.sendRedirect("workgroup-summary.jsp");
        return;
    }

    final String sess = request.getParameter("sessionID");
    final String delete = request.getParameter("delete");
    if (ModelUtil.hasLength(sess) && ModelUtil.hasLength(delete)) {
        Connection con = null;
        PreparedStatement pstmt = null;

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement("delete from fpSession where sessionID=?");
            pstmt.setString(1, sess);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement("delete from fpSessionProp where sessionID=?");
            pstmt.setString(1, sess);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement("delete from fpSessionMetadata where sessionID=?");
            pstmt.setString(1, sess);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement("delete from fpAgentSession where sessionID=?");
            pstmt.setString(1, sess);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }

    }

    boolean submit = request.getParameter("submit") != null;

    boolean errors = false;
    String errorMessage = "";

    String start = request.getParameter("startDate");
    String end = request.getParameter("endDate");

    Date startDate = null;
    Date endDate = null;

    if (submit) {

        DateFormat formatter = new SimpleDateFormat("MM/dd/yy");

        if (start == null || "".equals(start)) {
            errors = true;
            start = "";
            errorMessage = "Please specify a valid start date.";
        }
        else {
            try {
                startDate = formatter.parse(start);
            }
            catch (Exception e) {
                errors = true;
                start = "";
                errorMessage = "Please specify a valid start date.";
                Log.error(e);
            }
        }

        if (end == null || "".equals(end)) {
            errors = true;
            end = "";
            errorMessage = "Please specify a valid end date.";
        }
        else {
            try {
                endDate = formatter.parse(end);
            }
            catch (Exception e) {
                errors = true;
                end = "";
                errorMessage = "Please specify a valid end date.";
                Log.error(e);
            }
        }
    }


%>

<% if(errors){ %>
<div class="error">
    <%= errorMessage%>
</div>
<% } %>

<%   if (ModelUtil.hasLength(sess) && ModelUtil.hasLength(delete)) { %>
<div class="success">
    Conversation has been removed.
</div>
<% } %>

<p>
   Allows for specific report retrieval of previous conversations during two specified dates.
</p>

<div  class="jive-contentBox">
<form name="workgroupForm" method="post" action="chat-summary.jsp">
  <h4>Chat Transcripts</h4>
        <table cellpadding="3" cellspacing="1" border="0">
            <tr>
                <td width="1%" nowrap>
                    <span class="text">Select Workgroup:</span>
                </td>
                <td>
                    <select name="workgroupBox" class="text">
                        <%
                            String wgroup = request.getParameter("workgroupBox");
                            for (Workgroup w : wgManager.getWorkgroups()) {
                                String selectionID = "";
                                if (wgroup != null && wgroup.equals(w.getJID().toString())) {
                                    selectionID = "selected";
                                }
                        %>
                        <option value="<%= w.getJID().toString() %>" <%= selectionID %>>
                            <%= w.getJID().toString() %></option>
                        <%
                            }
                        %>
                    </select>
                </td>
            </tr>
            <tr>
                <td class="text" width="1%" nowrap>
                    Choose Date:
                </td>
                <td nowrap>
                    <!-- Start of Date -->
                    <TABLE border="0">
                        <tr valign="top">
                            <td width="1%" nowrap class="text">
                                From:
                            </td>
                            <td width="1%" nowrap class="text"><input type="text" name="startDate" id="startDate" size="15" value="<%= start != null ? start : ""%>"/><br/>
                            Use mm/dd/yy</td>
                            <td width="1%" nowrap>&nbsp;<img src="images/icon_calendarpicker.gif" vspace="3" id="startDateTrigger"></td>


                            <TD width="1%" nowrap class="text">
                                To:
                            </td>
                            <td width="1%" nowrap class="text"><input type="text" name="endDate" id="endDate" size="15" value="<%= end != null ? end : "" %>"/><br/>
                             Use mm/dd/yy</td>
                            <td>&nbsp;<img src="images/icon_calendarpicker.gif" vspace="3" id="endDateTrigger"></td>
                         </TR>
                    </TABLE>
                    <!-- End Of Date -->
                </td>
            </tr>
            <tr>
                <td>
                    <input type="submit" name="submit" value="View Chat Transcripts"/>
                </td>
                <td align="left">
                    &nbsp;
                </td>
            </tr>
        </table>
</form>
</div>
<%
    StringBuffer buf = new StringBuffer();
    final String workgroupName = request.getParameter("workgroupBox");
%>
<% if (ModelUtil.hasLength(workgroupName) && !errors) { %>
<%
    final Workgroup g = wgManager.getWorkgroup(new JID(workgroupName));
%>
<br>
<table class="jive-table"  cellspacing="0" border="0" width="100%">
    <th nowrap>
        Customer
    </th>
    <th>
        Agent
    </th>
     <th>
        Question
    </th>
    <th>
        Date/Time
    </th>
    <th>
        Options
    </th>
    <%
        Collection list = ChatTranscriptManager.getChatSessionsForWorkgroup(g, startDate, endDate);
        Iterator citer = list.iterator();
        while (citer.hasNext()) {
            ChatSession chatSession = (ChatSession)citer
                    .next();
            if (chatSession.getStartTime() == 0) {
                continue;
            }
            String sessionID = chatSession.getSessionID();
    %>
    <tr>
         <td nowrap width="1%" class="conversation-body">
               <%
                String email = chatSession.getEmail();
                if (email.indexOf('@') != -1) {
            %> <a href="mailto:<%=email%>">  <%= chatSession.getCustomerName() %> </a><%
        }
        else {
        %> <%= chatSession.getCustomerName()%> <%
            }
        %>

        </td>
        <td nowrap>
            <%
                AgentChatSession initial = chatSession.getFirstSession();
                if (initial == null) {
                    out.println("<font color=red>");
                    if (chatSession.getState() == 0) {
                        out.println("User left the queue.");
                    }
                    else if (chatSession.getState() == 1) {
                        out.println("No agent picked up request.");
                    }
                    else {
                        out.println("Agent never joined");
                    }
                    out.println("</font>");
                }
                else {
                    JID jid = new JID(initial.getAgentJID());
                    User user = UserManager.getInstance().getUser(jid.getNode());

                    out.println("<a href=\"/user-properties.jsp?username="+user.getName()+"\">"+user.getName()+"</a>");
                }
                final SimpleDateFormat dayFormatter = new SimpleDateFormat("MM/dd/yyyy h:mm:ss a");

                final String displayDate = dayFormatter
                    .format(new Date(chatSession.getStartTime()));
            %>
        </td>
        <td>
            <%= chatSession.getQuestion() %>
        </td>
        <td nowrap><%= displayDate  %>
        </td>
        <td nowrap>
            <a href="chat-conversation.jsp?sessionID=<%= sessionID %>">View</a>
            <a href="chat-summary.jsp?<%=buf.toString() %>&workgroupBox=<%= workgroupName%>&delete=true&sessionID=<%=sessionID%>&startDate=<%=start%>&endDate=<%=end%>&submit=true">Delete</a>
        </td>

    </tr>
    <% } %></table>
<%
    if (list.size() == 0) {

%>
<table class="jive-table" cellpadding="3" cellspacing="1" border="0" width="100%">
    <tr>
        <td class="c1" colspan=6>
            <tr><td class="text">No Chats have occured in this workgroup.</td></tr>
        </td>
    </tr>
</table>
<% } %>
<% } %>


<script type="text/javascript">
    function catcalc(cal) {
        var endDateField = $('endDate');
        var startDateField = $('startDate');

        var endTime = new Date(endDateField.value);
        var startTime = new Date(startDateField.value);
        if (endTime.getTime() < startTime.getTime()) {
            alert("Dates do not match");
            startDateField.value = "";
            endDateField.value= "";
        }
    }

    Calendar.setup(
    {
        inputField  : "startDate",         // ID of the input field
        ifFormat    : "%m/%d/%y",    // the date format
        button      : "startDateTrigger",       // ID of the button
        onUpdate    :  catcalc
    });

    Calendar.setup(
    {
        inputField  : "endDate",         // ID of the input field
        ifFormat    : "%m/%d/%y",    // the date format
        button      : "endDateTrigger",       // ID of the button
        onUpdate    :  catcalc
    });
</script>
</body>
</html>
