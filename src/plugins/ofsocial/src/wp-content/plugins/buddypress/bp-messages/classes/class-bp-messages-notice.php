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
 * BuddyPress Notices class.
 *
 * Use this class to create, activate, deactivate or delete notices.
 *
 * @since 1.0.0
 */
class BP_Messages_Notice {
	/**
	 * The notice ID.
	 *
	 * @var int
	 */
	public $id = null;

	/**
	 * The subject line for the notice.
	 *
	 * @var string
	 */
	public $subject;

	/**
	 * The content of the notice.
	 *
	 * @var string
	 */
	public $message;

	/**
	 * The date the notice was created.
	 *
	 * @var string
	 */
	public $date_sent;

	/**
	 * Whether the notice is active or not.
	 *
	 * @var int
	 */
	public $is_active;

	/**
	 * Constructor.
	 *
	 * @since 1.0.0
	 *
	 * @param int $id Optional. The ID of the current notice.
	 */
	public function __construct( $id = null ) {
		if ( $id ) {
			$this->id = $id;
			$this->populate();
		}
	}

	/**
	 * Populate method.
	 *
	 * Runs during constructor.
	 *
	 * @since 1.0.0
	 */
	public function populate() {
		global $wpdb;

		$bp = buddypress();

		$notice = $wpdb->get_row( $wpdb->prepare( "SELECT * FROM {$bp->messages->table_name_notices} WHERE id = %d", $this->id ) );

		if ( $notice ) {
			$this->subject   = $notice->subject;
			$this->message   = $notice->message;
			$this->date_sent = $notice->date_sent;
			$this->is_active = $notice->is_active;
		}
	}

	/**
	 * Saves a notice.
	 *
	 * @since 1.0.0
	 *
	 * @return bool
	 */
	public function save() {
		global $wpdb;

		$bp = buddypress();

		$this->subject = apply_filters( 'messages_notice_subject_before_save', $this->subject, $this->id );
		$this->message = apply_filters( 'messages_notice_message_before_save', $this->message, $this->id );

		/**
		 * Fires before the current message notice item gets saved.
		 *
		 * Please use this hook to filter the properties above. Each part will be passed in.
		 *
		 * @since 1.0.0
		 *
		 * @param BP_Messages_Notice $this Current instance of the message notice item being saved. Passed by reference.
		 */
		do_action_ref_array( 'messages_notice_before_save', array( &$this ) );

		if ( empty( $this->id ) ) {
			$sql = $wpdb->prepare( "INSERT INTO {$bp->messages->table_name_notices} (subject, message, date_sent, is_active) VALUES (%s, %s, %s, %d)", $this->subject, $this->message, $this->date_sent, $this->is_active );
		} else {
			$sql = $wpdb->prepare( "UPDATE {$bp->messages->table_name_notices} SET subject = %s, message = %s, is_active = %d WHERE id = %d", $this->subject, $this->message, $this->is_active, $this->id );
		}

		if ( ! $wpdb->query( $sql ) ) {
			return false;
		}

		if ( ! $id = $this->id ) {
			$id = $wpdb->insert_id;
		}

		// Now deactivate all notices apart from the new one.
		$wpdb->query( $wpdb->prepare( "UPDATE {$bp->messages->table_name_notices} SET is_active = 0 WHERE id != %d", $id ) );

		bp_update_user_last_activity( bp_loggedin_user_id(), bp_core_current_time() );

		/**
		 * Fires after the current message notice item has been saved.
		 *
		 * @since 1.0.0
		 *
		 * @param BP_Messages_Notice $this Current instance of the message item being saved. Passed by reference.
		 */
		do_action_ref_array( 'messages_notice_after_save', array( &$this ) );

		return true;
	}

	/**
	 * Activates a notice.
	 *
	 * @since 1.0.0
	 *
	 * @return bool
	 */
	public function activate() {
		$this->is_active = 1;
		return (bool) $this->save();
	}

	/**
	 * Deactivates a notice.
	 *
	 * @since 1.0.0
	 *
	 * @return bool
	 */
	public function deactivate() {
		$this->is_active = 0;
		return (bool) $this->save();
	}

	/**
	 * Deletes a notice.
	 *
	 * @since 1.0.0
	 *
	 * @return bool
	 */
	public function delete() {
		global $wpdb;

		/**
		 * Fires before the current message item has been deleted.
		 *
		 * @since 1.0.0
		 *
		 * @param BP_Messages_Notice $this Current instance of the message notice item being deleted.
		 */
		do_action( 'messages_notice_before_delete', $this );

		$bp  = buddypress();
		$sql = $wpdb->prepare( "DELETE FROM {$bp->messages->table_name_notices} WHERE id = %d", $this->id );

		if ( ! $wpdb->query( $sql ) ) {
			return false;
		}

		return true;
	}

	/** Static Methods ********************************************************/

	/**
	 * Pulls up a list of notices.
	 *
	 * To get all notices, pass a value of -1 to pag_num.
	 *
	 * @since 1.0.0
	 *
	 * @param array $args {
	 *     Array of parameters.
	 *     @type int $pag_num  Number of notices per page. Defaults to 20.
	 *     @type int $pag_page The page number.  Defaults to 1.
	 * }
	 * @return object List of notices to display.
	 */
	public static function get_notices( $args = array() ) {
		global $wpdb;

		$r = wp_parse_args( $args, array(
			'pag_num'  => 20, // Number of notices per page.
			'pag_page' => 1   // Page number.
		) );

		$limit_sql = '';
		if ( (int) $r['pag_num'] >= 0 ) {
			$limit_sql = $wpdb->prepare( "LIMIT %d, %d", (int) ( ( $r['pag_page'] - 1 ) * $r['pag_num'] ), (int) $r['pag_num'] );
		}

		$bp = buddypress();

		$notices = $wpdb->get_results( "SELECT * FROM {$bp->messages->table_name_notices} ORDER BY date_sent DESC {$limit_sql}" );

		return $notices;
	}

	/**
	 * Returns the total number of recorded notices.
	 *
	 * @since 1.0.0
	 *
	 * @return int
	 */
	public static function get_total_notice_count() {
		global $wpdb;

		$bp = buddypress();

		$notice_count = $wpdb->get_var( "SELECT COUNT(id) FROM {$bp->messages->table_name_notices}" );

		return $notice_count;
	}

	/**
	 * Returns the active notice that should be displayed on the frontend.
	 *
	 * @since 1.0.0
	 *
	 * @return object The BP_Messages_Notice object.
	 */
	public static function get_active() {
		$notice = wp_cache_get( 'active_notice', 'bp_messages' );

		if ( false === $notice ) {
			global $wpdb;

			$bp = buddypress();

			$notice_id = $wpdb->get_var( "SELECT id FROM {$bp->messages->table_name_notices} WHERE is_active = 1" );
			$notice    = new BP_Messages_Notice( $notice_id );

			wp_cache_set( 'active_notice', $notice, 'bp_messages' );
		}

		return $notice;
	}
}
