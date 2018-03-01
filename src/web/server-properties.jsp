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

<%@ page import="java.util.*,
                 org.jivesoftware.util.*,
                 org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.util.JiveGlobals"
    errorPage="error.jsp"
%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<%!
    /**
     * Make sure the String can wrap at 80 chars.
     *
     * @param str the string.
     * @return a wrappable string.
     */
    String wrappableString(String str) {
        if (str.length() <= 60) {
            return str;
        }
        int count = 0;
        StringBuffer buf = new StringBuffer();
        char [] strChars = str.toCharArray();
        for (char strChar : strChars) {
            if (Character.isWhitespace(strChar)) {
                count = 0;
            }
            count++;
            buf.append(strChar);
            if (count >= 60) {
                buf.append(" ");
                count = 0;
            }
        }
        return buf.toString();
    }

 %>

<%
    String propName = ParamUtils.getParameter(request,"propName");
    String propValue = ParamUtils.getParameter(request,"propValue",true);
    boolean edit = ParamUtils.getBooleanParameter(request,"edit");
    boolean encrypt = ParamUtils.getBooleanParameter(request,"encrypt");
    boolean save = request.getParameter("save") != null;
    boolean delete = ParamUtils.getBooleanParameter(request,"del");

    if (request.getParameter("cancel") != null) {
        response.sendRedirect("server-properties.jsp");
        return;
    }

    Map<String, String> errors = new HashMap<String, String>();
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (encrypt || save || delete) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            encrypt = false;
            save = false;
            delete = false;
            errors.put("csrf", "CSRF Failure!");
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);
    if (delete) {
        if (propName != null) {
            JiveGlobals.deleteProperty(propName);
            // Log the event
            webManager.logEvent("deleted server property "+propName, null);
            response.sendRedirect("server-properties.jsp?deletesuccess=true");
            return;
        }
    }

    if (save) {
        if (propName == null || "".equals(propName.trim()) || propName.startsWith("\"")) {
            errors.put("propName","");
        }
        if (propValue == null) {
            errors.put("propValue","");
        }
        else if (propValue.length() > 4000) {
            errors.put("propValueLength","");
        }
        if (errors.size() == 0) {
            JiveGlobals.setProperty(propName, propValue, encrypt);
            // Log the event
            webManager.logEvent("set server property "+propName, propName+" = " + (encrypt ? "********" : propValue));
            response.sendRedirect("server-properties.jsp?success=true");
            return;
        }
    }

    if (encrypt) {
        if (propName != null) {
            JiveGlobals.setPropertyEncrypted(propName, true);
            // Log the event
            webManager.logEvent("encrypted server property "+propName, null);
            response.sendRedirect("server-properties.jsp?encryptsuccess=true");
            return;
        }
    }

    if (edit) {
        propValue = JiveGlobals.getProperty(propName);
    }

    List<String> properties = JiveGlobals.getPropertyNames();
    Collections.sort(properties, new Comparator<String>() {
        public int compare(String s1, String s2) {
            return s1.toLowerCase().compareTo(s2.toLowerCase());
        }
    });
%>

<html>
    <head>
        <title><fmt:message key="server.properties.title"/></title>
        <meta name="pageID" content="server-props"/>
        <meta name="helpPage" content="manage_system_properties.html"/>
    </head>
    <body>

<p>
<fmt:message key="server.properties.info" />
</p>

<p><b><fmt:message key="server.properties.system" /></b></p>

