<?php
/**
 * BuddyPress Messages Actions.
 *
 * Action functions are exactly the same as screen functions, however they do
 * not have a template screen associated with them. Usually they will send the
 * user back to the default screen after execution.
 *
 * @package BuddyPress
 * @subpackage MessagesActions
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Handle creating of private messages or sitewide notices
 *
 * @since 2.4.0 This function was split from messages_screen_compose(). See #6505.
 *
 * @return boolean
 */
function bp_messages_action_create_message() {

	// Bail if not posting to the compose message screen.
	if ( ! bp_is_post_request() || ! bp_is_messages_component() || ! bp_is_current_action( 'compose' ) ) {
		return false;
	}

	// Check the nonce.
	check_admin_referer( 'messages_send_message' );

	// Define local variables.
	$redirect_to = '';
	$feedback    = '';
	$success     = false;

	// Missing subject or content.
	if ( empty( $_POST['subject'] ) || empty( $_POST['content'] ) ) {
		$success  = false;

		if ( empty( $_POST['subject'] ) ) {
			$feedback = __( 'Your message was not sent. Please enter a subject line.', 'buddypress' );
		} else {
			$feedback = __( 'Your message was not sent. Please enter some content.', 'buddypress' );
		}

	// Subject and content present.
	} else {

		// Setup the link to the logged-in user's messages.
		$member_messages = trailingslashit( bp_loggedin_user_domain() . bp_get_messages_slug() );

		// Site-wide notice.
		if ( isset( $_POST['send-notice'] ) ) {

			// Attempt to save the notice and redirect to notices.
			if ( messages_send_notice( $_POST['subject'], $_POST['content'] ) ) {
				$success     = true;
				$feedback    = __( 'Notice successfully created.', 'buddypress' );
				$redirect_to = trailingslashit( $member_messages . 'notices' );

			// Notice could not be sent.
			} else {
				$success  = false;
				$feedback = __( 'Notice was not created. Please try again.', 'buddypress' );
			}

		// Private conversation.
		} else {

			// Filter recipients into the format we need - array( 'username/userid', 'username/userid' ).
			$autocomplete_recipients = (array) explode( ',', $_POST['send-to-input']     );
			$typed_recipients        = (array) explode( ' ', $_POST['send_to_usernames'] );
			$recipients              = array_merge( $autocomplete_recipients, $typed_recipients );

			/**
			 * Filters the array of recipients to receive the composed message.
			 *
			 * @since 1.2.10
			 *
			 * @param array $recipients Array of recipients to receive message.
			 */
			$recipients = apply_filters( 'bp_messages_recipients', $recipients );

			// Attempt to send the message.
			$send = messages_new_message( array(
				'recipients' => $recipients,
				'subject'    => $_POST['subject'],
				'content'    => $_POST['content'],
				'error_type' => 'wp_error'
			) );

			// Send the message and redirect to it.
			if ( true === is_int( $send ) ) {
				$success     = true;
				$feedback    = __( 'Message successfully sent.', 'buddypress' );
				$view        = trailingslashit( $member_messages . 'view' );
				$redirect_to = trailingslashit( $view . $send );

			// Message could not be sent.
			} else {
				$success  = false;
				$feedback = $send->get_error_message();
			}
		}
	}

	// Feedback.
	if ( ! empty( $feedback ) ) {

		// Determine message type.
		$type = ( true === $success )
			? 'success'
			: 'error';

		// Add feedback message.
		bp_core_add_message( $feedback, $type );
	}

	// Maybe redirect.
	if ( ! empty( $redirect_to ) ) {
		bp_core_redirect( $redirect_to );
	}
}
add_action( 'bp_actions', 'bp_messages_action_create_message' );

/**
 * Handle editing of sitewide notices.
 *
 * @since 2.4.0 This function was split from messages_screen_notices(). See #6505.
 *
 * @global int $notice_id
 *
 * @return boolean
 */
