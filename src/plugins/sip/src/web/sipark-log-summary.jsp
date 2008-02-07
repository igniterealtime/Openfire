<%@ page import="org.jivesoftware.openfire.sip.calllog.CallLog,
                 org.jivesoftware.openfire.sip.calllog.CallLogDAO"
        %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="org.jivesoftware.util.*" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.net.URLEncoder" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%!
    final int DEFAULT_RANGE = 15;
    final int[] RANGE_PRESETS = {15, 25, 50, 75, 100};
%>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"/>
<% webManager.init(request, response, session, application, out); %>

<html>
<head>
    <title><fmt:message key="call.summary.title" /></title>
    <meta name="pageID" content="sipark-log-summary"/>
    <script src="/js/prototype.js" type="text/javascript"></script>
    <script src="/js/scriptaculous.js" type="text/javascript"></script>
    <script type="text/javascript" language="javascript" src="/js/tooltips/domLib.js"></script>
    <script type="text/javascript" language="javascript" src="/js/tooltips/domTT.js"></script>
    <style type="text/css">@import url( /js/jscalendar/calendar-win2k-cold-1.css );</style>
    <script type="text/javascript" src="/js/jscalendar/calendar.js"></script>
    <script type="text/javascript" src="/js/jscalendar/i18n.jsp"></script>
    <script type="text/javascript" src="/js/jscalendar/calendar-setup.js"></script>
    <style type="text/css">
        .jive-current {
            font-weight: bold;
            text-decoration: none;
        }

        .stat {
            margin: 0px 0px 8px 0px;
            border: 1px solid #cccccc;
            -moz-border-radius: 3px;
        }

        .stat td table {
            margin: 5px 10px 5px 10px;
        }
        .stat div.verticalrule {
            display: block;
            width: 1px;
            height: 110px;
            background-color: #cccccc;
            overflow: hidden;
            margin-left: 3px;
            margin-right: 3px;
        }
    </style>

    <script type="text/javascript">
        function hover(oRow) {
            oRow.style.background = "#A6CAF0";
            oRow.style.cursor = "pointer";
        }

        function noHover(oRow) {
            oRow.style.background = "white";
        }

        function submitFormAgain(start, range){
            document.f.start.value = start;
            document.f.range.value = range;
            document.f.parseRange.value = "true";
            document.f.submit();
        }
    </script>
    <style type="text/css">
        .stat {
            margin: 0px 0px 8px 0px;
            border: 1px solid #cccccc;
            -moz-border-radius: 3px;
        }

        .stat td table {
            margin: 5px 10px 5px 10px;
        }
        .stat div.verticalrule {
            display: block;
            width: 1px;
            height: 110px;
            background-color: #cccccc;
            overflow: hidden;
            margin-left: 3px;
            margin-right: 3px;
        }

        .content {
            border-color: #bbb;
            border-style: solid;
            border-width: 0px 0px 1px 0px;
        }

        /* Default DOM Tooltip Style */
        div.domTT {
            border: 1px solid #bbb;
            background-color: #FFFBE2;
            font-family: Arial, Helvetica sans-serif;
            font-size: 9px;
            padding: 5px;
        }

        div.domTT .caption {
            font-family: serif;
            font-size: 12px;
            font-weight: bold;
            padding: 1px 2px;
            color: #FFFFFF;
        }

        div.domTT .contents {
            font-size: 12px;
            font-family: sans-serif;
            padding: 3px 2px;
        }

        .textfield {
            font-size: 11px;
            font-family: Verdana, Arial, sans-serif;
            height: 20px;
            background: #efefef;
        }

        #searchResults h3 {
            font-size: 14px;
            padding: 0px;
            margin: 0px 0px 2px 0px;
            color: #104573;
        }

        #searchResults p.resultDescription {
            margin: 0px 0px 12px 0px;
        }
    </style>
    <script type="text/javascript">
        function grayOut(ele) {
            if (ele.value == 'Any') {
                ele.style.backgroundColor = "#FFFBE2";
            }
            else {
                ele.style.backgroundColor = "#ffffff";
            }
        }
    </script>
    <script type="text/javascript" src="/js/behaviour.js"></script>
