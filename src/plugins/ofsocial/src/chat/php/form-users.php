<?php

/*

Jappix - An open social platform
This is the user add form (install & manager)

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 08/05/11

*/

// Someone is trying to hack us?
if(!defined('JAPPIX_BASE'))
	exit;

?>

<fieldset>
	<legend><?php _e("New"); ?></legend>
	
	<label for="user_name"><?php _e("User"); ?></label><input id="user_name" class="icon <?php echo($form_parent); ?>-images" type="text" name="user_name" value="<?php echo(htmlspecialchars($user_name)); ?>" maxlength="30" />
	
	<label for="user_password"><?php _e("Password"); ?></label><input id="user_password" class="icon <?php echo($form_parent); ?>-images" type="password" name="user_password" maxlength="40" />
	
	<label for="user_repassword"><?php _e("Confirm"); ?></label><input id="user_repassword" class="icon <?php echo($form_parent); ?>-images" type="password" name="user_repassword" maxlength="40" />
</fieldset>
