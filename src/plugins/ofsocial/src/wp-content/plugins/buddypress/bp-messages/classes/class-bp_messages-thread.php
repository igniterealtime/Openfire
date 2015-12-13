<?php
/**
 * BuddyPress Messages Classes
 *
 * @package BuddyPress
 * @subpackage MessagesClasses
 */

// Exit if accessed directly
defined( 'ABSPATH' ) || exit;

/**
 * BuddyPress Message Thread class.
 *
 * @since BuddyPress (1.0.0)
 */
class BP_Messages_Thread {
	/**
	 * The message thread ID.
	 *
	 * @since BuddyPress (1.0.0)
	 * @var int
	 */
	public $thread_id;

	/**
	 * The current messages.
	 *
	 * @since BuddyPress (1.0.0)
	 * @var object
	 */
	public $messages;

	/**
	 * The current recipients in the message thread.
	 *
	 * @since BuddyPress (1.0.0)
	 * @var object
	 */
	public $recipients;

	/**
	 * The user IDs of all messages in the message thread.
	 *
	 * @since BuddyPress (1.2.0)
	 * @var array
	 */
	public $sender_ids;

	/**
	 * The unread count for the logged-in user.
	 *
	 * @since BuddyPress (1.2.0)
	 * @var int
	 */
	public $unread_count;

	/**
	 * The content of the last message in this thread.
	 *
	 * @since BuddyPress (1.2.0)
	 * @var string
	 */
	public $last_message_content;

	/**
	 * The date of the last message in this thread.
	 *
	 * @since BuddyPress (1.2.0)
	 * @var string
	 */
	public $last_message_date;

	/**
	 * The ID of the last message in this thread.
	 *
	 * @since BuddyPress (1.2.0)
	 * @var int
	 */
	public $last_message_id;

	/**
	 * The subject of the last message in this thread.
	 *
	 * @since BuddyPress (1.2.0)
	 * @var string
	 */
	public $last_message_subject;

	/**
	 * The user ID of the author of the last message in this thread.
	 *
	 * @since BuddyPress (1.2.0)
	 * @var int
	 */
	public $last_sender_id;

	/**
	 * Sort order of the messages in this thread (ASC or DESC).
	 *
	 * @since BuddyPress (1.5.0)
	 * @var string
	 */
	public $messages_order;

	/**
	 * Constructor.
	 *
	 * @since BuddyPress (1.0.0)
	 *
	 * @see BP_Messages_Thread::populate() for full description of parameters.
	 *
	 * @param bool   $thread_id
	 * @param string $order
	 * @param array  $args
	 */
	public function __construct( $thread_id = false, $order = 'ASC', $args = array() ) {
		if ( $thread_id ) {
			$this->populate( $thread_id, $order, $args );
		}
	}

	/**
	 * Populate method.
	 *
	 * Used in constructor.
	 *
	 * @since BuddyPress (1.0.0)
	 *
	 * @param int    $thread_id The message thread ID.
	 * @param string $order     The order to sort the messages. Either 'ASC' or 'DESC'.
	 * @param array $args {
	 *     Array of arguments.
	 *     @type bool $update_meta_cache Whether to pre-fetch metadata for
	 *                                   queried message items. Default: true.
	 * }
	 * @return bool False on failure.
	 */
	public function populate( $thread_id = 0, $order = 'ASC', $args = array() ) {
		global $wpdb;

		if( 'ASC' != $order && 'DESC' != $order ) {
			$order = 'ASC';
		}

		// merge $args with our defaults
		$r = wp_parse_args( $args, array(
			'update_meta_cache' => true
		) );

		$this->messages_order = $order;
		$this->thread_id      = $thread_id;

		// get messages for thread
		$this->messages = self::get_messages( $thread_id );

		if ( empty( $this->messages ) || is_wp_error( $this->messages ) ) {
			return false;
		}

		// flip if order is DESC
		if ( 'DESC' === $order ) {
			$this->messages = array_reverse( $this->messages );
		}

		foreach ( (array) $this->messages as $key => $message ) {
			$this->sender_ids[$message->sender_id] = $message->sender_id;
		}

		// Fetch the recipients
		$this->recipients = $this->get_recipients();

		// Get the unread count for the logged in user
		if ( isset( $this->recipients[bp_loggedin_user_id()] ) ) {
			$this->unread_count = $this->recipients[bp_loggedin_user_id()]->unread_count;
		}

		// Grab all message meta
		if ( true === (bool) $r['update_meta_cache'] ) {
			bp_messages_update_meta_cache( wp_list_pluck( $this->messages, 'id' ) );
		}

		/**
		 * Fires after a BP_Messages_Thread object has been populated.
		 *
		 * @since BuddyPress (2.2.0)
		 *
		 * @param BP_Messages_Thread $this Message thread object.
		 */
		do_action( 'bp_messages_thread_post_populate', $this );
	}

