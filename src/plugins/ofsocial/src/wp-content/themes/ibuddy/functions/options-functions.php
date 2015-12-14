<?php
/* 
 * Load the Options Panel
 *
 * @package iBuddy
 *
 */
 
if ( !function_exists( 'optionsframework_init' ) ) {
	define( 'OPTIONS_FRAMEWORK_DIRECTORY', get_template_directory_uri() . '/inc/' );
	require get_template_directory() . '/inc/options-framework.php';
}
 
/* The CSS file selctor "Skin" in the options panel */

function options_stylesheets_alt_style()   {
	if ( of_get_option('stylesheet') ) {
		wp_enqueue_style( 'options_stylesheets_alt_style', of_get_option('stylesheet'), array(), null );
	}
}
add_action( 'wp_enqueue_scripts', 'options_stylesheets_alt_style' );


/* function to load the private-bp.php if selected in options panel. */
function private_buddypress() {
	// Check to see if another plugin that does the smae thing has already been loaded.
	if (!function_exists('private_community_for_bp_init')) {
		if (of_get_option('lock-down') == 1){
		// load private locker file
		require get_template_directory() . '/inc/private-bp.php';
		}
 
	}
}
// Run 'after_theme_setup'
add_action('after_setup_theme', 'private_buddypress');


/* Custom Logo, link and tooltip on login page */

if (of_get_option('logo')) {
// Logo
function ibuddy_login_logo() {
    echo '<style type="text/css">
        h1 a { background-image:url('.of_get_option('logo').') !important; }
    </style>';
}
add_action('login_head', 'ibuddy_login_logo');

// URL
function ibuddy_login_url(){
    return get_bloginfo('url');
}
add_filter('login_headerurl', 'ibuddy_login_url');

// Tooltip
function ibuddy_login_tooltip() {
	return get_bloginfo('description');
}
add_filter('login_headertitle', 'ibuddy_login_tooltip');

}



?>
