<?php if ( bp_album_picture_has_activity() && bp_album_comments_enabled() ) : ?>

		<ul id="activity-stream" class="activity-list item-list">

	<?php while ( bp_activities() ) : bp_the_activity(); ?>

		<li class="<?php bp_activity_css_class(); ?>" id="activity-<?php bp_activity_id(); ?>">

			<div class="activity-content">
				<div class="activity-meta">
            <?php do_action( 'bp_media_before_activity_meta' ); ?>
					<?php if ( is_user_logged_in() && bp_activity_can_comment() ) : ?>
						<a href="<?php bp_activity_comment_link(); ?>" class="acomment-reply" id="acomment-comment-<?php bp_activity_id(); ?>"><?php _e( 'Reply', 'buddypress' ) ?> (<span><?php bp_activity_comment_count(); ?></span>)</a>
					<?php endif; ?>

					<?php if ( is_user_logged_in() ) : ?>
						<?php if ( !bp_get_activity_is_favorite() ) : ?>
							<a href="<?php bp_activity_favorite_link(); ?>" class="fav" title="<?php _e( 'Mark as Favorite', 'buddypress' ) ?>"><?php _e( 'Favorite', 'buddypress' ) ?></a>
						<?php else : ?>
							<a href="<?php bp_activity_unfavorite_link(); ?>" class="unfav" title="<?php _e( 'Remove Favorite', 'buddypress' ) ?>"><?php _e( 'Remove Favorite', 'buddypress' ) ?></a>
						<?php endif; ?>
					<?php endif;?>
               <?php do_action( 'bp_media_after_activity_meta' ); ?>
				</div>
			</div>

			<?php if ( 'activity_comment' == bp_get_activity_type() ) : ?>
				<div class="activity-inreplyto">
					<strong><?php _e( 'In reply to', 'buddypress' ) ?></strong> - <?php bp_activity_parent_content(); ?> &middot;
					<a href="<?php bp_activity_thread_permalink(); ?>" class="view" title="<?php _e( 'View Thread / Permalink', 'buddypress' ) ?>"><?php _e( 'View', 'buddypress' ) ?></a>
				</div>
			<?php endif; ?>

			<?php if ( bp_activity_can_comment() ) : ?>
				<div class="activity-comments">
					<?php bp_activity_comments(); ?>

					<?php if ( is_user_logged_in() ) : ?>
					<form action="<?php bp_activity_comment_form_action(); ?>" method="post" id="ac-form-<?php bp_activity_id() ?>" class="ac-form"<?php bp_activity_comment_form_nojs_display(); ?>>
						<div class="ac-reply-content">
							<div class="ac-textarea">
								<textarea id="ac-input-<?php bp_activity_id(); ?>" class="ac-input" name="ac_input_<?php bp_activity_id(); ?>"></textarea>
							</div>
							<input type="submit" name="ac_form_submit" value="<?php _e( 'Post', 'buddypress' ) ?> &rarr;" /> &nbsp; <?php _e( 'or press esc to cancel.', 'buddypress' ) ?>
							<input type="hidden" name="comment_form_id" value="<?php bp_activity_id(); ?>" />
						</div>
						<?php wp_nonce_field( 'new_activity_comment', '_wpnonce_new_activity_comment' ) ?>
					</form>
					<?php endif; ?>
				</div>
			<?php endif; ?>
		</li>

	<?php endwhile; ?>

		</ul>

<?php else : if(bp_is_my_profile() && bp_album_comments_enabled()){ ?>
	<div id="message" class="info">
		<p><?php echo sprintf(__( 'Comments are disabled for this picture. %sEdit the picture%s to enable them.', 'bp-album' ),'<a href="'.bp_album_get_picture_edit_url().'">','</a>'); ?></p>
	</div>

<?php } endif; ?>

<?php do_action( 'bp_after_activity_loop' ) ?>

<form action="" name="activity-loop-form" id="activity-loop-form" method="post">
	<?php wp_nonce_field( 'activity_filter', '_wpnonce_activity_filter' ); ?>
</form>