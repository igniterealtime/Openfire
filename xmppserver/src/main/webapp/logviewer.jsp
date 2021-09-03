<%@ page contentType="text/html; charset=UTF-8" %>
<%--
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
                 java.net.URLEncoder,
                 org.jivesoftware.util.JiveGlobals,
                 org.jivesoftware.openfire.user.*,
                 java.util.*"
%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />

<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>

<%!
    static final String NONE = LocaleUtils.getLocalizedString("global.none");

    static final String ASCENDING = "asc";

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

    private static boolean hasLogfileChanged(HttpServletRequest request, HttpServletResponse response, File logDir)
    {
        // Get the cookie associated with the log files
        HashMap cookie = parseCookie(CookieUtils.getCookie(request,"jiveforums.admin.logviewer"));
        HashMap<String,String> newCookie = new HashMap<String,String>();
        // Check for the value in the cookie:
        String key = "logfile.size";
        long savedSize = 0L;
        if (cookie.containsKey(key)) {
            try {
                savedSize = Long.parseLong((String) cookie.get(key));
            }
            catch (NumberFormatException nfe) {
            }
        }
        // Update the size in the Map:
        File logFile = new File(logDir, "openfire.log");
        long currentSize = logFile.length();
        newCookie.put(key, "" + currentSize);
        saveCookie(response, newCookie);
        return currentSize != savedSize;
    }
%>

<%
    // Get parameters
    String log = ParamUtils.getParameter(request, "log");
    String numLinesParam = ParamUtils.getParameter(request,"lines");
    int numLines = ParamUtils.getIntParameter(request,"lines",50);
    String refreshParam = ParamUtils.getParameter(request,"refresh");
    String mode = ParamUtils.getParameter(request,"mode");
    boolean clearLog = ParamUtils.getBooleanParameter(request,"clearLog");
    boolean markLog = ParamUtils.getBooleanParameter(request,"markLog");
    boolean saveLog = ParamUtils.getBooleanParameter(request,"saveLog");
    boolean emailLog = ParamUtils.getBooleanParameter(request,"emailLog");
    boolean debugEnabled = ParamUtils.getBooleanParameter(request,"debugEnabled");
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");


    // Enable/disable debugging
    if (request.getParameter("debugEnabled") != null && debugEnabled != Log.isDebugEnabled()) {
        if (!(csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam))) {
            Log.TRACE_ENABLED.setValue(debugEnabled);
            // Log the event
            admin.logEvent((debugEnabled ? "enabled" : "disabled")+" debug logging", null);
            response.sendRedirect("logviewer.jsp");
            return;
        }
    }

    // Sanitize variables to prevent vulnerabilities
    if (log != null) {
        log = StringUtils.escapeHTMLTags(log);
    }
    debugEnabled = Log.isTraceEnabled();
    User pageUser = admin.getUser();

    if (clearLog) {
        if (!(csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam))) {
            Log.rotateOpenfireLogFile();
            response.sendRedirect("logviewer.jsp");
            return;
        }
    }
    else if (markLog) {
        if (!(csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam))) {
            Log.markOpenfireLogFile(pageUser.getUsername());
            response.sendRedirect("logviewer.jsp");
            return;
        }
    }
    else if (saveLog) {
        response.sendRedirect(request.getContextPath() + "/servlet/JiveServlet/");
        return;
    }
    else if (emailLog) {
        response.sendRedirect("emaillog.jsp");
        return;
    }

    // Set defaults
    if (log == null) {
        log = "all";
    }
    if (mode == null) {
        mode = ASCENDING;
    }
    if (numLinesParam == null) {
        numLinesParam = "50";
    }

    // Other vars
    File logDir = new File(Log.getLogDirectory());
    String filename = "openfire.log";
    File logFile = new File(logDir, filename);

    // Determine if any of the log files contents have been updated:
    boolean hasLogfileChanged = hasLogfileChanged(request, response, logDir);
    csrfParam = StringUtils.randomString(16);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

%>

<html>
    <head>
        <title><fmt:message key="logviewer.title"/></title>
        <meta name="pageID" content="server-logs"/>
        <meta name="helpPage" content="use_the_server_logs.html"/>
    </head>
    <body>

<%  if (refreshParam != null && !NONE.equals(refreshParam)) { %>
    <meta http-equiv="refresh" content="<%= ParamUtils.getIntParameter(request,"refresh",10) %>">
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

<div class="logviewer">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<tbody>
    <tr>
        <td class="jive-spacer" width="1%">&nbsp;</td>
        <td class="jive-tab-active" width="1%">
            <a href="logviewer.jsp"
            ><fmt:message key="logviewer.openfire" /></a>
            <span class="new">
            <%= (hasLogfileChanged?"*":"") %>
            </span>
        </td>
        <td class="jive-stretch" width="98%" align="right" nowrap>
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
                <td nowrap><b><%= StringUtils.escapeHTMLTags(logFile.getName()) %></b> (<%= byteFormatter.format(logFile.length()) %>)</td>
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
                    <input type="hidden" name="csrf" value="${csrf}">
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
                <td nowrap><fmt:message key="logviewer.show"/></td>
                <td nowrap>
                    <select size="1" name="log" onchange="this.form.submit();">
                        <option value="all" <%=log.equals("all") ?"selected":""%>><fmt:message key="logviewer.all"/></option>
                        <option value="trace" <%=log.equals("trace") ?"selected":""%>><fmt:message key="logviewer.trace"/></option>
                        <option value="debug" <%=log.equals("debug") ?"selected":""%>><fmt:message key="logviewer.debug"/></option>
                        <option value="info" <%=log.equals("info") ?"selected":""%>><fmt:message key="logviewer.info"/></option>
                        <option value="warn" <%=log.equals("warn") ?"selected":""%>><fmt:message key="logviewer.warn"/></option>
                        <option value="error" <%=log.equals("error") ?"selected":""%>><fmt:message key="logviewer.error"/></option>
                    </select>
                </td>
            </tr>

            <tr>
                <td colspan="3">

                    <table cellpadding="0" cellspacing="0" border="0" width="100%">
                    <tr>
                        <td width="1%" nowrap>
                            <fmt:message key="logviewer.debug_log" />: &nbsp;
                        </td>
                        <td width="1%">
                            <input id="de01" type="radio" name="debugEnabled" value="true" <%= debugEnabled ? " checked" : "" %>>
                        </td>
                        <td width="1%" nowrap>
                            <label for="de01"><fmt:message key="logviewer.enabled" /></label> &nbsp;
                        </td>
                        <td width="1%">
                            <input id="de02" type="radio" name="debugEnabled" value="false" <%= debugEnabled ? "" : " checked" %>>
                        </td>
                        <td width="1%" nowrap>
                            <label for="de02"><fmt:message key="logviewer.disabled" /></label> &nbsp;
                        </td>
                        <td width="1%">
                            <input type="submit" name="" value="<fmt:message key="global.save_changes" />">
                        </td>
                        <td width="94%">&nbsp;</td>
                    </tr>
                    </table>
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

<iframe src="log.jsp?log=<%= URLEncoder.encode(log) %>&mode=<%= URLEncoder.encode(mode) %>&lines=<%= ("All".equals(numLinesParam) ? "All" : String.valueOf(numLines)) %>"
    frameborder="0" height="600" width="100%" marginheight="0" marginwidth="0" scrolling="auto"></iframe>

</form>

</div>

    </body>
</html>
