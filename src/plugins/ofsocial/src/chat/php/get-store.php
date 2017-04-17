<?php

/*

Jappix - An open social platform
This is the store configuration GET handler (manager)

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 28/12/10

*/

// Someone is trying to hack us?
if(!defined('JAPPIX_BASE'))
	exit;

// Purge requested
if(isset($_GET['p']) && preg_match('/^((everything)|(cache)|(logs)|(updates))$/', $_GET['p'])) {
	purgeFolder($_GET['p']);
?>
	
	<p class="info smallspace success"><?php _e("The storage folder you wanted to clean is now empty!"); ?></p>

<?php }

// Folder view?
if(isset($_GET['b']) && isset($_GET['s'])) {
	if($_GET['b'] == 'share')
		$share_folder = urldecode($_GET['s']);
	else if($_GET['b'] == 'music')
		$music_folder = urldecode($_GET['s']);
}

?>
