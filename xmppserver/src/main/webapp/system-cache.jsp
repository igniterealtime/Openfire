<%--
  -
  - Copyright (C) 2005-2008 Jive Software, 2017-2024 Ignite Realtime Foundation. All rights reserved.
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

<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="java.text.DecimalFormat"%>
<%@ page import="java.time.Duration"%>
<%@ page import="org.jivesoftware.util.CookieUtils"%>
<%@ page import="org.jivesoftware.util.ParamUtils"%>
<%@ page import="org.jivesoftware.util.StringUtils"%>
<%@ page import="org.jivesoftware.util.cache.Cache" %>
<%@ page import="org.jivesoftware.util.cache.CacheWrapper" %>
<%@ page import="org.jivesoftware.util.cache.DefaultCache" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib prefix="admin" uri="admin" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<html>
    <head>
        <title><fmt:message key="system.cache.title"/></title>
        <meta name="pageID" content="system-cache"/>
        <script>
        let selected = false;
        let cbstate = '';
        function handleCBClick(el) {
            let theform = el.form;
            for (let i=0; i<theform.elements.length; i++) {
                let theel = theform.elements[i];
                if (theel.name === 'cacheID') {
                    theel.checked = !selected;
                    toggleHighlight(theel);
                }
            }
            el.checked = !selected;
            selected = !selected;
            updateControls(theform);
        }
        function setCBState(theform) {
            for (let i=0; i<theform.elements.length; i++) {
                let theel = theform.elements[i];
                if (theel.name === 'cacheID') {
                    cbstate += theel.checked;
                }
            }
        }
        function clearCBs(theform) {
            for (let i=0; i<theform.elements.length; i++) {
                let theel = theform.elements[i];
                if (theel.name === 'cacheID') {
                    theel.checked = false;
                }
            }
        }
        function updateControls(theform) {
            let currentState = '';
            for (let i=0; i<theform.elements.length; i++) {
                let theel = theform.elements[i];
                if (theel.name === 'cacheID') {
                    currentState += theel.checked;
                }
            }
            theform.clear.disabled = currentState === cbstate;
        }
        function toggleHighlight(el) {
            let r = null;
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
    int[] cacheIDs = ParamUtils.getIntParameters(request, "cacheID", -1);

    // Get the list of existing caches
    Cache[] caches = webManager.getCaches();

    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (doClearCache) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            doClearCache = false;
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);
    // Clear one or multiple caches if requested.
    if (doClearCache) {
        for (int cacheID : cacheIDs) {
            final Cache cache = caches[cacheID];
            cache.clear();
            webManager.logEvent(String.format("Cleared cache '%s'", cache.getName()), null);
        }
    }
%>

<%  if (doClearCache) { %>

    <admin:infoBox type="success">
        <fmt:message key="system.cache.cleared" />
    </admin:infoBox>

<%  } %>

<p>
<fmt:message key="system.cache.info" />
</p>

<%  // cache variables
    double overallTotal = 0.0;
    double capacityUsed;
    double capacityTotal;
    double capacityUsedPercentage;
    double hitPercent;
    long hits;
    long misses;
    Long[] culls;
%>

<form action="system-cache.jsp" method="post" name="cacheForm">
        <input type="hidden" name="csrf" value="${csrf}">

<div class="jive-table">
<table>
<thead>
    <tr>
        <th><fmt:message key="system.cache.head.name" /></th>
        <th nowrap><fmt:message key="system.cache.head.max" /></th>
        <th nowrap><fmt:message key="system.cache.head.lifetime" /></th>
        <th style="text-align: center;" nowrap colspan="2"><fmt:message key="system.cache.head.current" /></th>
        <th nowrap><fmt:message key="system.cache.head.percent" /></th>
        <th style="text-align: center;" colspan="2"><fmt:message key="system.cache.head.effectiveness" /></th>
        <th style="text-align: center;" nowrap><fmt:message key="system.cache.head.culls" /><br/>3/6/12 <fmt:message key="global.hours" /></th>
        <th style="width: 1%" class="c5"><input type="checkbox" name="" value="" onclick="handleCBClick(this);"></th>
    </tr>
