<?php

/* Forums */

function bb_get_forums_hierarchical( $root = 0, $depth = 0, $leaves = false, $_recursed = false ) {
	static $_leaves = false;

	if (!$_recursed)
		$_leaves = false;

	$root = (int) $root;

	if ( false === $_leaves )
		$_leaves = $leaves ? $leaves : bb_get_forums();

	if ( !$_leaves )
		return false;

	$branch = array();

	reset($_leaves);

	while ( list($l, $leaf) = each($_leaves) ) {
		if ( $root == $leaf->forum_parent ) {
			$new_root = (int) $leaf->forum_id;
			unset($_leaves[$l]);
			$branch[$new_root] = 1 == $depth ? true : bb_get_forums_hierarchical( $new_root, $depth - 1, false, true );
			reset($_leaves);
		}
	}

	if ( !$_recursed ) {
		if ( !$root )
			foreach ( $_leaves as $leaf ) // Attach orphans to root
				$branch[$leaf->forum_id] = true;
		$_leaves = false;
		return ( empty($branch) ? false : $branch );
	}

	return $branch ? $branch : true;
}

function _bb_get_cached_data( $keys, $group, $callback ) {
	$return = array();
	foreach ( $keys as $key ) {
		// should use wp_cache_get_multi if available
		if ( false === $value = wp_cache_get( $key, $group ) ) {
			if ( !$value = call_user_func( $callback, $key ) ) {
				continue;
			}
		}
		$return[$key] = $value;
	}
	return $return;
}

// 'where' arg provided for backward compatibility only
function bb_get_forums( $args = null ) {
	global $bbdb;

	if ( is_numeric($args) ) {
		$args = array( 'child_of' => $args, 'hierarchical' => 1, 'depth' => 0 );
	} elseif ( is_callable($args) ) {
		$args = array( 'callback' => $args );
		if ( 1 < func_num_args() )
			$args['callback_args'] = func_get_arg(1);
	}

	$defaults = array( 'callback' => false, 'callback_args' => false, 'child_of' => 0, 'hierarchical' => 0, 'depth' => 0, 'cut_branch' => 0, 'where' => '', 'order_by' => 'forum_order' );
	$args = wp_parse_args( $args, $defaults );

	extract($args, EXTR_SKIP);
	$child_of = (int) $child_of;
	$hierarchical = 'false' === $hierarchical ? false : (bool) $hierarchical;
	$depth = (int) $depth;

	$where = apply_filters( 'get_forums_where', $where );
	$key = md5( serialize( $where . '|' . $order_by ) ); // The keys that change the SQL query
	if ( false !== $forum_ids = wp_cache_get( $key, 'bb_forums' ) ) {
		$forums = _bb_get_cached_data( $forum_ids, 'bb_forum', 'bb_get_forum' );
	} else {
		$forum_ids = array();
		$forums = array();
		$_forums = (array) $bbdb->get_results("SELECT * FROM $bbdb->forums $where ORDER BY `$order_by`;");
		$_forums = bb_append_meta( $_forums, 'forum' );
		foreach ( $_forums as $f ) {
			$forums[(int) $f->forum_id] = $f;
			$forum_ids[] = (int) $f->forum_id;
			wp_cache_add( $f->forum_id, $f, 'bb_forum' );
			wp_cache_add( $f->forum_slug, $f->forum_id, 'bb_forum_slug' );
		}
		wp_cache_set( $key, $forum_ids, 'bb_forums' );
	}

	$forums = (array) apply_filters( 'get_forums', $forums );

	if ( $child_of || $hierarchical || $depth ) {

		$_forums = bb_get_forums_hierarchical( $child_of, $depth, $forums );

		if ( !is_array( $_forums ) )
			return false;

		$_forums = (array) bb_flatten_array( $_forums, $cut_branch );

		foreach ( array_keys($_forums) as $_id )
			$_forums[$_id] = $forums[$_id];

		$forums = $_forums;
	}

	if ( !is_callable($callback) )
		return $forums;

	if ( !is_array($callback_args) )
		$callback_args = array();

	foreach ( array_keys($forums) as $f ) :
		$_callback_args = $callback_args;
		array_push( $_callback_args, $forums[$f]->forum_id );
		if ( false == call_user_func_array( $callback, $_callback_args ) ) // $forum_id will be last arg;
			unset($forums[$f]);
	endforeach;
	return $forums;
}

function bb_get_forum( $id ) {
	global $bbdb;

	if ( !is_numeric($id) ) {
		list($slug, $sql) = bb_get_sql_from_slug( 'forum', $id );
		$id = wp_cache_get( $slug, 'bb_forum_slug' );
	}

	// not else
	if ( is_numeric($id) )
		$sql = $bbdb->prepare( "forum_id = %d", $id );

	if ( 0 === $id || empty( $sql ) )
		return false;

	// $where is NOT bbdb:prepared
	if ( $where = apply_filters( 'get_forum_where', '' ) ) {
		$forum = $bbdb->get_row( "SELECT * FROM $bbdb->forums WHERE $sql $where" );
		return bb_append_meta( $forum, 'forum' );
	}

	if ( is_numeric($id) && false !== $forum = wp_cache_get( $id, 'bb_forum' ) )
		return $forum;

	$forum = $bbdb->get_row( "SELECT * FROM $bbdb->forums WHERE $sql" );
	$forum = bb_append_meta( $forum, 'forum' );
	wp_cache_set( $forum->forum_id, $forum, 'bb_forum' );
	wp_cache_add( $forum->forum_slug, $forum->forum_id, 'bb_forum_slug' );

	return $forum;
}
