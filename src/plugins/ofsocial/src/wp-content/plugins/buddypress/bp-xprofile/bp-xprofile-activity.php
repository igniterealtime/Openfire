<?php
/**
 * BuddyPress XProfile Activity & Notification Functions.
 *
 * These functions handle the recording, deleting and formatting of activity
 * items and notifications for the user and for this specific component.
 *
 * @package BuddyPress
 * @subpackage XProfileActivity
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Register the activity actions for the Extended Profile component.
 *
 * @since 1.0.0
 *
 * @uses bp_activity_set_action() To setup the individual actions.
 */
function xprofile_register_activity_actions() {

	// Register the activity stream actions for this component.
	bp_activity_set_action(
		// Older avatar activity items use 'profile' for component. See r4273.
		'profile',
		'new_avatar',
		__( 'Member changed profile picture', 'buddypress' ),
		'bp_xprofile_format_activity_action_new_avatar',
		__( 'Updated Profile Photos', 'buddypress' )
	);

	bp_activity_set_action(
		buddypress()->profile->id,
		'updated_profile',
		__( 'Updated Profile', 'buddypress' ),
		'bp_xprofile_format_activity_action_updated_profile',
		__( 'Profile Updates', 'buddypress' ),
		array( 'activity' )
	);

	/**
	 * Fires after the registration of default activity actions for Extended Profile component.
	 *
	 * @since 1.1.0
	 */
	do_action( 'xprofile_register_activity_actions' );
}
add_action( 'bp_register_activity_actions', 'xprofile_register_activity_actions' );

/**
 * Format 'new_avatar' activity actions.
 *
 * @since 2.0.0
 *
 * @param string $action   Static activity action.
 * @param object $activity Activity object.
 *
 * @return string
 */
function bp_xprofile_format_activity_action_new_avatar( $action, $activity ) {
	$userlink = bp_core_get_userlink( $activity->user_id );
	$action   = sprintf( __( '%s changed their profile picture', 'buddypress' ), $userlink );

	// Legacy filter - pass $user_id instead of $activity.
	if ( has_filter( 'bp_xprofile_new_avatar_action' ) ) {
		$action = apply_filters( 'bp_xprofile_new_avatar_action', $action, $activity->user_id );
	}

	/**
	 * Filters the formatted 'new_avatar' activity stream action.
	 *
	 * @since 2.0.0
	 *
	 * @param string $action   Formatted action for activity stream.
	 * @param object $activity Activity object.
	 */
	return apply_filters( 'bp_xprofile_format_activity_action_new_avatar', $action, $activity );
}

/**
 * Format 'updated_profile' activity actions.
 *
 * @since 2.0.0
 *
 * @param string $action   Static activity action.
 * @param object $activity Activity object.
 *
 * @return string
 */
function bp_xprofile_format_activity_action_updated_profile( $action, $activity ) {

	// Note for translators: The natural phrasing in English, "Joe updated
	// his profile", requires that we know Joe's gender, which we don't. If
	// your language doesn't have this restriction, feel free to use a more
	// natural translation.
	$profile_link = trailingslashit( bp_core_get_user_domain( $activity->user_id ) . bp_get_profile_slug() );
	$action	      = sprintf( __( "%s's profile was updated", 'buddypress' ), '<a href="' . $profile_link . '">' . bp_core_get_user_displayname( $activity->user_id ) . '</a>' );

	/**
	 * Filters the formatted 'updated_profile' activity stream action.
	 *
	 * @since 2.0.0
	 *
	 * @param string $action   Formatted action for activity stream.
	 * @param object $activity Activity object.
	 */
	return apply_filters( 'bp_xprofile_format_activity_action_updated_profile', $action, $activity );
}

/**
 * Records activity for the logged in user within the profile component so that
 * it will show in the users activity stream (if installed).
 *
 * @since 1.0.0
 *
 * @uses bp_activity_add() Adds an entry to the activity component tables for a specific activity.
 *
 * @param array|string $args String containing all variables used after bp_parse_args() call.
 *
 * @return array
 */
function xprofile_record_activity( $args = '' ) {

	// Bail if activity component is not active.
	if ( ! bp_is_active( 'activity' ) ) {
		return false;
	}

	// Parse the arguments.
	$r = bp_parse_args( $args, array(
		'user_id'           => bp_loggedin_user_id(),
		'action'            => '',
		'content'           => '',
		'primary_link'      => '',
		'component'         => buddypress()->profile->id,
		'type'              => false,
		'item_id'           => false,
		'secondary_item_id' => false,
		'recorded_time'     => bp_core_current_time(),
		'hide_sitewide'     => false
	) );

	return bp_activity_add( $r );
}

/**
 * Deletes activity for a user within the profile component so that it will be
 * removed from the users activity stream and sitewide stream (if installed).
 *
 * @since 1.0.0
 *
 * @uses bp_activity_delete() Deletes an entry to the activity component tables
 *                            for a specific activity.
 *
 * @param array|string $args Containing all variables used after bp_parse_args() call.
 *
 * @return bool
 */
function xprofile_delete_activity( $args = '' ) {

	// Bail if activity component is not active.
	if ( ! bp_is_active( 'activity' ) ) {
		return false;
	}

	// Parse the arguments.
	$r = bp_parse_args( $args, array(
		'component' => buddypress()->profile->id
	), 'xprofile_delete_activity' );

	// Delete the activity item.
	bp_activity_delete_by_item_id( $r );
}

