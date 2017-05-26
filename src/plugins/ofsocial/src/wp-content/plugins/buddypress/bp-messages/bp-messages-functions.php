<?php
/**
 * BuddyPress Messages Functions.
 *
 * Business functions are where all the magic happens in BuddyPress. They will
 * handle the actual saving or manipulation of information. Usually they will
 * hand off to a database class for data access, then return
 * true or false on success or failure.
 *
 * @package BuddyPress
 * @subpackage MessagesFunctions
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Create a new message.
 *
 * @since 2.4.0 Added 'error_type' as an additional $args parameter.
 *
 * @param array|string $args {
 *     Array of arguments.
 *     @type int    $sender_id  Optional. ID of the user who is sending the
 *                              message. Default: ID of the logged-in user.
 *     @type int    $thread_id  Optional. ID of the parent thread. Leave blank to
 *                              create a new thread for the message.
 *     @type array  $recipients IDs or usernames of message recipients. If this
 *                              is an existing thread, it is unnecessary to pass a $recipients
 *                              argument - existing thread recipients will be assumed.
 *     @type string $subject    Optional. Subject line for the message. For
 *                              existing threads, the existing subject will be used. For new
 *                              threads, 'No Subject' will be used if no $subject is provided.
 *     @type string $content    Content of the message. Cannot be empty.
 *     @type string $date_sent  Date sent, in 'Y-m-d H:i:s' format. Default: current date/time.
 *     @type string $error_type Optional. Error type. Either 'bool' or 'wp_error'. Default: 'bool'.
 * }
 * @return int|bool ID of the message thread on success, false on failure.
 */
function messages_new_message( $args = '' ) {

	// Parse the default arguments.
	$r = bp_parse_args( $args, array(
		'sender_id'  => bp_loggedin_user_id(),
		'thread_id'  => false,   // False for a new message, thread id for a reply to a thread.
		'recipients' => array(), // Can be an array of usernames, user_ids or mixed.
		'subject'    => false,
		'content'    => false,
		'date_sent'  => bp_core_current_time(),
		'error_type' => 'bool'
	), 'messages_new_message' );

	// Bail if no sender or no content.
	if ( empty( $r['sender_id'] ) || empty( $r['content'] ) ) {
		if ( 'wp_error' === $r['error_type'] ) {
			if ( empty( $r['sender_id'] ) ) {
				$error_code = 'messages_empty_sender';
				$feedback = __( 'Your message was not sent. Please use a valid sender.', 'buddypress' );
			} else {
				$error_code = 'messages_empty_content';
				$feedback = __( 'Your message was not sent. Please enter some content.', 'buddypress' );
			}

			return new WP_Error( $error_code, $feedback );

		} else {
			return false;
		}
	}

	// Create a new message object.
	$message            = new BP_Messages_Message;
	$message->thread_id = $r['thread_id'];
	$message->sender_id = $r['sender_id'];
	$message->subject   = $r['subject'];
	$message->message   = $r['content'];
	$message->date_sent = $r['date_sent'];

	// If we have a thread ID...
	if ( ! empty( $r['thread_id'] ) ) {

		// ...use the existing recipients
		$thread              = new BP_Messages_Thread( $r['thread_id'] );
		$message->recipients = $thread->get_recipients();

		// Strip the sender from the recipient list, and unset them if they are
		// not alone. If they are alone, let them talk to themselves.
		if ( isset( $message->recipients[ $r['sender_id'] ] ) && ( count( $message->recipients ) > 1 ) ) {
			unset( $message->recipients[ $r['sender_id'] ] );
		}

		// Set a default reply subject if none was sent.
		if ( empty( $message->subject ) ) {
			$message->subject = sprintf( __( 'Re: %s', 'buddypress' ), $thread->messages[0]->subject );
		}

	// ...otherwise use the recipients passed
	} else {

		// Bail if no recipients.
		if ( empty( $r['recipients'] ) ) {
			if ( 'wp_error' === $r['error_type'] ) {
				return new WP_Error( 'message_empty_recipients', __( 'Message could not be sent. Please enter a recipient.', 'buddypress' ) );
			} else {
				return false;
			}
		}

		// Set a default subject if none exists.
		if ( empty( $message->subject ) ) {
			$message->subject = __( 'No Subject', 'buddypress' );
		}

		// Setup the recipients array.
		$recipient_ids 	    = array();

		// Invalid recipients are added to an array, for future enhancements.
		$invalid_recipients = array();

		// Loop the recipients and convert all usernames to user_ids where needed.
		foreach( (array) $r['recipients'] as $recipient ) {

			// Trim spaces and skip if empty.
			$recipient = trim( $recipient );
			if ( empty( $recipient ) ) {
				continue;
			}

			// Check user_login / nicename columns first
			// @see http://buddypress.trac.wordpress.org/ticket/5151.
			if ( bp_is_username_compatibility_mode() ) {
				$recipient_id = bp_core_get_userid( urldecode( $recipient ) );
			} else {
				$recipient_id = bp_core_get_userid_from_nicename( $recipient );
			}

			// Check against user ID column if no match and if passed recipient is numeric.
			if ( empty( $recipient_id ) && is_numeric( $recipient ) ) {
				if ( bp_core_get_core_userdata( (int) $recipient ) ) {
					$recipient_id = (int) $recipient;
				}
			}

			// Decide which group to add this recipient to.
			if ( empty( $recipient_id ) ) {
				$invalid_recipients[] = $recipient;
			} else {
				$recipient_ids[] = (int) $recipient_id;
			}
		}

		// Strip the sender from the recipient list, and unset them if they are
		// not alone. If they are alone, let them talk to themselves.
		$self_send = array_search( $r['sender_id'], $recipient_ids );
		if ( ! empty( $self_send ) && ( count( $recipient_ids ) > 1 ) ) {
			unset( $recipient_ids[ $self_send ] );
		}

		// Remove duplicates & bail if no recipients.
		$recipient_ids = array_unique( $recipient_ids );
		if ( empty( $recipient_ids ) ) {
			if ( 'wp_error' === $r['error_type'] ) {
				return new WP_Error( 'message_invalid_recipients', __( 'Message could not be sent because you have entered an invalid username. Please try again.', 'buddypress' ) );
			} else {
				return false;
			}
		}

		// Format this to match existing recipients.
		foreach( (array) $recipient_ids as $i => $recipient_id ) {
			$message->recipients[$i]          = new stdClass;
			$message->recipients[$i]->user_id = $recipient_id;
		}
	}

	// Bail if message failed to send.
	if ( ! $message->send() ) {
		return false;
	}

	/**
	 * Fires after a message has been successfully sent.
	 *
	 * @since 1.1.0
	 *
	 * @param BP_Messages_Message $message Message object. Passed by reference.
	 */
	do_action_ref_array( 'messages_message_sent', array( &$message ) );

	// Return the thread ID.
	return $message->thread_id;
}

