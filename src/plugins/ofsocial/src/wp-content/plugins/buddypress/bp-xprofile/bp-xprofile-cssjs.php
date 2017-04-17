<?php
/**
 * BuddyPress XProfile CSS and JS.
 *
 * @package BuddyPress
 * @subpackage XProfileScripts
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Enqueue the CSS for XProfile admin styling.
 *
 * @since 1.1.0
 */
function xprofile_add_admin_css() {
	if ( !empty( $_GET['page'] ) && strpos( $_GET['page'], 'bp-profile-setup' ) !== false ) {
		$min = defined( 'SCRIPT_DEBUG' ) && SCRIPT_DEBUG ? '' : '.min';

		wp_enqueue_style( 'xprofile-admin-css', buddypress()->plugin_url . "bp-xprofile/admin/css/admin{$min}.css", array(), bp_get_version() );

		wp_style_add_data( 'xprofile-admin-css', 'rtl', true );
		if ( $min ) {
			wp_style_add_data( 'xprofile-admin-css', 'suffix', $min );
		}
	}
}
add_action( 'bp_admin_enqueue_scripts', 'xprofile_add_admin_css' );

/**
 * Enqueue the jQuery libraries for handling drag/drop/sort.
 *
 * @since 1.5.0
 */
function xprofile_add_admin_js() {
	if ( !empty( $_GET['page'] ) && strpos( $_GET['page'], 'bp-profile-setup' ) !== false ) {
		wp_enqueue_script( 'jquery-ui-core'      );
		wp_enqueue_script( 'jquery-ui-tabs'      );
		wp_enqueue_script( 'jquery-ui-mouse'     );
		wp_enqueue_script( 'jquery-ui-draggable' );
		wp_enqueue_script( 'jquery-ui-droppable' );
		wp_enqueue_script( 'jquery-ui-sortable'  );

		$min = defined( 'SCRIPT_DEBUG' ) && SCRIPT_DEBUG ? '' : '.min';
		wp_enqueue_script( 'xprofile-admin-js', buddypress()->plugin_url . "bp-xprofile/admin/js/admin{$min}.js", array( 'jquery', 'jquery-ui-sortable' ), bp_get_version() );

		// Localize strings.
		// supports_options_field_types is a dynamic list of field
		// types that support options, for use in showing/hiding the
		// "please enter options for this field" section.
		$strings = array(
			'supports_options_field_types' => array(),
		);

		foreach ( bp_xprofile_get_field_types() as $field_type => $field_type_class ) {
			$field = new $field_type_class();
			if ( $field->supports_options ) {
				$strings['supports_options_field_types'][] = $field_type;
			}
		}

		wp_localize_script( 'xprofile-admin-js', 'XProfileAdmin', $strings );
	}
}
add_action( 'bp_admin_enqueue_scripts', 'xprofile_add_admin_js', 1 );
