<%@ taglib uri="core" prefix="c"%>
<%@ taglib uri="fmt" prefix="fmt" %>
<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.messenger.container.*,
                 java.text.DateFormat,
                 java.util.HashMap,
                 java.util.Map,
                 org.jivesoftware.util.*" %>

<!-- Define Administration Bean -->
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>

<!-- Define BreadCrumbs -->
<c:set var="title" value="License Details"  />
<c:set var="breadcrumbs" value="${admin.breadCrumbs}"  />
<c:set target="${breadcrumbs}" property="Home" value="main.jsp" />
<c:set target="${breadcrumbs}" property="${title}" value="license-details.jsp" />
<%@ include file="top.jsp" %>
  


<%  // Get parameters
    String licenseText = ParamUtils.getParameter(request,"licenseText");
    boolean loadNew = request.getParameter("loadNew") != null;

    // Handle a new license
    Map errors = new HashMap();
    if (loadNew) {
        // Validate
        if (licenseText == null) {
            errors.put("licenseText","licenseText");
        }
        // if no errors, continue
        if (errors.size() == 0) {
        }
    }

    // Date dateFormatter for all dates on this page:
    DateFormat dateFormatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT);
%>

<p>
The following is a summary of your <fmt:message key="short.title" bundle="${lang}" /> license.
</p>


<table class="box" cellpadding="3" cellspacing="1" border="0" width="600">
<tr class="tableHeaderBlue"><td colspan="2" align="center"><fmt:message key="short.title" bundle="${lang}" /> License Details</td></tr>
<tr>
    <td class="jive-label">
        License Type:
    </td>
    <td>

    </td>
</tr>
<tr>
    <td class="jive-label">
        Maximum Allowable Sessions:
    </td>
    <td>
       Unlimited
    </td>
</tr>
<tr>
    <td class="jive-label">
        Expiration Date:
    </td>
    <td>


            Never


    </td>
</tr>
</table>
</div>

<%--

<br>

<p>
<b>Update License</b>
</p>

<p>
You can update your license by entering new license text below and clicking
"Update License". Note, this will first verify the license before overriding your
current one.
</p>

<form action="license-details.jsp" method="post">

<div class="jive-table">
<table cellpadding="3" cellspacing="1" border="0" width="100%">
<tr valign="top">
    <td class="jive-label">
        New License Text:
    </td>
    <td>
        <textarea cols="50" rows="6" wrap="virtual" name="licenseText"></textarea>
    </td>
</tr>
</table>
</div>

<br>

<input type="submit" name="loadNew" value="Update License">

</form>

--%>

<%@ include file="footer.jsp" %>