<?php

/*

Jappix - An open social platform
This is the Jappix Desktop PHP/HTML code

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
	<title><?php echo htmlspecialchars(SERVICE_NAME); ?> &bull; <?php _e("An open social network"); ?></title>
	<link rel="shortcut icon" href="./favicon.ico" />

	<?php echoGetFiles($hash, '', 'css', 'desktop.xml', ''); echo "\n"; ?>
	<!--[if lt IE 9]><?php echoGetFiles($hash, '', 'css', '', 'ie.css'); ?><![endif]-->

	<?php echoGetFiles($hash, $locale, 'js', 'desktop.xml', ''); echo "\n";

	if(anonymousMode()) {
		echo "\n\t";
		echoGetFiles($hash, '', 'css', 'anonymous.xml', '');
		echo "\n\t";
		echoGetFiles($hash, $locale, 'js', 'anonymous.xml', '');
		echo "\n";
	} ?>
</head>

<body class="body-images">

<?php

// Homepage?
if(!anonymousMode()) { ?>
	<!-- BEGIN HOMEPAGE -->
	<div id="home">
		<div class="main">
			<div class="left">
				<div class="home-images logo"></div>
				<p class="upper"><?php _e("Communicate with the entire world!"); ?></p>
				<p class="secondary"><?php _e("Jappix is a great social platform, that you can access wherever you are, whenever you want and communicate with whovever you want."); ?></p>
				<p class="secondary"><?php _e("It allows you to get in touch with the millions of users who currently use the XMPP network like you do with Jappix. Join the community and stay free!"); ?></p>
			</div>

			<div class="right">
				<h1 class="top default"><?php _e("Hi there!"); ?></h1>

				<div class="default homediv">
					<p><?php printf(T_("Welcome on %1s, “%2s”."), htmlspecialchars(SERVICE_NAME), htmlspecialchars(SERVICE_DESC)); ?></p>

					<p><?php _e("Login to your existing XMPP account or create a new one for free!"); ?></p>

					<button class="login buttons-images">
						<span class="home-images"></span>
						<span class="text"><?php _e("Login"); ?></span>
					</button>

					<button class="register buttons-images">
						<span class="home-images"></span>
						<span class="text"><?php _e("Register"); ?></span>
					</button>

					<p class="notice"><?php echo str_replace("PostPro", "<a href='http://www.post-pro.fr/'>PostPro</a>", T_("Jappix is an open-source project from PostPro, a non-profit organization which provides us a great help.")); ?></p>
				</div>

				<div class="navigation">
					<?php

						// Keep get var
						$keep_get = keepGet('m', false);

					?>
					<a class="home-images mobile" href="./?m=mobile<?php echo $keep_get; ?>"><?php _e("Mobile"); ?></a>
					<?php if(showManagerLink()) { ?>
					<a class="home-images manager" href="./?m=manager<?php echo $keep_get; ?>"><?php _e("Manager"); ?></a>
					<?php } ?>
					<a class="home-images project" href="https://project.jappix.com/"><?php _e("Project"); ?></a>
					<?php if(sslCheck() && !httpsForce()) echo sslLink(); ?>
				</div>
			</div>
		</div>

		<div class="locale" data-keepget="<?php echo(keepGet('l', false)); ?>">
			<div class="current"><?php echo(getLanguageName($locale)); ?></div>
		</div>

		<?php

			// Add the notice
			$conf_notice = readNotice();
			$type_notice = $conf_notice['type'];
			$text_notice = $conf_notice['notice'];

			// Simple notice
			if(($type_notice == 'simple') || ($type_notice == 'advanced')) {
				// We must encode special HTML characters
				if($type_notice == 'simple')
					$text_notice = '<span class="title home-images">'.T_("Notice").'</span><span class="text">'.htmlentities($text_notice).'</span>';

				// Echo the notice
				echo('<div class="notice '.$type_notice.'">'.$text_notice.'</div>');
			}

		?>
	</div>
	<!-- END HOMEPAGE -->
<?php } ?>

<!-- BEGIN BOARD -->
<div id="board">
	<noscript class="one-board info visible"><?php _e("JavaScript is missing in your web browser, so that you will not be able to launch Jappix! Please fix this."); ?></noscript>
</div>
<!-- END BOARD -->

</body>

</html>

<!-- Jappix <?php echo $version; ?> - An open social platform -->
