<?php
/**
 * BuddyPress - Private Message Content.
 *
 * This template is used in /messages/single.php during the message loop to
 * display each message and when a new message is created via AJAX.
 *
 * @since 2.4.0
 *
 * @package BuddyPress
 * @subpackage bp-legacy
 */

?>

			<div class="message-box <?php bp_the_thread_message_css_class(); ?>">

				<div class="message-metadata">

					<?php

					/**
					 * Fires before the single message header is displayed.
					 *
					 * @since 1.1.0
					 */
					do_action( 'bp_before_message_meta' ); ?>

					<?php bp_the_thread_message_sender_avatar( 'type=thumb&width=30&height=30' ); ?>

					<?php if ( bp_get_the_thread_message_sender_link() ) : ?>

						<strong><a href="<?php bp_the_thread_message_sender_link(); ?>" title="<?php bp_the_thread_message_sender_name(); ?>"><?php bp_the_thread_message_sender_name(); ?></a></strong>

					<?php else : ?>

						<strong><?php bp_the_thread_message_sender_name(); ?></strong>

					<?php endif; ?>

					<span class="activity"><?php bp_the_thread_message_time_since(); ?></span>

					<?php if ( bp_is_active( 'messages', 'star' ) ) : ?>
						<div class="message-star-actions">
							<?php bp_the_message_star_action_link(); ?>
						</div>
					<?php endif; ?>

					<?php

					/**
					 * Fires after the single message header is displayed.
					 *
					 * @since 1.1.0
					 */
					do_action( 'bp_after_message_meta' ); ?>

				</div><!-- .message-metadata -->

				<?php

				/**
				 * Fires before the message content for a private message.
				 *
				 * @since 1.1.0
				 */
				do_action( 'bp_before_message_content' ); ?>

				<div class="message-content">

					<?php bp_the_thread_message_content(); ?>

				</div><!-- .message-content -->

				<?php

				/**
				 * Fires after the message content for a private message.
				 *
				 * @since 1.1.0
				 */
				do_action( 'bp_after_message_content' ); ?>

				<div class="clear"></div>

			</div><!-- .message-box -->
