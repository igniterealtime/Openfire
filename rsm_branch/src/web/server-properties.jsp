<%--
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004-2005 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="java.util.*,
                 org.jivesoftware.util.*,
                 org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.util.JiveGlobals"
    errorPage="error.jsp"
%>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />

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
        for (int i=0; i<strChars.length; i++) {
            if (Character.isWhitespace(strChars[i])) {
                count = 0;
            }
            count++;
            buf.append(strChars[i]);
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
    boolean save = request.getParameter("save") != null;
    boolean delete = ParamUtils.getBooleanParameter(request,"del");

    if (request.getParameter("cancel") != null) {
        response.sendRedirect("server-properties.jsp");
        return;
    }

    if (delete) {
        if (propName != null) {
            JiveGlobals.deleteProperty(propName);
            response.sendRedirect("server-properties.jsp?deletesuccess=true");
            return;
        }
    }

    Map<String, String> errors = new HashMap<String, String>();
    if (save) {
        if (propName == null || "".equals(propName.trim())) {
            errors.put("propName","");
        }
        if (propValue == null) {
            errors.put("propValue","");
        }
        else if (propValue.length() > 4000) {
            errors.put("propValueLength","");
        }
        if (errors.size() == 0) {
            JiveGlobals.setProperty(propName, propValue);
            response.sendRedirect("server-properties.jsp?success=true");
            return;
        }
    }

    List properties = JiveGlobals.getPropertyNames();
    Collections.sort(properties, new Comparator() {
        public int compare(Object o1, Object o2) {
            String s1 = (String)o1;
            String s2 = (String)o2;
            return s1.toLowerCase().compareTo(s2.toLowerCase());
        }
    });

    if (edit) {
        propValue = JiveGlobals.getProperty(propName);
    }
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
        <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0"></td>
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
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
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
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        <fmt:message key="server.properties.deleted" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<%  if (edit) { %>

    <div class="jive-info">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/info-16x16.gif" width="16" height="16" border="0"></td>
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
        <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0"></td>
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
function dodelete(propName) {
    var dodelete = confirm('Are you sure you want to delete this property?');
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
<input type="hidden" name="edit" value="">
<input type="hidden" name="del" value="">
<input type="hidden" name="propName" value="">

<style type="text/css">
.hidebox {
    text-overflow : ellipsis;
    overflow : hidden;
    white-space : nowrap;
}
</style>

<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th nowrap><fmt:message key="server.properties.name" /></th>
        <th nowrap><fmt:message key="server.properties.value" /></th>
        <th style="text-align:center;"><fmt:message key="server.properties.edit" /></th>
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

    <%  for (int i=0; i<properties.size(); i++) {
            String n = (String)properties.get(i);
            String v = JiveGlobals.getProperty(n);
            v = StringUtils.replace(StringUtils.escapeHTMLTags(v), "\n", "<br>");
    %>
        <tr class="<%= (n.equals(propName) ? "hilite" : "") %>">

            <td>
                <div class="hidebox" style="width:200px;">
                <span title="<%= StringUtils.escapeHTMLTags(n) %>">
                <%= StringUtils.escapeHTMLTags(n) %>
                </span>
                </div>
            </td>
            <td>
                <div class="hidebox" style="width:300px;">
                    <%  if (n.indexOf("passwd") > -1 || n.indexOf("password") > -1 || n.indexOf("cookieKey") > -1) { %>
                        <span style="color:#999;"><i>hidden</i></span>
                    <%  } else { %>
                        <span title="<%= ("".equals(v) ? "&nbsp;" : v) %>"><%= ("".equals(v) ? "&nbsp;" : v) %></span>
                    <%  } %>
                </div>
            </td>
            <td align="center"><a href="#" onclick="doedit('<%= StringUtils.replace(n,"'","''") %>');"
                ><img src="images/edit-16x16.gif" width="16" height="16" alt="<fmt:message key="server.properties.alt_edit" />" border="0"></a
                >
            </td>
            <td align="center"><a href="#" onclick="return dodelete('<%= StringUtils.replace(n,"'","''") %>');"
                ><img src="images/delete-16x16.gif" width="16" height="16" alt="<fmt:message key="server.properties.alt_delete" />" border="0"></a
                >
            </td>
        </tr>

    <%  } %>

</tbody>
</table>
</div>

</form>

<br><br>

<a name="edit"></a>
<form action="server-properties.jsp" method="post" name="editform">

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

                <input type="hidden" name="propName" value="<%= StringUtils.escapeHTMLTags(propName) %>">
                <%= StringUtils.escapeHTMLTags(propName) %>

            <%  } else { %>

                <input type="text" name="propName" size="40" maxlength="100" value="<%= (propName != null ? StringUtils.escapeHTMLTags(propName) : "") %>">

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
            <textarea cols="45" rows="5" name="propValue" wrap="virtual"><%= (propValue != null ? StringUtils.escapeHTMLTags(propValue) : "") %></textarea>

            <%  if (errors.containsKey("propValue")) { %>

                <br><span class="jive-error-text"><fmt:message key="server.properties.enter_property_value" /></span>

            <%  } else if (errors.containsKey("propValueLength")) { %>

                <br><span class="jive-error-text"><fmt:message key="server.properties.max_character" /></span>

            <%  } %>
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

<br><br><br><br><br><br>
<br><br><br><br><br><br>
<br><br><br><br><br><br>
<br><br><br><br><br><br>

    </body>
</html>