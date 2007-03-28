<%@ page import="org.jivesoftware.util.LocaleUtils" %>
<%@ page import="org.jivesoftware.openfire.ldap.LdapManager, javax.naming.*, javax.naming.ldap.LdapContext, java.net.UnknownHostException" %>
<%@ page import="java.util.Map" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%
    boolean success = false;
    String errorDetail = "";
    Map<String, String> settings = (Map<String, String>) session.getAttribute("ldapSettings");
    if (settings != null) {
        LdapManager manager = new LdapManager(settings);
        LdapContext context = null;
        try {
            context = manager.getContext();
            NamingEnumeration list = context.list("");
            list.close();
            // We were able to successfully connect to the LDAP server
            success = true;
        }
        catch (NamingException e) {
            if (e instanceof AuthenticationException) {
                errorDetail = LocaleUtils.getLocalizedString("setup.ldap.server.test.error-auth");
            }
            else if (e instanceof CommunicationException) {
                errorDetail = LocaleUtils.getLocalizedString("setup.ldap.server.test.error-connection");
                Throwable cause = e.getCause();
                if (cause != null) {
                    if (cause instanceof UnknownHostException) {
                        errorDetail = LocaleUtils.getLocalizedString("setup.ldap.server.test.error-unknownhost");
                    }
                }
            }
            else if (e instanceof InvalidNameException) {
                errorDetail = LocaleUtils.getLocalizedString("setup.ldap.server.test.invalid-name");
            }
            else if (e instanceof NameNotFoundException) {
                errorDetail = LocaleUtils.getLocalizedString("setup.ldap.server.test.name-not-found");
            }
            else {
                errorDetail = e.getExplanation();
            }
            e.printStackTrace();
        }
        finally {
            if (context != null) {
                try {
                    context.close();
                }
                catch (Exception e) {
                }
            }
        }
    }
%>
    <!-- BEGIN connection settings test panel -->
	<div class="jive-testPanel">
		<div class="jive-testPanel-content">
		
			<div align="right" class="jive-testPanel-close">
				<a href="#" class="lbAction" rel="deactivate"><fmt:message key="setup.ldap.server.test.close" /></a>
			</div>
			
			
			<h2><fmt:message key="setup.ldap.server.test.title" />: <span><fmt:message key="setup.ldap.server.test.title-desc" /></span></h2>
            <% if (success) { %>
            <h4 class="jive-testSuccess"><fmt:message key="setup.ldap.server.test.status-success" /></h4>

			<p><fmt:message key="setup.ldap.server.test.status-success.detail" /></p>
            <% } else { %>
            <h4 class="jive-testError"><fmt:message key="setup.ldap.server.test.status-error" /></h4>
            <p><%= errorDetail %></p>
            <% } %>
            
        </div>
	</div>
	<!-- END connection settings test panel -->