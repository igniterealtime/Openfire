<?php

/* Posts */

/**
 * Check to make sure that a user is not making too many posts in a short amount of time.
 */
function bb_check_post_flood() {
	global $bbdb;
	$user_id = (int) $user_id;
	$throttle_time = bb_get_option( 'throttle_time' );

	if ( bb_current_user_can( 'manage_options' ) || empty( $throttle_time ) )
		return;

	if ( bb_is_user_logged_in() ) {
		$bb_current_user = bb_get_current_user();
		
		if ( isset($bb_current_user->data->last_posted) && time() < $bb_current_user->data->last_posted + $throttle_time && ! bb_current_user_can( 'throttle' ) )
			if ( defined( 'DOING_AJAX' ) && DOING_AJAX )
				die( __( 'Slow down; you move too fast.' ) );
			else
				bb_die( __( 'Slow down; you move too fast.' ) );
	} else {
		if ( ( $last_posted = bb_get_transient($_SERVER['REMOTE_ADDR'] . '_last_posted') ) && time() < $last_posted + $throttle_time )
			if ( defined('DOING_AJAX') && DOING_AJAX )
				die( __( 'Slow down; you move too fast.' ) );
			else
				bb_die( __( 'Slow down; you move too fast.' ) );
	}
}

/**
 * Get the current, non-logged-in poster data.
 * @return array The associative array of author, email, and url data.
 */
function bb_get_current_poster() {
	// Cookies should already be sanitized.
	$post_author = '';
	if ( isset( $_COOKIE['post_author_' . BB_HASH] ) )
		$post_author = $_COOKIE['post_author_' . BB_HASH];

	$post_author_email = '';
	if ( isset( $_COOKIE['post_author_email_' . BB_HASH] ) )
		$post_author_email = $_COOKIE['post_author_email_' . BB_HASH];

	$post_author_url = '';
	if ( isset( $_COOKIE['post_author_url_' . BB_HASH] ) )
		$post_author_url = $_COOKIE['post_author_url_' . BB_HASH];

	return compact( 'post_author', 'post_author_email', 'post_author_url' );
}

function bb_get_post( $post_id ) {
	global $bbdb;
	$post_id = (int) $post_id;
	if ( false === $post = wp_cache_get( $post_id, 'bb_post' ) ) {
		$post = $bbdb->get_row( $bbdb->prepare( "SELECT * FROM $bbdb->posts WHERE post_id = %d", $post_id ) );
		$post = bb_append_meta( $post, 'post' );
		wp_cache_set( $post_id, $post, 'bb_post' );
	}
	return $post;
}

// NOT bbdb::prepared
function bb_is_first( $post_id ) { // First post in thread
	global $bbdb;
	if ( !$bb_post = bb_get_post( $post_id ) )
		return false;
	$post_id = (int) $bb_post->post_id;
	$topic_id = (int) $bb_post->topic_id;

	static $first_post;
	if ( !isset( $first_post ) ) {
		$where = apply_filters('bb_is_first_where', 'AND post_status = 0');
		$first_post = (int) $bbdb->get_var("SELECT post_id FROM $bbdb->posts WHERE topic_id = $topic_id $where ORDER BY post_id ASC LIMIT 1");
	}

	return $post_id == $first_post;
}

// Globalizes the result.
function bb_get_first_post( $_topic = false, $author_cache = true ) {
	global $topic, $bb_first_post_cache, $bb_post;
	if ( !$_topic )
		$topic_id = (int) $topic->topic_id;
	else if ( is_object($_topic) )
		$topic_id = (int) $_topic->topic_id;
	else if ( is_numeric($_topic) )
		$topic_id = (int) $_topic;

	if ( !$topic_id )
		return false;

	if ( isset($bb_first_post_cache[$topic_id]) ) {
		$post = bb_get_post( $bb_first_post_cache[$topic_id] );
	} else {
		$first_posts = bb_cache_first_posts( array($topic_id), $author_cache );
		if ( isset($first_posts[$topic_id]) )
			$post = $first_posts[$topic_id];
	}

	if ( $post ) {
		$bb_post = $post;
		return $bb_post;
	}

	return false;
}

