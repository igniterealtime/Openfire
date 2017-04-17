<?php

require( './bb-load.php' );

bb_auth( 'logged_in' );

if ( !bb_is_user_logged_in() )
	bb_die( __( 'You need to be logged in to add a tag.' ) );

$topic_id = (int) @$_POST['id' ];
$page     = (int) @$_POST['page'];
$tag      =       @$_POST['tag'];
$tag      =       stripslashes( $tag );

bb_check_admin_referer( 'add-tag_' . $topic_id );

if ( !$topic = get_topic ( $topic_id ) )
	bb_die( __( 'Topic not found.' ) );

if ( bb_add_topic_tags( $topic_id, $tag ) )
	wp_redirect( get_topic_link( $topic_id, $page ) );
else
	bb_die( __( 'The tag was not added.  Either the tag name was invalid or the topic is closed.' ) );

exit;

?>
