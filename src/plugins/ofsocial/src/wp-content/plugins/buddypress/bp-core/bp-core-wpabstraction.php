<?php
/**
 * WordPress Abstraction.
 *
 * @package BuddyPress
 *
 * The functions within this file will detect the version of WordPress you are
 * running and will alter the environment so BuddyPress can run regardless.
 *
 * The code below mostly contains function mappings. This file is subject to
 * change at any time.
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Parse the WordPress core version number into the major release.
 *
 * @since 1.5.2
 *
 * @global string $wp_version
 *
 * @return string $wp_version
 */
function bp_get_major_wp_version() {
	global $wp_version;

	return (float) $wp_version;
}

/**
 * Only add MS-specific abstraction functions if WordPress is not in multisite mode.
 */
if ( !is_multisite() ) {
	global $wpdb;

	$wpdb->base_prefix = $wpdb->prefix;
	$wpdb->blogid      = BP_ROOT_BLOG;

	if ( !function_exists( 'get_blog_option' ) ) {
		/**
		 * @see get_blog_option()
		 */
		function get_blog_option( $blog_id, $option_name, $default = false ) {
			return get_option( $option_name, $default );
		}
	}

	if ( ! function_exists( 'add_blog_option' ) ) {
		/**
		 * @see add_blog_option()
		 */
		function add_blog_option( $blog_id, $option_name, $option_value ) {
			return add_option( $option_name, $option_value );
		}
	}

	if ( !function_exists( 'update_blog_option' ) ) {
		/**
		 * @see update_blog_option()
		 */
		function update_blog_option( $blog_id, $option_name, $value ) {
			return update_option( $option_name, $value );
		}
	}

	if ( !function_exists( 'delete_blog_option' ) ) {
		/**
		 * @see delete_blog_option()
		 */
		function delete_blog_option( $blog_id, $option_name ) {
			return delete_option( $option_name );
		}
	}

	if ( !function_exists( 'switch_to_blog' ) ) {
		/**
		 * @see switch_to_blog()
		 */
		function switch_to_blog( $new_blog, $deprecated = null ) {
			return bp_get_root_blog_id();
		}
	}

	if ( !function_exists( 'restore_current_blog' ) ) {
		/**
		 * @see restore_current_blog()
		 */
		function restore_current_blog() {
			return bp_get_root_blog_id();
		}
	}

	if ( !function_exists( 'get_blogs_of_user' ) ) {
		/**
		 * @see get_blogs_of_user()
		 */
		function get_blogs_of_user( $user_id, $all = false ) {
			return false;
		}
	}

	if ( !function_exists( 'update_blog_status' ) ) {
		/**
		 * @see update_blog_status()
		 */
		function update_blog_status( $blog_id, $pref, $value, $deprecated = null ) {
			return true;
		}
	}

	if ( !function_exists( 'is_subdomain_install' ) ) {
		/**
		 * @see is_subdomain_install()
		 */
		function is_subdomain_install() {
			if ( ( defined( 'VHOST' ) && 'yes' == VHOST ) || ( defined( 'SUBDOMAIN_INSTALL' ) && SUBDOMAIN_INSTALL ) )
				return true;

			return false;
		}
	}
}

/**
 * Get SQL chunk for filtering spam users from member queries.
 *
 * @internal
 * @todo Why is this function defined in this file?
 *
 * @param string|bool $prefix Global table prefix.
 *
 * @return string SQL chunk.
 */
function bp_core_get_status_sql( $prefix = false ) {
	if ( !is_multisite() )
		return "{$prefix}user_status = 0";
	else
		return "{$prefix}spam = 0 AND {$prefix}deleted = 0 AND {$prefix}user_status = 0";
}

/**
 * Multibyte encoding fallback functions.
 *
 * The PHP multibyte encoding extension is not enabled by default. In cases where it is not enabled,
 * these functions provide a fallback.
 *
 * Borrowed from MediaWiki, under the GPLv2. Thanks!
 */
if ( !function_exists( 'mb_strlen' ) ) {
	/**
	 * Fallback implementation of mb_strlen(), hardcoded to UTF-8.
	 *
	 * @param string $str String to be measured.
	 * @param string $enc Optional. Encoding type. Ignored.
	 *
	 * @return int String length.
	 */
	function mb_strlen( $str, $enc = '' ) {
		$counts = count_chars( $str );
		$total = 0;

		// Count ASCII bytes
		for( $i = 0; $i < 0x80; $i++ ) {
			$total += $counts[$i];
		}

		// Count multibyte sequence heads
		for( $i = 0xc0; $i < 0xff; $i++ ) {
			$total += $counts[$i];
		}
		return $total;
	}
}

if ( !function_exists( 'mb_strpos' ) ) {
	/**
	 * Fallback implementation of mb_strpos(), hardcoded to UTF-8.
	 *
	 * @param string $haystack String to search in.
	 * @param string $needle String to search for.
	 * @param int    $offset Optional. Start position for the search. Default: 0.
	 * @param string $encoding Optional. Encoding type. Ignored.
	 *
	 * @return int|bool Position of needle in haystack if found, else false.
	 */
	function mb_strpos( $haystack, $needle, $offset = 0, $encoding = '' ) {
		$needle = preg_quote( $needle, '/' );

		$ar = array();
		preg_match( '/' . $needle . '/u', $haystack, $ar, PREG_OFFSET_CAPTURE, $offset );

		if( isset( $ar[0][1] ) ) {
			return $ar[0][1];
		} else {
			return false;
		}
	}
}

if ( !function_exists( 'mb_strrpos' ) ) {
	/**
	 * Fallback implementation of mb_strrpos(), hardcoded to UTF-8.
	 *
	 * @param string $haystack String to search in.
	 * @param string $needle String to search for.
	 * @param int    $offset Optional. Start position for the search. Default: 0.
	 * @param string $encoding Optional. Encoding type. Ignored.
	 *
	 * @return int Position of last needle in haystack if found, else false.
	 */
	function mb_strrpos( $haystack, $needle, $offset = 0, $encoding = '' ) {
		$needle = preg_quote( $needle, '/' );

		$ar = array();
		preg_match_all( '/' . $needle . '/u', $haystack, $ar, PREG_OFFSET_CAPTURE, $offset );

		if( isset( $ar[0] ) && count( $ar[0] ) > 0 &&
			isset( $ar[0][count( $ar[0] ) - 1][1] ) ) {
			return $ar[0][count( $ar[0] ) - 1][1];
		} else {
			return false;
		}
	}
}
