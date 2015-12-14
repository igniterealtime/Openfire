<?php

/*

Jappix - An open social platform
This is the Jappix Install PHP/HTML code

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 20/02/11

*/

// Someone is trying to hack us?
if(!defined('JAPPIX_BASE'))
	exit;

// Define the configuration folder
$conf_folder = JAPPIX_BASE.'/store/conf';

// Initialize the step
$step = 1;

// Initialize some vars
$form_parent = 'install';
$user_name = '';
$user_password = '';
$valid_user = true;

if(isset($_POST['step']) && !empty($_POST['step'])) {
	$step = intval($_POST['step']);
	
	switch($step) {
		// Administrator account configuration submitted
		case 3:
			include(JAPPIX_BASE.'/php/post-users.php');
			break;
		
		// Main configuration submitted
		case 4:
			include(JAPPIX_BASE.'/php/post-main.php');
			break;
		
		// Hosts configuration submitted
		case 5:
			include(JAPPIX_BASE.'/php/post-hosts.php');
			break;
	}
}

// Not frozen on the previous step?
if(!isset($_POST['check']) && (isset($_POST['submit']) || isset($_POST['finish']))) {
	// Checks the current step is valid
	if(($step >= 2) && !is_dir($conf_folder))
		$step = 2;
	else if(($step >= 3) && !usersConfName())
		$step = 3;
	else if(($step >= 4) && !file_exists($conf_folder.'/main.xml'))
		$step = 4;
	else if(($step >= 5) && !file_exists($conf_folder.'/hosts.xml'))
		$step = 5;
	else
		$step++;
}

// These steps are not available
if(($step > 6) || !is_int($step))
	$step = 6;

// Get the current step title
$names = array(
	T_("Welcome"),
	T_("Storage configuration"),
	T_("Administrator account"),
	T_("Main configuration"),
	T_("Hosts configuration"),
	T_("Services installation")
);

// Continue marker
$continue = true;

// Form action
if($step < 6)
	$form_action = './?m=install'.keepGet('m', false);
else
	$form_action = './'.keepGet('m', true);

?>
<!DOCTYPE html>
<?php htmlTag($locale); ?>

<head>
	<meta http-equiv="content-type" content="text/html; charset=utf-8" />
	<meta name="robots" content="none" />
	<title><?php _e("Jappix installation"); ?> &bull; <?php echo($names[$step - 1]); ?></title>
	<link rel="shortcut icon" href="./favicon.ico" />
	<?php echoGetFiles($hash, '', 'css', 'install.xml', ''); echo "\n"; ?>
	<!--[if lt IE 9]><?php echoGetFiles($hash, '', 'css', '', 'ie.css'); ?><![endif]-->
</head>