</head>
<body>
<% // Get parameters
    int start = ParamUtils.getIntParameter(request, "start", 0);
    int range = ParamUtils.getIntParameter(request, "range", webManager.getRowsPerPage("user-summary", DEFAULT_RANGE));
    String username = ParamUtils.getParameter(request, "username");
    String numa = ParamUtils.getParameter(request, "numa");
    String numb = ParamUtils.getParameter(request, "numb");
    String type = ParamUtils.getParameter(request, "type");
    String startDate = request.getParameter("startDate");
    String endDate = request.getParameter("endDate");

    String filter = null;
    if (request.getParameter("submit") != null) {

        Date fromDate = null;
        Date uptoDate = null;

        String anyText = LocaleUtils.getLocalizedString("archive.settings.any", "sip");

        if (anyText.equals(startDate)) {
            startDate = null;
        }

        if (anyText.equals(endDate)) {
            endDate = null;
        }

        if (startDate != null && startDate.length() > 0) {
            DateFormat formatter = new SimpleDateFormat("MM/dd/yy");
            try {
                fromDate = formatter.parse(startDate);
            }
            catch (Exception e) {
                // TODO: mark as an error in the JSP instead of logging..
                Log.error(e);
            }
        }

        if (endDate != null && endDate.length() > 0) {
            DateFormat formatter = new SimpleDateFormat("MM/dd/yy");
            try {
                Date date = formatter.parse(endDate);
                // The user has chosen an end date and expects that any conversation
                // that falls on that day will be included in the search results. For
                // example, say the user choose 6/17/2006 as an end date. If a conversation
                // occurs at 5:33 PM that day, it should be included in the results. In
                // order to make this possible, we need to make the end date one millisecond
                // before the next day starts.
                uptoDate = new Date(date.getTime() + JiveConstants.DAY - 1);
            }
            catch (Exception e) {
                // TODO: mark as an error in the JSP instead of logging..
                Log.error(e);
            }
        }

        filter = CallLogDAO.createSQLFilter(username, numa, numb, type, fromDate, uptoDate);
    }

    if (request.getParameter("range") != null) {
        webManager.setRowsPerPage("user-summary", range);
    }
%>

<form name="jid" action="sipark-log-summary.jsp" method="post">
    <div>
    <table class="stat">
        <tr valign="top">
            <td>
            <table cellpadding="3" cellspacing="0" border="0" width="100%">
                <tbody>
                    <tr>
                        <td align="left" width="150"><fmt:message key="call.summary.username" />:&nbsp
                        </td>
                        <td align="left">
                            <input type="text" size="20" maxlength="100" name="username" value="<%= username != null ? username : ""%>">
                        </td>
                    </tr>
                    <tr>
                        <td align="left" width="150"><fmt:message key="call.summary.from"/>:&nbsp
                        </td>
                        <td align="left">
                            <input type="text" size="20" maxlength="100" name="numa" value="<%= numa != null ? numa : ""%>">
                        </td>
                    </tr>
                    <tr>
                        <td align="left" width="150"><fmt:message key="call.summary.destination"/>:&nbsp
                        </td>
                        <td align="left">
                            <input type="text" size="20" maxlength="100" name="numb" value="<%= numb != null ? numb : ""%>">
                        </td>
                    </tr>
                    <tr>
                        <td align="left" width="150"><fmt:message key="call.summary.type"/>:&nbsp
                        </td>
                        <td align="left">
                            <select name="type" size="1">
                                <option value="all" <%= "all".equals(type) || type == null ? "selected" : ""%>><fmt:message key="call.type.all"/>
                                <option value="received" <%= "received".equals(type)? "selected" : ""%>><fmt:message key="call.type.received"/>
                                <option value="loss" <%= "missed".equals(type)? "selected" : ""%>><fmt:message key="call.type.missed"/>
                                <option value="dialed" <%= "dialed".equals(type)? "selected" : ""%>><fmt:message key="call.type.dialed"/>
                            </select>
                        </td>
                    </tr>
                </tbody>
            </table>
            </td>
            <td width="0" height="100%" valign="middle">
                <div class="verticalrule"></div>
            </td>
            <td>
                <table>
                    <tr>
                        <td colspan="3">
                            <img src="images/icon_daterange.gif" align="absmiddle" alt="" style="margin: 0px 4px 0px 2px;"/>
                            <b><fmt:message key="archive.search.daterange" /></b>
                            <a onmouseover="domTT_activate(this, event, 'content',
                                '<fmt:message key="archive.search.daterange.tooltip"/>',
                                'trail', true, 'direction', 'northeast', 'width', '220');"><img src="images/icon_help_14x14.gif" vspace="2" align="texttop"/></a>
                        </td>
                    </tr>
                    <tr valign="top">
                        <td><fmt:message key="archive.search.daterange.start" /></td>
                        <td>
                            <input type="text" id="startDate" name="startDate" size="13"
                                   value="<%= startDate != null ? startDate :
                                   LocaleUtils.getLocalizedString("archive.search.daterange.any", "sip")%>" class="textfield"/><br/>
                            <span class="jive-description"><fmt:message key="archive.search.daterange.format" /></span>
                        </td>
                        <td>
                            <img src="images/icon_calendarpicker.gif" vspace="3" id="startDateTrigger">
                        </td>
                    </tr>
                    <tr valign="top">
                        <td><fmt:message key="archive.search.daterange.end" /></td>
                        <td>
                            <input type="text" id="endDate" name="endDate" size="13"
                                   value="<%= endDate != null ? endDate :
                                   LocaleUtils.getLocalizedString("archive.search.daterange.any", "sip") %>" class="textfield"/><br/>
                            <span class="jive-description"><fmt:message key="archive.search.daterange.format" /></span>
                        </td>
                        <td>
                            <img src="images/icon_calendarpicker.gif" vspace="3" id="endDateTrigger">
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
    </div>
    <input type="hidden" name="submit" value="true">
    <input type="submit" name="get" value="<fmt:message key="call.summary.filter"/>">
