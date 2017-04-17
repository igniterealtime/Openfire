<?php

/*

Jappix - An open social platform
This is the hosts configuration POST handler (install & manager)

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 27/05/11

*/

// Someone is trying to hack us?
if(!defined('JAPPIX_BASE'))
	exit;

// Main host
if(isset($_POST['host_main']) && !empty($_POST['host_main']))
	$host_main = stripslashes(htmlspecialchars($_POST['host_main']));
else
	$host_main = stripslashes(htmlspecialchars($hosts_default['main']));

// Groupchat host
if(isset($_POST['host_muc']) && !empty($_POST['host_muc']))
	$host_muc = stripslashes(htmlspecialchars($_POST['host_muc']));
else
	$host_muc = stripslashes(htmlspecialchars($hosts_default['muc']));

// Pubsub host
if(isset($_POST['host_pubsub']) && !empty($_POST['host_pubsub']))
	$host_pubsub = stripslashes(htmlspecialchars($_POST['host_pubsub']));
else
	$host_pubsub = stripslashes(htmlspecialchars($hosts_default['pubsub']));

// Directory host
if(isset($_POST['host_vjud']) && !empty($_POST['host_vjud']))
	$host_vjud = stripslashes(htmlspecialchars($_POST['host_vjud']));
else
	$host_vjud = stripslashes(htmlspecialchars($hosts_default['vjud']));

// Anonymous host
if(isset($_POST['host_anonymous']) && !empty($_POST['host_anonymous']))
	$host_anonymous = stripslashes(htmlspecialchars($_POST['host_anonymous']));
else
	$host_anonymous = stripslashes(htmlspecialchars($hosts_default['anonymous']));

// BOSH host
if(isset($_POST['host_bosh']) && !empty($_POST['host_bosh']))
	$host_bosh = stripslashes(htmlspecialchars($_POST['host_bosh']));
else
	$host_bosh = stripslashes(htmlspecialchars($hosts_default['bosh']));

// Main BOSH host
if(isset($_POST['host_bosh_main']) && !empty($_POST['host_bosh_main']))
	$host_bosh_main = stripslashes(htmlspecialchars($_POST['host_bosh_main']));
else
	$host_bosh_main = stripslashes(htmlspecialchars($hosts_default['bosh_main']));

// Mini BOSH host
if(isset($_POST['host_bosh_mini']) && !empty($_POST['host_bosh_mini']))
	$host_bosh_mini = stripslashes(htmlspecialchars($_POST['host_bosh_mini']));
else
	$host_bosh_mini = stripslashes(htmlspecialchars($hosts_default['bosh_mini']));

// Static host
if(isset($_POST['host_static']) && !empty($_POST['host_static']))
	$host_static = stripslashes(htmlspecialchars($_POST['host_static']));
else
	$host_static = stripslashes(htmlspecialchars($hosts_default['static']));

// Upload host
if(isset($_POST['host_upload']) && !empty($_POST['host_upload']))
	$host_upload = stripslashes(htmlspecialchars($_POST['host_upload']));
else
	$host_upload = stripslashes(htmlspecialchars($hosts_default['upload']));

// Generate the hosts XML content
$hosts_xml = 
	'<main>'.$host_main.'</main>
	<muc>'.$host_muc.'</muc>
	<pubsub>'.$host_pubsub.'</pubsub>
	<vjud>'.$host_vjud.'</vjud>
	<anonymous>'.$host_anonymous.'</anonymous>
	<bosh>'.$host_bosh.'</bosh>
	<bosh_main>'.$host_bosh_main.'</bosh_main>
	<bosh_mini>'.$host_bosh_mini.'</bosh_mini>
	<static>'.$host_static.'</static>
	<upload>'.$host_upload.'</upload>'
;

// Write the main configuration
writeXML('conf', 'hosts', $hosts_xml);
