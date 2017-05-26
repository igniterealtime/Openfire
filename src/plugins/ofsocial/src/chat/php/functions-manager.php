<?php

/*

Jappix - An open social platform
These are the PHP functions for Jappix manager

-------------------------------------------------

License: AGPL
Authors: Valérian Saliou, Mathieui, Olivier Migeot
Last revision: 08/05/11

*/

// The function to check an user is admin
function isAdmin($user, $password) {
	// Read the users.xml file
	$array = getUsers();
	
	// No data?
	if(empty($array))
		return false;
	
	// Our user is set and valid?
	if(isset($array[$user]) && ($array[$user] == $password))
		return true;
	
	// Not authorized
	return false;
}

// Checks if a file is a valid image
function isImage($file) {
	// This is an image
	if(preg_match('/^(.+)(\.)(png|jpg|jpeg|gif|bmp)$/i', $file))
		return true;
	
	return false;
}

// Puts a marker on the current opened manager tab
function currentTab($current, $page) {
	if($current == $page)
		echo ' class="tab-active"';
}

// Checks all the storage folders are writable
function storageWritable() {
	// Read the directory content
	$dir = JAPPIX_BASE.'/store/';
	$scan = scandir($dir);
	
	// Writable marker
	$writable = true;
	
	// Check that each folder is writable
	foreach($scan as $current) {
		// Current folder
		$folder = $dir.$current;
		
		// A folder is not writable?
		if(!preg_match('/^\.(.+)/', $current) && !is_writable($folder)) {
			// Try to change the folder rights
			chmod($folder, 0777);
			
			// Check it again!
			if(!is_writable($folder))
				$writable = false;
		}
	}
	
	return $writable;
}

// Removes a given directory (with all sub-elements)
function removeDir($dir) {
	// Can't open the dir
	if(!$dh = @opendir($dir))
		return;
	
	// Loop the current dir to remove its content
	while(false !== ($obj = readdir($dh))) {
		// Not a "real" directory
		if(($obj == '.') || ($obj == '..'))
			continue;
		
		// Not a file, remove this dir
		if(!@unlink($dir.'/'.$obj))
			removeDir($dir.'/'.$obj);
	}
	
	// Close the dir and remove it!
	closedir($dh);
	@rmdir($dir);
}

// Copies a given directory (with all sub-elements)
function copyDir($source, $destination) {
	// This is a directory
	if(is_dir($source)) {
		// Create the target directory
		@mkdir($destination);
		$directory = dir($source);
		
		// Append the source directory content into the target one
		while(FALSE !== ($readdirectory = $directory->read())) {
			if(($readdirectory == '.') || ($readdirectory == '..'))
				continue;
			
			$PathDir = $source.'/'.$readdirectory;
			
			// Recursive copy
			if(is_dir($PathDir)) {
				copyDir($PathDir, $destination.'/'.$readdirectory);
				
				continue;
			}
			
			copy($PathDir, $destination.'/'.$readdirectory);
		}
	 	
	 	// Close the source directory
		$directory->close();
	}
	
	// This is a file
	else
		copy($source, $destination);
}

// Gets the total size of a directory
function sizeDir($dir) {
	$size = 0;
	
	foreach(new RecursiveIteratorIterator(new RecursiveDirectoryIterator($dir)) as $file)
        	$size += $file->getSize();
	
	return $size;
}

// Set the good unity for a size in bytes
function numericToMonth($id) {
	$array = array(
		      	1 => T_("January"),
		      	2 => T_("February"),
		      	3 => T_("March"),
		      	4 => T_("April"),
		      	5 => T_("May"),
		      	6 => T_("June"),
		      	7 => T_("July"),
		      	8 => T_("August"),
		      	9 => T_("September"),
		      	10 => T_("October"),
		      	11 => T_("November"),
		      	12 =>T_( "December")
		      );
	
	return $array[$id];
}

