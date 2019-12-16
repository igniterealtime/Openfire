<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="org.jivesoftware.util.LocaleUtils" %>
<%@ page import="org.jivesoftware.util.ParamUtils, org.jivesoftware.openfire.ldap.LdapManager, org.jivesoftware.openfire.user.UserNotFoundException, org.xmpp.packet.JID" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.net.URLDecoder" %>
<%@ page import="org.jivesoftware.util.CookieUtils" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="javax.naming.ldap.LdapName" %>
<%@ page import="javax.naming.ldap.Rdn" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="admin" prefix="admin"%>

<%
    String username = URLDecoder.decode( ParamUtils.getParameter( request, "username"), "UTF-8" );
    String password = ParamUtils.getParameter(request, "password");
    boolean ldap = "true".equals(request.getParameter("ldap"));

    if (!ldap) {
        return;
    }

    Cookie csrfCookie = CookieUtils.getCookie( request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    String errorDetail = "";
    boolean success = false;

    if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
        errorDetail = "CSRF failure!";
    }

    // Used embedded in another page. Do not reset the csrf value. // csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    Map<String, String> settings = (Map<String, String>) session.getAttribute("ldapSettings");
    Map<String, String> userSettings =
            (Map<String, String>) session.getAttribute("ldapUserSettings");
    // Run the test if password was provided and we have the ldap information
    if (errorDetail.equals( "" ) && settings != null && password != null) {
        LdapManager manager = new LdapManager(settings);
        manager.setUsernameField(userSettings.get("ldap.usernameField"));
        manager.setSearchFilter(userSettings.get("ldap.searchFilter"));
        try {
            Rdn[] userRDN = manager.findUserRDN(JID.unescapeNode(username));
            // See if the user authenticates.
            if (manager.checkAuthentication(userRDN, password)) {
                // User was able to authenticate with provided password
                success = true;
            }
            else {
                errorDetail = LocaleUtils.getLocalizedString("setup.admin.settings.test.error-password");
            }
        }
        catch (UserNotFoundException e) {
            errorDetail = LocaleUtils.getLocalizedString("setup.admin.settings.test.error-user");
        }
        catch (Exception e) {
            errorDetail = e.getMessage();
            e.printStackTrace();
        }
    }

        pageContext.setAttribute( "errorDetail", errorDetail );
        pageContext.setAttribute( "success", success );
        pageContext.setAttribute( "hasPassword", password != null );
        pageContext.setAttribute( "username", JID.unescapeNode(username) );
%>
    <!-- BEGIN connection settings test panel -->
    <div class="jive-testPanel">
        <div class="jive-testPanel-content">

            <div align="right" class="jive-testPanel-close">
                <a href="#" class="lbAction" rel="deactivate"><fmt:message key="setup.ldap.server.test.close" /></a>
            </div>

            <h2><fmt:message key="global.test" />: <span><fmt:message key="setup.admin.settings.test.title-desc" /></span></h2>
            <c:if test="${hasPassword}">
                <c:choose>
                    <c:when test="${success}">
                        <h4 class="jive-testSuccess"><fmt:message key="setup.admin.settings.test.status-success" /></h4>
                        <p><fmt:message key="setup.admin.settings.test.status-success.detail" /></p>
                    </c:when>
                    <c:otherwise>
                        <h4 class="jive-testError"><fmt:message key="setup.admin.settings.test.status-error" /></h4>
                        <p><c:out value="${errorDetail}"/></p>
                    </c:otherwise>
                </c:choose>
            </c:if>

            <c:if test="${not success}">
            <form action="setup-admin-settings.jsp" name="testform" method="post">
                <input type="hidden" name="csrf" value="${csrf}"/>
                <input type="hidden" name="ldap" value="true">
                <input type="hidden" name="test" value="true">
                <input type="hidden" name="username" value="${admin:urlEncode(username)}">
                <table cellpadding="3" cellspacing="2" border="0">
                    <tr valign="top">
                        <td class="jive-label">
                            <fmt:message key="setup.admin.settings.administrator" />:
                        </td>
                        <td>
                             <c:out value="${username}"/>
                        </td>
                        <td>
                            &nbsp;
                        </td>
                    </tr>
                    <tr valign="top">
                        <td class="jive-label">
                            <fmt:message key="setup.ldap.server.password" />:
                        </td>
                         <td>
                        <input type="password" name="password" size="20" maxlength="50"/>
                        </td>
                        <td>
                            <input type="submit" name="addAdministrator" value="<fmt:message key="global.test" />"/>
                        </td>
                    </tr>
                </table>
            </form>
            </c:if>
        </div>
    </div>
    <!-- END connection settings test panel -->