	/**
	 * Mark a thread initialized in this class as read.
	 *
	 * @since BuddyPress (1.0.0)
	 *
	 * @see BP_Messages_Thread::mark_as_read()
	 */
	public function mark_read() {
		BP_Messages_Thread::mark_as_read( $this->thread_id );
	}

	/**
	 * Mark a thread initialized in this class as unread.
	 *
	 * @since BuddyPress (1.0.0)
	 *
	 * @see BP_Messages_Thread::mark_as_unread()
	 */
	public function mark_unread() {
		BP_Messages_Thread::mark_as_unread( $this->thread_id );
	}

	/**
	 * Returns recipients for a message thread.
	 *
	 * @since BuddyPress (1.0.0)
	 * @since BuddyPress (2.3.0) Added $thread_id as a parameter.
	 *
	 * @param int $thread_id The thread ID.
	 *
	 * @return array
	 */
	public function get_recipients( $thread_id = 0 ) {
		global $wpdb;

		if ( empty( $thread_id ) ) {
			$thread_id = $this->thread_id;
		}

		$recipients = wp_cache_get( 'thread_recipients_' . $thread_id, 'bp_messages' );
		if ( false === $recipients ) {
			$bp = buddypress();

			$recipients = array();
			$results    = $wpdb->get_results( $wpdb->prepare( "SELECT * FROM {$bp->messages->table_name_recipients} WHERE thread_id = %d", $thread_id ) );

			foreach ( (array) $results as $recipient ) {
				$recipients[ $recipient->user_id ] = $recipient;
			}

			wp_cache_set( 'thread_recipients_' . $thread_id, $recipients, 'bp_messages' );
		}

		/**
		 * Filters the recipients of a message thread.
		 *
		 * @since BuddyPress (2.2.0)
		 *
		 * @param array $recipients Array of recipient objects.
		 * @param int   $thread_id  ID of the current thread.
		 */
		return apply_filters( 'bp_messages_thread_get_recipients', $recipients, $thread_id );
	}

	/** Static Functions ******************************************************/

	/**
	 * Get all messages associated with a thread.
	 *
	 * @since BuddyPress (2.3.0)
	 *
	 * @param int $thread_id The message thread ID.
	 *
	 * @return array
	 */
	public static function get_messages( $thread_id = 0 ) {
		$messages = wp_cache_get( $thread_id, 'bp_messages_threads' );

		if ( false === $messages ) {
			global $wpdb;

			$bp = buddypress();

			// always sort by ASC by default
			$messages = $wpdb->get_results( $wpdb->prepare( "SELECT * FROM {$bp->messages->table_name_messages} WHERE thread_id = %d ORDER BY date_sent ASC", $thread_id ) );

			wp_cache_set( $thread_id, (array) $messages, 'bp_messages_threads' );
		}

		return $messages;
	}

	/**
	 * Static method to get message recipients by thread ID.
	 *
	 * @since BuddyPress (2.3.0)
	 *
	 * @param  int $thread_id The thread ID.
	 *
	 * @return array
	 */
	public static function get_recipients_for_thread( $thread_id = 0 ) {
		$thread = new self( false );
		return $thread->get_recipients( $thread_id );
	}

