<%--
  -
  - Copyright (C) 2004-2008 Jive Software, 2017-2022 Ignite Realtime Foundation. All rights reserved.
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
<%@ page import="java.io.*,
                 org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.util.JiveGlobals,
                 org.jivesoftware.util.StringUtils,
                 org.jivesoftware.openfire.auth.UnauthorizedException,
                 org.jivesoftware.openfire.user.UserNotFoundException,
                 org.jivesoftware.openfire.group.GroupNotFoundException"
    isErrorPage="true"
%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<fmt:setBundle basename="openfire_i18n"/>
<%  boolean debug = "true".equals(JiveGlobals.getProperty("skin.default.debug"));
    if (debug) {
        exception.printStackTrace();
    }
%>

<%  if (exception instanceof UnauthorizedException) { %>

    <p>
    <fmt:message key="error.admin_privileges" />
    </p>

<%  } else if (exception instanceof UserNotFoundException) {
        String username = ParamUtils.getParameter(request,"username");
%>
        <p>
        <%  if (username == null) { %>
            <fmt:message key="error.requested_user_not_found" />
        <%  } else { %>
            <fmt:message key="error.specific_user_not_found">
                <fmt:param value="${username}" />
            </fmt:message>
        <%  } %>
        </p>

<%  } else if (exception instanceof GroupNotFoundException) { %>

    <p>
    <fmt:message key="error.not_found_group" />
    </p>
    
<%  } %>

<%  if (exception != null) {
        StringWriter sout = new StringWriter();
        PrintWriter pout = new PrintWriter(sout);
        exception.printStackTrace(pout);
%>
    <fmt:message key="error.exception" />
    <pre>
<%= StringUtils.escapeHTMLTags(sout.toString()) %>
    </pre>

<%  } %>
