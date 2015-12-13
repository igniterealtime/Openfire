<?php
/**
 * Plugin Dependency Action Hooks.
 *
 * The purpose of the following hooks is to mimic the behavior of something
 * called 'plugin dependency' which enables a plugin to have plugins of their
 * own in a safe and reliable way.
 *
 * We do this in BuddyPress by mirroring existing WordPress hooks in many places
 * allowing dependant plugins to hook into the BuddyPress specific ones, thus
 * guaranteeing proper code execution only when BuddyPress is active.
 *
 * The following functions are wrappers for hooks, allowing them to be
 * manually called and/or piggy-backed on top of other hooks if needed.
 *
 * @todo use anonymous functions when PHP minimum requirement allows (5.3)
 *
 * @package BuddyPress
 * @subpackage Core
 */

/**
 * Fire the 'bp_include' action, where plugins should include files.
 */
function bp_include() {

	/**
	 * Fires inside the 'bp_include' function, where plugins should include files.
	 *
	 * @since 1.2.5
	 */
	do_action( 'bp_include' );
}

/**
 * Fire the 'bp_setup_components' action, where plugins should initialize components.
 */
function bp_setup_components() {

	/**
	 * Fires inside the 'bp_setup_components' function, where plugins should initialize components.
	 *
	 * @since 1.6.0
	 */
	do_action( 'bp_setup_components' );
}

/**
 * Fire the 'bp_setup_canonical_stack' action, where plugins should set up their canonical URL.
 */
function bp_setup_canonical_stack() {

	/**
	 * Fires inside the 'bp_setup_canonical_stack' function, where plugins should set up their canonical URL.
	 *
	 * @since 2.1.0
	 */
	do_action( 'bp_setup_canonical_stack' );
}

/**
 * Fire the 'bp_register_taxonomies' action, where plugins should register taxonomies.
 *
 * @since 2.2.0
 */
function bp_register_taxonomies() {

	/**
	 * Fires inside the 'bp_register_taxonomies' function, where plugins should register taxonomies.
	 *
	 * @since 2.2.0
	 */
	do_action( 'bp_register_taxonomies' );
}

/**
 * Fire the 'bp_setup_globals' action, where plugins should initialize global settings.
 */
function bp_setup_globals() {

	/**
	 * Fires inside the 'bp_setup_globals' function, where plugins should initialize global settings.
	 *
	 * @since 1.2.0
	 */
	do_action( 'bp_setup_globals' );
}

/**
 * Fire the 'bp_setup_nav' action, where plugins should register their navigation items.
 */
function bp_setup_nav() {

	/**
	 * Fires inside the 'bp_setup_nav' function, where plugins should register their navigation items.
	 *
	 * @since 1.2.0
	 */
	do_action( 'bp_setup_nav' );
}

/**
 * Fire the 'bp_setup_admin_bar' action, where plugins should add items to the WP admin bar.
 */
function bp_setup_admin_bar() {
	if ( bp_use_wp_admin_bar() ) {

		/**
		 * Fires inside the 'bp_setup_admin_bar' function, where plugins should add items to the WP admin bar.
		 *
		 * This hook will only fire if bp_use_wp_admin_bar() returns true.
		 *
		 * @since 1.5.0
		 */
		do_action( 'bp_setup_admin_bar' );
	}
}

/**
 * Fire the 'bp_setup_title' action, where plugins should modify the page title.
 */
function bp_setup_title() {

	/**
	 * Fires inside the 'bp_setup_title' function, where plugins should modify the page title.
	 *
	 * @since 1.5.0
	 */
	do_action( 'bp_setup_title' );
}

/**
 * Fire the 'bp_register_widgets' action, where plugins should register widgets.
 */
function bp_setup_widgets() {

	/**
	 * Fires inside the 'bp_register_widgets' function, where plugins should register widgets.
	 *
	 * @since 1.2.0
	 */
	do_action( 'bp_register_widgets' );
}

/**
 * Fire the 'bp_register_member_types' action, where plugins should register member types.
 *
 * @since 2.3.0
 */
function bp_register_member_types() {

	/**
	 * Fires inside bp_register_member_types(), so plugins can register member types.
	 *
	 * @since 2.3.0
	 */
	do_action( 'bp_register_member_types' );
}

/**
 * Fire the 'bp_setup_cache_groups' action, where cache groups are registered.
 *
 * @since 2.2.0
 */
function bp_setup_cache_groups() {

	/**
	 * Fires inside the 'bp_setup_cache_groups' function, where cache groups are registered.
	 *
	 * @since 2.2.0
	 */
	do_action( 'bp_setup_cache_groups' );
}

