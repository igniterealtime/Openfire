<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.*" %>

<%!
    final String INCOMPLETE = "incomplete";
    final String IN_PROGRESS = "in_progress";
    final String DONE = "done";
%>

<%  // Get sidebar values from the session:

    String step1 = (String)session.getAttribute("jive.setup.sidebar.1");
    String step2 = (String)session.getAttribute("jive.setup.sidebar.2");
    String step3 = (String)session.getAttribute("jive.setup.sidebar.3");
    String step4 = (String)session.getAttribute("jive.setup.sidebar.4");

    if (step1 == null) { step1 = IN_PROGRESS; }
    if (step2 == null) { step2 = INCOMPLETE; }
    if (step3 == null) { step3 = INCOMPLETE; }
    if (step4 == null) { step4 = INCOMPLETE; }

    String[] items = {step1, step2, step3, step4};
    String[] names = {
        "Install Checklist", "Server Settings", "Datasource Settings", "Admin Account"
    };
    String[] links = {
        "setup-index.jsp",
        "setup-host-settings.jsp",
        "setup-datasource-settings.jsp",
        "setup-admin-settings.jsp"
    };
%>

<table bgcolor="#cccccc" cellpadding="0" cellspacing="0" border="0" width="200">
<tr><td>
<table bgcolor="#cccccc" cellpadding="3" cellspacing="1" border="0" width="200">
<tr bgcolor="#eeeeee">
    <td align="center">
        <span style="padding:6px">
        <b>Setup Progress</b>
        </span>
    </td>
</tr>
<tr bgcolor="#ffffff">
    <td>
        <table cellpadding="5" cellspacing="0" border="0" width="100%">
        <%  for (int i=0; i<items.length; i++) { %>
            <tr>
            <%  if (INCOMPLETE.equals(items[i])) { %>

                <td width="1%"><img src="images/red.gif" width="20" height="20" border="0"></td>
                <td width="99%">
                        <%= names[i] %>
                </td>

            <%  } else if (IN_PROGRESS.equals(items[i])) { %>

                <td width="1%"><img src="images/yellow.gif" width="20" height="20" border="0"></td>
                <td width="99%">
                        <a href="<%= links[i] %>"><%= names[i] %></a>
                </td>

            <%  } else { %>

                <td width="1%"><img src="images/green.gif" width="20" height="20" border="0"></td>
                <td width="99%">
                        <a href="<%= links[i] %>"><%= names[i] %></a>
                </td>

            <%  } %>
            </tr>
        <%  } %>
        <tr><td colspan="2"><br><br><br><br></td></tr>
        </table>
    </td>
</tr>
</table>
</td></tr>
</table>
