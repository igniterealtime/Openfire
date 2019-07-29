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
                 java.text.SimpleDateFormat,
                 java.util.Date,
                 java.text.ParseException,
                 org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.util.Log,
                 org.jivesoftware.util.StringUtils"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />

<%!
    static final SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd kk:mm:ss");

    private static String parseDate(String input) {
        if (input == null || "".equals(input)) {
            return input;
        }
        if (input.length() < 19) {
            return input;
        }
        String d = input.substring(0,19);
        // try to parse it
        try {
            StringBuffer buf = new StringBuffer(input.length());
            synchronized (formatter) {
                Date date = formatter.parse(d);
                buf.append("<span class=\"date\" title=\"").append(formatter.format(date))
                        .append("\">");
            }
            buf.append(d).append("</span>");
            buf.append(input.substring(19,input.length()));
            return buf.toString();
        }
        catch (ParseException pe) {
            return input;
        }
    }

    private static String hilite(String input) {
        if (input == null || "".equals(input)) {
            return input;
        }
        if (input.indexOf("org.jivesoftware.") > -1) {
            StringBuffer buf = new StringBuffer();
            buf.append("<span class=\"hilite\">").append(input).append("</span>");
            return buf.toString();
        }
        else if (input.trim().startsWith("---") && input.trim().endsWith("---")) {
            StringBuffer buf = new StringBuffer();
            buf.append("<span class=\"hilite-marker\">").append(input).append("</span>");
            return buf.toString();
        }
        return input;
    }
%>

<%
    // Get parameters
    String log = ParamUtils.getParameter(request,"log");
    String numLinesParam = ParamUtils.getParameter(request,"lines");
    int numLines = ParamUtils.getIntParameter(request,"lines",50);
    String mode = ParamUtils.getParameter(request,"mode");

    // Only allow requests for valid log file names.
    if (!("debug".equals(log) || "warn".equals(log) || "info".equals(log) || "error".equals(log) || "all".equals(log))) {
        log = null;
    }

    // Set defaults
    if (log == null) {
        log = "all";
    }
    if (mode == null) {
        mode = "asc";
    }
    if (numLinesParam == null) {
        numLinesParam = "50";
    }

    // Other vars
    File logDir = new File(Log.getLogDirectory());
    String filename = log + ".log";
    File logFile = new File(logDir, filename);
    
    String lines[] = new String[0];
    int start = 0;
    try {
        String line;
        int totalNumLines = 0;
        try(FileInputStream fileInputStream = new FileInputStream(logFile);
            InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream, "UTF-8");
            BufferedReader in = new BufferedReader(inputStreamReader);){
            while ((line=in.readLine()) != null) {
                totalNumLines++;
        	}
        }
        // adjust the 'numLines' var to match totalNumLines if 'all' was passed in:
        if ("All".equals(numLinesParam)) {
            numLines = totalNumLines;
        }
        lines = new String[numLines];
        try(FileInputStream fileInputStream = new FileInputStream(logFile);
            InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream, "UTF-8");
            BufferedReader in = new BufferedReader(inputStreamReader);){
            // skip lines
            start = totalNumLines - numLines;
            if (start < 0) { start = 0; }
            for (int i=0; i<start; i++) {
                in.readLine();
            }
            int i = 0;
            if ("asc".equals(mode)) {
                while ((line=in.readLine()) != null && i<numLines) {
                    line = StringUtils.escapeHTMLTags(line);
                    line = parseDate(line);
                    line = hilite(line);
                    lines[i] = line;
                    i++;
                }
            }
            else {
                int end = lines.length-1;
                while ((line=in.readLine()) != null && i<numLines) {
                    line = StringUtils.escapeHTMLTags(line);
                    line = parseDate(line);
                    line = hilite(line);
                    lines[end-i] = line;
                    i++;
                }
            }
            numLines = start + i;
        }
    } catch (FileNotFoundException ex) {
        Log.info("Could not open (log)file.", ex);
    }
%>

<html>
<head>
    <title><%= StringUtils.escapeHTMLTags(log) %></title>
    <meta name="decorator" content="none"/>
    <style type="text/css">
    .log TABLE {
        border : 1px #ccc solid;
    }
    .log TH {
        font-family : verdana, arial, sans-serif;
        font-weight : bold;
        font-size : 8pt;
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
        font-family : courier new,monospace;
        font-size : 9pt;
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
    .hilite {
        color : #900;
    }
    .hilite-marker {
        background-color : #ff0;
        color : #000;
        font-weight : bold;
    }
    </style>
</head>
<body>

<div class="log">
<table cellpadding="1" cellspacing="0" border="0" width="100%">
<tr>
    <th class="head-num"><fmt:message key="log.line" /></th>
    <th>&nbsp;</th>
</tr>
<tr>
    <td width="1%" nowrap class="num">
        <%  if ("asc".equals(mode)) { %>
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
        <% for (String line1 : lines) {
            if (line1 != null) {
        %>
        <nobr><%= line1 %>
        </nobr>
        <br>

        <% }
        }
        %>
    </td>
</tr>
</table>
</div>

</body>
</html>
