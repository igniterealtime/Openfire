<?php

require_once( '../bb-load.php' );
require_once( BB_PATH . 'bb-admin/includes/functions.bb-admin.php' );

define('BB_EXPORT_USERS', 1);
define('BB_EXPORT_FORUMS', 2);
define('BB_EXPORT_TOPICS', 4);

// Some example usage of the bitwise export levels (can be defined in bb-config.php)
//define('BB_EXPORT_LEVEL', BB_EXPORT_USERS);
//define('BB_EXPORT_LEVEL', BB_EXPORT_USERS + BB_EXPORT_FORUMS);
//define('BB_EXPORT_LEVEL', BB_EXPORT_USERS + BB_EXPORT_FORUMS + BB_EXPORT_TOPICS);

if ( !defined('BB_EXPORT_LEVEL') )
	define('BB_EXPORT_LEVEL', 0);

if ( !BB_EXPORT_LEVEL || !bb_current_user_can( 'import_export' ) )
	bb_die( __('Either export is disabled or you are not allowed to export.') );

// See bb_export_user for syntax
function _bb_export_object( $object, $properties = null, $tabs = 1 ) {
	$r = '';

	if ( !$type = $object['type'] )
		return;
	unset($object['type']);

	$atts = '';
	$id = 0;
	foreach ( $object as $att => $v ) {
		if ( 'id' == $att ) {
			$id = $v;
			$v = $type . '_' . $v;
		}
		$atts .= " $att='$v'";
	}
	unset($att, $v);

	$r .= str_repeat("\t", $tabs) . "<$type{$atts}>\n";

	foreach ( (array) $properties as $k => $v ) {
		if ( 'meta' == $k ) {
			$data = '';
			foreach ( $v as $mk => $mv )
				$data .= str_repeat("\t", $tabs + 1) . "<meta key='$mk'><![CDATA[$mv]]></meta>\n";
		} else {
			if ( '!' == $k{0} ) {
				$k = substr($k, 1);
				$v = "<![CDATA[$v]]>";
			}
			$data = str_repeat("\t", $tabs + 1) . "<$k>$v</$k>\n";
		}
		$r .= $data;
	}

	$r .= apply_filters( 'in_bb_export_object_' . $type, '', $id );

	$r .= str_repeat("\t", $tabs) . "</$type>\n\n";

	return $r;
}

// See bb_export_user for syntax
function _bb_translate_for_export( $translate, &$data ) {
	$r = array();
	foreach ( $translate as $prop => $export ) {
		if ( '?' == $export{0} ) {
			$export = substr($export, 1);
			if ( !$data[$prop] ) {
				unset($data[$prop]);
				continue;
			}
		}
		if ( false === $export ) {
			unset($data[$prop]);
			continue;
		}
		$r[$export] = $data[$prop];
		unset($data[$prop]);
	}
	unset($export, $prop);
	return $r;
}

function bb_export_user( $user_id ) {
	global $bbdb;
	if ( !$_user = bb_get_user( $user_id ) )
		return;

	$_user = get_object_vars($_user);

	$atts = array(
		'type' => 'user',
		'id' => $_user['ID']
	);

	// ?url means url is optional.  Only include it in the export if it exists
	// !title means the title should be wrapped in CDATA
	// ?! is the correct order, not !?
	$translate = array(
		'user_login' => 'login',
		'user_pass' => 'pass',
		'user_email' => 'email',
		'user_url' => '?url',
		'user_registered' => 'incept',
		'display_name' => '?!title',
		'user_nicename' => '?nicename',
		'user_status' => '?status',
		'ID' => false
	);

	$user = _bb_translate_for_export( $translate, $_user );

	$meta = array();
	foreach ( $_user as $k => $v ) {
		if ( 0 !== strpos($k, $bbdb->prefix) && isset($_user[$bbdb->prefix . $k]) )
			continue;
		$meta[$k] = maybe_serialize($v);
	}
	unset($_user, $k, $v);

	$user['meta'] = $meta;

	return _bb_export_object( $atts, $user );
}

function bb_export_forum( $forum_id ) {
	if ( !$_forum = bb_get_forum( $forum_id ) )
		return;

	$_forum = get_object_vars( $_forum );

	$translate = array(
		'forum_name'   => '!title',
		'forum_desc'   => '?!content',
		'forum_parent' => '?parent'
	);

	$forum = _bb_translate_for_export( $translate, $_forum );

	return _bb_export_object( array('type' => 'forum', 'id' => $_forum['forum_id']), $forum );
}

