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
<title>Gateway Registrations</title>

<meta name="pageID" content="gateway-registrations">

<style type="text/css">
<!--	@import url("style/gateways.css");    -->
</style>

<script language="JavaScript" type="text/javascript" src="scripts/gateways.js"></script>

</head>
<body>


<p>Below is a list of all gateway service registrations. To filter by active sessions and/or specific gateways select the options 
below and update the view.</p>


<!-- BEGIN add registration -->
<div class="jive-gateway-addregBtn" id="jiveAddRegButton">
	<a href="#" onClick="toggleAdd(); return false" id="jiveAddRegLink">Add a new registration</a>
</div>
<div class="jive-gateway-addreg" id="jiveAddRegPanel" style="display: none;">
	<div class="jive-gateway-addregPad">
		<form action="" name="jive-addRegistration">
		<div class="jive-registrations-addJid">
			<input type="text" name="gatewayJID" size="12" maxlength="50" value=""><br>
			<strong>user (JID)</strong>
		</div>
		<div class="jive-registrations-addGateway">
			<select name="gateway" size="1">
			<option value="0" SELECTED> -- select -- </option>
			<option value="aim">AIM</option>
			<option value="icq">ICQ</option>
			<option value="msn">MSN</option>
			<option value="yahoo">Yahoo</option>
			</select><br>
			<strong>gateway</strong>
		</div>
		<div class="jive-registrations-addUsername">
			<input type="text" name="gatewayUser" size="12" maxlength="50" value=""><br>
			<strong>username</strong>
		</div>
		<div class="jive-registrations-addPassword">
			<input type="password" name="gatewayPass" size="12" maxlength="50" value=""><br>
			<strong>password</strong>
		</div>
		<div class="jive-registrations-addButtons">
			<input type="submit" name="Submit" value="Add" class="savechanges" onClick="toggleAdd();"> &nbsp;
			<input type="reset" name="reset" value="Cancel" class="cancel" onClick="toggleAdd();">
		</div>
		</form>
	</div>
</div>
<!-- END add registration -->




