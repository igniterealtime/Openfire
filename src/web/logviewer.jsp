<%@ taglib uri="core" prefix="c"%><%
/**
 *	$RCSfile$
 *	$Revision$
 *	$Date$
 */
%>

<%@ page import="java.io.*,
                 org.jivesoftware.util.*,
                 java.text.*,
                 org.jivesoftware.util.log.Logger,
                 org.jivesoftware.messenger.auth.UnauthorizedException,
                 org.jivesoftware.messenger.JiveGlobals,
                 org.jivesoftware.messenger.user.*,
                 java.util.*"
%>
<!-- Define Administration Bean -->
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>

<!-- Define BreadCrumbs -->
<c:set var="title" value="Log Viewer"  />
<c:set var="breadcrumbs" value="${admin.breadCrumbs}"  />
<c:set var="log" value="${param.log}" />
<c:set target="${breadcrumbs}" property="Home" value="main.jsp" />
<c:set target="${breadcrumbs}" property="${title}" value="logviewer.jsp?log=${log}" />
<%@ include file="top.jsp" %>

<%
String log = ParamUtils.getParameter(request, "log");
%>
<%!
    
    static final String ERROR = "error";
    static final String INFO = "info";
    static final String WARN = "warn";
    static final String DEBUG = "debug";
    static final String DEFAULT = ERROR;

    static final String ASCENDING = "asc";
    static final String DESCENDING = "desc";

    static final String[] LINES = {"50","100","250","500"};

    static final String[] REFRESHES = {"None","10","30","60","90"};

    // Days of the week
    private static final String[] DAYS_OF_WEEK =
            {"Sun","Mon","Tues","Wed","Thurs","Fri","Sat"};
    // Months of the year
    private static final String[] MONTHS_OF_YEAR =
            {"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug",
             "Sep","Oct","Nov","Dec"};

    static final SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd kk:mm");
    static Map dateFormatCache = new HashMap();

    static Calendar globalCal = Calendar.getInstance();

    public static TimeZone getTimeZone(HttpServletRequest request, User user) {
        TimeZone timeZone = JiveGlobals.getTimeZone();
        String timeZoneID = null;
        if (user != null) {
            timeZoneID = user.getProperty("jiveTimeZoneID");
        }
        else if (request != null) {
            Cookie cookie = getCookie(request, "jiveTimeZoneID");
            if (cookie != null) {
                timeZoneID = cookie.getValue();
            }
        }
        if (timeZoneID != null) {
            timeZone = TimeZone.getTimeZone(timeZoneID);
        }
        return timeZone;
    }

    public static String formatDate(HttpServletRequest request, User user, Date date) {
        Locale locale = JiveGlobals.getLocale();
        TimeZone timeZone = getTimeZone(request, user);
        // See if the date is today.
        // Check the cache of DateFormat objects:
        String key = locale.toString() + timeZone.getID();
        DateFormat formatter = (DateFormat)dateFormatCache.get(key);
        if (formatter == null) {
            formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM,
                    DateFormat.SHORT, locale);
            formatter.setTimeZone(timeZone);
            dateFormatCache.put(key,formatter);
        }
        // DateFormat objects are not thread safe, so we must synchronize on it.
        synchronized(formatter) {
            return formatter.format(date);
        }
    }

    public static String dateToText(HttpServletRequest request,
            User user, Date date)
    {
        if (date == null) {
            return "";
        }
        // Time difference between now and the parameter object's time. Using
        // the cache timer means the resolution is no better than a seconds,
        // for for our purposes, we don't need better.
        long delta = System.currentTimeMillis() - date.getTime();

        // Within the last hour
        if ((delta / JiveConstants.HOUR) < 1) {
            long minutes = (delta/JiveConstants.MINUTE);
            if (minutes == 0) {
                return "Less than 1 min ago";
            }
            else if (minutes == 1) {
                return "1 minute ago";
            }
            else {
                return (minutes + " minutes ago");
            }
        }

        // Sometime today
        if ((delta / JiveConstants.DAY) < 1) {
            long hours = (delta/JiveConstants.HOUR);
            if(hours <= 1) {
                return "1 hour ago";
            }
            else {
                return (hours + " hours ago");
            }
        }

        int hour = -1;
        int minute = -1;
        int am_pm = -1;
        int day_of_month = -1;
        int month = -1;
        int day_of_the_week = -1;

        synchronized (globalCal) {
            globalCal.setTime(date);
            globalCal.setTimeZone(getTimeZone(request, user));
            hour = globalCal.get(Calendar.HOUR);
            minute = globalCal.get(Calendar.MINUTE);
            am_pm = globalCal.get(Calendar.AM_PM);
            day_of_the_week = globalCal.get(Calendar.DAY_OF_WEEK);
            day_of_month = globalCal.get(Calendar.DAY_OF_MONTH);
            month = globalCal.get(Calendar.MONTH);
        }

        // Within the last week
        if ((delta / JiveConstants.WEEK) < 1) {
            long days = (delta/JiveConstants.DAY);
            if (days <= 1) {
                StringBuffer buf = new StringBuffer("Yesterday, ");
                if (am_pm == 1 && hour == 0) {
                    buf.append(12).append(":");
                } else {
                    buf.append(hour).append(":");
                }
                if (minute < 10) {
                    buf.append("0").append(minute);
                } else {
                    buf.append(minute);
                }
                buf.append(" ");
                if (am_pm == 0) {
                    buf.append("AM");
                } else {
                    buf.append("PM");
                }
                return buf.toString();
            }
            else {
                StringBuffer buf = new StringBuffer();
                buf.append(DAYS_OF_WEEK[day_of_the_week-1]);
                buf.append(", ").append(MONTHS_OF_YEAR[month]);
                buf.append(" ").append(day_of_month).append(" ");
                if (am_pm == 1 && hour == 0) {
                    buf.append(12).append(":");
                } else {
                    buf.append(hour).append(":");
                }
                if (minute < 10) {
                    buf.append("0").append(minute);
                } else {
                    buf.append(minute);
                }
                buf.append(" ");
                if (am_pm == 0) {
                    buf.append("AM");
                } else {
                    buf.append("PM");
                }
                return buf.toString();
            }
        }

        // More than a week ago.
        else {
            return formatDate(request, user, date);
        }
    }

    private static final String parseDate(HttpServletRequest request, User pageUser, String input) {
        if (input == null || "".equals(input)) {
            return input;
        }
        if (input.length() < 16) {
            return input;
        }
        String d = input.substring(0,16);
        // try to parse it
        try {
            Date date = formatter.parse(d);
            StringBuffer buf = new StringBuffer(input.length());
            buf.append("<span class=\"date\" title=\"").append(dateToText(request,pageUser,date)).append("\">");
            buf.append(d).append("</span>");
            buf.append(input.substring(16,input.length()));
            return buf.toString();
        }
        catch (ParseException pe) {
            return input;
        }
    }

    private static final String hilite(HttpServletRequest request, User pageUser, String input) {
        if (input == null || "".equals(input)) {
            return input;
        }
        if (input.indexOf("org.jivesoftware.") > -1) {
            StringBuffer buf = new StringBuffer();
            buf.append("<span class=\"hilite\">").append(input).append("</span>");
            return buf.toString();
        }
        return input;
    }

    private static HashMap parseCookie(Cookie cookie) {
        if (cookie == null || cookie.getValue() == null) {
            HashMap empty = new HashMap();
            return empty;
        }
        StringTokenizer tokenizer = new StringTokenizer(cookie.getValue(),"&");
        HashMap valueMap = new HashMap();
        while (tokenizer.hasMoreTokens()) {
            String tok = tokenizer.nextToken();
            int pos = tok.indexOf("=");
            String name = tok.substring(0,pos);
            String value = tok.substring(pos+1,tok.length());
            valueMap.put(name,value);
        }
        return valueMap;
    }

    public static Cookie getCookie(HttpServletRequest request, String name) {
        Cookie cookies[] = request.getCookies();
        // Return null if there are no cookies or the name is invalid.
        if (cookies == null || name == null || name.length() == 0) {
            return null;
        }
        // Otherwise, we  do a linear scan for the cookie.
        Cookie cookie = null;
        for (int i = 0; i < cookies.length; i++) {
            // If the current cookie name matches the one we're looking for, we've
            // found a matching cookie.
            if (cookies[i].getName().equals(name)) {
                cookie = cookies[i];
                // The best matching cookie will be the one that has the correct
                // domain name. If we've found the cookie with the correct domain name,
                // return it. Otherwise, we'll keep looking for a better match.
                if (request.getServerName().equals(cookie.getDomain())) {
                    break;
                }
            }
        }
        return cookie;
    }

    private static void saveCookie(HttpServletResponse response, HashMap cookie) {
        StringBuffer buf = new StringBuffer();
        for (Iterator iter=cookie.keySet().iterator(); iter.hasNext();) {
            String name = (String)iter.next();
            String value = (String)cookie.get(name);
            buf.append(name).append("=").append(value);
            if (iter.hasNext()) {
                buf.append("&");
            }
        }
        Cookie newCookie = new Cookie("jiveforums.admin.logviewer",buf.toString());
        newCookie.setPath("/");
        newCookie.setMaxAge(60*60*24*30); // one month
        response.addCookie(newCookie);
    }

    private static HashMap getLogUpdate(HttpServletRequest request, HttpServletResponse response,
            File logDir)
    {
        // Get the cookie associated with the log files
        HashMap cookie = parseCookie(getCookie(request,"jiveforums.admin.logviewer"));
        String[] logs = {"error", "info", "warn", "debug"};
        HashMap newCookie = new HashMap();
        HashMap updates = new HashMap();
        for (int i=0; i<logs.length; i++) {
            // Check for the value in the cookie:
            String key = logs[i] + ".size";
            long savedSize = 0L;
            if (cookie.containsKey(key)) {
                try {
                    savedSize = Long.parseLong((String)cookie.get(key));
                }
                catch (NumberFormatException nfe) {}
            }
            // Update the size in the Map:
            File logFile = new File(logDir, "jive." + logs[i] + ".log");
            long currentSize = 0;
            if (logFile.exists()){
                currentSize = logFile.length();
            }
            newCookie.put(key, ""+currentSize);
            if (currentSize != savedSize) {
                updates.put(logs[i], "true");
            }
        }
        saveCookie(response, newCookie);
        return updates;
    }
