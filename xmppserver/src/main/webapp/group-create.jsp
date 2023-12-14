<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  -
  - Copyright (C) 2004-2008 Jive Software, 2017-2023 Ignite Realtime Foundation. All rights reserved.
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

<%@ page import="org.jivesoftware.openfire.group.Group,
                 org.jivesoftware.openfire.group.GroupAlreadyExistsException,
                 org.jivesoftware.openfire.security.SecurityAuditManager,
                 org.jivesoftware.util.StringUtils"
    errorPage="error.jsp"
%>
<%@ page import="org.jivesoftware.util.ParamUtils"%>
<%@ page import="org.jivesoftware.util.CookieUtils"%>
<%@ page import="java.net.URLEncoder"%>
<%@ page import="java.util.HashMap"%>
<%@ page import="java.util.Map" %>
<%@ page import="org.slf4j.LoggerFactory" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib prefix="admin" uri="admin" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<%  webManager.init(request, response, session, application, out); %>

<%  // Get parameters //
    String groupName = ParamUtils.getParameter(request, "group");

    boolean create = request.getParameter("create") != null;
    boolean edit = request.getParameter("edit") != null;
    boolean cancel = request.getParameter("cancel") != null;
    String name = ParamUtils.getParameter(request, "name");
    String description = ParamUtils.getParameter(request, "description", true);
    
    Map<String, String> errors = new HashMap<>();
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (create || edit) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            create = false;
            edit = false;
            errors.put("csrf", "CSRF Failure!");
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    // Handle a cancel
    if (cancel) {
        if (groupName == null) {
            response.sendRedirect("group-summary.jsp");
        }
        else {
            response.sendRedirect("group-edit.jsp?group=" + URLEncoder.encode(groupName, "UTF-8"));    
        }
        return;
    }
    // Handle a request to create a group:
    if (create) {
        // Validate
        if (name == null) {
            errors.put("name", "");
        }
        // do a create if there were no errors
        if (errors.size() == 0) {
            try {
                Group newGroup = webManager.getGroupManager().createGroup(name);
                if (description != null) {
                    newGroup.setDescription(description);
                }

                if (!SecurityAuditManager.getSecurityAuditProvider().blockGroupEvents()) {
                    // Log the event
                    webManager.logEvent("created new group "+name, "description = "+description);
                }

                // Successful, so redirect
                response.sendRedirect("group-edit.jsp?creategroupsuccess=true&group=" + URLEncoder.encode(newGroup.getName(), "UTF-8"));
                return;
            }
            catch (GroupAlreadyExistsException e) {
                errors.put("groupAlreadyExists", "");
            }
            catch (Exception e) {
                errors.put("general", "");
                LoggerFactory.getLogger("group-create.jsp").warn("Problem creating group '{}' in admin console.", groupName, e);
            }
        }
    }
    // Handle a request to edit a group:
    if (edit) {
        // Validate
        if (name == null) {
            errors.put("name", "");
        }
        // do a create if there were no errors
        if (errors.size() == 0) {
            try {
                Group group = webManager.getGroupManager().getGroup(groupName);
                group.setName(name);
                if (description != null) {
                    group.setDescription(description);
                }

                if (!SecurityAuditManager.getSecurityAuditProvider().blockGroupEvents()) {
                    // Log the event
                    webManager.logEvent("edited group "+groupName, "description = "+description);
                }

                // Successful, so redirect
                response.sendRedirect("group-edit.jsp?groupChanged=true&group=" + URLEncoder.encode(group.getName(), "UTF-8"));
                return;
            }
            catch (Exception e) {
                errors.put("general", "");
                LoggerFactory.getLogger("group-create.jsp").warn("Problem editing group '{}' in admin console.", groupName, e);
            }
        }
    }

    pageContext.setAttribute("groupName", groupName);
    if (groupName != null) {
        pageContext.setAttribute("group", webManager.getGroupManager().getGroup(groupName));
    }
    pageContext.setAttribute( "errors", errors );
    pageContext.setAttribute( "name", name );
    pageContext.setAttribute( "description", description );
%>

