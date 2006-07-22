<%@ page import="java.util.*,
                 org.jivesoftware.wildfire.XMPPServer,
                 org.jivesoftware.util.*,
                 org.jivesoftware.wildfire.gateway.GatewayPlugin"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<html>
<head>
<title>Gateway Settings</title>

<meta name="pageID" content="gateway-settings">

<style type="text/css">
<!--	@import url("style/gateways.css");    -->
</style>

<script language="JavaScript" type="text/javascript" src="scripts/gateways.js"></script>

</head>
<body>


<p>Select which gateways will be allowed, what features are available, and who can connect to each gateway service. Checking a gateway enables the service.</p>


<form action="" name="gatewayForm">

	<!-- BEGIN gateway 1 - AIM -->
	<div class="jive-gateway" id="jiveAIM">
		<label for="jiveAIMcheckbox">
			<input type="checkbox" name="gateway" value="aim" id="jiveAIMcheckbox" checked onClick="checkToggle(jiveAIM); return true"> 
			<img src="/images/aim.gif" alt="" border="0">
			<strong>AOL Instant Messenger</strong>
		</label>
		<div class="jive-gatewayButtons">
			<a href="#" onclick="togglePanel(jiveAIMoptions,jiveAIMperms); return false" id="jiveAIMoptionsLink"">Options</a>
			<a href="#" onclick="togglePanel(jiveAIMperms,jiveAIMoptions); return false" id="jiveAIMpermsLink">Permissions</a>
		</div>
	</div>
	<div class="jive-gatewayPanel" id="jiveAIMoptions" style="display: none;">
		<div>
		<form action="">
			<input type="checkbox" name="filetransfer" value="enabled"> Enable file transfer<br>
			<input type="checkbox" name="reconnect" value="enabled"> Reconnect on disconnect<br>
			<input type="submit" name="submit" value="Save Options" onclick="togglePanel(jiveAIMoptions,jiveAIMperms); return false" class="jive-formButton"> 
			<input type="reset" name="cancel" value="Cancel" onclick="togglePanel(jiveAIMoptions,jiveAIMperms); return false" class="jive-formButton">
		</form>
		</div>
	</div>
	<div class="jive-gatewayPanel" id="jiveAIMperms" style="display: none;">
		<div>
		<form action="">
			<input type="radio" name="userreg" value="all" checked> All users can register<br>
			<input type="radio" name="userreg" value="specific"> These users and/or groups can register<br>
			<input type="radio" name="userreg" value="manual"> Manual registration only (see the Registrations section to manage)<br>
			<input type="submit" name="submit" value="Save Permissions" onclick="togglePanel(jiveAIMperms,jiveAIMoptions); return false" class="jive-formButton"> 
			<input type="reset" name="cancel" value="Cancel" onclick="togglePanel(jiveAIMperms,jiveAIMoptions); return false" class="jive-formButton">
		</form>
		</div>
	</div>
	<!-- END gateway 1 - AIM -->
	
	
	
	<!-- BEGIN gateway 2 - ICQ -->
	<div class="jive-gateway" id="jiveICQ">
		<label for="jiveICQcheckbox">
			<input type="checkbox" name="gateway" value="icq" id="jiveICQcheckbox" checked onClick="checkToggle(jiveICQ); return true"> 
			<img src="/images/icq.gif" alt="" border="0">
			<strong>ICQ</strong>
		</label>
		<div class="jive-gatewayButtons">
			<a href="#" onclick="togglePanel(jiveICQoptions,jiveICQperms); return false" id="jiveICQoptionsLink">Options</a>
			<a href="#" onclick="togglePanel(jiveICQperms,jiveICQoptions); return false" id="jiveICQpermsLink">Permissions</a>
		</div>
	</div>
	<div class="jive-gatewayPanel" id="jiveICQoptions" style="display: none;">
		<div>
		<form action="">
			<input type="checkbox" name="filetransfer" value="enabled"> Enable file transfer<br>
			<input type="checkbox" name="reconnect" value="enabled"> Reconnect on disconnect<br>
			<input type="submit" name="submit" value="Save Options" onclick="togglePanel(jiveICQoptions,jiveICQperms); return false" class="jive-formButton"> 
			<input type="reset" name="cancel" value="Cancel" onclick="togglePanel(jiveICQoptions,jiveICQperms); return false" class="jive-formButton">
		</form>
		</div>
	</div>
	<div class="jive-gatewayPanel" id="jiveICQperms" style="display: none;">
		<div>
		<form action="">
			<input type="radio" name="userreg" value="all" checked> All users can register<br>
			<input type="radio" name="userreg" value="specific"> These users and/or groups can register<br>
			<input type="radio" name="userreg" value="manual"> Manual registration only (see the Registrations section to manage)<br>
			<input type="submit" name="submit" value="Save Permissions" onclick="togglePanel(jiveICQperms,jiveICQoptions); return false" class="jive-formButton"> 
			<input type="reset" name="cancel" value="Cancel" onclick="togglePanel(jiveICQperms,jiveICQoptions); return false" class="jive-formButton">
		</form>
		</div>
	</div>
	<!-- END gateway 2 - ICQ -->
	
	
	
	<!-- BEGIN gateway 3 - MSN -->
	<div class="jive-gateway" id="jiveMSN">
		<label for="jiveMSNcheckbox">
			<input type="checkbox" name="gateway" value="msn" id="jiveMSNcheckbox" checked onClick="checkToggle(jiveMSN); return true"> 
			<img src="/images/msn.gif" alt="" border="0">
			<strong>MSN Messenger</strong>
		</label>
		<div class="jive-gatewayButtons">
			<a href="#" onclick="togglePanel(jiveMSNoptions,jiveMSNperms); return false" id="jiveMSNoptionsLink">Options</a>
			<a href="#" onclick="togglePanel(jiveMSNperms,jiveMSNoptions); return false" id="jiveMSNpermsLink">Permissions</a>
		</div>
	</div>
	<div class="jive-gatewayPanel" id="jiveMSNoptions" style="display: none;">
		<div>
		<form action="">
			<input type="checkbox" name="filetransfer" value="enabled"> Enable file transfer<br>
			<input type="checkbox" name="reconnect" value="enabled"> Reconnect on disconnect<br>
			<input type="submit" name="submit" value="Save Options" onclick="togglePanel(jiveMSNoptions,jiveMSNperms); return false" class="jive-formButton"> 
			<input type="reset" name="cancel" value="Cancel" onclick="togglePanel(jiveMSNoptions,jiveMSNperms); return false" class="jive-formButton">
		</form>
		</div>
	</div>
	<div class="jive-gatewayPanel" id="jiveMSNperms" style="display: none;">
		<div>
		<form action="">
			<input type="radio" name="userreg" value="all" checked> All users can register<br>
			<input type="radio" name="userreg" value="specific"> These users and/or groups can register<br>
			<input type="radio" name="userreg" value="manual"> Manual registration only (see the Registrations section to manage)<br>
			<input type="submit" name="submit" value="Save Permissions" onclick="togglePanel(jiveMSNerms,jiveMSNoptions); return false" class="jive-formButton"> 
			<input type="reset" name="cancel" value="Cancel" onclick="togglePanel(jiveMSNerms,jiveMSNoptions); return false" class="jive-formButton">
		</form>
		</div>
	</div>
	<!-- END gateway 3 - MSN -->
	
	
	
	<!-- BEGIN gateway 4 - Yahoo -->
	<div class="jive-gateway" id="jiveYAHOO">
		<label for="jiveYAHOOcheckbox">
			<input type="checkbox" name="gateway" value="yahoo" id="jiveYAHOOcheckbox" checked onClick="checkToggle(jiveYAHOO); return true"> 
			<img src="/images/yahoo.gif" alt="" border="0">
			<strong>Yahoo Messenger</strong>
		</label>
		<div class="jive-gatewayButtons">
			<a href="#" onclick="togglePanel(jiveYAHOOoptions,jiveYAHOOperms); return false" id="jiveYAHOOoptionsLink">Options</a>
			<a href="#" onclick="togglePanel(jiveYAHOOperms,jiveYAHOOoptions); return false" id="jiveYAHOOpermsLink">Permissions</a>
		</div>
	</div>
	<div class="jive-gatewayPanel" id="jiveYAHOOoptions" style="display: none;">
		<div>
		<form action="">
			<input type="checkbox" name="filetransfer" value="enabled"> Enable file transfer<br>
			<input type="checkbox" name="reconnect" value="enabled"> Reconnect on disconnect<br>
			<input type="submit" name="submit" value="Save Options" onclick="togglePanel(jiveYAHOOoptions,jiveYAHOOperms); return false" class="jive-formButton"> 
			<input type="reset" name="cancel" value="Cancel" onclick="togglePanel(jiveYAHOOoptions,jiveYAHOOperms); return false" class="jive-formButton">
		</form>
		</div>
	</div>
	<div class="jive-gatewayPanel" id="jiveYAHOOperms" style="display: none;">
		<div>
		<form action="">
			<input type="radio" name="userreg" value="all" checked> All users can register<br>
			<input type="radio" name="userreg" value="specific"> These users and/or groups can register<br>
			<input type="radio" name="userreg" value="manual"> Manual registration only (see the Registrations section to manage)<br>
			<input type="submit" name="submit" value="Save Permissions" onclick="togglePanel(jiveYAHOOerms,jiveYAHOOoptions); return false" class="jive-formButton"> 
			<input type="reset" name="cancel" value="Cancel" onclick="togglePanel(jiveYAHOOerms,jiveYAHOOoptions); return false" class="jive-formButton">
		</form>
		</div>
	</div>
	<!-- END gateway 4 - Yahoo -->

<!-- <br clear="all">

<input type="submit" name="submit" value="Save Settings">  -->


</form>


<br clear="all">


</body>
</html>
