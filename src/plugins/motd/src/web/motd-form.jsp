<%@ page
   import="org.jivesoftware.openfire.XMPPServer,
           org.jivesoftware.openfire.plugin.MotDPlugin,
           org.jivesoftware.util.CookieUtils,
           org.jivesoftware.util.ParamUtils,
           org.jivesoftware.util.StringUtils,
           java.util.HashMap"
errorPage="error.jsp"%>
<%@ page import="java.util.Map" %>

<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>

<%
    final boolean save = request.getParameter( "save" ) != null;
    final boolean motdEnabled = ParamUtils.getBooleanParameter( request, "motdenabled", false );
    final String motdSubject = ParamUtils.getParameter( request, "motdSubject" );
    final String motdMessage = ParamUtils.getParameter( request, "motdMessage" );

    final Cookie csrfCookie = CookieUtils.getCookie( request, "csrf" );
    final String csrfParam = ParamUtils.getParameter( request, "csrf" );

    final MotDPlugin plugin = (MotDPlugin) XMPPServer.getInstance().getPluginManager().getPlugin( "motd" );

    final Map<String, String> errors = new HashMap<>();
    if ( save )
    {
        if ( csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals( csrfParam ) )
        {
            errors.put( "csrf", "CSRF Failure!" );
        }

        if ( motdSubject == null || motdSubject.trim().length() < 1 )
        {
            errors.put( "missingMotdSubject", "missingMotdSubject" );
        }

        if ( motdMessage == null || motdMessage.trim().length() < 1 )
        {
            errors.put( "missingMotdMessage", "missingMotdMessage" );
        }

        if ( errors.isEmpty() )
        {
            plugin.setEnabled( motdEnabled );
            plugin.setSubject( motdSubject );
            plugin.setMessage( motdMessage );

            response.sendRedirect( "motd-form.jsp?settingsSaved=true" );
            return;
        }
    }

    final String csrf = StringUtils.randomString( 15 );
    CookieUtils.setCookie( request, response, "csrf", csrf, -1 );

    pageContext.setAttribute( "csrf", csrf );
    pageContext.setAttribute( "errors", errors );
    pageContext.setAttribute( "motdEnabled", plugin.isEnabled() );
    pageContext.setAttribute( "motdSubject", plugin.getSubject() );
    pageContext.setAttribute( "motdMessage", plugin.getMessage() );
%>

<html>
    <head>
      <title><fmt:message key="motd.title" /></title>
      <meta name="pageID" content="motd-form"/>
    </head>
    <body>

    <c:choose>
        <c:when test="${not empty param.settingsSaved and empty errors}">
            <admin:infoBox type="success"><fmt:message key="motd.saved.success" /></admin:infoBox>
        </c:when>
        <c:otherwise>
            <c:forEach var="err" items="${errors}">
                <admin:infobox type="error">
                    <c:choose>
                        <c:when test="${err.key eq 'missingMotdSubject'}"><fmt:message key="motd.subject.missing"/></c:when>
                        <c:when test="${err.key eq 'missingMotdMessage'}"><fmt:message key="motd.message.missing"/></c:when>
                        <c:otherwise>
                            <c:if test="${not empty err.value}">
                                <c:out value="${err.value}"/>
                            </c:if>
                            (<c:out value="${err.key}"/>)
                        </c:otherwise>
                    </c:choose>
                </admin:infobox>
            </c:forEach>
        </c:otherwise>
    </c:choose>

    <p><fmt:message key="motd.directions" /></p>

    <form action="motd-form.jsp?save" method="post">

        <fmt:message key="motd.options" var="boxtitle"/>
        <admin:contentBox title="${boxtitle}">

            <p><input type="checkbox" name="motdenabled" id="motdenabled" ${ motdEnabled ? 'checked' : '' }/> <label for="motdenabled"><fmt:message key="motd.enable" /></label></p>

            <table cellpadding="3" cellspacing="0" border="0" width="100%">
                <tr>
                    <td width="5%" valign="top"><fmt:message key="motd.subject" />:&nbsp;*</td>
                    <td width="95%"><input type="text" name="motdSubject" value="${motdSubject}"></td>
                </tr>
                <tr>
                    <td width="5%" valign="top"><fmt:message key="motd.message" />:&nbsp;*</td>
                    <td width="95%"><textarea cols="45" rows="5" wrap="virtual" name="motdMessage"><c:out value="${motdMessage}"/></textarea></td>
                </tr>
                <tr>
                    <td colspan="2" style="padding-top: 10px"><input type="submit" value="<fmt:message key="motd.button.save" />"/></td>
                </tr>
            </table>

        </admin:contentBox>

        <span class="jive-description">
            * <fmt:message key="motd.required" />
        </span>

        <input type="hidden" name="csrf" value="${csrf}">

    </form>

</body>
</html>