<%  if (errors.size() > 0) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="server.properties.error" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } else if ("true".equals(request.getParameter("success"))) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="server.properties.saved" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } else if ("true".equals(request.getParameter("deletesuccess"))) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="server.properties.deleted" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } else if ("true".equals(request.getParameter("encryptsuccess"))) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="server.properties.encrypted" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<%  if (edit) { %>

    <div class="jive-info">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/info-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="server.properties.edit_property" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<%  if (request.getParameter("delerror") != null) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="server.properties.error_deleting" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<script language="JavaScript" type="text/javascript">
function doedit(propName) {
    document.propform.propName.value = propName;
    document.propform.edit.value = 'true';
    document.propform.action = document.propform.action + '#edit';
    document.propform.submit();
}
function doencrypt(propName) {
    var doencrypt = confirm('<fmt:message key="server.properties.encrypt_confirm" />');
    if (doencrypt) {
        document.propform.propName.value = propName;
        document.propform.encrypt.value = 'true';
        document.propform.action = document.propform.action + '#encrypt';
        document.propform.submit();
    }
}
function dodelete(propName) {
    var dodelete = confirm('<fmt:message key="server.properties.delete_confirm" />');
    if (dodelete) {
        document.propform.propName.value = propName;
        document.propform.del.value = 'true';
        document.propform.submit();
        return true;
    }
    else {
        return false;
    }
}
</script>

<form action="server-properties.jsp" method="post" name="propform">
<input type="hidden" name="csrf" value="${csrf}">
<input type="hidden" name="edit" value="">
<input type="hidden" name="encrypt" value="">
<input type="hidden" name="del" value="">
<input type="hidden" name="propName" value="">

<style type="text/css">
.nameColumn {
    text-overflow : ellipsis;
    overflow : hidden;
    white-space : nowrap;
    max-width : 200px;
}
.valueColumn {
    text-overflow : ellipsis;
    overflow : hidden;
    white-space : nowrap;
    max-width : 300px;
}
</style>

<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th nowrap><fmt:message key="server.properties.name" /></th>
        <th nowrap><fmt:message key="server.properties.value" /></th>
        <th style="text-align:center;"><fmt:message key="server.properties.edit" /></th>
        <th style="text-align:center;"><fmt:message key="server.properties.encrypt" /></th>
        <th style="text-align:center;"><fmt:message key="global.delete" /></th>
    </tr>
</thead>
<tbody>

    <%  if (properties.size() == 0) { %>

        <tr>
            <td colspan="4">
                <fmt:message key="server.properties.no_property" />
            </td>
        </tr>

    <%  } %>

    <% for (String n : properties) {
        String v = JiveGlobals.getProperty(n);
        v = StringUtils.replace(StringUtils.escapeHTMLTags(v), "\n", "<br>");
    %>
    <tr class="<%= (n.equals(propName) ? "hilite" : "") %>">

        <td class="nameColumn"><%= StringUtils.escapeHTMLTags(n) %></td>
        <td class="valueColumn">
                <% if (JiveGlobals.isPropertyEncrypted(n) ||
                       JiveGlobals.isPropertySensitive(n)) { %>
                <span style="color:#999;"><i>hidden</i></span>
                <% } else { %>
                <%= ("".equals(v) ? "&nbsp;" : v) %>
                <% } %>
        </td>
        <td align="center"><a href="#" onclick="doedit('<%= StringUtils.replace(StringUtils.escapeHTMLTags(n),"'","''") %>');"
                ><img src="images/edit-16x16.gif" width="16" height="16"
                      alt="<fmt:message key="server.properties.alt_edit" />" border="0"></a
                >
        </td>
        <td align="center"><%
          if (!JiveGlobals.isPropertyEncrypted(n)) { %>
            <a href="#"  onclick="doencrypt('<%= StringUtils.replace(StringUtils.escapeHTMLTags(n),"'","''") %>');" >
            <img src="images/add-16x16.gif" width="16" height="16" alt="<fmt:message key="server.properties.alt_encrypt" />" border="0"></a><% 
          } else { %>
            <img src="images/lock.gif" width="16" height="16" alt="<fmt:message key="server.properties.alt_encrypted" />" border="0"><%
          } %> 
            
        </td>
        <td align="center"><a href="#" onclick="return dodelete('<%= StringUtils.replace(StringUtils.escapeHTMLTags(n),"'","''") %>');"
                ><img src="images/delete-16x16.gif" width="16" height="16"
                      alt="<fmt:message key="server.properties.alt_delete" />" border="0"></a
                >
        </td>
    </tr>

    <% } %>

</tbody>
</table>
</div>

</form>

<br><br>

<a name="edit"></a>
<form action="server-properties.jsp" method="post" name="editform">
<input type="hidden" name="csrf" value="${csrf}">

<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th colspan="2">
            <%  if (edit) { %>
                <fmt:message key="server.properties.edit_property_title" />
            <%  } else { %>
                <fmt:message key="server.properties.new_property" />
            <%  } %>
        </th>
    </tr>
</thead>
<tbody>
    <tr valign="top">
        <td>
            <fmt:message key="server.properties.name" />:
        </td>
        <td>
            <%  if (edit) { %>

                <input type="hidden" name="propName" value="<%= StringUtils.escapeForXML(propName) %>">
                <%= StringUtils.escapeHTMLTags(propName) %>

            <%  } else { %>

                <input type="text" name="propName" size="40" maxlength="100" value="<%= (propName != null ? StringUtils.escapeForXML(propName) : "") %>">

                <%  if (errors.containsKey("propName")) { %>

                    <br><span class="jive-error-text"><fmt:message key="server.properties.enter_property_name" /></span>

                <%  } %>

            <%  } %>
        </td>
    </tr>
    <tr valign="top">
        <td>
            <fmt:message key="server.properties.value" />:
        </td>
        <td>
            <% if (JiveGlobals.isPropertyEncrypted(propName) || JiveGlobals.isPropertySensitive(propName)) { propValue = null; } %>
            <textarea cols="45" rows="5" name="propValue" wrap="virtual"><%= (propValue != null ? StringUtils.escapeHTMLTags(propValue, false) : "") %></textarea>

            <%  if (errors.containsKey("propValue")) { %>

                <br><span class="jive-error-text"><fmt:message key="server.properties.enter_property_value" /></span>

            <%  } else if (errors.containsKey("propValueLength")) { %>

                <br><span class="jive-error-text"><fmt:message key="server.properties.max_character" /></span>

            <%  } %>
        </td>
    </tr>
    <tr valign="top">
        <td>
            <fmt:message key="server.properties.encryption" />:
        </td>
        <td>
            <input type="radio" name="encrypt" value="true" <%= JiveGlobals.isPropertyEncrypted(propName) ? "checked" : "" %> /><fmt:message key="server.properties.encrypt_property_true"/><br/>
            <input type="radio" name="encrypt" value="false" <%= JiveGlobals.isPropertyEncrypted(propName) ? "" : "checked" %>/><fmt:message key="server.properties.encrypt_property_false"/>
        </td>
    </tr>
</tbody>
<tfoot>
    <tr>
        <td colspan="2">
            <input type="submit" name="save" value="<fmt:message key="global.save_property" />">
            <input type="submit" name="cancel" value="<fmt:message key="global.cancel" />">
        </td>
    </tr>
</tfoot>
</table>
</div>

</form>

    </body>
</html>
