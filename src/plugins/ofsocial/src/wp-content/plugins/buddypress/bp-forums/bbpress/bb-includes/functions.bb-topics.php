<?php

/* Topics */

function get_topic( $id, $cache = true ) {
	global $bbdb;

	if ( !is_numeric($id) ) {
		list($slug, $sql) = bb_get_sql_from_slug( 'topic', $id );
		$id = wp_cache_get( $slug, 'bb_topic_slug' );
	}

	// not else
	if ( is_numeric($id) ) {
		$id = (int) $id;
		$sql = "topic_id = $id";
	}

	if ( 0 === $id || !$sql )
		return false;

	// &= not =&
	$cache &= 'AND topic_status = 0' == $where = apply_filters( 'get_topic_where', 'AND topic_status = 0' );

	if ( ( $cache || !$where ) && is_numeric($id) && false !== $topic = wp_cache_get( $id, 'bb_topic' ) )
		return $topic;

	// $where is NOT bbdb:prepared
	$topic = $bbdb->get_row( "SELECT * FROM $bbdb->topics WHERE $sql $where" );
	$topic = bb_append_meta( $topic, 'topic' );

	if ( $cache ) {
		wp_cache_set( $topic->topic_id, $topic, 'bb_topic' );
		wp_cache_add( $topic->topic_slug, $topic->topic_id, 'bb_topic_slug' );
	}

	return $topic;
}

function bb_get_topic_from_uri( $uri ) {
	// Extract the topic id or slug of the uri
	if ( 'topic' === bb_get_path(0, false, $uri) ) {
		$topic_id_or_slug = bb_get_path(1, false, $uri);
	} else {
		if ($parsed_uri = parse_url($uri)) {
			if (preg_match('@/topic\.php$@'  ,$parsed_uri['path'])) {
				$args = wp_parse_args($parsed_uri['query']);
				if (isset($args['id'])) {
					$topic_id_or_slug = $args['id'];
				}
			}
		}
	}

	if ( !$topic_id_or_slug )
		return false;

	return get_topic( $topic_id_or_slug );
}

function get_latest_topics( $args = null ) {
	$defaults = array( 'forum' => false, 'page' => 1, 'exclude' => false, 'number' => false );
	if ( is_numeric( $args ) )
		$args = array( 'forum' => $args );
	else
		$args = wp_parse_args( $args ); // Make sure it's an array
	if ( 1 < func_num_args() )
		$args['page'] = func_get_arg(1);
	if ( 2 < func_num_args() )
		$args['exclude'] = func_get_arg(2);

	$args = wp_parse_args( $args, $defaults );
	extract( $args, EXTR_SKIP );

	if ( $exclude ) {
		$exclude = '-' . str_replace(',', ',-', $exclude);
		$exclude = str_replace('--', '-', $exclude);
		if ( $forum )
			$forum = (string) $forum . ",$exclude";
		else
			$forum = $exclude;
	}

	$q = array(
		'forum_id' => $forum,
		'page' => $page,
		'per_page' => $number,
		'index_hint' => 'USE INDEX (`forum_time`)'
	);

	if ( bb_is_front() )
		$q['sticky'] = '-2';
	elseif ( bb_is_forum() || bb_is_view() )
		$q['sticky'] = 0;

	// Last param makes filters back compat
	$query = new BB_Query( 'topic', $q, 'get_latest_topics' );
	return $query->results;
}

function get_sticky_topics( $forum = false, $display = 1 ) {
	if ( 1 != $display ) // Why is this even here?
		return false;

	$q = array(
		'forum_id' => $forum,
		'sticky' => bb_is_front() ? 'super' : 'sticky'
	);

	$query = new BB_Query( 'topic', $q, 'get_sticky_topics' );
	return $query->results;
}

function get_recent_user_threads( $user_id ) {
	global $page;
	$q = array( 'page' => $page, 'topic_author_id' => $user_id, 'order_by' => 't.topic_time');

	$query = new BB_Query( 'topic', $q, 'get_recent_user_threads' );

	return $query->results;
}