// Ignore the return value.  Cache first posts with this function and use bb_get_first_post to grab each.
// NOT bbdb::prepared
function bb_cache_first_posts( $_topics = false, $author_cache = true ) {
	global $topics, $bb_first_post_cache, $bbdb;
	if ( !$_topics )
		$_topics =& $topics;
	if ( !is_array($_topics) )
		return false;

	$topic_ids = array();
	foreach ( $_topics as $topic )
		if ( is_object($topic) )
			$topic_ids[] = (int) $topic->topic_id;
		else if ( is_numeric($topic) )
			$topic_ids[] = (int) $topic;

	$_topic_ids = join(',', $topic_ids);

	$posts = (array) bb_cache_posts( "SELECT post_id FROM $bbdb->posts WHERE topic_id IN ($_topic_ids) AND post_position = 1", true );

	$first_posts = array();
	foreach ( $posts as $post ) {
		$bb_first_post_cache[(int) $post->topic_id] = (int) $post->post_id;
		$first_posts[(int) $post->topic_id] = $post;
	}

	if ( $author_cache )
		bb_post_author_cache( $posts );

	return $first_posts;
}

function bb_cache_posts( $query, $post_id_query = false ) {
	global $bbdb;

	$_query = '';
	$_query_post_ids = array();
	$_query_posts = array();
	$_cached_posts = array();
	$ordered_post_ids = array();

	if ( $post_id_query && is_string( $query ) ) {
		// The query is a SQL query to retrieve post_ids only
		$key = md5( $query );
		if ( false === $post_ids = wp_cache_get( $key, 'bb_cache_posts_post_ids' ) ) {
			if ( !$post_ids = (array) $bbdb->get_col( $query, 0 ) ) {
				return array();
			}
			wp_cache_add( $key, $post_ids, 'bb_cache_posts_post_ids' );
		}
		$query = $post_ids;
	}

	if ( is_array( $query ) ) {
		$get_order_from_query = false;

		foreach ( $query as $_post_id ) {
			$ordered_post_ids[] = $_post_id;
			if ( false === $_post = wp_cache_get( $_post_id, 'bb_post' ) ) {
				$_query_post_ids[] = $_post_id;
			} else {
				$_cached_posts[$_post->post_id] = $_post;
			}
		}

		if ( count( $_query_post_ids ) ) {
			// Escape the ids for the SQL query
			$_query_post_ids = $bbdb->escape_deep( $_query_post_ids );

			// Sort the ids so the MySQL will more consistently cache the query
			sort( $_query_post_ids );

			$_query = "SELECT * FROM $bbdb->posts WHERE post_id IN ('" . join( "','", $_query_post_ids ) . "')";
		}
	} else {
		// The query is a full SQL query which needs to be executed
		$get_order_from_query = true;
		$_query = $query;
	}

	if ( $_query_posts = (array) $bbdb->get_results( $_query ) ) {
		$_appendable_posts = array();
		foreach ( $_query_posts as $_query_post ) {
			if ( $get_order_from_query ) {
				$ordered_post_ids[] = $_query_post->post_id;
			}
			if ( false === $_post = wp_cache_get( $_query_post->post_id, 'bb_post' ) ) {
				$_appendable_posts[] = $_query_post;
			} else {
				$_cached_posts[$_query_post->post_id] = $_post;
			}
		}
		if ( count( $_appendable_posts ) ) {
			$_query_posts = bb_append_meta( $_appendable_posts, 'post' );
			foreach( $_query_posts as $_query_post ) {
				wp_cache_add( $_query_post->post_id, $_query_post, 'bb_post' );
			}
		} else {
			$_query_posts = array();
		}
	} else {
		$_query_posts = array();
	}

	foreach ( array_merge( $_cached_posts, $_query_posts ) as $_post ) {
		$keyed_posts[$_post->post_id] = $_post;
	}

	$the_posts = array();
	foreach ( $ordered_post_ids as $ordered_post_id ) {
		$the_posts[] = $keyed_posts[$ordered_post_id];
	}

	return $the_posts;
}

// Globalizes the result
function bb_get_last_post( $_topic = false, $author_cache = true ) {
	global $topic, $bb_post;
	if ( !$_topic )
		$topic_id = (int) $topic->topic_id;
	else if ( is_object($_topic) )
		$topic_id = (int) $_topic->topic_id;
	else if ( is_numeric($_topic) )
		$topic_id = (int) $_topic;

	if ( !$topic_id )
		return false;

	$_topic = get_topic( $topic_id );

	if ( $post = bb_get_post( $_topic->topic_last_post_id ) ) {
		if ( $author_cache )
			bb_post_author_cache( array($post) );
		$bb_post = $post;
	}

	return $post;
}

