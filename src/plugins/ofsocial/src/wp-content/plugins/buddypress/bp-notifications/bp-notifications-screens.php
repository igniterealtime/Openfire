<?php
/**
 * BuddyPress Notifications Screen Functions.
 *
 * Screen functions are the controllers of BuddyPress. They will execute when
 * their specific URL is caught. They will first save or manipulate data using
 * business functions, then pass on the user to a template file.
 *
 * @package BuddyPress
 * @subpackage NotificationsScreens
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Catch and route the 'unread' notifications screen.
 *
 * @since 1.9.0
 */
function bp_notifications_screen_unread() {

	/**
	 * Fires right before the loading of the notifications unread screen template file.
	 *
	 * @since 1.9.0
	 */
	do_action( 'bp_notifications_screen_unread' );

	/**
	 * Filters the template to load for the notifications unread screen.
	 *
	 * @since 1.9.0
	 *
	 * @param string $template Path to the notifications unread template to load.
	 */
	bp_core_load_template( apply_filters( 'bp_notifications_template_unread', 'members/single/home' ) );
}

/**
 * Catch and route the 'read' notifications screen.
 *
 * @since 1.9.0
 */
function bp_notifications_screen_read() {

	/**
	 * Fires right before the loading of the notifications read screen template file.
	 *
	 * @since 1.9.0
	 */
	do_action( 'bp_notifications_screen_read' );

	/**
	 * Filters the template to load for the notifications read screen.
	 *
	 * @since 1.9.0
	 *
	 * @param string $template Path to the notifications read template to load.
	 */
	bp_core_load_template( apply_filters( 'bp_notifications_template_read', 'members/single/home' ) );
}

/**
 * Catch and route the 'settings' notifications screen.
 *
 * @since 1.9.0
 */
function bp_notifications_screen_settings() {

}