function bb_insert_topic( $args = null ) {
	global $bbdb;

	if ( !$args = wp_parse_args( $args ) )
		return false;

	$fields = array_keys( $args );

	if ( isset($args['topic_id']) && false !== $args['topic_id'] ) {
		$update = true;
		if ( !$topic_id = (int) get_topic_id( $args['topic_id'] ) )
			return false;
		// Get from db, not cache.  Good idea?  Prevents trying to update meta_key names in the topic table (get_topic() returns appended topic obj)
		$topic = $bbdb->get_row( $bbdb->prepare( "SELECT * FROM $bbdb->topics WHERE topic_id = %d", $topic_id ) );
		$defaults = get_object_vars( $topic );
		unset($defaults['topic_id']);

		// Only update the args we passed
		$fields = array_intersect( $fields, array_keys($defaults) );
		if ( in_array( 'topic_poster', $fields ) )
			$fields[] = 'topic_poster_name';
		if ( in_array( 'topic_last_poster', $fields ) )
			$fields[] = 'topic_last_poster_name';
	} else {
		$topic_id = false;
		$update = false;

		$now = bb_current_time('mysql');
		$current_user_id = bb_get_current_user_info( 'id' );

		$defaults = array(
			'topic_title' => '',
			'topic_slug' => '',
			'topic_poster' => $current_user_id, // accepts ids
			'topic_poster_name' => '', // accept names
			'topic_last_poster' => $current_user_id, // accepts ids
			'topic_last_poster_name' => '', // accept names
			'topic_start_time' => $now,
			'topic_time' => $now,
			'topic_open' => 1,
			'forum_id' => 0 // accepts ids or slugs
		);

		// Insert all args
		$fields = array_keys($defaults);
	}

	$defaults['tags'] = false; // accepts array or comma delimited string
	extract( wp_parse_args( $args, $defaults ) );
	unset($defaults['tags']);

	if ( !$forum = bb_get_forum( $forum_id ) )
		return false;
	$forum_id = (int) $forum->forum_id;

	if ( !$user = bb_get_user( $topic_poster ) )
		$user = bb_get_user( $topic_poster_name, array( 'by' => 'login' ) );

	if ( !empty( $user ) ) {
		$topic_poster      = $user->ID;
		$topic_poster_name = $user->user_login;
	}

	if ( !$last_user = bb_get_user( $topic_last_poster ) )
		$last_user = bb_get_user( $topic_last_poster_name, array( 'by' => 'login' ) );

	if ( !empty( $last_user ) ) {
		$topic_last_poster      = $last_user->ID;
		$topic_last_poster_name = $last_user->user_login;
	}

	if ( in_array( 'topic_title', $fields ) ) {
		$topic_title = apply_filters( 'pre_topic_title', $topic_title, $topic_id );
		if ( strlen($topic_title) < 1 )
			return false;
	}

	if ( in_array( 'topic_slug', $fields ) ) {
		$slug_sql = $update ?
			"SELECT topic_slug FROM $bbdb->topics WHERE topic_slug = %s AND topic_id != %d" :
			"SELECT topic_slug FROM $bbdb->topics WHERE topic_slug = %s";

		$topic_slug = $_topic_slug = bb_slug_sanitize( $topic_slug ? $topic_slug : wp_specialchars_decode( $topic_title, ENT_QUOTES ) );
		if ( strlen( $_topic_slug ) < 1 )
			$topic_slug = $_topic_slug = '0';

		while ( is_numeric($topic_slug) || $existing_slug = $bbdb->get_var( $bbdb->prepare( $slug_sql, $topic_slug, $topic_id ) ) )
			$topic_slug = bb_slug_increment( $_topic_slug, $existing_slug );
	}

	if ( $update ) {
		$bbdb->update( $bbdb->topics, compact( $fields ), compact( 'topic_id' ) );
		wp_cache_delete( $topic_id, 'bb_topic' );
		if ( in_array( 'topic_slug', $fields ) )
			wp_cache_delete( $topic->topic_slug, 'bb_topic_slug' );
		wp_cache_flush( 'bb_query' );
		wp_cache_flush( 'bb_cache_posts_post_ids' );
		do_action( 'bb_update_topic', $topic_id );
	} else {
		$bbdb->insert( $bbdb->topics, compact( $fields ) );
		$topic_id = $bbdb->insert_id;
		$bbdb->query( $bbdb->prepare( "UPDATE $bbdb->forums SET topics = topics + 1 WHERE forum_id = %d", $forum_id ) );
		wp_cache_delete( $forum_id, 'bb_forum' );
		wp_cache_flush( 'bb_forums' );
		wp_cache_flush( 'bb_query' );
		wp_cache_flush( 'bb_cache_posts_post_ids' );
		do_action( 'bb_new_topic', $topic_id );
	}

	if ( !empty( $tags ) )
		bb_add_topic_tags( $topic_id, $tags );

	do_action( 'bb_insert_topic', $topic_id, $args, compact( array_keys($args) ) ); // topic_id, what was passed, what was used

	return $topic_id;
}

// Deprecated: expects $title to be pre-escaped
function bb_new_topic( $title, $forum, $tags = '', $args = '' ) {
	$title = stripslashes( $title );
	$tags  = stripslashes( $tags );
	$forum = (int) $forum;
	return bb_insert_topic( wp_parse_args( $args ) + array( 'topic_title' => $title, 'forum_id' => $forum, 'tags' => $tags ) );
}

// Deprecated: expects $title to be pre-escaped
function bb_update_topic( $title, $topic_id ) {
	$title = stripslashes( $title );
	return bb_insert_topic( array( 'topic_title' => $title, 'topic_id' => $topic_id ) );
}