function bp_messages_action_edit_notice() {
	global $notice_id;

	// Bail if not viewing a single notice URL.
	if ( ! bp_is_messages_component() || ! bp_is_current_action( 'notices' ) || ! bp_action_variable( 1 ) ) {
		return false;
	}

	// Get action variables.
	$action    = bp_action_variable( 0 ); // deactivate|activate|delete.
	$notice_id = bp_action_variable( 1 ); // 1|2|3|etc...

	// Bail if notice ID is not numeric.
	if ( ! is_numeric( $notice_id ) ) {
		return;
	}

	// Define local variables.
	$redirect_to = '';
	$feedback    = '';
	$success     = false;

	// Get the notice from database.
	$notice = new BP_Messages_Notice( $notice_id );

	// Take action.
	switch ( $action ) {

		// Deactivate.
		case 'deactivate' :
			$success  = $notice->deactivate();
			$feedback = true === $success
				? __( 'Notice deactivated successfully.',              'buddypress' )
				: __( 'There was a problem deactivating that notice.', 'buddypress' );
			break;

		// Activate.
		case 'activate' :
			$success  = $notice->activate();
			$feedback = true === $success
				? __( 'Notice activated successfully.',              'buddypress' )
				: __( 'There was a problem activating that notice.', 'buddypress' );
			break;

		// Delete.
		case 'delete' :
			$success  = $notice->delete();
			$feedback = true === $success
				? __( 'Notice deleted successfully.',              'buddypress' )
				: __( 'There was a problem deleting that notice.', 'buddypress' );
			break;
	}

	// Feedback.
	if ( ! empty( $feedback ) ) {

		// Determine message type.
		$type = ( true === $success )
			? 'success'
			: 'error';

		// Add feedback message.
		bp_core_add_message( $feedback, $type );
	}

	// Redirect.
	$member_notices = trailingslashit( bp_loggedin_user_domain() . bp_get_messages_slug() );
	$redirect_to    = trailingslashit( $member_notices . 'notices' );

	bp_core_redirect( $redirect_to );
}
add_action( 'bp_actions', 'bp_messages_action_edit_notice' );

/**
 * Process a request to view a single message thread.
 */
function messages_action_conversation() {

	// Bail if not viewing a single conversation.
	if ( ! bp_is_messages_component() || ! bp_is_current_action( 'view' ) ) {
		return false;
	}

	// Get the thread ID from the action variable.
	$thread_id = (int) bp_action_variable( 0 );

	if ( ! messages_is_valid_thread( $thread_id ) || ( ! messages_check_thread_access( $thread_id ) && ! bp_current_user_can( 'bp_moderate' ) ) ) {
		bp_core_redirect( trailingslashit( bp_displayed_user_domain() . bp_get_messages_slug() ) );
	}

	// Check if a new reply has been submitted.
	if ( isset( $_POST['send'] ) ) {

		// Check the nonce.
		check_admin_referer( 'messages_send_message', 'send_message_nonce' );

		$new_reply = messages_new_message( array(
			'thread_id' => $thread_id,
			'subject'   => ! empty( $_POST['subject'] ) ? $_POST['subject'] : false,
			'content'   => $_POST['content']
		) );

		// Send the reply.
		if ( ! empty( $new_reply ) ) {
			bp_core_add_message( __( 'Your reply was sent successfully', 'buddypress' ) );
		} else {
			bp_core_add_message( __( 'There was a problem sending your reply. Please try again.', 'buddypress' ), 'error' );
		}

		bp_core_redirect( bp_displayed_user_domain() . bp_get_messages_slug() . '/view/' . $thread_id . '/' );
	}

	// Mark message read.
	messages_mark_thread_read( $thread_id );

	/**
	 * Fires after processing a view request for a single message thread.
	 *
	 * @since 1.7.0
	 */
	do_action( 'messages_action_conversation' );
}
add_action( 'bp_actions', 'messages_action_conversation' );

/**
 * Process a request to delete a message.
 *
 * @return bool False on failure.
 */