%>

<%  

    // Get parameters
    String numLinesParam = ParamUtils.getParameter(request,"lines");
    int numLines = ParamUtils.getIntParameter(request,"lines",50);
    int refresh = ParamUtils.getIntParameter(request,"refresh",10);
    String refreshParam = ParamUtils.getParameter(request,"refresh");
    boolean save = ParamUtils.getBooleanParameter(request,"save");
    String mode = ParamUtils.getParameter(request,"mode");
    boolean debugEnabled = ParamUtils.getBooleanParameter(request,"debugEnabled");
    boolean wasDebugEnabled = ParamUtils.getBooleanParameter(request,"wasDebugEnabled");
    boolean debugAlert = ParamUtils.getBooleanParameter(request,"debugAlert");

    // Enable/disable debugging
    if (request.getParameter("wasDebugEnabled") != null && wasDebugEnabled != debugEnabled) {
        JiveGlobals.setProperty("log.debug.enabled",String.valueOf(debugEnabled));
        response.sendRedirect("logviewer.jsp?log=debug&debugAlert=true");
        return;
    }

    debugEnabled = "true".equals(JiveGlobals.getProperty("log.debug.enabled"));

    // Set defaults
    if (log == null) {
        log = DEFAULT;
    }
    if (mode == null) {
        mode = ASCENDING;
    }
    if (numLinesParam == null) {
        numLinesParam = "50";
    }

    // Other vars
    File logDir = admin.getContainer().getModuleContext().getLogDirectory();
    String filename = "jive." + log + ".log";
    File logFile = new File(logDir, filename);
    boolean tooBig = false;
    if (logFile.exists()){
        tooBig = (logFile.length() / (1024)) > 250;
    }

    // Determine if any of the log files contents have been updated:
    HashMap newlogs = getLogUpdate(request, response, logDir);

    String line = null;
    int totalNumLines = 0;

    if (logFile.exists()){
        BufferedReader in = new BufferedReader(new FileReader(logFile));
        while ((line=in.readLine()) != null) {
            totalNumLines++;
        }
        in.close();
        // adjust the 'numLines' var to match totalNumLines if 'all' was passed in:
        if ("All".equals(numLinesParam)) {
            numLines = totalNumLines;
        }
    }
    else {
        numLines = 0;
    }
    String[] lines = new String[numLines];
    int start = totalNumLines - numLines;
    if (logFile.exists()){
        BufferedReader in = new BufferedReader(new FileReader(logFile));
        // skip lines
        if (start < 0) { start = 0; }
        for (int j=0; j<start; j++) {
            in.readLine();
        }
        int i = 0;
        if (ASCENDING.equals(mode)) {
            while ((line=in.readLine()) != null && i<numLines) {
                line = parseDate(request, admin.getUser(), line);
                line = hilite(request, admin.getUser(), line);
                lines[i] = line;
                i++;
            }
        }
        else {
            int end = lines.length-1;
            while ((line=in.readLine()) != null && i<numLines) {
                line = parseDate(request, admin.getUser(), line);
                line = hilite(request, admin.getUser(), line);
                lines[end-i] = line;
                i++;
            }
        }
        numLines = start + i;
    }
