<?php
/**
 * BuddyPress Messages Classes.
 *
 * @package BuddyPress
 * @subpackage MessagesClasses
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Single message class.
 */
class BP_Messages_Message {
	/**
	 * ID of the message.
	 *
	 * @var int
	 */
	public $id;

	/**
	 * ID of the message thread.
	 *
	 * @var int
	 */
	public $thread_id;

	/**
	 * ID of the sender.
	 *
	 * @var int
	 */
	public $sender_id;

	/**
	 * Subject line of the message.
	 *
	 * @var string
	 */
	public $subject;

	/**
	 * Content of the message.
	 *
	 * @var string
	 */
	public $message;

	/**
	 * Date the message was sent.
	 *
	 * @var string
	 */
	public $date_sent;

	/**
	 * Message recipients.
	 *
	 * @var bool|array
	 */
	public $recipients = false;

	/**
	 * Constructor.
	 *
	 * @param int $id Optional. ID of the message.
	 */
	public function __construct( $id = null ) {
		$this->date_sent = bp_core_current_time();
		$this->sender_id = bp_loggedin_user_id();

		if ( !empty( $id ) ) {
			$this->populate( $id );
		}
	}

	/**
	 * Set up data related to a specific message object.
	 *
	 * @param int $id ID of the message.
	 */
	public function populate( $id ) {
		global $wpdb;

		$bp = buddypress();

		if ( $message = $wpdb->get_row( $wpdb->prepare( "SELECT * FROM {$bp->messages->table_name_messages} WHERE id = %d", $id ) ) ) {
			$this->id        = $message->id;
			$this->thread_id = $message->thread_id;
			$this->sender_id = $message->sender_id;
			$this->subject   = $message->subject;
			$this->message   = $message->message;
			$this->date_sent = $message->date_sent;
		}
	}

	/**
	 * Send a message.
	 *
	 * @return int|bool ID of the newly created message on success, false on failure.
	 */
	public function send() {
		global $wpdb;

		$bp = buddypress();

		$this->sender_id = apply_filters( 'messages_message_sender_id_before_save', $this->sender_id, $this->id );
		$this->thread_id = apply_filters( 'messages_message_thread_id_before_save', $this->thread_id, $this->id );
		$this->subject   = apply_filters( 'messages_message_subject_before_save',   $this->subject,   $this->id );
		$this->message   = apply_filters( 'messages_message_content_before_save',   $this->message,   $this->id );
		$this->date_sent = apply_filters( 'messages_message_date_sent_before_save', $this->date_sent, $this->id );

		/**
		 * Fires before the current message item gets saved.
		 *
		 * Please use this hook to filter the properties above. Each part will be passed in.
		 *
		 * @since 1.0.0
		 *
		 * @param BP_Messages_Message $this Current instance of the message item being saved. Passed by reference.
		 */
		do_action_ref_array( 'messages_message_before_save', array( &$this ) );

		// Make sure we have at least one recipient before sending.
		if ( empty( $this->recipients ) )
			return false;

		$new_thread = false;

		// If we have no thread_id then this is the first message of a new thread.
		if ( empty( $this->thread_id ) ) {
			$this->thread_id = (int) $wpdb->get_var( "SELECT MAX(thread_id) FROM {$bp->messages->table_name_messages}" ) + 1;
			$new_thread = true;
		}

		// First insert the message into the messages table.
		if ( !$wpdb->query( $wpdb->prepare( "INSERT INTO {$bp->messages->table_name_messages} ( thread_id, sender_id, subject, message, date_sent ) VALUES ( %d, %d, %s, %s, %s )", $this->thread_id, $this->sender_id, $this->subject, $this->message, $this->date_sent ) ) )
			return false;

		$this->id = $wpdb->insert_id;

		$recipient_ids = array();

		if ( $new_thread ) {
			// Add an recipient entry for all recipients.
			foreach ( (array) $this->recipients as $recipient ) {
				$wpdb->query( $wpdb->prepare( "INSERT INTO {$bp->messages->table_name_recipients} ( user_id, thread_id, unread_count ) VALUES ( %d, %d, 1 )", $recipient->user_id, $this->thread_id ) );
				$recipient_ids[] = $recipient->user_id;
			}

			// Add a sender recipient entry if the sender is not in the list of recipients.
			if ( !in_array( $this->sender_id, $recipient_ids ) )
				$wpdb->query( $wpdb->prepare( "INSERT INTO {$bp->messages->table_name_recipients} ( user_id, thread_id, sender_only ) VALUES ( %d, %d, 1 )", $this->sender_id, $this->thread_id ) );
		} else {
			// Update the unread count for all recipients.
			$wpdb->query( $wpdb->prepare( "UPDATE {$bp->messages->table_name_recipients} SET unread_count = unread_count + 1, sender_only = 0, is_deleted = 0 WHERE thread_id = %d AND user_id != %d", $this->thread_id, $this->sender_id ) );
		}

		messages_remove_callback_values();

		/**
		 * Fires after the current message item has been saved.
		 *
		 * @since 1.0.0
		 *
		 * @param BP_Messages_Message $this Current instance of the message item being saved. Passed by reference.
		 */
		do_action_ref_array( 'messages_message_after_save', array( &$this ) );

		return $this->id;
	}

