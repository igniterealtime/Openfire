<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="org.jivesoftware.util.LocaleUtils" %>
<%@ page import="org.jivesoftware.openfire.ldap.LdapManager, javax.naming.*, javax.naming.ldap.LdapContext, java.net.UnknownHostException" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.io.PrintWriter" %>
<%@ page import="java.io.StringWriter" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%
    boolean success = false;
    String errorDetail = "";
    Map<String, String> settings = (Map<String, String>) session.getAttribute("ldapSettings");
    if (settings != null) {
        settings.computeIfAbsent( "ldap.adminPassword", (key) -> LdapManager.getInstance().getAdminPassword() );
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
            // Store stacktrace as attribute.
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            pageContext.setAttribute( "stacktrace", sw.toString() );

            // Print stacktrace to std-out.
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

    pageContext.setAttribute( "success", success );
    pageContext.setAttribute( "errorDetail", errorDetail );
%>
    <!-- BEGIN connection settings test panel -->
    <div class="jive-testPanel">
        <div class="jive-testPanel-content" style="min-width: 600px;">
        
            <div align="right" class="jive-testPanel-close">
                <a href="#" class="lbAction" rel="deactivate"><fmt:message key="setup.ldap.server.test.close" /></a>
            </div>
            
            
            <h2><fmt:message key="setup.ldap.server.test.title" />: <span><fmt:message key="setup.ldap.server.test.title-desc" /></span></h2>
            <c:choose>
                <c:when test="${success}">
                    <h4 class="jive-testSuccess"><fmt:message key="setup.ldap.server.test.status-success" /></h4>
                    <p><fmt:message key="setup.ldap.server.test.status-success.detail" /></p>
                </c:when>
                <c:otherwise>
                    <h4 class="jive-testError"><fmt:message key="setup.ldap.server.test.status-error" /></h4>
                    <p><c:out value="${errorDetail}"/></p>
                    <c:if test="${not empty stacktrace}">
                        <h3 style="margin: 8px 0 3px;"><fmt:message key="setup.ldap.server.test.stacktrace" />:</h3>
                        <p><textarea style="width: 100%; max-width: 100%; height: 160px; font-size: smaller"><c:out value="${stacktrace}"/></textarea></p>
                    </c:if>
                </c:otherwise>
            </c:choose>

        </div>
    </div>
    <!-- END connection settings test panel -->
