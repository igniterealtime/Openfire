<?php

/*

Jappix - An open social platform
This is the Jappix Manager PHP/HTML code

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 08/05/11

*/

// Someone is trying to hack us?
if(!defined('JAPPIX_BASE'))
	exit;

// Get the manager functions
require_once(JAPPIX_BASE.'/php/functions-manager.php');

// Session manager
$id = 0;
$login_fired = false;
$logout_fired = false;
$form_parent = 'manager';
$user_password = '';
$user_remember = '';
$user = '';
$password = '';
$user_meta = T_("unknown");
$user_name = '';
$add_button = false;
$remove_button = false;
$save_button = false;
$check_updates = false;

// Start the session
session_start();

// Force the updates check?
if(isset($_GET['p']) && ($_GET['p'] == 'check'))
	$check_updates = true;

// Login form is sent
if(isset($_POST['login'])) {
	// Form sent pointer
	$login_fired = true;
	
	// Extract the user name
	if(isset($_POST['admin_name']) && !empty($_POST['admin_name']))
		$user = trim($_POST['admin_name']);
	
	if($user && (isset($_POST['admin_password']) && !empty($_POST['admin_password']))) {
		// Get the password values
		$password = genStrongHash(trim($_POST['admin_password']));
		
		// Write the session
		$_SESSION['jappix_user'] = $user;
		$_SESSION['jappix_password'] = $password;
	}
}

// Session is set
else if((isset($_SESSION['jappix_user']) && !empty($_SESSION['jappix_user'])) && (isset($_SESSION['jappix_password']) && !empty($_SESSION['jappix_password']))) {
	// Form sent pointer
	$login_fired = true;
	
	// Get the session values
	$user = $_SESSION['jappix_user'];
	$password = $_SESSION['jappix_password'];
}

// Validate the current session
if($login_fired && isAdmin($user, $password))
	$id = 1;

// Any special page requested (and authorized)?
if(($id != 0) && isset($_GET['a']) && !empty($_GET['a'])) {
	// Extract the page name
	$page_requested = $_GET['a'];
	
	switch($page_requested) {
		// Logout request
		case 'logout':
			// Remove the session
			unset($_SESSION['jappix_user']);
			unset($_SESSION['jappix_password']);
			
			// Set a logout marker
			$logout_fired = true;
			
			// Page ID
			$id = 0;
			
			break;
		
		// Configuration request
		case 'configuration':
			// Allowed buttons
			$save_button = true;
			
			// Page ID
			$id = 2;
			
			break;
		
		// Hosts request
		case 'hosts':
			// Allowed buttons
			$save_button = true;
			
			// Page ID
			$id = 3;
			
			break;
		
		// Storage request
		case 'storage':
			// Allowed buttons
			$remove_button = true;
			
			// Page ID
			$id = 4;
			
			break;
		
		// Design request
		case 'design':
			// Allowed buttons
			$save_button = true;
			$remove_button = true;
			
			// Page ID
			$id = 5;
			
			break;
		
		// Users request
		case 'users':
			// Allowed buttons
			$add_button = true;
			$remove_button = true;
			
			// Page ID
			$id = 6;
			
			break;
		
		// Updates request
		case 'updates':
			// Page ID
			$id = 7;
			
			break;
		
		// Default page when authorized (statistics)
		default:
			// Page ID
			$id = 1;
	}
}

// Page server-readable names
$identifiers = array(
	'login',
	'statistics',
	'configuration',
	'hosts',
	'storage',
	'design',
	'users',
	'updates'
);

// Page human-readable names
$names = array(
	T_("Manager access"),
	T_("Statistics"),
	T_("Configuration"),
	T_("Hosts"),
	T_("Storage"),
	T_("Design"),
	T_("Users"),
	T_("Updates")
);

// Any user for the meta?
if($user && ($id != 0))
	$user_meta = $user;

// Define current page identifier & name
$page_identifier = $identifiers[$id];
$page_name = $names[$id];

// Define the current page form action
if($id == 0)
	$form_action = keepGet('(m|a|p)', false);
else
	$form_action = keepGet('(m|p)', false);

?>
<!DOCTYPE html>
<?php htmlTag($locale); ?>

