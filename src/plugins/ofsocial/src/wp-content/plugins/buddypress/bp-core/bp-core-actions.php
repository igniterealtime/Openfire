<?php
/**
 * BuddyPress Filters & Actions.
 *
 * @package BuddyPress
 * @subpackage Hooks
 *
 * This file contains the actions and filters that are used through-out BuddyPress.
 * They are consolidated here to make searching for them easier, and to help
 * developers understand at a glance the order in which things occur.
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
add_action( 'plugins_loaded',          'bp_loaded',                 10    );
add_action( 'init',                    'bp_init',                   10    );
add_action( 'parse_query',             'bp_parse_query',            2     ); // Early for overrides.
add_action( 'wp',                      'bp_ready',                  10    );
add_action( 'set_current_user',        'bp_setup_current_user',     10    );
add_action( 'setup_theme',             'bp_setup_theme',            10    );
add_action( 'after_setup_theme',       'bp_after_setup_theme',      100   ); // After WP themes.
add_action( 'wp_enqueue_scripts',      'bp_enqueue_scripts',        10    );
add_action( 'admin_bar_menu',          'bp_setup_admin_bar',        20    ); // After WP core.
add_action( 'template_redirect',       'bp_template_redirect',      10    );
add_action( 'widgets_init',            'bp_widgets_init',           10    );
add_action( 'generate_rewrite_rules',  'bp_generate_rewrite_rules', 10    );

/**
 * bp_loaded - Attached to 'plugins_loaded' above.
 *
 * Attach various loader actions to the bp_loaded action.
 * The load order helps to execute code at the correct time.
 *                                                      v---Load order
 */
add_action( 'bp_loaded', 'bp_setup_components',         2  );
add_action( 'bp_loaded', 'bp_include',                  4  );
add_action( 'bp_loaded', 'bp_setup_cache_groups',       5  );
add_action( 'bp_loaded', 'bp_setup_widgets',            6  );
add_action( 'bp_loaded', 'bp_register_member_types',    8  );
add_action( 'bp_loaded', 'bp_register_theme_packages',  12 );
add_action( 'bp_loaded', 'bp_register_theme_directory', 14 );

/**
 * bp_init - Attached to 'init' above.
 *
 * Attach various initialization actions to the bp_init action.
 * The load order helps to execute code at the correct time.
 *                                                   v---Load order
 */
add_action( 'bp_init', 'bp_core_set_uri_globals',    2  );
add_action( 'bp_init', 'bp_register_taxonomies',     3  );
add_action( 'bp_init', 'bp_setup_globals',           4  );
add_action( 'bp_init', 'bp_setup_canonical_stack',   5  );
add_action( 'bp_init', 'bp_setup_nav',               6  );
add_action( 'bp_init', 'bp_setup_title',             8  );
add_action( 'bp_init', 'bp_core_load_admin_bar_css', 12 );
add_action( 'bp_init', 'bp_add_rewrite_tags',        20 );
add_action( 'bp_init', 'bp_add_rewrite_rules',       30 );
add_action( 'bp_init', 'bp_add_permastructs',        40 );

/**
 * bp_template_redirect - Attached to 'template_redirect' above.
 *
 * Attach various template actions to the bp_template_redirect action.
 * The load order helps to execute code at the correct time.
 *
 * Note that we currently use template_redirect versus template include because
 * BuddyPress is a bully and overrides the existing themes output in many
 * places. This won't always be this way, we promise.
 *                                                           v---Load order
 */
add_action( 'bp_template_redirect', 'bp_redirect_canonical', 2  );
add_action( 'bp_template_redirect', 'bp_actions',            4  );
add_action( 'bp_template_redirect', 'bp_screens',            6  );
add_action( 'bp_template_redirect', 'bp_post_request',       10 );
add_action( 'bp_template_redirect', 'bp_get_request',        10 );

/**
 * Add the BuddyPress functions file and the Theme Compat Default features.
 */
add_action( 'bp_after_setup_theme', 'bp_load_theme_functions',                    1 );
add_action( 'bp_after_setup_theme', 'bp_register_theme_compat_default_features', 10 );

// Load the admin.
if ( is_admin() ) {
	add_action( 'bp_loaded', 'bp_admin' );
}

// Activation redirect.
add_action( 'bp_activation', 'bp_add_activation_redirect' );
