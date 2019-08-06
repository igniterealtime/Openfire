<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="org.jivesoftware.openfire.pubsub.PubSubServiceInfo,
                 org.jivesoftware.openfire.pubsub.PubSubServiceInfo.listType,
                 org.jivesoftware.util.CookieUtils,
                 org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.util.StringUtils,
                 org.xmpp.forms.DataForm,
                 java.util.HashMap,
                 java.util.Map"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters //
    boolean cancel = ParamUtils.getParameter(request,"cancel") != null;
    boolean update = ParamUtils.getParameter(request,"update") != null;
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    final Map<String, String> errors = new HashMap<>();

    boolean formSubmitted = false;
    if (csrfParam != null) {
        formSubmitted = true;
    }

    if (update) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            update = false;
            errors.put("csrf", "CSRF Failure!");
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    // Handle a cancel
    if (cancel) {
        response.sendRedirect("pubsub-node-summary.jsp");
        return;
    }

    final PubSubServiceInfo pubSubServiceInfo = webManager.getPubSubInfo();

    DataForm form = pubSubServiceInfo.getServiceConfigurationForm();

    // Handle a service update:
    if (errors.isEmpty() && update) {

        pubSubServiceInfo.configureService(pubSubServiceInfo.processForm(form, request, null));

        // Done, so redirect
        response.sendRedirect("pubsub-service-summary.jsp?updateSuccess=true");
        return;
    }

    if (formSubmitted) {
        form = pubSubServiceInfo.processForm(form, request, null);
    }

    Map<String,listType> listTypes = new HashMap<>();

    listTypes.put("pubsub#allowedToCreate", listType.user);
    listTypes.put("pubsub#sysadmins", listType.user);

    pubSubServiceInfo.validateAdditions(form, request, listTypes, errors);

    pageContext.setAttribute("fields", form.getFields());
    pageContext.setAttribute("listTypes", listTypes);
    pageContext.setAttribute("errors", errors);

%>

<html>
    <head>
        <title><fmt:message key="pubsub.service.summary.title"/></title>
        <meta name="pageID" content="pubsub-service-summary"/>
    </head>
    <body>

<c:choose>
    <c:when test="${empty errors and param.updateSuccess}">
        <admin:infobox type="success">
            <fmt:message key="pubsub.service.summary.updated" />
        </admin:infobox>
    </c:when>
    <c:otherwise>
        <c:forEach var="err" items="${errors}">
            <admin:infobox type="error">
                <c:choose>
                    <c:when test="${err.key eq 'csrf'}"><fmt:message key="global.csrf.failed" /></c:when>
                    <c:otherwise>
                        <c:if test="${not empty err.value}">
                            <fmt:message key="admin.error"/>: <c:out value="${err.value}"/>
                        </c:if>
                        (<c:out value="${err.key}"/>)
                    </c:otherwise>
                </c:choose>
            </admin:infobox>
        </c:forEach>
    </c:otherwise>
</c:choose>

<p>
    <fmt:message key="pubsub.service.summary.info" />
</p>

<form action="pubsub-service-summary.jsp">
    <input type="hidden" name="csrf" value="${csrf}">

<fieldset>
    <legend><fmt:message key="pubsub.node.delete.details_title" /></legend>
    <div>

    <c:set var="fields" value="${fields}" scope="request"/>
    <c:set var="listTypes" value="${listTypes}" scope="request"/>
    <c:set var="errors" value="${errors}" scope="request"/>

    <c:import url="pubsub-form-table.jsp">
       <c:param name="detailPreFix" value ="pubsub.service.form.detail"/>
    </c:import>

    </div>
</fieldset>

<br><br>

<input type="submit" name="update" value="<fmt:message key="global.update" />">
<input type="submit" name="cancel" value="<fmt:message key="global.cancel" />">
</form>

    </body>
</html>
