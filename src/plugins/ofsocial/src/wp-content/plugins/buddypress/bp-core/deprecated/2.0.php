<?php
/**
 * Deprecated Functions
 *
 * @package BuddyPress
 * @subpackage Core
 * @deprecated 2.0.0
 */

// Exit if accessed directly
defined( 'ABSPATH' ) || exit;

/**
 * @deprecated 2.0.0
 */
function bp_activity_clear_meta_cache_for_activity() {
	_deprecated_function( __FUNCTION__, '2.0.0', 'Use WP metadata API instead' );
}

/**
 * @deprecated 2.0.0
 */
function bp_blogs_catch_published_post() {
	_deprecated_function( __FUNCTION__, '2.0', 'bp_blogs_catch_transition_post_status()' );
}

/**
 * @deprecated 2.0.0
 */
function bp_messages_screen_inbox_mark_notifications() {
	_deprecated_function( __FUNCTION__, '2.0', 'bp_messages_screen_conversation_mark_notifications()' );
}
