<%@ page import="org.jivesoftware.util.LocaleUtils" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.jivesoftware.openfire.clearspace.ClearspaceManager" %>
<%@ page import="java.net.UnknownHostException" %>
<%@ page import="javax.net.ssl.SSLException" %>
<%@ page import="org.jivesoftware.openfire.clearspace.ConnectionException" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%
    boolean success = false;
    Throwable exception = null;
    String errorDetail = "";
    String exceptionDetail = "";
    Map<String, String> settings = (Map<String, String>) session.getAttribute("clearspaceSettings");
    if (settings != null) {
        ClearspaceManager manager = new ClearspaceManager(settings);
        exception = manager.testConnection();
        if (exception == null) {
            success = true;
        }
        else {
            if (exception instanceof ConnectionException) {
                ConnectionException connException = (ConnectionException) exception;
                switch (connException.getErrorType()) {
                    case AUTHENTICATION:
                        errorDetail = LocaleUtils.getLocalizedString("setup.clearspace.service.test.error-authentication");
                        break;
                    case PAGE_NOT_FOUND:
                        errorDetail = LocaleUtils.getLocalizedString("setup.clearspace.service.test.error-pageNotFound");
                        break;
                    case SERVICE_NOT_AVAIBLE:
                        errorDetail = LocaleUtils.getLocalizedString("setup.clearspace.service.test.error-serviceNotAvaitble");
                        break;
                    case UPDATE_STATE:
                        errorDetail = LocaleUtils.getLocalizedString("setup.clearspace.service.test.error-updateState");
                        break;
                    case UNKNOWN_HOST:
                        errorDetail = LocaleUtils.getLocalizedString("setup.clearspace.service.test.error-unknownHost");
                        break;
                    case OTHER:
                        errorDetail = LocaleUtils.getLocalizedString("setup.clearspace.service.test.error-connection");
                        break;
                }
            } else {
                errorDetail = LocaleUtils.getLocalizedString("setup.clearspace.service.test.error-connection");
            }

            if (exception.getCause() != null) {
                if (exception.getCause() instanceof UnknownHostException) {
                    exceptionDetail = exception.toString();
                }
                else {
                    exceptionDetail = exception.getCause().getMessage();
                }
            } else {
                exceptionDetail = exception.getMessage();
            }
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
            <p><b><fmt:message key="setup.clearspace.service.test.error-detail" /></b></p>
            <p><%= exceptionDetail %></p>
            <% } %>

        </div>
	</div>
	<!-- END connection settings test panel -->