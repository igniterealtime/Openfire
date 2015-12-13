<?php
/**
 * BuddyPress Member Notifications Functions.
 *
 * Functions and filters used in the Notifications component.
 *
 * @package BuddyPress
 * @subpackage NotificationsFunctions
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Add a notification for a specific user, from a specific component.
 *
 * @since 1.9.0
 *
 * @param array $args {
 *     Array of arguments describing the notification. All are optional.
 *     @type int    $user_id           ID of the user to associate the notification with.
 *     @type int    $item_id           ID of the item to associate the notification with.
 *     @type int    $secondary_item_id ID of the secondary item to associate the
 *                                     notification with.
 *     @type string $component_name    Name of the component to associate the
 *                                     notification with.
 *     @type string $component_action  Name of the action to associate the
 *                                     notification with.
 *     @type string $date_notified     Timestamp for the notification.
 * }
 * @return int|bool ID of the newly created notification on success, false
 *                  on failure.
 */
function bp_notifications_add_notification( $args = array() ) {

	$r = bp_parse_args( $args, array(
		'user_id'           => 0,
		'item_id'           => 0,
		'secondary_item_id' => 0,
		'component_name'    => '',
		'component_action'  => '',
		'date_notified'     => bp_core_current_time(),
		'is_new'            => 1,
		'allow_duplicate'   => false,
	), 'notifications_add_notification' );;

	// Check for existing duplicate notifications.
	if ( ! $r['allow_duplicate'] ) {
		// Date_notified, allow_duplicate don't count toward
		// duplicate status.
		$existing = BP_Notifications_Notification::get( array(
			'user_id'           => $r['user_id'],
			'item_id'           => $r['item_id'],
			'secondary_item_id' => $r['secondary_item_id'],
			'component_name'    => $r['component_name'],
			'component_action'  => $r['component_action'],
			'is_new'            => $r['is_new'],
		) );

		if ( ! empty( $existing ) ) {
			return false;
		}
	}

	// Setup the new notification.
	$notification                    = new BP_Notifications_Notification;
	$notification->user_id           = $r['user_id'];
	$notification->item_id           = $r['item_id'];
	$notification->secondary_item_id = $r['secondary_item_id'];
	$notification->component_name    = $r['component_name'];
	$notification->component_action  = $r['component_action'];
	$notification->date_notified     = $r['date_notified'];
	$notification->is_new            = $r['is_new'];

	// Save the new notification.
	return $notification->save();
}

/**
 * Get a specific notification by its ID.
 *
 * @since 1.9.0
 *
 * @param int $id ID of the notification.
 * @return BP_Notifications_Notification Notification object for ID specified.
 */
function bp_notifications_get_notification( $id ) {
	return new BP_Notifications_Notification( $id );
}

/**
 * Delete a specific notification by its ID.
 *
 * @since 1.9.0
 *
 * @param int $id ID of the notification to delete.
 * @return bool True on success, false on failure.
 */
function bp_notifications_delete_notification( $id ) {
	if ( ! bp_notifications_check_notification_access( bp_loggedin_user_id(), $id ) ) {
		return false;
	}

	return BP_Notifications_Notification::delete( array( 'id' => $id ) );
}

/**
 * Mark notification read/unread for a user by ID.
 *
 * Used when clearing out notifications for a specific notification item.
 *
 * @since 1.9.0
 *
 * @param int      $id     ID of the notification.
 * @param int|bool $is_new 0 for read, 1 for unread.
 * @return bool True on success, false on failure.
 */
function bp_notifications_mark_notification( $id, $is_new = false ) {
	if ( ! bp_notifications_check_notification_access( bp_loggedin_user_id(), $id ) ) {
		return false;
	}

	return BP_Notifications_Notification::update(
		array( 'is_new' => $is_new ),
		array( 'id'     => $id     )
	);
}

/**
 * Get all notifications for a user and cache them.
 *
 * @since 2.1.0
 *
 * @param int $user_id ID of the user whose notifications are being fetched.
 * @return array $notifications Array of notifications for user.
 */
