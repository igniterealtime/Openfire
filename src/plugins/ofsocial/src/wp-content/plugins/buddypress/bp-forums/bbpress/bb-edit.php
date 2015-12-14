<?php
require('./bb-load.php');

bb_auth('logged_in');

$post_id = (int) $_POST['post_id'];

$bb_post = bb_get_post( $post_id );

if ( !$bb_post ) {
	wp_redirect( bb_get_uri(null, null, BB_URI_CONTEXT_HEADER) );
	die();
}

if ( !bb_current_user_can( 'edit_post', $post_id ) )
	bb_die(__('Sorry, post is too old.'));

bb_check_admin_referer( 'edit-post_' . $post_id );

if ( 0 != $bb_post->post_status && 'all' == $_GET['view'] ) // We're trying to edit a deleted post
	add_filter('bb_is_first_where', 'bb_no_where');

// Check possible anonymous user data
$post_author = $post_email = $post_url = '';

if ( !bb_get_user( get_post_author_id( $post_id ) ) ) {
	if ( !$post_author = sanitize_user( trim( $_POST['author'] ) ) )
		bb_die( __( 'Every post needs an author name!' ) );
	elseif ( !$post_email = sanitize_email( trim( $_POST['email'] ) ) )
		bb_die( __( 'Every post needs a valid email address!' ) );

	if ( !empty( $_POST['url'] ) )
		$post_url = esc_url( trim( $_POST['url'] ) );
}

// Loop through possible anonymous post data
foreach( array('post_author', 'post_email', 'post_url') as $field ) {
	if ( ! empty( $$field ) ) {
		$post_data[$field] = $$field;
	}
}

// Setup topic data
if ( bb_is_first( $bb_post->post_id ) && bb_current_user_can( 'edit_topic', $bb_post->topic_id ) ) {

	$post_data['topic_title'] = stripslashes( $_POST['topic'] );
	$post_data['topic_id']    = $bb_post->topic_id;

	bb_insert_topic( $post_data );
}

// Setup post data
$post_data['post_text'] = stripslashes( $_POST['post_content'] );
$post_data['post_id']   = $post_id;

bb_insert_post( $post_data );

if ( $post_id ) {
	if ( $_REQUEST['view'] === 'all' ) {
		add_filter( 'get_post_link', 'bb_make_link_view_all' );
	}
	$post_link = get_post_link( $post_id );
	wp_redirect( $post_link );
} else {
	wp_redirect( bb_get_uri(null, null, BB_URI_CONTEXT_HEADER) );
}
exit;
?>