/**
 * Send a notice.
 *
 * @param string $subject Subject of the notice.
 * @param string $message Content of the notice.
 * @return bool True on success, false on failure.
 */
function messages_send_notice( $subject, $message ) {
	if ( !bp_current_user_can( 'bp_moderate' ) || empty( $subject ) || empty( $message ) ) {
		return false;

	// Has access to send notices, lets do it.
	} else {
		$notice            = new BP_Messages_Notice;
		$notice->subject   = $subject;
		$notice->message   = $message;
		$notice->date_sent = bp_core_current_time();
		$notice->is_active = 1;
		$notice->save(); // Send it.

		/**
		 * Fires after a notice has been successfully sent.
		 *
		 * @since 1.0.0
		 *
		 * @param string $subject Subject of the notice.
		 * @param string $message Content of the notice.
		 */
		do_action_ref_array( 'messages_send_notice', array( $subject, $message ) );

		return true;
	}
}

/**
 * Delete message thread(s).
 *
 * @param int|array $thread_ids Thread ID or array of thread IDs.
 * @return bool True on success, false on failure.
 */
function messages_delete_thread( $thread_ids ) {

	/**
	 * Fires before specified thread IDs have been deleted.
	 *
	 * @since 1.5.0
	 *
	 * @param int|array Thread ID or array of thread IDs that were deleted.
	 */
	do_action( 'messages_before_delete_thread', $thread_ids );

	if ( is_array( $thread_ids ) ) {
		$error = 0;
		for ( $i = 0, $count = count( $thread_ids ); $i < $count; ++$i ) {
			if ( ! BP_Messages_Thread::delete( $thread_ids[$i] ) ) {
				$error = 1;
			}
		}

		if ( ! empty( $error ) ) {
			return false;
		}

		/**
		 * Fires after specified thread IDs have been deleted.
		 *
		 * @since 1.0.0
		 *
		 * @param int|array Thread ID or array of thread IDs that were deleted.
		 */
		do_action( 'messages_delete_thread', $thread_ids );

		return true;
	} else {
		if ( ! BP_Messages_Thread::delete( $thread_ids ) ) {
			return false;
		}

		/** This action is documented in bp-messages/bp-messages-functions.php */
		do_action( 'messages_delete_thread', $thread_ids );

		return true;
	}
}

