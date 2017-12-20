<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<%@ page import="com.reucon.openfire.plugins.userstatus.UserStatusPlugin" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.util.JiveGlobals" %>

<% // Get parameters
    boolean update = request.getParameter("update") != null;
    boolean updateSuccess = false;
    Integer historyDays = ParamUtils.getIntParameter(request, "historyDays", UserStatusPlugin.DEFAULT_HISTORY_DAYS);
    Integer historyOption = ParamUtils.getIntParameter(request, "historyOption", 1);

    if (historyOption == -1 || historyOption == 0)
    {
        historyDays = historyOption;
    }

    // Perform update if requested
    if (update)
    {
        if (historyDays >= -1)
        {
            JiveGlobals.setProperty(UserStatusPlugin.HISTORY_DAYS_PROPERTY, historyDays.toString());
            updateSuccess = true;
        }
    }

    historyDays = JiveGlobals.getIntProperty(UserStatusPlugin.HISTORY_DAYS_PROPERTY, UserStatusPlugin.DEFAULT_HISTORY_DAYS);
%>

<html>
<head>
    <title>
        <fmt:message key="user-status.settings.title"/>
    </title>
    <meta name="pageID" content="user-status-settings"/>
</head>
<body>

<p>
    <fmt:message key="user-status.settings.intro"/>
</p>

<% if (updateSuccess)
{ %>

<div id="updateSuccessMessage" class="success">
    <fmt:message key="user-status.settings.update.success"/>
</div>
<script type="text/javascript">
    setTimeout("Effect.Fade('updateSuccessMessage')", 3000)
</script>

<% } %>

<form action="user-status-settings.jsp" method="post">
    <div class="jive-contentBoxHeader">
        <fmt:message key="user-status.settings.basic.title"/>
    </div>
    <div class="jive-contentBox">
        <table cellpadding="3" cellspacing="0" border="0">
            <tbody>
                <tr>
                    <td width="1%" valign="top" nowrap>
                        <input type="radio" name="historyOption" value="0" id="rb01" onchange="historyOptionChanged()"
                        <%= (historyDays == 0 ? "checked" : "") %>>
                    </td>
                    <td width="99%">
                        <label for="rb01">
                            <fmt:message key="user-status.settings.basic.history.disabled"/>
                        </label>
                    </td>
                </tr>
                <tr>
                    <td width="1%" valign="top" nowrap>
                        <input type="radio" name="historyOption" value="-1" id="rb02" onchange="historyOptionChanged()"
                        <%= (historyDays == -1 ? "checked" : "") %>>
                    </td>
                    <td width="99%">
                        <label for="rb02">
                            <fmt:message key="user-status.settings.basic.history.forever"/>
                        </label>
                    </td>
                </tr>
                <tr>
                    <td width="1%" valign="top" nowrap>
                        <input type="radio" name="historyOption" value="1" id="rb03" onchange="historyOptionChanged()"
                        <%= (historyDays > 0 ? "checked" : "") %>>
                    </td>
                    <td width="99%">
                        <fmt:message key="user-status.settings.basic.historyDays">
                            <fmt:param><input type="text" name="historyDays" size="3" maxlength="6" id="historyDays"
                                              value="<%= historyDays > 0 ? historyDays : "" %>"
                                    <%= historyDays > 0 ? "" : "disabled=\"disabled\"" %>/>
                            </fmt:param>
                        </fmt:message>
                    </td>
                </tr>
            </tbody>
        </table>
    </div>
    <input type="submit" name="update" value="<fmt:message key="user-status.settings.save"/>"/>
</form>

<script type="text/javascript">
    function historyOptionChanged()
    {
        if ($('rb01').checked || $('rb02').checked)
        {
            $('historyDays').value = '';
            $('historyDays').disabled = true;
        }
        else
        {
            $('historyDays').disabled = false;
        }
    }
</script>

</body>
</html>