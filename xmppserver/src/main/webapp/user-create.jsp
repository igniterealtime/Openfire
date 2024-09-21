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

<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.openfire.user.*,
                 java.net.URLEncoder,
                 gnu.inet.encoding.Stringprep,
                 gnu.inet.encoding.StringprepException,
                 java.util.stream.Collectors"
    errorPage="error.jsp"
%>
<%@ page import="java.util.List"%>
<%@ page import="java.util.Map"%>
<%@ page import="java.util.HashMap"%>
<%@ page import="org.xmpp.packet.JID"%>
<%@ page import="org.jivesoftware.openfire.security.SecurityAuditManager" %>
<%@ page import="org.jivesoftware.openfire.admin.AdminManager" %>
<%@ page import="org.jivesoftware.openfire.group.GroupNotFoundException" %>
<%@ page import="org.jivesoftware.openfire.group.Group" %>
<%@ page import="org.slf4j.LoggerFactory" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib prefix="admin" uri="admin" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters //
    boolean another = request.getParameter("another") != null;
    boolean create = another || request.getParameter("create") != null;
    boolean cancel = request.getParameter("cancel") != null;
    String username = ParamUtils.getParameter(request,"username");
    String name = ParamUtils.getParameter(request,"name");
    String email = ParamUtils.getParameter(request,"email");
    String password = ParamUtils.getParameter(request,"password");
    String passwordConfirm = ParamUtils.getParameter(request,"passwordConfirm");
    boolean isAdmin = ParamUtils.getBooleanParameter(request,"isadmin");
    String group = ParamUtils.getParameter(request,"group");
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    Map<String, String> errors = new HashMap<>();
    if (create) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            create = false;
            errors.put("csrf", "CSRF Failure!");
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    // Handle a cancel
    if (cancel) {
        response.sendRedirect("user-summary.jsp");
        return;
    }

    List<String> groupNames = webManager.getGroupManager().getGroups()
                           .stream()
                           .map(Group::getName)
                           .collect(Collectors.toList());
    // Handle a request to create a user:
    if (create) {
        // Validate
        if (username == null) {
            errors.put("username","");
        }
        else {
            try {
                username = username.trim().toLowerCase();
                username = JID.escapeNode(username);
                username = Stringprep.nodeprep(username);
            }
            catch (StringprepException se) {
                errors.put("username", "");
            }
        }
        // Trim the password. This means we don't accept spaces as passwords. We don't
        // trim the passwordConfirm as well since not trimming will ensure the user doesn't
        // think space is an ok password character.
        if (password == null || password.trim().equals("")) {
            errors.put("password","");
        }
        if (passwordConfirm == null) {
            errors.put("passwordConfirm","");
        }
        if (password != null && passwordConfirm != null && !password.equals(passwordConfirm)) {
            errors.put("passwordMatch","");
        }
        // If provider requires email, validate
        if (UserManager.getUserProvider().isEmailRequired()) {
            if (!StringUtils.isValidEmailAddress(email)) {
                errors.put("email","");
            }
        }
        // If provider requires name, validate
        if (UserManager.getUserProvider().isNameRequired()) {
            if (name == null || name.equals("")) {
                errors.put("name","");
            }
        }

        //If a group name is entered and there is no matching group, add an error
        if (group != null && !group.trim().isEmpty()){
            if (!groupNames.contains(group)) {
                errors.put("groupNotFound","");
            }
        }

        // do a create if there were no errors
        if (errors.size() == 0) {
            try {
                User newUser = webManager.getUserManager().createUser(username, password, name, email);

                if (!AdminManager.getAdminProvider().isReadOnly()) {
                    boolean isCurrentAdmin = AdminManager.getInstance().isUserAdmin(newUser.getUsername(), false);
                    if (isCurrentAdmin && !isAdmin) {
                        AdminManager.getInstance().removeAdminAccount(newUser.getUsername());
                    }
                    else if (!isCurrentAdmin && isAdmin) {
                        AdminManager.getInstance().addAdminAccount(newUser.getUsername());
                    }
                }

                if (!SecurityAuditManager.getSecurityAuditProvider().blockUserEvents()) {
                    // Log the event
                    webManager.logEvent("created new user "+username, "name = "+name+", email = "+email+", admin = "+isAdmin);
                }

                if (group != null && !group.trim().isEmpty()){
                    webManager.getGroupManager().getGroup(group).getMembers().add(webManager.getXMPPServer().createJID(username, null));
                }

                if (!SecurityAuditManager.getSecurityAuditProvider().blockGroupEvents()) {
                    // Log the event
                    webManager.logEvent("added group member to " + group, "username = " + username);
                }

                // Successful, so redirect
                if (another) {
                    response.sendRedirect("user-create.jsp?success=true");
                }
                else {
                    response.sendRedirect("user-properties.jsp?success=true&username=" +
                            URLEncoder.encode(newUser.getUsername(), "UTF-8"));
                }
                return;
            }
            catch (UserAlreadyExistsException e) {
                errors.put("usernameAlreadyExists","");
            }
            catch (Exception e) {
                errors.put("general","");
                LoggerFactory.getLogger("user-create.jsp").error("Unexpected error while creating user '{}' in admin console.", username, e);
            }
        }
    }
    pageContext.setAttribute("errors", errors);
    pageContext.setAttribute("groupNames", groupNames);
    pageContext.setAttribute("success", request.getParameter("success") != null);