/**
 * Set up the currently logged-in user.
 *
 * We white-list the WordPress customizer which purposely loads the user early.
 *
 * @link https://buddypress.trac.wordpress.org/ticket/6046
 * @link https://core.trac.wordpress.org/ticket/24169
 *
 * @uses did_action() To make sure the user isn't loaded out of order.
 * @uses do_action() Calls 'bp_setup_current_user'.
 */
function bp_setup_current_user() {

	// If the current user is being setup before the "init" action has fired,
	// strange (and difficult to debug) role/capability issues will occur.
	if ( ! isset( $GLOBALS['wp_customize'] ) && ! did_action( 'after_setup_theme' ) ) {
		_doing_it_wrong( __FUNCTION__, __( 'The current user is being initialized without using $wp->init().', 'buddypress' ), '1.7' );
	}

	/**
	 * Fires to set up the current user setup process.
	 *
	 * @since 1.7.0
	 */
	do_action( 'bp_setup_current_user' );
}

/**
 * Fire the 'bp_init' action, BuddyPress's main initialization hook.
 */
function bp_init() {

	/**
	 * Fires inside the 'bp_init' function, BuddyPress' main initialization hook.
	 *
	 * @since 1.2.0
	 */
	do_action( 'bp_init' );
}

/**
 * Fire the 'bp_loaded' action, which fires after BP's core plugin files have been loaded.
 *
 * Attached to 'plugins_loaded'.
 */
function bp_loaded() {

	/**
	 * Fires inside the 'bp_loaded' function, which fires after BP's core plugin files have been loaded.
	 *
	 * @since 1.2.5
	 */
	do_action( 'bp_loaded' );
}

/**
 * Fire the 'bp_ready' action, which runs after BP is set up and the page is about to render.
 *
 * Attached to 'wp'.
 */
function bp_ready() {

	/**
	 * Fires inside the 'bp_ready' function, which runs after BP is set up and the page is about to render.
	 *
	 * @since 1.6.0
	 */
	do_action( 'bp_ready' );
}

/**
 * Fire the 'bp_actions' action, which runs just before rendering.
 *
 * Attach potential template actions, such as catching form requests or routing
 * custom URLs.
 */
function bp_actions() {

	/**
	 * Fires inside the 'bp_actions' function, which runs just before rendering.
	 *
	 * @since 1.5.0
	 */
	do_action( 'bp_actions' );
}

/**
 * Fire the 'bp_screens' action, which runs just before rendering.
 *
 * Runs just after 'bp_actions'. Use this hook to attach your template
 * loaders.
 */
function bp_screens() {

	/**
	 * Fires inside the 'bp_screens' function, which runs just before rendering.
	 *
	 * Runs just after 'bp_actions'. Use this hook to attach your template loaders.
	 *
	 * @since 1.5.0
	 */
	do_action( 'bp_screens' );
}

/**
 * Fire 'bp_widgets_init', which runs after widgets have been set up.
 *
 * Hooked to 'widgets_init'.
 */
function bp_widgets_init() {

	/**
	 * Fires inside the 'bp_widgets_init' function, which runs after widgets have been set up.
	 *
	 * Hooked to 'widgets_init'.
	 *
	 * @since 1.6.0
	 */
	do_action ( 'bp_widgets_init' );
}

/**
 * Fire 'bp_head', which is used to hook scripts and styles in the <head>.
 *
 * Hooked to 'wp_head'.
 */
function bp_head() {
	do_action ( 'bp_head' );
}

/** Theme Permissions *********************************************************/

/**
 * Fire the 'bp_template_redirect' action.
 *
 * Run at 'template_redirect', just before WordPress selects and loads a theme
 * template. The main purpose of this hook in BuddyPress is to redirect users
 * who do not have the proper permission to access certain content.
 *
 * @since 1.6.0
 *
 * @uses do_action()
 */
function bp_template_redirect() {

	/**
	 * Fires inside the 'bp_template_redirect' function.
	 *
	 * @since 1.6.0
	 */
	do_action( 'bp_template_redirect' );
}

/** Theme Helpers *************************************************************/

/**
 * Fire the 'bp_register_theme_directory' action.
 *
 * The main action used registering theme directories.
 *
 * @since 1.5.0
 *
 * @uses do_action()
 */
function bp_register_theme_directory() {

	/**
	 * Fires inside the 'bp_register_theme_directory' function.
	 *
	 * The main action used registering theme directories.
	 *
	 * @since 1.7.0
	 */
	do_action( 'bp_register_theme_directory' );
}