function bp_notifications_get_all_notifications_for_user( $user_id = 0 ) {

	// Default to displayed user if no ID is passed.
	if ( empty( $user_id ) ) {
		$user_id = ( bp_displayed_user_id() ) ? bp_displayed_user_id() : bp_loggedin_user_id();
	}

	// Get notifications out of the cache, or query if necessary.
	$notifications = wp_cache_get( 'all_for_user_' . $user_id, 'bp_notifications' );
	if ( false === $notifications ) {
		$notifications = BP_Notifications_Notification::get( array(
			'user_id' => $user_id
		) );
		wp_cache_set( 'all_for_user_' . $user_id, $notifications, 'bp_notifications' );
	}

	/**
	 * Filters all notifications for a user.
	 *
	 * @since 2.1.0
	 *
	 * @param array $notifications Array of notifications for user.
	 * @param int   $user_id       ID of the user being fetched.
	 */
	return apply_filters( 'bp_notifications_get_all_notifications_for_user', $notifications, $user_id );
}

/**
 * Get notifications for a specific user.
 *
 * @since 1.9.0
 *
 * @param int    $user_id ID of the user whose notifications are being fetched.
 * @param string $format  Format of the returned values. 'string' returns HTML,
 *                        while 'object' returns a structured object for parsing.
 * @return mixed Object or array on success, false on failure.
 */
function bp_notifications_get_notifications_for_user( $user_id, $format = 'string' ) {

	// Setup local variables.
	$bp = buddypress();

	// Get notifications (out of the cache, or query if necessary).
	$notifications         = bp_notifications_get_all_notifications_for_user( $user_id );
	$grouped_notifications = array(); // Notification groups.
	$renderable            = array(); // Renderable notifications.

	// Group notifications by component and component_action and provide totals.
	for ( $i = 0, $count = count( $notifications ); $i < $count; ++$i ) {
		$notification = $notifications[$i];
		$grouped_notifications[$notification->component_name][$notification->component_action][] = $notification;
	}

	// Bail if no notification groups.
	if ( empty( $grouped_notifications ) ) {
		return false;
	}

	// Calculate a renderable output for each notification type.
	foreach ( $grouped_notifications as $component_name => $action_arrays ) {

		// We prefer that extended profile component-related notifications use
		// the component_name of 'xprofile'. However, the extended profile child
		// object in the $bp object is keyed as 'profile', which is where we need
		// to look for the registered notification callback.
		if ( 'xprofile' == $component_name ) {
			$component_name = 'profile';
		}

		// Skip if group is empty.
		if ( empty( $action_arrays ) ) {
			continue;
		}

		// Loop through each actionable item and try to map it to a component.
		foreach ( (array) $action_arrays as $component_action_name => $component_action_items ) {

			// Get the number of actionable items.
			$action_item_count = count( $component_action_items );

			// Skip if the count is less than 1.
			if ( $action_item_count < 1 ) {
				continue;
			}

			// Callback function exists.
			if ( isset( $bp->{$component_name}->notification_callback ) && is_callable( $bp->{$component_name}->notification_callback ) ) {

				// Function should return an object.
				if ( 'object' === $format ) {

					// Retrieve the content of the notification using the callback.
					$content = call_user_func(
						$bp->{$component_name}->notification_callback,
						$component_action_name,
						$component_action_items[0]->item_id,
						$component_action_items[0]->secondary_item_id,
						$action_item_count,
						'array'
					);

					// Create the object to be returned.
					$notification_object = $component_action_items[0];

					// Minimal backpat with non-compatible notification
					// callback functions.
					if ( is_string( $content ) ) {
						$notification_object->content = $content;
						$notification_object->href    = bp_loggedin_user_domain();
					} else {
						$notification_object->content = $content['text'];
						$notification_object->href    = $content['link'];
					}

					$renderable[] = $notification_object;

				// Return an array of content strings.
				} else {
					$content      = call_user_func( $bp->{$component_name}->notification_callback, $component_action_name, $component_action_items[0]->item_id, $component_action_items[0]->secondary_item_id, $action_item_count );
					$renderable[] = $content;
				}

			// @deprecated format_notification_function - 1.5
			} elseif ( isset( $bp->{$component_name}->format_notification_function ) && function_exists( $bp->{$component_name}->format_notification_function ) ) {
				$renderable[] = call_user_func( $bp->{$component_name}->format_notification_function, $component_action_name, $component_action_items[0]->item_id, $component_action_items[0]->secondary_item_id, $action_item_count );

			// Allow non BuddyPress components to hook in.
			} else {

				// The array to reference with apply_filters_ref_array().
				$ref_array = array(
					$component_action_name,
					$component_action_items[0]->item_id,
					$component_action_items[0]->secondary_item_id,
					$action_item_count,
					$format
				);

				// Function should return an object.
				if ( 'object' === $format ) {

					/**
					 * Filters the notifications for a user.
					 *
					 * @since 1.9.0
					 *
					 * @param array $ref_array Array of properties for the current notification being rendered.
					 */
					$content = apply_filters_ref_array( 'bp_notifications_get_notifications_for_user', $ref_array );

					// Create the object to be returned.
					$notification_object = $component_action_items[0];

					// Minimal backpat with non-compatible notification
					// callback functions.
					if ( is_string( $content ) ) {
						$notification_object->content = $content;
						$notification_object->href    = bp_loggedin_user_domain();
					} else {
						$notification_object->content = $content['text'];
						$notification_object->href    = $content['link'];
					}

					$renderable[] = $notification_object;

				// Return an array of content strings.
				} else {

					/** This filters is documented in bp-notifications/bp-notifications-functions.php */
					$renderable[] = apply_filters_ref_array( 'bp_notifications_get_notifications_for_user', $ref_array );
				}
			}
		}
	}

	// If renderable is empty array, set to false.
	if ( empty( $renderable ) ) {
		$renderable = false;
	}

	/**
	 * Filters the final array of notifications to be displayed for a user.
	 *
	 * @since 1.6.0
	 *
	 * @param array|bool $renderable Array of notifications to render or false if no notifications.
	 * @param int        $user_id    ID of the user whose notifications are being displayed.
	 * @param string     $format     Display format requested for the notifications.
	 */
	return apply_filters( 'bp_core_get_notifications_for_user', $renderable, $user_id, $format );
}

