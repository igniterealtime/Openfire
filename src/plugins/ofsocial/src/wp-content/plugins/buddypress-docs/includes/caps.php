<?php

/**
 * BuddyPress Docs capabilities and roles
 *
 * Inspired by bbPress 2.0
 *
 * @package BuddyPress_Docs
 * @subpackage Caps
 * @since 1.2
 */

// Exit if accessed directly
if ( !defined( 'ABSPATH' ) ) exit;

/**
 * Map our caps to WP's
 *
 * @since 1.2
 *
 * @param array $caps Capabilities for meta capability
 * @param string $cap Capability name
 * @param int $user_id User id
 * @param mixed $args Arguments passed to map_meta_cap filter
 * @return array Actual capabilities for meta capability
 */
function bp_docs_map_meta_caps( $caps, $cap, $user_id, $args ) {
	global $post, $wp_post_types;

	// No need to continue if BuddyPress Docs hasn't been initialized
	$pt = bp_docs_get_post_type_name();
	if ( empty( $pt ) ) {
		return $caps;
	}

	switch ( $cap ) {
		case 'bp_docs_create' :
			// Reset all caps. We bake from scratch
			$caps = array();

			// Should never get here if there's no user
			if ( ! $user_id ) {
				$caps[] = 'do_not_allow';

			// All logged-in users can create
			} else {
				$caps[] = 'exist';
			}

			break;

		case 'bp_docs_read' :
		case 'bp_docs_edit' :
		case 'bp_docs_view_history' :
		case 'bp_docs_manage' :
		case 'bp_docs_read_comments' :
		case 'bp_docs_post_comments' :
			// Reset all caps. We bake from scratch
			$caps = array();

			$doc = bp_docs_get_doc_for_caps( $args );
			if ( empty( $doc ) || ( $doc instanceof WP_Post && 0 === $doc->ID && bp_docs_get_post_type_name() === $doc->post_type ) ) {
				return array( 'read' );
			}

			// Special case: view_history requires post revisions
			// @todo Move this to addon-history
			if ( 'bp_docs_view_history' === $cap && ! wp_revisions_enabled( $doc ) ) {
				return array( 'do_not_allow' );
			}

			// Admins can do everything
			if ( user_can( $user_id, 'bp_moderate' ) ) {
				return array( 'exist' );
			}

			$doc_settings = bp_docs_get_doc_settings( $doc->ID );

			// Caps are stored without the 'bp_docs_' prefix,
			// mostly for legacy reasons
			$cap_name = substr( $cap, 8 );

			switch ( $doc_settings[ $cap_name ] ) {
				case 'anyone' :
					$caps[] = 'exist';
					break;

				case 'loggedin' :
					if ( ! $user_id ) {
						$caps[] = 'do_not_allow';
					} else {
						$caps[] = 'exist';
					}

					break;

				case 'creator' :
					if ( $user_id == $doc->post_author ) {
						$caps[] = 'exist';
					} else {
						$caps[] = 'do_not_allow';
					}

					break;

				case 'no-one' :
				default :
					$caps[] = 'do_not_allow';
					break;

				// Group-specific caps get passed to filter
			}

			break;
	}

	return apply_filters( 'bp_docs_map_meta_caps', $caps, $cap, $user_id, $args );
}
add_filter( 'map_meta_cap', 'bp_docs_map_meta_caps', 10, 4 );

/**
 * Load up the doc to check against for meta cap mapping
 *
 * @since 1.2
 *
 * @param array $args The $args argument passed by the map_meta_cap filter. May be empty
 * @return obj $doc
 */
function bp_docs_get_doc_for_caps( $args = array() ) {
	global $post;

	$doc_id = 0;
	$doc = NULL;
	if ( isset( $args[0] ) ) {
		$doc_id = $args[0];
		$doc = get_post( $doc_id );
	} else if ( isset( $post->ID ) ) {
		$doc = $post;
	}

	if ( ! is_a( $doc, 'WP_Post' ) || bp_docs_get_post_type_name() !== $doc->post_type ) {
		$doc = null;
	}

	return apply_filters( 'bp_docs_get_doc_for_caps', $doc, $args );
}

/**
 * Utility function for checking whether a doc's permission settings are set to Custom, and that
 * a given user has access to the doc for that particular action.
 *
 * @since 1.2
 *
 * @param
 */
function bp_docs_user_has_custom_access( $user_id, $doc_settings, $key ) {
	// Default to true, so that if it's not set to 'custom', you pass through
	$has_access = true;

	if ( isset( $doc_settings[$key] ) && 'custom' == $doc_settings[$key] && is_array( $doc_settings[$key] ) ) {
		$has_access = in_array( $user_id, $doc_settings[$key] );
	}

	return $has_access;
}