%>

<html>
    <head>
        <title><fmt:message key="user.create.title"/></title>
        <meta name="pageID" content="user-create"/>
        <meta name="helpPage" content="add_users_to_the_system.html"/>
    </head>
    <body>

<% if (UserManager.getUserProvider().isReadOnly()) { %>
<div class="error">
    <fmt:message key="user.read_only"/>
</div>
<% } %>

<p><fmt:message key="user.create.info" /></p>

<%--<c:set var="submit" value="${param.create}"/>--%>
<%--<c:set var="errors" value="${errors}"/>--%>

<c:choose>
    <c:when test="${not empty errors}">
        <c:forEach var="err" items="${errors}">
            <admin:infobox type="error">
                <c:choose>
                    <c:when test="${err.key eq 'csrf'}"><fmt:message key="global.csrf.failed" /></c:when>
                    <c:when test="${err.key eq 'general'}"><fmt:message key="user.create.error_creating_account" /></c:when>
                    <c:when test="${err.key eq 'username'}"><fmt:message key="user.create.invalid_username" /></c:when>
                    <c:when test="${err.key eq 'usernameAlreadyExists'}"><fmt:message key="user.create.user_exist" /></c:when>
                    <c:when test="${err.key eq 'name'}"><fmt:message key="user.create.invalid_name" /></c:when>
                    <c:when test="${err.key eq 'email'}"><fmt:message key="user.create.invalid_email" /></c:when>
                    <c:when test="${err.key eq 'password'}"><fmt:message key="user.create.invalid_password" /></c:when>
                    <c:when test="${err.key eq 'passwordMatch'}"><fmt:message key="user.create.invalid_match_password" /></c:when>
                    <c:when test="${err.key eq 'passwordConfirm'}"><fmt:message key="user.create.invalid_password_confirm" /></c:when>
                    <c:when test="${err.key eq 'groupNotFound'}"><fmt:message key="user.create.invalid_group" /></c:when>
                    <c:otherwise>
                        <c:if test="${not empty err.value}">
                            <fmt:message key="admin.error"/>: <c:out value="${err.value}"/>
                        </c:if>
                        (<c:out value="${err.key}"/>)
                    </c:otherwise>
                </c:choose>
            </admin:infobox>
        </c:forEach>
    </c:when>
    <c:when test="${success}">
        <admin:infoBox type="success">
            <fmt:message key="user.create.created_success" />
        </admin:infoBox>
    </c:when>
</c:choose>

