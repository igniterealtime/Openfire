<?php

/*

Jappix - An open social platform
This is the Jappix geolocation script

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 27/05/11

*/

// PHP base
define('JAPPIX_BASE', '..');

// Get the needed files
require_once('./functions.php');
require_once('./read-main.php');
require_once('./read-hosts.php');

// Optimize the page rendering
hideErrors();
compressThis();

// Not allowed for a special node
if(isStatic() || isUpload())
	exit;

// If valid data was sent
if((isset($_GET['latitude']) && !empty($_GET['latitude'])) && (isset($_GET['longitude']) && !empty($_GET['longitude'])) && (isset($_GET['language']) && !empty($_GET['language']))) {
	// Set a XML header
	header('Content-Type: text/xml; charset=utf-8');
	
	// Get the XML content
	$xml = file_get_contents('http://maps.googleapis.com/maps/api/geocode/xml?latlng='.urlencode($_GET['latitude']).','.urlencode($_GET['longitude']).'&language='.urlencode($_GET['language']).'&sensor=true');
	
	exit($xml);
}

?>