function messages_action_delete_message() {

	if ( ! bp_is_messages_component() || bp_is_current_action( 'notices' ) || ! bp_is_action_variable( 'delete', 0 ) ) {
		return false;
	}

	$thread_id = bp_action_variable( 1 );

	if ( !$thread_id || !is_numeric( $thread_id ) || !messages_check_thread_access( $thread_id ) ) {
		bp_core_redirect( trailingslashit( bp_displayed_user_domain() . bp_get_messages_slug() . '/' . bp_current_action() ) );
	} else {
		if ( ! check_admin_referer( 'messages_delete_thread' ) ) {
			return false;
		}

		// Delete message.
		if ( !messages_delete_thread( $thread_id ) ) {
			bp_core_add_message( __('There was an error deleting that message.', 'buddypress'), 'error' );
		} else {
			bp_core_add_message( __('Message deleted.', 'buddypress') );
		}
		bp_core_redirect( trailingslashit( bp_displayed_user_domain() . bp_get_messages_slug() . '/' . bp_current_action() ) );
	}
}
add_action( 'bp_actions', 'messages_action_delete_message' );

/**
 * Handle marking a single message thread as read.
 *
 * @since 2.2.0
 *
 * @return bool|null Returns false on failure. Otherwise redirects back to the
 *                   message box URL.
 */
function bp_messages_action_mark_read() {

	if ( ! bp_is_messages_component() || bp_is_current_action( 'notices' ) || ! bp_is_action_variable( 'read', 0 ) ) {
		return false;
	}

	$action = ! empty( $_GET['action'] ) ? $_GET['action'] : '';
	$nonce  = ! empty( $_GET['_wpnonce'] ) ? $_GET['_wpnonce'] : '';
	$id     = ! empty( $_GET['message_id'] ) ? intval( $_GET['message_id'] ) : '';

	// Bail if no action or no ID.
	if ( 'read' !== $action || empty( $id ) || empty( $nonce ) ) {
		return false;
	}

	// Check the nonce.
	if ( ! bp_verify_nonce_request( 'bp_message_thread_mark_read_' . $id ) ) {
		return false;
	}

	// Check access to the message and mark as read.
	if ( messages_check_thread_access( $id ) ) {
		messages_mark_thread_read( $id );
		bp_core_add_message( __( 'Message marked as read.', 'buddypress' ) );
	} else {
		bp_core_add_message( __( 'There was a problem marking that message.', 'buddypress' ), 'error' );
	}

	// Redirect back to the message box.
	bp_core_redirect( bp_displayed_user_domain() . bp_get_messages_slug() . '/' . bp_current_action() );
}
add_action( 'bp_actions', 'bp_messages_action_mark_read' );

/**
 * Handle marking a single message thread as unread.
 *
 * @since 2.2.0
 *
 * @return bool|null Returns false on failure. Otherwise redirects back to the
 *                   message box URL.
 */
function bp_messages_action_mark_unread() {

	if ( ! bp_is_messages_component() || bp_is_current_action( 'notices' ) || ! bp_is_action_variable( 'unread', 0 ) ) {
		return false;
	}

	$action = ! empty( $_GET['action'] ) ? $_GET['action'] : '';
	$nonce  = ! empty( $_GET['_wpnonce'] ) ? $_GET['_wpnonce'] : '';
	$id     = ! empty( $_GET['message_id'] ) ? intval( $_GET['message_id'] ) : '';

	// Bail if no action or no ID.
	if ( 'unread' !== $action || empty( $id ) || empty( $nonce ) ) {
		return false;
	}

	// Check the nonce.
	if ( ! bp_verify_nonce_request( 'bp_message_thread_mark_unread_' . $id ) ) {
		return false;
	}

	// Check access to the message and mark unread.
	if ( messages_check_thread_access( $id ) ) {
		messages_mark_thread_unread( $id );
		bp_core_add_message( __( 'Message marked unread.', 'buddypress' ) );
	} else {
		bp_core_add_message( __( 'There was a problem marking that message.', 'buddypress' ), 'error' );
	}

	// Redirect back to the message box URL.
	bp_core_redirect( bp_displayed_user_domain() . bp_get_messages_slug() . '/' . bp_current_action() );
}
add_action( 'bp_actions', 'bp_messages_action_mark_unread' );

