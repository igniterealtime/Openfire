<?php
require('admin-action.php');

$post_id = (int) $_GET['id'];

if ( !bb_current_user_can( 'delete_post', $post_id ) ) {
	wp_redirect( bb_get_uri(null, null, BB_URI_CONTEXT_HEADER) );
	exit;
}

bb_check_admin_referer( 'delete-post_' . $post_id );

$status  = (int) $_GET['status'];
$bb_post = bb_get_post ( $post_id );
$old_status = (int) $bb_post->post_status;

if ( !$bb_post )
	bb_die(__('There is a problem with that post, pardner.'));

if ( 0 == $status && 0 != $bb_post->post_status ) // We're undeleting
	add_filter('bb_delete_post', 'bb_topics_replied_on_undelete_post');

bb_delete_post( $post_id, $status );

$message = '';
switch ( $old_status ) {
	case 0:
		switch ( $status ) {
			case 0:
				break;
			case 1:
				$message = 'deleted';
				break;
			default:
				$message = 'spammed';
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
			default:
				$message = 'spammed';
				break;
		}
		break;
	default:
		switch ( $status ) {
			case 0:
				$message = 'unspammed-normal';
				break;
			case 1:
				$message = 'unspammed-deleted';
				break;
			default:
				break;
		}
		break;
}

$topic = get_topic( $bb_post->topic_id );

if ( $sendto = wp_get_referer() ) {
	$sendto = remove_query_arg( 'message', $sendto );
	$sendto = add_query_arg( 'message', $message, $sendto );
	$send_to_topic = bb_get_topic_from_uri( $sendto );
	if ( $send_to_topic && $topic->topic_id == $send_to_topic->topic_id )
		$sendto = add_query_arg( 'view', 'all', $sendto );
} else if ( $topic->topic_posts == 0 ) {
	$sendto = get_forum_link( $topic->forum_id );
} else {
	$the_page = bb_get_page_number( $bb_post->post_position );
	$sendto = get_topic_link( $bb_post->topic_id, $the_page );
}

bb_safe_redirect( $sendto );
exit;
