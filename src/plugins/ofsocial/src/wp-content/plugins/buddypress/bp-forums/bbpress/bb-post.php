<?php
require( './bb-load.php' );

if ( bb_is_login_required() )
	bb_auth( 'logged_in' );

bb_check_post_flood();

if ( !$post_content = trim( $_POST['post_content'] ) )
	bb_die( __( 'You need to actually submit some content!' ) );

$post_author = $post_email = $post_url = '';

if ( !bb_is_user_logged_in() ) {
	if ( bb_is_login_required() ) {
		bb_die( __( 'You are not allowed to post.  Are you logged in?' ) );
	} else {
		if ( !$post_author = sanitize_user( trim( $_POST['author'] ) ) )
			bb_die( __( 'You need to submit your name!' ) );
		elseif ( !$post_email = sanitize_email( trim( $_POST['email'] ) ) )
			bb_die( __( 'You need to submit a valid email address!' ) );

		if ( !empty( $_POST['url'] ) )
			$post_url = esc_url( trim( $_POST['url'] ) );
	}
}



if ( isset($_POST['topic']) && $forum_id = (int) $_POST['forum_id'] ) {
	if ( bb_is_login_required() && ! bb_current_user_can('write_posts') )
		bb_die(__('You are not allowed to post.  Are you logged in?'));

	if ( bb_is_login_required() && ! bb_current_user_can( 'write_topic', $forum_id ) )
		bb_die(__('You are not allowed to write new topics.'));

	bb_check_admin_referer( 'create-topic' );

	$topic = trim( $_POST['topic'] );
	$tags  = trim( $_POST['tags']  );

	if ('' == $topic)
		bb_die(__('Please enter a topic title'));

	$args = array();

	if ( isset( $post_author ) )
		$args['topic_poster_name'] = $args['topic_last_poster_name'] = $post_author;

	$topic_id = bb_new_topic( $topic, $forum_id, $tags, $args );

} elseif ( isset($_POST['topic_id'] ) ) {
	$topic_id = (int) $_POST['topic_id'];
	bb_check_admin_referer( 'create-post_' . $topic_id );
}

if ( bb_is_login_required() && ! bb_current_user_can( 'write_post', $topic_id ) )
	bb_die(__('You are not allowed to post.  Are you logged in?'));

if ( !topic_is_open( $topic_id ) )
	bb_die(__('This topic has been closed'));

$post_data = array(
	'post_text' => stripslashes($_POST['post_content']),
	'topic_id' => $topic_id,
);

foreach( array('post_author', 'post_email', 'post_url') as $field ) {
	if ( ! empty( $$field ) ) {
		$post_data[$field] = $$field;
	}
}

$post_id = bb_insert_post($post_data);

$tags  = trim( $_POST['tags']  );
bb_add_topic_tags( $topic_id, $tags );

$topic = get_topic( $topic_id, false );
$link = get_post_link($post_id);

if ( $topic->topic_posts )
	$link = add_query_arg( 'replies', $topic->topic_posts, $link );

// This action used to be bb_post.php, changed to avoid conflict in bb_load_template()
do_action( 'bb-post.php', $post_id );
if ($post_id)
	wp_redirect( $link );
else
	wp_redirect( bb_get_uri(null, null, BB_URI_CONTEXT_HEADER) );
exit;
