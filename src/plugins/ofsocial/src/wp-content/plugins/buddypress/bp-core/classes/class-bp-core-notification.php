<?php
/**
 * Core component classes.
 *
 * @package BuddyPress
 * @subpackage Core
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * BP_Core_Notification is deprecated.
 *
 * Use BP_Notifications_Notification instead.
 *
 * @package BuddyPress Core
 * @deprecated since 1.9.0
 */
class BP_Core_Notification {

	/**
	 * The notification id.
	 *
	 * @var int
	 */
	public $id;

	/**
	 * The ID to which the notification relates to within the component.
	 *
	 * @var int
	 */
	public $item_id;

	/**
	 * The secondary ID to which the notification relates to within the component.
	 *
	 * @var int
	 */
	public $secondary_item_id = null;

	/**
	 * The user ID for who the notification is for.
	 *
	 * @var int
	 */
	public $user_id;

	/**
	 * The name of the component that the notification is for.
	 *
	 * @var string
	 */
	public $component_name;

	/**
	 * The action within the component which the notification is related to.
	 *
	 * @var string
	 */
	public $component_action;

	/**
	 * The date the notification was created.
	 *
	 * @var string
	 */
	public $date_notified;

	/**
	 * Is the notification new or has it already been read.
	 *
	 * @var boolean
	 */
	public $is_new;

	/** Public Methods ********************************************************/

	/**
	 * Constructor
	 *
	 * @param int $id
	 */
	public function __construct( $id = 0 ) {
		if ( !empty( $id ) ) {
			$this->id = $id;
			$this->populate();
		}
	}

	/**
	 * Update or insert notification details into the database.
	 *
	 * @global wpdb $wpdb WordPress database object.
	 *
	 * @return bool Success or failure.
	 */
	public function save() {
		global $wpdb;

		$bp = buddypress();

		// Update
		if ( !empty( $this->id ) ) {
			$sql = $wpdb->prepare( "UPDATE {$bp->core->table_name_notifications} SET item_id = %d, secondary_item_id = %d, user_id = %d, component_name = %s, component_action = %d, date_notified = %s, is_new = %d ) WHERE id = %d", $this->item_id, $this->secondary_item_id, $this->user_id, $this->component_name, $this->component_action, $this->date_notified, $this->is_new, $this->id );

		// Save
		} else {
			$sql = $wpdb->prepare( "INSERT INTO {$bp->core->table_name_notifications} ( item_id, secondary_item_id, user_id, component_name, component_action, date_notified, is_new ) VALUES ( %d, %d, %d, %s, %s, %s, %d )", $this->item_id, $this->secondary_item_id, $this->user_id, $this->component_name, $this->component_action, $this->date_notified, $this->is_new );
		}

		if ( !$result = $wpdb->query( $sql ) )
			return false;

		$this->id = $wpdb->insert_id;

		return true;
	}

	/** Private Methods *******************************************************/

	/**
	 * Fetches the notification data from the database.
	 *
	 * @global wpdb $wpdb WordPress database object.
	 */
	public function populate() {
		global $wpdb;

		$bp = buddypress();

		if ( $notification = $wpdb->get_row( $wpdb->prepare( "SELECT * FROM {$bp->core->table_name_notifications} WHERE id = %d", $this->id ) ) ) {
			$this->item_id = $notification->item_id;
			$this->secondary_item_id = $notification->secondary_item_id;
			$this->user_id           = $notification->user_id;
			$this->component_name    = $notification->component_name;
			$this->component_action  = $notification->component_action;
			$this->date_notified     = $notification->date_notified;
			$this->is_new            = $notification->is_new;
		}
	}

	/** Static Methods ********************************************************/

	/**
	 * Check the access for a user.
	 *
	 * @param int $user_id         ID to check access for.
	 * @param int $notification_id Notification ID to check for.
	 *
	 * @return string
	 */
	public static function check_access( $user_id, $notification_id ) {
		global $wpdb;

		$bp = buddypress();

		return $wpdb->get_var( $wpdb->prepare( "SELECT COUNT(id) FROM {$bp->core->table_name_notifications} WHERE id = %d AND user_id = %d", $notification_id, $user_id ) );
	}

