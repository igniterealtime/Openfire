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
                 org.jivesoftware.openfire.*,
                 org.jivesoftware.openfire.vcard.xep0398.PEPAvatar"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<html>
<head>
<title><fmt:message key="avatarconversion.settings.title"/></title>
<meta name="pageID" content="server-avatar-conversion"/>
</head>
<body>

<%  // Get parameters:
    boolean update = request.getParameter("update") != null;
    boolean avatarconversionEnabled = ParamUtils.getBooleanParameter(request,"avatarconversionEnabled");
    boolean deleteotherEnabled = ParamUtils.getBooleanParameter(request,"deleteotherEnabled");
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (update) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            update = false;
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

   
    if (update) {
        JiveGlobals.setProperty( PEPAvatar.PROPERTY_ENABLE_XEP398,  avatarconversionEnabled?"true":"false");
        JiveGlobals.setProperty( PEPAvatar.PROPERTY_DELETE_OTHER_AVATAR, deleteotherEnabled?"true":"false");
        // Log the event
        webManager.logEvent((avatarconversionEnabled ? "enabled" : "disabled")+" avatarconversion", null);
    %>
    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="avatarconversion.settings.update" />
        </td></tr>
    </tbody>
    </table>
    </div><br>
    <%
    
    }

    // Set page vars
    avatarconversionEnabled = Boolean.parseBoolean(JiveGlobals.getProperty( PEPAvatar.PROPERTY_ENABLE_XEP398,"false"));
    deleteotherEnabled = Boolean.parseBoolean(JiveGlobals.getProperty( PEPAvatar.PROPERTY_DELETE_OTHER_AVATAR,"false"));
%>

<p>
<fmt:message key="avatarconversion.settings.info" />
</p>

<!-- BEGIN 'Set Avatarconversion Policy' -->
<form action="avatar-conversion.jsp">
    <input type="hidden" name="csrf" value="${csrf}">
    <div class="jive-contentBoxHeader">
        <fmt:message key="avatarconversion.settings.policy" />
    </div>
    <div class="jive-contentBox">
        <table cellpadding="3" cellspacing="0" border="0">
        <tbody>
            <tr valign="top">
                <td width="1%" nowrap>
                    <input type="radio" name="avatarconversionEnabled" value="true" id="rb01"
                     <%= (avatarconversionEnabled ? "checked" : "") %>>
                </td>
                <td width="99%">
                    <label for="rb01">
                    <b><fmt:message key="avatarconversion.settings.enable" /></b> -
                    <fmt:message key="avatarconversion.settings.enable_info" />
                    </label>
                </td>
            </tr>
            <tr valign="top">
                <td width="1%" nowrap>
                    <input type="radio" name="avatarconversionEnabled" value="false" id="rb02"
                     <%= (!avatarconversionEnabled ? "checked" : "") %>>
                </td>
                <td width="99%">
                    <label for="rb02">
                    <b><fmt:message key="avatarconversion.settings.disable" /></b> -
                    <fmt:message key="avatarconversion.settings.disable_info" />
                    </label>
                </td>
            </tr>
            <tr valign="top">
                <td width="1%" nowrap>
                    <input type="checkbox" name="deleteotherEnabled" id="deleteotherEnabled"
                     <%= (deleteotherEnabled ? "checked" : "") %>>
                </td>
                <td width="99%">
                    <label for="rb02">
                     <b><fmt:message key="avatarconversion.settings.deleteotheravatar" /></b> -
                     <fmt:message key="avatarconversion.settings.deleteotheravatar_info" />
                    </label>
                </td>
            </tr>
        </tbody>
        </table>
    </div>
    <input type="submit" name="update" value="<fmt:message key="global.save_settings" />">
</form>
<!-- END 'Set Private Data Policy' -->

</body>
</html>
