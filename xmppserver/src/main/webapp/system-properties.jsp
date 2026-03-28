<%--
  -
  - Copyright (C) 2019-2022 Ignite Realtime Foundation. All rights reserved.
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
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ page contentType="text/html; charset=UTF-8" %>
<jsp:useBean scope="request" id="errorMessage" class="java.lang.String"/>
<jsp:useBean scope="request" id="warningMessage" class="java.lang.String"/>
<jsp:useBean scope="request" id="successMessage" class="java.lang.String"/>
<jsp:useBean scope="request" id="csrf" type="java.lang.String"/>
<jsp:useBean scope="request" id="listPager" type="org.jivesoftware.util.ListPager"/>
<jsp:useBean scope="request" id="search" type="org.jivesoftware.admin.servlet.SystemPropertiesServlet.Search"/>
<jsp:useBean scope="request" id="plugins" type="java.util.List"/>

<html>
<head>
    <title><fmt:message key="server.properties.title"/></title>
    <meta name="pageID" content="server-props"/>
    <meta name="helpPage" content="manage_system_properties.html"/>
    <script src="js/list-pager.js"></script>
    <script src="js/system-properties.js"></script>
    <style>
        .nameColumn {
            overflow-wrap: break-word;
            word-wrap: break-word;
            max-width: 250px;
        }

        .valueColumn {
            max-width: 250px;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        .hidden {
            color: #999;
            font-style: italic;
        }

        img.clickable {
            cursor: pointer;
        }
    </style>
</head>
<body>

<c:if test="${not empty errorMessage}">
    <div class="error">${errorMessage}</div>
</c:if>

<c:if test="${not empty warningMessage}">
    <div class="warning">${warningMessage}</div>
</c:if>

<c:if test="${not empty successMessage}">
    <div class="success">${successMessage}</div>
</c:if>

<p><fmt:message key="server.properties.info"/></p>

<p><b><fmt:message key="server.properties.system"/></b></p>

<p>
    <fmt:message key="server.properties.total"/>: <c:out value="${listPager.totalItemCount}"/>
    <c:if test="${listPager.filtered}">
        <fmt:message key="server.properties.filtered"/>: <c:out value="${listPager.filteredItemCount}"/>
    </c:if>

    <c:if test="${listPager.totalPages > 1}">
        <fmt:message key="global.showing"/> <c:out value="${listPager.firstItemNumberOnPage}"/>-<c:out
        value="${listPager.lastItemNumberOnPage}"/>
    </c:if>
    -- <fmt:message key="server.properties.per-page"/>: ${listPager.pageSizeSelection}

</p>

<p><fmt:message key="global.pages"/>: [ ${listPager.pageLinks} ]</p>
<div class="jive-table">
    <table>
        <thead>
        <tr>
            <th nowrap><label for="searchName"><fmt:message key="server.properties.name"/></label></th>
            <th nowrap><label for="searchValue"><fmt:message key="server.properties.value"/></label></th>
            <th nowrap><label for="searchDefaultValue"><fmt:message key="server.properties.default"/></label></th>
            <th nowrap><label for="searchPlugin"><fmt:message key="server.properties.plugin"/></label></th>
            <th nowrap><label for="searchDescription"><fmt:message key="server.properties.description"/></label></th>
            <th nowrap><label for="searchDynamic"><fmt:message key="server.properties.dynamic"/></label></th>
            <th nowrap><label for="searchSetByUser"><fmt:message key="server.properties.setbyuser"/></label></th>
            <th style="text-align:center;"><fmt:message key="server.properties.edit"/></th>
            <th style="text-align:center;"><fmt:message key="server.properties.encrypt"/></th>
            <th style="text-align:center;"><fmt:message key="global.delete"/></th>
        </tr>
        <tr>
            <td nowrap>
                <input type="search"
                       id="searchName"
                       size="20"
                       value="<c:out value="${search.name}"/>"/>
                <img src="images/search-16x16.png"
                     width="16" height="16"
                     class="clickable"
                     alt="Search" title="Search"
                     style="vertical-align: middle;"
                >
            </td>
            <td nowrap>
                <input type="search"
                       id="searchValue"
                       size="20"
                       value="<c:out value="${search.value}"/>"/>
                <img src="images/search-16x16.png"
                     width="16" height="16"
                     class="clickable"
                     alt="Search" title="Search"
                     style="vertical-align: middle;"
                >
            </td>
            <td nowrap>
                <select id="searchDefaultValue">
                    <option value="" <c:if test="${search.defaultValue == ''}">selected</c:if>><fmt:message key="server.properties.search_all"/></option>
                    <option value="unchanged" <c:if test="${search.defaultValue == 'unchanged'}">selected</c:if>><fmt:message key="server.properties.default.search_unchanged"/></option>
                    <option value="changed" <c:if test="${search.defaultValue == 'changed'}">selected</c:if>><fmt:message key="server.properties.default.search_changed"/></option>
                    <option value="unknown" <c:if test="${search.defaultValue == 'unknown'}">selected</c:if>><fmt:message key="server.properties.default.unknown"/></option>
                </select>
            </td>
            <td nowrap>
                <select id="searchPlugin">
                    <option value="" <c:if test="${search.plugin == ''}">selected</c:if>><fmt:message key="server.properties.search_all"/></option>
                    <option value="none" <c:if test="${search.plugin == 'none'}">selected</c:if>><fmt:message key="server.properties.search_none"/></option>
                    <c:forEach var="plugin" items="${plugins}">
                        <option value="<c:out value="${plugin}"/>" <c:if test="${search.plugin == plugin}">selected</c:if>><c:out value="${plugin}"/></option>
                    </c:forEach>
                </select>
            </td>
            <td nowrap>
                <input type="search"
                       id="searchDescription"
                       size="20"
                       value="<c:out value="${search.description}"/>"/>
                <img src="images/search-16x16.png"
                     width="16" height="16"
                     class="clickable"
                     alt="Search" title="Search"
                     style="vertical-align: middle;"
                >
            </td>
            <td nowrap style="text-align: center">
                <select id="searchDynamic">
                    <option value="" <c:if test="${search.dynamic == ''}">selected</c:if>><fmt:message key="server.properties.search_all"/></option>
                    <option value="true" <c:if test="${search.dynamic == 'true'}">selected</c:if>><fmt:message key="server.properties.dynamic.search.true"/></option>
                    <option value="false" <c:if test="${search.dynamic == 'false'}">selected</c:if>><fmt:message key="server.properties.dynamic.search.false"/></option>
                    <option value="restart" <c:if test="${search.dynamic == 'restart'}">selected</c:if>><fmt:message key="server.properties.dynamic.search.restart"/></option>
                    <option value="unknown" <c:if test="${search.dynamic == 'unknown'}">selected</c:if>><fmt:message key="server.properties.default.unknown"/></option>
                </select>
            </td>
            <td nowrap style="text-align: center">
                <select id="searchSetByUser">
                    <option value="" <c:if test="${search.setByUser == ''}">selected</c:if>><fmt:message key="server.properties.search_all"/></option>
                    <option value="true" <c:if test="${search.setByUser == 'true'}">selected</c:if>><fmt:message key="server.properties.setbyuser.search.true"/></option>
                    <option value="false" <c:if test="${search.setByUser == 'false'}">selected</c:if>><fmt:message key="server.properties.setbyuser.search.false"/></option>
                </select>
            </td>

            <td nowrap>

            </td>
            <td nowrap>

            </td>
            <td nowrap>

            </td>
        </tr>
        </thead>
        <tbody>
        <c:forEach var="property" items="${listPager.itemsOnCurrentPage}">
            <%--@elvariable id="property" type="org.jivesoftware.admin.servlet.SystemPropertiesServlet.CompoundProperty"--%>
            <tr>
                <td class="nameColumn">
                        <%--
                        Note; wrap the property key (and value) in a span so it's easy to extract it in JavaScript
                        without any extra whitespace
                        --%>
                    <span><c:out value="${property.key}"/></span>
                </td>
                <td class="valueColumn">
                    <c:choose>
                        <c:when test="${property.hidden}">
                            <span class="hidden">hidden</span>
                        </c:when>
                        <c:when test="${property.valueAsSaved == null}">
                            <span class="hidden">none</span>
                        </c:when>
                        <c:otherwise>
                            <span title="<c:out value="${property.valueAsSaved}"/>"><c:out value="${property.valueAsSaved}"/></span>
                            <c:if test="${property.valueAsSaved != property.displayValue}">
                                (<c:out value="${property.displayValue}"/>)
                            </c:if>
                        </c:otherwise>
                    </c:choose>
                </td>
                <td class="valueColumn">
                    <c:choose>
                        <c:when test="${!property.systemProperty}">
                            <span class="hidden">unknown</span>
                        </c:when>
                        <c:when test="${property.defaultDisplayValue == null}">
                            <span class="hidden">none</span>
                        </c:when>
                        <c:otherwise>
                            <span title="<c:out value="${property.defaultDisplayValue}"/>"><c:out value="${property.defaultDisplayValue}"/></span>
                        </c:otherwise>
                    </c:choose>
                </td>
                <td><c:out value="${property.plugin}"/></td>
                <td><c:out value="${property.description}"/></td>
                <td style="text-align:center">
                    <c:if test="${property.systemProperty}">
                        <c:choose>
                            <c:when test="${property.dynamic}">
                                <img src="images/check-16x16.gif" alt="<fmt:message key="server.properties.alt_dynamic"/>">
                            </c:when>
                            <c:when test="${!property.dynamic}">
                                <img src="images/orange-dash_16x16.gif" width="16" height="16" alt="<fmt:message key="server.properties.alt_static"/>">
                                <c:if test="${property.restartRequired}">
                                    <img src="images/icon_warning-small.gif" width="16" height="16" alt="<fmt:message key="server.properties.alt_restart-required"/>">
                                </c:if>
                            </c:when>
                        </c:choose>
                    </c:if>
                </td>
                <td style="text-align:center">
                    <c:choose>
                        <c:when test="${property.setByUser}">
                            <img src="images/check-16x16.gif" alt="<fmt:message key="server.properties.alt_setbyuser"/>">
                        </c:when>
                        <c:otherwise>
                            <img src="images/orange-dash_16x16.gif" width="16" height="16" alt="<fmt:message key="server.properties.alt_default"/>">
                        </c:otherwise>
                    </c:choose>
                </td>
                <td style="text-align:center">
                    <img class="clickable"
                        src="images/edit-16x16.gif"
                        width="16" height="16"
                        data-hidden="<c:out value='${property.hidden}'/>"
                        data-encrypted="<c:out value='${property.encrypted}'/>"
                        data-null-value="<c:out value='${property.displayValue == null}'/>"
                        alt="<fmt:message key="server.properties.alt_edit"/>">
                </td>
                <td style="text-align:center">
                    <c:choose>
                        <c:when test="${property.encrypted}">
                            <img src="images/lock.gif" width="16" height="16"
                                 alt="<fmt:message key="server.properties.alt_encrypted"/>">
                        </c:when>
                        <c:otherwise>
                            <img class="clickable"
                                src="images/add-16x16.gif"
                                width="16" height="16"
                                alt="<fmt:message key="server.properties.alt_encrypt"/>">
                        </c:otherwise>
                    </c:choose>
                </td>
                <td style="text-align:center">
                    <img class="clickable"
                        src="images/delete-16x16.gif"
                        width="16" height="16"
                        alt="<fmt:message key="server.properties.alt_delete"/>">
                </td>
            </tr>
        </c:forEach>
        </tbody>
    </table>
</div>
<p><fmt:message key="global.pages"/>: [ ${listPager.pageLinks} ]</p>
<form id='paginationForm'
      data-additional-form-fields='["searchName", "searchValue", "searchDefaultValue", "searchPlugin", "searchDescription", "searchDynamic", "searchSetByUser"]'
      data-page-size='${listPager.pageSize}'>
    ${listPager.hiddenFields}
</form>

<div class="jive-table">
    <table>
        <thead>
        <tr>
            <th colspan="2">
                <span id="editPropertyTitle" style="display:none"><fmt:message key="server.properties.edit_property_title"/></span>
                <span id="newPropertyTitle"><fmt:message key="server.properties.new_property"/></span>
            </th>
        </tr>
        </thead>
        <tbody>
        <tr>
            <td>
                <label for="editPropertyName">
                    <fmt:message key="server.properties.name"/>:
                </label>
            </td>
            <td>
                <input type="text" id="editPropertyName" name="propName" size="40" maxlength="100">
            </td>
        </tr>
        <tr>
            <td>
                <label for="editPropertyValue">
                    <fmt:message key="server.properties.value"/>:
                </label>
            </td>
            <td>
                <textarea id="editPropertyValue" cols="45" rows="5" name="propValue" wrap="soft"></textarea>
            </td>
        </tr>
        <tr>
            <td>
                <label for="defaultPropertyValue">
                    <fmt:message key="server.properties.default"/>:
                </label>
            </td>
            <td>
                <span id="defaultPropertyValue"></span>
            </td>
        </tr>
        <tr>
            <td>
                <fmt:message key="server.properties.encryption"/>:
            </td>
            <td>
                <input type="radio"
                       name="encrypt"
                       id="editPropertyEncryptTrue"
                       value="true"/>
                <label for="editPropertyEncryptTrue"><fmt:message key="server.properties.encrypt_property_true"/></label>
                <br/>
                <input type="radio"
                       name="encrypt"
                       id="editPropertyEncryptFalse"
                       value="false"
                       checked/>
                <label for="editPropertyEncryptFalse"><fmt:message key="server.properties.encrypt_property_false"/></label>
            </td>
        </tr>
        </tbody>
        <tfoot>
        <tr>
            <td colspan="2">
                <input type="button" id="savePropertyBtn" value="<fmt:message key="global.save_property" />">
                <input type="button" id="cancelPropertyBtn" value="<fmt:message key="global.cancel" />">
            </td>
        </tr>
        </tfoot>
    </table>
</div>


<form method="post" id="actionForm"
      data-encrypt-confirm="<fmt:message key="server.properties.encrypt_confirm"/>"
      data-delete-confirm="<fmt:message key="server.properties.delete_confirm"/>">
    ${listPager.hiddenFields}
    <input type="hidden" name="csrf" value="<c:out value='${csrf}'/>">
    <input type="hidden" name="action">
    <input type="hidden" name="key">
    <input type="hidden" name="value">
    <input type="hidden" name="encrypt">
</form>
<%-- Refactor; disable inline JS in ListPager and use list-pager.js --%>
<% listPager.setInlineJsDisabled(true); %>
</body>
</html>
