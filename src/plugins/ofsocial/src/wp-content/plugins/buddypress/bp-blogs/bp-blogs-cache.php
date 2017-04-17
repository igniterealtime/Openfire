<?php
/**
 * BuddyPress Blogs Caching.
 *
 * Caching functions handle the clearing of cached objects and pages on specific
 * actions throughout BuddyPress.
 *
 * @package BuddyPress
 * @subpackage BlogsCache
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Slurp up blogmeta for a specified set of blogs.
 *
 * It grabs all blogmeta associated with all of the blogs passed
 * in $blog_ids and adds it to the WP cache. This improves efficiency when
 * using querying blogmeta inline.
 *
 * @param int|string|array|bool $blog_ids Accepts a single blog ID, or a comma-
 *                                        separated list or array of blog IDs.
 */
function bp_blogs_update_meta_cache( $blog_ids = false ) {
	$cache_args = array(
		'object_ids' 	   => $blog_ids,
		'object_type' 	   => buddypress()->blogs->id,
		'object_column'    => 'blog_id',
		'cache_group'      => 'blog_meta',
		'meta_table' 	   => buddypress()->blogs->table_name_blogmeta,
	);

	bp_update_meta_cache( $cache_args );
}
/**
 * Clear the blog object cache.
 *
 * @since 1.0.0
 *
 * @param int $blog_id ID of the current blog.
 * @param int $user_id ID of the user whose blog cache should be cleared.
 */
function bp_blogs_clear_blog_object_cache( $blog_id = 0, $user_id = 0 ) {
	if ( ! empty( $user_id ) ) {
		wp_cache_delete( 'bp_blogs_of_user_'        . $user_id, 'bp' );
		wp_cache_delete( 'bp_total_blogs_for_user_' . $user_id, 'bp' );
	}

	wp_cache_delete( 'bp_total_blogs', 'bp' );
}

// List actions to clear object caches on.
add_action( 'bp_blogs_remove_blog_for_user', 'bp_blogs_clear_blog_object_cache', 10, 2 );
add_action( 'wpmu_new_blog',                 'bp_blogs_clear_blog_object_cache', 10, 2 );
add_action( 'bp_blogs_remove_blog',          'bp_blogs_clear_blog_object_cache' );

// List actions to clear super cached pages on, if super cache is installed.
add_action( 'bp_blogs_remove_data_for_blog', 'bp_core_clear_cache' );
add_action( 'bp_blogs_remove_comment',       'bp_core_clear_cache' );
add_action( 'bp_blogs_remove_post',          'bp_core_clear_cache' );
add_action( 'bp_blogs_remove_blog_for_user', 'bp_core_clear_cache' );
add_action( 'bp_blogs_remove_blog',          'bp_core_clear_cache' );
add_action( 'bp_blogs_new_blog_comment',     'bp_core_clear_cache' );
add_action( 'bp_blogs_new_blog_post',        'bp_core_clear_cache' );
add_action( 'bp_blogs_new_blog',             'bp_core_clear_cache' );
add_action( 'bp_blogs_remove_data',          'bp_core_clear_cache' );
