<%@ taglib uri="core" prefix="c"%>
<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>


<c:set var="sidebar" value="${param.sidebar}" />
<c:if test="${empty sidebar}" >
  <c:set var="sidebar" value="${sessionScope.jive.admin.sidebarTab}" />
    <c:if test="${empty sidebar}">
       <c:set var="sidebar" value="server" />   
    </c:if>
</c:if>

<!-- Handle SideBar -->
<c:set var="s" value="${pageScope.sbar}" />
<c:if test="${!empty s}">
  <c:set var="sidebar" value="${s}" />
</c:if>  

<jsp:useBean id="sidebar" type="java.lang.String"  />


    

<div style="background-color:#F1F1ED">
<table bgcolor="#bbbbbb" cellpadding="0" cellspacing="0" border="0" width="100%">
<tr><td><img src="images/blank.gif" width="1" height="1" border="0"></td></tr>
</table>
<table bgcolor="#dddddd" cellpadding="0" cellspacing="0" border="0" width="100%">
<tr><td><img src="images/blank.gif" width="1" height="1" border="0"></td></tr>
</table>
<table bgcolor="#eeeeee" cellpadding="0" cellspacing="0" border="0" width="100%">
<tr><td><img src="images/blank.gif" width="1" height="1" border="0"></td></tr>
</table>
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<tr><td><img src="images/blank.gif" width="1" height="5" border="0"></td></tr>
</table>

<c:choose> 
<c:when test="${sidebar == 'server'}" >
    <table class="sidebar-group" cellpadding="3" cellspacing="0" border="0" width="100%">
    <tr>
        <th colspan="2">
            Server Manager
        </th>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td>
            <a href="server-status.jsp" >Server Status</a>
        </td>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td>
            <a href="server-props.jsp" >Server Properties</a>
        </td>
    </tr>
    <!-- TODO: CHECK FOR PLUGIN -->
    <tr>
        <td class="bullet">&#149;</td>
        <td>
            <a href="logviewer.jsp" >Server Logs</a>
        </td>
    </tr>

    <tr><td colspan="2">&nbsp;</td></tr>

    <tr>
        <th colspan="2">
            Server Settings
        </th>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td>
            <a href="reg-settings.jsp" >Registration and Login Settings</a>
        </td>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td>
            <a href="session-conflict.jsp" >Resource Conflict Policy</a>
        </td>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td>
            <a href="offline-messages.jsp" >Offline Message Policy</a>
        </td>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td>
            <a href="audit-policy.jsp" >Message Audit Policy</a>
        </td>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td>
            <a href="private-data-settings.jsp" >Private Data Storage</a>
        </td>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td>
            <a href="chatroom-history-settings.jsp" >Chatroom History Settings</a>
        </td>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td>
            <a href="ssl-settings.jsp" >SSL Settings</a>
        </td>
    </tr>
      <tr><td colspan="2">&nbsp;</td></tr>
     <tr>
        <th colspan="2">
            Group Chat Settings
        </th>
    </tr>
      <tr>
        <td class="bullet">&#149;</td>
        <td>
            <a href="muc-server-props-edit-form.jsp" >Group Chat Properties</a>
        </td>
    </tr>
      <tr>
        <td class="bullet">&#149;</td>
        <td>
            <a href="muc-history-settings.jsp" >Group Chat History Settings</a>
        </td>
    </tr>
      <tr>
        <td class="bullet">&#149;</td>
        <td>
            <a href="muc-sysadmins.jsp" >Group Chat System Administrators</a>
        </td>
    </tr>
      <tr>
        <td class="bullet">&#149;</td>
        <td>
            <a href="muc-create-permission.jsp" >Group Chat Room Permissions</a>
        </td>
    </tr>

    </table>

</c:when>
<c:when test="${sidebar == 'database'}" >
    <table class="sidebar-group" cellpadding="3" cellspacing="0" border="0" width="100%">
    <tr>
        <th colspan="2">
            Database Manager
        </th>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td>
            <a href="db-connection.jsp" >Connection Info</a>
        </td>
    </tr>
    </table>
</c:when>
<c:when test="${sidebar == 'users'}" >
    <table class="sidebar-group" cellpadding="3" cellspacing="0" border="0" width="100%">
    <tr>
        <th colspan="2">
            Users
        </th>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td>
            <a href="user-summary.jsp" >User Summary</a>
        </td>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td>
            <a href="user-search.jsp" >User Search</a>
        </td>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td>
            <a href="user-create.jsp" >Create User</a>
        </td>
    </tr>
    </table>
</c:when>
<c:when test="${sidebar == 'session'}">
    <table class="sidebar-group" cellpadding="3" cellspacing="0" border="0" width="100%">
    <tr>
        <th colspan="2">
            Session Manager
        </th>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td>
            <a href="session-summary.jsp" >List Sessions</a>
        </td>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td>
            <a href="session-settings.jsp" >Session Settings</a>
        </td>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td>
            <a href="user-message.jsp?tabs=false" >Send Administrative Message</a>
        </td>
    </tr>
    </table>
</c:when>
<c:when test="${sidebar == 'muc'}">
    <table class="sidebar-group" cellpadding="3" cellspacing="0" border="0" width="100%">
    <tr>
        <th colspan="2">
            MUC Manager
        </th>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td>
            <a href="muc-server-props-edit-form.jsp" >Server Properties</a>
        </td>
    </tr>
    <tr>
        <td class="bullet">&#149;</td>
        <td>
            <a href="muc-history-settings.jsp" >History Settings</a>
        </td>
    </tr>
    <!--<tr>
        <td class="bullet">&#149;</td>
        <td>
            <a href="muc-send-message.jsp?tabs=false" >Send Message To Room</a>
        </td>
    </tr>-->

    <tr><td colspan="2">&nbsp;</td></tr>

    <tr>
        <th colspan="2">
            Permission Settings
        </th>
    </tr>
    <!--<tr>
        <td class="bullet">&#149;</td>
        <td>
            <a href="muc-sysadmins.jsp" >Users with role of sysadmin</a>
        </td>
    </tr>-->
    <tr>
        <td class="bullet">&#149;</td>
        <td>
            <a href="muc-create-permission.jsp" >Users allowed to create rooms</a>
        </td>
    </tr>
    </table>
</c:when>
</c:choose>
</div>





