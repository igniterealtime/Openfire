<?php
/**
 * Deprecated functions.
 *
 * @deprecated 2.3.0
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Return the referrer URL without the http(s)://
 *
 * @deprecated 2.3.0
 *
 * @return string The referrer URL.
 */
function bp_core_referrer() {
	_deprecated_function( __FUNCTION__, '2.3.0', 'bp_get_referer_path()' );
	$referer = explode( '/', wp_get_referer() );
	unset( $referer[0], $referer[1], $referer[2] );
	return implode( '/', $referer );
}

