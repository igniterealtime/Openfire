<?php

/*

Jappix - An open social platform
These are the main configuration variables

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 27/05/11

*/

// Someone is trying to hack us?
if(!defined('JAPPIX_BASE'))
	exit;

// Define the vars with the main configuration constants
$service_name = htmlspecialchars(SERVICE_NAME);
$service_desc = htmlspecialchars(SERVICE_DESC);
$jappix_resource = htmlspecialchars(JAPPIX_RESOURCE);
$lock_host = htmlspecialchars(LOCK_HOST);
$anonymous_mode = htmlspecialchars(ANONYMOUS);
$registration = htmlspecialchars(REGISTRATION);
$bosh_proxy = htmlspecialchars(BOSH_PROXY);
$manager_link = htmlspecialchars(MANAGER_LINK);
$encryption = htmlspecialchars(ENCRYPTION);
$https_storage = htmlspecialchars(HTTPS_STORAGE);
$https_force = htmlspecialchars(HTTPS_FORCE);
$compression = htmlspecialchars(COMPRESSION);
$multi_files = htmlspecialchars(MULTI_FILES);
$developer = htmlspecialchars(DEVELOPER);

?>
