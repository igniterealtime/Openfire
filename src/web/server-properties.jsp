<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="java.io.File,
                 java.util.*,
                 org.jivesoftware.util.*,
                 org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.messenger.JiveGlobals,
                 org.jivesoftware.admin.AdminPageBean"
    errorPage="error.jsp"
%>

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

    Map errors = new HashMap();
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

<%  // Title of this page and breadcrumbs
    String title = "System Properties";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "server-properties.jsp"));
    pageinfo.setPageID("server-props");
%>

<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<p>
Below is a list of the system properties. Values for password sensitive fields are hidden.
Long property names and values are clipped. Hold your mouse over the property name to see
the full value or to see both the full name and value, click the edit icon next to the
property.
</p>

<p><b>System Properties</b></p>

<%  if (errors.size() > 0) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        Error -- creating the property failed, see below.
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
        Property saved successfully.
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
        Property deleted successfully.
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
        Use the form below this table to edit the property value.
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
        Error deleting the property.
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
        <th nowrap>Property Name</th>
        <th nowrap>Property Value</th>
        <th style="text-align:center;">Edit</th>
        <th style="text-align:center;">Delete</th>
    </tr>
</thead>
<tbody>

    <%  if (properties.size() == 0) { %>

        <tr>
            <td colspan="4">
                No properties set.
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
                <div class="hidebox" style="width:150px;">
                <span title="<%= StringUtils.escapeHTMLTags(n) %>">
                <%= StringUtils.escapeHTMLTags(n) %>
                </span>
                </div>
            </td>
            <td>
                <div class="hidebox" style="width:350px;">
                    <%  if (n.indexOf("passwd") > -1 || n.indexOf("password") > -1 || n.indexOf("cookieKey") > -1) { %>
                        <span style="color:#999;"><i>hidden</i></span>
                    <%  } else { %>
                        <%= ("".equals(v) ? "&nbsp;" : v) %>
                    <%  } %>
                </div>
            </td>
            <td align="center"><a href="#" onclick="doedit('<%= StringUtils.replace(n,"'","''") %>');"
                ><img src="images/edit-16x16.gif" width="16" height="16" alt="Click to edit this property" border="0"></a
                >
            </td>
            <td align="center"><a href="#" onclick="return dodelete('<%= StringUtils.replace(n,"'","''") %>');"
                ><img src="images/delete-16x16.gif" width="16" height="16" alt="Click to delete this property" border="0"></a
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
                Edit property
            <%  } else { %>
                Add new property
            <%  } %>
        </th>
    </tr>
</thead>
<tbody>
    <tr valign="top">
        <td>
            Property Name:
        </td>
        <td>
            <%  if (edit) { %>

                <input type="hidden" name="propName" value="<%= StringUtils.escapeHTMLTags(propName) %>">
                <%= StringUtils.escapeHTMLTags(propName) %>

            <%  } else { %>

                <input type="text" name="propName" size="40" maxlength="100" value="<%= (propName != null ? StringUtils.escapeHTMLTags(propName) : "") %>">

                <%  if (errors.containsKey("propName")) { %>

                    <br><span class="jive-error-text">Please enter a property name.</span>

                <%  } %>

            <%  } %>
        </td>
    </tr>
    <tr valign="top">
        <td>
            Property Value:
        </td>
        <td>
            <textarea cols="45" rows="5" name="propValue" wrap="virtual"><%= (propValue != null ? StringUtils.escapeHTMLTags(propValue) : "") %></textarea>

            <%  if (errors.containsKey("propValue")) { %>

                <br><span class="jive-error-text">Please enter a property value.</span>

            <%  } else if (errors.containsKey("propValueLength")) { %>

                <br><span class="jive-error-text">1000 character max.</span>

            <%  } %>
        </td>
    </tr>
</tbody>
<tfoot>
    <tr>
        <td colspan="2">
            <input type="submit" name="save" value="Save Property">
            <input type="submit" name="cancel" value="Cancel">
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

<jsp:include page="bottom.jsp" flush="true" />