<form name="f" action="user-create.jsp" method="get" autocomplete="off">
    <input type="hidden" name="csrf" value="${csrf}">

    <div class="jive-contentBoxHeader">
        <fmt:message key="user.create.new_user" />
    </div>
    <div class="jive-contentBox">
        <table>
        <tbody>
        <tr>
            <td style="width: 1%; white-space: nowrap"><label for="usernametf"><fmt:message key="user.create.username" />:</label> *</td>
            <td>
                <input type="text" name="username" size="30" maxlength="75" value="<%= ((username!=null) ? StringUtils.escapeForXML(username) : "") %>"
                 id="usernametf" autocomplete="off">
            </td>
        </tr>
        <tr>
            <td style="width: 1%; white-space: nowrap"><label for="nametf"><fmt:message key="user.create.name" />:</label> <%= UserManager.getUserProvider().isNameRequired() ? "*" : "" %></td>
            <td>
                <input type="text" name="name" size="30" maxlength="75" value="<%= ((name!=null) ? StringUtils.escapeForXML(name) : "") %>"
                 id="nametf" autocomplete="off">
            </td>
        </tr>
        <tr>
            <td style="width: 1%; white-space: nowrap">
                <label for="emailtf"><fmt:message key="user.create.email" />:</label> <%= UserManager.getUserProvider().isEmailRequired() ? "*" : "" %></td>
            <td>
                <input type="text" name="email" size="30" maxlength="75" value="<%= ((email!=null) ? StringUtils.escapeForXML(email) : "") %>"
                 id="emailtf" autocomplete="off">
            </td>
        </tr>
        <tr>
            <td nowrap>
                <label for="passtf"><fmt:message key="user.create.pwd" />:</label> *
            </td>
            <td>
                <input type="password" name="password" value="" size="20" maxlength="75"
                 id="passtf" autocomplete="off">
            </td>
        </tr>
        <tr>
            <td style="width: 1%; white-space: nowrap">
                <label for="confpasstf"><fmt:message key="user.create.confirm_pwd" />:</label> *
            </td>
            <td>
                <input type="password" name="passwordConfirm" value="" size="20" maxlength="75"
                 id="confpasstf" autocomplete="off">
            </td>
        </tr>
        <% if (!AdminManager.getAdminProvider().isReadOnly()) { %>
        <tr>
            <td class="c1">
                <label for="isadmin"><fmt:message key="user.create.isadmin" /></label>
            </td>
            <td>
                <input type="checkbox" id="isadmin" name="isadmin">
                (<fmt:message key="user.create.admin_info"/>)
            </td>
        </tr>
        <% } %>
        <c:if test="${not empty groupNames}">
        <tr>
            <td class="c1">
                <label for="grouptf"><fmt:message key="user.create.group"/>:</label>
            </td>
            <td>
                <input type="text" name="group" size="30" maxlength="75" value="<%= ((group!=null) ? StringUtils.escapeForXML(group) : "") %>"
                       id="grouptf" autocomplete="off" list="groupNames" >
                <datalist id="groupNames">
                    <c:forEach var="groupName" items="${groupNames}">
                        <option value="${fn:escapeXml(groupName)}">
                            <c:out value="${groupName}"/>
                        </option>
                    </c:forEach>
                </datalist>
            </td>
        </tr>
        </c:if>
        <tr>

            <td colspan="2" style="padding-top: 10px;">
                <input type="submit" name="create" value="<fmt:message key="user.create.create" />">
                <input type="submit" name="another" value="<fmt:message key="user.create.create_another" />">
                <input type="submit" name="cancel" value="<fmt:message key="global.cancel" />"></td>
        </tr>
        </tbody>
        </table>

    </div>

    <span class="jive-description">
    * <fmt:message key="user.create.requied" />
    </span>


</form>

<script>
document.f.username.focus();
</script>

<%  // Disable the form if a read-only user provider.
if (UserManager.getUserProvider().isReadOnly()) { %>

<script>
  function disable() {
    let limit = document.forms[0].elements.length;
    for (let i=0;i<limit;i++) {
      document.forms[0].elements[i].disabled = true;
    }
  }
  disable();
</script>
    <% } %>

    </body>
</html>
