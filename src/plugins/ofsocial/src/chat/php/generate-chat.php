<?php

/*

Jappix - An open social platform
This is the PHP script used to generate a chat log

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

// Create the HTML file to be downloaded
if(isset($_POST['content']) && isset($_POST['xid']) && !empty($_POST['xid']) && isset($_POST['nick']) && !empty($_POST['nick']) && isset($_POST['avatar']) && !empty($_POST['avatar']) && isset($_POST['date']) && !empty($_POST['date']) && isset($_POST['type']) && !empty($_POST['type'])) {
	// Get the POST vars
	$original = $_POST['content'];
	$xid = $_POST['xid'];
	$nick = $_POST['nick'];
	$avatar = $_POST['avatar'];
	$date = $_POST['date'];
	$type = $_POST['type'];
	
	// Generate the XID link
	$xid_link = 'xmpp:'.$xid;
	
	if($type == 'groupchat')
		$xid_link .= '?join';
	
	// Generates the avatar code
	if($avatar != 'none')
		$avatar = '<div class="avatar-container">'.$avatar.'</div>';
	else
		$avatar = '';
	
	// Generates an human-readable date
	$date = explode('T', $date);
	$date = explode('-', $date[0]);
	$date = $date[2].'/'.$date[1].'/'.$date[0];
	
	// Generate some values
	$content_dir = '../store/logs/';
	$filename = 'chat_log-'.md5($xid.time());
	$filepath = $content_dir.$filename.'.html';
	
	// Create the HTML code
	$new_text_inter = 
'<!DOCTYPE html>
<html>	

<head>
	<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
	<title>'.$nick.' ('.$xid.')</title>
	<style type="text/css">
		* {
			margin: 0;
			padding: 0;
		}
		
		body {
			background-color: #424242;
			font-family : Verdana, Arial, Helvetica, sans-serif;
			font-size: 0.8em;
			text-shadow: 0 0 5px white;
			color: white;
			margin: 8px;
			padding: 8px 12px;
		}
		
		a {
			color: white;
		}
		
		#head {
		
		}
		
		#head .avatar-container {
			text-align: center;
			float: left;
			height: 70px;
			width: 70px;
			margin-right: 18px;
		}
		
		#head .avatar {
			max-height: 70px;
			max-width: 70px;
		}
		
		#head h1 {
			font-size: 2.2em;
			margin: 0;
			text-shadow: 1px 1px 1px black;
		}
		
		#head h3 {
			font-size: 0.95em;
			margin: 0;
		}
		
		#head h5 {
			font-size: 0.9em;
			margin: 8px 0 16px 0;
		}
		
		#head h3,
		#head h5 {
			text-shadow: 0 0 1px black;
		}
		
		#content {
			background-color: #e8f1f3;
			color: black;
			padding: 14px 18px;
			border-radius: 4px;
			clear: both;
			-moz-border-radius: 4px;
			-webkit-border-radius: 4px;
			box-shadow: 0 0 20px #202020;
			-moz-box-shadow: 0 0 20px #202020;
			-webkit-box-shadow: 0 0 20px #202020;
		}
		
		#content a {
			color: black;
		}
		
		#content .one-group {
			border-bottom: 1px dotted #d0d0d0;
			padding-bottom: 8px;
			margin-bottom: 10px;
		}
		
		#content .one-group b.name {
			display: block;
			margin-bottom: 4px;
		}
		
		#content .one-group b.name.me {
			color: #123a5c;
		}
		
		#content .one-group b.name.him {
			color: #801e1e;
		}
		
		#content .one-group span.date {
			float: right;
			font-size: 0.9em;
		}
		
		#content .user-message {
			margin-bottom: 3px;
		}
		
		#content .system-message {
			color: #053805;
			margin-bottom: 3px;
			padding-left: 0 !important;
		}
		
		#content .system-message a {
			color: #053805;
		}
		
		.hidden {
			display: none !important;
		}
	</style>
</head>

<body>
	<div id="head">
		'.$avatar.'
		
		<h1>'.$nick.'</h1>
		<h3><a href="'.$xid_link.'">'.$xid.'</a></h3>
		<h5>'.$date.'</h5>
	</div>
	
	<div id="content">
		'.$original.'
	</div>
</body>
</html>'
;
	
	$new_text = stripslashes($new_text_inter);
	
	// Write the code into a file
	file_put_contents($filepath, $new_text);
	
	// Security: remove the file and stop the script if too bit (+6MiB)
	if(filesize($filepath) > 6000000) {
		unlink($filepath);
		exit;
	}
	
	// Return to the user the generated file ID
	exit($filename);
}

?>