// No return value. Cache last posts with this function and use bb_get_last_post to grab each.
// NOT bbdb::prepared
function bb_cache_last_posts( $_topics = false, $author_cache = true ) {
	global $topics, $bbdb;
	if ( !$_topics )
		$_topics =& $topics;
	if ( !is_array($_topics) )
		return false;

	$last_post_ids = array();
	$topic_ids = array();
	foreach ( $_topics as $topic )
		if ( is_object($topic) )
			$last_post_ids[] = (int) $topic->topic_last_post_id;
		else if ( is_numeric($topic) && false !== $cached_topic = wp_cache_get( $topic, 'bb_topic' ) )
			$last_post_ids[] = (int) $cached_topic->topic_last_post_id;
		else if ( is_numeric($topic) )
			$topic_ids[] = (int) $topic;

	if ( !empty($last_post_ids) ) {
		$_last_post_ids = join(',', $last_post_ids);
		$posts = (array) bb_cache_posts( "SELECT post_id FROM $bbdb->posts WHERE post_id IN ($_last_post_ids) AND post_status = 0", true );
		if ( $author_cache )
			bb_post_author_cache( $posts );
	}

	if ( !empty($topic_ids) ) {	
		$_topic_ids = join(',', $topic_ids);
		$posts = (array) bb_cache_posts( "SELECT p.post_id FROM $bbdb->topics AS t LEFT JOIN $bbdb->posts AS p ON ( t.topic_last_post_id = p.post_id ) WHERE t.topic_id IN ($_topic_ids) AND p.post_status = 0", true );
		if ( $author_cache )
			bb_post_author_cache( $posts );
	}
}

// NOT bbdb::prepared
function bb_cache_post_topics( $posts ) {
	global $bbdb;

	if ( !$posts )
		return;

	$topic_ids = array();
	foreach ( $posts as $post )
		if ( false === wp_cache_get( $post->topic_id, 'bb_topic' ) )
			$topic_ids[] = (int) $post->topic_id;

	if ( !$topic_ids )
		return;

	sort( $topic_ids );
	$topic_ids = join(',', $topic_ids);

	if ( $topics = $bbdb->get_results( "SELECT * FROM $bbdb->topics WHERE topic_id IN($topic_ids)" ) )
		bb_append_meta( $topics, 'topic' );
}

function bb_get_latest_posts( $limit = 0, $page = 1 ) {
	$limit = (int) $limit;
	$post_query = new BB_Query( 'post', array( 'page' => $page, 'per_page' => $limit ), 'get_latest_posts' );
	return $post_query->results;
}

function bb_get_latest_forum_posts( $forum_id, $limit = 0, $page = 1 ) {
	$forum_id = (int) $forum_id;
	$limit    = (int) $limit;
	$post_query = new BB_Query( 'post', array( 'forum_id' => $forum_id, 'page' => $page, 'per_page' => $limit ), 'get_latest_forum_posts' );
	return $post_query->results;
}