	/**
	 * Mark messages in a thread as deleted or delete all messages in a thread.
	 *
	 * Note: All messages in a thread are deleted once every recipient in a thread
	 * has marked the thread as deleted.
	 *
	 * @since BuddyPress (1.0.0)
	 *
	 * @param int $thread_id The message thread ID.
	 *
	 * @return bool
	 */
	public static function delete( $thread_id ) {
		global $wpdb;

		/**
		 * Fires before a message thread is marked as deleted.
		 *
		 * @since BuddyPress (2.2.0)
		 *
		 * @param int $thread_id ID of the thread being deleted.
		 */
		do_action( 'bp_messages_thread_before_mark_delete', $thread_id );

		$bp = buddypress();

		// Mark messages as deleted
		//
		// @todo the reliance on bp_loggedin_user_id() sucks for plugins
		//       refactor this method to accept a $user_id parameter
		$wpdb->query( $wpdb->prepare( "UPDATE {$bp->messages->table_name_recipients} SET is_deleted = 1 WHERE thread_id = %d AND user_id = %d", $thread_id, bp_loggedin_user_id() ) );

		// Get the message ids in order to pass to the action
		$message_ids = $wpdb->get_col( $wpdb->prepare( "SELECT id FROM {$bp->messages->table_name_messages} WHERE thread_id = %d", $thread_id ) );

		// Check to see if any more recipients remain for this message
		$recipients = $wpdb->get_results( $wpdb->prepare( "SELECT id FROM {$bp->messages->table_name_recipients} WHERE thread_id = %d AND is_deleted = 0", $thread_id ) );

		// No more recipients so delete all messages associated with the thread
		if ( empty( $recipients ) ) {

			/**
			 * Fires before an entire message thread is deleted.
			 *
			 * @since BuddyPress (2.2.0)
			 *
			 * @param int   $thread_id   ID of the thread being deleted.
			 * @param array $message_ids IDs of messages being deleted.
			 */
			do_action( 'bp_messages_thread_before_delete', $thread_id, $message_ids );

			// Delete all the messages
			$wpdb->query( $wpdb->prepare( "DELETE FROM {$bp->messages->table_name_messages} WHERE thread_id = %d", $thread_id ) );

			// Do something for each message ID
			foreach ( $message_ids as $message_id ) {
				// Delete message meta
				bp_messages_delete_meta( $message_id );

				/**
				 * Fires after a message is deleted. This hook is poorly named.
				 *
				 * @since BuddyPress (1.0.0)
				 *
				 * @param int $message_id ID of the message.
				 */
				do_action( 'messages_thread_deleted_thread', $message_id );
			}

			// Delete all the recipients
			$wpdb->query( $wpdb->prepare( "DELETE FROM {$bp->messages->table_name_recipients} WHERE thread_id = %d", $thread_id ) );
		}

		/**
		 * Fires after a message thread is either marked as deleted or deleted.
		 *
		 * @since BuddyPress (2.2.0)
		 *
		 * @param int   $thread_id   ID of the thread being deleted.
		 * @param array $message_ids IDs of messages being deleted.
		 */
		do_action( 'bp_messages_thread_after_delete', $thread_id, $message_ids );

		return true;
	}

