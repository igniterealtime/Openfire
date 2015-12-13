<?php
require_once('./bb-load.php');
$topic_id = 0;

$view_deleted = false;
if ( bb_current_user_can('browse_deleted') && 'all' == @$_GET['view'] ) {
	add_filter('get_topic_where', 'bb_no_where');
	$view_deleted = true;
}

bb_repermalink();

if ( !$topic )
	bb_die(__('Topic not found.'));

if ( $view_deleted ) {
	add_filter('get_thread_where', create_function('', 'return "p.topic_id = ' . $topic_id . '";'));
	add_filter('get_thread_post_ids', create_function('', 'return "p.topic_id = ' . $topic_id . '";'));
	add_filter('post_edit_uri', 'bb_make_link_view_all');
}

$bb_db_override = false;
do_action( 'bb_topic.php_pre_db', $topic_id );

if ( !$bb_db_override ) :
	$posts = get_thread( $topic_id, $page );
	$forum = bb_get_forum ( $topic->forum_id );

	$tags  = bb_get_topic_tags ( $topic_id );
	if ( $tags && $bb_current_id = bb_get_current_user_info( 'id' ) ) {
		$user_tags  = bb_get_user_tags   ( $topic_id, $bb_current_id );
		$other_tags = bb_get_other_tags  ( $topic_id, $bb_current_id );
		$public_tags = bb_get_public_tags( $topic_id );
	} elseif ( is_array($tags) ) {
		$user_tags  = false;
		$other_tags = bb_get_public_tags( $topic_id );
		$public_tags =& $other_tags;
	} else {
		$user_tags = $other_tags = $public_tags = false;
	}

	$list_start = ($page - 1) * bb_get_option('page_topics') + 1;

	bb_post_author_cache($posts);
endif;

bb_load_template( 'topic.php', array('bb_db_override', 'user_tags', 'other_tags', 'list_start'), $topic_id );

?>