/** Delete ********************************************************************/

/**
 * Delete notifications for a user by type.
 *
 * Used when clearing out notifications for a specific component when the user
 * has visited that component.
 *
 * @since 1.9.0
 *
 * @param int    $user_id          ID of the user whose notifications are being deleted.
 * @param string $component_name   Name of the associated component.
 * @param string $component_action Name of the associated action.
 * @return bool True on success, false on failure.
 */
function bp_notifications_delete_notifications_by_type( $user_id, $component_name, $component_action ) {
	return BP_Notifications_Notification::delete( array(
		'user_id'          => $user_id,
		'component_name'   => $component_name,
		'component_action' => $component_action,
	) );
}

/**
 * Delete notifications for an item ID.
 *
 * Used when clearing out notifications for a specific component when the user
 * has visited that component.
 *
 * @since 1.9.0
 *
 * @param int      $user_id           ID of the user whose notifications are being deleted.
 * @param int      $item_id           ID of the associated item.
 * @param string   $component_name    Name of the associated component.
 * @param string   $component_action  Name of the associated action.
 * @param int|bool $secondary_item_id ID of the secondary associated item.
 * @return bool True on success, false on failure.
 */
function bp_notifications_delete_notifications_by_item_id( $user_id, $item_id, $component_name, $component_action, $secondary_item_id = false ) {
	return BP_Notifications_Notification::delete( array(
		'user_id'           => $user_id,
		'item_id'           => $item_id,
		'secondary_item_id' => $secondary_item_id,
		'component_name'    => $component_name,
		'component_action'  => $component_action,
	) );
}

/**
 * Delete all notifications by type.
 *
 * Used when clearing out notifications for an entire component.
 *
 * @since 1.9.0
 *
 * @param int         $item_id           ID of the user whose notifications are being deleted.
 * @param string      $component_name    Name of the associated component.
 * @param string|bool $component_action  Optional. Name of the associated action.
 * @param int|bool    $secondary_item_id Optional. ID of the secondary associated item.
 * @return bool True on success, false on failure.
 */
