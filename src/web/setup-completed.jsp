<%@ taglib uri="core" prefix="c"%>
<%@ taglib uri="fmt" prefix="fmt" %>
<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>  

<%@ page import="org.jivesoftware.util.*" %>

<html>
<head>
	<title><fmt:message key="short.title" bundle="${lang}" /> Setup</title>

	<link rel="stylesheet" type="text/css" href="setup-style.css">
</head>

<body>

<span class="jive-setup-header">
<table cellpadding="8" cellspacing="0" border="0" width="100%">
<tr>
    <td width="99%">
        <fmt:message key="short.title" bundle="${lang}" /> Setup
    </td>
    <td width="1%" nowrap>
        <font size="-2" face="arial,helvetica,sans-serif" color="#ffffff">
        <b>
        Jive Software
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
        Setup Already Run
        </p>

        <p>
        It appears setup has already been run. To administer your community, please use the
        <a href="index.jsp"><fmt:message key="short.title" bundle="${lang}" /> Admin Tool</a>. To re-run
        setup, you need to stop your appserver, delete the "setup" property from the
        jive-messenger.xml file, restart Messenger then reload the setup tool.
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
