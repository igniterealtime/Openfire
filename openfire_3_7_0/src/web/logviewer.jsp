<%--
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

<%@ page import="java.io.*,
                 org.jivesoftware.util.*,
                 java.text.*,
                 org.jivesoftware.util.JiveGlobals,
                 org.jivesoftware.openfire.user.*,
                 java.util.*"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />

<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>

<%!
    static final String NONE = LocaleUtils.getLocalizedString("global.none");

    static final String ERROR = "error";
    static final String INFO = "info";
    static final String WARN = "warn";
    static final String DEBUG = "debug";
    static final String DEFAULT = ERROR;

    static final String ASCENDING = "asc";
    static final String DESCENDING = "desc";

    static final String[] LINES = {"50","100","250","500"};

    static final String[] REFRESHES = {NONE,"10","30","60","90"};

    private static HashMap parseCookie(Cookie cookie) {
        if (cookie == null || cookie.getValue() == null) {
            return new HashMap();
        }
        StringTokenizer tokenizer = new StringTokenizer(cookie.getValue(),"&");
        HashMap<String, String> valueMap = new HashMap<String, String>();
        while (tokenizer.hasMoreTokens()) {
            String tok = tokenizer.nextToken();
            int pos = tok.indexOf("=");
            if (pos > 0) {
                String name = tok.substring(0,pos);
                String value = tok.substring(pos+1,tok.length());
                valueMap.put(name,value);
            }
        }
        return valueMap;
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
        HashMap cookie = parseCookie(CookieUtils.getCookie(request,"jiveforums.admin.logviewer"));
        String[] logs = {"error", "info", "warn", "debug"};
        HashMap<String,String> newCookie = new HashMap<String,String>();
        HashMap<String,String> updates = new HashMap<String,String>();
        for (String log : logs) {
            // Check for the value in the cookie:
            String key = log + ".size";
            long savedSize = 0L;
            if (cookie.containsKey(key)) {
                try {
                    savedSize = Long.parseLong((String) cookie.get(key));
                }
                catch (NumberFormatException nfe) {
                }
            }
            // Update the size in the Map:
            File logFile = new File(logDir, log + ".log");
            long currentSize = logFile.length();
            newCookie.put(key, "" + currentSize);
            if (currentSize != savedSize) {
                updates.put(log, "true");
            }
        }
        saveCookie(response, newCookie);
        return updates;
    }
%>

<%
    // Get parameters
    String log = ParamUtils.getParameter(request, "log");
    String numLinesParam = ParamUtils.getParameter(request,"lines");
    int numLines = ParamUtils.getIntParameter(request,"lines",50);
    int refresh = ParamUtils.getIntParameter(request,"refresh",10);
    String refreshParam = ParamUtils.getParameter(request,"refresh");
    String mode = ParamUtils.getParameter(request,"mode");
    boolean clearLog = ParamUtils.getBooleanParameter(request,"clearLog");
    boolean markLog = ParamUtils.getBooleanParameter(request,"markLog");
    boolean saveLog = ParamUtils.getBooleanParameter(request,"saveLog");
    boolean emailLog = ParamUtils.getBooleanParameter(request,"emailLog");
    boolean debugEnabled = ParamUtils.getBooleanParameter(request,"debugEnabled");
    boolean wasDebugEnabled = ParamUtils.getBooleanParameter(request,"wasDebugEnabled");

    // Enable/disable debugging
    if (request.getParameter("wasDebugEnabled") != null && wasDebugEnabled != debugEnabled) {
        Log.setDebugEnabled(debugEnabled);
        // Log the event
        admin.logEvent((debugEnabled ? "enabled" : "disabled")+" debug logging", null);
        response.sendRedirect("logviewer.jsp?log=debug");
        return;
    }

    // Santize variables to prevent vulnerabilities
    if (log != null) {
        log = StringUtils.escapeHTMLTags(log);
    }
    debugEnabled = Log.isDebugEnabled();
    User pageUser = admin.getUser();

    if (clearLog && log != null) {
        if ("error".equals(log)) {
            Log.rotateErrorLogFile();
        }
        else if ("warn".equals(log)) {
            Log.rotateWarnLogFile();
        }
        else if ("info".equals(log)) {
            Log.rotateInfoLogFile();
        }
        else if ("debug".equals(log)) {
            Log.rotateDebugLogFile();
        }
        response.sendRedirect("logviewer.jsp?log=" + log);
        return;
    }
    else if (markLog && log != null) {
        if ("error".equals(log)) {
            Log.markErrorLogFile(pageUser.getUsername());
        }
        else if ("warn".equals(log)) {
            Log.markWarnLogFile(pageUser.getUsername());
        }
        else if ("info".equals(log)) {
            Log.markInfoLogFile(pageUser.getUsername());
        }
        else if ("debug".equals(log)) {
            Log.markDebugLogFile(pageUser.getUsername());
        }
        response.sendRedirect("logviewer.jsp?log=" + log);
        return;
    }
    else if (saveLog && log != null) {
        saveLog = false;
        response.sendRedirect(request.getContextPath() + "/servlet/JiveServlet/?log=" + log);
        return;
    }
    else if (emailLog && log != null) {
        response.sendRedirect("emaillog.jsp?log=" + log);
        return;
    }

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
    File logDir = new File(Log.getLogDirectory());
    String filename = log + ".log";
    File logFile = new File(logDir, filename);

    // Determine if any of the log files contents have been updated:
    HashMap newlogs = getLogUpdate(request, response, logDir);
