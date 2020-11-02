<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  -
  - Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

<%@ page import="org.jivesoftware.util.ParamUtils"%>
<%@ page import="org.jivesoftware.util.StringUtils"%>
<%@ page import="org.xmpp.packet.JID"%>
<%@ page import="java.net.URLEncoder"%>
<%@ page import="java.util.*"%>
<%@ page import="org.jivesoftware.openfire.privacy.PrivacyListProvider" %>
<%@ page import="org.jivesoftware.openfire.privacy.PrivacyList" %>
<%@ page import="org.jivesoftware.openfire.privacy.PrivacyListManager" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<!-- Define Administration Bean -->
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<%
    webManager.init(pageContext);
%>

<%
    // Get parameters
    String username = StringUtils.escapeHTMLTags(ParamUtils.getParameter(request, "username"));

    Map<String, Boolean> privacyListNames = PrivacyListProvider.getInstance().getPrivacyLists( username );
    String defaultListName = "";
    Set<PrivacyList> privacyLists = new HashSet<>();
    for (final Map.Entry<String, Boolean> entry : privacyListNames.entrySet() ) {
        String name = entry.getKey();
        if ( entry.getValue() ) {
            defaultListName = name;
        }
        privacyLists.add(PrivacyListManager.getInstance().getPrivacyList(username, name));
    }

    pageContext.setAttribute("username", username);
    pageContext.setAttribute("defaultListName", defaultListName);
    pageContext.setAttribute("privacyLists", privacyLists);
%>

<html>
<head>
<title><fmt:message key="user.privacylists.title" /></title>
<meta name="subPageID" content="user-privacylists" />
<meta name="extraParams" content="<%="username="+URLEncoder.encode(username, "UTF-8")%>" />
</head>
<body>
    <p>
        <fmt:message key="user.privacylists.info" />
        <b><c:out value="${username}"/>.</b>
    </p>

    <c:choose>
        <c:when test="${empty privacyLists}">
            <p><fmt:message key="user.privacylists.no_lists" /></p>
        </c:when>
        <c:otherwise>
            <c:forEach items="${privacyLists}" var="privacyList">
                <div class="jive-table">
                    <table cellpadding="0" cellspacing="0" border="0" width="100%">
                        <thead>
                            <tr>
                                <th>&nbsp;</th>
                                <th colspan="3"><c:out value="${privacyList.name}"/> <c:if test="${privacyList.name eq defaultListName}"><fmt:message key="user.privacylist.is_default_list" /></c:if></th>
                            </tr>
                        </thead>
                        <tbody>
                            <c:choose>
                                <c:when test="${empty privacyList.items}">
                                    <fmt:message key="user.privacylists.no_items_on_list" />
                                </c:when>
                                <c:otherwise>
                                    <c:forEach items="${privacyList.items}" var="item" varStatus="status">
                                        <tr class="${ ( (status.index + 1) % 2 ) eq 0 ? 'jive-even' : 'jive-odd'}">
                                            <td width="1%" valign="top"><c:out value="${item.order}"/></td>
                                            <td width="1%"><c:choose><c:when test="${item.allow}"><fmt:message key="user.privacylist.allow" /></c:when><c:otherwise><fmt:message key="user.privacylist.deny" /></c:otherwise></c:choose></td>
                                            <td width="1%"><c:out value="${item.type}"/></td>
                                            <td><c:choose>
                                                <c:when test="${item.type eq 'jid'}"><c:out value="${item.JID}"/></c:when>
                                                <c:when test="${item.type eq 'group'}"><c:out value="${item.group}"/></c:when>
                                                <c:when test="${item.type eq 'subscription'}"><c:out value="${item.subscription}"/></c:when>
                                            </c:choose></td>
                                        </tr>
                                    </c:forEach>
                                </c:otherwise>
                            </c:choose>
                        </tbody>
                    </table>
                </div>

                <br/>
            </c:forEach>
        </c:otherwise>
    </c:choose>

</body>
</html>
