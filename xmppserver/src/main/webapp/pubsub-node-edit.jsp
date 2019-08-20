<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="org.jivesoftware.openfire.pubsub.LeafNode,
                 org.jivesoftware.openfire.pubsub.Node,
                 org.jivesoftware.openfire.pubsub.PubSubServiceInfo,
                 org.jivesoftware.openfire.pubsub.PubSubServiceInfo.listType,
                 org.jivesoftware.util.CookieUtils,
                 org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.util.StringUtils,
                 org.xmpp.forms.DataForm,
                 java.util.ArrayList,
                 java.util.HashMap,
                 java.util.Map,
                 java.net.URLEncoder"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib uri="admin" prefix="admin" %>
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

    String nodeID = ParamUtils.getParameter(request,"nodeID");
    String reason = ParamUtils.getParameter(request,"reason");

    // Handle a cancel
    if (cancel) {
        response.sendRedirect("pubsub-node-summary.jsp");
        return;
    }

    final PubSubServiceInfo pubSubServiceInfo = webManager.getPubSubInfo();

    // Load the node object
    Node node = pubSubServiceInfo.getNode(nodeID);

    DataForm form = node.getConfigurationForm();

    //Field that will not be returned to the server, i.e. cannot be edited on this page
    ArrayList<String> nonReturnFields = new ArrayList<String>();
    //This is the parent collection, this form is not a great way to edit this,
    //and the back end has issues when the root Collection is the parent.
    nonReturnFields.add("pubsub#collection");

    //owner and publishers are more easily managed through the affiliates admin pages
    nonReturnFields.add("pubsub#owner");
    nonReturnFields.add("pubsub#publisher");

    //replyto and replyroom were removed from XEP-60 at version 1.13
    nonReturnFields.add("pubsub#replyto");
    nonReturnFields.add("pubsub#replyroom");

    //nodes that will not be displayed in the form.
    ArrayList<String> nonDisplayFields = new ArrayList<String>();
    //changing nodes from leaf to collection is a bad idea, but the value is required in the returned form.
    nonDisplayFields.add("pubsub#node_type");

    //fields not being returned are not shown
    nonDisplayFields.addAll(nonReturnFields);

    // Handle update:
    if (update) {
        // Delete the node
        if (node != null) {

            node.configure(pubSubServiceInfo.processForm(form, request, nonReturnFields));

            // Log the event
            webManager.logEvent("Configuration updated for " + nodeID, null);
        }
        // Done, so redirect
        response.sendRedirect( "pubsub-node-edit.jsp?nodeID=" + URLEncoder.encode( nodeID, "UTF-8" ) + "&updateSuccess=true");
        return;
    }

    if (formSubmitted) {
        form = pubSubServiceInfo.processForm(form, request, nonReturnFields);
    }

    Map<String,listType> listTypes = new HashMap<>();

    listTypes.put("pubsub#contact", listType.user);
    listTypes.put("pubsub#replyto", listType.user);
    listTypes.put("pubsub#roster_groups_allowed", listType.group);

    pubSubServiceInfo.validateAdditions(form, request, listTypes, errors);

    pageContext.setAttribute("node", node);
    pageContext.setAttribute("fields", form.getFields());
    pageContext.setAttribute("nonDisplayFields", nonDisplayFields);
    pageContext.setAttribute("listTypes", listTypes);
    pageContext.setAttribute("errors", errors);

%>

<html>
    <head>
        <title><fmt:message key="pubsub.node.edit.title"/></title>
        <meta name="subPageID" content="pubsub-node-edit"/>
        <meta name="extraParams" content="nodeID=${admin:urlEncode(node.nodeID)}"/>
        <script>
        function clearSelected(name){
            var elements = document.getElementById(name).options;

            for(var i = 0; i < elements.length; i++){
              elements[i].selected = false;
            }
          }
        </script>
    </head>
    <body>

    <c:choose>
        <c:when test="${empty errors and param.updateSuccess}">
            <admin:infobox type="success">
                <fmt:message key="pubsub.node.edit.updated" />
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
        <fmt:message key="pubsub.node.edit.info" />
        <b>
            <c:out value="${node.nodeID}"/>
        </b>
    </p>

    <div class="jive-table">
    <table cellpadding="0" cellspacing="0" border="0" width="100%">
    <thead>
        <tr>
            <th scope="col"><fmt:message key="pubsub.node.summary.id" /></th>
            <th scope="col"><fmt:message key="pubsub.node.summary.creator" /></th>
            <th scope="col"><fmt:message key="pubsub.node.summary.items" /></th>
            <th scope="col"><fmt:message key="pubsub.node.summary.affiliates" /></th>
            <th scope="col"><fmt:message key="pubsub.node.summary.subscribers" /></th>
            <th scope="col"><fmt:message key="pubsub.node.summary.created" /></th>
            <th scope="col"><fmt:message key="pubsub.node.summary.modified" /></th>
        </tr>
    </thead>
    <tbody>
        <tr>
            <td><c:out value="${node.nodeID}"/></td>
            <td><c:out value="${node.creator}"/></td>
            <td><c:out value="${node.publishedItems.size()}"/></td>
            <td><c:out value="${node.allAffiliates.size()}"/></td>
            <td><c:out value="${node.allSubscriptions.size()}"/></td>
            <td><fmt:formatDate type="both" dateStyle="medium" timeStyle="short" value="${node.creationDate}" /></td>
            <td><fmt:formatDate type="both" dateStyle="medium" timeStyle="short" value="${node.modificationDate}" /></td>
        </tr>
    </tbody>
    </table>
    </div>


<form action="pubsub-node-edit.jsp">
    <input type="hidden" name="csrf" value="${csrf}">
    <input type="hidden" name="nodeID" value="${fn:escapeXml(node.nodeID)}">
    <br>

<fieldset>
    <legend><fmt:message key="pubsub.node.edit.details_title" /></legend>
    <div>

    <c:set var="fields" value="${fields}" scope="request"/>
    <c:set var="nonDisplayFields" value="${nonDisplayFields}" scope="request"/>
    <c:set var="listTypes" value="${listTypes}" scope="request"/>
    <c:set var="errors" value="${errors}" scope="request"/>

    <c:import url="pubsub-form-table.jsp">
       <c:param name="detailPreFix" value ="pubsub.node.edit.detail"/>
    </c:import>

    </div>

</fieldset>
<br>
        <input type="submit" name="update" value="<fmt:message key="global.update" />">
        <input type="submit" name="cancel" value="<fmt:message key="global.cancel" />">
</form>

    </body>
</html>