/**
 * Fire the 'bp_register_theme_packages' action.
 *
 * The main action used registering theme packages.
 *
 * @since 1.7.0
 *
 * @uses do_action()
 */
function bp_register_theme_packages() {

	/**
	 * Fires inside the 'bp_register_theme_packages' function.
	 *
	 * @since 1.7.0
	 */
	do_action( 'bp_register_theme_packages' );
}

/**
 * Fire the 'bp_enqueue_scripts' action, where BP enqueues its CSS and JS.
 *
 * @since 1.6.0
 *
 * @uses do_action() Calls 'bp_enqueue_scripts'.
 */
function bp_enqueue_scripts() {

	/**
	 * Fires inside the 'bp_enqueue_scripts' function, where BP enqueues its CSS and JS.
	 *
	 * @since 1.6.0
	 */
	do_action ( 'bp_enqueue_scripts' );
}

/**
 * Fire the 'bp_add_rewrite_tag' action, where BP adds its custom rewrite tags.
 *
 * @since 1.8.0
 *
 * @uses do_action() Calls 'bp_add_rewrite_tags'.
 */
function bp_add_rewrite_tags() {

	/**
	 * Fires inside the 'bp_add_rewrite_tags' function, where BP adds its custom rewrite tags.
	 *
	 * @since 1.8.0
	 */
	do_action( 'bp_add_rewrite_tags' );
}

/**
 * Fire the 'bp_add_rewrite_rules' action, where BP adds its custom rewrite rules.
 *
 * @since 1.9.0
 *
 * @uses do_action() Calls 'bp_add_rewrite_rules'.
 */
function bp_add_rewrite_rules() {

	/**
	 * Fires inside the 'bp_add_rewrite_rules' function, where BP adds its custom rewrite rules.
	 *
	 * @since 1.9.0
	 */
	do_action( 'bp_add_rewrite_rules' );
}

/**
 * Fire the 'bp_add_permastructs' action, where BP adds its BP-specific permalink structure.
 *
 * @since 1.9.0
 *
 * @uses do_action() Calls 'bp_add_permastructs'.
 */
function bp_add_permastructs() {

	/**
	 * Fires inside the 'bp_add_permastructs' function, where BP adds its BP-specific permalink structure.
	 *
	 * @since 1.9.0
	 */
	do_action( 'bp_add_permastructs' );
}

/**
 * Fire the 'bp_setup_theme' action.
 *
 * The main purpose of 'bp_setup_theme' is give themes a place to load their
 * BuddyPress-specific functionality.
 *
 * @since 1.6.0
 *
 * @uses do_action() Calls 'bp_setup_theme'.
 */
function bp_setup_theme() {

	/**
	 * Fires inside the 'bp_setup_theme' function.
	 *
	 * @since 1.6.0
	 */
	do_action ( 'bp_setup_theme' );
}

/**
 * Fire the 'bp_after_setup_theme' action.
 *
 * Piggy-back action for BuddyPress-specific theme actions once the theme has
 * been set up and the theme's functions.php has loaded.
 *
 * Hooked to 'after_setup_theme' with a priority of 100. This allows plenty of
 * time for other themes to load their features, such as BuddyPress support,
 * before our theme compatibility layer kicks in.
 *
 * @since 1.6.0
 *
 * @uses do_action() Calls 'bp_after_setup_theme'.
 */
function bp_after_setup_theme() {

	/**
	 * Fires inside the 'bp_after_setup_theme' function.
	 *
	 * @since 1.7.0
	 */
	do_action ( 'bp_after_setup_theme' );
}

/** Theme Compatibility Filter ************************************************/

/**
 * Fire the 'bp_request' filter, a piggy-back of WP's 'request'.
 *
 * @since 1.7.0
 *
 * @see WP::parse_request() for a description of parameters.
 *
 * @param array $query_vars See {@link WP::parse_request()}.
 *
 * @return array $query_vars See {@link WP::parse_request()}.
 */
function bp_request( $query_vars = array() ) {

	/**
	 * Filters the query_vars for the current request.
	 *
	 * @since 1.7.0
	 *
	 * @param array $query_vars Array of query variables.
	 */
	return apply_filters( 'bp_request', $query_vars );
}

/**
 * Fire the 'bp_login_redirect' filter, a piggy-back of WP's 'login_redirect'.
 *
 * @since 1.7.0
 *
 * @param string $redirect_to     See 'login_redirect'.
 * @param string $redirect_to_raw See 'login_redirect'.
 *
 * @param bool   $user See 'login_redirect'.
 *
 * @return string
 */
