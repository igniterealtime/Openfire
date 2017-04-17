<?php

/**
 * Plugin Name: BuddyMobile
 * Plugin URI:  http://buddypress.org
 * Description: Mobile theme for optimized mobile experience on BuddyPress sites
 * Author:      modemlooper
 * Author URI:  http://twitter.com/modemlooper
 * Version:     1.9.4
 */

// Exit if accessed directly
if ( !defined( 'ABSPATH' ) ) exit;

function BP_mobile_init() {
    require( dirname( __FILE__ ) . '/includes/bp-mobile-class.php' );
}
add_action( 'plugins_loaded', 'BP_mobile_init' );


function buddymobile_textdomain_init() {
	$mofile        = sprintf( 'buddymobile-%s.mo', get_locale() );
	$mofile_local  = dirname( __FILE__ )  . '/languages/' . $mofile;

	if ( file_exists( $mofile_local ) )
	return load_textdomain( 'buddymobile', $mofile_local );
}
add_action( 'plugins_loaded', 'buddymobile_textdomain_init' );