<body class="body-images">
	<form id="install" method="post" action="<?php echo $form_action; ?>">
		<div id="install-top">
			<div class="logo install-images"><?php _e("Installation"); ?></div>
			<div class="step"><?php echo $step; ?> <span>/ 6</span></div>
			<div class="clear"></div>
			
			<input type="hidden" name="step" value="<?php echo($step); ?>" />
		</div>
		
		<div id="install-content">
			<?php
			
			// First step: welcome
			if($step == 1) { ?>
				<h3 class="start install-images"><?php _e("Welcome to the Jappix installation!"); ?></h3>
				
				<p><?php _e("This tool will help you fastly install Jappix, the first full-featured XMPP-based social platform, on your server. You don't even need any technical knowledge."); ?></p>
				<p><?php _e("Let's have a look at the installation steps:"); ?></p>
				
				<ol>
					<li><?php _e("Welcome"); ?></li>
					<li><?php _e("Storage configuration"); ?></li>
					<li><?php _e("Administrator account"); ?></li>
					<li><?php _e("Main configuration"); ?></li>
					<li><?php _e("Hosts configuration"); ?></li>
					<li><?php _e("Services installation"); ?></li>
				</ol>
				
				<p><?php printf(T_("If the current language does not match yours (%1s), you can make Jappix speak %2s it will be saved."), getLanguageName($locale), languageSwitcher($locale)); ?></p>
				
				<p><?php _e("If you want to get some help about the Jappix installation and configuration, you can use our whole documentation, available at:"); ?> <a href="http://codingteam.net/project/jappix/doc" target="_blank">http://codingteam.net/project/jappix/doc</a></p>
				
				<p><?php _e("It's time to build your own social cloud: just go to the next step!"); ?></p>
			<?php }
			
			// Second step: storage configuration
			else if($step == 2) { ?>
				<h3 class="storage install-images"><?php _e("Storage configuration"); ?></h3>
				
				<p><?php _e("Jappix stores persistent data (such as shared files, chat logs, your own music and its configuration) into a single secured storage folder."); ?></p>
				
				<p><?php printf(T_("Jappix must be able to write in this folder to create its sub-directories. If not, you must set the rights to %1s or change the folder owner to %2s (depending of your configuration)."), '<em>777</em>', '<em>www-data</em>'); ?></p>
				
				<?php if(is_writable(JAPPIX_BASE.'/store')) {
					// Create the store tree
					include(JAPPIX_BASE.'/php/store-tree.php');
				?>
					<p class="info bigspace success"><?php _e("The folder is writable, you can continue!"); ?></p>
				<?php }
				
				else {
					$continue = false;
				?>
					<p class="info bigspace fail"><?php printf(T_("The folder is not writable, set the right permissions to the %s directory."), "<em>./store</em>"); ?></p>
				<?php } ?>
			<?php }
			
			// Third step: administrator account
			else if($step == 3) { ?>
				<h3 class="account  install-images"><?php _e("Administrator account"); ?></h3>
				
				<p><?php _e("Jappix offers you the possibility to manage your configuration, install new plugins or search for updates. That's why you must create an administrator account to access the manager."); ?></p>
				
				<p><?php _e("When Jappix will be installed, just click on the manager link on the home page to access it."); ?></p>
				
				<?php
				
				// Include the user add form
				include(JAPPIX_BASE.'/php/form-users.php');
				
				if(!$valid_user) { ?>
					<p class="info bigspace fail"><?php _e("Oops, you missed something or the two passwords do not match!"); ?></p>
				<?php }
			}
			
			// Fourth step: main configuration
			else if($step == 4) { ?>
				<h3 class="main install-images"><?php _e("Main configuration"); ?></h3>
				
				<p><?php _e("Jappix needs that you specify some values to work. Please correct the following inputs (or keep the default values, which are sufficient for most people)."); ?></p>
				
				<p><?php _e("Note that if you don't specify a value which is compulsory, it will be automatically completed with the default one."); ?></p>
				
				<?php
				
				// Define the main configuration variables
				include(JAPPIX_BASE.'/php/vars-main.php');
				
				// Are we using developer mode?
				if(preg_match('/~dev/i', $version))
					$developer = 'on';
				
				// Include the main configuration form
				include(JAPPIX_BASE.'/php/form-main.php');
			}
			
			// Fifth step: hosts configuration
			else if($step == 5) { ?>
				<h3 class="hosts install-images"><?php _e("Hosts configuration"); ?></h3>
				
				<p><?php _e("This page helps you specify the default hosts Jappix will connect to. You can leave it as it is and continue if you want to use the official service hosts."); ?></p>
				
				<p><?php _e("Maybe you don't know what a BOSH server is? In fact, this is a relay between a Jappix client and a XMPP server, which is necessary because of technical limitations."); ?></p>
				
				<p><?php _e("Note that if you don't specify a value which is compulsory, it will be automatically completed with the default one."); ?></p>
				
				<?php
				
				// Define the hosts configuration variables
				include(JAPPIX_BASE.'/php/vars-hosts.php');
				
				// Include the hosts configuration form
				include(JAPPIX_BASE.'/php/form-hosts.php');
			}
			
			// Last step: services installation
			else if($step == 6) { ?>
				<h3 class="services install-images"><?php _e("Services installation"); ?></h3>
				
				<p><?php _e("You can install some extra softwares on your server, to extend your Jappix features. Some others might be modified, because of security restrictions which are set by default."); ?></p>
				<p><?php _e("To perform this, you must be able to access your server's shell and be logged in as root. Remember this is facultative, Jappix will work without these modules, but some of its features will be unavailable."); ?></p>
				
				<?php
				
				// Write the installed marker
				writeXML('conf', 'installed', '<installed>true</installed>');
				
				// Checks some services are installed
				$services_functions = array('gd_info');
				$services_names = array('GD');
				$services_packages = array('php5-gd');
				
				for($i = 0; $i < count($services_names); $i++) {
					$service_class = 'info smallspace';
					
					// First info?
					if($i == 0)
						$service_class .= ' first';
					
					// Service installed?
					if(function_exists($services_functions[$i])) { ?>
						<p class="<?php echo($service_class) ?> success"><?php printf(T_("%s is installed on your system."), $services_names[$i]); ?></p>
					<?php }
					
					// Missing service!
					else { ?>
						<p class="<?php echo($service_class) ?> fail"><?php printf(T_("%1s is not installed on your system, you should install %2s."), $services_names[$i], '<em>'.$services_packages[$i].'</em>'); ?></p>
					<?php }
				}
				
				// Checks the upload size limit
				$upload_max = uploadMaxSize();
				$upload_human = formatBytes($upload_max);
				
				if($upload_max >= 7000000) { ?>
					<p class="info smallspace last success"><?php printf(T_("PHP maximum upload size is sufficient (%s)."), $upload_human); ?></p>
				<?php }
				
				else { ?>
					<p class="info smallspace last fail"><?php printf(T_("PHP maximum upload size is not sufficient (%1s), you should define it to %2s in %3s."), $upload_human, '8M', '<em>php.ini</em>'); ?></p>
				<?php } ?>
				
				<p><?php _e("After you finished the setup, Jappix will generate the cache files. It might be slow, just wait until the application is displayed and do not press any button."); ?></p>
				
				<p><?php _e("Thanks for using Jappix!"); ?></p>
			<?php } ?>
		</div>
		
		<div id="install-buttons">
			<?php if($continue && ($step < 6)) { ?>
				<input type="submit" name="submit" value="<?php _e("Next"); ?> »" />
			<?php } if($step == 6) { ?>
				<input type="submit" name="finish" value="<?php _e("Finish"); ?> »" />
			<?php } if(!$continue) { ?>
				<input type="submit" name="check" value="<?php _e("Check again"); ?>" />
			<?php } ?>
			
			<div class="clear"></div>
		</div>
	</form>
</body>

</html>

<!-- Jappix Install <?php echo $version; ?> - An open social platform -->
