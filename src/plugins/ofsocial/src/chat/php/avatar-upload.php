<?php

/*

Jappix - An open social platform
This is the avatar upload PHP script for Jappix

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

// Set a special XML header
header('Content-Type: text/xml; charset=utf-8');

// No file uploaded?
if((!isset($_FILES['file']) || empty($_FILES['file'])) || (!isset($_POST['id']) || empty($_POST['id'])))
	exit(
'<jappix xmlns=\'jappix:avatar:post\' id=\'0\'>
	<error>bad-request</error>
</jappix>'
	);

// Get the POST vars
$id = $_POST['id'];
$tmp_filename = $_FILES['file']['tmp_name'];
$old_filename = $_FILES['file']['name'];

// Get the file extension
$ext = getFileExt($old_filename);

// Hash it!
$filename = md5($old_filename.time()).$ext;

// Define some vars
$path = JAPPIX_BASE.'/store/avatars/'.$filename;

// Define MIME type
if($ext == 'jpg')
	$ext = 'jpeg';

$mime = 'image/'.$ext;

// Unsupported file extension?
if(!preg_match('/^(jpeg|png|gif)$/i', $ext))
	exit(
'<jappix xmlns=\'jappix:avatar:post\' id=\''.$id.'\'>
	<error>forbidden-type</error>
</jappix>'
	);

// File upload error?
if(!is_uploaded_file($tmp_filename) || !move_uploaded_file($tmp_filename, $path))
	exit(
'<jappix xmlns=\'jappix:file:post\' id=\''.$id.'\'>
	<error>move-error</error>
</jappix>'
	);

// Resize the image?
if(!function_exists('gd_info') || resizeImage($path, $ext, 96, 96)) {
	try {
		// Encode the file
		$binval = base64_encode(file_get_contents($path));
		
		// Remove the file
		unlink($path);
		
		exit(
'<jappix xmlns=\'jappix:file:post\' id=\''.$id.'\'>
	<type>'.$mime.'</type>
	<binval>'.$binval.'</binval>
</jappix>'
		);
	}
	
	catch(Exception $e) {
		// Remove the file
		unlink($path);
		
		exit(
'<jappix xmlns=\'jappix:file:post\' id=\''.$id.'\'>
	<error>server-error</error>
</jappix>'
		);
	}
}

// Remove the file
unlink($path);

// Something went wrong!
exit(
'<jappix xmlns=\'jappix:file:post\' id=\''.$id.'\'>
	<error>service-unavailable</error>
</jappix>'
);

?>
