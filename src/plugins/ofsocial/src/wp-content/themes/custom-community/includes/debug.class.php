<?php
/**
 * Simple Debug class for WordPress
 *
 * @author Fabian Wolf
 * @link http://usability-idealist.de/
 * @version 1.4-light
 * @license GNU GPL v3
 * 
 * Features:
 * - class acts as a namespace
 * - debug output is displayed only to logged in users with the manage_options capability (may be optionally changed or disabled)
 * 
 * Removed features (due to WordPress Theme Repository rules; only available in the full class at @link https://github.com/ginsterbusch/__debug or with the cc2 premium extension):
 * - optionally writes data into a remote-read protected logfile (default location: wp_upload_dir)
 */

if( !class_exists( '__debug' ) ) :
	
	include_once( str_replace( basename( __FILE__ ), 'debug-lite.class.php', __FILE__ ) );

endif; // class exists
 