	/**
	 * Get current message threads for a user.
	 *
	 * @since BuddyPress (1.0.0)
	 *
	 * @param array $args {
	 *     Array of arguments.
	 *     @type int    $user_id      The user ID.
	 *     @type string $box          The type of mailbox to get. Either 'inbox' or 'sentbox'.
	 *                                Defaults to 'inbox'.
	 *     @type string $type         The type of messages to get. Either 'all' or 'unread'
	 *                                or 'read'. Defaults to 'all'.
	 *     @type int    $limit        The number of messages to get. Defaults to null.
	 *     @type int    $page         The page number to get. Defaults to null.
	 *     @type string $search_terms The search term to use. Defaults to ''.
	 *     @type array  $meta_query   Meta query arguments. See WP_Meta_Query for more details.
	 * }
	 * @return array|bool Array on success. Boolean false on failure.
	 */
	public static function get_current_threads_for_user( $args = array() ) {
		global $wpdb;

		// Backward compatibility with old method of passing arguments
		if ( ! is_array( $args ) || func_num_args() > 1 ) {
			_deprecated_argument( __METHOD__, '2.2.0', sprintf( __( 'Arguments passed to %1$s should be in an associative array. See the inline documentation at %2$s for more details.', 'buddypress' ), __METHOD__, __FILE__ ) );

			$old_args_keys = array(
				0 => 'user_id',
				1 => 'box',
				2 => 'type',
				3 => 'limit',
				4 => 'page',
				5 => 'search_terms',
			);

			$func_args = func_get_args();
			$args      = bp_core_parse_args_array( $old_args_keys, $func_args );
		}

		$defaults = array(
			'user_id'      => false,
			'box'          => 'inbox',
			'type'         => 'all',
			'limit'        => null,
			'page'         => null,
			'search_terms' => '',
			'meta_query'   => array()
		);
		$r = wp_parse_args( $args, $defaults );

		$pag_sql = $type_sql = $search_sql = $user_id_sql = $sender_sql = '';
		$meta_query_sql = array(
			'join'  => '',
			'where' => ''
		);

		if ( $r['limit'] && $r['page'] ) {
			$pag_sql = $wpdb->prepare( " LIMIT %d, %d", intval( ( $r['page'] - 1 ) * $r['limit'] ), intval( $r['limit'] ) );
		}

		if ( $r['type'] == 'unread' ) {
			$type_sql = " AND r.unread_count != 0 ";
		} elseif ( $r['type'] == 'read' ) {
			$type_sql = " AND r.unread_count = 0 ";
		}

		if ( ! empty( $r['search_terms'] ) ) {
			$search_terms_like = '%' . bp_esc_like( $r['search_terms'] ) . '%';
			$search_sql        = $wpdb->prepare( "AND ( subject LIKE %s OR message LIKE %s )", $search_terms_like, $search_terms_like );
		}

		if ( ! empty( $r['user_id'] ) ) {
			if ( 'sentbox' == $r['box'] ) {
				$user_id_sql = 'AND ' . $wpdb->prepare( 'm.sender_id = %d', $r['user_id'] );
				$sender_sql  = ' AND m.sender_id = r.user_id';
			} else {
				$user_id_sql = 'AND ' . $wpdb->prepare( 'r.user_id = %d', $r['user_id'] );
				$sender_sql  = ' AND r.sender_only = 0';
			}
		}

		// Process meta query into SQL
		$meta_query = self::get_meta_query_sql( $r['meta_query'] );
		if ( ! empty( $meta_query['join'] ) ) {
			$meta_query_sql['join'] = $meta_query['join'];
		}
		if ( ! empty( $meta_query['where'] ) ) {
			$meta_query_sql['where'] = $meta_query['where'];
		}

		$bp = buddypress();

		// set up SQL array
		$sql = array();
		$sql['select'] = 'SELECT m.thread_id, MAX(m.date_sent) AS date_sent';
		$sql['from']   = "FROM {$bp->messages->table_name_recipients} r INNER JOIN {$bp->messages->table_name_messages} m ON m.thread_id = r.thread_id {$meta_query_sql['join']}";
		$sql['where']  = "WHERE r.is_deleted = 0 {$user_id_sql} {$sender_sql} {$type_sql} {$search_sql} {$meta_query_sql['where']}";
		$sql['misc']   = "GROUP BY m.thread_id ORDER BY date_sent DESC {$pag_sql}";

		// get thread IDs
		$thread_ids = $wpdb->get_results( implode( ' ', $sql ) );
		if ( empty( $thread_ids ) ) {
			return false;
		}

		// adjust $sql to work for thread total
		$sql['select'] = 'SELECT COUNT( DISTINCT m.thread_id )';
		unset( $sql['misc'] );
		$total_threads = $wpdb->get_var( implode( ' ', $sql ) );

		// Sort threads by date_sent
		foreach( (array) $thread_ids as $thread ) {
			$sorted_threads[$thread->thread_id] = strtotime( $thread->date_sent );
		}

		arsort( $sorted_threads );

		$threads = false;
		foreach ( (array) $sorted_threads as $thread_id => $date_sent ) {
			$threads[] = new BP_Messages_Thread( $thread_id, 'ASC', array(
				'update_meta_cache' => false
			) );
		}

		/**
		 * Filters the results of the query for a user's message threads.
		 *
		 * @since BuddyPress (2.2.0)
		 *
		 * @param array $value {
		 *     @type array $threads       Array of threads. Passed by reference.
		 *     @type int   $total_threads Number of threads found by the query.
		 * }
		 */
		return apply_filters( 'bp_messages_thread_current_threads', array( 'threads' => &$threads, 'total' => (int) $total_threads ) );
	}

	/**
	 * Get the SQL for the 'meta_query' param in BP_Messages_Thread::get_current_threads_for_user().
	 *
	 * We use WP_Meta_Query to do the heavy lifting of parsing the meta_query array
	 * and creating the necessary SQL clauses.
	 *
	 * @since BuddyPress (2.2.0)
	 *
	 * @param array $meta_query An array of meta_query filters. See the
	 *                          documentation for WP_Meta_Query for details.
	 *
	 * @return array $sql_array 'join' and 'where' clauses.
	 */
	public static function get_meta_query_sql( $meta_query = array() ) {
		global $wpdb;

		$sql_array = array(
			'join'  => '',
			'where' => '',
		);

		if ( ! empty( $meta_query ) ) {
			$meta_query = new WP_Meta_Query( $meta_query );

			// WP_Meta_Query expects the table name at
			// $wpdb->messagemeta
			$wpdb->messagemeta = buddypress()->messages->table_name_meta;

			return $meta_query->get_sql( 'message', 'm', 'id' );
		}

		return $sql_array;
	}

