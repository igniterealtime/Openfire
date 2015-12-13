<?php
/**
 * BuddyPress Messages Notifications.
 *
 * @package BuddyPress
 * @subpackage MessagesNotifications
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/** Email *********************************************************************/

/**
 * Email message recipients to alert them of a new unread private message.
 *
 * @since 1.0.0
 *
 * @param array|BP_Messages_Message $raw_args {
 *     Array of arguments. Also accepts a BP_Messages_Message object.
 *     @type array  $recipients    User IDs of recipients.
 *     @type string $email_subject Subject line of message.
 *     @type string $email_content Content of message.
 *     @type int    $sender_id     User ID of sender.
 * }
 */
function messages_notification_new_message( $raw_args = array() ) {

	// Cast possible $message object as an array.
	if ( is_object( $raw_args ) ) {
		$args = (array) $raw_args;
	} else {
		$args = $raw_args;
	}

	// These should be extracted below.
	$recipients    = array();
	$email_subject = $email_content = '';
	$sender_id     = 0;

	// Barf.
	extract( $args );

	// Get the sender display name.
	$sender_name = bp_core_get_user_displayname( $sender_id );

	// Bail if no recipients.
	if ( ! empty( $recipients ) ) {

		foreach ( $recipients as $recipient ) {

			if ( $sender_id == $recipient->user_id || 'no' == bp_get_user_meta( $recipient->user_id, 'notification_messages_new_message', true ) ) {
				continue;
			}

			// User data and links.
			$ud = get_userdata( $recipient->user_id );

			// Bail if user cannot be found.
			if ( empty( $ud ) ) {
				continue;
			}

			$message_link  = bp_core_get_user_domain( $recipient->user_id ) . bp_get_messages_slug() .'/';
			$settings_slug = function_exists( 'bp_get_settings_slug' ) ? bp_get_settings_slug() : 'settings';
			$settings_link = bp_core_get_user_domain( $recipient->user_id ) . $settings_slug . '/notifications/';

			// Sender info.
			$sender_name   = stripslashes( $sender_name );
			$subject       = stripslashes( wp_filter_kses( $subject ) );
			$content       = stripslashes( wp_filter_kses( $message ) );

			// Set up and send the message.
			$email_to      = $ud->user_email;
			$email_subject = bp_get_email_subject( array( 'text' => sprintf( __( 'New message from %s', 'buddypress' ), $sender_name ) ) );

			$email_content = sprintf( __(
'%1$s sent you a new message:

Subject: %2$s

"%3$s"

To view and read your messages please log in and visit: %4$s

---------------------
', 'buddypress' ), $sender_name, $subject, $content, $message_link );

			// Only show the disable notifications line if the settings component is enabled.
			if ( bp_is_active( 'settings' ) ) {
				$email_content .= sprintf( __( 'To disable these notifications, please log in and go to: %s', 'buddypress' ), $settings_link );
			}

			/**
			 * Filters the user email that the message notification will be sent to.
			 *
			 * @since 1.2.0
			 *
			 * @param string  $email_to User email the notification is being sent to.
			 * @param WP_User $ud       WP_User object of who is receiving the message.
			 */
			$email_to      = apply_filters( 'messages_notification_new_message_to',      $email_to, $ud );

			/**
			 * Filters the message notification subject that will be sent to user.
			 *
			 * @since 1.2.0
			 *
			 * @param string  $email_subject Email notification subject text.
			 * @param string  $sender_name   Name of the person who sent the message.
			 * @param WP_User $ud            WP_User object of who is receiving the message.
			 */
			$email_subject = apply_filters( 'messages_notification_new_message_subject', $email_subject, $sender_name, $ud );

			/**
			 * Filters the message notification message that will be sent to user.
			 *
			 * @since 1.2.0
			 *
			 * @param string  $email_content Email notification message text.
			 * @param string  $sender_name   Name of the person who sent the message.
			 * @param string  $subject       Email notification subject text.
			 * @param string  $content       Content of the message.
			 * @param string  $message_link  URL permalink for the message.
			 * @param string  $settings_link URL permalink for the user's notification settings area.
			 * @param WP_User $ud            WP_User object of who is receiving the message.
			 */
			$email_content = apply_filters( 'messages_notification_new_message_message', $email_content, $sender_name, $subject, $content, $message_link, $settings_link, $ud );

			wp_mail( $email_to, $email_subject, $email_content );
		}
	}

	/**
	 * Fires after the sending of a new message email notification.
	 *
	 * @since 1.5.0
	 *
	 * @param array  $recipients    User IDs of recipients.
	 * @param string $email_subject Email notification subject text.
	 * @param string $email_content Email notification message text.
	 * @param array  $$args         Array of originally provided arguments.
	 */
	do_action( 'bp_messages_sent_notification_email', $recipients, $email_subject, $email_content, $args );
}
add_action( 'messages_message_sent', 'messages_notification_new_message', 10 );

/** Notifications *************************************************************/

/**
 * Format notifications for the Messages component.
 *
 * @since 1.0.0
 *
 * @param string $action            The kind of notification being rendered.
 * @param int    $item_id           The primary item id.
 * @param int    $secondary_item_id The secondary item id.
 * @param int    $total_items       The total number of messaging-related notifications
 *                                  waiting for the user.
 * @param string $format            Return value format. 'string' for BuddyBar-compatible
 *                                  notifications; 'array' for WP Toolbar. Default: 'string'.
 * @return string|array Formatted notifications.
 */
function messages_format_notifications( $action, $item_id, $secondary_item_id, $total_items, $format = 'string' ) {
	$total_items = (int) $total_items;
	$link        = trailingslashit( bp_loggedin_user_domain() . bp_get_messages_slug() . '/inbox' );
	$title       = __( 'Inbox', 'buddypress' );
	$amount      = 'single';

	if ( 'new_message' === $action ) {
		if ( $total_items > 1 ) {
			$amount = 'multiple';
			$text   = sprintf( __( 'You have %d new messages', 'buddypress' ), $total_items );
		} else {
			$amount = 'single';

			// Get message thread ID.
			$message   = new BP_Messages_Message( $item_id );
			$thread_id = $message->thread_id;
			$link      = ( ! empty( $thread_id ) )
				? bp_get_message_thread_view_link( $thread_id )
				: false;

			if ( ! empty( $secondary_item_id ) ) {
				$text = sprintf( __( '%s sent you a new private message', 'buddypress' ), bp_core_get_user_displayname( $secondary_item_id ) );
			} else {
				$text = sprintf( _n( 'You have %s new private message', 'You have %s new private messages', $total_items, 'buddypress' ), bp_core_number_format( $total_items ) );
			}
		}
	}

	if ( 'string' === $format ) {
		if ( ! empty( $link ) ) {
			$retval = '<a href="' . esc_url( $link ) . '" title="' . esc_attr( $title ) . '">' . esc_html( $text ) . '</a>';
		} else {
			$retval = esc_html( $text );
		}

		/**
		 * Filters the new message notification text before the notification is created.
		 *
		 * This is a dynamic filter. Possible filter names are:
		 *   - 'bp_messages_multiple_new_message_notification'.
		 *   - 'bp_messages_single_new_message_notification'.
		 *
		 * @param string $retval            Notification text.
		 * @param int    $total_items       Number of messages referred to by the notification.
		 * @param string $text              The raw notification test (ie, not wrapped in a link).
		 * @param int    $item_id           ID of the associated item.
		 * @param int    $secondary_item_id ID of the secondary associated item.
		 */
		$return = apply_filters( 'bp_messages_' . $amount . '_new_message_notification', $retval, (int) $total_items, $text, $link, $item_id, $secondary_item_id );
	} else {
		/** This filter is documented in bp-messages/bp-messages-notifications.php */
		$return = apply_filters( 'bp_messages_' . $amount . '_new_message_notification', array(
			'text' => $text,
			'link' => $link
		), $link, (int) $total_items, $text, $link, $item_id, $secondary_item_id );
	}

	/**
	 * Fires right before returning the formatted message notifications.
	 *
	 * @since 1.0.0
	 *
	 * @param string $action            The type of message notification.
	 * @param int    $item_id           The primary item ID.
	 * @param int    $secondary_item_id The secondary item ID.
	 * @param int    $total_items       Total amount of items to format.
	 */
	do_action( 'messages_format_notifications', $action, $item_id, $secondary_item_id, $total_items );

	return $return;
}

/**
 * Send notifications to message recipients.
 *
 * @since 1.9.0
 *
 * @param BP_Messages_Message $message Message object.
 */
function bp_messages_message_sent_add_notification( $message ) {
	if ( bp_is_active( 'notifications' ) && ! empty( $message->recipients ) ) {
		foreach ( (array) $message->recipients as $recipient ) {
			bp_notifications_add_notification( array(
				'user_id'           => $recipient->user_id,
				'item_id'           => $message->id,
				'secondary_item_id' => $message->sender_id,
				'component_name'    => buddypress()->messages->id,
				'component_action'  => 'new_message',
				'date_notified'     => bp_core_current_time(),
				'is_new'            => 1,
			) );
		}
	}
}
add_action( 'messages_message_sent', 'bp_messages_message_sent_add_notification', 10 );

/**
 * Mark new message notification when member reads a message thread directly.
 *
 * @since 1.9.0
 */
function bp_messages_screen_conversation_mark_notifications() {
	if ( bp_is_active( 'notifications' ) ) {
		global $thread_template;

		// Get unread PM notifications for the user.
		$new_pm_notifications = BP_Notifications_Notification::get( array(
			'user_id'           => bp_loggedin_user_id(),
			'component_name'    => buddypress()->messages->id,
			'component_action'  => 'new_message',
			'is_new'            => 1,
		) );
		$unread_message_ids = wp_list_pluck( $new_pm_notifications, 'item_id' );

		// No unread PMs, so stop!
		if ( empty( $unread_message_ids ) ) {
			return;
		}

		// Get the unread message ids for this thread only.
		$message_ids = array_intersect( $unread_message_ids, wp_list_pluck( $thread_template->thread->messages, 'id' ) );

		// Mark each notification for each PM message as read.
		foreach ( $message_ids as $message_id ) {
			bp_notifications_mark_notifications_by_item_id( bp_loggedin_user_id(), (int) $message_id, buddypress()->messages->id, 'new_message' );
		}
	}
}
add_action( 'thread_loop_start', 'bp_messages_screen_conversation_mark_notifications', 10 );

/**
 * When a message is deleted, delete corresponding notifications.
 *
 * @since 2.0.0
 *
 * @param int   $thread_id   ID of the thread.
 * @param array $message_ids IDs of the messages.
 */
function bp_messages_message_delete_notifications( $thread_id, $message_ids ) {
	if ( ! bp_is_active( 'notifications' ) ) {
		return;
	}

	// For each recipient, delete notifications corresponding to each message.
	$thread = new BP_Messages_Thread( $thread_id );
	foreach ( $thread->get_recipients() as $recipient ) {
		foreach ( $message_ids as $message_id ) {
			bp_notifications_delete_notifications_by_item_id( $recipient->user_id, (int) $message_id, buddypress()->messages->id, 'new_message' );
		}
	}
}
add_action( 'bp_messages_thread_after_delete', 'bp_messages_message_delete_notifications', 10, 2 );