</thead>
<tbody>

<%  // Loop through each cache, print out its info
    for (int i=0; i<caches.length; i++) {
        Cache cache = caches[i];
        final Cache.CapacityUnit capacityUnit = cache.getCapacityUnit();
        if (capacityUnit == Cache.CapacityUnit.BYTES && cache.getMaxCacheSize() != -1 && cache.getMaxCacheSize() != Long.MAX_VALUE) {
            overallTotal += (double) cache.getMaxCacheSize();
        }
        capacityUsed = (double) cache.getLongCacheSize();
        capacityTotal = (double) cache.getMaxCacheSize();
        capacityUsedPercentage = 100 * capacityUsed / capacityTotal;
        hits = cache.getCacheHits();
        misses = cache.getCacheMisses();
        boolean lowEffec = false;
        if (hits + misses == 0) {
            hitPercent = -1;
        }
        else {
            hitPercent = 100 * (double) hits / (hits+misses);
            lowEffec = (hits+misses > 500 && hitPercent < 85.0 && capacityUsedPercentage >= 80.0);
        }
        if (cache instanceof CacheWrapper && ((CacheWrapper) cache).getWrappedCache() instanceof DefaultCache) {
            culls = new Long[3];
            final DefaultCache defaultCache = (DefaultCache) ((CacheWrapper) cache).getWrappedCache();
            culls[0] = defaultCache.getCacheCulls(Duration.ofHours(3));
            culls[1] = defaultCache.getCacheCulls(Duration.ofHours(6));
            culls[2] = defaultCache.getCacheCulls(Duration.ofHours(12));
        } else {
            culls = null;
        }
        // OF-1365: Don't allow caches that do not expire to be purged. Many of these caches store data that cannot be recovered again.
        final boolean canPurge = cache.getMaxLifetime() > -1;

        pageContext.setAttribute("cacheName", cache.getName());
        pageContext.setAttribute("capacityUnit", capacityUnit);
        pageContext.setAttribute("maxCacheSize", cache.getMaxCacheSize());
        pageContext.setAttribute("maxCacheSizeRemark", cache.getMaxCacheSizeRemark());
        pageContext.setAttribute("cacheSize", cache.getLongCacheSize());
        pageContext.setAttribute("cacheSizeRemark", cache.getCacheSizeRemark());
        pageContext.setAttribute("maxLifetime", cache.getMaxLifetime());
        pageContext.setAttribute("maxLifeTimeDuration", Duration.ofMillis(cache.getMaxLifetime()));
        pageContext.setAttribute("capacityUsed", capacityUsed); // This is a double representation of cacheSize
        pageContext.setAttribute("capacityTotal", capacityTotal); // This is a double representation of maxCacheSize
        pageContext.setAttribute("capacityUsedPercentage", capacityUsedPercentage);
        pageContext.setAttribute("hits", hits);
        pageContext.setAttribute("misses", misses);
        pageContext.setAttribute("lowEffec", lowEffec);
        pageContext.setAttribute("hitPercent", hitPercent);
        pageContext.setAttribute("hasCulls", culls != null);
        if (culls != null) {
            pageContext.setAttribute("culls3", culls[0]);
            pageContext.setAttribute("culls6", culls[1]);
            pageContext.setAttribute("culls12", culls[2]);
        }
        pageContext.setAttribute("canPurge", canPurge);
%>
    <tr>
        <td class="c1">
            <table>
            <tr>
                <td style="width: 1%;" class="icon"><img src="images/cache-16x16.gif" alt=""></td>
                <td><a href="SystemCacheDetails.jsp?cacheName=${admin:urlEncode(cacheName)}"><c:out value="${cacheName}"/></a></td>
            </tr>
            </table>
        </td>
        <td class="c2">
            <c:choose>
                <c:when test="${empty capacityUnit}">
                    N/A
                </c:when>
                <c:when test="${maxCacheSize eq -1 or maxCacheSize eq 2147483647}">
                    <fmt:message key="global.unlimited" />
                </c:when>
                <c:when test="${capacityUnit == 'BYTES'}">
                    <fmt:formatNumber type="number" pattern="#0.00" value="${capacityTotal / (1024*1024)}" />&nbsp;MB
                    <c:if test="${not empty maxCacheSizeRemark}">
                        (<c:out value="${maxCacheSizeRemark}"/>)
                    </c:if>
                </c:when>
                <c:otherwise>
                    <fmt:formatNumber type="number" value="${maxCacheSize}" />
                    <c:if test="${not empty maxCacheSizeRemark}">
                        (<c:out value="${maxCacheSizeRemark}"/>)
                    </c:if>
                </c:otherwise>
            </c:choose>
        </td>
        <td class="c2">
            <c:choose>
                <c:when test="${maxLifetime ne -1}">
                    <c:out value="${admin:fullElapsedTime(maxLifeTimeDuration)}"/>
                </c:when>
                <c:otherwise>
                    <fmt:message key="global.unlimited" />
                </c:otherwise>
            </c:choose>
        </td>
        <td class="c3" style="text-align: right; padding-right:0;">
            <fmt:formatNumber type="number" value="${cacheSize}" />
        </td>
        <td class="c3" style="text-align: left; padding-left:0;">
            <c:if test="${capacityUnit == 'BYTES'}">
                &nbsp;/&nbsp;<fmt:formatNumber type="number" pattern="#0.00" value="${capacityUsed / (1024*1024)}" />&nbsp;MB
            </c:if>
            <c:if test="${not empty cacheSizeRemark}">
                &nbsp;(<c:out value="${cacheSizeRemark}"/>)
            </c:if>
        </td>
        <td class="c3">
            <c:choose>
                <c:when test="${maxCacheSize eq -1 or maxCacheSize eq 2147483647}">
                    <fmt:message key="global.unlimited" />
                </c:when>
                <c:otherwise>
                    <fmt:formatNumber type="number" pattern="#0.0" value="${capacityUsedPercentage}" />%
                </c:otherwise>
            </c:choose>
        </td>
        <td class="c4" style="text-align: right; padding-right:0;">
            <fmt:formatNumber type="number" value="${hits}" />/<fmt:formatNumber type="number" value="${hits + misses}" />
        </td>
        <td class="c4" style="text-align: left; padding-left:0;">
            <c:if test="${lowEffec}"><span style="color: red;"></c:if>
            <c:if test="${hitPercent ge 0}">
                &nbsp;(<fmt:formatNumber type="number" pattern="#0.0" value="${hitPercent}" />%)
            </c:if>
            <c:if test="${lowEffec}">*</span></c:if>
        </td>
        <td class="c4" style="text-align: center">
            <c:choose>
                <c:when test="${hasCulls}">
                    <fmt:formatNumber type="number" value="${culls3}" />/<fmt:formatNumber type="number" value="${culls6}" />/<fmt:formatNumber type="number" value="${culls12}" />
                </c:when>
                <c:otherwise>N/A</c:otherwise>
            </c:choose>
        </td>
        <td style="width: 1%" class="c5">
            <% if ( canPurge ) {%>
            <input type="checkbox" name="cacheID" value="<%= i %>" onclick="updateControls(this.form);toggleHighlight(this);">
            <% } %>
        </td>
    </tr>

<%  } %>

<tr style="background-color: #EEEEEE">
    <td style="text-align: right" class="c1">
        <fmt:message key="system.cache.total" />
    </td>
    <td class="c2">
        <%= new DecimalFormat("#0.00").format(overallTotal/(1024.0*1024.0)) %> MB
    </td>
    <td style="text-align: right" colspan="8">
        <input type="submit" name="clear" value="<fmt:message key="system.cache.clear-selected" />" disabled>
    </td>
</tr>
</tbody>
</table>
</div>

<p class="jive-description">
<fmt:message key="system.cache.desc.effectiveness" />
</p>

    <script>
    clearCBs(document.cacheForm);
    setCBState(document.cacheForm);
    </script>

    </form>

    </body>
</html>
