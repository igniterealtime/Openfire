<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>  

<%@ page import="org.jivesoftware.util.*" %>

<html>
<head>
	<title><fmt:message key="short.title" /> <fmt:message key="setup.completed.setup" /></title>

	<link rel="stylesheet" type="text/css" href="setup-style.css">
</head>

<body>

<span class="jive-setup-header">
<table cellpadding="8" cellspacing="0" border="0" width="100%">
<tr>
    <td width="99%">
        <fmt:message key="short.title" /> <fmt:message key="setup.completed.setup" />
    </td>
    <td width="1%" nowrap>
        <font size="-2" face="arial,helvetica,sans-serif" color="#ffffff">
        <b>
        <fmt:message key="setup.completed.jive" />
        </b>
        </font>
    </td>
</tr>
</table>
</span>
<table bgcolor="#bbbbbb" cellpadding="0" cellspacing="0" border="0" width="100%">
<tr><td><img src="images/blank.gif" width="1" height="1" border="0"></td></tr>
</table>
<table bgcolor="#dddddd" cellpadding="0" cellspacing="0" border="0" width="100%">
<tr><td><img src="images/blank.gif" width="1" height="1" border="0"></td></tr>

</table>
<table bgcolor="#eeeeee" cellpadding="0" cellspacing="0" border="0" width="100%">
<tr><td><img src="images/blank.gif" width="1" height="1" border="0"></td></tr>
</table>

<br>

<table cellpadding="0" cellspacing="0" border="0" width="100%">
<tr valign="top">

    <td width="98%">

        <p class="jive-setup-page-header">
        <fmt:message key="setup.completed.run" />
        </p>

        <p>
        <fmt:message key="setup.completed.run_info" />
        <a href="index.jsp"><fmt:message key="short.title" /> <fmt:message key="setup.completed.run_info1" /></a>.
        <fmt:message key="setup.completed.run_info2" />
        </p>

        <form action="index.jsp">

        <br><br>

        <center>
        <input type="submit" value="Login to Admin Tool">
        </center>

        </form>

    </td>
</tr>
</table>


<%@ include file="setup-footer.jsp"%>
