<?php
/**
 * BuddyPress Core Caching Functions.
 *
 * @package BuddyPress
 *
 * Caching functions handle the clearing of cached objects and pages on specific
 * actions throughout BuddyPress.
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Prune the WP Super Cache.
 *
 * @see prune_super_cache()
 *
 * When wp-super-cache is installed this function will clear cached pages
 * so that success/error messages are not cached, or time sensitive content.
 *
 * @return int
 */
function bp_core_clear_cache() {
	global $cache_path;

	if ( function_exists( 'prune_super_cache' ) ) {

		/**
		 * Fires before the pruning of WP Super Cache.
		 *
		 * @since 1.0.0
		 */
		do_action( 'bp_core_clear_cache' );
		return prune_super_cache( $cache_path, true );
	}
}

/**
 * Clear all cached objects for a user, or those that a user is part of.
 *
 * @param string $user_id User ID to delete cache for.
 */
function bp_core_clear_user_object_cache( $user_id ) {
	wp_cache_delete( 'bp_user_' . $user_id, 'bp' );
}

/**
 * Clear member count caches and transients.
 */
function bp_core_clear_member_count_caches() {
	wp_cache_delete( 'bp_total_member_count', 'bp' );
	delete_transient( 'bp_active_member_count' );
}
add_action( 'bp_core_activated_user',         'bp_core_clear_member_count_caches' );
add_action( 'bp_core_process_spammer_status', 'bp_core_clear_member_count_caches' );
add_action( 'bp_core_deleted_account',        'bp_core_clear_member_count_caches' );
add_action( 'bp_first_activity_for_member',   'bp_core_clear_member_count_caches' );
add_action( 'deleted_user',                   'bp_core_clear_member_count_caches' );

/**
 * Clear the directory_pages cache when one of the pages is updated.
 *
 * @since 2.0.0
 *
 * @param int $post_id ID of the page that was saved.
 */
function bp_core_clear_directory_pages_cache_page_edit( $post_id ) {
	if ( ! bp_is_root_blog() ) {
		return;
	}

	// Bail if BP is not defined here.
	if ( ! buddypress() ) {
		return;
	}

	$page_ids = bp_core_get_directory_page_ids( 'all' );

	if ( ! in_array( $post_id, (array) $page_ids ) ) {
		return;
	}

	wp_cache_delete( 'directory_pages', 'bp' );
}
add_action( 'save_post', 'bp_core_clear_directory_pages_cache_page_edit' );

/**
 * Clear the directory_pages cache when the bp-pages option is updated.
 *
 * @since 2.0.0
 *
 * @param string $option Option name.
 */
function bp_core_clear_directory_pages_cache_settings_edit( $option ) {
	if ( 'bp-pages' === $option ) {
		wp_cache_delete( 'directory_pages', 'bp' );
	}
}
add_action( 'update_option', 'bp_core_clear_directory_pages_cache_settings_edit' );

/**
 * Clear the root_blog_options cache when any of its options are updated.
 *
 * @since 2.0.0
 *
 * @param string $option Option name.
 */
function bp_core_clear_root_options_cache( $option ) {
	$keys = array_keys( bp_get_default_options() );
	$keys = array_merge( $keys, array(
		'registration',
		'avatar_default',
		'tags_blog_id',
		'sitewide_tags_blog',
		'registration',
		'fileupload_mask',
	) );

	if ( in_array( $option, $keys ) ) {
		wp_cache_delete( 'root_blog_options', 'bp' );
	}
}
add_action( 'update_option', 'bp_core_clear_root_options_cache' );
add_action( 'update_site_option', 'bp_core_clear_root_options_cache' );
add_action( 'add_option', 'bp_core_clear_root_options_cache' );
add_action( 'add_site_option', 'bp_core_clear_root_options_cache' );

/**
 * Determine which items from a list do not have cached values.
 *
 * @since 2.0.0
 *
 * @param array  $item_ids    ID list.
 * @param string $cache_group The cache group to check against.
 *
 * @return array
 */