	/**
	 * Mark a thread as read.
	 *
	 * @since BuddyPress (1.0.0)
	 *
	 * @param int $thread_id The message thread ID.
	 */
	public static function mark_as_read( $thread_id ) {
		global $wpdb;

		$bp  = buddypress();
		$sql = $wpdb->prepare( "UPDATE {$bp->messages->table_name_recipients} SET unread_count = 0 WHERE user_id = %d AND thread_id = %d", bp_loggedin_user_id(), $thread_id );
		$wpdb->query($sql);

		wp_cache_delete( 'thread_recipients_' . $thread_id, 'bp_messages' );
		wp_cache_delete( bp_loggedin_user_id(), 'bp_messages_unread_count' );
	}

	/**
	 * Mark a thread as unread.
	 *
	 * @since BuddyPress (1.0.0)
	 *
	 * @param int $thread_id The message thread ID.
	 */
	public static function mark_as_unread( $thread_id ) {
		global $wpdb;

		$bp  = buddypress();
		$sql = $wpdb->prepare( "UPDATE {$bp->messages->table_name_recipients} SET unread_count = 1 WHERE user_id = %d AND thread_id = %d", bp_loggedin_user_id(), $thread_id );
		$wpdb->query($sql);

		wp_cache_delete( 'thread_recipients_' . $thread_id, 'bp_messages' );
		wp_cache_delete( bp_loggedin_user_id(), 'bp_messages_unread_count' );
	}

	/**
	 * Returns the total number of message threads for a user.
	 *
	 * @since BuddyPress (1.0.0)
	 *
	 * @param int    $user_id The user ID.
	 * @param string $box     The type of mailbox to get. Either 'inbox' or 'sentbox'.
	 *                        Defaults to 'inbox'.
	 * @param string $type    The type of messages to get. Either 'all' or 'unread'.
	 *                        or 'read'. Defaults to 'all'.
	 * @return int
	 */
	public static function get_total_threads_for_user( $user_id, $box = 'inbox', $type = 'all' ) {
		global $wpdb;

		$exclude_sender = '';
		if ( $box != 'sentbox' )
			$exclude_sender = ' AND sender_only != 1';

		if ( $type == 'unread' )
			$type_sql = " AND unread_count != 0 ";
		elseif ( $type == 'read' )
			$type_sql = " AND unread_count = 0 ";

		$bp = buddypress();

		return (int) $wpdb->get_var( $wpdb->prepare( "SELECT COUNT(thread_id) FROM {$bp->messages->table_name_recipients} WHERE user_id = %d AND is_deleted = 0{$exclude_sender} {$type_sql}", $user_id ) );
	}

	/**
	 * Determine if the logged-in user is a sender of any message in a thread.
	 *
	 * @since BuddyPress (1.0.0)
	 *
	 * @param int $thread_id The message thread ID.
	 *
	 * @return bool
	 */
	public static function user_is_sender( $thread_id ) {
		global $wpdb;

		$bp = buddypress();

		$sender_ids = $wpdb->get_col( $wpdb->prepare( "SELECT sender_id FROM {$bp->messages->table_name_messages} WHERE thread_id = %d", $thread_id ) );

		if ( ! $sender_ids ) {
			return false;
		}

		return in_array( bp_loggedin_user_id(), $sender_ids );
	}

	/**
	 * Returns the userlink of the last sender in a message thread.
	 *
	 * @since BuddyPress (1.0.0)
	 *
	 * @param int $thread_id The message thread ID.
	 *
	 * @return string|bool The user link on success. Boolean false on failure.
	 */
	public static function get_last_sender( $thread_id ) {
		global $wpdb;

		$bp = buddypress();

		if ( ! $sender_id = $wpdb->get_var( $wpdb->prepare( "SELECT sender_id FROM {$bp->messages->table_name_messages} WHERE thread_id = %d GROUP BY sender_id ORDER BY date_sent LIMIT 1", $thread_id ) ) ) {
			return false;
		}

		return bp_core_get_userlink( $sender_id, true );
	}

