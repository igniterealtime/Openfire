<?php
/**
 * BuddyPress taxonomy functions.
 *
 * Most BuddyPress taxonomy functions are wrappers for their WordPress counterparts.
 * Because BuddyPress can be activated in various ways in a network environment, we
 * must switch to the root blog before using the WP functions.
 *
 * @since 2.2.0
 *
 * @package BuddyPress
 * @subpackage Core
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Register our default taxonomies.
 *
 * @since 2.2.0
 */
function bp_register_default_taxonomies() {
	// Member Type.
	register_taxonomy( 'bp_member_type', 'user', array(
		'public' => false,
	) );
}
add_action( 'bp_register_taxonomies', 'bp_register_default_taxonomies' );

/**
 * Set taxonomy terms on a BuddyPress object.
 *
 * @since 2.2.0
 *
 * @see wp_set_object_terms() for a full description of function and parameters.
 *
 * @param int          $object_id Object ID.
 * @param string|array $terms     Term or terms to set.
 * @param string       $taxonomy  Taxonomy name.
 * @param bool         $append    Optional. True to append terms to existing terms. Default: false.
 *
 * @return array Array of term taxonomy IDs.
 */
function bp_set_object_terms( $object_id, $terms, $taxonomy, $append = false ) {
	$is_root_blog = bp_is_root_blog();

	if ( ! $is_root_blog ) {
		switch_to_blog( bp_get_root_blog_id() );
	}

	$retval = wp_set_object_terms( $object_id, $terms, $taxonomy, $append );

	if ( ! $is_root_blog ) {
		restore_current_blog();
	}

	return $retval;
}

/**
 * Get taxonomy terms for a BuddyPress object.
 *
 * @since 2.2.0
 *
 * @see wp_get_object_terms() for a full description of function and parameters.
 *
 * @param int|array    $object_ids ID or IDs of objects.
 * @param string|array $taxonomies Name or names of taxonomies to match.
 * @param array        $args       See {@see wp_get_object_terms()}.
 *
 * @return array
 */
function bp_get_object_terms( $object_ids, $taxonomies, $args = array() ) {
	$is_root_blog = bp_is_root_blog();

	if ( ! $is_root_blog ) {
		switch_to_blog( bp_get_root_blog_id() );
	}

	$retval = wp_get_object_terms( $object_ids, $taxonomies, $args );

	if ( ! $is_root_blog ) {
		restore_current_blog();
	}

	return $retval;
}

/**
 * Remove taxonomy terms on a BuddyPress object.
 *
 * @since 2.3.0
 *
 * @see wp_remove_object_terms() for a full description of function and parameters.
 *
 * @param int          $object_id Object ID.
 * @param string|array $terms     Term or terms to remove.
 * @param string       $taxonomy  Taxonomy name.
 *
 * @return bool|WP_Error True on success, false or WP_Error on failure.
 */
function bp_remove_object_terms( $object_id, $terms, $taxonomy ) {
	$is_root_blog = bp_is_root_blog();

	if ( ! $is_root_blog ) {
		switch_to_blog( bp_get_root_blog_id() );
	}

	$retval = wp_remove_object_terms( $object_id, $terms, $taxonomy );

	if ( ! $is_root_blog ) {
		restore_current_blog();
	}

	return $retval;
}
