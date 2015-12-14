<?php
/**
 * BuddyPress XProfile Settings.
 *
 * @package    BuddyPress
 * @subpackage XProfileSettings
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Query all profile fields and their visibility data for display in settings.
 *
 * @since 2.0.0
 *
 * @param array|string $args Array of args for the settings fields.
 *
 * @return array
 */
function bp_xprofile_get_settings_fields( $args = '' ) {

	// Parse the possible arguments.
	$r = bp_parse_args( $args, array(
		'user_id'                => bp_displayed_user_id(),
		'profile_group_id'       => false,
		'hide_empty_groups'      => false,
		'hide_empty_fields'      => false,
		'fetch_fields'           => true,
		'fetch_field_data'       => false,
		'fetch_visibility_level' => true,
		'exclude_groups'         => false,
		'exclude_fields'         => false
	), 'xprofile_get_settings_fields' );

	return bp_has_profile( $r );
}

/**
 * Adds feedback messages when successfully saving profile field settings.
 *
 * @since 2.0.0
 *
 * @uses bp_core_add_message()
 * @uses bp_is_my_profile()
 */
function bp_xprofile_settings_add_feedback_message() {

	// Default message type is success.
	$type    = 'success';
	$message = __( 'Your profile settings have been saved.',        'buddypress' );

	// Community moderator editing another user's settings.
	if ( ! bp_is_my_profile() && bp_core_can_edit_settings() ) {
		$message = __( "This member's profile settings have been saved.", 'buddypress' );
	}

	// Add the message.
	bp_core_add_message( $message, $type );
}
add_action( 'bp_xprofile_settings_after_save', 'bp_xprofile_settings_add_feedback_message' );
