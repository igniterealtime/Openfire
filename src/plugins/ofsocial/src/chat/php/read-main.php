<?php

/*

Jappix - An open social platform
This is the main configuration reader

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 27/05/11

*/

// Someone is trying to hack us?
if(!defined('JAPPIX_BASE'))
	exit;

// Finally, define the main configuration globals
define('SERVICE_NAME', "Jappix");
define('SERVICE_DESC', "Jappix Client");
define('JAPPIX_RESOURCE', "jappix");
define('LOCK_HOST', 'on');
define('ANONYMOUS', 'off');
define('REGISTRATION', 'off');
define('BOSH_PROXY', 'off');
define('MANAGER_LINK', 'off');
define('ENCRYPTION', 'off');
define('HTTPS_STORAGE', 'off');
define('HTTPS_FORCE', 'off');
define('COMPRESSION', 'off');
define('MULTI_FILES', 'off');
define('DEVELOPER', 'on');

?>