	/**
	 * Fetches all the notifications in the database for a specific user.
	 *
	 * @global wpdb $wpdb WordPress database object
	 *
	 * @param int    $user_id User ID.
	 * @param string $status 'is_new' or 'all'.
	 *
	 * @return array Associative array
	 * @static
	 */
	public static function get_all_for_user( $user_id, $status = 'is_new' ) {
		global $wpdb;

		$bp = buddypress();

		$is_new = ( 'is_new' === $status )
			? ' AND is_new = 1 '
			: '';

 		return $wpdb->get_results( $wpdb->prepare( "SELECT * FROM {$bp->core->table_name_notifications} WHERE user_id = %d {$is_new}", $user_id ) );
	}

	/**
	 * Delete all the notifications for a user based on the component name and action.
	 *
	 * @global wpdb $wpdb WordPress database object.
	 *
	 * @param int    $user_id
	 * @param string $component_name
	 * @param string $component_action
	 *
	 * @static
	 *
	 * @return mixed
	 */
	public static function delete_for_user_by_type( $user_id, $component_name, $component_action ) {
		global $wpdb;

		$bp = buddypress();

		return $wpdb->query( $wpdb->prepare( "DELETE FROM {$bp->core->table_name_notifications} WHERE user_id = %d AND component_name = %s AND component_action = %s", $user_id, $component_name, $component_action ) );
	}

	/**
	 * Delete all the notifications that have a specific item id, component name and action.
	 *
	 * @global wpdb $wpdb WordPress database object.
	 *
	 * @param int      $user_id           The ID of the user who the notifications are for.
	 * @param int      $item_id           The item ID of the notifications we wish to delete.
	 * @param string   $component_name    The name of the component that the notifications we wish to delete.
	 * @param string   $component_action  The action of the component that the notifications we wish to delete.
	 * @param int|bool $secondary_item_id (optional) The secondary item id of the notifications that we wish to
	 *                                    use to delete.
	 * @static
	 *
	 * @return mixed
	 */
	public static function delete_for_user_by_item_id( $user_id, $item_id, $component_name, $component_action, $secondary_item_id = false ) {
		global $wpdb;

		$bp = buddypress();

		$secondary_item_sql = !empty( $secondary_item_id )
			? $wpdb->prepare( " AND secondary_item_id = %d", $secondary_item_id )
			: '';

		return $wpdb->query( $wpdb->prepare( "DELETE FROM {$bp->core->table_name_notifications} WHERE user_id = %d AND item_id = %d AND component_name = %s AND component_action = %s{$secondary_item_sql}", $user_id, $item_id, $component_name, $component_action ) );
	}

	/**
	 * Deletes all the notifications sent by a specific user, by component and action.
	 *
	 * @global wpdb $wpdb WordPress database object.
	 *
	 * @param int    $user_id          The ID of the user whose sent notifications we wish to delete.
	 * @param string $component_name   The name of the component the notification was sent from.
	 * @param string $component_action The action of the component the notification was sent from.
	 * @static
	 *
	 * @return mixed
	 */
	public static function delete_from_user_by_type( $user_id, $component_name, $component_action ) {
		global $wpdb;

		$bp = buddypress();

		return $wpdb->query( $wpdb->prepare( "DELETE FROM {$bp->core->table_name_notifications} WHERE item_id = %d AND component_name = %s AND component_action = %s", $user_id, $component_name, $component_action ) );
	}

	/**
	 * Deletes all the notifications for all users by item id, and optional secondary item id,
	 * and component name and action.
	 *
	 * @global wpdb $wpdb WordPress database object.
	 *
	 * @param string $item_id           The item id that they notifications are to be for.
	 * @param string $component_name    The component that the notifications are to be from.
	 * @param string $component_action  The action that the notifications are to be from.
	 * @param string $secondary_item_id Optional secondary item id that the notifications are to have.
	 * @static
	 *
	 * @return mixed
	 */
	public static function delete_all_by_type( $item_id, $component_name, $component_action, $secondary_item_id ) {
		global $wpdb;

		if ( $component_action )
			$component_action_sql = $wpdb->prepare( "AND component_action = %s", $component_action );
		else
			$component_action_sql = '';

		if ( $secondary_item_id )
			$secondary_item_sql = $wpdb->prepare( "AND secondary_item_id = %d", $secondary_item_id );
		else
			$secondary_item_sql = '';

		$bp = buddypress();

		return $wpdb->query( $wpdb->prepare( "DELETE FROM {$bp->core->table_name_notifications} WHERE item_id = %d AND component_name = %s {$component_action_sql} {$secondary_item_sql}", $item_id, $component_name ) );
	}
}
