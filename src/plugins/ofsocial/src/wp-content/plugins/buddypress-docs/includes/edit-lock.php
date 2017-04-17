<?php

/**
 * Functionality related to Edit Lock
 *
 * @since 1.6.0
 */

/**
 * BP Docs heartbeat interval.
 *
 * @since 1.6.0
 *
 * @return int
 */
function bp_docs_heartbeat_pulse() {
	// Check whether a global heartbeat already exists
	$heartbeat_settings = apply_filters( 'heartbeat_settings', array() );
	if ( ! empty( $heartbeat_settings['interval'] ) ) {
		if ( 'fast' === $heartbeat_settings['interval'] ) {
			$pulse = 5;
		} else {
			$pulse = intval( $heartbeat_settings['interval'] );
		}
	}

	// Fallback
	if ( empty( $pulse ) ) {
		$pulse = 15;
	}

	// Filter here to specify a Docs-specific pulse frequency
	$pulse = intval( apply_filters( 'bp_docs_activity_pulse', $pulse ) );

	return $pulse;
}

/**
 * Handle heartbeat
 *
 * @since 1.6.0
 */
function bp_docs_heartbeat_callback( $response, $data ) {
	if ( empty( $data['doc_id'] ) ) {
		return $response;
	}

	$doc_id = intval( $data['doc_id'] );

	if ( ! $doc_id ) {
		return $response;
	}

	$uid = bp_loggedin_user_id();

	$lock = bp_docs_check_post_lock( $doc_id );

	// No lock, or belongs to the current user
	if ( empty( $lock ) || $lock == bp_loggedin_user_id() ) {
		$time = time();
		$lstring = "$time:$uid";
		update_post_meta( $doc_id, '_bp_docs_last_pinged', $lstring );

	// Someone else is editing, so bounce
	} else {
		$bounce = bp_docs_get_doc_link( $doc_id );
		$by = new WP_User( $lock );
		if ( ! empty( $by->user_nicename ) ) {
			$bounce = add_query_arg( 'by', $by->user_nicename, $bounce );
		}
		$response['bp_docs_bounce'] = $bounce;
	}

	return $response;
}
add_filter( 'heartbeat_received', 'bp_docs_heartbeat_callback', 10, 2 );

/**
 * Prevent a user from visiting the Edit page if it's locked.
 *
 * @since 1.6.0
 */
function bp_docs_edit_lock_redirect() {
	if ( ! bp_docs_is_doc_edit() ) {
		return;
	}

	$doc_id = get_queried_object_id();

	$lock = bp_docs_check_post_lock( $doc_id );

	if ( ! empty( $lock ) && $lock != bp_loggedin_user_id() ) {
		$bounce = bp_docs_get_doc_link( $doc_id );
		wp_redirect( $bounce );
	}
}
add_action( 'bp_actions', 'bp_docs_edit_lock_redirect' );

/**
 * Check to see if the post is currently being edited by another user.
 *
 * This is a verbatim copy of wp_check_post_lock(), which is only available
 * in the admin
 *
 * @since 1.2.8
 *
 * @param int $post_id ID of the post to check for editing
 * @return bool|int False: not locked or locked by current user. Int: user ID of user with lock.
 */
function bp_docs_check_post_lock( $post_id ) {
	if ( !$post = get_post( $post_id ) )
		return false;

	if ( !$lock = get_post_meta( $post->ID, '_bp_docs_last_pinged', true ) )
		return false;

	$lock = explode( ':', $lock );
	$time = $lock[0];
	$user = isset( $lock[1] ) ? $lock[1] : get_post_meta( $post->ID, '_edit_last', true );

	$heartbeat_interval = bp_docs_heartbeat_pulse();

	// Bail out of the lock if four pings have been missed (one minute, by default)
	$time_window = apply_filters( 'bp_docs_post_lock_interval', $heartbeat_interval * 4 );

	if ( $time && $time > time() - $time_window && $user != get_current_user_id() ) {
		return $user;
	}

	return false;
}

/**
 * Get the lock status of a doc
 *
 * The function first tries to get the lock status out of $bp. If it has to look it up, it
 * stores the data in $bp for future use.
 *
 * @package BuddyPress Docs
 * @since 1.0-beta-2
 *
 * @param int $doc_id Optional. Defaults to the doc currently being viewed
 * @return int Returns 0 if there is no lock, otherwise returns the user_id of the locker
 */
function bp_docs_is_doc_edit_locked( $doc_id = false ) {
	global $bp, $post;

	// Try to get the lock out of $bp first
	if ( isset( $bp->bp_docs->current_doc_lock ) ) {
		$is_edit_locked = $bp->bp_docs->current_doc_lock;
	} else {
		$is_edit_locked = 0;

		if ( empty( $doc_id ) )
			$doc_id = !empty( $post->ID ) ? $post->ID : false;

		if ( $doc_id ) {
			$is_edit_locked = bp_docs_check_post_lock( $doc_id );
		}

		// Put into the $bp global to avoid extra lookups
		$bp->bp_docs->current_doc_lock = $is_edit_locked;
	}

	return apply_filters( 'bp_docs_is_doc_edit_locked', $is_edit_locked, $doc_id );
}