<html>
<head>
    <c:choose>
        <c:when test="${not empty groupName}">
            <title><fmt:message key="group.edit.title" /></title>
            <meta name="pageID" content="group-create"/>
        </c:when>
        <c:otherwise>
            <title><fmt:message key="group.create.title" /></title>
            <meta name="subPageID" content="group-edit"/>
            <meta name="extraParams" content="group=${admin:urlEncode(groupName)}"/>
        </c:otherwise>
    </c:choose>
    <meta name="helpPage" content="create_a_group.html"/>
</head>
<body>

<c:set var="submit" value="${param.create}"/>

<c:if test="${not empty errors['general']}">
    <admin:infoBox type="error">
        <fmt:message key="group.create.error" />
    </admin:infoBox>
</c:if>

<c:if test="${webManager.groupManager.readOnly}">
    <div class="error">
        <fmt:message key="group.read_only"/>
    </div>
</c:if>

<p>
    <c:choose>
        <c:when test="${not empty groupName}">
            <fmt:message key="group.edit.details_info" />
        </c:when>
        <c:otherwise>
            <fmt:message key="group.create.form" />
        </c:otherwise>
    </c:choose>
</p>

<form name="f" action="group-create.jsp" method="post">
    <input type="hidden" name="csrf" value="${csrf}">

    <c:if test="${not empty groupName}">
        <input type="hidden" name="group" value="<c:out value="${groupName}"/>" id="existingName">
    </c:if>

    <!-- BEGIN create group -->
    <div class="jive-contentBoxHeader">
        <c:choose>
            <c:when test="${not empty groupName}">
                <fmt:message key="group.edit.title" />
            </c:when>
            <c:otherwise>
                <fmt:message key="group.create.new_group_title" />
            </c:otherwise>
        </c:choose>
    </div>
    <div class="jive-contentBox">
        <table>
    <tr>
        <td style="width: 1%; white-space: nowrap">
            <label for="gname"><fmt:message key="group.create.group_name" /></label> *
        </td>
        <td>
            <input type="text" name="name" size="30" maxlength="50" value="<c:out value="${name}"/>" id="gname">
        </td>
    </tr>

    <c:if test="${not empty errors['name']}">
        <tr>
            <td style="width: 1%; white-space: nowrap">&nbsp;</td>
            <td>
                <span class="jive-error-text"><fmt:message key="group.create.invalid_group_name" /></span>
            </td>
        </tr>
    </c:if>
    <c:if test="${not empty errors['groupAlreadyExists']}">
        <tr>
            <td style="width: 1%; white-space: nowrap">&nbsp;</td>
            <td>
                <span class="jive-error-text"><fmt:message key="group.create.invalid_group_info" /></span>
            </td>
        </tr>
    </c:if>

    <tr>
        <td style="width: 1%; white-space: nowrap">
            <label for="gdesc"><fmt:message key="group.create.label_description" /></label>
        </td>
        <td>
            <textarea name="description" cols="30" rows="3" maxlength="255" id="gdesc"><c:out value="${description}"/></textarea>
        </td>
    </tr>

    <c:if test="${not empty errors['description']}">
        <tr>
            <td style="width: 1%; white-space: nowrap">&nbsp;</td>
            <td>
                <span class="jive-error-text"><fmt:message key="group.create.invalid_description" /></span>
            </td>
        </tr>
    </c:if>

    <tr>
        <td></td>
        <td>
            <c:choose>
                <c:when test="${not empty groupName}">
                    <input type="submit" name="edit" value="<fmt:message key="group.edit.title" />">
                </c:when>
                <c:otherwise>
                    <input type="submit" name="create" value="<fmt:message key="group.create.create" />">
                </c:otherwise>
            </c:choose>
            <input type="submit" name="cancel" value="<fmt:message key="global.cancel" />">
        </td>
    </tr>
    </table>
    </div>
    <span class="jive-description">* <fmt:message key="group.create.required_fields" /> </span>
    <!-- END create group -->

</form>

<script>
document.f.name.focus();
</script>

<c:if test="${webManager.groupManager.readOnly}">
    <script>
      function disable() {
        let limit = document.forms[0].elements.length;
        for (let i=0;i<limit;i++) {
          document.forms[0].elements[i].disabled = true;
        }
      }
      disable();
    </script>
</c:if>

</body>
</html>
