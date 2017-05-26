<?php
/**
 * BuddyPress - Members Single Message
 *
 * @package BuddyPress
 * @subpackage bp-legacy
 */

?>
<div id="message-thread">

	<?php

	/**
	 * Fires before the display of a single member message thread content.
	 *
	 * @since 1.1.0
	 */
	do_action( 'bp_before_message_thread_content' ); ?>

	<?php if ( bp_thread_has_messages() ) : ?>

		<h3 id="message-subject"><?php bp_the_thread_subject(); ?></h3>

		<p id="message-recipients">
			<span class="highlight">

				<?php if ( bp_get_thread_recipients_count() <= 1 ) : ?>

					<?php _e( 'You are alone in this conversation.', 'buddypress' ); ?>

				<?php elseif ( bp_get_max_thread_recipients_to_list() <= bp_get_thread_recipients_count() ) : ?>

					<?php printf( __( 'Conversation between %s recipients.', 'buddypress' ), number_format_i18n( bp_get_thread_recipients_count() ) ); ?>

				<?php else : ?>

					<?php printf( __( 'Conversation between %s and you.', 'buddypress' ), bp_get_thread_recipients_list() ); ?>

				<?php endif; ?>

			</span>

			<a class="button confirm" href="<?php bp_the_thread_delete_link(); ?>" title="<?php esc_attr_e( "Delete Conversation", "buddypress" ); ?>"><?php _e( 'Delete', 'buddypress' ); ?></a>
		</p>

		<?php

		/**
		 * Fires before the display of the message thread list.
		 *
		 * @since 1.1.0
		 */
		do_action( 'bp_before_message_thread_list' ); ?>

		<?php while ( bp_thread_messages() ) : bp_thread_the_message(); ?>
			<?php bp_get_template_part( 'members/single/messages/message' ); ?>
		<?php endwhile; ?>

		<?php

		/**
		 * Fires after the display of the message thread list.
		 *
		 * @since 1.1.0
		 */
		do_action( 'bp_after_message_thread_list' ); ?>

		<?php

		/**
		 * Fires before the display of the message thread reply form.
		 *
		 * @since 1.1.0
		 */
		do_action( 'bp_before_message_thread_reply' ); ?>

		<form id="send-reply" action="<?php bp_messages_form_action(); ?>" method="post" class="standard-form">

			<div class="message-box">

				<div class="message-metadata">

					<?php

					/** This action is documented in bp-templates/bp-legacy/buddypress-functions.php */
					do_action( 'bp_before_message_meta' ); ?>

					<div class="avatar-box">
						<?php bp_loggedin_user_avatar( 'type=thumb&height=30&width=30' ); ?>

						<strong><?php _e( 'Send a Reply', 'buddypress' ); ?></strong>
					</div>

					<?php

					/** This action is documented in bp-templates/bp-legacy/buddypress-functions.php */
					do_action( 'bp_after_message_meta' ); ?>

				</div><!-- .message-metadata -->

				<div class="message-content">

					<?php

					/**
					 * Fires before the display of the message reply box.
					 *
					 * @since 1.1.0
					 */
					do_action( 'bp_before_message_reply_box' ); ?>

					<label for="message_content" class="bp-screen-reader-text"><?php _e( 'Reply to Message', 'buddypress' ); ?></label>
					<textarea name="content" id="message_content" rows="15" cols="40"></textarea>

					<?php

					/**
					 * Fires after the display of the message reply box.
					 *
					 * @since 1.1.0
					 */
					do_action( 'bp_after_message_reply_box' ); ?>

					<div class="submit">
						<input type="submit" name="send" value="<?php esc_attr_e( 'Send Reply', 'buddypress' ); ?>" id="send_reply_button"/>
					</div>

					<input type="hidden" id="thread_id" name="thread_id" value="<?php bp_the_thread_id(); ?>" />
					<input type="hidden" id="messages_order" name="messages_order" value="<?php bp_thread_messages_order(); ?>" />
					<?php wp_nonce_field( 'messages_send_message', 'send_message_nonce' ); ?>

				</div><!-- .message-content -->

			</div><!-- .message-box -->

		</form><!-- #send-reply -->

		<?php

		/**
		 * Fires after the display of the message thread reply form.
		 *
		 * @since 1.1.0
		 */
		do_action( 'bp_after_message_thread_reply' ); ?>

	<?php endif; ?>

	<?php

	/**
	 * Fires after the display of a single member message thread content.
	 *
	 * @since 1.1.0
	 */
	do_action( 'bp_after_message_thread_content' ); ?>

</div>