%>

<html>
    <head>
        <title><fmt:message key="logviewer.title"/></title>
        <meta name="pageID" content="server-logs"/>
        <meta name="helpPage" content="use_the_server_logs.html"/>
    </head>
    <body>

<%  if (refreshParam != null && !NONE.equals(refreshParam)) { %>
    <meta http-equiv="refresh" content="<%= refresh %>">
<%  } %>

<div id="logviewer">

<style type="text/css">
SELECT, INPUT {
    font-family : verdana, arial, sans-serif;
    font-size : 8pt;
}
.date {
    color : #00f;
    border-width : 0 0 1px 0;
    border-style : dotted;
    border-color : #00f;
}
.buttons TD {
    padding : 3px;
}
.buttons .icon-label {
    padding-right : 1em;
}
.log-info {
    border-width : 0 1px 1px 1px;
    border-color : #ccc;
    border-style : solid;
}
IFRAME {
    border : 1px #666 solid;
}
</style>

<form action="logviewer.jsp" name="logViewer" method="get">
<input type="hidden" name="log" value="<%= log %>">

<div class="logviewer">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<tbody>
    <tr>
        <td class="jive-spacer" width="1%">&nbsp;</td>
        <td class="jive-tab<%= (("error".equals(log))?"-active":"") %>" width="1%">
            <a href="logviewer.jsp?log=error"
            ><fmt:message key="logviewer.error" /></a>
            <span class="new">
            <%= ((newlogs.containsKey("error"))?"*":"") %>
            </span>
        </td>
        <td class="jive-spacer" width="1%">&nbsp;</td>
        <td class="jive-tab<%= (("warn".equals(log))?"-active":"") %>" width="1%">
            <a href="logviewer.jsp?log=warn"
            ><fmt:message key="logviewer.warn" /></a>
            <span class="new">
            <%= ((newlogs.containsKey("warn"))?"*":"") %>
            </span>
        </td>
        <td class="jive-spacer" width="1%">&nbsp;</td>
        <td class="jive-tab<%= (("info".equals(log))?"-active":"") %>" width="1%">
            <a href="logviewer.jsp?log=info"
            ><fmt:message key="logviewer.info" /></a>
            <span class="new">
            <%= ((newlogs.containsKey("info"))?"*":"") %>
            </span>
        </td>
        <td class="jive-spacer" width="1%">&nbsp;</td>
        <td class="jive-tab<%= (("debug".equals(log))?"-active":"") %>" width="1%">
            <a href="logviewer.jsp?log=debug"
            ><fmt:message key="logviewer.debug" /></a>
            <span class="new">
            <%= ((newlogs.containsKey("debug"))?"*":"") %>
            </span>
        </td>
        <td class="jive-stretch" width="92%" align="right" nowrap>
            &nbsp;
        </td>
    </tr>
</tbody>
</table>
</div>

<%  ByteFormat byteFormatter = new ByteFormat();
    Date lastMod = new Date(logFile.lastModified());
    DateFormat dateFormatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);
%>