// Extracts the version number with a version ID
function versionNumber($id) {
	// First, extract the number string from the [X]
	$extract = preg_replace('/^(.+)\[(\S+)\]$/', '$2', $id);
	
	// Second extract: ~ (when this is a special version, like ~dev)
	if(strrpos($extract, '~') !== false) {
		$extract = preg_replace('/^(.+)~(.+)$/', '$1', $extract);
		
		// Allows updates
		$extract = floatval($extract) - 0.01;
	}
	
	// Normal version
	else
		$extract = floatval($extract);
	
	return $extract;
}

// Checks for new Jappix updates
function newUpdates($force) {
	// No need to check if developer mode
	if(isDeveloper())
		return false;
	
	$cache_path = JAPPIX_BASE.'/store/updates/version.xml';
	
	// No cache, obsolete one or refresh forced
	if(!file_exists($cache_path) || (file_exists($cache_path) && (time() - (filemtime($cache_path)) >= 86400)) || $force) {
		// Get the content
		$last_version = file_get_contents('https://project.jappix.com/xml/version.xml');
		
		// Write the content
		file_put_contents($cache_path, $last_version);
	}
	
	// Read from the cache
	else
		$last_version = file_get_contents($cache_path);
	
	// Parse the XML
	$xml = @simplexml_load_string($last_version);
	
	// No data?
	if($xml === FALSE)
		return false;
	
	// Get the version numbers
	$current_version = getVersion();
	$last_version = $xml->id;
	
	// Check if we have the latest version
	$current_version = versionNumber($current_version);
	$last_version = versionNumber($last_version);
	
	if($current_version < $last_version)
		return true;
	
	return false;
}

// Gets the Jappix update informations
function updateInformations() {
	// Get the XML file content
	$data = file_get_contents(JAPPIX_BASE.'/store/updates/version.xml');
	
	// Transform the XML content into an array
	$array = array();
	
	// No XML?
	if(!$data)
		return $array;
	
	$xml = new SimpleXMLElement($data);
	
	// Parse the XML to add it to the array
	foreach($xml->children() as $this_child) {
		// Get the node name
		$current_name = $this_child->getName();
		
		// Push it to the array, with a basic HTML encoding
		$array[$current_name] = str_replace('\n', '<br />', $this_child);
	}
	
	// Return this array
	return $array;
}