/**
 * Register an activity action for the Extended Profiles component.
 *
 * @since 1.0.0
 *
 * @param string $key Key.
 * @param string $value Value.
 *
 * @return bool True if success, false on failure.
 */
function xprofile_register_activity_action( $key, $value ) {

	// Bail if activity component is not active.
	if ( ! bp_is_active( 'activity' ) ) {
		return false;
	}

	/**
	 * Filters the return value of bp_activity_set_action.
	 *
	 * @since 1.1.0
	 *
	 * @param bool   $value Whether or not an action was successfully registered.
	 * @param string $key   Key used for the registered action.
	 * @param string $value Value used for the registered action.
	 */
	return apply_filters( 'xprofile_register_activity_action', bp_activity_set_action( buddypress()->profile->id, $key, $value ), $key, $value );
}

/**
 * Adds an activity stream item when a user has uploaded a new avatar.
 *
 * @since 1.0.0
 * @since 2.3.4 Add new parameter to get the user id the avatar was set for.
 *
 * @uses bp_activity_add() Adds an entry to the activity component tables for a
 *                         specific activity
 *
 * @param int $user_id The user id the avatar was set for.
 *
 * @return bool
 */
function bp_xprofile_new_avatar_activity( $user_id = 0 ) {

	// Bail if activity component is not active.
	if ( ! bp_is_active( 'activity' ) ) {
		return false;
	}

	if ( empty( $user_id ) ) {
		$user_id = bp_displayed_user_id();
	}

	/**
	 * Filters the user ID when a user has uploaded a new avatar.
	 *
	 * @since 1.5.0
	 *
	 * @param int $user_id ID of the user the avatar was set for.
	 */
	$user_id = apply_filters( 'bp_xprofile_new_avatar_user_id', $user_id );

	// Add the activity.
	bp_activity_add( array(
		'user_id'   => $user_id,
		'component' => 'profile',
		'type'      => 'new_avatar'
	) );
}
add_action( 'xprofile_avatar_uploaded', 'bp_xprofile_new_avatar_activity' );

/**
 * Add an activity item when a user has updated his profile.
 *
 * @since 2.0.0
 *
 * @param int   $user_id    ID of the user who has updated his profile.
 * @param array $field_ids  IDs of the fields submitted.
 * @param bool  $errors     True if validation or saving errors occurred, otherwise false.
 * @param array $old_values Pre-save xprofile field values and visibility levels.
 * @param array $new_values Post-save xprofile field values and visibility levels.
 *
 * @return bool True on success, false on failure.
 */
function bp_xprofile_updated_profile_activity( $user_id, $field_ids = array(), $errors = false, $old_values = array(), $new_values = array() ) {

	// If there were errors, don't post.
	if ( ! empty( $errors ) ) {
		return false;
	}

	// Bail if activity component is not active.
	if ( ! bp_is_active( 'activity' ) ) {
		return false;
	}

	// Don't post if there have been no changes, or if the changes are
	// related solely to non-public fields.
	$public_changes = false;
	foreach ( $new_values as $field_id => $new_value ) {
		$old_value = isset( $old_values[ $field_id ] ) ? $old_values[ $field_id ] : '';

		// Don't register changes to private fields.
		if ( empty( $new_value['visibility'] ) || ( 'public' !== $new_value['visibility'] ) ) {
			continue;
		}

		// Don't register if there have been no changes.
		if ( $new_value === $old_value ) {
			continue;
		}

		// Looks like we have public changes - no need to keep checking.
		$public_changes = true;
		break;
	}

	// Bail if no public changes.
	if ( empty( $public_changes ) ) {
		return false;
	}

	// Throttle to one activity of this type per 2 hours.
	$existing = bp_activity_get( array(
		'max'    => 1,
		'filter' => array(
			'user_id' => $user_id,
			'object'  => buddypress()->profile->id,
			'action'  => 'updated_profile',
		),
	) );

	// Default throttle time is 2 hours. Filter to change (in seconds).
	if ( ! empty( $existing['activities'] ) ) {

		/**
		 * Filters the throttle time, in seconds, used to prevent excessive activity posting.
		 *
		 * @since 2.0.0
		 *
		 * @param int $value Throttle time, in seconds.
		 */
		$throttle_period = apply_filters( 'bp_xprofile_updated_profile_activity_throttle_time', HOUR_IN_SECONDS * 2 );
		$then            = strtotime( $existing['activities'][0]->date_recorded );
		$now             = strtotime( bp_core_current_time() );

		// Bail if throttled.
		if ( ( $now - $then ) < $throttle_period ) {
			return false;
		}
	}

	// If we've reached this point, assemble and post the activity item.
	$profile_link = trailingslashit( bp_core_get_user_domain( $user_id ) . bp_get_profile_slug() );

	return (bool) xprofile_record_activity( array(
		'user_id'      => $user_id,
		'primary_link' => $profile_link,
		'component'    => buddypress()->profile->id,
		'type'         => 'updated_profile',
	) );
}
add_action( 'xprofile_updated_profile', 'bp_xprofile_updated_profile_activity', 10, 5 );

/**
 * Add filters for xprofile activity types to Show dropdowns.
 *
 * @since 2.0.0
 * @todo Mark as deprecated
 */
function xprofile_activity_filter_options() {
	?>

	<option value="updated_profile"><?php _e( 'Profile Updates', 'buddypress' ) ?></option>

	<?php
}