<div class="log-info">
<table cellpadding="6" cellspacing="0" border="0" width="100%">
<tbody>
    <tr>
        <td>
            <table cellpadding="3" cellspacing="0" border="0" width="100%">
            <tr>
                <td nowrap><fmt:message key="logviewer.log" /></td>
                <td nowrap><b><%= logFile.getName() %></b> (<%= byteFormatter.format(logFile.length()) %>)</td>
                <td width="96%" rowspan="3">&nbsp;</td>
                <td nowrap><fmt:message key="logviewer.order" /></td>
                <td nowrap>
                    <input type="radio" name="mode" value="asc"<%= ("asc".equals(mode)?" checked":"") %>
                     onclick="this.form.submit();" id="rb01"
                     ><label for="rb01"><fmt:message key="logviewer.normal" /></label>
                    <input type="radio" name="mode" value="desc"<%= ("desc".equals(mode)?" checked":"") %>
                     onclick="this.form.submit();" id="rb02"
                     ><label for="rb02"><fmt:message key="logviewer.reverse" /></label>
                </td>
            </tr>
            <tr>
                <td nowrap><fmt:message key="logviewer.modified" /></td>
                <td nowrap>
                    <span><%= dateFormatter.format(lastMod) %></span>
                </td>
                <td nowrap><fmt:message key="logviewer.line" /></td>
                <td nowrap>
                    <select name="lines" size="1"
                     onchange="this.form.submit();">
                        <% for (String aLINES : LINES) {
                            String selected = (aLINES.equals(numLinesParam)) ? " selected" : "";
                        %>
                        <option value="<%= aLINES %>"<%= selected %>><%= aLINES %></option>

                        <%  } %>
                            <option value="All"<%= (("All".equals(numLinesParam))?" selected":"") %>
                             ><fmt:message key="logviewer.all" /></option>
                    </select>
                </td>
            </tr>
            <tr>
                <td colspan="2">
                    <script language="JavaScript" type="text/javascript">
                        <!--
                        function setLog(log) {
                            document.logViewer.clearLog.value = 'false';
                            document.logViewer.markLog.value = 'false';
                            document.logViewer.saveLog.value = 'false';
                            document.logViewer.emailLog.value = 'false';

                            var t = eval("document.logViewer." + log);
                            t.value = 'true';
                        }
                        // -->
                    </script>
                    <input type="hidden" name="clearLog" value="false">
                    <input type="hidden" name="markLog" value="false">
                    <input type="hidden" name="saveLog" value="false">
                    <input type="hidden" name="emailLog" value="false">
                    <div class="buttons">
                    <table cellpadding="0" cellspacing="0" border="0">
                    <tbody>
                        <tr>
                            <td class="icon">
                                <a href="#" onclick="if (confirm('<fmt:message key="logviewer.confirm" />')) {setLog('clearLog'); document.logViewer.submit(); return true;} else { return false; }"><img src="images/delete-16x16.gif" border="0" alt="<fmt:message key="logviewer.alt_clear" />"></a>
                            </td>
                            <td class="icon-label">
                                <a href="#" onclick="if (confirm('<fmt:message key="logviewer.confirm" />')) {setLog('clearLog'); document.logViewer.submit(); return true;} else { return false; }"
                                 ><fmt:message key="logviewer.clear" /></a>
                            </td>
                            <td class="icon">
                                <a href="#" onclick="setLog('markLog'); document.logViewer.submit(); return true;"><img src="images/mark-16x16.gif" border="0" alt="<fmt:message key="logviewer.alt_mark" />"></a>
                            </td>
                            <td class="icon-label">
                                <a href="#" onclick="setLog('markLog'); document.logViewer.submit(); return true;"
                                 ><fmt:message key="logviewer.mark" /></a>
                            </td>
                        </tr>
                    </tbody>
                    </table>
                    </div>
                </td>
                <td nowrap><fmt:message key="global.refresh" />:</td>
                <td nowrap>
                    <select size="1" name="refresh" onchange="this.form.submit();">
                    <% for (String aREFRESHES : REFRESHES) {
                        String selected = aREFRESHES.equals(refreshParam) ? " selected" : "";
                    %>
                        <option value="<%= aREFRESHES %>"<%= selected %>><%= aREFRESHES %>

                    <%  } %>
                    </select>
                    (<fmt:message key="global.seconds" />)
                </td>
            </tr>

            <%  if ("debug".equals(log)) { %>

                <tr>
                    <td colspan="5">

                        <table cellpadding="0" cellspacing="0" border="0" width="100%">
                        <tr>
                            <td width="1%" nowrap>
                                <fmt:message key="logviewer.debug_log" />: &nbsp;
                            </td>
                            <td width="1%">
                                <input type="radio" name="debugEnabled" value="true"<%= ((debugEnabled) ? " checked" : "") %> id="de01">
                            </td>
                            <td width="1%" nowrap>
                                <label for="de01"><fmt:message key="logviewer.enabled" /></label> &nbsp;
                            </td>
                            <td width="1%">
                                <input type="radio" name="debugEnabled" value="false"<%= ((!debugEnabled) ? " checked" : "") %> id="de02">
                            </td>
                            <td width="1%" nowrap>
                                <label for="de02">Disabled</label> &nbsp;
                            </td>
                            <td width="1%">
                                <input type="hidden" name="wasDebugEnabled" value="<%= debugEnabled %>">
                                <input type="submit" name="" value="<fmt:message key="global.save_changes" />">
                            </td>
                            <td width="94%">&nbsp;</td>
                        </tr>
                        </table>
                    </td>
                </tr>

            <%  } %>

            </table>
        </td>
    </tr>
</tbody>
</table>
</div>

<br>

<span class="jive-description" style="color:#666;">
<fmt:message key="logviewer.log_dir" />: <%= JiveGlobals.getHomeDirectory() %><%= File.separator %>logs
</span>

<br><br>

<iframe src="log.jsp?log=<%= log %>&mode=<%= mode %>&lines=<%= ("All".equals(numLinesParam) ? "All" : String.valueOf(numLines)) %>"
    frameborder="0" height="400" width="100%" marginheight="0" marginwidth="0" scrolling="auto"></iframe>

</form>

</div>

    </body>
</html>