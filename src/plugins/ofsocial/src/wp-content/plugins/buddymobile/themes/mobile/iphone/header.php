<!DOCTYPE html>

<html xmlns="http://www.w3.org/1999/xhtml" <?php language_attributes(); ?>>

	<head profile="http://gmpg.org/xfn/11">

		<link rel="apple-touch-icon-precomposed" href="<?php bm_touch_icon(); ?>" />
		<meta content="minimum-scale=1.0, width=device-width, maximum-scale=0.6667, user-scalable=no" name="viewport" />
		<meta name="apple-mobile-web-app-capable" content="yes">

		<title><?php bloginfo('name'); ?></title>

		<link rel="stylesheet" href="<?php bloginfo('stylesheet_url'); ?>" type="text/css" media="screen" />

		<?php do_action( 'bp_head' ) ?>


		<?php wp_head(); ?>

		<script type="text/javascript">
		
		addEventListener("load", function(){
        	setTimeout(function(){
                window.scrollTo(0, 1);
            }, 100);

    	}, false);

		</script>

	</head>

	<body <?php body_class() ?>>

		<div id="topbar">

			<div id="title"><?php bloginfo('name') ?></div>

			<div id="leftnav">
				<div id="menu"><?php _e( 'Menu', 'buddymobile' ) ?></div> 
			</div>

		<?php if ( is_front_page()) { ?>

			<?php if ( !is_user_logged_in() ) { ?>
				<div id="rightnav"><div id="login" href="#"><?php _e( 'Login', 'buddymobile' ) ?></div> </div>
			<?php } ?>

		<?php } ?>

		<?php if ( bp_is_my_profile() && !bp_is_single_activity() && bp_is_activity_component() || bp_is_activity_component() && !bp_is_single_activity() && !bp_is_user_activity()  || bp_is_group_single() && bp_is_group_home() ) : ?>

			<?php if ( is_user_logged_in() ) : ?>
				<div id="rightnav"><div id="status"><?php _e( 'Post', 'buddymobile' ) ?></div> </div>
			<?php endif ; ?>

		<?php endif ; ?>

		</div>
