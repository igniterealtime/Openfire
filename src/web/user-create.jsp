<%@ taglib uri="core" prefix="c"%><%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.*,
                 java.util.HashMap,
                 java.util.Map,
                 org.jivesoftware.messenger.user.UserManager,
                 org.jivesoftware.messenger.user.*,
                 org.jivesoftware.messenger.user.spi.*,
                 java.util.*,
                 org.jivesoftware.messenger.*,
                 org.jivesoftware.admin.*,
                 java.io.StringWriter,
                 java.io.StringWriter,
                 java.io.IOException,
                 org.jivesoftware.messenger.auth.UnauthorizedException,
                 java.io.PrintStream,
                 org.dom4j.xpath.DefaultXPath,
                 org.dom4j.*"
%>

<%-- Define Administration Bean --%>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<jsp:useBean id="errors" class="java.util.HashMap" />
<jsp:useBean id="userData" class="org.jivesoftware.messenger.user.spi.UserPrivateData" />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters //
    boolean create = request.getParameter("create") != null;
    boolean cancel = request.getParameter("cancel") != null;
    String username = ParamUtils.getParameter(request,"username");
    String name = ParamUtils.getParameter(request,"name");
    String email = ParamUtils.getParameter(request,"email");
    String password = ParamUtils.getParameter(request,"password");
    String passwordConfirm = ParamUtils.getParameter(request,"passwordConfirm");

    // Handle a cancel
    if (cancel) {
        response.sendRedirect("user-summary.jsp");
        return;
    }

    // Handle a request to create a user:
    if (create) {
        // Validate
        if (username == null) {
            errors.put("username","");
        }
        if (password == null) {
            errors.put("password","");
        }
        if (passwordConfirm == null) {
            errors.put("passwordConfirm","");
        }
        if (password != null && passwordConfirm != null && !password.equals(passwordConfirm)) {
            errors.put("passwordMatch","");
        }

        // do a create if there were no errors
        if (errors.size() == 0) {
            try {
                User newUser = webManager.getUserManager().createUser(username, password, email);
                if (name != null) {
                    newUser.getInfo().setName(name);
                }
                newUser.saveInfo();
                
                // Successful, so redirect
                response.sendRedirect("user-create-success.jsp?username=" + newUser.getUsername());
                return;
            }
            catch (UserAlreadyExistsException e) {
                e.printStackTrace();
                errors.put("usernameAlreadyExists","");
            }
            catch (Exception e) {
                e.printStackTrace();
                errors.put("general","");
                Log.error(e);
            }
        }
    }
%>



<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "Create User";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "main.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "user-create.jsp"));
    pageinfo.setPageID("user-create");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<c:set var="submit" value="${param.create}" />
<c:set var="errors" value="${errors}" />

<%  if (errors.get("general") != null) { %>

    <p class="jive-error-text">
    Error creating the user account. Please check your error logs.
    </p>

<%  } %>

<table class="box" cellpadding="3" cellspacing="1" border="0" width="600">
<form name="f" action="user-create.jsp" method="post">
<tr><td class="text" colspan="2">
Use the form below to create a new user in the system.
</td></tr>

<tr class="jive-even">
    <td>
        Username: *
    </td>
    <td>
        <input type="text" name="username" size="30" maxlength="75"
         value="<%= ((username!=null) ? username : "") %>">

        <%  if (errors.get("username") != null) { %>

            <span class="jive-error-text">
            Invalid username.
            </span>

        <%  } else if (errors.get("usernameAlreadyExists") != null) { %>

            <span class="jive-error-text">
            Username already exists - please choose a different one.
            </span>

        <%  } %>
    </td>
</tr>
<tr class="jive-odd">
    <td>
        Name:
    </td>
    <td>
        <input type="text" name="name" size="30" maxlength="75"
         value="<%= ((name!=null) ? name : "") %>">

        <%  if (errors.get("name") != null) { %>

            <span class="jive-error-text">
            Invalid name.
            </span>

        <%  } %>
    </td>
</tr>
<tr class="jive-even">
    <td>
        Email:
    </td>
    <td>
        <input type="text" name="email" size="30" maxlength="75"
         value="<%= ((email!=null) ? email : "") %>">

        <%  if (errors.get("email") != null) { %>

            <span class="jive-error-text">
            Invalid email.
            </span>

        <%  } %>
    </td>
</tr>
<tr class="jive-odd">
    <td>
        Password: *
    </td>
    <td>
        <input type="password" name="password" value="" size="20" maxlength="75">

        <%  if (errors.get("password") != null) { %>

            <span class="jive-error-text">
            Invalid password.
            </span>

        <%  } else if (errors.get("passwordMatch") != null) { %>

            <span class="jive-error-text">
            Passwords don't match.
            </span>

        <%  } %>
    </td>
</tr>
<tr class="jive-even">
    <td>
        Confirm Password: *
    </td>
    <td>
        <input type="password" name="passwordConfirm" value="" size="20" maxlength="75">

        <%  if (errors.get("passwordConfirm") != null) { %>

            <span class="jive-error-text">
            Invalid password confirmation.
            </span>

        <%  } %>
    </td>
</tr>
</table>
</div>

<p>
* Required fields
</p>

<input type="submit" name="create" value="Create User">
<input type="submit" name="cancel" value="Cancel">

</form>

<script language="JavaScript" type="text/javascript">
document.f.username.focus();

function checkFields() {
  
}
</script>

<%@ include file="footer.jsp" %>
