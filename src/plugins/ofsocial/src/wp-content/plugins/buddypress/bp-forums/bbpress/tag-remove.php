<?php

require( './bb-load.php' );

bb_auth( 'logged_in' );

$tag_id   = (int) @$_GET['tag'];
$user_id  = (int) @$_GET['user'];
$topic_id = (int) @$_GET['topic'];

bb_check_admin_referer( 'remove-tag_' . $tag_id . '|' . $topic_id );

$tag   = bb_get_tag ( $tag_id );
$topic = get_topic  ( $topic_id );
$user  = bb_get_user( $user_id );

if ( !$tag || !$topic )
	bb_die( __( 'Invalid tag or topic.' ) );

if ( false !== bb_remove_topic_tag( $tag_id, $user_id, $topic_id ) ) {
	if ( !$redirect = wp_get_referer() )
		$redirect = get_topic_link( $topic_id );
	bb_safe_redirect( $redirect );
} else {
	bb_die( __( 'The tag was not removed.' ) );
}

exit;

?>
