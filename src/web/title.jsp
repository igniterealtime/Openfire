<%--
  - $RCSfile$
  - $Revision$
  - $Date$
  -
  - Copyright (C) 1999-2004 Jive Software. All rights reserved.
  -
  - This software is the proprietary information of Jive Software. Use is subject to license terms.
--%>

<%@ page import="java.util.Collection,
                 java.util.Iterator,
                 org.jivesoftware.admin.AdminPageBean" %>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />

<%
    String title = pageinfo.getTitle();
    Collection breadcrumbs = pageinfo.getBreadcrumbs();
%>

<table cellpadding="0" cellspacing="0" border="0" width="100%">
<tbody>
    <tr valign="top">
        <td width="99%" id="jive-title">
            <%= (title == null ? "&nbsp;" : title) %>
        </td>
        <td width="1%" id="jive-breadcrumbs">

            <%  for (Iterator iter=breadcrumbs.iterator(); iter.hasNext(); ) {
                    AdminPageBean.Breadcrumb crumb = (AdminPageBean.Breadcrumb)iter.next();
            %>
                <a href="<%= crumb.getUrl() %>"><%= crumb.getName() %></a>

                <%  if (iter.hasNext()) { %>

                    &raquo;

                <%  } %>

            <%  } %>

        </td>
    </tr>
</tbody>
</table>