	/**
	 * Gets the unread message count for a user.
	 *
	 * @since BuddyPress (1.0.0)
	 *
	 * @param int $user_id The user ID.
	 *
	 * @return int
	 */
	public static function get_inbox_count( $user_id = 0 ) {
		global $wpdb;

		if ( empty( $user_id ) ) {
			$user_id = bp_loggedin_user_id();
		}

		$unread_count = wp_cache_get( $user_id, 'bp_messages_unread_count' );

		if ( false === $unread_count ) {
			$bp = buddypress();

			$unread_count = (int) $wpdb->get_var( $wpdb->prepare( "SELECT SUM(unread_count) FROM {$bp->messages->table_name_recipients} WHERE user_id = %d AND is_deleted = 0 AND sender_only = 0", $user_id ) );

			wp_cache_set( $user_id, $unread_count, 'bp_messages_unread_count' );
		}

		/**
		 * Filters a user's unread message count.
		 *
		 * @since BuddyPress (2.2.0)
		 *
		 * @param int $unread_count Unread message count.
		 * @param int $user_id      ID of the user.
		 */
		return apply_filters( 'messages_thread_get_inbox_count', (int) $unread_count, $user_id );
	}

	/**
	 * Checks whether a user is a part of a message thread discussion.
	 *
	 * @since BuddyPress (1.0.0)
	 *
	 * @param int $thread_id The message thread ID.
	 * @param int $user_id   The user ID.
	 *
	 * @return int|null The recorded recipient ID on success, null on failure.
	 */
	public static function check_access( $thread_id, $user_id = 0 ) {
		if ( empty( $user_id ) ) {
			$user_id = bp_loggedin_user_id();
		}

		$recipients = self::get_recipients_for_thread( $thread_id );

		if ( isset( $recipients[$user_id] ) && 0 == $recipients[$user_id]->is_deleted ) {
			return $recipients[$user_id]->id;
		} else {
			return null;
		}
	}

	/**
	 * Checks whether a message thread exists.
	 *
	 * @since BuddyPress (1.0.0)
	 *
	 * @param int $thread_id The message thread ID.
	 *
	 * @return int|null The message thread ID on success, null on failure.
	 */
	public static function is_valid( $thread_id = 0 ) {
		// Bail if no thread ID is passed
		if ( empty( $thread_id ) ) {
			return false;
		}

		$thread = self::get_messages( $thread_id );

		if ( ! empty( $thread ) ) {
			return $thread_id;
		} else {
			return null;
		}
	}

	/**
	 * Returns a string containing all the message recipient userlinks.
	 *
	 * String is comma-delimited.
	 *
	 * If a message thread has more than four users, the returned string is simply
	 * "X Recipients" where "X" is the number of recipients in the message thread.
	 *
	 * @since BuddyPress (1.0.0)
	 *
	 * @param array $recipients Array containing the message recipients (array of objects).
	 *
	 * @return string
	 */
	public static function get_recipient_links( $recipients ) {
		if ( count( $recipients ) >= 5 )
			return sprintf( __( '%s Recipients', 'buddypress' ), number_format_i18n( count( $recipients ) ) );

		$recipient_links = array();

		foreach ( (array) $recipients as $recipient ) {
			$recipient_link = bp_core_get_userlink( $recipient->user_id );

			if ( empty( $recipient_link ) ) {
				$recipient_link = __( 'Deleted User', 'buddypress' );
			}

			$recipient_links[] = $recipient_link;
		}

		return implode( ', ', (array) $recipient_links );
	}

	/**
	 * Upgrade method for the older BP message thread DB table.
	 *
	 * @since BuddyPress (1.2.0)
	 *
	 * @todo We should remove this.  No one is going to upgrade from v1.1, right?
	 * @return bool
	 */
	public static function update_tables() {
		global $wpdb;

		$bp_prefix = bp_core_get_table_prefix();
		$errors    = false;
		$threads   = $wpdb->get_results( "SELECT * FROM {$bp_prefix}bp_messages_threads" );

		// Nothing to update, just return true to remove the table
		if ( empty( $threads ) ) {
			return true;
		}

		$bp = buddypress();

		foreach( (array) $threads as $thread ) {
			$message_ids = maybe_unserialize( $thread->message_ids );

			if ( !empty( $message_ids ) ) {
				$message_ids = implode( ',', $message_ids );

				// Add the thread_id to the messages table
				if ( ! $wpdb->query( $wpdb->prepare( "UPDATE {$bp->messages->table_name_messages} SET thread_id = %d WHERE id IN ({$message_ids})", $thread->id ) ) )
					$errors = true;
			}
		}

		if ( $errors ) {
			return false;
		}

		return true;
	}
}
