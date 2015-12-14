<?php

/*

Jappix - An open social platform
This is the Jappix Mobile PHP/HTML code

-------------------------------------------------

License: AGPL
Author: Valérian Saliou
Last revision: 18/05/11

*/

// Someone is trying to hack us?
if(!defined('JAPPIX_BASE'))
	exit;

?>

<!DOCTYPE html>
<?php htmlTag($locale); ?>

<head>
	<meta http-equiv="content-type" content="text/html; charset=utf-8" />
	<title><?php _e("Jappix Mobile"); ?></title>
	<link rel="shortcut icon" href="./favicon.ico" />
	<?php echoGetFiles($hash, '', 'css', 'mobile.xml', ''); echo "\n"; ?>
	<?php echoGetFiles($hash, $locale, 'js', 'mobile.xml', ''); echo "\n"; ?>
</head>

<body>
	<div id="home">
		<div class="header">
			<div class="mobile-images"></div>
		</div>

		<noscript>
			<div class="notification" id="noscript">
				<?php _e("Please enable JavaScript"); ?>
			</div>
		</noscript>

		<div class="notification" id="error">
			<?php _e("Error"); ?>
		</div>

		<div class="notification" id="info">
			<?php _e("Please wait..."); ?>
		</div>

		<div class="login">
			<?php _e("Login"); ?>

			<form action="#" method="post" onsubmit="return doLogin(this);">
				<input class="xid mobile-images" type="text" name="xid" required="" />
				<input class="password mobile-images" type="password" id="pwd" name="pwd" required="" />
				<?php if(REGISTRATION != 'off') { ?>
				<label><input class="register" type="checkbox" id="reg" name="reg" /><?php _e("Register"); ?></label>
				<?php } ?>
				<input type="submit" name="ok" value="<?php _e("Here we go!"); ?>" />
			</form>
		</div>

		<a href="./?m=desktop<?php echo keepGet('m', false); ?>"><?php _e("Desktop"); ?></a>
	</div>
</body>

</html>

<!-- Jappix Mobile <?php echo $version; ?> - An open social platform -->
