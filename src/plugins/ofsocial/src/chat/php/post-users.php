<?php

/*

Jappix - An open social platform
This is the user add POST handler (install & manager)

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 28/12/10

*/

// Someone is trying to hack us?
if(!defined('JAPPIX_BASE'))
	exit;

// Marker
$valid_user = true;

// Administrator name
if(isset($_POST['user_name']) && !empty($_POST['user_name']))
	$user_name = trim($_POST['user_name']);
else
	$valid_user = false;

// Administrator password (first)
if(isset($_POST['user_password']) && !empty($_POST['user_password']))
	$user_password = trim($_POST['user_password']);
else
	$valid_user = false;

// Administrator password (second)
if(isset($_POST['user_repassword']) && ($user_password != $_POST['user_repassword']))
	$valid_user = false;

// Generate the users XML content
if($valid_user) {
	// Add our user
	manageUsers('add', array($user_name => $user_password));
	
	// Reset the user name
	$user_name = '';
}

?>
