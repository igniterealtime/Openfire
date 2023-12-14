<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  -
  - Copyright (C) 2004-2008 Jive Software, 2017-2022 Ignite Realtime Foundation. All rights reserved.
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
<%@ page import="org.fusesource.jansi.HtmlAnsiOutputStream" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="java.util.Arrays" %>

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
            StringBuilder buf = new StringBuilder(input.length());
            synchronized (formatter) {
                Date date = formatter.parse(d);
                buf.append("<span class=\"date\" title=\"").append(formatter.format(date)).append("\">");
            }
            buf.append(d).append("</span>");
            buf.append(input.substring(19));
            return buf.toString();
        }
        catch (ParseException pe) {
            return input;
        }
    }

    /**
     * Formats ANSI control characters as HTML. Also escapes basic HTML characters.
     */
    private static String ansiToHtml(String input) {
        try (
            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
            final HtmlAnsiOutputStream hos = new HtmlAnsiOutputStream(bos);
        ) {
            hos.write(input.getBytes(StandardCharsets.UTF_8));
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // ANSI-parsing issue? At least escape HTML.
            return StringUtils.escapeHTMLTags(input);
        }
    }

    private static String leadingWhitespaceNonBreaking(String input) {
        if (input == null || "".equals(input)) {
            return input;
        }
        int i = 0;
        while (i < input.length() && Character.isWhitespace(input.charAt(i))) {
            i++;
        }
        if (i>0) {
            // Replace leading whitespace with non-breaking characters (to simulate indentation).
            input = "&nbsp;&nbsp;&nbsp;&nbsp;" + input.substring(i);
        }
        return input;
    }

    private static String hilite(String input) {
        if (input == null || "".equals(input)) {
            return input;
        }
        if (input.contains("org.jivesoftware.")) {
            return "<span class=\"hilite\">" + input + "</span>";
        }
        else if (input.trim().startsWith("---") && input.trim().endsWith("---")) {
            return "<span class=\"hilite-marker\">" + input + "</span>";
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
    if (!Arrays.asList("trace","debug","warn","info","error").contains(log)) {
        log = null;
    }

    // Set defaults
    if (mode == null) {
        mode = "asc";
    }
    if (numLinesParam == null) {
        numLinesParam = "50";
    }

    // Other vars
    File logDir = new File(Log.getLogDirectory());
    String filename = "openfire.log";
    File logFile = new File(logDir, filename);
    
    String[] lines = new String[0];
    int start = 0;
    try {
        String line;
        int totalNumLines = 0;
        try(FileInputStream fileInputStream = new FileInputStream(logFile);
            InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream, StandardCharsets.UTF_8);
            BufferedReader in = new BufferedReader(inputStreamReader);){
            while ((line=in.readLine()) != null) {
                if (shouldPrintLine(log, line)) {
                    totalNumLines++;
                }
        	}
        }
        // adjust the 'numLines' var to match totalNumLines if 'all' was passed in:
        if ("All".equals(numLinesParam)) {
            numLines = totalNumLines;
        }
        lines = new String[numLines];
        try(FileInputStream fileInputStream = new FileInputStream(logFile);
            InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream, StandardCharsets.UTF_8);
            BufferedReader in = new BufferedReader(inputStreamReader);){
            // skip lines
            start = totalNumLines - numLines;
            if (start < 0) { start = 0; }
            int i = 0;
            int j = 0;
            int end = lines.length-1;
            while ((line=in.readLine()) != null && i<numLines) {
                if (!shouldPrintLine(log, line)) {
                    continue;
                }
                j++;
                if (j<start) {
                    continue;
                }
                //line = StringUtils.escapeHTMLTags(line);
                line = ansiToHtml(line);
                line = leadingWhitespaceNonBreaking(line);
                line = parseDate(line);
                line = hilite(line);
                if ("asc".equals(mode)) {
                    lines[i] = line;
                } else {
                    lines[end-i] = line;
                }
                i++;
            }
            numLines = start + i;
        }
    } catch (FileNotFoundException ex) {
        System.err.println("Openfire admin console could not open (log)file.");
        ex.printStackTrace();
    }
%>
<%!
    String lastLevel = null;
    boolean shouldPrintLine(String level, final String line) {
        if (level == null) {
            return true;
        }
        // If the line doesn't start with the date/time pattern as defined in log4j2.xml, do not skip (probably a stack trace)
        final boolean startsWithDateTime =  line.matches("^\\d{4}.\\d{2}.\\d{2} \\d{2}:\\d{2}:\\d{2}.*");
        // The third 'word' (space-separated sequence of characters) should include the log level (possibly surrounded with ANSI escape characters).
        final String[] words = line.split(" ");
        final boolean hasAtLeastThreeWords = words.length >= 3;

        // Determine what the log level of this line is.
        String detectedLevel = null;
        if (startsWithDateTime && hasAtLeastThreeWords) {
            if (words[2].contains("ERROR")) {
                detectedLevel = "error";
            } else if (words[2].contains("WARN")) {
                detectedLevel = "warn";
            } else if (words[2].contains("INFO")) {
                detectedLevel = "info";
            } else if (words[2].contains("DEBUG")) {
                detectedLevel = "debug";
            } else if (words[2].contains("TRACE")) {
                detectedLevel = "trace";
            }
        }

        final boolean result;
        if (detectedLevel != null && detectedLevel.equalsIgnoreCase(level)) {
            result = true;
        } else if (detectedLevel == null && level.equalsIgnoreCase(lastLevel)) {
            // Assume that this line belongs to the last line that defined a level (eg: stacktrace)
            result = true;
        } else {
            result = false;
        }

        if (detectedLevel != null) {
            lastLevel = detectedLevel;
        }

        return result;
    }
%>

<html>
<head>
    <title>openfire.log</title>
    <meta name="decorator" content="none"/>
    <style>
    .log TABLE {
        border : 1px #ccc solid;
        border-collapse: collapse;
    }
    .log TH {
        font-family : verdana, arial, sans-serif;
        font-weight : bold;
        font-size : 8pt;
        color: #eee;
    }
    .log TR TH {
        background-color : #333;
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
        color: #eee;
        background-color : #333;
    }
    .log .num {
        width : 1%;
        background-color : #333 !important;
        border-right : 1px #ccc solid;
        padding-left : 2px;
        padding-right : 2px;
    }
    .log .line {
        padding-left : 10px;
    }
    .hilite {
        color : #fff;
    }
    .hilite-marker {
        color : #cc0000;
        font-weight : bold;
        text-decoration-style: double;
    }
    </style>
</head>
<body>

<div class="log">
<table style="line-height: 100%;">
<tr>
    <th class="head-num"><fmt:message key="log.line" /></th>
    <th>&nbsp;</th>
</tr>
<tr>
    <td style="width: 1%; white-space: nowrap" class="num">
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
    <td class="line" style="white-space: nowrap">
        <% for (String line1 : lines) {
            if (line1 != null) {
        %>
        <%= line1 %>
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
