<?php

/**
 * BP-ALBUM CSS/JS CLASS
 * Handles loading CSS and JS
 *
 * @since 0.1.8.0
 * @package BP-Album
 * @subpackage CSS/JS
 * @license GPL v2.0
 * @link http://code.google.com/p/buddypress-media/
 *
 * ========================================================================================================
 */


/**
 * bp_album_add_js()
 *
 * This function will enqueue the components Javascript file, so that you can make
 * use of any Javascript you bundle with your component within your interface screens.
 *
 * @version 0.1.8.12
 * @since 0.1.8.0
 */
function bp_album_add_js() {

	global $bp;

	if ( $bp->current_component == $bp->album->slug )
		wp_enqueue_script( 'bp-album-js', WP_PLUGIN_URL .'/buddypics/includes/js/general.js' );
}
// add_action( 'template_redirect', 'bp_album_add_js', 1 );

/**
 * bp_album_add_css()
 *
 * @version 0.1.8.12
 * @since 0.1.8.0
 */
function bp_album_add_css() {

	global $bp;

		wp_enqueue_style( 'bp-album-css', WP_PLUGIN_URL .'/buddypics/includes/css/general.css' );
		wp_print_styles();
}

add_action( 'wp_head', 'bp_album_add_css' );

?>