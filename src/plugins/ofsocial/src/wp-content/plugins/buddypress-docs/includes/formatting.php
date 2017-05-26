<?php

/**
 * This file contains functions and filters that modify the appearance of content in the context
 * of BuddyPress Docs pages
 *
 * @package BuddyPress Docs
 * @since 1.0-beta
 */

/**
 * Reduces the length of excerpts on the BP Docs doc list
 *
 * @package BuddyPress Docs
 * @since 1.0-beta
 *
 * @uses apply_filters() Plugins can filter bp_docs_excerpt_length to change the default
 * @param int $length WordPress's default excerpt length
 * @return int $length The filtered excerpt length
 */
function bp_docs_excerpt_length( $length ) {
	if ( bp_docs_is_bp_docs_page() ) {
		$length = bp_docs_get_excerpt_length();
	}

	return $length;
}
add_filter( 'excerpt_length', 'bp_docs_excerpt_length' );

function bp_docs_get_excerpt_length() {
	$length = (int) get_option( 'bp-docs-excerpt-length', 20 );
	return apply_filters( 'bp_docs_excerpt_length', $length );
}

/**
 * Adds spaces after the commas in the tag edit textarea. Annoying that WP doesn't do this.
 *
 * @package BuddyPress Docs
 * @since 1.0-beta
 *
 * @param string $tags The string of tags
 * @return string $tags The string of tags with purdy commas
 */
function bp_docs_tags_comma_space( $tags ) {
	if ( bp_docs_is_bp_docs_page() ) {
		$tags = explode( ',', $tags );
		$tags = implode( ', ', $tags );
	}

	return $tags;
}
add_filter( 'terms_to_edit', 'bp_docs_tags_comma_space' );
