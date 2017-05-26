<?php
require_once('./bb-load.php');

bb_repermalink(); // The magic happens here.

if ( $self ) {
	if ( strpos($self, '.php') !== false ) {
		require($self);
	} else {
		require( BB_PATH . 'profile-base.php' );
	}
	return;
}

$reg_time = bb_gmtstrtotime( $user->user_registered );
$profile_info_keys = bb_get_profile_info_keys();

if ( !isset( $_GET['updated'] ) )
	$updated = false;
else
	$updated = true;

do_action( 'bb_profile.php_pre_db', $user_id );

if ( isset($user->is_bozo) && $user->is_bozo && $user->ID != bb_get_current_user_info( 'id' ) && !bb_current_user_can( 'moderate' ) )
	$profile_info_keys = array();

$posts = bb_get_recent_user_replies( $user_id );
$topics = get_recent_user_threads( $user_id );

bb_load_template( 'profile.php', array('reg_time', 'profile_info_keys', 'updated', 'threads'), $user_id );