function bb_export_topic( $topic_id ) {
	if ( !$_topic = get_topic( $topic_id ) )
		return;

	$_topic = get_object_vars( $_topic );

	$atts = array(
		'type' => 'topic',
		'id' => $_topic['topic_id'],
		'author' => 'user_' . $_topic['topic_poster'],
		'in' => 'forum_' . $_topic['forum_id']
	);

	$translate = array(
		'topic_title' => '!title',
		'topic_start_time' => 'incept',
		'topic_status' => '?status',
		'topic_id' => false,
		'topic_poster' => false,
		'topic_poster_name' => false,
		'topic_last_poster' => false,
		'topic_last_poster_name' => false,
		'topic_time' => false,
		'forum_id' => false,
		'topic_last_post_id' => false,
		'topic_posts' => false,
		'tag_count' => false
	);

	$topic = _bb_translate_for_export( $translate, $_topic );

	$meta = array();
	foreach ( $_topic as $k => $v )
		$meta[$k] = maybe_serialize($v);
	unset($_topic, $k, $v);

	$topic['meta'] = $meta;

	return _bb_export_object( $atts, $topic );
}

function bb_export_post( $post_id ) {
	if ( !$_post = bb_get_post( $post_id ) )
		return;

	$_post = get_object_vars($_post);

	$atts = array(
		'type' => 'post',
		'id' => $_post['post_id'],
		'author' => 'user_' . $_post['poster_id']
	);

	$translate = array(
		'post_time' => 'incept',
		'post_text' => '!content',
		'post_status' => '?status',
		'post_id' => false,
		'poster_id' => false,
		'forum_id' => false,
		'topic_id' => false,
		'post_position' => false
	);

	$post = _bb_translate_for_export( $translate, $_post );

	$post['meta'] = $_post;

	return _bb_export_object( $atts, $post, 2 );
}

// One of these things is not like the others...
function bb_export_tag( $tag ) {
	// id here is not numeric.  does not currently preserve tagged_on
	return "\t\t<tag author='user_$tag->user_id' id='tag_$tag->tag'><![CDATA[$tag->raw_tag]]></tag>\n";
}

function bb_export_topic_tags( $r, $topic_id ) {
	global $topic_tag_cache;
	if ( !get_topic( $topic_id ) )
		return;

	if ( !$tags = bb_get_topic_tags( $topic_id ) )
		return $r;

	$r .= "\n";

	foreach ( (array) $tags as $tag )
		$r .= bb_export_tag( $tag );
	$topic_tag_cache = array();

	return $r;
}

function bb_export_topic_posts( $r, $topic_id ) {
	if ( !get_topic( $topic_id ) )
		return;

	$r .= "\n";

	$page = 1;
	while ( $posts = get_thread( $topic_id, array( 'post_status' => 'all', 'page' => $page++ ) ) ) {
		foreach ( $posts as $post )
			$r .= bb_export_post( $post->post_id );
	}

	return $r;
}

function bb_export() {
	global $bb;

	define( 'BB_EXPORTING', true );
	do_action( 'bb_pre_export' );

	$bb->use_cache = false; // Turn off hard cache
	$bb->page_topics = 100;

	echo "<forums-data version='0.75'>\n";

	if (BB_EXPORT_LEVEL & BB_EXPORT_USERS) {
		$page = 1;
		while ( ( $users = bb_user_search( array('page' => $page++) ) ) && !is_wp_error( $users ) ) {
			foreach ( $users as $user )
				echo bb_export_user( $user->ID );
		}
		unset($users, $user, $page);
	}

	if (BB_EXPORT_LEVEL & BB_EXPORT_FORUMS) {
		$forums = bb_get_forums();
		foreach ( $forums as $forum )
			echo bb_export_forum( $forum->forum_id );
		unset($forums, $forum);
	}

	if (BB_EXPORT_LEVEL & BB_EXPORT_TOPICS) {
		$page = 1;
		while ( $topics = get_latest_topics( 0, $page++ ) ) {
			foreach ( $topics as $topic )
				echo bb_export_topic( $topic->topic_id );
		}
		unset($topics, $topic, $page);
	}

	do_action( 'bb_export' );

	echo '</forums-data>';
}

add_filter( 'in_bb_export_object_topic', 'bb_export_topic_tags', 10, 2 );
add_filter( 'in_bb_export_object_topic', 'bb_export_topic_posts', 10, 2 );
add_filter( 'get_forum_where', 'bb_no_where', 9999 );
add_filter( 'get_forums_where', 'bb_no_where', 9999 );
add_filter( 'get_latest_topics_where', 'bb_no_where', 9999 );
add_filter( 'get_user_where', 'bb_no_where', 9999 );
add_filter( 'cache_users_where', 'bb_no_where', 9999 );

bb_export();

?>