	/**
	 * Get a list of recipients for a message.
	 *
	 * @return object $value List of recipients for a message.
	 */
	public function get_recipients() {
		global $wpdb;

		$bp = buddypress();

		return $wpdb->get_results( $wpdb->prepare( "SELECT user_id FROM {$bp->messages->table_name_recipients} WHERE thread_id = %d", $this->thread_id ) );
	}

	/** Static Functions **************************************************/

	/**
	 * Get list of recipient IDs from their usernames.
	 *
	 * @param array $recipient_usernames Usernames of recipients.
	 * @return array $recipient_ids Array of Recepient IDs.
	 */
	public static function get_recipient_ids( $recipient_usernames ) {
		if ( !$recipient_usernames )
			return false;

		if ( is_array( $recipient_usernames ) ) {
			for ( $i = 0, $count = count( $recipient_usernames ); $i < $count; ++$i ) {
				if ( $rid = bp_core_get_userid( trim($recipient_usernames[$i]) ) ) {
					$recipient_ids[] = $rid;
				}
			}
		}

		return $recipient_ids;
	}

	/**
	 * Get the ID of the message last sent by the logged-in user for a given thread.
	 *
	 * @param int $thread_id ID of the thread.
	 * @return int|null ID of the message if found, otherwise null.
	 */
	public static function get_last_sent_for_user( $thread_id ) {
		global $wpdb;

		$bp = buddypress();

		return $wpdb->get_var( $wpdb->prepare( "SELECT id FROM {$bp->messages->table_name_messages} WHERE sender_id = %d AND thread_id = %d ORDER BY date_sent DESC LIMIT 1", bp_loggedin_user_id(), $thread_id ) );
	}

	/**
	 * Check whether a user is the sender of a message.
	 *
	 * @param int $user_id    ID of the user.
	 * @param int $message_id ID of the message.
	 * @return int|null Returns the ID of the message if the user is the
	 *                  sender, otherwise null.
	 */
	public static function is_user_sender( $user_id, $message_id ) {
		global $wpdb;

		$bp = buddypress();

		return $wpdb->get_var( $wpdb->prepare( "SELECT id FROM {$bp->messages->table_name_messages} WHERE sender_id = %d AND id = %d", $user_id, $message_id ) );
	}

	/**
	 * Get the ID of the sender of a message.
	 *
	 * @param int $message_id ID of the message.
	 * @return int|null The ID of the sender if found, otherwise null.
	 */
	public static function get_message_sender( $message_id ) {
		global $wpdb;

		$bp = buddypress();

		return $wpdb->get_var( $wpdb->prepare( "SELECT sender_id FROM {$bp->messages->table_name_messages} WHERE id = %d", $message_id ) );
	}
}
