<?php
/**
* BuddyPress - Activity Stream (Single Item)
*
* This template is used by activity-loop.php and AJAX functions to show
* each activity.
 *
 * @package Status
 * @since 1.0
 */
?>


<?php do_action( 'bp_before_activity_entry' ); ?>

<li class="<?php bp_activity_css_class(); ?> activity-entry" id="activity-<?php bp_activity_id(); ?>">
	<div class="activity-avatar">
		<a href="<?php bp_activity_user_link(); ?>">

			<?php bp_activity_avatar(); ?>

		</a>
	</div>

	<div class="activity-content">
		<div class="activity-details">
		<div class="activity-header">
				<?php bp_activity_action(); ?>
		</div>


				</div>
		<?php if ( 'activity_comment' == bp_get_activity_type() ) : ?>
			<div class="activity-inreplyto">
				<strong><?php _e( 'In reply to: ', 'status' ); ?></strong><?php bp_activity_parent_content(); ?> <a href="<?php bp_activity_thread_permalink(); ?>" class="view" title="<?php _e( 'View Thread / Permalink', 'status' ); ?>"><?php _e( 'View', 'status' ); ?></a>
			</div>
		<?php endif; ?>
		<?php if ( bp_activity_has_content() ) : ?>
			<div class="activity-inner">

				<?php bp_activity_content_body(); ?>

			</div>

		<?php endif; ?>

		<?php do_action( 'bp_activity_entry_content' ); ?>
	</div>
	<?php if ( is_user_logged_in() ) : ?>

		<div class="activity-meta">
			<?php if ( bp_activity_can_comment() ) : ?>

				<a href="<?php bp_get_activity_comment_link(); ?>" class="button acomment-reply bp-primary-action" id="acomment-comment-<?php bp_activity_id(); ?>" title="View comments and reply"><?php printf( __( 'Comment <span>%s</span>', 'status' ), bp_activity_get_comment_count() ); ?></a>

			 <?php 
			// Do_action moved to bring show links into correct position, hnla
			do_action( 'bp_activity_entry_meta' ); ?>
			<?php endif; ?>

			<?php if ( bp_activity_can_favorite() ) : ?>

				<?php if ( !bp_get_activity_is_favorite() ) : ?>

					<a href="<?php bp_activity_favorite_link(); ?>" class="button fav bp-secondary-action" title="<?php esc_attr_e( 'Mark as Favorite', 'status' ); ?>"><?php _e( 'Favorite', 'status' ) ?></a>

				<?php else : ?>

					<a href="<?php bp_activity_unfavorite_link(); ?>" class="button unfav bp-secondary-action" title="<?php esc_attr_e( 'Remove Favorite', 'status' ); ?>"><?php _e( 'Remove Favorite', 'status' ) ?></a>

				<?php endif; ?>

			<?php endif; ?>

			<?php if ( bp_activity_user_can_delete() ) bp_activity_delete_link(); ?>



		</div>

	<?php endif; ?>


	<?php do_action( 'bp_before_activity_entry_comments' ); ?>

	<?php if ( ( is_user_logged_in() && bp_activity_can_comment() ) || bp_activity_get_comment_count() ) : ?>

		<div class="activity-comments">

			<?php bp_activity_comments(); ?>

			<?php if ( is_user_logged_in() ) : ?>

				<form action="<?php bp_activity_comment_form_action(); ?>" method="post" id="ac-form-<?php bp_activity_id(); ?>" class="ac-form"<?php bp_activity_comment_form_nojs_display(); ?>>
					<div class="ac-reply-avatar"><?php bp_loggedin_user_avatar( 'width=' . BP_AVATAR_THUMB_WIDTH . '&height=' . BP_AVATAR_THUMB_HEIGHT ); ?></div>
					<div class="ac-reply-content">
						<div class="ac-textarea">
							<textarea id="ac-input-<?php bp_activity_id(); ?>" class="ac-input" name="ac_input_<?php bp_activity_id(); ?>"></textarea>
						</div>
						<input type="submit" name="ac_form_submit" value="<?php _e( 'Post', 'status' ); ?>" /> &nbsp; <?php _e( 'or press esc to cancel.', 'status' ); ?>
						<input type="hidden" name="comment_form_id" value="<?php bp_activity_id(); ?>" />
					</div>

					<?php do_action( 'bp_activity_entry_comments' ); ?>

					<?php wp_nonce_field( 'new_activity_comment', '_wpnonce_new_activity_comment' ); ?>

				</form>

			<?php endif; ?>

		</div>

	<?php endif; ?>

	<?php do_action( 'bp_after_activity_entry_comments' ); ?>

</li>

<?php do_action( 'bp_after_activity_entry' ); ?>