function bb_delete_topic( $topic_id, $new_status = 0 ) {
	global $bbdb;
	$topic_id = (int) $topic_id;
	add_filter( 'get_topic_where', 'bb_no_where' );
	if ( $topic = get_topic( $topic_id ) ) {
		$new_status = (int) $new_status;
		$old_status = (int) $topic->topic_status;
		if ( $new_status == $old_status )
			return;

		$thread_args = array( 'per_page' => -1, 'order' => 'DESC' );
		if ( 0 != $old_status && 0 == $new_status )
			$thread_args['post_status'] = 'all';
		$poster_ids = array();
		$posts = get_thread( $topic_id, $thread_args );
		if ( $posts && count( $posts ) ) {
			foreach ( $posts as $post ) {
				_bb_delete_post( $post->post_id, $new_status );
				$poster_ids[] = $post->poster_id;
			}
		}

		if ( count( $poster_ids ) ) {
			foreach ( array_unique( $poster_ids ) as $id ) {
				if ( $user = bb_get_user( $id ) ) {
					$topics_replied_key = $bbdb->prefix . 'topics_replied';
					bb_update_usermeta( $user->ID, $topics_replied_key, ( $old_status ? $user->$topics_replied_key + 1 : $user->$topics_replied_key - 1 ) );
				}
			}
		}

		if ( $ids = $bbdb->get_col( "SELECT user_id, meta_value FROM $bbdb->usermeta WHERE meta_key = 'favorites' and FIND_IN_SET('$topic_id', meta_value) > 0" ) )
			foreach ( $ids as $id )
				bb_remove_user_favorite( $id, $topic_id );

		switch ( $new_status ) {
			case 0: // Undeleting
				$bbdb->update( $bbdb->topics, array( 'topic_status' => $new_status ), compact( 'topic_id' ) );
				$topic_posts = (int) $bbdb->get_var( $bbdb->prepare(
					"SELECT COUNT(*) FROM $bbdb->posts WHERE topic_id = %d AND post_status = 0", $topic_id
				) );
				$all_posts = (int) $bbdb->get_var( $bbdb->prepare(
					"SELECT COUNT(*) FROM $bbdb->posts WHERE topic_id = %d", $topic_id
				) );
				bb_update_topicmeta( $topic_id, 'deleted_posts', $all_posts - $topic_posts );
				$bbdb->query( $bbdb->prepare(
					"UPDATE $bbdb->forums SET topics = topics + 1, posts = posts + %d WHERE forum_id = %d", $topic_posts, $topic->forum_id
				) );
				$bbdb->update( $bbdb->topics, compact( 'topic_posts' ), compact( 'topic_id' ) );
				bb_topic_set_last_post( $topic_id );
				bb_update_post_positions( $topic_id );
				break;

			default: // Other statuses (like Delete and Bozo)
				bb_remove_topic_tags( $topic_id );
				$bbdb->update( $bbdb->topics, array( 'topic_status' => $new_status, 'tag_count' => 0 ), compact( 'topic_id' ) );
				$bbdb->query( $bbdb->prepare(
					"UPDATE $bbdb->forums SET topics = topics - 1, posts = posts - %d WHERE forum_id = %d", $topic->topic_posts, $topic->forum_id
				) );
				break;
		}

		do_action( 'bb_delete_topic', $topic_id, $new_status, $old_status );
		wp_cache_delete( $topic_id, 'bb_topic' );
		wp_cache_delete( $topic->topic_slug, 'bb_topic_slug' );
		wp_cache_delete( $topic_id, 'bb_thread' );
		wp_cache_delete( $topic->forum_id, 'bb_forum' );
		wp_cache_flush( 'bb_forums' );
		wp_cache_flush( 'bb_query' );
		wp_cache_flush( 'bb_cache_posts_post_ids' );
		return $topic_id;
	} else {
		return false;
	}
}

function bb_move_topic( $topic_id, $forum_id ) {
	global $bbdb;
	$topic = get_topic( $topic_id );
	$forum = bb_get_forum( $forum_id );
	$topic_id = (int) $topic->topic_id;
	$forum_id = (int) $forum->forum_id;

	if ( $topic && $forum && $topic->forum_id != $forum_id ) {
		$bbdb->update( $bbdb->posts, compact( 'forum_id' ), compact( 'topic_id' ) );
		$bbdb->update( $bbdb->topics, compact( 'forum_id' ), compact( 'topic_id' ) );
		$bbdb->query( $bbdb->prepare(
			"UPDATE $bbdb->forums SET topics = topics + 1, posts = posts + %d WHERE forum_id = %d", $topic->topic_posts, $forum_id
		) );
		$bbdb->query( $bbdb->prepare( 
			"UPDATE $bbdb->forums SET topics = topics - 1, posts = posts - %d WHERE forum_id = %d", $topic->topic_posts, $topic->forum_id
		) );
		wp_cache_flush( 'bb_post' );
		wp_cache_delete( $topic_id, 'bb_topic' );
		wp_cache_delete( $forum_id, 'bb_forum' );
		wp_cache_flush( 'bb_forums' );
		wp_cache_flush( 'bb_query' );
		wp_cache_flush( 'bb_cache_posts_post_ids' );

		do_action( 'bb_move_topic', $topic_id, $forum_id, $topic->forum_id );

		return $forum_id;
	}
	return false;
}