/**
 * Handle bulk management (mark as read/unread, delete) of message threads.
 *
 * @since 2.2.0
 *
 * @return bool Returns false on failure. Otherwise redirects back to the
 *              message box URL.
 */
function bp_messages_action_bulk_manage() {

	if ( ! bp_is_messages_component() || bp_is_current_action( 'notices' ) || ! bp_is_action_variable( 'bulk-manage', 0 ) ) {
		return false;
	}

	$action   = ! empty( $_POST['messages_bulk_action'] ) ? $_POST['messages_bulk_action'] : '';
	$nonce    = ! empty( $_POST['messages_bulk_nonce'] ) ? $_POST['messages_bulk_nonce'] : '';
	$messages = ! empty( $_POST['message_ids'] ) ? $_POST['message_ids'] : '';

	$messages = wp_parse_id_list( $messages );

	// Bail if no action or no IDs.
	if ( ( ! in_array( $action, array( 'delete', 'read', 'unread' ) ) ) || empty( $messages ) || empty( $nonce ) ) {
		bp_core_redirect( bp_displayed_user_domain() . bp_get_messages_slug() . '/' . bp_current_action() . '/' );
	}

	// Check the nonce.
	if ( ! wp_verify_nonce( $nonce, 'messages_bulk_nonce' ) ) {
		return false;
	}

	// Make sure the user has access to all notifications before managing them.
	foreach ( $messages as $message ) {
		if ( ! messages_check_thread_access( $message ) ) {
			bp_core_add_message( __( 'There was a problem managing your messages.', 'buddypress' ), 'error' );
			bp_core_redirect( bp_displayed_user_domain() . bp_get_messages_slug() . '/' . bp_current_action() . '/' );
		}
	}

	// Delete, mark as read or unread depending on the user 'action'.
	switch ( $action ) {
		case 'delete' :
			foreach ( $messages as $message ) {
				messages_delete_thread( $message );
			}
			bp_core_add_message( __( 'Messages deleted.', 'buddypress' ) );
		break;

		case 'read' :
			foreach ( $messages as $message ) {
				messages_mark_thread_read( $message );
			}
			bp_core_add_message( __( 'Messages marked as read', 'buddypress' ) );
		break;

		case 'unread' :
			foreach ( $messages as $message ) {
				messages_mark_thread_unread( $message );
			}
			bp_core_add_message( __( 'Messages marked as unread.', 'buddypress' ) );
		break;
	}

	// Redirect back to message box.
	bp_core_redirect( bp_displayed_user_domain() . bp_get_messages_slug() . '/' . bp_current_action() . '/' );
}
add_action( 'bp_actions', 'bp_messages_action_bulk_manage' );

/**
 * Process a request to bulk delete messages.
 *
 * @return bool False on failure.
 */
function messages_action_bulk_delete() {

	if ( ! bp_is_messages_component() || ! bp_is_action_variable( 'bulk-delete', 0 ) ) {
		return false;
	}

	$thread_ids = $_POST['thread_ids'];

	if ( !$thread_ids || !messages_check_thread_access( $thread_ids ) ) {
		bp_core_redirect( trailingslashit( bp_displayed_user_domain() . bp_get_messages_slug() . '/' . bp_current_action() ) );
	} else {
		if ( !check_admin_referer( 'messages_delete_thread' ) ) {
			return false;
		}

		if ( !messages_delete_thread( $thread_ids ) ) {
			bp_core_add_message( __('There was an error deleting messages.', 'buddypress'), 'error' );
		} else {
			bp_core_add_message( __('Messages deleted.', 'buddypress') );
		}

		bp_core_redirect( trailingslashit( bp_displayed_user_domain() . bp_get_messages_slug() . '/' . bp_current_action() ) );
	}
}
add_action( 'bp_actions', 'messages_action_bulk_delete' );
