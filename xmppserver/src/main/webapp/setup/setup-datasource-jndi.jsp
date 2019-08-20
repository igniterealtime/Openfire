<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="org.jivesoftware.database.DbConnectionManager,
                 org.jivesoftware.database.JNDIDataSourceProvider,
                 org.jivesoftware.openfire.XMPPServer,
                 javax.naming.Binding,
                 javax.naming.Context" %>
<%@ page import="javax.naming.InitialContext"%>
<%@ page import="javax.naming.NamingEnumeration"%>
<%@ page import="java.util.HashMap"%>
<%@ page import="java.util.Map"%>
<%@ page import="org.jivesoftware.util.*" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%
    // Redirect if we've already run setup:
    if (!XMPPServer.getInstance().isSetupMode()) {
        response.sendRedirect("setup-completed.jsp");
        return;
    }
%>

<%
    boolean embeddedMode = false;
    try {
        ClassUtils.forName("org.jivesoftware.openfire.starter.ServerStarter");
        embeddedMode = true;
    }
    catch (Exception ignored) {}
    // check for embedded mode:
    if (embeddedMode) {
        // disallow jndi, redirect back to main db page:
        response.sendRedirect("setup-datasource-settings.jsp");
        return;
    }
%>

<%  // Get parameters
    String jndiName = ParamUtils.getParameter(request,"jndiName");
    String jndiNameMode = ParamUtils.getParameter(request,"jndiNameMode");
    boolean doContinue = request.getParameter("continue") != null;

    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    // Handle a continue request:
    Map<String,String> errors = new HashMap<>();

    if (doContinue) {
        if ( csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals( csrfParam ) ) {
            doContinue = false;
            errors.put( "general", "CSRF Failure!" );
        }
    }

    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    if (doContinue) {
        String lookupName = null;
        // Validate the fields:
        if ("custom".equals(jndiNameMode) && jndiName == null) {
            errors.put("jndiName","Please enter a valid JNDI name.");
        }
        else if ((jndiNameMode == null || "custom".equals(jndiNameMode)) && jndiName != null) {
            lookupName = jndiName;
        }
        else {
            lookupName = jndiNameMode;
        }
        // if no errors, continue
        if (errors.size() == 0) {
            // Set the JNDI connection class property in the jive props file
            JiveGlobals.setProperty("connectionProvider.className",
                    "org.jivesoftware.database.JNDIDataSourceProvider");
            // Save the name (must do this *first* before initializing
            // the JNDIDataSourceProvider
            JiveGlobals.setXMLProperty("database.JNDIProvider.name",lookupName);
            // Use the Jive default connection provider
            JNDIDataSourceProvider conProvider = new JNDIDataSourceProvider();
            // Set the provider in the connection manager
            DbConnectionManager.setConnectionProvider(conProvider);
            // Try to establish a connection to the datasource
            if (DbConnectionManager.testConnection(errors)) {
                // Finished, so redirect
                response.sendRedirect("setup-admin-settings.jsp");
                return;
            }
        }
    }
    pageContext.setAttribute("localizedShortTitle", LocaleUtils.getLocalizedString("short.title") );
    pageContext.setAttribute("errors", errors);
    pageContext.setAttribute("jndiName", jndiName);
%>

<html>
    <head>
        <title><fmt:message key="setup.datasource.jndi.setting" /></title>
        <meta name="currentStep" content="2"/>
    </head>
<body>

<p class="jive-setup-page-header">
<fmt:message key="setup.datasource.jndi.setting" />
</p>

<p>
<fmt:message key="setup.datasource.jndi.setting_info">
    <fmt:param value="${localizedShortTitle}" />
    <fmt:param value="<tt>java:comp/env/jdbc/[DataSourceName]</tt>" />
</fmt:message>
</p>

<c:if test="${not empty errors and empty errors['jndiName']}">
    <p class="jive-error-text">
        <c:out value="${errors['general']}"/>
    </p>
</c:if>

<form action="setup-datasource-jndi.jsp" name="jndiform" method="post">
    <input type="hidden" name="csrf" value="${csrf}">

<%  boolean isLookupNames = false;
    Context context = null;
    NamingEnumeration<Binding> ne = null;
    try {
        context = new InitialContext();
        ne = context.listBindings("java:comp/env/jdbc");
        isLookupNames = ne.hasMore();
    }
    catch (Exception e) {}

    pageContext.setAttribute( "isLookupNames", isLookupNames );
    pageContext.setAttribute( "namingEnumeration", ne );

%>

<c:choose>
    <c:when test="${isLookupNames}">
        <label for="jndiName">fmt:message key="setup.datasource.jndi.name" /></label>
        <input type="text" name="jndiName" id="jndiName" size="30" maxlength="100" value="${not empty jndiName ? fn:escapeXml(jndiName) : ''}">
    </c:when>
    <c:otherwise>

    <table cellpadding="3" cellspacing="3" border="0">
    <tr>
        <td><input type="radio" name="jndiNameMode" value="custom"></td>
        <td>
            <span onclick="document.jndiform.jndiName.focus();"><label for="jndiName"><fmt:message key="setup.datasource.jndi.custom" /></label></span>
            &nbsp;
            <input type="text" name="jndiName" id="jndiName" size="30" maxlength="100" value="${not empty jndiName ? fn:escapeXml(jndiName) : ''}" onfocus="this.form.jndiNameMode[0].checked=true;">
            <c:if test="${not empty errors['jndiName']}">
                <span class="jive-error-text"><br>
                    <fmt:message key="setup.datasource.jndi.valid_name" />
                </span>
            </c:if>
        </td>
    </tr>
    <c:if test="${not empty namingEnumeration}">
        <c:forEach items="${namingEnumeration}" var="binding" varStatus="status">
            <tr>
                <td><input type="radio" name="jndiNameMode" value="java:comp/env/jdbc/${binding.name}" id="rb${status.index}"></td>
                <td>
                    <label for="rb${status.index}" style="font-weight:normal"
                    >java:comp/env/jdbc/<b><c:out value="${binding.name}"/></b></label>
                </td>
            </tr>
        </c:forEach>
    </c:if>
    </table>

    </c:otherwise>
</c:choose>

<br><br>

<hr size="0">

<div align="right">
    <input type="submit" name="continue" value=" <fmt:message key="global.continue" /> ">
    <br>
    <fmt:message key="setup.datasource.jndi.note" />
</div>

</form>

<script language="JavaScript" type="text/javascript">
<!--
document.jndiform.jndiName.focus();
//-->
</script>

</body>
</html>
