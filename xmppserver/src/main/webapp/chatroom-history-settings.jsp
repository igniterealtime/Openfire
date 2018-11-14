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

<%@ page import="org.jivesoftware.util.*,
                 java.util.*,
                 org.jivesoftware.openfire.muc.HistoryStrategy,
                 org.jivesoftware.openfire.muc.MultiUserChatService"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<html>
    <head>
        <title><fmt:message key="chatroom.history.settings.title"/></title>
        <meta name="subPageID" content="server-chatroom-history"/>
        <meta name="helpPage" content="edit_group_chat_history_settings.html"/>
    </head>
    <body>

<%! // Global vars and methods:
    static final int ALL = 1;
    static final int NONE = 2;
    static final int NUMBER = 3;
%>

<%
    // TODO: This file is never used currently.
    // Get parameters:
    boolean update = request.getParameter("update") != null;
    int policy = ParamUtils.getIntParameter(request,"policy",-1);
    int numMessages = ParamUtils.getIntParameter(request,"numMessages",0);
    String mucname = ParamUtils.getParameter(request, "mucname");

    // Get an audit manager:
    MultiUserChatService muc = webManager.getMultiUserChatManager().getMultiUserChatService(mucname);
    HistoryStrategy historyStrat = muc.getHistoryStrategy();

    Map<String, String> errors = new HashMap<String, String>();
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (update) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            update = false;
            errors.put("csrf", "CSRF Failure!");
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);
    if (update) {
        if (policy != ALL && policy != NONE && policy != NUMBER) {
            errors.put("general", "Please choose a valid chat history policy.");
        }
        else {
            if (policy == NUMBER && numMessages <= 0) {
                errors.put("numMessages", "Please enter a valid number of messages.");
            }
        }
        if (errors.size() == 0) {
            if (policy == ALL) {
                historyStrat.setType(HistoryStrategy.Type.all);
            }
            else if (policy == NONE) {
                historyStrat.setType(HistoryStrategy.Type.none);
            }
            else if (policy == NUMBER) {
                historyStrat.setType(HistoryStrategy.Type.number);
                historyStrat.setMaxNumber(numMessages);
            }
            // Log the event
            webManager.logEvent("edited chatroom history settings", "type = "+policy+"\nmax messages = "+numMessages);
            // All done, redirect
            %>
    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="chatroom.history.settings.saved_successfully" />
        </td></tr>
    </tbody>
    </table>
    </div><br>
            <%
        }
    }

    // Set page vars
    if (errors.size() == 0) {
        if (historyStrat.getType() == HistoryStrategy.Type.all) {
            policy = ALL;
        }
        else if (historyStrat.getType() == HistoryStrategy.Type.none) {
            policy = NONE;
        }
        else if (historyStrat.getType() == HistoryStrategy.Type.number) {
            policy = NUMBER;
        }
        numMessages = historyStrat.getMaxNumber();
    }
%>

<p>
<fmt:message key="chatroom.history.settings.info_response1" />
<fmt:message key="short.title" /> <fmt:message key="chatroom.history.settings.info_response2" />
</p>

<form action="chatroom-history-settings.jsp" method="post">
    <input type="hidden" name="csrf" value="${csrf}">

<fieldset>
    <legend><fmt:message key="chatroom.history.settings.policy" /></legend>
    <div>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr valign="top" class="">
            <td width="1%" nowrap>
                <input type="radio" name="policy" value="<%= NONE %>" id="rb01"
                 <%= ((policy==NONE) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb01"><b><fmt:message key="chatroom.history.settings.label_show_title" /></b>
                </label><fmt:message key="chatroom.history.settings.label_show_content" />
            </td>
        </tr>
        <tr valign="top">
            <td width="1%" nowrap>
                <input type="radio" name="policy" value="<%= ALL %>" id="rb02"
                 <%= ((policy==ALL) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb02"><b><fmt:message key="chatroom.history.settings.label_show_Entire_title" /></b></label>
                <fmt:message key="chatroom.history.settings.label_show_Entire_content" />  
            </td>
        </tr>
        <tr valign="top" class="">
            <td width="1%" nowrap>
                <input type="radio" name="policy" value="<%= NUMBER %>" id="rb03"
                 <%= ((policy==NUMBER) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb03"><b><fmt:message key="chatroom.history.settings.label_show_message_number_title" /></b></label> - 
                <fmt:message key="chatroom.history.settings.label_show_message_number_content" />
        </tr>
        <tr valign="top" class="">
            <td width="1%" nowrap>
                &nbsp;
            </td>
            <td width="99%">
                <input type="text" name="numMessages" size="5" maxlength="10"
                 onclick="this.form.policy[2].checked=true;"
                 value="<%= ((numMessages > 0) ? ""+numMessages : "") %>"> messages
            </td>
        </tr>
    </tbody>
    </table>
    </div>
</fieldset>

<br><br>

<input type="submit" name="update" value="<fmt:message key="global.save_settings" />">

</form>

    </body>
</html>