function bp_login_redirect( $redirect_to = '', $redirect_to_raw = '', $user = false ) {

	/**
	 * Filters the URL to redirect to after login.
	 *
	 * @since 1.7.0
	 *
	 * @param string           $redirect_to     The redirect destination URL.
	 * @param string           $redirect_to_raw The requested redirect destination URL passed as a parameter.
	 * @param WP_User|WP_Error $user            WP_User object if login was successful, WP_Error object otherwise.
	 */
	return apply_filters( 'bp_login_redirect', $redirect_to, $redirect_to_raw, $user );
}

/**
 * Fire 'bp_template_include', main filter used for theme compatibility and displaying custom BP theme files.
 *
 * Hooked to 'template_include'.
 *
 * @since 1.6.0
 *
 * @uses apply_filters()
 *
 * @param string $template See 'template_include'.
 *
 * @return string Template file to use.
 */
function bp_template_include( $template = '' ) {

	/**
	 * Filters the template to use with template_include.
	 *
	 * @since 1.6.0
	 *
	 * @param string $template The path of the template to include.
	 */
	return apply_filters( 'bp_template_include', $template );
}

/**
 * Fire the 'bp_generate_rewrite_rules' action, where BP generates its rewrite rules.
 *
 * @since 1.7.0
 *
 * @uses do_action() Calls 'bp_generate_rewrite_rules' with {@link WP_Rewrite}.
 *
 * @param WP_Rewrite $wp_rewrite See 'generate_rewrite_rules'.
 */
function bp_generate_rewrite_rules( $wp_rewrite ) {

	/**
	 * Fires inside the 'bp_generate_rewrite_rules' function.
	 *
	 * @since 1.7.0
	 *
	 * @param WP_Rewrite $wp_rewrite WP_Rewrite object. Passed by reference.
	 */
	do_action_ref_array( 'bp_generate_rewrite_rules', array( &$wp_rewrite ) );
}

/**
 * Fire the 'bp_allowed_themes' filter.
 *
 * Filter the allowed themes list for BuddyPress-specific themes.
 *
 * @since 1.7.0
 *
 * @uses apply_filters() Calls 'bp_allowed_themes' with the allowed themes list.
 *
 * @param array $themes
 *
 * @return array
 */
function bp_allowed_themes( $themes ) {

	/**
	 * Filters the allowed themes list for BuddyPress-specific themes.
	 *
	 * @since 1.7.0
	 *
	 * @param string $template The path of the template to include.
	 */
	return apply_filters( 'bp_allowed_themes', $themes );
}

/** Requests ******************************************************************/

/**
 * The main action used for handling theme-side POST requests.
 *
 * @since 1.9.0
 * @uses do_action()
 */
function bp_post_request() {

	// Bail if not a POST action
	if ( ! bp_is_post_request() ) {
		return;
	}

	// Bail if no action
	if ( empty( $_POST['action'] ) ) {
		return;
	}

	// Sanitize the POST action
	$action = sanitize_key( $_POST['action'] );

	/**
	 * Fires at the end of the bp_post_request function.
	 *
	 * This dynamic action is probably the one you want to use. It narrows down
	 * the scope of the 'action' without needing to check it in your function.
	 *
	 * @since 1.9.0
	 */
	do_action( 'bp_post_request_' . $action );

	/**
	 * Fires at the end of the bp_post_request function.
	 *
	 * Use this static action if you don't mind checking the 'action' yourself.
	 *
	 * @since 1.9.0
	 *
	 * @param string $action The action being run.
	 */
	do_action( 'bp_post_request',   $action );
}

/**
 * The main action used for handling theme-side GET requests.
 *
 * @since 1.9.0
 * @uses do_action()
 */
function bp_get_request() {

	// Bail if not a POST action
	if ( ! bp_is_get_request() ) {
		return;
	}

	// Bail if no action
	if ( empty( $_GET['action'] ) ) {
		return;
	}

	// Sanitize the GET action
	$action = sanitize_key( $_GET['action'] );

	/**
	 * Fires at the end of the bp_get_request function.
	 *
	 * This dynamic action is probably the one you want to use. It narrows down
	 * the scope of the 'action' without needing to check it in your function.
	 *
	 * @since 1.9.0
	 */
	do_action( 'bp_get_request_' . $action );

	/**
	 * Fires at the end of the bp_get_request function.
	 *
	 * Use this static action if you don't mind checking the 'action' yourself.
	 *
	 * @since 1.9.0
	 *
	 * @param string $action The action being run.
	 */
	do_action( 'bp_get_request',   $action );
}
