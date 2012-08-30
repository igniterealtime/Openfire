<%@ page
	import="org.jivesoftware.openfire.plugin.married.JustMarriedPlugin"%>
<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt"%>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<%
	webManager.init(request, response, session, application, out);
	String oldName = request.getParameter("oldName");
	String newName = request.getParameter("newName");
	String keepCopy = request.getParameter("copy");
	String newEmail = request.getParameter("email");
	String newRealName = request.getParameter("realName");
%>

<html>
<head>
<title>Just married - name changer</title>
<meta name="pageID" content="justmarried" />
<meta name="helpPage" content="" />
<script src="./js/bootstrap.min.js" type="text/javascript"></script>
<link href="./css/bootstrap.min.css" rel="stylesheet" type="text/css">

</head>
<body>

	<div class="jive-contentBoxHeader">Just married</div>
	<div class="jive-contentBox">
		<%
			if (oldName != null && newName != null && oldName.trim().length() > 0 && newName.trim().length() > 0) {
				boolean success = JustMarriedPlugin.changeName(oldName, newName, keepCopy == null ? true : false, newEmail, newRealName);
				if (success) {
					out.write("<div class=\"success\">Sucessfully renamed user " + oldName + " to " + newName
							+ "!</div>");
				} else {
					out.write("<div class=\"error\">Something went wrong :-/. Please have a closer look to the error log!</div>");
				}
			} else {
		%>
		<form class="form-horizontal">
			<fieldset>
				<legend>Change the name here</legend>
				<label class="control-label" for="input01">Current username*</label>
				<div
					<%out.write(oldName != null && oldName.length() == 0 ? "class=\"control-group error\""
						: "class=\"controls\"");%>>
					<input type="text" name="oldName" style="height:26px"
						class="input-xlarge"
						<%out.write(oldName != null && oldName.length() == 0 ? "id=\"inputError\"" : "id=\"input01\"");%>>
					<p class="help-block">The current username e.g user.name
						(without server)</p>
				</div>
				<label class="control-label" for="input01">New username*</label>
				<div
					<%out.write(newName != null && newName.length() == 0 ? "class=\"control-group error\""
						: "class=\"controls\"");%>>
					<input type="text" name="newName" style="height:26px"
						class="input-xlarge"
						<%out.write(newName != null && newName.length() == 0 ? "id=\"inputError\"" : "id=\"input01\"");%>>
					<p class="help-block">The new username e.g user.newname
						(without server)</p>
				</div>
				<label class="control-label" for="input01">New E-Mail address</label>
				<div class="controls">
					<input type="text" name="email" style="height:26px"
						class="input-xlarge" id="input01">
					<p class="help-block">New email address. Will copy address from old user if field is empty.</p>
				</div>
				<label class="control-label" for="input01">New Name</label>
				<div class="controls">
					<input type="text" name="realName" style="height:26px"
						class="input-xlarge" id="input01">
					<p class="help-block">Will copy name from old user if field is empty.</p>
				</div>
				<div class="control-group">
					<label class="checkbox"> <input type="checkbox"
						id="optionsCheckbox2" name="copy" value="keepCopy"> Keep a
						copy of the old username
					</label>
				</div>
				<div class="control-group">
					<button type="submit" class="btn btn-primary">Rename user</button>
				</div>
				<p class="help-block">* Mandatory item</p>
			</fieldset>
		</form>

		<%
			}
		%>

	</div>
</body>
</html>