function bp_notifications_delete_all_notifications_by_type( $item_id, $component_name, $component_action = false, $secondary_item_id = false ) {
	return BP_Notifications_Notification::delete( array(
		'item_id'           => $item_id,
		'secondary_item_id' => $secondary_item_id,
		'component_name'    => $component_name,
		'component_action'  => $component_action,
	) );
}

/**
 * Delete all notifications from a user.
 *
 * Used when clearing out all notifications for a user, when deleted or spammed.
 *
 * @todo This function assumes that items with the user_id in the item_id slot
 *       are associated with that user. However, this will only be true with
 *       certain components (such as Friends). Use with caution!
 *
 * @since 1.9.0
 *
 * @param int    $user_id          ID of the user whose associated items are being deleted.
 * @param string $component_name   Name of the associated component.
 * @param string $component_action Name of the associated action.
 * @return bool True on success, false on failure.
 */
function bp_notifications_delete_notifications_from_user( $user_id, $component_name, $component_action ) {
	return BP_Notifications_Notification::delete( array(
		'item_id'           => $user_id,
		'component_name'    => $component_name,
		'component_action'  => $component_action,
	) );
}

/** Mark **********************************************************************/

/**
 * Mark notifications read/unread for a user by type.
 *
 * Used when clearing out notifications for a specific component when the user
 * has visited that component.
 *
 * @since 1.9.0
 *
 * @param int      $user_id          ID of the user whose notifications are being deleted.
 * @param string   $component_name   Name of the associated component.
 * @param string   $component_action Name of the associated action.
 * @param int|bool $is_new           0 for read, 1 for unread.
 * @return bool True on success, false on failure.
 */
function bp_notifications_mark_notifications_by_type( $user_id, $component_name, $component_action, $is_new = false ) {
	return BP_Notifications_Notification::update(
		array(
			'is_new' => $is_new
		),
		array(
			'user_id'          => $user_id,
			'component_name'   => $component_name,
			'component_action' => $component_action
		)
	);
}

/**
 * Mark notifications read/unread for an item ID.
 *
 * Used when clearing out notifications for a specific component when the user
 * has visited that component.
 *
 * @since 1.9.0
 *
 * @param int      $user_id           ID of the user whose notifications are being deleted.
 * @param int      $item_id           ID of the associated item.
 * @param string   $component_name    Name of the associated component.
 * @param string   $component_action  Name of the associated action.
 * @param int|bool $secondary_item_id ID of the secondary associated item.
 * @param int|bool $is_new            0 for read, 1 for unread.
 * @return bool True on success, false on failure.
 */
function bp_notifications_mark_notifications_by_item_id( $user_id, $item_id, $component_name, $component_action, $secondary_item_id = false, $is_new = false ) {
	return BP_Notifications_Notification::update(
		array(
			'is_new' => $is_new
		),
		array(
			'user_id'           => $user_id,
			'item_id'           => $item_id,
			'secondary_item_id' => $secondary_item_id,
			'component_name'    => $component_name,
			'component_action'  => $component_action
		)
	);
}

/**
 * Mark all notifications read/unread by type.
 *
 * Used when clearing out notifications for an entire component.
 *
 * @since 1.9.0
 *
 * @param int         $item_id           ID of the user whose notifications are being deleted.
 * @param string      $component_name    Name of the associated component.
 * @param string|bool $component_action  Optional. Name of the associated action.
 * @param int|bool    $secondary_item_id Optional. ID of the secondary associated item.
 * @param int|bool    $is_new            0 for read, 1 for unread.
 * @return bool True on success, false on failure.
 */
function bp_notifications_mark_all_notifications_by_type( $item_id, $component_name, $component_action = false, $secondary_item_id = false, $is_new = false ) {
	return BP_Notifications_Notification::update(
		array(
			'is_new' => $is_new
		),
		array(
			'item_id'           => $item_id,
			'secondary_item_id' => $secondary_item_id,
			'component_name'    => $component_name,
			'component_action'  => $component_action
		)
	);
}

