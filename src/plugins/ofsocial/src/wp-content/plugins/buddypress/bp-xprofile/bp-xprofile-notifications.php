<?php
/** Notifications *************************************************************/

/**
 * Format notifications for the extended profile (Xprofile) component.
 *
 * @since 2.4.0
 *
 * @param string $action            The kind of notification being rendered.
 * @param int    $item_id           The primary item ID.
 * @param int    $secondary_item_id The secondary item ID.
 * @param int    $total_items       The total number of messaging-related notifications
 *                                  waiting for the user.
 * @param string $format            'string' for BuddyBar-compatible notifications; 'array'
 *                                  for WP Toolbar. Default: 'string'.
 *
 * @return string
 */
function xprofile_format_notifications( $action, $item_id, $secondary_item_id, $total_items, $format = 'string' ) {
	switch ( $action ) {
		default:

			/**
			 * Allows plugins to filter extended profile-related custom notifications.
			 * Notifications must have a 'component_name' of 'xprofile' to be routed
			 * to this function.
			 *
			 *
			 * @since 2.4.0
			 *
			 * @param string $notification      Null value.
			 * @param int    $item_id           The primary item ID.
			 * @param int    $secondary_item_id The secondary item ID.
			 * @param int    $total_items       The total number of messaging-related notifications
			 *                                  waiting for the user.
			 * @param string $format            'string' for BuddyBar-compatible notifications;
			 *                                  'array' for WP Toolbar.
			 */
			$custom_action_notification = apply_filters( 'bp_xprofile_' . $action . '_notification', null, $item_id, $secondary_item_id, $total_items, $format );

			if ( ! is_null( $custom_action_notification ) ) {
				return $custom_action_notification;
			}

			break;
	}

	return false;
}