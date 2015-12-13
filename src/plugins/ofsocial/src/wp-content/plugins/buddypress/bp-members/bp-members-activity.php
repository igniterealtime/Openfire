<?php
/**
 * BuddyPress Member Activity
 *
 * @package BuddyPress
 * @subpackage MembersActivity
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Register the 'new member' activity type.
 *
 * @since 2.2.0
 *
 * @uses bp_activity_set_action()
 * @uses buddypress()
 */
function bp_members_register_activity_actions() {

	bp_activity_set_action(
		buddypress()->members->id,
		'new_member',
		__( 'New member registered', 'buddypress' ),
		'bp_members_format_activity_action_new_member',
		__( 'New Members', 'buddypress' ),
		array( 'activity' )
	);

	/**
	 * Fires after the default 'new member' activity types are registered.
	 *
	 * @since 2.2.0
	 */
	do_action( 'bp_members_register_activity_actions' );
}
add_action( 'bp_register_activity_actions', 'bp_members_register_activity_actions' );

/**
 * Format 'new_member' activity actions.
 *
 * @since 2.2.0
 *
 * @param string $action   Static activity action.
 * @param object $activity Activity object.
 * @return string $action
 */
function bp_members_format_activity_action_new_member( $action, $activity ) {
	$userlink = bp_core_get_userlink( $activity->user_id );
	$action   = sprintf( __( '%s became a registered member', 'buddypress' ), $userlink );

	// Legacy filter - pass $user_id instead of $activity.
	if ( has_filter( 'bp_core_activity_registered_member_action' ) ) {
		$action = apply_filters( 'bp_core_activity_registered_member_action', $action, $activity->user_id );
	}

	/**
	 * Filters the formatted 'new member' activity actions.
	 *
	 * @since 2.2.0
	 *
	 * @param string $action   Static activity action.
	 * @param object $activity Activity object.
	 */
	return apply_filters( 'bp_members_format_activity_action_new_member', $action, $activity );
}

/**
 * Create a "became a registered user" activity item when a user activates his account.
 *
 * @param array $user Array of userdata passed to bp_core_activated_user hook.
 * @return bool
 */
function bp_core_new_user_activity( $user ) {
	if ( empty( $user ) ) {
		return false;
	}

	if ( is_array( $user ) ) {
		$user_id = $user['user_id'];
	} else {
		$user_id = $user;
	}

	if ( empty( $user_id ) ) {
		return false;
	}

	bp_activity_add( array(
		'user_id'   => $user_id,
		'component' => buddypress()->members->id,
		'type'      => 'new_member'
	) );
}
add_action( 'bp_core_activated_user', 'bp_core_new_user_activity' );