/**
 * Mark all notifications read/unread from a user.
 *
 * Used when clearing out all notifications for a user, when deleted or spammed.
 *
 * @todo This function assumes that items with the user_id in the item_id slot
 *       are associated with that user. However, this will only be true with
 *       certain components (such as Friends). Use with caution!
 *
 * @since 1.9.0
 *
 * @param int      $user_id          ID of the user whose associated items are being deleted.
 * @param string   $component_name   Name of the associated component.
 * @param string   $component_action Name of the associated action.
 * @param int|bool $is_new           0 for read, 1 for unread.
 * @return bool True on success, false on failure.
 */
function bp_notifications_mark_notifications_from_user( $user_id, $component_name, $component_action, $is_new = false ) {
	return BP_Notifications_Notification::update(
		array(
			'is_new' => $is_new
		),
		array(
			'item_id'          => $user_id,
			'component_name'   => $component_name,
			'component_action' => $component_action
		)
	);
}

/** Helpers *******************************************************************/

/**
 * Check if a user has access to a specific notification.
 *
 * Used before deleting a notification for a user.
 *
 * @since 1.9.0
 *
 * @param int $user_id         ID of the user being checked.
 * @param int $notification_id ID of the notification being checked.
 * @return bool True if the notification belongs to the user, otherwise false.
 */
function bp_notifications_check_notification_access( $user_id, $notification_id ) {
	return (bool) BP_Notifications_Notification::check_access( $user_id, $notification_id );
}

/**
 * Get a count of unread notification items for a user.
 *
 * @since 1.9.0
 *
 * @param int $user_id ID of the user whose unread notifications are being
 *                     counted.
 * @return int Unread notification count.
 */
function bp_notifications_get_unread_notification_count( $user_id = 0 ) {
	$notifications = bp_notifications_get_all_notifications_for_user( $user_id );
	$count         = ! empty( $notifications ) ? count( $notifications ) : 0;

	/**
	 * Filters the count of unread notification items for a user.
	 *
	 * @since 1.9.0
	 *
	 * @param int $count Count of unread notification items for a user.
	 */
	return apply_filters( 'bp_notifications_get_total_notification_count', (int) $count );
}

/**
 * Return an array of component names that are currently active and have
 * registered Notifications callbacks.
 *
 * @since 1.9.1
 *
 * @see http://buddypress.trac.wordpress.org/ticket/5300
 *
 * @return array $component_names Array of registered components.
 */
function bp_notifications_get_registered_components() {

	// Load BuddyPress.
	$bp = buddypress();

	// Setup return value.
	$component_names = array();

	// Get the active components.
	$active_components = array_keys( $bp->active_components );

	// Loop through components, look for callbacks, add to return value.
	foreach ( $active_components as $component ) {
		if ( !empty( $bp->$component->notification_callback ) ) {
			$component_names[] = $component;
		}
		// The extended profile component is identified in the active_components array as 'xprofile'.
		// However, the extended profile child object has the key 'profile' in the $bp object.
		if ( 'xprofile' == $component && ! empty( $bp->profile->notification_callback ) ) {
			$component_names[] = $component;
		}
	}

	/**
	 * Filters active components with registered notifications callbacks.
	 *
	 * @since 1.9.1
	 *
	 * @param array $component_names   Array of registered component names.
	 * @param array $active_components Array of active components.
	 */
	return apply_filters( 'bp_notifications_get_registered_components', $component_names, $active_components );
}

/** Meta **********************************************************************/

/**
 * Delete a meta entry from the DB for a notification item.
 *
 * @since 2.3.0
 *
 * @global object $wpdb WordPress database access object.
 *
 * @param int    $notification_id ID of the notification item whose metadata is being deleted.
 * @param string $meta_key        Optional. The key of the metadata being deleted. If
 *                                omitted, all metadata associated with the notification
 *                                item will be deleted.
 * @param string $meta_value      Optional. If present, the metadata will only be
 *                                deleted if the meta_value matches this parameter.
 * @param bool   $delete_all      Optional. If true, delete matching metadata entries
 *                                for all objects, ignoring the specified object_id. Otherwise,
 *                                only delete matching metadata entries for the specified
 *                                notification item. Default: false.
 * @return bool                   True on success, false on failure.
 */
