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

<%@ page import="org.jivesoftware.util.*,
                 java.net.URLEncoder"
    errorPage="error.jsp"
%>
<%@ page import="java.util.Map"%>
<%@ page import="java.util.HashMap"%><%@ page import="org.xmpp.packet.JID"%>
<%@ page import="org.jivesoftware.openfire.roster.Roster" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="org.jivesoftware.openfire.user.UserAlreadyExistsException" %>
<%@ page import="org.jivesoftware.openfire.SharedGroupException" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<% // Get parameters //
    boolean another = request.getParameter("another") != null;
    boolean add = another || request.getParameter("add") != null;
    boolean cancel = request.getParameter("cancel") != null;
    String username = ParamUtils.getParameter(request, "username");
    String jid = ParamUtils.getParameter(request, "jid");
    String nickname = ParamUtils.getParameter(request, "nickname");
    String groups = ParamUtils.getParameter(request, "groups");

    Map<String, String> errors = new HashMap<String, String>();
    // Handle a cancel
    if (cancel) {
        response.sendRedirect("user-roster.jsp?username=" + URLEncoder.encode(username, "UTF-8"));
        return;
    }
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (add) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            add = false;
            errors.put("csrf", "CSRF Failure!");
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    // Handle a request to create a user:
    if (add) {
        // do an add if there were no errors
        if (errors.size() == 0) {
            try {
                // Load the user's roster object
                Roster roster = webManager.getRosterManager().getRoster(username);

                List<String> groupList = new ArrayList<String>();
                if (groups != null) {
                    for (String group : groups.split(",")) {
                        groupList.add(group.trim());
                    }
                }

                // Load the roster item from the user's roster.
                roster.createRosterItem(new JID(jid), nickname, groupList, true, true);

                // Log the event
                webManager.logEvent("added roster item for "+username, "roster item:\njid = "+jid+"\nnickname = "+nickname+"\ngroupList = "+groupList);

                // Successful, so redirect
                if (another) {
                    response.sendRedirect("user-roster-add.jsp?success=true&username=" + URLEncoder.encode(username, "UTF-8"));
                } else {
                    response.sendRedirect("user-roster.jsp?username=" + URLEncoder.encode(username, "UTF-8") + "&addsuccess=true");
                }
                return;
            }
            catch (UserAlreadyExistsException e) {
                errors.put("usernameAlreadyExists","");
            }
            catch (SharedGroupException e) {
                errors.put("uneditableGroup","");
            }
            catch (IllegalArgumentException e) {
                errors.put("illegalJID","");
            }
            catch (Exception e) {
                errors.put("general","");
                Log.error(e);
            }
        }
    }
%>

<html>
    <head>
        <title><fmt:message key="user.roster.add.title"/></title>
        <meta name="subPageID" content="user-roster"/>
        <meta name="extraParams" content="<%= "username="+URLEncoder.encode(username, "UTF-8") %>"/>
    </head>
    <body>

<p>
    <fmt:message key="user.roster.add.info">
        <fmt:param value="<%= StringUtils.escapeForXML(username) %>"/>
    </fmt:message>
</p>

<%--<c:set var="submit" value="${param.create}"/>--%>
<%--<c:set var="errors" value="${errors}"/>--%>

<%  if (!errors.isEmpty()) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
            <td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""/></td>
            <td class="jive-icon-label">

            <% if (errors.get("general") != null) { %>
                <fmt:message key="user.roster.add.error_adding_item" />
            <% } else if (errors.get("usernameAlreadyExists") != null) { %>
                <fmt:message key="user.roster.add.item_exists" />
            <% } else if (errors.get("uneditableGroup") != null) { %>
                <fmt:message key="user.roster.add.uneditable_group" />
            <% } else if (errors.get("illegalJID") != null) { %>
                <fmt:message key="user.roster.add.illegal_jid" />
            <% } %>
            </td>
        </tr>
    </tbody>
    </table>
    </div>
    <br>

<%  } else if (request.getParameter("success") != null) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="user.roster.add.success" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<form name="f" action="user-roster-add.jsp" method="get">
        <input type="hidden" name="csrf" value="${csrf}">

<input type="hidden" name="username" value="<%= StringUtils.escapeForXML(username) %>">

    <div class="jive-contentBoxHeader">
        <fmt:message key="user.roster.add.new_item" />
    </div>
    <div class="jive-contentBox">
        <table cellpadding="3" cellspacing="0" border="0">
        <tbody>
        <tr>
            <td width="1%" nowrap><label for="jidtf"><fmt:message key="user.roster.jid" />:</label> *</td>
            <td width="99%">
                <input type="text" name="jid" size="30" maxlength="255" value="<%= ((jid!=null) ? StringUtils.escapeForXML(jid) : "") %>"
                 id="jidtf">
            </td>
        </tr>
        <tr>
            <td width="1%" nowrap>
                <label for="nicknametf"><fmt:message key="user.roster.nickname" />:</label></td>
            <td width="99%">
                <input type="text" name="nickname" size="30" maxlength="255" value="<%= ((nickname!=null) ? StringUtils.escapeForXML(nickname) : "") %>"
                 id="nicknametf">
            </td>
        </tr>
        <tr>
            <td width="1%" nowrap>
                <label for="groupstf"><fmt:message key="user.roster.groups" />:</label></td>
            <td width="99%">
                <input type="text" name="groups" size="30" maxlength="255" value="<%= ((groups!=null) ? StringUtils.escapeForXML(groups) : "") %>"
                 id="groupstf">
            </td>
        </tr>
        <tr>

            <td colspan="2" style="padding-top: 10px;">
                <input type="submit" name="add" value="<fmt:message key="user.roster.add.add" />">
                <input type="submit" name="another" value="<fmt:message key="user.roster.add.add_another" />">
                <input type="submit" name="cancel" value="<fmt:message key="global.cancel" />"></td>
        </tr>
        </tbody>
        </table>

    </div>

    <span class="jive-description">
    * <fmt:message key="user.roster.add.required" />
    </span>

</form>

    <script language="JavaScript" type="text/javascript">
    document.f.jid.focus();
    </script>

    </body>
</html>
