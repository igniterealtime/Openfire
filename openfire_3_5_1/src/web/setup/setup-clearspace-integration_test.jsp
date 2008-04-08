<%@ page import="org.jivesoftware.util.LocaleUtils" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.jivesoftware.openfire.clearspace.ClearspaceManager" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%
    boolean success = false;
    String errorDetail = "";
    Map<String, String> settings = (Map<String, String>) session.getAttribute("clearspaceSettings");
    if (settings != null) {
        ClearspaceManager manager = new ClearspaceManager(settings);
        if (manager.testConnection()) {
            success = true;
        }
        else {
            errorDetail = LocaleUtils.getLocalizedString("setup.clearspace.service.test.error-connection");
        }
    }
%>
    <!-- BEGIN connection settings test panel -->
	<div class="jive-testPanel">
		<div class="jive-testPanel-content">

			<div align="right" class="jive-testPanel-close">
				<a href="#" class="lbAction" rel="deactivate"><fmt:message key="setup.clearspace.service.test.close" /></a>
			</div>


			<h2><fmt:message key="setup.clearspace.service.test.title" />: <span><fmt:message key="setup.clearspace.service.test.title-desc" /></span></h2>
            <% if (success) { %>
            <h4 class="jive-testSuccess"><fmt:message key="setup.clearspace.service.test.status-success" /></h4>

			<p><fmt:message key="setup.clearspace.service.test.status-success.detail" /></p>
            <% } else { %>
            <h4 class="jive-testError"><fmt:message key="setup.clearspace.service.test.status-error" /></h4>
            <p><%= errorDetail %></p>
            <% } %>

        </div>
	</div>
	<!-- END connection settings test panel -->