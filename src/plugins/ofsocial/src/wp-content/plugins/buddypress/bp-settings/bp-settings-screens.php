<?php
/**
 * BuddyPress Settings Screens.
 *
 * @package BuddyPress
 * @subpackage SettingsScreens
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Show the general settings template.
 *
 * @since 1.5.0
 */
function bp_settings_screen_general() {

	if ( bp_action_variables() ) {
		bp_do_404();
		return;
	}

	/**
	 * Filters the template file path to use for the general settings screen.
	 *
	 * @since 1.6.0
	 *
	 * @param string $value Directory path to look in for the template file.
	 */
	bp_core_load_template( apply_filters( 'bp_settings_screen_general_settings', 'members/single/settings/general' ) );
}

/**
 * Show the notifications settings template.
 *
 * @since 1.5.0
 */
function bp_settings_screen_notification() {

	if ( bp_action_variables() ) {
		bp_do_404();
		return;
	}

	/**
	 * Filters the template file path to use for the notification settings screen.
	 *
	 * @since 1.6.0
	 *
	 * @param string $value Directory path to look in for the template file.
	 */
	bp_core_load_template( apply_filters( 'bp_settings_screen_notification_settings', 'members/single/settings/notifications' ) );
}

/**
 * Show the delete-account settings template.
 *
 * @since 1.5.0
 */
function bp_settings_screen_delete_account() {

	if ( bp_action_variables() ) {
		bp_do_404();
		return;
	}

	/**
	 * Filters the template file path to use for the delete-account settings screen.
	 *
	 * @since 1.6.0
	 *
	 * @param string $value Directory path to look in for the template file.
	 */
	bp_core_load_template( apply_filters( 'bp_settings_screen_delete_account', 'members/single/settings/delete-account' ) );
}

/**
 * Show the capabilities settings template.
 *
 * @since 1.6.0
 */
function bp_settings_screen_capabilities() {

	if ( bp_action_variables() ) {
		bp_do_404();
		return;
	}

	/**
	 * Filters the template file path to use for the capabilities settings screen.
	 *
	 * @since 1.6.0
	 *
	 * @param string $value Directory path to look in for the template file.
	 */
	bp_core_load_template( apply_filters( 'bp_settings_screen_capabilities', 'members/single/settings/capabilities' ) );
}