/**
 * Check whether a user has access to a thread.
 *
 * @param int $thread_id ID of the thread.
 * @param int $user_id   Optional. ID of the user. Default: ID of the logged-in user.
 * @return int|null Message ID if the user has access, otherwise null.
 */
function messages_check_thread_access( $thread_id, $user_id = 0 ) {
	if ( empty( $user_id ) ) {
		$user_id = bp_loggedin_user_id();
	}

	return BP_Messages_Thread::check_access( $thread_id, $user_id );
}

/**
 * Mark a thread as read.
 *
 * Wrapper for {@link BP_Messages_Thread::mark_as_read()}.
 *
 * @param int $thread_id ID of the thread.
 */
function messages_mark_thread_read( $thread_id ) {
	return BP_Messages_Thread::mark_as_read( $thread_id );
}

/**
 * Mark a thread as unread.
 *
 * Wrapper for {@link BP_Messages_Thread::mark_as_unread()}.
 *
 * @param int $thread_id ID of the thread.
 */
function messages_mark_thread_unread( $thread_id ) {
	return BP_Messages_Thread::mark_as_unread( $thread_id );
}

/**
 * Set messages-related cookies.
 *
 * Saves the 'bp_messages_send_to', 'bp_messages_subject', and
 * 'bp_messages_content' cookies, which are used when setting up the default
 * values on the messages page.
 *
 * @param string $recipients Comma-separated list of recipient usernames.
 * @param string $subject    Subject of the message.
 * @param string $content    Content of the message.
 */
function messages_add_callback_values( $recipients, $subject, $content ) {
	@setcookie( 'bp_messages_send_to', $recipients, time() + 60 * 60 * 24, COOKIEPATH );
	@setcookie( 'bp_messages_subject', $subject,    time() + 60 * 60 * 24, COOKIEPATH );
	@setcookie( 'bp_messages_content', $content,    time() + 60 * 60 * 24, COOKIEPATH );
}

/**
 * Unset messages-related cookies.
 *
 * @see messages_add_callback_values()
 */
function messages_remove_callback_values() {
	@setcookie( 'bp_messages_send_to', false, time() - 1000, COOKIEPATH );
	@setcookie( 'bp_messages_subject', false, time() - 1000, COOKIEPATH );
	@setcookie( 'bp_messages_content', false, time() - 1000, COOKIEPATH );
}

/**
 * Get the unread messages count for a user.
 *
 * @param int $user_id Optional. ID of the user. Default: ID of the logged-in user.
 * @return int
 */
function messages_get_unread_count( $user_id = 0 ) {
	if ( empty( $user_id ) ) {
		$user_id = bp_loggedin_user_id();
	}

	return BP_Messages_Thread::get_inbox_count( $user_id );
}

/**
 * Check whether a user is the sender of a message.
 *
 * @param int $user_id    ID of the user.
 * @param int $message_id ID of the message.
 * @return int|null Returns the ID of the message if the user is the
 *                  sender, otherwise null.
 */
function messages_is_user_sender( $user_id, $message_id ) {
	return BP_Messages_Message::is_user_sender( $user_id, $message_id );
}

/**
 * Get the ID of the sender of a message.
 *
 * @param int $message_id ID of the message.
 * @return int|null The ID of the sender if found, otherwise null.
 */
function messages_get_message_sender( $message_id ) {
	return BP_Messages_Message::get_message_sender( $message_id );
}

/**
 * Check whether a message thread exists.
 *
 * @param int $thread_id ID of the thread.
 * @return int|null The message thread ID on success, null on failure.
 */
function messages_is_valid_thread( $thread_id ) {
	return BP_Messages_Thread::is_valid( $thread_id );
}

/**
 * Get the thread ID from a message ID.
 *
 * @since 2.3.0
 *
 * @param  int $message_id ID of the message.
 * @return int The ID of the thread if found, otherwise 0.
 */
