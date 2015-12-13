<?php
/**
 * BuddyPress - Groups Single Forum Edit.
 *
 * @package BuddyPress
 * @subpackage bp-legacy
 */

/**
 * Fires at the top of the group forum edit form.
 *
 * @since 1.2.4
 */
do_action( 'bp_before_group_forum_edit_form' ); ?>

<?php if ( bp_has_forum_topic_posts() ) : ?>

	<form action="<?php bp_forum_topic_action(); ?>" method="post" id="forum-topic-form" class="standard-form">

		<div class="item-list-tabs" id="subnav" role="navigation">
			<ul>
				<li>
					<a href="#post-topic-reply"><?php _e( 'Reply', 'buddypress' ); ?></a>
				</li>

				<?php if ( bp_forums_has_directory() ) : ?>

					<li>
						<a href="<?php bp_forums_directory_permalink(); ?>"><?php _e( 'Forum Directory', 'buddypress' ); ?></a>
					</li>

				<?php endif; ?>

			</ul>
		</div>

		<div id="topic-meta">
			<h3><?php _e( 'Edit:', 'buddypress' ); ?> <?php bp_the_topic_title(); ?> (<?php bp_the_topic_total_post_count(); ?>)</h3>

			<?php if ( bp_group_is_admin() || bp_group_is_mod() || bp_get_the_topic_is_mine() ) : ?>

				<div class="last admin-links">

					<?php bp_the_topic_admin_links(); ?>

				</div>

			<?php endif; ?>

			<?php

			/**
			 * Fires at the end of the group forum topic meta section.
			 *
			 * @since 1.2.5
			 */
			do_action( 'bp_group_forum_topic_meta' ); ?>

		</div>

		<?php if ( bp_is_edit_topic() ) : ?>

			<div id="edit-topic">

				<?php

				/**
				 * Fires before the group forum topic form fields.
				 *
				 * @since 1.2.0
				 */
				do_action( 'bp_group_before_edit_forum_topic' ); ?>

				<label for="topic_title"><?php _e( 'Title:', 'buddypress' ); ?></label>
				<input type="text" name="topic_title" id="topic_title" value="<?php bp_the_topic_title(); ?>" maxlength="100" />

				<label for="topic_text"><?php _e( 'Content:', 'buddypress' ); ?></label>
				<textarea name="topic_text" id="topic_text"><?php bp_the_topic_text(); ?></textarea>

				<label for="topic_tags"><?php _e( 'Tags (comma separated):', 'buddypress' ); ?></label>
				<input type="text" name="topic_tags" id="topic_tags" value="<?php bp_forum_topic_tag_list(); ?>" />

				<?php

				/**
				 * Fires after the group forum topic form fields.
				 *
				 * @since 1.2.0
				 */
				do_action( 'bp_group_after_edit_forum_topic' ); ?>

				<p class="submit"><input type="submit" name="save_changes" id="save_changes" value="<?php esc_attr_e( 'Save Changes', 'buddypress' ); ?>" /></p>

				<?php wp_nonce_field( 'bp_forums_edit_topic' ); ?>

			</div>

		<?php else : ?>

			<div id="edit-post">

				<?php

				/**
				 * Fires before the group edit forum textarea.
				 *
				 * @since 1.2.0
				 */
				do_action( 'bp_group_before_edit_forum_post' ); ?>

				<label for="post_text" class="bp-screen-reader-text"><?php _e( 'Edit text', 'buddypress' ); ?></label>
				<textarea name="post_text" id="post_text"><?php bp_the_topic_post_edit_text(); ?></textarea>

				<?php

				/**
				 * Fires after the group edit forum textarea.
				 *
				 * @since 1.2.0
				 */
				do_action( 'bp_group_after_edit_forum_post' ); ?>

				<p class="submit"><input type="submit" name="save_changes" id="save_changes" value="<?php esc_attr_e( 'Save Changes', 'buddypress' ); ?>" /></p>

				<?php wp_nonce_field( 'bp_forums_edit_post' ); ?>

			</div>

		<?php endif; ?>

	</form><!-- #forum-topic-form -->

<?php else: ?>

	<div id="message" class="info">
		<p><?php _e( 'This topic does not exist.', 'buddypress' ); ?></p>
	</div>

<?php endif;?>

<?php

/**
 * Fires at the end of the group forum edit form.
 *
 * @since 1.2.4
 */
do_action( 'bp_after_group_forum_edit_form' ); ?>