function bb_insert_post( $args = null ) {
	global $bbdb, $bb_current_user, $bb;

	if ( !$args = wp_parse_args( $args ) )
		return false;

	$fields = array_keys( $args );

	if ( isset($args['post_id']) && false !== $args['post_id'] ) {
		$update = true;
		if ( !$post_id = (int) get_post_id( $args['post_id'] ) )
			return false;
		// Get from db, not cache.  Good idea?
		$post = $bbdb->get_row( $bbdb->prepare( "SELECT * FROM $bbdb->posts WHERE post_id = %d", $post_id ) );
		$defaults = get_object_vars( $post );
		unset( $defaults['post_id'] );

		// Only update the args we passed
		$fields = array_intersect( $fields, array_keys($defaults) );
		if ( in_array( 'topic_id', $fields ) )
			$fields[] = 'forum_id';

		// No need to run filters if these aren't changing
		// bb_new_post() and bb_update_post() will always run filters
		$run_filters = (bool) array_intersect( array( 'post_status', 'post_text' ), $fields );
	} else {
		$post_id = false;
		$update = false;
		$now = bb_current_time( 'mysql' );
		$current_user_id = bb_get_current_user_info( 'id' );
		$ip_address = $_SERVER['REMOTE_ADDR'];

		$defaults = array(
			'topic_id' => 0,
			'post_text' => '',
			'post_time' => $now,
			'poster_id' => $current_user_id, // accepts ids or names
			'poster_ip' => $ip_address,
			'post_status' => 0, // use bb_delete_post() instead
			'post_position' => false
		);

		// Insert all args
		$fields = array_keys($defaults);
		$fields[] = 'forum_id';

		$run_filters = true;
	}

	$defaults['throttle'] = true;
	extract( wp_parse_args( $args, $defaults ) );
	
	// If the user is not logged in and loginless posting is ON, then this function expects $post_author, $post_email and $post_url to be sanitized (check bb-post.php for example)

	if ( !$topic = get_topic( $topic_id ) )
		return false;

	if ( bb_is_login_required() && ! $user = bb_get_user( $poster_id ) )
		return false;

	$topic_id = (int) $topic->topic_id;
	$forum_id = (int) $topic->forum_id;

	if ( $run_filters && !$post_text = apply_filters('pre_post', $post_text, $post_id, $topic_id) )
		return false;

	if ( $update ) // Don't change post_status with this function.  Use bb_delete_post().
		$post_status = $post->post_status;

	if ( $run_filters )
		$post_status = (int) apply_filters('pre_post_status', $post_status, $post_id, $topic_id);

	if ( false === $post_position )
		$post_position = $topic_posts = intval( ( 0 == $post_status ) ? $topic->topic_posts + 1 : $topic->topic_posts );

	unset($defaults['throttle']);

	if ( $update ) {
		$bbdb->update( $bbdb->posts, compact( $fields ), compact( 'post_id' ) );
		wp_cache_delete( $post_id, 'bb_post' );
	} else {
		$bbdb->insert( $bbdb->posts, compact( $fields ) );
		$post_id = $topic_last_post_id = (int) $bbdb->insert_id;

		if ( 0 == $post_status ) {
			$topic_time = $post_time;
			$topic_last_poster = ( ! bb_is_user_logged_in() && ! bb_is_login_required() ) ? -1 : $poster_id;
			$topic_last_poster_name = ( ! bb_is_user_logged_in() && ! bb_is_login_required() ) ? $post_author : $user->user_login;

			$bbdb->query( $bbdb->prepare( "UPDATE $bbdb->forums SET posts = posts + 1 WHERE forum_id = %d;", $topic->forum_id ) );
			$bbdb->update(
				$bbdb->topics,
				compact( 'topic_time', 'topic_last_poster', 'topic_last_poster_name', 'topic_last_post_id', 'topic_posts' ),
				compact ( 'topic_id' )
			);

			$query = new BB_Query( 'post', array( 'post_author_id' => $poster_id, 'topic_id' => $topic_id, 'post_id' => "-$post_id" ) );
			if ( !$query->results ) {
				$topics_replied_key = $bbdb->prefix . 'topics_replied';
				bb_update_usermeta( $poster_id, $topics_replied_key, $user->$topics_replied_key + 1 );
			}

		} else {
			bb_update_topicmeta( $topic->topic_id, 'deleted_posts', isset($topic->deleted_posts) ? $topic->deleted_posts + 1 : 1 );
		}
	}
	bb_update_topic_voices( $topic_id );

	// if user not logged in, save user data as meta data
	if ( !$user ) {
		bb_update_meta($post_id, 'post_author', $post_author, 'post');
		bb_update_meta($post_id, 'post_email', $post_email, 'post');
		bb_update_meta($post_id, 'post_url', $post_url, 'post');
	}
	
	if ( $throttle && !bb_current_user_can( 'throttle' ) ) {
		if ( $user )
			bb_update_usermeta( $poster_id, 'last_posted', time() );
		else
			bb_set_transient( $_SERVER['REMOTE_ADDR'] . '_last_posted', time() );
	}
	
	if ( !bb_is_login_required() && !$user = bb_get_user( $poster_id ) ) {
		$post_cookie_lifetime = apply_filters( 'bb_post_cookie_lifetime', 30000000 );
		setcookie( 'post_author_' . BB_HASH, $post_author, time() + $post_cookie_lifetime, $bb->cookiepath, $bb->cookiedomain );
		setcookie( 'post_author_email_' . BB_HASH, $post_email, time() + $post_cookie_lifetime, $bb->cookiepath, $bb->cookiedomain );
		setcookie( 'post_author_url_' . BB_HASH, $post_url, time() + $post_cookie_lifetime, $bb->cookiepath, $bb->cookiedomain );
	}

	wp_cache_delete( $topic_id, 'bb_topic' );
	wp_cache_delete( $topic_id, 'bb_thread' );
	wp_cache_delete( $forum_id, 'bb_forum' );
	wp_cache_flush( 'bb_forums' );
	wp_cache_flush( 'bb_query' );
	wp_cache_flush( 'bb_cache_posts_post_ids' );

	if ( $update ) // fire actions after cache is flushed
		do_action( 'bb_update_post', $post_id );
	else
		do_action( 'bb_new_post', $post_id );

	do_action( 'bb_insert_post', $post_id, $args, compact( array_keys($args) ) ); // post_id, what was passed, what was used

	if (bb_get_option('enable_pingback')) {
		bb_update_postmeta($post_id, 'pingback_queued', '');
		wp_schedule_single_event(time(), 'do_pingbacks');
	}

	return $post_id;
}