function bp_notifications_delete_meta( $notification_id, $meta_key = '', $meta_value = '', $delete_all = false ) {

	// Legacy - if no meta_key is passed, delete all for the item.
	if ( empty( $meta_key ) ) {
		$all_meta = bp_notifications_get_meta( $notification_id );
		$keys     = ! empty( $all_meta )
			? array_keys( $all_meta )
			: array();

		// With no meta_key, ignore $delete_all.
		$delete_all = false;
	} else {
		$keys = array( $meta_key );
	}

	$retval = true;

	add_filter( 'query', 'bp_filter_metaid_column_name' );
	foreach ( $keys as $key ) {
		$retval = delete_metadata( 'notifications', $notification_id, $key, $meta_value, $delete_all );
	}
	remove_filter( 'query', 'bp_filter_metaid_column_name' );

	return $retval;
}

/**
 * Get metadata for a given notification item.
 *
 * @since 2.3.0
 *
 * @uses apply_filters() To call the 'bp_notifications_get_meta' hook.
 *
 * @param int    $notification_id ID of the notification item whose metadata is being requested.
 * @param string $meta_key        Optional. If present, only the metadata matching
 *                                that meta key will be returned. Otherwise, all metadata for the
 *                                notification item will be fetched.
 * @param bool   $single          Optional. If true, return only the first value of the
 *                                specified meta_key. This parameter has no effect if meta_key is not
 *                                specified. Default: true.
 * @return mixed                  The meta value(s) being requested.
 */
function bp_notifications_get_meta( $notification_id = 0, $meta_key = '', $single = true ) {
	add_filter( 'query', 'bp_filter_metaid_column_name' );
	$retval = get_metadata( 'notifications', $notification_id, $meta_key, $single );
	remove_filter( 'query', 'bp_filter_metaid_column_name' );

	/**
	 * Filters the metadata for a specified notification item.
	 *
	 * @since 2.3.0
	 *
	 * @param mixed  $retval          The meta values for the notification item.
	 * @param int    $notification_id ID of the notification item.
	 * @param string $meta_key        Meta key for the value being requested.
	 * @param bool   $single          Whether to return one matched meta key row or all.
	 */
	return apply_filters( 'bp_notifications_get_meta', $retval, $notification_id, $meta_key, $single );
}

/**
 * Update a piece of notification meta.
 *
 * @since 1.2.0
 *
 * @param  int    $notification_id ID of the notification item whose metadata is being
 *                                 updated.
 * @param  string $meta_key        Key of the metadata being updated.
 * @param  mixed  $meta_value      Value to be set.
 * @param  mixed  $prev_value      Optional. If specified, only update existing
 *                                 metadata entries with the specified value.
 *                                 Otherwise, update all entries.
 * @return bool|int                Returns false on failure. On successful
 *                                 update of existing metadata, returns true. On
 *                                 successful creation of new metadata,  returns
 *                                 the integer ID of the new metadata row.
 */
function bp_notifications_update_meta( $notification_id, $meta_key, $meta_value, $prev_value = '' ) {
	add_filter( 'query', 'bp_filter_metaid_column_name' );
	$retval = update_metadata( 'notifications', $notification_id, $meta_key, $meta_value, $prev_value );
	remove_filter( 'query', 'bp_filter_metaid_column_name' );

	return $retval;
}

/**
 * Add a piece of notification metadata.
 *
 * @since 2.3.0
 *
 * @param int    $notification_id ID of the notification item.
 * @param string $meta_key        Metadata key.
 * @param mixed  $meta_value      Metadata value.
 * @param bool   $unique          Optional. Whether to enforce a single metadata value
 *                                for the given key. If true, and the object already has a value for
 *                                the key, no change will be made. Default: false.
 * @return int|bool               The meta ID on successful update, false on failure.
 */
function bp_notifications_add_meta( $notification_id, $meta_key, $meta_value, $unique = false ) {
	add_filter( 'query', 'bp_filter_metaid_column_name' );
	$retval = add_metadata( 'notifications', $notification_id, $meta_key, $meta_value, $unique );
	remove_filter( 'query', 'bp_filter_metaid_column_name' );

	return $retval;
}
