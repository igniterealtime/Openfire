<?php
/*
Plugin Name: BuddyPics
Plugin URI: http://github.com/modemlooper
Description: Photo Albums for BuddyPress.
Version: 0.3.2
Tested up to: 4.1
Requires at least: 3.8
License: GNU General Public License 2.0 (GPL) http://www.gnu.org/licenses/gpl.html
Author: modemlooper
Author URI: https://twitter.com/modemlooper
Network: True
Text Domain: bp-album
*/


function buddypics_bp_check() {
	if ( !class_exists('BuddyPress') ) {
		add_action( 'admin_notices', 'buddypics_install_buddypress_notice' );
	}
}
add_action('plugins_loaded', 'buddypics_bp_check', 999);

function buddypics_install_buddypress_notice() {
	echo '<div id="message" class="error fade"><p style="line-height: 150%">';
	_e('<strong>BuddyPics</strong></a> requires the BuddyPress plugin to work. Please <a href="http://buddypress.org/download">install BuddyPress</a> first, or <a href="plugins.php">deactivate BuddyPics</a>.');
	echo '</p></div>';
}

function buddypics_init() {
	require( dirname( __FILE__ ) . '/includes/bpa.core.php' );
}
add_action( 'bp_include', 'buddypics_init' );