// Deprecated: expects $post_text to be pre-escaped
function bb_new_post( $topic_id, $post_text ) {
	$post_text = stripslashes( $post_text );
	return bb_insert_post( compact( 'topic_id', 'post_text' ) );
}

// Deprecated: expects $post_text to be pre-escaped
function bb_update_post( $post_text, $post_id, $topic_id ) {
	$post_text = stripslashes( $post_text );
	return bb_insert_post( compact( 'post_text', 'post_id', 'topic_id' ) );
}

function bb_update_post_positions( $topic_id ) {
	global $bbdb;
	$topic_id = (int) $topic_id;
	$posts = get_thread( $topic_id, array( 'per_page' => '-1' ) );
	if ( $posts ) {
		foreach ( $posts as $i => $post ) {
			$bbdb->query( $bbdb->prepare( "UPDATE $bbdb->posts SET post_position = %d WHERE post_id = %d", $i + 1, $post->post_id ) );
			wp_cache_delete( (int) $post->post_id, 'bb_post' );
		}
		wp_cache_delete( $topic_id, 'bb_thread' );
		wp_cache_flush( 'bb_query' );
		wp_cache_flush( 'bb_cache_posts_post_ids' );
		return true;
	} else {
		return false;
	}
}

function bb_delete_post( $post_id, $new_status = 0 ) {
	global $bbdb, $topic, $bb_post;
	$post_id = (int) $post_id;
	$bb_post    = bb_get_post ( $post_id );
	$new_status = (int) $new_status;
	$old_status = (int) $bb_post->post_status;
	add_filter( 'get_topic_where', 'bb_no_where' );
	$topic   = get_topic( $bb_post->topic_id );
	$topic_id = (int) $topic->topic_id;

	if ( $bb_post ) {
		$uid = (int) $bb_post->poster_id;
		if ( $new_status == $old_status )
			return;
		_bb_delete_post( $post_id, $new_status );
		if ( 0 == $old_status ) {
			bb_update_topicmeta( $topic_id, 'deleted_posts', $topic->deleted_posts + 1 );
			$bbdb->query( $bbdb->prepare( "UPDATE $bbdb->forums SET posts = posts - 1 WHERE forum_id = %d", $topic->forum_id ) );
		} else if ( 0 == $new_status ) {
			bb_update_topicmeta( $topic_id, 'deleted_posts', $topic->deleted_posts - 1 );
			$bbdb->query( $bbdb->prepare( "UPDATE $bbdb->forums SET posts = posts + 1 WHERE forum_id = %d", $topic->forum_id ) );
		}
		$posts = (int) $bbdb->get_var( $bbdb->prepare( "SELECT COUNT(*) FROM $bbdb->posts WHERE topic_id = %d AND post_status = 0", $topic_id ) );
		$bbdb->update( $bbdb->topics, array( 'topic_posts' => $posts ), compact( 'topic_id' ) );

		if ( 0 == $posts ) {
			if ( 0 == $topic->topic_status || 1 == $new_status )
				bb_delete_topic( $topic_id, $new_status );
		} else {
			if ( 0 != $topic->topic_status ) {
				$bbdb->update( $bbdb->topics, array( 'topic_status' => 0 ), compact( 'topic_id' ) );
				$bbdb->query( $bbdb->prepare( "UPDATE $bbdb->forums SET topics = topics + 1 WHERE forum_id = %d", $topic->forum_id ) );
			}
			bb_topic_set_last_post( $topic_id );
			bb_update_post_positions( $topic_id );
			bb_update_topic_voices( $topic_id );
		}

		$user = bb_get_user( $uid );

		$user_posts = new BB_Query( 'post', array( 'post_author_id' => $user->ID, 'topic_id' => $topic_id ) );
		if ( $new_status && !$user_posts->results ) {
			$topics_replied_key = $bbdb->prefix . 'topics_replied';
			bb_update_usermeta( $user->ID, $topics_replied_key, $user->$topics_replied_key - 1 );
		}
		wp_cache_delete( $topic_id, 'bb_topic' );
		wp_cache_delete( $topic_id, 'bb_thread' );
		wp_cache_flush( 'bb_forums' );
		wp_cache_flush( 'bb_query' );
		wp_cache_flush( 'bb_cache_posts_post_ids' );
		do_action( 'bb_delete_post', $post_id, $new_status, $old_status );
		return $post_id;
	} else {
		return false;
	}
}