%>

<%@ include file="header.jsp" %>

<%  if (refreshParam != null && !"None".equals(refreshParam)) { %>

    <meta http-equiv="refresh" content="<%= refresh %>">

<%  } %>

<%  if (debugAlert) { %>

    <script language="JavaScript" type="text/javascript">
    alert('Your change to the debug logging will go into affect after you restart your appserver.');
    </script>

<%  } %>



<style type="text/css">
.log TABLE {
    border : 1px #ccc solid;
}
.log TH {
    font-family : verdana, arial;
    font-weight : bold;
    font-size : 0.7em;
}
.log TR TH {
    background-color : #ddd;
    border-bottom : 1px #ccc solid;
    padding-left : 2px;
    padding-right : 2px;
    text-align : left;
}
.log .head-num {
    border-right : 1px #ccc solid;
}
.log TD {
    font-family : courier new;
    font-size : 0.75em;
    background-color : #ffe;
}
.log .num {
    width : 1%;
    background-color : #eee !important;
    border-right : 1px #ccc solid;
    padding-left : 2px;
    padding-right : 2px;
}
.log .line {
    padding-left : 10px;
}
.container {
    border-width : 0px 1px 1px 1px;
    border-color : #ccc;
    border-style : solid;
}
.info TD {
    font-family : verdana, arial;
    font-size : 0.7em;
}
SELECT {
    font-family : verdana, arial;
    font-size : 0.8em;
}
.info .label {
    padding-right : 6px;
}
.date {
    color : #00f;
    border-width : 0px 0px 1px 0px;
    border-style : dotted;
    border-color : #00f;
}
.new {
    font-family : courier new;
    font-weight : bold;
    color : #600;
}
.hilite {
    color : #900;
}
</style>