</form>

<%
    if (filter != null) {
        // Get the user manager
        int logCount = CallLogDAO.getLogCount(filter);

        // paginator vars
        int numPages = (int) Math.ceil((double) logCount / (double) range);
        int curPage = (start / range) + 1;

        StringBuffer sb = new StringBuffer();
        if (username != null)
            sb.append("&username=").append(URLEncoder.encode(username, "utf-8"));
        if (numa != null)
            sb.append("&numa=").append(URLEncoder.encode(numa, "utf-8"));
        if (numb != null)
            sb.append("&numb=").append(URLEncoder.encode(numb, "utf-8"));
        if (type != null)
            sb.append("&type=").append(URLEncoder.encode(type, "utf-8"));
        if (startDate != null)
            sb.append("&startDate=").append(URLEncoder.encode(startDate, "utf-8"));
        if (endDate != null)
            sb.append("&endDate=").append(URLEncoder.encode(endDate, "utf-8"));
        String urlParams = sb.toString();
%>
<p>
    <fmt:message key="call.summary.total_calls"/>
    :
    <b><%= LocaleUtils.getLocalizedNumber(logCount) %>
    </b> --

    <% if (numPages > 1) { %>

    <fmt:message key="global.showing"/>
    <%= LocaleUtils.getLocalizedNumber(start + 1) %>-<%= LocaleUtils
        .getLocalizedNumber(start + range) %>

    <% } %>

   -<fmt:message key="call.summary.calls_per_page"/>:
    <select size="1"
            onchange="location.href='sipark-log-summary.jsp?submit=true&start=0<%= urlParams %>&range=' + this.options[this.selectedIndex].value;">

        <% for (int i = 0; i < RANGE_PRESETS.length; i++) { %>

        <option value="<%= RANGE_PRESETS[i] %>"
                <%= (RANGE_PRESETS[i] == range ? "selected" : "") %>><%= RANGE_PRESETS[i] %>
        </option>

        <% } %>

    </select>
</p>

<% if (numPages > 1) { %>

<p>
    <fmt:message key="global.pages"/>
    :
    [
    <% int num = 15 + curPage;
        int s = curPage - 1;
        if (s > 5) {
            s -= 5;
        }
        if (s < 5) {
            s = 0;
        }
        if (s > 2) {
    %>
    <a href="sipark-log-summary.jsp?submit=true&start=0&range=<%= range %><%= urlParams %>">1</a> ...

    <%
        }
        int i;
        for (i = s; i < numPages && i < num; i++) {
            String sep = ((i + 1) < numPages) ? " " : "";
            boolean isCurrent = (i + 1) == curPage;
    %>
    <a href="sipark-log-summary.jsp?submit=true&start=<%= (i*range) %>&range=<%= range %><%= urlParams %>"
       class="<%= ((isCurrent) ? "jive-current" : "") %>"
            ><%= (i + 1) %>
    </a><%= sep %>

    <% } %>

    <% if (i < numPages) { %>

    ... <a
        href="sipark-log-summary.jsp?submit=true&start=<%= ((numPages-1)*range) %>&range=<%= range %>"><%= numPages %><%= urlParams %>
</a>

    <% } %>

    ]

</p>

<% } %>

<div class="jive-table">
    <table cellpadding="0" cellspacing="0" border="0" width="100%">
        <thead>
            <tr>
                <th>&nbsp;</th>
                <th nowrap><fmt:message key="call.summary.time"/></th>
                <th nowrap><fmt:message key="call.summary.username"/></th>
                <th nowrap><fmt:message key="call.summary.from"/></th>
                <th nowrap><fmt:message key="call.summary.destination"/></th>
                <th nowrap><fmt:message key="call.summary.duration"/></th>
                <th nowrap><fmt:message key="call.summary.type"/></th>
            </tr>
        </thead>
        <tbody>

            <% // Print the list of users
                Collection<CallLog> calls = CallLogDAO.getCalls(filter, start, range);
                if (calls.isEmpty()) {
            %>
            <tr>
                <td align="center" colspan="7">
                    <fmt:message key="call.summary.no-entries"/>
                </td>
            </tr>

            <%
                }
                int i = start;
                for (CallLog call : calls) {
                    i++;
            %>
            <tr class="jive-<%= (((i%2)==0) ? "even" : "odd") %>">
                <td width="1%">
                    <%= i %>
                </td>
                <td width="30%">
                    <%=JiveGlobals.formatDateTime(new Date(call.getDateTime()))%>
                </td>
                <td width="10%" align="center" valign="middle">
                    <%=call.getUsername()%>&nbsp;
                </td>
                <td width="20%" align="left">
                    <%=call.getNumA()%>&nbsp;
                </td>
                <td width="20%" align="left">
                    <%=call.getNumB()%>&nbsp;
                </td>
                <td width="5%" align="left">
                    <%=call.getDuration()%>
                </td>
                <td width="5%" align="left">
                    <%=call.getType()%>
                </td>
            </tr>

            <%
                }
            %>
        </tbody>
    </table>
</div>

<% if (numPages > 1) { %>

<p>
    <fmt:message key="global.pages"/>
    :
    [
    <% int num = 15 + curPage;
        int s = curPage - 1;
        if (s > 5) {
            s -= 5;
        }
        if (s < 5) {
            s = 0;
        }
        if (s > 2) {
    %>
    <a href="sipark-log-summary.jsp?submit=true&start=0&range=<%= range %><%= urlParams %>">1</a> ...

    <%
        }
        for (i = s; i < numPages && i < num; i++) {
            String sep = ((i + 1) < numPages) ? " " : "";
            boolean isCurrent = (i + 1) == curPage;
    %>
    <a href="sipark-log-summary.jsp?submit=true&start=<%= (i*range) %>&range=<%= range %><%= urlParams %>"
       class="<%= ((isCurrent) ? "jive-current" : "") %>"
            ><%= (i + 1) %>
    </a><%= sep %>

    <% } %>

    <% if (i < numPages) { %>

    ... <a
        href="sipark-log-summary.jsp?submit=true&start=<%= ((numPages-1)*range) %>&range=<%= range %>"><%= numPages %><%= urlParams %>
</a>

    <% } %>

    ]

</p>

<% } } %>

<script type="text/javascript">
    grayOut(jid.startDate);
    grayOut(jid.endDate);

     function catcalc(cal) {
        var endDateField = $('endDate');
        var startDateField = $('startDate');

        var endTime = new Date(endDateField.value);
        var startTime = new Date(startDateField.value);
        if(endTime.getTime() < startTime.getTime()){
            alert("<fmt:message key="archive.search.daterange.error" />");
            startDateField.value = "<fmt:message key="archive.search.daterange.any" />";
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
