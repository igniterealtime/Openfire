<?php
/**
 * Sidebar
 *
 * @package Status
 * @since 1.0
 */
?>

<?php do_action( 'bp_before_sidebar' ) ?>
<adiv id="sidebar" class="secondary widget-area" role="complementary">
	<?php do_action( 'bp_inside_before_sidebar' ) ?>
	<?php if ( is_user_logged_in() ) : ?>
		<?php do_action( 'bp_before_sidebar_me' ) ?>
		<div id="sidebar-me">
			
			<?php if( bp_is_active( 'friends' ) ) : ?>
			<aside id="friends-loop">
				<div class="widget">
					<h3 class="widgettitle"><?php _e( 'Your Friends', 'status' ) ?></h3>
					<?php status_showfriends();?>
				</div>
			</aside>
			<?php endif; ?>
			
			<?php do_action( 'bp_sidebar_me' ) ?>
			<?php if ( bp_is_active( 'messages' ) ) : ?>
				<?php bp_message_get_notices(); /* Site wide notices to all users */ ?>
			<?php endif; ?>
		</div>
		<?php do_action( 'bp_after_sidebar_me' ) ?>
	<?php endif; ?>
	<?php /* Show forum tags on the forums directory */
	if ( bp_is_active( 'forums' ) && bp_is_forums_component() && bp_is_directory() ) : ?>
		<aside id="forum-directory-tags" class="widget tags">
			<h3 class="widgettitle"><?php _e( 'Forum Topic Tags', 'status' ) ?></h3>
			<div id="tag-text"><?php bp_forums_tag_heat_map(); ?></div>
		</aside>
	<?php endif; ?>

	<?php dynamic_sidebar( 'sidebar-buddypress' ) ?>

	<?php do_action( 'bp_inside_after_sidebar' ) ?>
</aside>
<?php do_action( 'bp_after_sidebar' ) ?>
