<%@ page import="java.util.*,
                 org.jivesoftware.admin.*,
                 org.jivesoftware.messenger.XMPPServer,
                 org.jivesoftware.util.*"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%-- Define Administration Bean --%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<c:set var="admin" value="${admin.manager}" />
<% admin.init(request, response, session, application, out ); %>


<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%
    String title = "Core Monitor Graphing Properties";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(LocaleUtils.getLocalizedString("global.main"), "../../index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "graphsetup.jsp"));
    pageinfo.setPageID("monitor-service");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<jsp:useBean id="graphBuilder" scope="session" class="org.jivesoftware.messenger.plugin.monitor.GraphBuilderBean"/>

<div style="width:100%;border:1px solid black;">

	<img id="img" src="/plugins/monitor/chart?store=store">

<div>


<link rel="stylesheet" type="text/css" href="http://www.jivesoftware.org/forums/style.jsp">

<style>

table {border-collapse:collapse;}
table td {padding:3px;}
caption {font-weight:bold;background:#5500FF;}
fieldset {margin:5px;width:750px;}
table {width:100%;}

input[type="button"] {width:65px;}
td input[type="button"] {text-align:right;}
input[type="text"] {border:black solid 1px;}

select {border:black solid 1px;}
tr.hover {background: gray;}
td.action {text-align:right;}
td>input[type="text"] {width:100%;}
td>select {width:95%;}

tfoot td {text-align:center;}
tfoot>tr:hover {width:65px;background:#AAAAAA;}
tfoot {visibility:hidden;}

thead td {font-weight:bold;}


</style>

<script type="text/javascript" src="dwr/utils.js"></script>
<script type="text/javascript" src="dwr/engine.js"></script>
    
<script>

function toggle(id) {
	var style = id.style.visibility;
	if(style == 'hidden' || style == '') 
		id.style.visibility='visible';
	else
		id.style.visibility='hidden';
}

</script>

<form ACTION="graphsetup.jsp" method="GET">



<fieldset>

<table width="100%" border=0>
<tbody>
<tr>
	<td>ObjectName</td>
	<td><select>
	<c:forEach var="mbean" items="${graphBuilder.chartableMBeans}">
		<option><c:out value="${mbean}" /></option>
	</c:forEach>
	</select></td>
	<td>Store</td>
	<td><select><c:forEach var="store" items="${graphBuilder.stores}">
	<option><c:out value="${store}"/></option>
	</c:forEach></select></td>
</tr>
</tbody>
</table>

</fieldset>


<fieldset>
<table width="100%">
<caption>Datasources</caption>
<thead>
<tr>
<td>Graph Item</td>
<td>DataSource</td>
<td>Name</td>
<td>CF Type</td>
<td class="action"><input type="button" value="add"></td>
</tr>
</thead>
<tbody>
<!-- sample data -->
<tr>
	<td>input0</td>
	<td>somefilename.rrd</td>
	<td>in</td>
	<td>AVERAGE</td>
	<td class="action"><input type="button" value="edit"></td>
<tr>

<!-- sample expr -->
<tr>
	<td>input1</td>
	<td colspan="3">in,8,+</td>
	<td class="action"><input type="button" value="edit"></td>
</tr>

</tbody>
<tfoot>
<tr>
<td><input type="text" name="name"></td>
<td><input type="text" name="name"></td>
<td><input type="text" name="name"></td>
<td><select name="confunc">
	<option>AVERAGE</option>
	<option>MAX</option>
	<option>MIN</option>
	<option>LAST</option>
	</select>
</td>
<td class="action"><input type="button" value="update"></td>
</tr>
</tfoot>
</table>

</fieldset>


<fieldset>

<table width="100%">
<caption>Graph Elements</caption>
<thead>
<tr>
	<td>Type</td>
	<td>Datasource</td>
	<td>Color</td>
	<td>Title</td>
	<td class="action"><input type="button" value="add"></td>
</tr>
</thead>

<tbody>
<!-- sample graph element -->
<tr>
	<td>LINE</td>
	<td>in8</td>
	<td>#AA333344</td>
	<td>Input</td>
	<td class="action"><input type="button" value="edit"></td>
</tr>

</tr>
</tbody>
<tfoot>
<tr>
<td><select name="type">
	<option>LINE</option>
	<option>AREA</option>
	<option>STACK</option>
	<option>HRULE</option>
	<option>VRULE</option>
	</select>
</td>
<td><input type="text" value=""></td>
<td><input type="text" value=""></td>
<td><input type="text" value=""></td>
<td class="action"><input type="button" value="edit"></td>
</tr>
</tfoot>
</table>
</fieldset>

<fieldset>
<table width="100%">
<caption>Legend</caption>
<thead>
<tr>
	<td>Name</td>
	<td>Consol Function</td>
	<td>Description</td>
	<td class="action"><input type="button" value="add" onClick="toggle(graphedit)"></td>
</tr>
</tr>
</thead>
<tbody>
<tr>
	<td>in8</td>
	<td>AVERAGE</td>
	<td>avgTotal=%.2lf %sbits/sec</td>
	<td class="action"><input type="button" value="edit"></td>
</tr>
</tbody>
<tfoot id="graphedit">
<tr>
	<td><input type="text"></td>
	<td><select name="confunc">
	<option>AVERAGE</option>
	<option>MAX</option>
	<option>MIN</option>
	<option>LAST</option>
	</select>
	</td>
	<td><input type="text"></td>
	<td class="action"><input value="update" type="button"></td>
</tr>
</tfoot>
</table
</fieldset>

</form>