// Processes the Jappix update from an external package
function processUpdate($url) {
	// Archive path
	$name = md5($url).'.zip';
	$update_dir = $dir_base.'store/updates/';
	$path = JAPPIX_BASE.'/store/updates/'.$name;
	$extract_to = $update_dir.'jappix/';
	$store_tree = JAPPIX_BASE.'/php/store-tree.php';
	
	// We must get the archive from the server
	if(!file_exists($path)) {
		echo('<p>» '.T_("Downloading package...").'</p>');
		
		// Open the packages
		$local = fopen($path, 'w');
		$remote = fopen($url, 'r');
		
		// Could not open a socket?!
		if(!$remote) {
			echo('<p>» '.T_("Aborted: socket error!").'</p>');
			
			// Remove the broken local archive
			unlink($path);
			
			return false;
		}
		
		// Read the file
		while(!feof($remote)) {
			// Get the buffer
			$buffer = fread($remote, 1024);
			
			// Any error?
			if($buffer == 'Error.') {
				echo('<p>» '.T_("Aborted: buffer error!").'</p>');
				
				// Remove the broken local archive
				unlink($path);
				
				return false;
			}
			
			// Write the buffer to the file
			fwrite($local, $buffer);
			
			// Flush the current buffer
			ob_flush();
			flush();
   		}
   		
   		// Close the files
   		fclose($local);
   		fclose($remote);
	}
	
	// Then, we extract the archive
	echo('<p>» '.T_("Extracting package...").'</p>');
	
	try {
		$zip = new ZipArchive;
		$zip_open = $zip->open($path);
		
		if($zip_open === TRUE) {
			$zip->extractTo($update_dir);
			$zip->close();
		}
		
		else {
			echo('<p>» '.T_("Aborted: could not extract the package!").'</p>');
			
			// Remove the broken source folder
			removeDir($to_remove);
			
			return false;
		}
	}
	
	// PHP does not provide Zip archives support
	catch(Exception $e) {
		echo('<p>» '.T_("Aborted: could not extract the package!").'</p>');
		
		// Remove the broken source folder
		removeDir($to_remove);
		
		return false;
	}
	
	// Remove the ./store dir from the source directory
	removeDir($extract_to.'store/');
	
	// Then, we remove the Jappix system files
	echo('<p>» '.T_("Removing current Jappix system files...").'</p>');
	
	// Open the general directory
	$dir_base = JAPPIX_BASE.'/';
	$scan = scandir($dir_base);
	
	// Filter the scan array
	$scan = array_diff($scan, array('.', '..', '.svn', 'store'));
	
	// Check all the files are writable
	foreach($scan as $scanned) {
		// Element path
		$scanned_current = $dir_base.$scanned;
		
		// Element not writable
		if(!is_writable($scanned_current)) {
			// Try to change the element rights
			chmod($scanned_current, 0777);
			
			// Check it again!
			if(!is_writable($scanned_current)) {
				echo('<p>» '.T_("Aborted: everything is not writable!").'</p>');
				
				return false;
			}
		}
	}
	
   	// Process the files deletion
   	foreach($scan as $current) {
   		$to_remove = $dir_base.$current;
   		
   		// Remove folders
		if(is_dir($to_remove))
			removeDir($to_remove);
		
		// Remove files
		else
			unlink($to_remove);
   	}
	
	// Move the extracted files to the base
	copyDir($extract_to, $dir_base);
	
	// Remove the source directory
	removeDir($extract_to);
	
	// Regenerates the store tree
	if(file_exists($store_tree)) {
		echo('<p>» '.T_("Regenerating storage folder tree...").'</p>');
		
		// Call the special regeneration script
		include($store_tree);
	}
	
	// Remove the version package
	unlink($path);
	
	// The new version is now installed!
	echo('<p>» '.T_("Jappix is now up to date!").'</p>');
	
	return true;
}

// Returns an array with the biggest share folders
function shareStats() {
	// Define some stuffs
	$path = JAPPIX_BASE.'/store/share/';
	$array = array();
	
	// Open the directory
	$scan = scandir($path);
	
	// Loop the share files
	foreach($scan as $current) {
		if(is_dir($path.$current) && !preg_match('/^(\.(.+)?)$/i', $current))
			array_push($array, $current);
   	}
	
	return $array;
}

// Returns the largest share folders
function largestShare($array, $number) {
	// Define some stuffs
	$path = JAPPIX_BASE.'/store/share/';
	$size_array = array();
	
	// Push the results in an array
	foreach($array as $current)
		$size_array[$current] = sizeDir($path.$current);
	
	// Sort this array
	arsort($size_array);
	
	// Select the first biggest values
	$size_array = array_slice($size_array, 0, $number);
	
	return $size_array;
}

// Returns the others statistics array
function otherStats() {
	// Fill the array with the values
	$others_stats = array(
			     	T_("Backgrounds") => sizeDir(JAPPIX_BASE.'/store/backgrounds/'),
			     	T_("Cache") => sizeDir(JAPPIX_BASE.'/store/cache/'),
			     	T_("Configuration") => sizeDir(JAPPIX_BASE.'/store/conf/'),
			     	T_("Logs") => sizeDir(JAPPIX_BASE.'/store/logs/'),
			     	T_("Music") => sizeDir(JAPPIX_BASE.'/store/music/'),
			     	T_("Share") => sizeDir(JAPPIX_BASE.'/store/share/'),
			     	T_("Updates") => sizeDir(JAPPIX_BASE.'/store/updates/')
			     );
	
	// Sort this array
	arsort($others_stats);
	
	return $others_stats;
}