function bp_get_non_cached_ids( $item_ids, $cache_group ) {
	$uncached = array();

	foreach ( $item_ids as $item_id ) {
		$item_id = (int) $item_id;
		if ( false === wp_cache_get( $item_id, $cache_group ) ) {
			$uncached[] = $item_id;
		}
	}

	return $uncached;
}

/**
 * Update the metadata cache for the specified objects.
 *
 * Based on WordPress's {@link update_meta_cache()}, this function primes the
 * cache with metadata related to a set of objects. This is typically done when
 * querying for a loop of objects; pre-fetching metadata for each queried
 * object can lead to dramatic performance improvements when using metadata
 * in the context of template loops.
 *
 * @since 1.6.0
 *
 * @global object $wpdb WordPress database object for queries..
 *
 * @param array $args {
 *     Array of arguments.
 *     @type array|string $object_ids       List of object IDs to fetch metadata for.
 *                                          Accepts an array or a comma-separated list of numeric IDs.
 *     @type string       $object_type      The type of object, eg 'groups' or 'activity'.
 *     @type string       $meta_table       The name of the metadata table being queried.
 *     @type string       $object_column    Optional. The name of the database column where IDs
 *                                          (those provided by $object_ids) are found. Eg, 'group_id'
 *                                          for the groups metadata tables. Default: $object_type . '_id'.
 *     @type string       $cache_key_prefix Optional. The prefix to use when creating
 *                                          cache key names. Default: the value of $meta_table.
 * }
 *
 * @return array|bool Metadata cache for the specified objects, or false on failure.
 */
function bp_update_meta_cache( $args = array() ) {
	global $wpdb;

	$defaults = array(
		'object_ids' 	   => array(), // Comma-separated list or array of item ids
		'object_type' 	   => '',      // Canonical component id: groups, members, etc
		'cache_group'      => '',      // Cache group
		'meta_table' 	   => '',      // Name of the table containing the metadata
		'object_column'    => '',      // DB column for the object ids (group_id, etc)
		'cache_key_prefix' => ''       // Prefix to use when creating cache key names. Eg 'bp_groups_groupmeta'
	);
	$r = wp_parse_args( $args, $defaults );
	extract( $r );

	if ( empty( $object_ids ) || empty( $object_type ) || empty( $meta_table ) || empty( $cache_group ) ) {
		return false;
	}

	if ( empty( $cache_key_prefix ) ) {
		$cache_key_prefix = $meta_table;
	}

	if ( empty( $object_column ) ) {
		$object_column = $object_type . '_id';
	}

	if ( ! $cache_group ) {
		return false;
	}

	$object_ids   = wp_parse_id_list( $object_ids );
	$uncached_ids = bp_get_non_cached_ids( $object_ids, $cache_group );

	$cache = array();

	// Get meta info
	if ( ! empty( $uncached_ids ) ) {
		$id_list   = join( ',', wp_parse_id_list( $uncached_ids ) );
		$meta_list = $wpdb->get_results( esc_sql( "SELECT {$object_column}, meta_key, meta_value FROM {$meta_table} WHERE {$object_column} IN ({$id_list})" ), ARRAY_A );

		if ( ! empty( $meta_list ) ) {
			foreach ( $meta_list as $metarow ) {
				$mpid = intval( $metarow[$object_column] );
				$mkey = $metarow['meta_key'];
				$mval = $metarow['meta_value'];

				// Force subkeys to be array type:
				if ( !isset( $cache[$mpid] ) || !is_array( $cache[$mpid] ) )
					$cache[$mpid] = array();
				if ( !isset( $cache[$mpid][$mkey] ) || !is_array( $cache[$mpid][$mkey] ) )
					$cache[$mpid][$mkey] = array();

				// Add a value to the current pid/key:
				$cache[$mpid][$mkey][] = $mval;
			}
		}

		foreach ( $uncached_ids as $uncached_id ) {
			// Cache empty values as well
			if ( ! isset( $cache[ $uncached_id ] ) ) {
				$cache[ $uncached_id ] = array();
			}

			wp_cache_set( $uncached_id, $cache[ $uncached_id ], $cache_group );
		}
	}

	return $cache;
}
