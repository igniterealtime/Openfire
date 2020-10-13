<%@ page contentType="text/html; charset=UTF-8" %>
<%--
--%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<html>
<head>
<meta charset="UTF-8">
<meta http-equiv="X-UA-Compatible" content="IE=edge">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title><fmt:message key="short.title" /> <fmt:message key="setup.completed.setup" /></title>
<link rel="stylesheet" href="style/framework/css/bootstrap.min.css" type="text/css">
<link rel="stylesheet" href="style/framework/css/font-awesome.min.css" type="text/css">
<link rel="stylesheet" type="text/css" href="../style/setup.css">
</head>

<body style="background-image: none;">



<!-- BEGIN jive-header -->
<div id="jive-header">
    <div id="jive-logo" title="openfire"></div>
    <div id="jive-header-text"><fmt:message key="setup.title" /></div>
</div>
<!-- END jive-header -->

<!-- BEGIN jive-body -->
<div id="jive-body" style="left: 0px; padding: 30px 50px 30px 50px;">


<table cellpadding="0" cellspacing="0" border="0" width="100%">
<tr valign="top">

    <td width="98%">

        <h1>
        <fmt:message key="setup.completed.run" />
        </h1>

        <p>
        <fmt:message key="setup.completed.run_info" />
        <a href="index.jsp"><fmt:message key="short.title" /> <fmt:message key="setup.completed.run_info1" /></a>.
        <fmt:message key="setup.completed.run_info2" />
        </p>

        <br><br>
            <div class="jive_setup_launchAdmin" align="center">
                <a href="../index.jsp"><fmt:message key="setup.finished.login" /></a>
            </div>


    </td>
</tr>
</table>
</div>
<!-- END jive-body -->



<!-- BEGIN jive-footer -->
<div id="jive-footer"></div>
<!-- END jive-footer -->






</body>
</html>
