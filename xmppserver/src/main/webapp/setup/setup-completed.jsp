<%--
  -
  - Copyright (C) 2004-2007 Jive Software, 2017-2022 Ignite Realtime Foundation. All rights reserved.
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     http://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
--%>
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


<table>
<tr>

    <td style="width: 98%">

        <h1>
        <fmt:message key="setup.completed.run" />
        </h1>

        <p>
        <fmt:message key="setup.completed.run_info" />
        <a href="index.jsp"><fmt:message key="short.title" /> <fmt:message key="setup.completed.run_info1" /></a>.
        <fmt:message key="setup.completed.run_info2" />
        </p>

        <br><br>
            <div class="jive_setup_launchAdmin" style="text-align: center">
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