function bb_topic_set_last_post( $topic_id ) {
	global $bbdb;
	$topic_id = (int) $topic_id;
	$old_post = $bbdb->get_row( $bbdb->prepare(
		"SELECT post_id, poster_id, post_time FROM $bbdb->posts WHERE topic_id = %d AND post_status = 0 ORDER BY post_time DESC LIMIT 1", $topic_id
	) );
	$old_poster = bb_get_user( $old_post->poster_id );
	wp_cache_delete( $topic_id, 'bb_topic' );
	return $bbdb->update( $bbdb->topics, array( 'topic_time' => $old_post->post_time, 'topic_last_poster' => $old_post->poster_id, 'topic_last_poster_name' => $old_poster->login_name, 'topic_last_post_id' => $old_post->post_id ), compact( 'topic_id' ) );
}

function bb_close_topic( $topic_id ) {
	global $bbdb;
	$topic_id = (int) $topic_id;
	wp_cache_delete( $topic_id, 'bb_topic' );
	$r = $bbdb->update( $bbdb->topics, array( 'topic_open' => 0 ), compact( 'topic_id' ) );
	do_action('close_topic', $topic_id, $r);
	return $r;
}

function bb_open_topic( $topic_id ) {
	global $bbdb;
	$topic_id = (int) $topic_id;
	wp_cache_delete( $topic_id, 'bb_topic' );
	$r = $bbdb->update( $bbdb->topics, array( 'topic_open' => 1 ), compact( 'topic_id' ) );
	do_action('open_topic', $topic_id, $r);
	return $r;
}

function bb_stick_topic( $topic_id, $super = 0 ) {
	global $bbdb;
	$topic_id = (int) $topic_id;
	$stick = 1 + abs((int) $super);
	wp_cache_delete( $topic_id, 'bb_topic' );
	$r = $bbdb->update( $bbdb->topics, array( 'topic_sticky' => $stick ), compact( 'topic_id' ) );
	do_action('stick_topic', $topic_id, $r);
	return $r;
}

function bb_unstick_topic( $topic_id ) {
	global $bbdb;
	$topic_id = (int) $topic_id;
	wp_cache_delete( $topic_id, 'bb_topic' );
	$r = $bbdb->update( $bbdb->topics, array( 'topic_sticky' => 0 ), compact( 'topic_id' ) );
	do_action('unstick_topic', $topic_id, $r);
	return $r;
}

function topic_is_open( $topic_id = 0 ) {
	$topic = get_topic( get_topic_id( $topic_id ) );
	return 1 == $topic->topic_open;
}

function topic_is_sticky( $topic_id = 0 ) {
	$topic = get_topic( get_topic_id( $topic_id ) );
	return '0' !== $topic->topic_sticky;
}

function bb_update_topic_voices( $topic_id )
{
	if ( !$topic_id ) {
		return;
	}

	$topic_id = abs( (int) $topic_id );

	global $bbdb;
	if ( $voices = $bbdb->get_col( $bbdb->prepare( "SELECT DISTINCT poster_id FROM $bbdb->posts WHERE topic_id = %s AND post_status = '0';", $topic_id ) ) ) {
		$voices = count( $voices );
		bb_update_topicmeta( $topic_id, 'voices_count', $voices );
	}
}

/* Thread */

// Thread, topic?  Guh-wah?
// A topic is the container, the thread is it's contents (the posts)

function get_thread( $topic_id, $args = null ) {
	$defaults = array( 'page' => 1, 'order' => 'ASC' );
	if ( is_numeric( $args ) )
		$args = array( 'page' => $args );
	if ( @func_get_arg(2) )
		$defaults['order'] = 'DESC';

	$args = wp_parse_args( $args, $defaults );
	$args['topic_id'] = $topic_id;

	$query = new BB_Query( 'post', $args, 'get_thread' );
	return $query->results;
}

// deprecated
function get_thread_post_ids( $topic_id ) {
	$return = array( 'post' => array(), 'poster' => array() );
	foreach ( get_thread( $topic_id, array( 'per_page' => -1 ) ) as $post ) {
		$return['post'][] = $post->post_id;
		$return['poster'][] = $post->poster_id;
	}
	return $return;
}