// Gets the array of the visits stats
function getVisits() {
	// New array
	$array = array(
		      	'total' => 0,
		      	'daily' => 0,
		      	'weekly' => 0,
		      	'monthly' => 0,
		      	'yearly' => 0
		      );
	
	// Read the data
	$data = readXML('access', 'total');
	
	// Any data?
	if($data) {
		// Initialize the visits reading
		$xml = new SimpleXMLElement($data);
		
		// Get the XML values
		$array['total'] = intval($xml->total);
		$array['stamp'] = intval($xml->stamp);
		
		// Get the age of the stats
		$age = time() - $array['stamp'];
		
		// Generate the time-dependant values
		$timed = array(
			      	'daily' => 86400,
			      	'weekly' => 604800,
			      	'monthly' => 2678400,
			      	'yearly' => 31536000
			      );
		
		foreach($timed as $timed_key => $timed_value) {
			if($age >= $timed_value)
				$array[$timed_key] = intval($array['total'] / ($age / $timed[$timed_key])).'';
			else
				$array[$timed_key] = $array['total'].'';
		}
	}
	
	return $array;
}

// Gets the array of the monthly visits
function getMonthlyVisits() {
	// New array
	$array = array();
	
	// Read the data
	$data = readXML('access', 'months');
	
	// Get the XML file values
	if($data) {
		// Initialize the visits reading
		$xml = new SimpleXMLElement($data);
		
		// Loop the visit elements
		foreach($xml->children() as $child) {
			// Get the current month ID
			$current_id = intval(preg_replace('/month_([0-9]+)/i', '$1', $child->getName()));
			
			// Get the current month name
			$current_name = numericToMonth($current_id);
			
			// Push it!
			$array[$current_name] = intval($child);
		}
	}
	
	return $array;
}

// Purges the target folder content
function purgeFolder($folder) {
	// Array of the folders to purge
	$array = array();
	
	// We must purge all the folders?
	if($folder == 'everything')
		array_push($array, 'cache', 'logs', 'updates');
	else
		array_push($array, $folder);
	
	// All right, now we can empty it!
	foreach($array as $current_folder) {
		// Scan the current directory
		$directory = JAPPIX_BASE.'/store/'.$current_folder.'/';
		$scan = scandir($directory);
	   	$scan = array_diff($scan, array('.', '..', '.svn', 'index.html'));
	   	
	   	// Process the files deletion
	   	foreach($scan as $current) {
	   		$remove_this = $directory.$current;
	   		
	   		// Remove folders
			if(is_dir($remove_this))
				removeDir($remove_this);
			
			// Remove files
			else
				unlink($remove_this);
	   	}
	}
}

// Returns folder browsing informations
function browseFolder($folder, $mode) {
	// Scan the target directory
	$directory = JAPPIX_BASE.'/store/'.$folder;
	$scan = scandir($directory);
	$scan = array_diff($scan, array('.', '..', '.svn', 'index.html'));
	$keep_get = keepGet('(s|b)', false);
	
	// Odd/even marker
	$marker = 'odd';
	
	// Not in the root folder: show previous link
	if(strpos($folder, '/') != false) {
		// Filter the folder name
		$previous_folder = substr($folder, 0, strrpos($folder, '/'));
		
		echo('<div class="one-browse previous manager-images"><a href="./?b='.$mode.'&s='.urlencode($previous_folder).$keep_get.'">'.T_("Previous").'</a></div>');
	}
	
	// Empty or non-existing directory?
	if(!count($scan) || !is_dir($directory)) {
		echo('<div class="one-browse '.$marker.' alert manager-images">'.T_("The folder is empty.").'</div>');
		
		return false;
	}
	
	// Echo the browsing HTML code
	foreach($scan as $current) {
		// Generate the item path$directory
		$path = $directory.'/'.$current;
		$file = $folder.'/'.$current;
		
		// Directory?
		if(is_dir($path)) {
			$type = 'folder';
			$href = './?b='.$mode.'&s='.urlencode($file).$keep_get;
			$target = '';
		}
		
		// File?
		else {
			$type = getFileType(getFileExt($path));
			$href = $path;
			$target = ' target="_blank"';
		}
		
		echo('<div class="one-browse '.$marker.' '.$type.' manager-images"><a href="'.$href.'"'.$target.'>'.htmlspecialchars($current).'</a><input type="checkbox" name="element_'.md5($file).'" value="'.htmlspecialchars($file).'" /></div>');
		
		// Change the marker
		if($marker == 'odd')
			$marker = 'even';
		else
			$marker = 'odd';
	}
	
	return true;
}

