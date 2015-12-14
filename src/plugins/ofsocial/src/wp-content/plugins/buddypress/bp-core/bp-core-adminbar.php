<?php
/**
 * BuddyPress Core Toolbar.
 *
 * Handles the core functions related to the WordPress Toolbar.
 *
 * @package BuddyPress
 * @subpackage Core
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Add the secondary BuddyPress area to the my-account menu.
 *
 * @since 1.6.0
 *
 * @global WP_Admin_Bar $wp_admin_bar
 */
function bp_admin_bar_my_account_root() {
	global $wp_admin_bar;

	// Bail if this is an ajax request
	if ( !bp_use_wp_admin_bar() || defined( 'DOING_AJAX' ) )
		return;

	// Only add menu for logged in user
	if ( is_user_logged_in() ) {

		// Add secondary parent item for all BuddyPress components
		$wp_admin_bar->add_menu( array(
			'parent'    => 'my-account',
			'id'        => 'my-account-buddypress',
			'title'     => __( 'My Account', 'buddypress' ),
			'group'     => true,
			'meta'      => array(
				'class' => 'ab-sub-secondary'
			)
		) );

		// Remove 'Edit' post link as it's not applicable to BP
		// Remove when https://core.trac.wordpress.org/ticket/29538 is addressed
		if ( is_buddypress() ) {
			$wp_admin_bar->remove_node( 'edit' );
		}
	}
}
add_action( 'admin_bar_menu', 'bp_admin_bar_my_account_root', 100 );

/**
 * Handle the Toolbar/BuddyBar business.
 *
 * @since 1.2.0
 *
 * @global string $wp_version
 * @uses bp_get_option()
 * @uses is_user_logged_in()
 * @uses bp_use_wp_admin_bar()
 * @uses show_admin_bar()
 * @uses add_action() To hook 'bp_adminbar_logo' to 'bp_adminbar_logo'.
 * @uses add_action() To hook 'bp_adminbar_login_menu' to 'bp_adminbar_menus'.
 * @uses add_action() To hook 'bp_adminbar_account_menu' to 'bp_adminbar_menus'.
 * @uses add_action() To hook 'bp_adminbar_thisblog_menu' to 'bp_adminbar_menus'.
 * @uses add_action() To hook 'bp_adminbar_random_menu' to 'bp_adminbar_menus'.
 * @uses add_action() To hook 'bp_core_admin_bar' to 'wp_footer'.
 * @uses add_action() To hook 'bp_core_admin_bar' to 'admin_footer'.
 */
function bp_core_load_admin_bar() {

	// Show the Toolbar for logged out users
	if ( ! is_user_logged_in() && (int) bp_get_option( 'hide-loggedout-adminbar' ) != 1 ) {
		show_admin_bar( true );
	}

	// Hide the WordPress Toolbar and show the BuddyBar
	if ( ! bp_use_wp_admin_bar() ) {
		_doing_it_wrong( __FUNCTION__, __( 'The BuddyBar is no longer supported. Please migrate to the WordPress toolbar as soon as possible.', 'buddypress' ), '2.1.0' );

		// Keep the WP Toolbar from loading
		show_admin_bar( false );

		// Actions used to build the BP Toolbar
		add_action( 'bp_adminbar_logo',  'bp_adminbar_logo'               );
		add_action( 'bp_adminbar_menus', 'bp_adminbar_login_menu',    2   );
		add_action( 'bp_adminbar_menus', 'bp_adminbar_account_menu',  4   );
		add_action( 'bp_adminbar_menus', 'bp_adminbar_thisblog_menu', 6   );
		add_action( 'bp_adminbar_menus', 'bp_adminbar_random_menu',   100 );

		// Actions used to append BP Toolbar to footer
		add_action( 'wp_footer',    'bp_core_admin_bar', 8 );
		add_action( 'admin_footer', 'bp_core_admin_bar'    );
	}
}
add_action( 'init', 'bp_core_load_admin_bar', 9 );

/**
 * Handle the enqueueing of toolbar CSS.
 *
 * This function exists mostly for backwards compatibility reasons, so anyone
 * previously unhooking this function can continue to do so. It's hooked to
 * the `bp_init` action in `bp-core-actions.php`.
 *
 * @since 1.5.0
 */
function bp_core_load_admin_bar_css() {
	add_action( 'bp_enqueue_scripts',       'bp_core_enqueue_admin_bar_css', 1 );
	add_action( 'bp_admin_enqueue_scripts', 'bp_core_enqueue_admin_bar_css', 1 );
}

/**
 * Enqueue supplemental WordPress Toolbar styling.
 *
 * @since 2.1.0
 *
 * @see bp_core_register_common_styles()
 * @see bp_core_load_admin_bar_css()
 */
function bp_core_enqueue_admin_bar_css() {

	// Bail if not using WordPress's admin bar or it's not showing on this
	// page request.
	if ( ! bp_use_wp_admin_bar() || ! is_admin_bar_showing() ) {
		return;
	}

	// Enqueue the additional adminbar css
	wp_enqueue_style( 'bp-admin-bar' );
}
