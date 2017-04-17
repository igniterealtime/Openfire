<?php

/**
 * BuddyMobile - footer template
 *
 * This template displays the footer contents.
 *
 * @package BuddyMobile
 *
 */

?>

	<div id="mobileNav" class="overthrow">
	<ul id="mobile-nav">
    <?php if ( is_user_logged_in() && function_exists( 'bp_is_active' ) ) : ?>

	    	<li><a href="<?php echo bp_loggedin_user_domain() ?>" title="<?php _e( 'Profile', 'buddymobile' ) ?>"><?php _e( 'Profile', 'buddymobile' ) ?></a></li>
		<?php if( current_user_can( 'publish_posts') ) : ?>
			<li><a href="<?php bloginfo('wpurl'); ?>/wp-admin"><?php _e( 'Admin', 'buddymobile' ) ?></a></li>
		<?php endif ; ?>

		<li><a href="<?php echo wp_logout_url( home_url() ); ?>"><?php _e( 'Log Out', 'buddymobile' ) ?></a></li>
	<? else : ?>
		<li class="menu-item"><a href="<?php bloginfo('wpurl'); ?>/wp-login.php"><?php _e( 'Log In', 'buddymobile' ) ?></a></li>

		<?php do_action( 'bp_after_sidebar_login_form' ) ?>

        </ul>

    <?php endif; ?>

        <ul><li class="sep"><?php _e( 'Main', 'buddymobile' ) ?></li></ul>
        <?php if ( has_nav_menu( 'mobile-menu' ) ) {
	wp_nav_menu( array( 'container' => false, 'menu_id' => 'mobile-nav', 'theme_location' => 'mobile-menu' ) );
} ?>


    </div>


    <div id="loginNav">
    		<?php if ( function_exists( 'bp_is_active' ) ) : ?>

			<div id="userInfo">

			<?php if ( is_user_logged_in() ) : ?>

			<a href="<?php echo bp_loggedin_user_domain() ?>">
				<?php bp_loggedin_user_avatar( 'type=thumb&width=40&height=40' ) ?>
			</a>

			<h2><?php echo bp_core_get_userlink( bp_loggedin_user_id() ); ?></h2>
			<a class="button logout" href="<?php echo wp_logout_url( bp_get_root_domain() ) ?>"><?php _e( 'Log Out', 'buddymobile' ) ?></a>


		<?php if ( function_exists( 'bp_message_get_notices' ) ) : ?>
			<?php bp_message_get_notices(); /* Site wide notices to all users */ ?>
		<?php endif; ?>


		<div id="notifications">
			<?php  //bp_notification_badge() ?>
		</div>

	<?php else : ?>

		<p id="login-text">
			<?php _e( 'To start connecting please log in first.', 'buddymobile' ) ?>
			<?php if ( bp_get_signup_allowed() ) : ?>
				<?php printf( __( ' You can also <a href="%s" title="Create an account">create an account</a>.', 'buddymobile' ), site_url( BP_REGISTER_SLUG . '/' ) ) ?>
			<?php endif; ?>
		</p>

		<form name="login-form" id="sidebar-login-form" class="standard-form" action="<?php echo site_url( 'wp-login.php', 'login_post' ) ?>" method="post">
			<label><?php _e( 'Username', 'buddymobile' ) ?><br />
			<input type="text" name="log" id="sidebar-user-login" class="input" value="<?php echo esc_attr(stripslashes($user_login)); ?>" tabindex="97" /></label>

			<label><?php _e( 'Password', 'buddymobile' ) ?><br />
			<input type="password" name="pwd" id="sidebar-user-pass" class="input" value="" tabindex="98" /></label>

			<p class="forgetmenot"><label><input name="rememberme" type="checkbox" id="sidebar-rememberme" value="forever" tabindex="99" /> <?php _e( 'Remember Me', 'buddymobile' ) ?></label></p>

			<?php do_action( 'bp_sidebar_login_form' ) ?>
			<input type="submit" name="wp-submit" id="sidebar-wp-submit" value="<?php _e('Log In'); ?>" tabindex="100" />
			<input type="hidden" name="testcookie" value="1" />
		</form>



	<?php endif; ?>

		</div>
<?php endif; ?>

		</div>

    <div id="footer">
        <p><a href="" id="theme-switch"><?php _e( 'View full site', 'buddymobile' ) ?></a></p>

        <?php do_action( 'bp_after_footer' )  ?>
    </div><!-- #footer -->

    <?php wp_footer(); ?>

</body>
</html>