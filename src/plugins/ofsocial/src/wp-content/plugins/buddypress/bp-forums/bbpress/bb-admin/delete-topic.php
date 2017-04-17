<?php
require('admin-action.php');

$topic_id = (int) $_GET['id'];

if ( !bb_current_user_can( 'delete_topic', $topic_id ) ) {
	wp_redirect( bb_get_uri(null, null, BB_URI_CONTEXT_HEADER) );
	exit;
}

bb_check_admin_referer( 'delete-topic_' . $topic_id );

$topic = get_topic( $topic_id );
$old_status = (int) $topic->topic_status;

if ( !$topic )
	bb_die(__('There is a problem with that topic, pardner.'));

$status = $topic->topic_status ? 0 : 1;
bb_delete_topic( $topic->topic_id, $status );

$message = '';
switch ( $old_status ) {
	case 0:
		switch ( $status ) {
			case 0:
				break;
			case 1:
				$message = 'deleted';
				break;
		}
		break;
	case 1:
		switch ( $status ) {
			case 0:
				$message = 'undeleted';
				break;
			case 1:
				break;
		}
		break;
}

if ( $sendto = wp_get_referer() ) {
	$sendto = remove_query_arg( 'message', $sendto );
	$sendto = add_query_arg( 'message', $message, $sendto );
} elseif ( 0 == $topic->topic_status )
	$sendto = get_forum_link( $topic->forum_id );
else
	$sendto = get_topic_link( $topic_id );
	
wp_redirect( $sendto );
exit;
