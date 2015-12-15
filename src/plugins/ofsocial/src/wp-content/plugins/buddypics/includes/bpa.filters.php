<?php

/**
 * BP-ALBUM FILTERS CLASS
 *
 * @since 0.1.8.0
 * @package BP-Album
 * @subpackage Filters
 * @license GPL v2.0
 * @link http://code.google.com/p/buddypress-media/
 *
 * ========================================================================================================
 */


/**
 * bp_album_make_nofollow_filter()
 *
 * This filter is a direct copy of "bp_activity_make_nofollow_filter_callback". It is pasted here and re-named because
 * if the user disables the activity stream, BuddyPress disables bp_activity_make_nofollow_filter_callback, causing an
 * error if we use it.
 *
 * @version 0.1.8.12
 * @since 0.1.8.0
 */
function bp_album_make_nofollow_filter( $text ) {
	return preg_replace_callback( '|<a (.+?)>|i', 'bp_album_make_nofollow_filter_callback', $text );
}
	function bp_album_make_nofollow_filter_callback( $matches ) {
		$text = $matches[1];
		$text = str_replace( array( ' rel="nofollow"', " rel='nofollow'"), '', $text );
		return "<a $text rel=\"nofollow\">";
	}

add_filter( 'bp_album_title_before_save', 'wp_filter_kses', 1 );
add_filter( 'bp_album_title_before_save', 'strip_tags', 1 );

add_filter( 'bp_album_description_before_save', 'wp_filter_kses', 1 );
add_filter( 'bp_album_description_before_save', 'strip_tags', 1 );

add_filter( 'bp_album_get_picture_title', 'wp_filter_kses', 1 );
add_filter( 'bp_album_get_picture_title', 'wptexturize' );
add_filter( 'bp_album_get_picture_title', 'convert_smilies' );
add_filter( 'bp_album_get_picture_title', 'convert_chars' );

add_filter( 'bp_album_get_picture_title_truncate', 'wp_filter_kses', 1 );
add_filter( 'bp_album_get_picture_title_truncate', 'wptexturize' );
add_filter( 'bp_album_get_picture_title_truncate', 'convert_smilies' );
add_filter( 'bp_album_get_picture_title_truncate', 'convert_chars' );

add_filter( 'bp_album_get_picture_desc', 'wp_filter_kses', 1 );
add_filter( 'bp_album_get_picture_desc', 'force_balance_tags' );
add_filter( 'bp_album_get_picture_desc', 'wptexturize' );
add_filter( 'bp_album_get_picture_desc', 'convert_smilies' );
add_filter( 'bp_album_get_picture_desc', 'convert_chars' );
add_filter( 'bp_album_get_picture_desc', 'make_clickable' );
add_filter( 'bp_album_get_picture_desc', 'bp_album_make_nofollow_filter' );
add_filter( 'bp_album_get_picture_desc', 'wpautop' );

add_filter( 'bp_album_get_picture_desc_truncate', 'wp_filter_kses', 1 );
add_filter( 'bp_album_get_picture_desc_truncate', 'force_balance_tags' );
add_filter( 'bp_album_get_picture_desc_truncate', 'wptexturize' );
add_filter( 'bp_album_get_picture_desc_truncate', 'convert_smilies' );
add_filter( 'bp_album_get_picture_desc_truncate', 'convert_chars' );
add_filter( 'bp_album_get_picture_desc_truncate', 'make_clickable' );
add_filter( 'bp_album_get_picture_desc_truncate', 'bp_album_make_nofollow_filter' );

?>