<%@ page import="org.jivesoftware.util.cache.Cache"%>
<%@ page import="org.jivesoftware.util.ParamUtils"%>
<%@ page import="java.text.DecimalFormat"%>
<%--
  -	$RCSfile$
  -	$Revision: $
  -	$Date: $
  -
  - Copyright (C) 2005-2008 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution, or a commercial license
  - agreement with Jive.
--%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<html>
    <head>
        <title><fmt:message key="system.cache.title"/></title>
        <meta name="pageID" content="system-cache"/>
        <script language="JavaScript" type="text/javascript">
        var selected = false;
        var cbstate = '';
        function handleCBClick(el) {
            var theform = el.form;
            for (var i=0; i<theform.elements.length; i++) {
                var theel = theform.elements[i];
                if (theel.name == 'cacheID') {
                    theel.checked = !selected;
                    toggleHighlight(theel);
                }
            }
            el.checked = !selected;
            selected = !selected;
            updateControls(theform);
        }
        function setCBState(theform) {
            for (var i=0; i<theform.elements.length; i++) {
                var theel = theform.elements[i];
                if (theel.name == 'cacheID') {
                    cbstate += theel.checked;
                }
            }
        }
        function clearCBs(theform) {
            for (var i=0; i<theform.elements.length; i++) {
                var theel = theform.elements[i];
                if (theel.name == 'cacheID') {
                    theel.checked = false;
                }
            }
        }
        function updateControls(theform) {
            var currentState = '';
            for (var i=0; i<theform.elements.length; i++) {
                var theel = theform.elements[i];
                if (theel.name == 'cacheID') {
                    currentState += theel.checked;
                }
            }
            if (currentState != cbstate) {
                theform.clear.disabled = false;
            }
            else {
                theform.clear.disabled = true;
            }
        }
        function toggleHighlight(el) {
            var r = null;
            if (el.parentNode && el.parentNode.parentNode) {
                r = el.parentNode.parentNode;
            }
            else if (el.parentElement && el.parentElement.parentElement) {
                r = el.parentElement.parentElement;
            }
            if (r) {
                if (el.checked) {
                    r.className = "jive-row-sel";
                }
                else {
                    r.className = "jive-row";
                }
            }
        }
        </script>
    </head>
    <body>

<% // Get parameters
    boolean doClearCache = request.getParameter("clear") != null;
    int refresh = ParamUtils.getIntParameter(request, "refresh", -1);
    int[] cacheIDs = ParamUtils.getIntParameters(request, "cacheID", -1);

    // Get the list of existing caches
    Cache[] caches = webManager.getCaches();

    // Clear one or multiple caches if requested.
    if (doClearCache) {
        for (int cacheID : cacheIDs) {
            caches[cacheID].clear();
        }
    }

    // decimal formatter for cache values
    DecimalFormat mbFormat = new DecimalFormat("#0.00");
    DecimalFormat percentFormat = new DecimalFormat("#0.0");
    percentFormat.setNegativePrefix("");
%>

<%  if (doClearCache) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="system.cache.cleared" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<p>
<fmt:message key="system.cache.info" />
</p>

<%  // cache variables
    double overallTotal = 0.0;
    double memUsed;
    double totalMem;
    double freeMem;
    double usedMem;
    String hitPercent;
    long hits;
    long misses;
%>

<form action="system-cache.jsp" method="post" name="cacheForm">

<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th width="39%" nowrap><fmt:message key="system.cache.head.name" /></th>
        <th width="10%" nowrap><fmt:message key="system.cache.head.max" /></th>
        <th width="10%" nowrap><fmt:message key="system.cache.head.current" /></th>
        <th width="20%" nowrap><fmt:message key="system.cache.head.percent" /></th>
        <th width="20%" nowrap><fmt:message key="system.cache.head.effectiveness" /></th>
        <th width="1%" class="c5"><input type="checkbox" name="" value="" onclick="handleCBClick(this);"></th>
    </tr>
</thead>
<tbody>

<%  // Loop through each cache, print out its info
    for (int i=0; i<caches.length; i++) {
        Cache cache = caches[i];
        if (cache.getMaxCacheSize() != -1 && cache.getMaxCacheSize() != Integer.MAX_VALUE) {
            overallTotal += (double)cache.getMaxCacheSize();
        }
        memUsed = (double)cache.getCacheSize()/(1024*1024);
        totalMem = (double)cache.getMaxCacheSize()/(1024*1024);
        freeMem = 100 - 100*memUsed/totalMem;
        usedMem = 100*memUsed/totalMem;
        hits = cache.getCacheHits();
        misses = cache.getCacheMisses();
        boolean lowEffec = false;
        if (hits + misses == 0) {
            hitPercent = "N/A";
        }
        else {
            double hitValue = 100*(double)hits/(hits+misses);
            hitPercent = percentFormat.format(hitValue) + "%";
            lowEffec = (hits > 500 && hitValue < 85.0 && freeMem < 20.0);
        }
%>
    <tr class="<%= (lowEffec ? "jive-error" : "") %>">
        <td class="c1">
            <table cellpadding="0" cellspacing="0" border="0">
            <tr>
                <td class="icon"><img src="images/cache-16x16.gif" width="16" height="16" alt="" border="0"></td>
                <td><%= cache.getName() %></td>
            </tr>
            </table>
        </td>
        <td class="c2">
            <% if (cache.getMaxCacheSize() != -1 && cache.getMaxCacheSize() != Integer.MAX_VALUE) { %>
                <%= mbFormat.format(totalMem) %> MB
            <% } else { %>
                <fmt:message key="global.unlimited" />
            <% } %>
        </td>
        <td class="c3">
            <%= mbFormat.format(memUsed)%> MB
        </td>
        <td class="c3">
            <% if (cache.getMaxCacheSize() != -1 && cache.getMaxCacheSize() != Integer.MAX_VALUE) { %>
                <%= percentFormat.format(usedMem)%>%
            <% } else { %>
                N/A
            <% } %>
        </td>
        <td class="c4">
            <%= hitPercent%>
        </td>

        <td width="1%" class="c5"><input type="checkbox" name="cacheID" value="<%= i %>" onclick="updateControls(this.form);toggleHighlight(this);"></td>
    </tr>

<%  } %>

<tr bgcolor="#eeeeee">
    <td align="right" class="c1">
        <fmt:message key="system.cache.total" />
    </td>
    <td class="c2">
        <%= mbFormat.format(overallTotal/(1024.0*1024.0)) %> MB
    </td>
    <td align="right" colspan="4">
        <input type="submit" name="clear" value="<fmt:message key="system.cache.clear-selected" />" disabled>
    </td>
</tr>
</tbody>
</table>
</div>

<p class="jive-description">
<fmt:message key="system.cache.desc.effectiveness" />
</p>

    <script language="JavaScript" type="text/javascript">
    clearCBs(document.cacheForm);
    setCBState(document.cacheForm);
    </script>

    </form>

    </body>
</html>
