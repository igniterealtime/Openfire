<?php

/*

Jappix - An open social platform
This is the hosts configuration form (install & manager)

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 11/06/11

*/

// Someone is trying to hack us?
if(!defined('JAPPIX_BASE'))
	exit;

?>
<a class="info smallspace neutral" href="http://codingteam.net/project/jappix/doc/JappixApp#title-3" target="_blank"><?php _e("Need help? You'd better read our documentation page about how to fill this form!"); ?></a>

<fieldset>
	<legend><?php _e("General"); ?></legend>
	
	<label for="host_main"><?php _e("Main host"); ?></label><input id="host_main" type="text" name="host_main" value="<?php echo $host_main; ?>" pattern="[^@/]+" />
	
	<label for="host_muc"><?php _e("Groupchat host"); ?></label><input id="host_muc" type="text" name="host_muc" value="<?php echo $host_muc; ?>" pattern="[^@/]+" />
	
	<label for="host_pubsub"><?php _e("Pubsub host"); ?></label><input id="host_pubsub" type="text" name="host_pubsub" value="<?php echo $host_pubsub; ?>" pattern="[^@/]+" />
</fieldset>

<fieldset>
	<legend><?php _e("Advanced"); ?></legend>
	
	<label for="host_anonymous"><?php _e("Anonymous host"); ?></label><input id="host_anonymous" type="text" name="host_anonymous" value="<?php echo $host_anonymous; ?>" pattern="[^@/]+" />
	
	<label for="host_vjud"><?php _e("Directory host"); ?></label><input id="host_vjud" type="text" name="host_vjud" value="<?php echo $host_vjud; ?>" pattern="[^@/]+" />
	
	<label for="host_bosh"><?php _e("BOSH host"); ?></label><input id="host_bosh" type="text" name="host_bosh" value="<?php echo $host_bosh; ?>" />
	
	<input type="hidden" name="host_bosh_main" value="<?php echo $host_bosh_main; ?>" />
	
	<input type="hidden" name="host_bosh_mini" value="<?php echo $host_bosh_mini; ?>" />
	
	<input type="hidden" name="host_static" value="<?php echo $host_static; ?>" />
	
	<input type="hidden" name="host_upload" value="<?php echo $host_upload; ?>" />
</fieldset>