function messages_get_message_thread_id( $message_id = 0 ) {
	global $wpdb;

	$bp = buddypress();

	return (int) $wpdb->get_var( $wpdb->prepare( "SELECT thread_id FROM {$bp->messages->table_name_messages} WHERE id = %d", $message_id ) );
}

/** Messages Meta *******************************************************/

/**
 * Delete metadata for a message.
 *
 * If $meta_key is false, this will delete all meta for the message ID.
 *
 * @since 2.2.0
 *
 * @see delete_metadata() for full documentation excluding $meta_type variable.
 *
 * @param int         $message_id ID of the message to have meta deleted for.
 * @param string|bool $meta_key   Meta key to delete. Default false.
 * @param string|bool $meta_value Meta value to delete. Default false.
 * @param bool        $delete_all Whether or not to delete all meta data.
 * @return bool
 */
function bp_messages_delete_meta( $message_id, $meta_key = false, $meta_value = false, $delete_all = false ) {
	// Legacy - if no meta_key is passed, delete all for the item.
	if ( empty( $meta_key ) ) {
		global $wpdb;

		$keys = $wpdb->get_col( $wpdb->prepare( "SELECT meta_key FROM {$wpdb->messagemeta} WHERE message_id = %d", $message_id ) );

		// With no meta_key, ignore $delete_all.
		$delete_all = false;
	} else {
		$keys = array( $meta_key );
	}

	// No keys, so stop now!
	if ( empty( $keys ) ) {
		return false;
	}

	add_filter( 'query', 'bp_filter_metaid_column_name' );

	foreach ( $keys as $key ) {
		$retval = delete_metadata( 'message', $message_id, $key, $meta_value, $delete_all );
	}

	remove_filter( 'query', 'bp_filter_metaid_column_name' );

	return $retval;
}

/**
 * Get a piece of message metadata.
 *
 * @since 2.2.0
 *
 * @see get_metadata() for full documentation excluding $meta_type variable.
 *
 * @param int    $message_id ID of the message to retrieve meta for.
 * @param string $meta_key   Meta key to retrieve. Default empty string.
 * @param bool   $single     Whether or not to fetch all or a single value.
 * @return mixed
 */
function bp_messages_get_meta( $message_id, $meta_key = '', $single = true ) {
	add_filter( 'query', 'bp_filter_metaid_column_name' );
	$retval = get_metadata( 'message', $message_id, $meta_key, $single );
	remove_filter( 'query', 'bp_filter_metaid_column_name' );

	return $retval;
}

/**
 * Update a piece of message metadata.
 *
 * @since 2.2.0
 *
 * @see update_metadata() for full documentation excluding $meta_type variable.
 *
 * @param int         $message_id ID of the message to have meta deleted for.
 * @param string|bool $meta_key   Meta key to update.
 * @param string|bool $meta_value Meta value to update.
 * @param string      $prev_value If specified, only update existing metadata entries with
 *                                the specified value. Otherwise, update all entries.
 * @return mixed
 */
function bp_messages_update_meta( $message_id, $meta_key, $meta_value, $prev_value = '' ) {
	add_filter( 'query', 'bp_filter_metaid_column_name' );
	$retval = update_metadata( 'message', $message_id, $meta_key, $meta_value, $prev_value );
	remove_filter( 'query', 'bp_filter_metaid_column_name' );

	return $retval;
}

/**
 * Add a piece of message metadata.
 *
 * @since 2.2.0
 *
 * @see add_metadata() for full documentation excluding $meta_type variable.
 *
 * @param int         $message_id ID of the message to have meta deleted for.
 * @param string|bool $meta_key   Meta key to update.
 * @param string|bool $meta_value Meta value to update.
 * @param bool        $unique     Whether the specified metadata key should be
 *                                unique for the object. If true, and the object
 *                                already has a value for the specified metadata key,
 *                                no change will be made.
 * @return mixed
 */
function bp_messages_add_meta( $message_id, $meta_key, $meta_value, $unique = false ) {
	add_filter( 'query', 'bp_filter_metaid_column_name' );
	$retval = add_metadata( 'message', $message_id, $meta_key, $meta_value, $unique );
	remove_filter( 'query', 'bp_filter_metaid_column_name' );

	return $retval;
}
