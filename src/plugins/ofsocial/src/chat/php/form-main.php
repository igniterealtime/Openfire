<?php

/*

Jappix - An open social platform
This is the main configuration form (install & manager)

-------------------------------------------------

License: AGPL
Authors: Valérian Saliou, Emmanuel Gil Peyrot
Last revision: 27/05/11

*/

// Someone is trying to hack us?
if(!defined('JAPPIX_BASE'))
	exit;

// Checks the checkboxes which are set "on"
$checked = ' checked=""';

// Host locking
if($lock_host == 'on')
	$check_lock_host = $checked;
else
	$check_lock_host = '';

// Anonymous mode
if($anonymous_mode == 'on')
	$check_anonymous_mode = $checked;
else
	$check_anonymous_mode = '';

// Registration
if($registration == 'on')
	$check_registration = $checked;
else
	$check_registration = '';

// BOSH proxy
if($bosh_proxy == 'on')
	$check_bosh_proxy = $checked;
else
	$check_bosh_proxy = '';

// Manager link
if($manager_link == 'on')
	$check_manager_link = $checked;
else
	$check_manager_link = '';

// Encryption
if($encryption == 'on')
	$check_encryption = $checked;
else
	$check_encryption = '';

// HTTPS storage
if($https_storage == 'on')
	$check_https_storage = $checked;
else
	$check_https_storage = '';

// Force HTTPS
if($https_force == 'on')
	$check_https_force = $checked;
else
	$check_https_force = '';

// Compression
if($compression == 'on')
	$check_compression = $checked;
else
	$check_compression = '';

?>

<a class="info smallspace neutral" href="http://codingteam.net/project/jappix/doc/JappixApp#title-2" target="_blank"><?php _e("Need help? You'd better read our documentation page about how to fill this form!"); ?></a>

<fieldset>
	<legend><?php _e("Service"); ?></legend>
	
	<label for="service_name"><?php _e("Service name"); ?></label><input id="service_name" type="text" name="service_name" value="<?php echo $service_name; ?>" maxlength="14" />
	
	<label for="service_desc"><?php _e("Service description"); ?></label><input id="service_desc" type="text" name="service_desc" value="<?php echo $service_desc; ?>" maxlength="30" />
</fieldset>

<fieldset>
	<legend><?php _e("Connection"); ?></legend>
	
	<label for="jappix_resource"><?php _e("Resource"); ?></label><input id="jappix_resource" type="text" name="jappix_resource" value="<?php echo $jappix_resource; ?>" maxlength="1023" />
	
	<label for="lock_host"><?php _e("Lock the host"); ?></label><input id="lock_host" type="checkbox" name="lock_host"<?php echo $check_lock_host; ?> />
	
	<label for="anonymous_mode"><?php _e("Anonymous mode"); ?></label><input id="anonymous_mode" type="checkbox" name="anonymous_mode"<?php echo $check_anonymous_mode; ?> />
	
	<label for="registration"><?php _e("Registration allowed"); ?></label><input id="registration" type="checkbox" name="registration"<?php echo $check_registration; ?> />
	
	<label for="bosh_proxy"><?php _e("Use a proxy"); ?></label><input id="bosh_proxy" type="checkbox" name="bosh_proxy"<?php echo $check_bosh_proxy; ?> />
</fieldset>

<fieldset>
	<legend><?php _e("Others"); ?></legend>
	
	<label for="manager_link"><?php _e("Manager link"); ?></label><input id="manager_link" type="checkbox" name="manager_link"<?php echo $check_manager_link; ?> />
</fieldset>

<fieldset>
	<legend><?php _e("Advanced"); ?></legend>
	
	<label for="encryption"><?php _e("Encryption"); ?></label><input id="encryption" type="checkbox" name="encryption"<?php echo $check_encryption; ?> />
	
	<label for="https_storage"><?php _e("HTTPS storage"); ?></label><input id="https_storage" type="checkbox" name="https_storage"<?php echo $check_https_storage; ?> />
	
	<label for="https_force"><?php _e("Force HTTPS"); ?></label><input id="https_force" type="checkbox" name="https_force"<?php echo $check_https_force; ?> />
	
	<label for="compression"><?php _e("Compression"); ?></label><input id="compression" type="checkbox" name="compression"<?php echo $check_compression; ?> />
	
	<input type="hidden" name="multi_files" value="<?php echo $multi_files; ?>" />
	
	<input type="hidden" name="developer" value="<?php echo $developer; ?>" />
</fieldset>
