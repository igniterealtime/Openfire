<?php
require_once('./bb-load.php');

if ( isset( $_GET['fav'] ) && isset( $_GET['topic_id'] ) ) {
	bb_auth( 'logged_in' );

	if ( !bb_current_user_can( 'edit_favorites_of', $user_id ) ) {
		bb_die( __( 'You cannot edit those favorites. How did you get here?' ) );
	}

	$fav = (int) $_GET['fav'];
	$topic_id = (int) $_GET['topic_id'];

	bb_check_admin_referer( 'toggle-favorite_' . $topic_id );

	$topic = get_topic( $topic_id );
	if ( !$topic || 0 != $topic->topic_status ) {
		exit;
	}

	if ( $fav ) {
		bb_add_user_favorite( $user_id, $topic_id );
	} else {
		bb_remove_user_favorite( $user_id, $topic_id );
	}

	$ref = wp_get_referer();
	if ( false !== strpos( $ref, bb_get_uri( null, null, BB_URI_CONTEXT_TEXT ) ) ) {
		bb_safe_redirect( $ref );
	} else {
		wp_redirect( get_topic_link( $topic_id ) );
	}
	exit;
}

if ( !bb_is_profile() ) {
	$sendto = get_profile_tab_link( $user->ID, 'favorites' );
	wp_redirect( $sendto );
	exit;
}

if ( $topics = get_user_favorites( $user->ID, true ) ) {
	bb_cache_last_posts( $topics );
}

$favorites_total = isset( $user->favorites ) ? count( explode( ',', $user->favorites ) ) : 0;

bb_load_template( 'favorites.php', array( 'favorites_total', 'self' ) );
