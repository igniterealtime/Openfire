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

<%@ page import="java.util.ArrayList,
                 java.util.Comparator,
                 java.util.List,
                 java.util.function.Predicate"
%>
<%@ page import="org.jivesoftware.openfire.group.Group" %>
<%@ page import="org.jivesoftware.openfire.group.GroupManager" %>
<%@ page import="org.jivesoftware.util.ListPager" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<html>
    <head>
        <title><fmt:message key="group.summary.title"/></title>
        <meta name="pageID" content="group-summary"/>
        <meta name="helpPage" content="about_users_and_groups.html"/>
    </head>
    <body>

<%  // Get parameters
    Predicate<Group> filter = group -> true;
    final String searchGroupName = ParamUtils.getStringParameter(request, "searchGroupName", "").trim();
    pageContext.setAttribute("searchGroupName", searchGroupName);
    if(!searchGroupName.isEmpty()) {
        final String searchCriteria = searchGroupName.toLowerCase();
        filter = filter.and(group -> group.getName().toLowerCase().contains(searchCriteria));
    }
    final String searchGroupDescription = ParamUtils.getStringParameter(request, "searchGroupDescription", "").trim();
    pageContext.setAttribute("searchGroupDescription", searchGroupDescription);
    if(!searchGroupDescription.isEmpty()) {
        final String searchCriteria = searchGroupDescription.toLowerCase();
        filter = filter.and(group -> group.getDescription().toLowerCase().contains(searchCriteria));
    }

    final GroupManager groupManager = webManager.getGroupManager();
    final List<Group> groups = new ArrayList<>(groupManager.getGroups());
    groups.sort(Comparator.comparing(group -> group.getName().toLowerCase()));
    final ListPager<Group> listPager = new ListPager<>(request, response, groups, filter, "searchGroupName", "searchGroupDescription");
    pageContext.setAttribute("listPager", listPager);
    pageContext.setAttribute("canEdit", !groupManager.isReadOnly());
    pageContext.setAttribute("deleteSuccess", request.getParameter("deletesuccess") != null);

%>

<c:if test="${deleteSuccess}">
    <div class="success"><fmt:message key="group.summary.delete_group"/></div>
</c:if>

<fmt:message key="group.summary.total_group" /> <b>${listPager.totalItemCount}</b>
<c:if test="${listPager.filtered}">
    <fmt:message key="group.summary.filtered_group_count" />: <c:out value="${listPager.filteredItemCount}"/>
</c:if>

<c:if test="${listPager.totalItemCount > 0}">
<c:if test="${listPager.totalPages > 1}">
    <fmt:message key="global.showing" /> <c:out value="${listPager.firstItemNumberOnPage}"/>-<c:out value="${listPager.lastItemNumberOnPage}"/>
</c:if>
-- <fmt:message key="group.summary.groups_per_page" />: ${listPager.pageSizeSelection}

<p><fmt:message key="global.pages" />: [ ${listPager.pageLinks} ]</p>
</c:if>

<div class="jive-table">
    <table cellpadding="0" cellspacing="0" border="0" width="100%">
        <thead>
        <tr>
            <th>&nbsp;</th>
            <th nowrap><fmt:message key="group.summary.page_name" /></th>
            <th nowrap><fmt:message key="group.summary.page_description" /></th>
            <th nowrap><fmt:message key="group.summary.page_member" /></th>
            <th nowrap><fmt:message key="group.summary.page_admin" /></th>
            <th nowrap><fmt:message key="group.summary.page_edit" /></th>
            <c:if test="${canEdit}">
                <th nowrap><fmt:message key="global.delete" /></th>
            </c:if>
        </tr>
        <c:if test="${listPager.totalPages > 1}">
        <tr>
            <td></td>
            <td nowrap>
                <input type="search"
                       id="searchGroupName"
                       size="20"
                       value="<c:out value="${searchGroupName}"/>"/>
                <img src="images/search-16x16.png"
                     width="16" height="16"
                     alt="search" title="search"
                     style="vertical-align: middle;"
                     onclick="submitForm();"
                >
            </td>
            <td nowrap>
                <input type="search"
                       id="searchGroupDescription"
                       size="20"
                       value="<c:out value="${searchGroupDescription}"/>"/>
                <img src="images/search-16x16.png"
                     width="16" height="16"
                     alt="search" title="search"
                     style="vertical-align: middle;"
                     onclick="submitForm();"
                >
            </td>
            <td></td>
            <td></td>
            <td></td>
            <c:if test="${canEdit}">
                <td></td>
            </c:if>
        </tr>
        </c:if>
        </thead>
        <tbody>
        <c:if test="${listPager.totalItemCount < 1}">
            <tr>
                <td align="center" colspan="6">
                    <fmt:message key="group.summary.no_groups" />
                </td>
            </tr>
        </c:if>
        <%--@elvariable id="group" type="org.jivesoftware.openfire.group.Group"--%>
        <c:forEach var="group" items="${listPager.itemsOnCurrentPage}" varStatus="loop">
        <tr class="${ (loop.index%2)==0 ? 'jive-even' : 'jive-odd'}">
            <td width="1%">
                <c:out value="${listPager.firstItemNumberOnPage + loop.index}"/>
            </td>
            <td width="22%">
                <a href="group-edit.jsp?group=<c:out value="${group.name}"/>"
                   title='<fmt:message key="global.click_edit"/>'
                ><c:out value="${group.name}"/></a>
            </td>
            <td width="50%"><c:out value="${group.description}"/></td>
            <td width="10%"><c:out value="${group.members.size()}"/></td>
            <td width="10%"><c:out value="${group.admins.size()}"/></td>
            <td width="1%">
                <a href="group-edit.jsp?group=<c:out value="${group.name}"/>"
                   title='<fmt:message key="global.click_edit"/>'
                ><img src="images/edit-16x16.gif"
                      width="16" height="16" border="0" alt='<fmt:message key="global.click_edit"/>'></a>
            </td>
            <c:if test="${canEdit}">
                <td width="1%">
                    <a href="group-delete.jsp?group=<c:out value="${group.name}"/>"
                                   title='<fmt:message key="global.click_delete" />'
                    ><img src="images/delete-16x16.gif"
                          width="16" height="16" border="0" alt='<fmt:message key="global.click_delete" />'></a>
                </td>
            </c:if>
        </tr>
        </c:forEach>
        </tbody>
    </table>
</div>

<c:if test="${listPager.totalItemCount > 0}">
<p><fmt:message key="global.pages" />: [ ${listPager.pageLinks} ]</p>
</c:if>

${listPager.jumpToPageForm}

<script type="text/javascript">
    ${listPager.pageFunctions}
</script>

</body>
</html>
