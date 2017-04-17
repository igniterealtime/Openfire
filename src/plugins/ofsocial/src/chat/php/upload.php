<?php

/*

Jappix - An open social platform
This is the Jappix Static PHP/HTML code

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 27/05/11

*/

// Someone is trying to hack us?
if(!defined('JAPPIX_BASE'))
	exit;

?>
<!DOCTYPE html>
<?php htmlTag($locale); ?>

<head>
	<meta http-equiv="content-type" content="text/html; charset=utf-8" />
	<title><?php echo htmlspecialchars(SERVICE_NAME); ?> &bull; <?php _e("User uploads server"); ?></title>
	<link rel="shortcut icon" href="./favicon.ico" />
</head>

<body>
	<h1><?php echo htmlspecialchars(SERVICE_NAME); ?> - <?php _e("User uploads server"); ?></h1>
	<p><?php printf(T_("This is the user uploads server for %1s, “%2s”."), htmlspecialchars(SERVICE_NAME), htmlspecialchars(SERVICE_DESC)); ?></p>
	<?php if(showManagerLink()) { ?>
	<p><a href="./?m=manager<?php echo keepGet('m', false); ?>"><?php _e("Manager"); ?></a></p>
	<?php } ?>
</body>

</html>

<!-- Jappix Upload <?php echo $version; ?> - An open social platform -->
