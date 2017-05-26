<?php
/**
 * BuddyPress XProfile Actions.
 *
 * Action functions are exactly the same as screen functions, however they do not
 * have a template screen associated with them. Usually they will send the user
 * back to the default screen after execution.
 *
 * @package BuddyPress
 * @subpackage XProfileActions
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * This function runs when an action is set for a screen:
 * example.com/members/andy/profile/change-avatar/ [delete-avatar]
 *
 * The function will delete the active avatar for a user.
 *
 * @uses bp_core_delete_avatar() Deletes the active avatar for the logged in user.
 * @uses add_action() Runs a specific function for an action when it fires.
 */
function xprofile_action_delete_avatar() {

	if ( ! bp_is_user_change_avatar() || ! bp_is_action_variable( 'delete-avatar', 0 ) ) {
		return false;
	}

	// Check the nonce.
	check_admin_referer( 'bp_delete_avatar_link' );

	if ( ! bp_is_my_profile() && ! bp_current_user_can( 'bp_moderate' ) ) {
		return false;
	}

	if ( bp_core_delete_existing_avatar( array( 'item_id' => bp_displayed_user_id() ) ) ) {
		bp_core_add_message( __( 'Your profile photo was deleted successfully!', 'buddypress' ) );
	} else {
		bp_core_add_message( __( 'There was a problem deleting your profile photo. Please try again.', 'buddypress' ), 'error' );
	}

	bp_core_redirect( wp_get_referer() );
}
add_action( 'bp_actions', 'xprofile_action_delete_avatar' );

/**
 * Handles the saving of xprofile field visibilities.
 *
 * @since 1.9.0
 */
function bp_xprofile_action_settings() {

	// Bail if not a POST action.
	if ( 'POST' !== strtoupper( $_SERVER['REQUEST_METHOD'] ) ) {
		return;
	}

	// Bail if no submit action.
	if ( ! isset( $_POST['xprofile-settings-submit'] ) ) {
		return;
	}

	// Bail if not in settings.
	if ( ! bp_is_user_settings_profile() ) {
		return;
	}

	// 404 if there are any additional action variables attached
	if ( bp_action_variables() ) {
		bp_do_404();
		return;
	}

	// Nonce check.
	check_admin_referer( 'bp_xprofile_settings' );

	/**
	 * Fires before saving xprofile field visibilities.
	 *
	 * @since 2.0.0
	 */
	do_action( 'bp_xprofile_settings_before_save' );

	/** Save ******************************************************************/

	// Only save if there are field ID's being posted.
	if ( ! empty( $_POST['field_ids'] ) ) {

		// Get the POST'ed field ID's.
		$posted_field_ids = explode( ',', $_POST['field_ids'] );

		// Backward compatibility: a bug in BP 2.0 caused only a single
		// group's field IDs to be submitted. Look for values submitted
		// in the POST request that may not appear in 'field_ids', and
		// add them to the list of IDs to save.
		foreach ( $_POST as $posted_key => $posted_value ) {
			preg_match( '/^field_([0-9]+)_visibility$/', $posted_key, $matches );
			if ( ! empty( $matches[1] ) && ! in_array( $matches[1], $posted_field_ids ) ) {
				$posted_field_ids[] = $matches[1];
			}
		}

		// Save the visibility settings.
		foreach ( $posted_field_ids as $field_id ) {

			$visibility_level = 'public';

			if ( !empty( $_POST['field_' . $field_id . '_visibility'] ) ) {
				$visibility_level = $_POST['field_' . $field_id . '_visibility'];
			}

			xprofile_set_field_visibility_level( $field_id, bp_displayed_user_id(), $visibility_level );
		}
	}

	/** Other *****************************************************************/

	/**
	 * Fires after saving xprofile field visibilities.
	 *
	 * @since 2.0.0
	 */
	do_action( 'bp_xprofile_settings_after_save' );

	// Redirect to the root domain.
	bp_core_redirect( bp_displayed_user_domain() . bp_get_settings_slug() . '/profile' );
}
add_action( 'bp_actions', 'bp_xprofile_action_settings' );
