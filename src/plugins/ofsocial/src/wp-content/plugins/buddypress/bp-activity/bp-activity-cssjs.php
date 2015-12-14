<?php
/**
 * Activity component CSS/JS
 *
 * @package BuddyPress
 * @subpackage ActivityScripts
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Enqueue @mentions JS.
 *
 * @since 2.1.0
 */
function bp_activity_mentions_script() {
	if ( ! bp_activity_maybe_load_mentions_scripts() ) {
		return;
	}

	// Special handling for New/Edit screens in wp-admin.
	if ( is_admin() ) {
		if (
			! get_current_screen() ||
			! in_array( get_current_screen()->base, array( 'page', 'post' ) ) ||
			! post_type_supports( get_current_screen()->post_type, 'editor' ) ) {
			return;
		}
	}


	$min = defined( 'SCRIPT_DEBUG' ) && SCRIPT_DEBUG ? '' : '.min';

	wp_enqueue_script( 'bp-mentions', buddypress()->plugin_url . "bp-activity/js/mentions{$min}.js", array( 'jquery', 'jquery-atwho' ), bp_get_version(), true );
	wp_enqueue_style( 'bp-mentions-css', buddypress()->plugin_url . "bp-activity/css/mentions{$min}.css", array(), bp_get_version() );

	wp_style_add_data( 'bp-mentions-css', 'rtl', true );
	if ( $min ) {
		wp_style_add_data( 'bp-mentions-css', 'suffix', $min );
	}

	// If the script has been enqueued, let's attach our mentions TinyMCE init callback.
	add_filter( 'tiny_mce_before_init', 'bp_add_mentions_on_tinymce_init', 10, 2 );

	/**
	 * Fires at the end of the Activity Mentions script.
	 *
	 * This is the hook where BP components can add their own prefetched results
	 * friends to the page for quicker @mentions lookups.
	 *
	 * @since 2.1.0
	 */
	do_action( 'bp_activity_mentions_prime_results' );
}
add_action( 'bp_enqueue_scripts', 'bp_activity_mentions_script' );
add_action( 'bp_admin_enqueue_scripts', 'bp_activity_mentions_script' );

/**
 * Bind the mentions listener to a wp_editor instance when TinyMCE initializes.
 *
 * @since 2.3.3
 *
 * @param array  $settings   An array with TinyMCE config.
 * @param string $editor_id Unique editor identifier, e.g. 'content'.
 * @return array  $mceInit   An array with TinyMCE config.
 */
function bp_add_mentions_on_tinymce_init( $settings, $editor_id ) {
	// We only apply the mentions init to the visual post editor in the WP dashboard.
	if ( 'content' === $editor_id ) {
		$settings['init_instance_callback'] = 'window.bp.mentions.tinyMCEinit';
	}

	return $settings;
}
