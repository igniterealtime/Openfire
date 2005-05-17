<%@ page import="java.util.*,
                 org.jivesoftware.admin.*,
                 org.jivesoftware.messenger.XMPPServer,
				 org.jivesoftware.messenger.plugin.SearchPlugin,
                 org.jivesoftware.util.*"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%-- Define Administration Bean --%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<c:set var="admin" value="${admin.manager}" />
<% admin.init(request, response, session, application, out ); %>

<%  // Get parameters
    boolean save = request.getParameter("save") != null;
    boolean success = request.getParameter("success") != null;
    String searchName = ParamUtils.getParameter(request, "searchname");
	boolean searchEnabled = ParamUtils.getBooleanParameter(request, "searchEnabled");

	SearchPlugin plugin = (SearchPlugin) XMPPServer.getInstance().getPluginManager().getPlugin("search");

    // Handle a save
    Map errors = new HashMap();
    if (save) {
        if (searchName == null || searchName.indexOf('.') >= 0 || searchName.trim().length() < 1) {
            errors.put("searchname", "searchname");
        }
        
        if (errors.size() == 0) {
        	plugin.setServiceEnabled(searchEnabled);
            plugin.setServiceName(searchName.trim());
            response.sendRedirect("search-props-edit-form.jsp?success=true");
            return;
        }
    }
    else {
        searchName = plugin.getServiceName();
    }

    if (errors.size() == 0) {
        searchName = plugin.getServiceName();
    }
    
    searchEnabled = plugin.getServiceEnabled();
%>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  
    String title = "Search Service Properties";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(LocaleUtils.getLocalizedString("global.main"), "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "search-props-edit-form.jsp"));
    pageinfo.setPageID("search-props-edit-form");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<p>
Use the form below to edit search service settings, these settings do not affect the user search in the admin console.<br>
</p>

<%  if (success) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
            Service properties edited successfully.
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } else if (errors.size() > 0) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        Error setting the service name.
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<form action="search-props-edit-form.jsp?save" method="post">

<fieldset>
    <legend>Service Enabled</legend>
    <div>
    <p>
    You can choose to enable or disable user searches from clients. Disabling this services does not prevent user searches from the admin console.
    </p>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td width="1%">
            <input type="radio" name="searchEnabled" value="true" id="rb01"
             <%= ((searchEnabled) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb01"><b>Enabled</b></label> - Clients will be able to search for users.
            </td>
        </tr>
        <tr>
            <td width="1%">
            <input type="radio" name="searchEnabled" value="false" id="rb02"
             <%= ((!searchEnabled) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb02"><b>Disabled</b></label> - Clients will not be able to search for users.
            </td>
        </tr>
    </tbody>
    </table>
    </div>
</fieldset>

<br><br>

<fieldset>
    <legend>Service Name</legend>
    <div>
    <table cellpadding="3" cellspacing="0" border="0">

    <tr>
        <td class="c1">
           Search service name:
        </td>
        <td>
        <input type="text" size="30" maxlength="150" name="searchname"  value="<%= (searchName != null ? searchName : "") %>">.<%=XMPPServer.getInstance().getServerInfo().getName() %>

        <%  if (errors.containsKey("searchname")) { %>

            <span class="jive-error-text">
            <br>Please enter a valid name.
            </span>

        <%  } %>
        </td>
    </tr>
    </table>
    </div>
</fieldset>

<br><br>

<input type="submit" value="Save Properties">
</form>

<jsp:include page="bottom.jsp" flush="true" />
