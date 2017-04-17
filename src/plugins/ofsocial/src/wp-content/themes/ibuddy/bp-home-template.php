<?php
/*
Template Name: Home
 
 * BuddyPress home page template.
 *
 * This template can be selected when creating new page.
 *
 * @package iBuddy
 */
?>
<?php if(of_get_option('lock_down_home')) : ?>
	<?php if (is_user_logged_in()) : ?>
		<?php wp_redirect(bp_loggedin_user_domain()); exit; ?>
	<?php endif; ?>
<?php endif; ?>
<?php get_header(); ?>
<div id="primary" class="content-area">
		<div id="content" class="site-content" role="main">
	
			<?php include_once( ABSPATH . 'wp-admin/includes/plugin.php' ); ?>
				<?php if ( is_plugin_active('buddypress/bp-loader.php')) : ?>
					<?php get_template_part( 'home', 'content' ); ?>
			<?php else: ?>
				<header class="entry-header">
				<h1 class="entry-title" style="text-align: center;"><?php _e('Please Install and Activate BuddyPress','ibuddy'); ?></h1>
				</header>
			<?php endif; ?>
		</div><!-- #content -->
	</div><!-- #primary -->
<?php get_home_sidebar(); ?>
<?php get_footer(); ?>