// Removes selected elements (files/folders)
function removeElements() {
	// Initialize the match
	$elements_removed = false;
	$elements_remove = array();
	
	// Try to get the elements to remove
	foreach($_POST as $post_key => $post_value) {
		// Is a safe file?
		if(preg_match('/^element_(.+)$/i', $post_key) && isSafe($post_value)) {
			// Update the marker
			$elements_removed = true;
			
			// Get the real path
			$post_element = JAPPIX_BASE.'/store/'.$post_value;
			
			// Remove the current element
			if(is_dir($post_element))
				removeDir($post_element);
			else if(file_exists($post_element))
				unlink($post_element);
		}
	}
	
	// Show a notification message
	if($elements_removed)
		echo('<p class="info smallspace success">'.T_("The selected elements have been removed.").'</p>');
	else
		echo('<p class="info smallspace fail">'.T_("You must select elements to remove!").'</p>');
}

// Returns users browsing informations
function browseUsers() {
	// Get the users
	$array = getUsers();
	
	// Odd/even marker
	$marker = 'odd';
	
	// Echo the browsing HTML code
	foreach($array as $user => $password) {
		// Filter the username
		$user = htmlspecialchars($user);
		
		// Output the code
		echo('<div class="one-browse '.$marker.' user manager-images"><span>'.$user.'</span><input type="checkbox" name="admin_'.md5($user).'" value="'.$user.'" /><div class="clear"></div></div>');
		
		// Change the marker
		if($marker == 'odd')
			$marker = 'even';
		else
			$marker = 'odd';
	}
}

// Reads the background configuration
function readBackground() {
	// Read the background configuration XML
	$background_data = readXML('conf', 'background');
	
	// Get the default values
	$background_default = defaultBackground();
	
	// Stored data array
	$background_conf = array();
	
	// Read the stored values
	if($background_data) {
		// Initialize the background configuration XML data
		$background_xml = new SimpleXMLElement($background_data);
		
		// Loop the notice configuration elements
		foreach($background_xml->children() as $background_child)
			$background_conf[$background_child->getName()] = $background_child;
	}
	
	// Checks no value is missing in the stored configuration
	foreach($background_default as $background_name => $background_value) {
		if(!isset($background_conf[$background_name]) || empty($background_conf[$background_name]))
			$background_conf[$background_name] = $background_default[$background_name];
	}
	
	return $background_conf;
}

// Writes the background configuration
function writeBackground($array) {
	// Generate the XML data
	$xml = '';
	
	foreach($array as $key => $value)
		$xml .= "\n".'	<'.$key.'>'.stripslashes(htmlspecialchars($value)).'</'.$key.'>';
	
	// Write this data
	writeXML('conf', 'background', $xml);
}

// Generates a list of the available background images
function getBackgrounds() {
	// Initialize the result array
	$array = array();
	
	// Scan the background directory
	$scan = scandir(JAPPIX_BASE.'/store/backgrounds/');
	
	foreach($scan as $current) {
		if(isImage($current))
			array_push($array, $current);
	}
	
	return $array;
}

// Writes the notice configuration
function writeNotice($type, $simple) {
	// Generate the XML data
	$xml = 
	'<type>'.$type.'</type>
	<notice>'.stripslashes(htmlspecialchars($simple)).'</notice>'
	;
	
	// Write this data
	writeXML('conf', 'notice', $xml);
}

?>