<!-- BEGIN registrations table -->
<div class="jive-registrations">


	<!-- BEGIN results -->
	<div class="jive-registrations-results">
		Registrations: <strong>1-15</strong> of <strong>52</strong>
	</div>
	<!-- END results -->


	<!-- BEGIN results size (num per page) -->
	<div class="jive-registrations-resultsSize">
		<select name="numPerPage" id="numPerPage" size="1">
		<option value="1" SELECTED>15</option>
		<option value="2">30</option>
		<option value="3">50</option>
		<option value="4">100</option>
		</select>
		<span>per page</span>
	</div>
	<!-- END results size -->


	<!-- BEGIN pagination -->
	<div class="jive-registrations-pagination">
		<strong>Page:</strong> &nbsp; 
		<a href="#"><strong>1</strong></a> 
		<a href="#">2</a> 
		<a href="#">3</a> 
		<a href="#">4</a> -
		<a href="#"><strong>Next ></strong></a>
	</div>
	<!-- END pagination -->

	
	
	
	<!-- BEGIN gateway filter -->
	<form action="" name="jive-filterForm">
	<div class="jive-gateway-filter" id="jiveGatewayFilters">
		<div>
		<strong>Filter by:</strong>
		<label for="filterAIMcheckbox">
			<input type="checkbox" name="filter" value="aim" checked id="filterAIMcheckbox"> 
			<img src="/images/aim.gif" alt="" border="0"> 
			<span>AIM</span>
		</label>
		<label for="filterICQcheckbox">
			<input type="checkbox" name="filter" value="icq" checked id="filterICQcheckbox"> 
			<img src="/images/icq.gif" alt="" border="0"> 
			<span>ICQ</span>
		</label>
		<label for="filterMSNcheckbox">
			<input type="checkbox" name="filter" value="msn" checked id="filterMSNcheckbox"> 
			<img src="/images/msn.gif" alt="" border="0"> 
			<span>MSN</span>
		</label>
		<label for="filterYAHOOcheckbox">
			<input type="checkbox" name="filter" value="yahoo" checked id="filterYAHOOcheckbox"> 
			<img src="/images/yahoo.gif" alt="" border="0"> 
			<span>Yahoo</span>
		</label>
		<label for="filterActiveOnly">
			<input type="checkbox" name="filter" value="signedon" id="filterActiveOnly"> 
			<span>Signed on only</span>
		</label>	
		<input type="submit" name="submit" value="Update" class="filterBtn"> 
		</div>
	</div>
	</form>
	<!-- END gateway filter -->
	
	

	<!-- BEGIN registrations table -->
	<table cellpadding="0" cellspacing="0">
	<thead>
		<tr>
			<th width="20" class="border-left">&nbsp;</th>
			<th width="25%">User</th>
			<th>Service/Username</th>
			<th>Last Login</th>
			<th width="1%"><div align="center">Edit</div></th>
			<th width="1%" class="border-right">Remove</th>
		</tr>
	</thead>
	<tbody>
		
		<!-- <tr id="jiveRegistration1" class="jive-registrations-normal"> -->
		<tr id="jiveRegistration1">
			<td align="center">
			<img src="/images/im_available.gif" alt="online" border="0"></td>
			<td>anthony@jivesoftware.com</td>
			<td><span class="jive-gateway-online jive-gateway-AIMon">antmanJive</span></td>
			<td>Jun 27, 2006</td>
			<td align="center"><a href="#" onClick="toggleEdit(1); return false"><img src="/images/edit-16x16.gif" alt="" border="0"></a></td>
			<td align="center"><a href="#" onClick="alert('Are you sure you want to delete this registration?'); return false"><img src="/images/delete-16x16.gif" alt="" border="0"></a></td>
		</tr>
		<!-- <tr id="jiveRegistrationEdit1" class="jive-registrations-edit"> -->
		<tr id="jiveRegistrationEdit1" style="display: none;">
			<td align="center">
			<img src="/images/im_available.gif" alt="online" border="0"></td>
			<td>anthony@jivesoftware.com</td>
			<td colspan="4">
			<span class="jive-gateway-online jive-gateway-AIMon">
				<div class="jive-registrations-editUsername">
				<input type="text" name="aimname" size="12" maxlength="50" value="antmanJive"><br>
				<strong>username</strong>
				</div>
				<div class="jive-registrations-editPassword">
				<input type="password" name="aimpwd" size="12" maxlength="50" value="*********"><br>
				<strong>password</strong>
				</div>
				<div class="jive-registrations-editButtons">
				<input type="submit" name="Submit" value="Save Changes" class="savechanges" onClick="toggleEdit(1);"> &nbsp;
				<input type="reset" name="reset" value="Cancel" class="cancel" onClick="toggleEdit(1);">
				</div>
			</span>
			</td>
		</tr>
		<tr>
			<td align="center">
			<img src="/images/im_away.gif" alt="online" border="0"></td>
			<td>barry@jivesoftware.com</td>
			<td><span class="jive-gateway-offline jive-gateway-MSNoff">mr_barry@msn.com</span></td>
			<td>Jub 27, 2006</td>
			<td align="center"><a href="#"><img src="/images/edit-16x16.gif" alt="" border="0"></a></td>
			<td align="center"><a href="#" onClick="alert('Are you sure you want to delete this registration?'); return false"><img src="/images/delete-16x16.gif" alt="" border="0"></a></td>
		</tr>
		<tr>
			<td align="center">
			<img src="/images/im_available.gif" alt="online" border="0"></td>
			<td>bill@jivesoftware.com</td>
			<td><span class="jive-gateway-online jive-gateway-AIMon">lynchbill</span></td>
			<td>Jul 15, 2006</td>
			<td align="center"><a href="#"><img src="/images/edit-16x16.gif" alt="" border="0"></a></td>
			<td align="center"><a href="#" onClick="alert('Are you sure you want to delete this registration?'); return false"><img src="/images/delete-16x16.gif" alt="" border="0"></a></td>
		</tr>
		<tr>
			<td align="center">
			<img src="/images/im_available.gif" alt="online" border="0"></td>
			<td>dave@jivesoftware.com</td>
			<td><span class="jive-gateway-online jive-gateway-AIMon">djhersh</span></td>
			<td>Jun 11, 2006</td>
			<td align="center"><a href="#"><img src="/images/edit-16x16.gif" alt="" border="0"></a></td>
			<td align="center"><a href="#" onClick="alert('Are you sure you want to delete this registration?'); return false"><img src="/images/delete-16x16.gif" alt="" border="0"></a></td>
		</tr>
		<tr>
			<td align="center">
			<img src="/images/im_available.gif" alt="online" border="0"></td>
			<td>matt@jivesoftware.com</td>
			<td><span class="jive-gateway-online jive-gateway-AIMon">tuckermatt</span></td>
			<td>Jul 15, 2006</td>
			<td align="center"><a href="#"><img src="/images/edit-16x16.gif" alt="" border="0"></a></td>
			<td align="center"><a href="#" onClick="alert('Are you sure you want to delete this registration?'); return false"><img src="/images/delete-16x16.gif" alt="" border="0"></a></td>
		</tr>
		<tr>
			<td align="center">
			<img src="/images/im_available.gif" alt="online" border="0"></td>
			<td>matt@jivesoftware.com</td>
			<td><span class="jive-gateway-offline jive-gateway-ICQoff">543124</span></td>
			<td>Jun 22, 2006</td>
			<td align="center"><a href="#"><img src="/images/edit-16x16.gif" alt="" border="0"></a></td>
			<td align="center"><a href="#" onClick="alert('Are you sure you want to delete this registration?'); return false"><img src="/images/delete-16x16.gif" alt="" border="0"></a></td>
		</tr>
		<tr>
			<td align="center">
			<img src="/images/im_available.gif" alt="online" border="0"></td>
			<td>matt@jivesoftware.com</td>
			<td><span class="jive-gateway-offline jive-gateway-MSNoff">mtucker@hotmail.com</span></td>
			<td>Jul 1, 2006</td>
			<td align="center"><a href="#"><img src="/images/edit-16x16.gif" alt="" border="0"></a></td>
			<td align="center"><a href="#" onClick="alert('Are you sure you want to delete this registration?'); return false"><img src="/images/delete-16x16.gif" alt="" border="0"></a></td>
		</tr>
		<tr>
			<td align="center">
			<img src="/images/im_available.gif" alt="online" border="0"></td>
			<td>matt@jivesoftware.com</td>
			<td><span class="jive-gateway-online jive-gateway-Yon">matt_tucker@yahoo.com</span></td>
			<td>Jul 11, 2006</td>
			<td align="center"><a href="#"><img src="/images/edit-16x16.gif" alt="" border="0"></a></td>
			<td align="center"><a href="#" onClick="alert('Are you sure you want to delete this registration?'); return false"><img src="/images/delete-16x16.gif" alt="" border="0"></a></td>
		</tr>
		<tr>
			<td align="center">
			<img src="/images/im_available.gif" alt="online" border="0"></td>
			<td>nick@jivesoftware.com</td>
			<td><span class="jive-gateway-online jive-gateway-MSNon">nickJive@hotmail.com</span></td>
			<td>Jul 5, 2006</td>
			<td align="center"><a href="#"><img src="/images/edit-16x16.gif" alt="" border="0"></a></td>
			<td align="center"><a href="#" onClick="alert('Are you sure you want to delete this registration?'); return false"><img src="/images/delete-16x16.gif" alt="" border="0"></a></td>
		</tr>
		<tr>
			<td align="center">
			<img src="/images/im_away.gif" alt="online" border="0"></td>
			<td>ryan@jivesoftware.com</td>
			<td><span class="jive-gateway-offline jive-gateway-AIMoff">vanman0001</span></td>
			<td>Jul 18, 2006</td>
			<td align="center"><a href="#"><img src="/images/edit-16x16.gif" alt="" border="0"></a></td>
			<td align="center"><a href="#" onClick="alert('Are you sure you want to delete this registration?'); return false"><img src="/images/delete-16x16.gif" alt="" border="0"></a></td>
		</tr>
		<tr>
			<td align="center">
			<img src="/images/im_away.gif" alt="online" border="0"></td>
			<td>ryan@jivesoftware.com</td>
			<td><span class="jive-gateway-offline jive-gateway-ICQoff">4918312</span></td>
			<td>Jun 22, 2006</td>
			<td align="center"><a href="#"><img src="/images/edit-16x16.gif" alt="" border="0"></a></td>
			<td align="center"><a href="#" onClick="alert('Are you sure you want to delete this registration?'); return false"><img src="/images/delete-16x16.gif" alt="" border="0"></a></td>
		</tr>
		<tr>
			<td align="center">
			<img src="/images/im_away.gif" alt="online" border="0"></td>
			<td>ryan@jivesoftware.com</td>
			<td><span class="jive-gateway-offline jive-gateway-MSNoff">ryan_vanderzanden@hotmail.com</span></td>
			<td>Jul 7, 2006</td>
			<td align="center"><a href="#"><img src="/images/edit-16x16.gif" alt="" border="0"></a></td>
			<td align="center"><a href="#" onClick="alert('Are you sure you want to delete this registration?'); return false"><img src="/images/delete-16x16.gif" alt="" border="0"></a></td>
		</tr>
		<tr>
			<td align="center">
			<img src="/images/im_away.gif" alt="online" border="0"></td>
			<td>ryan@jivesoftware.com</td>
			<td><span class="jive-gateway-offline jive-gateway-Yoff">ryan_vanderzanden@yahoo.com</span></td>
			<td>Jun 14, 2006</td>
			<td align="center"><a href="#"><img src="/images/edit-16x16.gif" alt="" border="0"></a></td>
			<td align="center"><a href="#" onClick="alert('Are you sure you want to delete this registration?'); return false"><img src="/images/delete-16x16.gif" alt="" border="0"></a></td>
		</tr>
		<tr>
			<td align="center">
			<img src="/images/im_away.gif" alt="online" border="0"></td>
			<td>sam@jivesoftware.com</td>
			<td><span class="jive-gateway-offline jive-gateway-AIMoff">mr_sam</span></td>
			<td>Jul 14, 2006</td>
			<td align="center"><a href="#"><img src="/images/edit-16x16.gif" alt="" border="0"></a></td>
			<td align="center"><a href="#" onClick="alert('Are you sure you want to delete this registration?'); return false"><img src="/images/delete-16x16.gif" alt="" border="0"></a></td>
		</tr>
		<tr>
			<td align="center">
			<img src="/images/im_away.gif" alt="online" border="0"></td>
			<td>sam@jivesoftware.com</td>
			<td><span class="jive-gateway-offline jive-gateway-Yoff">samJive@yahoo.com</span></td>
			<td>Jul 15, 2006</td>
			<td align="center"><a href="#"><img src="/images/edit-16x16.gif" alt="" border="0"></a></td>
			<td align="center"><a href="#" onClick="alert('Are you sure you want to delete this registration?'); return false"><img src="/images/delete-16x16.gif" alt="" border="0"></a></td>
		</tr>
	</tbody>
	</table>
	<!-- BEGIN registrations table -->


	<!-- BEGIN pagination -->
	<div class="jive-registrations-pagination">
		<strong>Page:</strong> &nbsp; 
		<a href="#"><strong>1</strong></a> 
		<a href="#">2</a> 
		<a href="#">3</a> 
		<a href="#">4</a> -
		<a href="#"><strong>Next ></strong></a>
	</div>
	<!-- END pagination -->


</div>
<!-- END registrations table -->


<br clear="all">


</body>
</html>
