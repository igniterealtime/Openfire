<?php

/*

Jappix - An open social platform
This is the main configuration POST handler (install & manager)

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 27/05/11

*/

// Someone is trying to hack us?
if(!defined('JAPPIX_BASE'))
	exit;

// Service name
if(isset($_POST['service_name']) && !empty($_POST['service_name']))
	$service_name = stripslashes(htmlspecialchars($_POST['service_name']));
else
	$service_name = stripslashes(htmlspecialchars($main_default['name']));

// Service description
if(isset($_POST['service_desc']) && !empty($_POST['service_desc']))
	$service_desc = stripslashes(htmlspecialchars($_POST['service_desc']));
else
	$service_desc = stripslashes(htmlspecialchars($main_default['desc']));

// Jappix resource
if(isset($_POST['jappix_resource']) && !empty($_POST['jappix_resource']))
	$jappix_resource = stripslashes(htmlspecialchars($_POST['jappix_resource']));
else
	$jappix_resource = stripslashes(htmlspecialchars($main_default['resource']));

// Lock host
if(isset($_POST['lock_host']) && !empty($_POST['lock_host']))
	$lock_host = 'on';
else
	$lock_host = 'off';

// Anonymous mode
if(isset($_POST['anonymous_mode']) && !empty($_POST['anonymous_mode']))
	$anonymous_mode = 'on';
else
	$anonymous_mode = 'off';

// Registration
if(isset($_POST['registration']) && !empty($_POST['registration']))
	$registration = 'on';
else
	$registration = 'off';

// BOSH proxy
if(isset($_POST['bosh_proxy']) && !empty($_POST['bosh_proxy']))
	$bosh_proxy = 'on';
else
	$bosh_proxy = 'off';

// Manager link
if(isset($_POST['manager_link']) && !empty($_POST['manager_link']))
	$manager_link = 'on';
else
	$manager_link = 'off';

// Encryption
if(isset($_POST['encryption']) && !empty($_POST['encryption']))
	$encryption = 'on';
else
	$encryption = 'off';

// HTTPS storage
if(isset($_POST['https_storage']) && !empty($_POST['https_storage']))
	$https_storage = 'on';
else
	$https_storage = 'off';

// Force HTTPS
if(isset($_POST['https_force']) && !empty($_POST['https_force']))
	$https_force = 'on';
else
	$https_force = 'off';

// Compression
if(isset($_POST['compression']) && !empty($_POST['compression']))
	$compression = 'on';
else
	$compression = 'off';

// Multiple resources
if(isset($_POST['multi_files']) && ($_POST['multi_files'] == 'on'))
	$multi_files = 'on';
else
	$multi_files = 'off';

// Developer mode
if(isset($_POST['developer']) && ($_POST['developer'] == 'on'))
	$developer = 'on';
else
	$developer = 'off';

// Generate the configuration XML content
$conf_xml = 
	'<name>'.$service_name.'</name>
	<desc>'.$service_desc.'</desc>
	<resource>'.$jappix_resource.'</resource>
	<lock>'.$lock_host.'</lock>
	<anonymous>'.$anonymous_mode.'</anonymous>
	<registration>'.$registration.'</registration>
	<bosh_proxy>'.$bosh_proxy.'</bosh_proxy>
	<manager_link>'.$manager_link.'</manager_link>
	<encryption>'.$encryption.'</encryption>
	<https_storage>'.$https_storage.'</https_storage>
	<https_force>'.$https_force.'</https_force>
	<compression>'.$compression.'</compression>
	<multi_files>'.$multi_files.'</multi_files>
	<developer>'.$developer.'</developer>'
;

// Write the main configuration
writeXML('conf', 'main', $conf_xml);
