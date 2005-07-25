<%@ page import="java.util.*,
                 org.jivesoftware.admin.*,
                 org.jivesoftware.messenger.XMPPServer,
                 org.jivesoftware.util.*,
                 org.jivesoftware.messenger.plugin.PresencePlugin"
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
	boolean presencePublic = ParamUtils.getBooleanParameter(request, "presencePublic");

	PresencePlugin plugin = (PresencePlugin) XMPPServer.getInstance().getPluginManager().getPlugin("presence");

    // Handle a save
    Map errors = new HashMap();
    if (save) {
        if (errors.size() == 0) {
        	plugin.setPresencePublic(presencePublic);
            response.sendRedirect("presence-service.jsp?success=true");
            return;
        }
    }

    presencePublic = plugin.isPresencePublic();
%>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%
    String title = "Presence Servlet Properties";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(LocaleUtils.getLocalizedString("global.main"), "../../index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "presence-service.jsp"));
    pageinfo.setPageID("presence-servlet");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<p>
Use the form below to configure users presence visibility. By default, users
presence should only be visible to those users that are authorized.<br>
</p>

<%  if (success) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
            Presence servlet properties edited successfully.
        </td></tr>
    </tbody>
    </table>
    </div><br>
<% } %>

<form action="presence-service.jsp?save" method="post">

<fieldset>
    <legend>Presence visibility</legend>
    <div>
    <p>
    For security reasons, the XMPP allows users to control which users are authorized to see their presences. However, it is
    possible to configure the servlet so that anyone may get information about users presences. Use this feature with
    precaution.
    </p>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td width="1%">
            <input type="radio" name="presencePublic" value="true" id="rb01"
             <%= ((presencePublic) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb01"><b>Anyone</b> - Anyone may get presence information.</label>
            </td>
        </tr>
        <tr>
            <td width="1%">
            <input type="radio" name="presencePublic" value="false" id="rb02"
             <%= ((!presencePublic) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb02"><b>Subscribed</b> - Presence information is only visibile to authorized users.</label>
            </td>
        </tr>
    </tbody>
    </table>
    </div>
</fieldset>

<br><br>

<input type="submit" value="Save Properties">
</form>

<jsp:include page="bottom.jsp" flush="true" />
