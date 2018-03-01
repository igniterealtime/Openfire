<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.openfire.pubsub.Node,
                 org.jivesoftware.openfire.pubsub.PubSubServiceInfo,
                 org.jivesoftware.openfire.pubsub.PubSubServiceInfo.listType,
                 org.xmpp.forms.DataForm,
                 org.xmpp.forms.FormField,
                 org.xmpp.forms.FormField.Type,
                 java.net.URLEncoder,
                 java.util.*"
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

    boolean formSubmitted = false;
    if (csrfParam != null) {
        formSubmitted = true;
    }

    if (update) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            update = false;
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
    if (update) {

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

    Map<String,String> errors = new HashMap<>();

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

<p>
    <fmt:message key="pubsub.service.summary.info" />
</p>

<c:if test="${param.updateSuccess}">
    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="pubsub.service.summary.updated" />
        </td></tr>
    </tbody>
    </table>
    </div><br>
</c:if>

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
