<%--
  -
  - Copyright (C) 2017-2022 Ignite Realtime Foundation. All rights reserved.
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
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn"%>
<%@ taglib prefix="admin" uri="admin" %>

<!--
Parameters:
fields - fields from a data form (needs to be set as request scope)
nonDisplayFields - a list of field names that shouldn't be displayed (needs to be set as request scope)
listTypes - a map of field.variables to listType (user or group)
errors - a map of field.variable to error strings
detailPreFix - property prefix for additional detail to be displayed against the fields on the form
 -->

<script>
    function clearSelected(id) {
        let elements = document.getElementById(id).options;

        for (let i = 0; i < elements.length; i++) {
            elements[i].selected = false;
        }
    }

    function deleteTableRow(rowId) {
        let row = document.getElementById(rowId);
        row.parentNode.removeChild(row);
    }

    function detect_enter_keyboard(event) {

        let key_board_keycode = event.which || event.keyCode;
        if (key_board_keycode === 13) {
            event.preventDefault();
            var target = event.target || event.srcElement;
            var buttonId = target.id.split('-')[0] + '-Add';
            document.getElementById(buttonId).click();
        }
    }
</script>
<c:if test="${not empty errors}">
    <c:forEach var="error" items="${errors}">
        <admin:infoBox type="error">
            <c:out value="${error.value}" />
        </admin:infoBox>
    </c:forEach>
</c:if>
<table style="width: 1%;">
    <tbody>
        <c:forEach var="field" items="${requestScope.fields}">
            <c:if test="${not requestScope.nonDisplayFields.contains(field.variable)}">
                <tr>
                    <c:set var="isList" value="${field.type.name() eq 'list_multi' or field.type.name() eq 'jid_multi'}" />
                    <c:set var="fieldId" value="${fn:escapeXml(field.variable)}"/>
                    <td nowrap style="min-width: 300px"><label style="font-weight: bold" for="${fieldId}"><c:out value="${field.label}"/></label></td>
                    <c:choose>
                        <c:when test="${field.type.name() eq 'boolean_type'}">
                            <td style="width: 1%" rowspan="2"><input type="checkbox" name="${fieldId}" id="${fieldId}" ${field.firstValue == 1 ? 'checked="checked"' : '' } /></td>
                        </c:when>
                        <c:when test="${field.type.name() eq 'text_single'}">
                            <td style="width: 1%" rowspan="2"><input type="text" name="${fieldId}" id="${fieldId}" value="${fn:escapeXml(field.firstValue)}" style="width: 200px;" /></td>
                        </c:when>
                        <c:when test="${field.type.name() eq 'list_single'}">
                            <td style="width: 1%" rowspan="2"><select name="${fieldId}" id="${fieldId}" style="width: 200px;">
                                    <c:forEach var="option" items="${field.options}">
                                        <option value="${fn:escapeXml(option.value)}" ${option.value == field.firstValue ? 'selected' : '' }>
                                            <c:out value="${option.label ? option.label : option.value}"/>
                                        </option>
                                    </c:forEach>
                            </select></td>
                        </c:when>
                        <c:when test="${isList and not empty field.options}">
                            <td style="width: 1%" rowspan="2"><select name="${fieldId}" id="${fieldId}" style="width: 200px;" multiple>
                                    <c:forEach var="option" items="${field.options}">
                                        <option value="${fn:escapeXml(option.value)}" ${ field.values.contains(option.value) ? 'selected' : '' }>
                                            <c:out value="${option.label ? option.label : option.value }"/>
                                        </option>
                                    </c:forEach>
                            </select>
                                <button type="button" onclick="clearSelected('${fieldId}')">
                                    <fmt:message key="pubsub.form.clearSelection" />
                                </button></td>
                        </c:when>
                        <c:when test="${isList and empty field.options}">
                            <td rowspan="2">
                                <div class="jive-table">
                                    <table id="${fieldId}" style="width: 100%">
                                        <thead>
                                            <tr>
                                                <th scope="col"><fmt:message key="pubsub.form.${listTypes[field.variable]}" /></th>
                                                <th scope="col"><fmt:message key="pubsub.form.action" /></th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            <c:forEach var="value" items="${field.values}" varStatus="loop">
                                                <tr id="${fieldId}${loop.index}">
                                                    <td><input type="hidden" name="${fieldId}" value="${fn:escapeXml(value)}" /><c:out value="${value}"/></td>
                                                    <td><button type="button" onclick="deleteTableRow('${fieldId}${loop.index}')">Remove</button></td>
                                                </tr>
                                            </c:forEach>
                                            <tr>
                                                <td><input type="text" style="width: 200px;" id="${fieldId}-Additional" name="${fieldId}-Additional" onkeypress="detect_enter_keyboard(event)" /></td>
                                                <td><input type="submit" id="${fieldId}-Add" name="${fieldId}-Add" value="<fmt:message key="global.add" />"></td>
                                            </tr>
                                        </tbody>
                                    </table>
                                </div>
                            </td>
                        </c:when>
                    </c:choose>
                </tr>
                <tr>
                    <td><fmt:message var="detail" key="${param.detailPreFix}.${fn:substringAfter(field.variable, '#')}" />
                        <c:if test="${not fn:startsWith(detail, '???')}">
                            <c:out value="${detail}" />
                        </c:if>
                    </td>
                </tr>
            </c:if>
        </c:forEach>
    </tbody>
</table>
