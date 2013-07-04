<%@ page import="java.util.*,
                 org.jivesoftware.openfire.XMPPServer,
                 org.jivesoftware.openfire.plugin.SearchPlugin,
                 org.jivesoftware.openfire.user.*,
                 org.jivesoftware.util.*"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%  // Get parameters
    boolean save = request.getParameter("save") != null;
    boolean success = request.getParameter("success") != null;
    String searchName = ParamUtils.getParameter(request, "searchname");
    boolean searchEnabled = ParamUtils.getBooleanParameter(request, "searchEnabled");
	boolean groupOnly = ParamUtils.getBooleanParameter(request, "groupOnly");
    
    SearchPlugin plugin = (SearchPlugin) XMPPServer.getInstance().getPluginManager().getPlugin("search");

    // Handle a save
    Map<String,String> errors = new HashMap<String,String>();
    if (save) {
        if (searchName == null || searchName.indexOf('.') >= 0 || searchName.trim().length() < 1) {
            errors.put("searchname", "searchname");
        }
        else {
            if (errors.size() == 0) {
                plugin.setServiceEnabled(searchEnabled);
                plugin.setServiceName(searchName.trim());

                ArrayList<String> excludedFields = new ArrayList<String>();
                for (String field : UserManager.getInstance().getSearchFields()) {
                    if (!ParamUtils.getBooleanParameter(request, field)) {
                         excludedFields.add(field);
                    }
                }
                plugin.setExcludedFields(excludedFields);
				plugin.setGroupOnly(groupOnly);
                response.sendRedirect("search-props-edit-form.jsp?success=true");
                return;
            }
        }
    }
    else {
        searchName = plugin.getServiceName();
    }

    if (errors.size() == 0) {
        searchName = plugin.getServiceName();
    }
    
    searchEnabled = plugin.getServiceEnabled();
    Collection<String> searchableFields = plugin.getFilteredSearchFields();
    groupOnly = plugin.isGroupOnly();
%>

<html>
    <head>
        <title><fmt:message key="search.props.edit.form.title" /></title>
        <meta name="pageID" content="search-props-edit-form"/>
    </head>
    <body>

<p>
<fmt:message key="search.props.edit.form.directions" />
</p>

<%  if (success) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
            <fmt:message key="search.props.edit.form.successful_edit" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } else if (errors.size() > 0) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
            <fmt:message key="search.props.edit.form.error" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<form action="search-props-edit-form.jsp?save" method="post">

<div class="jive-contentBoxHeader"><fmt:message key="search.props.edit.form.service_enabled" /></div>
<div class="jive-contentBox">
    <p>
    <fmt:message key="search.props.edit.form.service_enabled_directions" />
    </p>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td width="1%">
            <input type="radio" name="searchEnabled" value="true" id="rb01"
             <%= ((searchEnabled) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb01"><b><fmt:message key="search.props.edit.form.enabled" /></b></label> - <fmt:message key="search.props.edit.form.enabled_details" />
            </td>
        </tr>
        <tr>
            <td width="1%">
            <input type="radio" name="searchEnabled" value="false" id="rb02"
             <%= ((!searchEnabled) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb02"><b><fmt:message key="search.props.edit.form.disabled" /></b></label> - <fmt:message key="search.props.edit.form.disabled_details" />
            </td>
        </tr>
    </tbody>
    </table>
</div>

<br>

<div class="jive-contentBoxHeader"><fmt:message key="search.props.edit.form.service_name" /></div>
<div class="jive-contentBox">
    <table cellpadding="3" cellspacing="0" border="0">
    <tr>
        <td class="c1">
           <fmt:message key="search.props.edit.form.search_service_name" />:
        </td>
        <td>
        <input type="text" size="30" maxlength="150" name="searchname"  value="<%= (searchName != null ? searchName : "") %>">.<%=XMPPServer.getInstance().getServerInfo().getXMPPDomain() %>

        <%  if (errors.containsKey("searchname")) { %>

            <span class="jive-error-text">
            <br><fmt:message key="search.props.edit.form.search_service_name_details" />
            </span>

        <%  } %>
        </td>
    </tr>
    </table>
</div>

<br>

<div class="jive-contentBoxHeader"><fmt:message key="search.props.edit.form.searchable_fields" /></div>
<div class="jive-contentBox">
    <p>
    <fmt:message key="search.props.edit.form.searchable_fields_details" />
    </p>
    <table class="jive-table" cellpadding="3" cellspacing="0" border="0" width="400">
        <tr>
            <th align="center" width="1%"><fmt:message key="search.props.edit.form.enabled" /></th>
            <th align="left" width="99%"><fmt:message key="search.props.edit.form.fields" /></th>
        </tr>
        <% for (String field : UserManager.getInstance().getSearchFields()) { %>
        <tr>
            <td align="center" width="1%"><input type="checkbox"  <%=searchableFields.contains(field) ? "checked" : "" %>  name="<%=field %>"></td>
            <td align="left" width="99%"><%=field %></td>
        </tr>
        <% } %>
    </table>
</div>

<br>

<div class="jive-contentBoxHeader"><fmt:message key="search.props.edit.form.search_scope" /></div>
<div class="jive-contentBox">
    <p>
    <fmt:message key="search.props.edit.form.search_scope_directions" />
    </p>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td width="1%">
            <input type="radio" name="groupOnly" value="false" id="rb-grouponly-01"
             <%= ((!groupOnly) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb-grouponly-01"><b><fmt:message key="search.props.edit.form.search_scope_anyone" /></b></label> - <fmt:message key="search.props.edit.form.search_scope_anyone_details" />
            </td>
        </tr>
        <tr>
            <td width="1%">
            <input type="radio" name="groupOnly" value="true" id="rb-grouponly-02"
             <%= ((groupOnly) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb-grouponly-02"><b><fmt:message key="search.props.edit.form.search_scope_groups" /></b></label> - <fmt:message key="search.props.edit.form.search_scope_groups_details" />
            </td>
        </tr>
    </tbody>
    </table>
</div>

<br>


<input type="submit" value="<fmt:message key="search.props.edit.form.save_properties" />">
</form>

</body>
</html>