<br>
<form action="logviewer.jsp">

<input type="hidden" name="log" value="<%= log %>">

<table class="jive-tabs" cellpadding="0" cellspacing="0" border="0">
<tr>
    <td class="jive-tab-spacer" width="1%"><img src="images/blank.gif" width="5" height="1" border="0"></td>
    <td class="jive-<%= (("error".equals(log))?"selected-":"") %>tab" width="1%" nowrap>
        <a href="logviewer.jsp?log=error"
        >Error</a>
        <span class="new">
        <%= ((newlogs.containsKey("error"))?"*":"") %>
        </span>
    </td>
    <td class="jive-tab-spacer" width="1%"><img src="images/blank.gif" width="5" height="1" border="0"></td>
    <td class="jive-<%= (("info".equals(log))?"selected-":"") %>tab" width="1%" nowrap>
        <a href="logviewer.jsp?log=info"
        >Info</a>
        <span class="new">
        <%= ((newlogs.containsKey("info"))?"*":"") %>
        </span>
    </td>
    <td class="jive-tab-spacer" width="1%"><img src="images/blank.gif" width="5" height="1" border="0"></td>
    <td class="jive-<%= (("warn".equals(log))?"selected-":"") %>tab" width="1%" nowrap>
        <a href="logviewer.jsp?log=warn"
        >Warn</a>
        <span class="new">
        <%= ((newlogs.containsKey("warn"))?"*":"") %>
        </span>
    </td>
    <td class="jive-tab-spacer" width="1%"><img src="images/blank.gif" width="5" height="1" border="0"></td>
    <td class="jive-<%= (("debug".equals(log))?"selected-":"") %>tab" width="1%" nowrap>
        <a href="logviewer.jsp?log=debug"
        >Debug</a>
        <span class="new">
        <%= ((newlogs.containsKey("debug"))?"*":"") %>
        </span>
    </td>
    <td class="jive-tab-spring" width="92%" align="right" nowrap>
        &nbsp;
    </td>
