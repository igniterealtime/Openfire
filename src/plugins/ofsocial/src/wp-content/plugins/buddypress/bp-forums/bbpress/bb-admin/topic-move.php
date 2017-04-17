<?php
require_once('admin-action.php');

$topic_id = absint( $_POST['topic_id'] );
$forum_id = absint( $_POST['forum_id'] );

if ( !is_numeric($topic_id) || !is_numeric($forum_id) )
	bb_die(__('Invalid topic or forum.'));

if ( !bb_current_user_can( 'move_topic', $topic_id, $forum_id ) ) {
	wp_redirect( bb_get_uri(null, null, BB_URI_CONTEXT_HEADER) );
	exit;
}

bb_check_admin_referer( 'move-topic_' . $topic_id );

$topic = get_topic( $topic_id );
$forum = bb_get_forum( $forum_id );

if ( !$topic || !$forum )
	bb_die(__('Your topic or forum caused all manner of confusion'));

bb_move_topic( $topic_id, $forum_id );

if ( !$redirect = wp_get_referer() )
	$redirect = get_topic_link( $topic_id );

bb_safe_redirect( $redirect );
exit;
?>