/**
 * Echoes the output of bp_docs_get_current_doc_locker_name()
 *
 * @package BuddyPress Docs
 * @since 1.0-beta-2
 */
function bp_docs_current_doc_locker_name() {
	echo bp_docs_get_current_doc_locker_name();
}
	/**
	 * Get the name of the user locking the current document, if any
	 *
	 * @package BuddyPress Docs
	 * @since 1.0-beta-2
	 *
	 * @return string $locker_name The full name of the locking user
	 */
	function bp_docs_get_current_doc_locker_name() {
		$locker_name = '';

		$locker_id = bp_docs_is_doc_edit_locked();

		if ( $locker_id )
			$locker_name = bp_core_get_user_displayname( $locker_id );

		return apply_filters( 'bp_docs_get_current_doc_locker_name', $locker_name, $locker_id );
	}

/**
 * Echoes the output of bp_docs_get_force_cancel_edit_lock_link()
 *
 * @package BuddyPress Docs
 * @since 1.0-beta-2
 */
function bp_docs_force_cancel_edit_lock_link() {
	echo bp_docs_get_force_cancel_edit_lock_link();
}
	/**
	 * Get the URL for canceling the edit lock on the current doc
	 *
	 * @package BuddyPress Docs
	 * @since 1.0-beta-2
	 *
	 * @return string $cancel_link href for the cancel edit lock link
	 */
	function bp_docs_get_force_cancel_edit_lock_link() {
		global $post;

		$doc_id = !empty( $post->ID ) ? $post->ID : false;

		if ( !$doc_id )
			return false;

		$doc_permalink = bp_docs_get_doc_link( $doc_id );

		$cancel_link = wp_nonce_url( add_query_arg( 'bpd_action', 'cancel_edit_lock', $doc_permalink ), 'bp_docs_cancel_edit_lock' );

		return apply_filters( 'bp_docs_get_force_cancel_edit_lock_link', $cancel_link, $doc_permalink );
	}

/**
 * Echoes the output of bp_docs_get_cancel_edit_link()
 *
 * @package BuddyPress Docs
 * @since 1.0-beta-2
 */
function bp_docs_cancel_edit_link() {
	echo bp_docs_get_cancel_edit_link();
}
	/**
	 * Get the URL for canceling out of Edit mode on a doc
	 *
	 * This used to be a straight link back to non-edit mode, but something fancier is needed
	 * in order to detect the Cancel and to remove the edit lock.
	 *
	 * @package BuddyPress Docs
	 * @since 1.0-beta-2
	 *
	 * @return string $cancel_link href for the cancel edit link
	 */
	function bp_docs_get_cancel_edit_link() {
		global $bp, $post;

		$doc_id = get_queried_object_id();

		if ( !$doc_id )
			return false;

		$doc_permalink = bp_docs_get_doc_link( $doc_id );

		$cancel_link = add_query_arg( 'bpd_action', 'cancel_edit', $doc_permalink );

		return apply_filters( 'bp_docs_get_cancel_edit_link', $cancel_link, $doc_permalink );
	}

/**
 * AJAX handler for remove_edit_lock option
 *
 * This function is called when a user is editing a Doc and clicks a link to leave the page
 *
 * @package BuddyPress Docs
 * @since 1.1
 */
function bp_docs_remove_edit_lock() {
	$doc_id = isset( $_POST['doc_id'] ) ? $_POST['doc_id'] : false;

	if ( !$doc_id )
		return false;

	delete_post_meta( $doc_id, '_bp_docs_last_pinged' );
}
add_action( 'wp_ajax_remove_edit_lock', 'bp_docs_remove_edit_lock' );

/**
 * AJAX handler for setting edit lock.
 *
 * Called when a user enters an Edit page.
 *
 * @since 1.6.0
 */
function bp_docs_add_edit_lock_cb() {
	$doc_id = isset( $_POST['doc_id'] ) ? (int) $_POST['doc_id'] : false;

	if ( ! $doc_id ) {
		return;
	}

	$doc = get_post( $doc_id );

	if ( ! $doc || is_wp_error( $doc ) ) {
		return;
	}

	if ( bp_docs_get_post_type_name() !== $doc->post_type ) {
		return;
	}

	if ( ! is_user_logged_in() ) {
		return;
	}

	// Is this post already locked?
	$lock = bp_docs_check_post_lock( $doc_id );
	if ( ! empty( $lock ) && $lock != bp_loggedin_user_id() ) {
		die();
	}

	$now = time();
	$user_id = bp_loggedin_user_id();
	$lock = "$now:$user_id";

	update_post_meta( $doc_id, '_bp_docs_last_pinged', $lock );

	die( json_encode( '1' ) );
}
add_action( 'wp_ajax_add_edit_lock', 'bp_docs_add_edit_lock_cb' );

