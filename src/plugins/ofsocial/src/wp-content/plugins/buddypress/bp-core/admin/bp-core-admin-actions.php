<?php
/**
 * BuddyPress Admin Actions.
 *
 * This file contains the actions that are used through-out BuddyPress Admin. They
 * are consolidated here to make searching for them easier, and to help developers
 * understand at a glance the order in which things occur.
 *
 * There are a few common places that additional actions can currently be found.
 *
 *  - BuddyPress: In {@link BuddyPress::setup_actions()} in BuddyPress.php
 *  - Admin: More in {@link bp_Admin::setup_actions()} in admin.php
 *
 * @package BuddyPress
 * @subpackage Admin
 * @see bp-core-actions.php
 * @see bp-core-filters.php
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Attach BuddyPress to WordPress.
 *
 * BuddyPress uses its own internal actions to help aid in third-party plugin
 * development, and to limit the amount of potential future code changes when
 * updates to WordPress core occur.
 *
 * These actions exist to create the concept of 'plugin dependencies'. They
 * provide a safe way for plugins to execute code *only* when BuddyPress is
 * installed and activated, without needing to do complicated guesswork.
 *
 * For more information on how this works, see the 'Plugin Dependency' section
 * near the bottom of this file.
 *
 *           v--WordPress Actions       v--BuddyPress Sub-actions
 */
add_action( 'admin_menu',              'bp_admin_menu'                    );
add_action( 'admin_init',              'bp_admin_init'                    );
add_action( 'admin_head',              'bp_admin_head'                    );
add_action( 'admin_notices',           'bp_admin_notices'                 );
add_action( 'admin_enqueue_scripts',   'bp_admin_enqueue_scripts'         );
add_action( 'network_admin_menu',      'bp_admin_menu'                    );
add_action( 'custom_menu_order',       'bp_admin_custom_menu_order'       );
add_action( 'menu_order',              'bp_admin_menu_order'              );
add_action( 'wpmu_new_blog',           'bp_new_site',               10, 6 );

// Hook on to admin_init
add_action( 'bp_admin_init', 'bp_setup_updater',          1000 );
add_action( 'bp_admin_init', 'bp_core_activation_notice', 1010 );
add_action( 'bp_admin_init', 'bp_register_importers'           );
add_action( 'bp_admin_init', 'bp_register_admin_style'         );
add_action( 'bp_admin_init', 'bp_register_admin_settings'      );
add_action( 'bp_admin_init', 'bp_do_activation_redirect', 1    );

// Add a new separator
add_action( 'bp_admin_menu', 'bp_admin_separator' );

/**
 * When a new site is created in a multisite installation, run the activation
 * routine on that site.
 *
 * @since 1.7.0
 *
 * @param int    $blog_id
 * @param int    $user_id
 * @param string $domain
 * @param string $path
 * @param int    $site_id
 * @param array  $meta
 */
function bp_new_site( $blog_id, $user_id, $domain, $path, $site_id, $meta ) {

	// Bail if plugin is not network activated
	if ( ! is_plugin_active_for_network( buddypress()->basename ) )
		return;

	// Switch to the new blog
	switch_to_blog( $blog_id );

	/**
	 * Fires the activation routine for a new site created in a multisite installation.
	 *
	 * @since 1.7.0
	 *
	 * @param int    $blog_id ID of the blog being installed to.
	 * @param int    $user_id ID of the user the install is for.
	 * @param string $domain  Domain to use with the install.
	 * @param string $path    Path to use with the install.
	 * @param int    $site_id ID of the site being installed to.
	 * @param array  $meta    Metadata to use with the site creation.
	 */
	do_action( 'bp_new_site', $blog_id, $user_id, $domain, $path, $site_id, $meta );

	// restore original blog
	restore_current_blog();
}

/** Sub-Actions ***************************************************************/

/**
 * Piggy back admin_init action.
 *
 * @since 1.7.0
 * @uses do_action() Calls 'bp_admin_init'.
 */
function bp_admin_init() {

	/**
	 * Fires inside the bp_admin_init function.
	 *
	 * @since 1.6.0
	 */
	do_action( 'bp_admin_init' );
}

/**
 * Piggy back admin_menu action.
 *
 * @since 1.7.0
 * @uses do_action() Calls 'bp_admin_menu'.
 */
function bp_admin_menu() {

	/**
	 * Fires inside the bp_admin_menu function.
	 *
	 * @since 1.7.0
	 */
	do_action( 'bp_admin_menu' );
}

/**
 * Piggy back admin_head action.
 *
 * @since 1.7.0
 * @uses do_action() Calls 'bp_admin_head'.
 */
function bp_admin_head() {

	/**
	 * Fires inside the bp_admin_head function.
	 *
	 * @since 1.6.0
	 */
	do_action( 'bp_admin_head' );
}

/**
 * Piggy back admin_notices action.
 *
 * @since 1.7.0
 * @uses do_action() Calls 'bp_admin_notices'.
 */
function bp_admin_notices() {

	/**
	 * Fires inside the bp_admin_notices function.
	 *
	 * @since 1.5.0
	 */
	do_action( 'bp_admin_notices' );
}

/**
 * Piggy back admin_enqueue_scripts action.
 *
 * @since 1.7.0
 *
 * @uses do_action() Calls 'bp_admin_enqueue_scripts''.
 *
 * @param string $hook_suffix The current admin page, passed to
 *                            'admin_enqueue_scripts'.
 */
function bp_admin_enqueue_scripts( $hook_suffix = '' ) {

	/**
	 * Fires inside the bp_admin_enqueue_scripts function.
	 *
	 * @since 1.7.0
	 *
	 * @param string $hook_suffix The current admin page, passed to admin_enqueue_scripts.
	 */
	do_action( 'bp_admin_enqueue_scripts', $hook_suffix );
}

/**
 * Dedicated action to register BuddyPress importers.
 *
 * @since 1.7.0
 * @uses do_action() Calls 'bp_admin_notices'.
 */
function bp_register_importers() {

	/**
	 * Fires inside the bp_register_importers function.
	 *
	 * Used to register a BuddyPress importer.
	 *
	 * @since 1.7.0
	 */
	do_action( 'bp_register_importers' );
}

/**
 * Dedicated action to register admin styles.
 *
 * @since 1.7.0
 * @uses do_action() Calls 'bp_admin_notices'.
 */
function bp_register_admin_style() {

	/**
	 * Fires inside the bp_register_admin_style function.
	 *
	 * @since 1.7.0
	 */
	do_action( 'bp_register_admin_style' );
}

/**
 * Dedicated action to register admin settings.
 *
 * @since 1.7.0
 * @uses do_action() Calls 'bp_register_admin_settings'.
 */
function bp_register_admin_settings() {

	/**
	 * Fires inside the bp_register_admin_settings function.
	 *
	 * @since 1.6.0
	 */
	do_action( 'bp_register_admin_settings' );
}
