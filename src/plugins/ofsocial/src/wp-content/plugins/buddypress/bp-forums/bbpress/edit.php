<?php
require('./bb-load.php');

bb_auth('logged_in');

$post_id = (int) $_GET['id'];

$bb_post  = bb_get_post( $post_id );

if ( !$bb_post || !bb_current_user_can( 'edit_post', $post_id ) ) {
	wp_redirect( bb_get_uri(null, null, BB_URI_CONTEXT_HEADER) );
	die();
}

if ( 0 != $bb_post->post_status && 'all' == $_GET['view'] ) // We're trying to edit a deleted post
	add_filter('bb_is_first_where', 'bb_no_where');

$topic = get_topic( $bb_post->topic_id );

if ( bb_is_first( $bb_post->post_id ) && bb_current_user_can( 'edit_topic', $topic->topic_id ) ) 
	$topic_title = $topic->topic_title;
else 
	$topic_title = false;


bb_load_template( 'edit-post.php', array('topic_title') );

?>
