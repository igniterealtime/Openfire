<%@ taglib uri="core" prefix="c"%>
<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>
<%@ page import="org.jivesoftware.messenger.container.ServiceLookup,
                 org.jivesoftware.messenger.container.ServiceLookupFactory,
                 org.jivesoftware.messenger.container.Container"
                 errorPage="error.jsp"
%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html>
<head>
    <title>Jive Messenger Admin</title>
    <link rel="stylesheet" href="style/global.css" type="text/css">
     <script language="JavaScript" type="text/javascript">
     <!-- // code for window popups
       function helpwin(page, hashLink) {
          window.open('helpwin.jsp?f='+page+'&hash='+hashLink,'newWindow','width=500,height=550,menubar=yes,location=no,personalbar=no,scrollbars=yes,resize=yes');
       }
       //-->
    </script>
</head>

<BODY text="#000000" bottomMargin="0" vLink="#0000cc" aLink="#0000cc" link="#0000cc" bgColor="#ffffff" leftMargin="1" topMargin="1" rightMargin="1" marginwidth="0" marginheight="0">

<table cellspacing="0" border="0" width="100%" class="box">
<tr>
    <td colspan="2" width="100%" height="50">
        <!-- Include top tabs -->
        <%@ include file="tabs.jsp" %>
    </td>
</tr>
<tr valign="top">
    <td width="150" bgcolor="#F1F1ED">
        <%@ include file="sidebar.jsp" %>
    </td>
    <td>
        <table width="100%">
        <tr>
            <td>
                <!-- Add Breadcrumbs -->
                <%@ include file="breadcrumbs.jsp" %>
            </td>
        </tr>
        <tr>
            <td style="padding:10px;">