function _bb_delete_post( $post_id, $post_status ) {
	global $bbdb;
	$post_id = (int) $post_id;
	$post_status = (int) $post_status;
	$bbdb->update( $bbdb->posts, compact( 'post_status' ), compact( 'post_id' ) );
	wp_cache_delete( $post_id, 'bb_post' );
	do_action( '_bb_delete_post', $post_id, $post_status );
}

function bb_topics_replied_on_undelete_post( $post_id ) {
	global $bbdb;
	$bb_post = bb_get_post( $post_id );
	$topic = get_topic( $bb_post->topic_id );

	$user_posts = new BB_Query( 'post', array( 'post_author_id' => $bb_post->poster_id, 'topic_id' => $topic->topic_id ) );

	if ( 1 == count($user_posts) && $user = bb_get_user( $bb_post->poster_id ) ) {
		$topics_replied_key = $bbdb->prefix . 'topics_replied';
		bb_update_usermeta( $user->ID, $topics_replied_key, $user->$topics_replied_key + 1 );
	}
}

function bb_post_author_cache($posts) {
	if ( !$posts )
		return;

	$ids = array();
	foreach ($posts as $bb_post)
		$ids[] = $bb_post->poster_id;

	if ( $ids )
		bb_cache_users(array_unique($ids));
}

// These two filters are lame.  It'd be nice if we could do this in the query parameters
function bb_get_recent_user_replies_fields( $fields ) {
	return 'MAX( p.post_id ) AS post_id';
}

function bb_get_recent_user_replies_group_by() {
	return 'p.topic_id';
}

function bb_get_recent_user_replies( $user_id ) {
	global $bbdb;
	$user_id = (int) $user_id;

	$post_query = new BB_Query(
		'post',
		array(
			'post_author_id' => $user_id,
			'order_by' => 'post_id',
			'post_id_only' => true,
		),
		'get_recent_user_replies'
	);

	return $post_query->results;
}

/**
 * Sends notification emails for new posts.
 *
 * Gets new post's ID and check if there are subscribed
 * user to that topic, and if there are, send notifications
 *
 * @since 1.1
 *
 * @param int $post_id ID of new post
 */