<head>
	<meta http-equiv="content-type" content="text/html; charset=utf-8" />
	<meta name="robots" content="none" />
	<title><?php _e("Jappix manager"); ?> &bull; <?php echo($page_name); ?></title>
	<link rel="shortcut icon" href="./favicon.ico" />
	<?php echoGetFiles($hash, '', 'css', 'manager.xml', ''); echo "\n"; ?>
	<!--[if lt IE 9]><?php echoGetFiles($hash, '', 'css', '', 'ie.css'); ?><![endif]-->
</head>

<body class="body-images">
	<form id="manager" enctype="multipart/form-data" method="post" action="./?m=manager<?php echo $form_action; ?>">
		<div id="manager-top">
			<div class="logo manager-images"><?php _e("Manager"); ?></div>
			
			<div class="meta">
				<span><?php echo(htmlspecialchars($user_meta)); ?></span>
				
				<?php if($id != 0) {
				
					// Keep get
					$keep_get = keepGet('(a|p|b|s)', false);
				
				?>
					<a class="logout manager-images" href="./?a=logout<?php echo $keep_get; ?>"><?php _e("Disconnect"); ?></a>
				<?php } ?>
				
				<a class="close manager-images" href="./<?php echo keepGet('(m|a|p|b|s)', true); ?>"><?php _e("Close"); ?></a>
			</div>
			
			<div class="clear"></div>
		</div>
		
		<?php if($id != 0) { ?>
			<div id="manager-tabs">
				<a<?php currentTab('statistics', $page_identifier); ?> href="./?a=statistics<?php echo $keep_get; ?>"><?php _e("Statistics"); ?></a>
				<a<?php currentTab('configuration', $page_identifier); ?> href="./?a=configuration<?php echo $keep_get; ?>"><?php _e("Configuration"); ?></a>
				<a<?php currentTab('hosts', $page_identifier); ?> href="./?a=hosts<?php echo $keep_get; ?>"><?php _e("Hosts"); ?></a>
				<a<?php currentTab('storage', $page_identifier); ?> href="./?a=storage<?php echo $keep_get; ?>"><?php _e("Storage"); ?></a>
				<a<?php currentTab('design', $page_identifier); ?> href="./?a=design<?php echo $keep_get; ?>"><?php _e("Design"); ?></a>
				<a<?php currentTab('users', $page_identifier); ?> href="./?a=users<?php echo $keep_get; ?>"><?php _e("Users"); ?></a>
				<a<?php currentTab('updates', $page_identifier); ?> class="last" href="./?a=updates<?php echo $keep_get; ?>"><?php _e("Updates"); ?></a>
			</div>
		<?php } ?>
		
		<div id="manager-content">
			<?php
			
			if($id != 0) {
				if(!storageWritable()) { ?>
					<p class="info bottomspace fail"><?php _e("Your storage folders are not writable, please apply the good rights!"); ?></p>
				<?php }
				
				if(BOSHProxy() && extension_loaded('suhosin') && (ini_get('suhosin.get.max_value_length') < 1000000)) { ?>
					<p class="info bottomspace neutral"><?php printf(T_("%1s may cause problems to the proxy, please increase %2s value up to %3s!"), 'Suhosin', '<em>suhosin.get.max_value_length</em>', '1000000'); ?></p>
				<?php }
				
				if(newUpdates($check_updates)) { ?>
					<a class="info bottomspace neutral" href="./?a=updates<?php echo $keep_get; ?>"><?php _e("A new Jappix version is available! Check what is new and launch the update!"); ?></a>
				<?php }
			}
			
			// Authorized and statistics page requested
			if($id == 1) { ?>
				<h3 class="statistics manager-images"><?php _e("Statistics"); ?></h3>
				
				<p><?php _e("Basic statistics are processed by Jappix about some important things, you can find them below."); ?></p>
				
				<h4><?php _e("Access statistics"); ?></h4>
				
				<?php
				
				// Read the visits values
				$visits = getVisits();
				
				?>
				
				<ul class="stats">
					<li class="total"><b><?php _e("Total"); ?></b><span><?php echo $visits['total']; ?></span></li>
					<li><b><?php _e("Daily"); ?></b><span><?php echo $visits['daily']; ?></span></li>
					<li><b><?php _e("Weekly"); ?></b><span><?php echo $visits['weekly']; ?></span></li>
					<li><b><?php _e("Monthly"); ?></b><span><?php echo $visits['monthly']; ?></span></li>
					<li><b><?php _e("Yearly"); ?></b><span><?php echo $visits['yearly']; ?></span></li>
				</ul>
				
				<object class="stats" type="image/svg+xml" data="./php/stats-svg.php?l=<?php echo $locale; ?>&amp;g=access"></object>
				
				<?php
				
				// Get the share stats
				$share_stats = shareStats();
				
				// Any share stats to display?
				if(count($share_stats)) { ?>
					<h4><?php _e("Share statistics"); ?></h4>
					
					<ol class="stats">
						<?php
						
						// Display the users who have the largest share folder
						$share_users = largestShare($share_stats, 8);
						
						foreach($share_users as $current_user => $current_value)
							echo('<li><b><a href="xmpp:'.$current_user.'">'.$current_user.'</a></b><span>'.formatBytes($current_value).'</span></li>');
						
						?>
					</ol>
					
					<object class="stats" type="image/svg+xml" data="./php/stats-svg.php?l=<?php echo $locale; ?>&amp;g=share"></object>
				<?php } ?>
				
				<h4><?php _e("Other statistics"); ?></h4>
				
				<ul class="stats">
					<li class="total"><b><?php _e("Total"); ?></b><span><?php echo formatBytes(sizeDir(JAPPIX_BASE.'/store/')); ?></span></li>
					
					<?php
					
					// Append the human-readable array values
					$others_stats = otherStats();
					
					foreach($others_stats as $others_name => $others_value)
						echo('<li><b>'.$others_name.'</b><span>'.formatBytes($others_value).'</span></li>');
					
					?>
				</ul>
				
				<object class="stats" type="image/svg+xml" data="./php/stats-svg.php?l=<?php echo $locale; ?>&amp;g=others"></object>
			<?php }
			
			// Authorized and configuration page requested
			else if($id == 2) { ?>
				<h3 class="configuration manager-images"><?php _e("Configuration"); ?></h3>
				
				<p><?php _e("Change your Jappix node configuration with this tool."); ?></p>
				
				<p><?php _e("Note that if you don't specify a value which is compulsory, it will be automatically completed with the default one."); ?></p>
				
				<?php
				
				// Define the main configuration variables
				include(JAPPIX_BASE.'/php/vars-main.php');
				
				// Read the main configuration POST
				if(isset($_POST['save'])) {
					include(JAPPIX_BASE.'/php/post-main.php');
					
					// Show a success alert
					?>
						<p class="info smallspace success"><?php _e("Changes saved!"); ?></p>
					<?php
				}
				
				// Include the main configuration form
				include(JAPPIX_BASE.'/php/form-main.php');
			}
			
			// Authorized and hosts page requested
			else if($id == 3) { ?>
				<h3 class="hosts manager-images"><?php _e("Hosts"); ?></h3>
				
				<p><?php _e("Change the XMPP hosts that this Jappix node serve with this tool."); ?></p>
				
				<p><?php _e("Maybe you don't know what a BOSH server is? In fact, this is a relay between a Jappix client and a XMPP server, which is necessary because of technical limitations."); ?></p>
				
				<p><?php _e("Note that if you don't specify a value which is compulsory, it will be automatically completed with the default one."); ?></p>
				
				<?php
				
				// Define the hosts configuration variables
				include(JAPPIX_BASE.'/php/vars-hosts.php');
				
				// Read the hosts configuration POST
				if(isset($_POST['save'])) {
					include(JAPPIX_BASE.'/php/post-hosts.php');
					
					// Show a success alert
					?>
						<p class="info smallspace success"><?php _e("Changes saved!"); ?></p>
					<?php
				}
				
				// Include the hosts configuration form
				include(JAPPIX_BASE.'/php/form-hosts.php');
			}
			
			// Authorized and storage page requested
			else if($id == 4) { ?>
				<h3 class="storage manager-images"><?php _e("Storage"); ?></h3>
				
				<p><?php _e("All this Jappix node stored files can be managed with this tool: please select a sub-folder and start editing its content!"); ?></p>
				
				<?php
				
					// Include the store configuration vars
					include(JAPPIX_BASE.'/php/vars-store.php');
					
					// Include the store configuration POST handler
					include(JAPPIX_BASE.'/php/post-store.php');
					
					// Include the store configuration GET handler
					include(JAPPIX_BASE.'/php/get-store.php');
					
				?>
				
				<h4><?php _e("Maintenance"); ?></h4>
				
				<p><?php _e("Keep your Jappix node fresh and fast, clean the storage folders regularly!"); ?></p>
				
				<?php
				
					// Keep get
					$keep_get = keepGet('p', false);
				
				?>
				
				<ul>
					<li class="total"><a href="./?p=everything<?php echo $keep_get; ?>"><?php _e("Clean everything"); ?></a></li>
					<li><a href="./?p=cache<?php echo $keep_get; ?>"><?php _e("Purge cache"); ?></a></li>
					<li><a href="./?p=logs<?php echo $keep_get; ?>"><?php _e("Purge logs"); ?></a></li>
					<li><a href="./?p=updates<?php echo $keep_get; ?>"><?php _e("Purge updates"); ?></a></li>
				</ul>
				
				<h4><?php _e("Share"); ?></h4>
				
				<p><?php _e("Stay tuned in what your users store on your server and remove undesired content with this tool."); ?></p>
				
				<fieldset>
					<legend><?php _e("Browse"); ?></legend>
					
					<div class="browse">
						<?php
						
						// List the share files
						browseFolder($share_folder, 'share');
						
						?>
					</div>
				</fieldset>
				
				<h4><?php _e("Music"); ?></h4>
				
				<p><?php _e("Upload your music (Ogg Vorbis, MP3 or WAV) to be able to listen to it in Jappix!"); ?></p>
				
				<p><?php printf(T_("The file you want to upload must be smaller than %s."), formatBytes(uploadMaxSize()).''); ?></p>
				
				<fieldset>
					<legend><?php _e("New"); ?></legend>
					
					<input type="hidden" name="MAX_FILE_SIZE" value="<?php echo(uploadMaxSize().''); ?>">
					
					<label for="music_title"><?php _e("Title"); ?></label><input id="music_title" class="icon manager-images" type="text" name="music_title" value="<?php echo(htmlspecialchars($music_title)); ?>" />
					
					<label for="music_artist"><?php _e("Artist"); ?></label><input id="music_artist" class="icon manager-images" type="text" name="music_artist" value="<?php echo(htmlspecialchars($music_artist)); ?>" />
					
					<label for="music_album"><?php _e("Album"); ?></label><input id="music_album" class="icon manager-images" type="text" name="music_album" value="<?php echo(htmlspecialchars($music_album)); ?>" />
					
					<label for="music_file"><?php _e("File"); ?></label><input id="music_file" type="file" name="music_file" accept="audio/*" />
					<label for="music_upload"><?php _e("Upload"); ?></label><input id="music_upload" type="submit" name="upload" value="<?php _e("Upload"); ?>" />
				</fieldset>
				
				<fieldset>
					<legend><?php _e("Browse"); ?></legend>
					
					<div class="browse">
						<?php
						
						// List the music files
						browseFolder($music_folder, 'music');
						
						?>
					</div>
				</fieldset>
			<?php }
			
			// Authorized and design page requested
			else if($id == 5) { ?>
				<h3 class="design manager-images"><?php _e("Design"); ?></h3>
				
				<p><?php _e("Jappix is fully customisable: you can change its design right here."); ?></p>
				
				<?php
				
					// Include the design configuration vars
					include(JAPPIX_BASE.'/php/vars-design.php');
					
					// Include the design configuration POST handler
					include(JAPPIX_BASE.'/php/post-design.php');
					
					// Include the design configuration reader
					include(JAPPIX_BASE.'/php/read-design.php');
					
					// Folder view?
					if(isset($_GET['b']) && isset($_GET['s']) && ($_GET['b'] == 'backgrounds'))
						$backgrounds_folder = urldecode($_GET['s']);
				
				?>
				
				<h4><?php _e("Background"); ?></h4>
				
				<p><?php _e("Change your Jappix node background with this tool. You can either set a custom color or an uploaded image. Let your creativity flow!"); ?></p>
				
				<label class="master" for="background_default"><input id="background_default" type="radio" name="background_type" value="default"<?php echo($background_default); ?> /><?php _e("Use default background"); ?></label>
				
				<?php if($backgrounds_number) { ?>
					<label class="master" for="background_image"><input id="background_image" type="radio" name="background_type" value="image"<?php echo($background_image); ?> /><?php _e("Use your own image"); ?></label>
				
					<div class="sub">
						<p><?php _e("Select a background to use and change the display options."); ?></p>
						
						<label for="background_image_file"><?php _e("Image"); ?></label><select id="background_image_file" name="background_image_file">
							<?php
							
								// List the background files
								foreach($backgrounds as $backgrounds_current) {
									// Check this is the selected background
									if($backgrounds_current == $background['image_file'])
										$backgrounds_selected = ' selected=""';
									else
										$backgrounds_selected = '';
									
									// Encode the current background name
									$backgrounds_current = htmlspecialchars($backgrounds_current);
									
									echo('<option value="'.$backgrounds_current.'"'.$backgrounds_selected.'>'.$backgrounds_current.'</option>');
								}
							
							?>
						</select>
						
						<label for="background_image_repeat"><?php _e("Repeat"); ?></label><select id="background_image_repeat" name="background_image_repeat">
							<option value="no-repeat"<?php echo($background_image_repeat_no); ?>><?php _e("No"); ?></option>
							<option value="repeat"<?php echo($background_image_repeat_all); ?>><?php _e("All"); ?></option>
							<option value="repeat-x"<?php echo($background_image_repeat_x); ?>><?php _e("Horizontal"); ?></option>
							<option value="repeat-y"<?php echo($background_image_repeat_y); ?>><?php _e("Vertical"); ?></option>
						</select>
						
						<label for="background_image_horizontal"><?php _e("Horizontal"); ?></label><select id="background_image_horizontal" name="background_image_horizontal">
							<option value="center"<?php echo($background_image_horizontal_center); ?>><?php _e("Center"); ?></option>
							<option value="left"<?php echo($background_image_horizontal_left); ?>><?php _e("Left"); ?></option>
							<option value="right"<?php echo($background_image_horizontal_right); ?>><?php _e("Right"); ?></option>
						</select>
						
						<label for="background_image_vertical"><?php _e("Vertical"); ?></label><select id="background_image_vertical" name="background_image_vertical">
							<option value="center"<?php echo($background_image_vertical_center); ?>><?php _e("Center"); ?></option>
							<option value="top"<?php echo($background_image_vertical_top); ?>><?php _e("Top"); ?></option>
							<option value="bottom"<?php echo($background_image_vertical_bottom); ?>><?php _e("Bottom"); ?></option>
						</select>
						
						<label for="background_image_adapt"><?php _e("Adapt"); ?></label><input id="background_image_adapt" type="checkbox" name="background_image_adapt"<?php echo($background_image_adapt); ?> />
						
						<label for="background_image_color"><?php _e("Color"); ?></label><input id="background_image_color" class="icon manager-images" type="color" name="background_image_color" value="<?php echo(htmlspecialchars($background['image_color'])); ?>" />
						
						<div class="clear"></div>
					</div>
				<?php } ?>
				
				<label class="master" for="background_color"><input id="background_color" type="radio" name="background_type" value="color"<?php echo($background_color); ?> /><?php _e("Use your own color"); ?></label>
				
				<div class="sub">
					<p><?php _e("Type the hexadecimal color value you want to use as a background."); ?></p>
					
					<label for="background_color_color"><?php _e("Color"); ?></label><input id="background_color_color" class="icon manager-images" type="color" name="background_color_color" value="<?php echo(htmlspecialchars($background['color_color'])); ?>" />
					
					<div class="clear"></div>
				</div>
				
				<h4><?php _e("Manage backgrounds"); ?></h4>
				
				<p><?php _e("You can add a new background to the list with this tool. Please send a valid image."); ?></p>
				
				<div class="sub">
					<p><?php printf(T_("The file you want to upload must be smaller than %s."), formatBytes(uploadMaxSize()).''); ?></p>
					
					<input type="hidden" name="MAX_FILE_SIZE" value="<?php echo(uploadMaxSize().''); ?>">
					
					<label for="background_image_upload"><?php _e("File"); ?></label><input id="background_image_upload" type="file" name="background_image_upload" accept="image/*" />
					<label for="background_image_upload"><?php _e("Upload"); ?></label><input id="background_image_upload" type="submit" name="upload" value="<?php _e("Upload"); ?>" />
					
					<div class="clear"></div>
				</div>
				
				<p><?php _e("If you want to remove some backgrounds, use the browser below."); ?></p>
				
				<fieldset>
					<legend><?php _e("List"); ?></legend>
					
					<div class="browse">
						<?php
						
						// List the background files
						browseFolder($backgrounds_folder, 'backgrounds');
						
						?>
					</div>
				</fieldset>
				
				<h4><?php _e("Notice"); ?></h4>
				
				<p><?php _e("Define a homepage notice for all your users, such as a warn, an important message or an advert with this tool."); ?></p>
				
				<label class="master" for="notice_none"><input id="notice_none" type="radio" name="notice_type" value="none"<?php echo($notice_none); ?> /><?php _e("None"); ?></label>
				
				<label class="master" for="notice_simple"><input id="notice_simple" type="radio" name="notice_type" value="simple"<?php echo($notice_simple); ?> /><?php _e("Simple notice"); ?></label>
				
				<div class="sub">
					<p><?php _e("This notice only needs simple text to be displayed, but no code is allowed!"); ?></p>
				</div>
				
				<label class="master" for="notice_advanced"><input id="notice_advanced" type="radio" name="notice_type" value="advanced"<?php echo($notice_advanced); ?> /><?php _e("Advanced notice"); ?></label>
				
				<div class="sub">
					<p><?php _e("You can customize your notice with embedded HTML, CSS and JavaScript, but you need to code the style."); ?></p>
				</div>
				
				<div class="clear"></div>
				
				<textarea class="notice-text" name="notice_text" rows="8" cols="60"><?php echo(htmlspecialchars($notice_text)); ?></textarea>
			<?php }
			
			// Authorized and users page requested
			else if($id == 6) { ?>
				<h3 class="users manager-images"><?php _e("Users"); ?></h3>
				
				<p><?php _e("You can define more than one administrator for this Jappix node. You can also change a password with this tool."); ?></p>
				
				<?php
				
				// Add an user?
				if(isset($_POST['add'])) {
					// Include the users POST handler
					include(JAPPIX_BASE.'/php/post-users.php');
					
					if($valid_user) { ?>
						<p class="info smallspace success"><?php _e("The user has been added!"); ?></p>
					<?php }
					
					else { ?>
						<p class="info smallspace fail"><?php _e("Oops, you missed something or the two passwords do not match!"); ?></p>
				<?php }
				}
				
				// Remove an user?
				else if(isset($_POST['remove'])) {
					// Initialize the match
					$users_removed = false;
					$users_remove = array();
					
					// Try to get the users to remove
					foreach($_POST as $post_key => $post_value) {
						// Is it an admin user?
						if(preg_match('/^admin_(.+)$/i', $post_key)) {
							// Update the marker
							$users_removed = true;
							
							// Push the value to the global array
							array_push($users_remove, $post_value);
						}
					}
					
					// Somebody has been removed
					if($users_removed) {
					
						// Remove the users!
						manageUsers('remove', $users_remove);
					
					?>
						<p class="info smallspace success"><?php _e("The chosen users have been removed."); ?></p>
					<?php }
					
					// Nobody has been removed
					else { ?>
						<p class="info smallspace fail"><?php _e("You must select one or more users to be removed!"); ?></p>
				<?php }
				} ?>
				
				<h4><?php _e("Add"); ?></h4>
				
				<p><?php _e("Add a new user with this tool, or change a password (type an existing username). Please submit a strong password!"); ?></p>
				
				<?php
				
				// Include the user add form
				include(JAPPIX_BASE.'/php/form-users.php');
				
				?>
				
				<h4><?php _e("Manage"); ?></h4>
				
				<p><?php _e("Remove users with this tool. Note that you cannot remove an user if he is the only one remaining."); ?></p>
				
				<fieldset>
					<legend><?php _e("List"); ?></legend>
					
					<div class="browse">
						<?php
						
						// List the users
						browseUsers();
						
						?>
					</div>
				</fieldset>
			<?php }
			
			// Authorized and updates page requested
			else if($id == 7) { ?>
				<h3 class="updates manager-images"><?php _e("Updates"); ?></h3>
				
				<p><?php _e("Update your Jappix node with this tool, or check if a new one is available. Informations about the latest version are also displayed (in english)."); ?></p>
				
				<?php
				
				// Using developer mode (no need to update)?
				if(isDeveloper()) { ?>
					<h4><?php _e("Check for updates"); ?></h4>
					
					<p class="info smallspace neutral"><?php printf(T_("You are using a development version of Jappix. Update it through our repository by executing: %s."), '<em>svn up</em>'); ?></p>
				<?php }
				
				// New updates available?
				else if(newUpdates($check_updates)) {
					// Get the update informations
					$update_infos = updateInformations();
					
					// We can launch the update!
					if(isset($_GET['p']) && ($_GET['p'] == 'update')) { ?>
						<h4><?php _e("Update in progress"); ?></h4>
						
						<?php if(processUpdate($update_infos['url'])) { ?>
							<p class="info smallspace success"><?php _e("Jappix has been updated: you are now running the latest version. Have fun!"); ?></p>
						<?php } else { ?>
							<p class="info smallspace fail"><?php _e("The update has failed! Please try again later."); ?></p>
						<?php }
					}
					
					// We just show a notice
					else {
				?>
						<h4><?php _e("Available updates"); ?></h4>
						
						<a class="info smallspace fail" href="./?p=update<?php echo keepGet('(p|b|s)', false); ?>"><?php printf(T_("Your version is out to date. Update it now to %s by clicking here!"), '<em>'.$update_infos['id'].'</em>'); ?></a>
						
						<h4><?php _e("What's new?"); ?></h4>
						
						<div><?php echo $update_infos['description']; ?></div>
				<?php }
				
				// No new update
				} else { ?>
					<h4><?php _e("Check for updates"); ?></h4>
					
					<a class="info smallspace success" href="./?p=check<?php echo keepGet('(p|b|s)', false); ?>"><?php _e("Your version seems to be up to date, but you can check updates manually by clicking here."); ?></a>
				<?php } ?>
			<?php }
			
			// Not authorized, show the login form
			else { ?>
				<h3 class="login manager-images"><?php _e("Manager access"); ?></h3>
				
				<p><?php _e("This is a restricted area: only the authorized users can manage this Jappix node."); ?></p>
				<p><?php _e("Please use the form below to login to the administration panel."); ?></p>
				<p><?php _e("To improve security, sessions are limited in time and when your browser will be closed, you will be logged out."); ?></p>
				
				<fieldset>
					<legend><?php _e("Credentials"); ?></legend>
					
					<label for="admin_name"><?php _e("User"); ?></label><input id="admin_name" class="icon manager-images" type="text" name="admin_name" value="<?php echo(htmlspecialchars($user)); ?>" required="" />
					
					<label for="admin_password"><?php _e("Password"); ?></label><input id="admin_password" class="icon manager-images" type="password" name="admin_password" required="" />
				</fieldset>
				
				<?php
				
				// Disconnected
				if($logout_fired) { ?>
					<p class="info bigspace success"><?php _e("You have been logged out. Goodbye!"); ?></p>
				<?php }
				
				// Login error
				else if($login_fired) { ?>
					<p class="info bigspace fail"><?php _e("Oops, you could not be recognized as a valid administrator. Check your credentials!"); ?></p>
				<?php
				
					// Remove the session
					unset($_SESSION['jappix_user']);
					unset($_SESSION['jappix_password']);
				
				}
			} ?>
			
			<div class="clear"></div>
		</div>
		
		<div id="manager-buttons">
			<?php if($id == 0) { ?>
				<input type="submit" name="login" value="<?php _e("Here we go!"); ?>" />
			<?php } else { ?>
				<?php } if($add_button) { ?>
					<input type="submit" name="add" value="<?php _e("Add"); ?>" />
				<?php } if($save_button) { ?>
					<input type="submit" name="save" value="<?php _e("Save"); ?>" />
				<?php } if($remove_button) { ?>
					<input type="submit" name="remove" value="<?php _e("Remove"); ?>" />
			<?php } ?>
			
			<div class="clear"></div>
		</div>
		
	</form>
</body>

</html>

<!-- Jappix Manager <?php echo $version; ?> - An open social platform -->