</tr>
</table>
<table class="container" cellpadding="6" cellspacing="0" border="0" width="100%">
<tr><td>

    <span class="info">
    <table cellpadding="2" cellspacing="0" border="0" width="100%">
    <tr><td colspan="5"><img src="images/blank.gif" width="1" height="4" border="0"></td></tr>
    <tr>
        <td class="label" width="1%">Filename:</td>
        <td width="1%" nowrap><b><%= logFile.getName() %></b></td>
        <td rowspan="3" width="96%">&nbsp;</td>
        <td class="label" width="1%">Order:</td>
        <td width="1%" nowrap>
            <input type="radio" name="mode" value="desc"<%= ("desc".equals(mode)?" checked":"") %>
             onclick="this.form.submit();"
             id="rb01"
             > <label for="rb01">Newest at top</label>
            <input type="radio" name="mode" value="asc"<%= ("asc".equals(mode)?" checked":"") %>
             onclick="this.form.submit();"
             id="rb02"
             > <label for="rb02">Newest at bottom</label>
        </td>
    </tr>
    <tr>
        <td class="label" width="1%" nowrap>Last Modified:</td>
        <%  Date lastMod = new Date(logFile.lastModified());
            DateFormat dateFormatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        %>
        <td width="1%" nowrap><span title"<%= dateToText(request, admin.getUser(), lastMod) %>"><%= dateFormatter.format(lastMod) %></span></td>
        <td class="label" width="1%">Lines:</td>
        <td width="1%" nowrap>
            <select name="lines" size="1"
             onchange="this.form.submit();">
                <%  for (int j=0; j<LINES.length; j++) {
                        String selected = (LINES[j].equals(numLinesParam))?" selected":"";
                %>
                    <option value="<%= LINES[j] %>"<%= selected %>><%= LINES[j] %>

                <%  } %>
                <%  if (!tooBig) { %>
                    <option value="All"<%= (("All".equals(numLinesParam))?" selected":"") %>
                     >All
                <%  } %>
            </select>
        </td>
    </tr>
    <tr>
        <td class="label" width="1%">Size:</td>
        <%  ByteFormat byteFormatter = new ByteFormat(); %>
        <td width="1%" nowrap><%= byteFormatter.format(logFile.length()) %></td>
        <td class="label" width="1%">Refresh:</td>
        <td width="1%" nowrap>
            <select size="1" name="refresh" onchange="this.form.submit();">
            <%  for (int j=0; j<REFRESHES.length; j++) {
                    String selected = REFRESHES[j].equals(refreshParam)?" selected":"";
            %>
                <option value="<%= REFRESHES[j] %>"<%= selected %>><%= REFRESHES[j] %>

            <%  } %>
            </select>
            (seconds)
        </td>
    </tr>

    <%  // Print out a special switch to enable/disable debugging
        if ("debug".equals(log)) {
    %>
        <input type="hidden" name="wasDebugEnabled" value="<%= debugEnabled %>">
        <tr valign="top">
            <td class="label" width="1%">Debugging Enabled:</td>
            <td width="1%" nowrap>

                <input type="radio" name="debugEnabled" value="true"<%= ((debugEnabled) ? " checked" : "") %> id="de01">
                <label for="de01">Enabled</label>

                <input type="radio" name="debugEnabled" value="false"<%= ((!debugEnabled) ? " checked" : "") %> id="de02">
                <label for="de02">Disabled</label>

                (change requires restart)

                <br>

                <input type="submit" name="" value="Update">
            </td>
            <td colspan="3">&nbsp;</td>
        </tr>

    <%  } %>

    <tr><td colspan="5"><img src="images/blank.gif" width="1" height="8" border="0"></td></tr>
    </table>
    </span>

</td></tr>
</table>

<br>

<span class="log">
<table cellpadding="1" cellspacing="0" border="0" width="100%">
<tr>
    <th class="head-num">line</th>
    <th>message</th>
</tr>
<tr>
    <td width="1%" nowrap class="num">
        <%  if (ASCENDING.equals(mode)) { %>
            <%  for (int j=start+1; j<=numLines; j++) { %>
                <%= j %><br>
            <%  } %>
        <%  } else { %>
            <%  for(int j=numLines; j>=start+1; j--) { %>
                <%= j %><br>
            <%  } %>
        <%  } %>
    </td>
    <td width="99%" class="line">
        <%  for (int j=0; j<lines.length; j++) {
                if (lines[j] != null) {
        %>
            <nobr><%= lines[j] %></nobr><br>

        <%      }
            }
        %>
    </td>
</tr>
</table>
</span>

</form>

<%@ include file="bottom.jsp" %>