function bb_notify_subscribers( $post_id ) {
	global $bbdb;

	if ( !$post = bb_get_post( $post_id ) )
		return false;

	// bozo or spam
 	if ( 2 == $post->post_status )
 		return false;

	if ( !$topic = get_topic( $post->topic_id ) )
		return false;
	
	$post_id = $post->post_id;
	$topic_id = $topic->topic_id;

	if ( !$poster_name = get_post_author( $post_id ) )
		return false;
	
	do_action( 'bb_pre_notify_subscribers', $post_id, $topic_id );

	if ( !$user_ids = $bbdb->get_col( $bbdb->prepare( "SELECT `$bbdb->term_relationships`.`object_id`
		FROM $bbdb->term_relationships, $bbdb->term_taxonomy, $bbdb->terms
		WHERE `$bbdb->term_relationships`.`term_taxonomy_id` = `$bbdb->term_taxonomy`.`term_taxonomy_id`
		AND `$bbdb->term_taxonomy`.`term_id` = `$bbdb->terms`.`term_id`
		AND `$bbdb->term_taxonomy`.`taxonomy` = 'bb_subscribe'
		AND `$bbdb->terms`.`slug` = 'topic-%d'",
		$topic_id ) ) )
		return false;

	foreach ( (array) $user_ids as $user_id ) {
		if ( $user_id == $post->poster_id )
			continue; // don't send notifications to the person who made the post
		
		$user = bb_get_user( $user_id );
		
		if ( !$message = apply_filters( 'bb_subscription_mail_message', __( "%1\$s wrote:\n\n%2\$s\n\nRead this post on the forums: %3\$s\n\nYou're getting this email because you subscribed to '%4\$s.'\nPlease click the link above, login, and click 'Unsubscribe' at the top of the page to stop receiving emails from this topic." ), $post_id, $topic_id ) )
			continue; /* For plugins */
		
		bb_mail(
			$user->user_email,
			apply_filters( 'bb_subscription_mail_title', '[' . bb_get_option( 'name' ) . '] ' . $topic->topic_title, $post_id, $topic_id ),
			sprintf( $message, $poster_name, strip_tags( $post->post_text ), get_post_link( $post_id ), strip_tags( $topic->topic_title ) )
		);
	}
	
	do_action( 'bb_post_notify_subscribers', $post_id, $topic_id );
}

/**
 * Updates user's subscription status in database.
 *
 * Gets user's new subscription status for topic and
 * adds new status to database.
 *
 * @since 1.1
 *
 * @param int $topic_id ID of topic for subscription
 * @param string $new_status New subscription status
 * @param int $user_id Optional. ID of user for subscription
 */
function bb_subscription_management( $topic_id, $new_status, $user_id = '' ) {
	global $bbdb, $wp_taxonomy_object;
	
	$topic = get_topic( $topic_id );
	if (!$user_id) {
		$user_id = bb_get_current_user_info( 'id' );
	}
	
	do_action( 'bb_subscripton_management', $topic_id, $new_status, $user_id );
	
	switch ( $new_status ) {
		case 'add':
			$tt_ids = $wp_taxonomy_object->set_object_terms( $user_id, 'topic-' . $topic->topic_id, 'bb_subscribe', array( 'append' => true, 'user_id' => $user_id ) );
			break;
		case 'remove':
			// I hate this with the passion of a thousand suns
			$term_id = $bbdb->get_var( "SELECT term_id FROM $bbdb->terms WHERE slug = 'topic-$topic->topic_id'" );
			$term_taxonomy_id = $bbdb->get_var( "SELECT term_taxonomy_id FROM $bbdb->term_taxonomy WHERE term_id = $term_id AND taxonomy = 'bb_subscribe'" );
			$bbdb->query( "DELETE FROM $bbdb->term_relationships WHERE object_id = $user_id AND term_taxonomy_id = $term_taxonomy_id" );
			$bbdb->query( "DELETE FROM $bbdb->term_taxonomy WHERE term_id = $term_id AND taxonomy = 'bb_subscribe'" );
			break;
	}
	
}

/**
 * Process subscription checkbox submission.
 *
 * Get ID of and new subscription status and pass values to
 * bb_user_subscribe_checkbox_update function
 *
 * @since 1.1
 *
 * @param int $post_id ID of new/edited post
 */
function bb_user_subscribe_checkbox_update( $post_id ) {
	if ( !bb_is_user_logged_in() )
		return false;
	
	$post		= bb_get_post( $post_id );
	$topic_id	= (int) $post->topic_id;
	$subscribed	= bb_is_user_subscribed( array( 'topic_id' => $topic_id, 'user_id' => $post->poster_id ) ) ? true : false;
	$check		= $_REQUEST['subscription_checkbox'];
	
	do_action( 'bb_user_subscribe_checkbox_update', $post_id, $topic_id, $subscribe, $check );
	
	if ( 'subscribe' == $check && !$subscribed )
		bb_subscription_management( $topic_id, 'add' );
	elseif ( !$check && $subscribed )
		bb_subscription_management( $topic_id, 'remove' );
	
}
