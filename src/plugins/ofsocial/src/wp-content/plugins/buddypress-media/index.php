<?php

/*
  Plugin Name: rtMedia for WordPress, BuddyPress and bbPress
  Plugin URI: http://rtcamp.com/rtmedia/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media
  Description: This plugin adds missing media rich features like photos, videos and audio uploading to BuddyPress which are essential if you are building social network, seriously!
  Version: 3.9.5
  Author: rtCamp
  Text Domain: buddypress-media
  Author URI: http://rtcamp.com/?utm_source=dashboard&utm_medium=plugin&utm_campaign=buddypress-media
  Domain Path: /languages/
 */

/**
 * Main file, contains the plugin metadata and activation processes
 *
 * @package    BuddyPressMedia
 * @subpackage Main
 */
if ( ! defined( 'RTMEDIA_PATH' ) ) {

	/**
	 *  The server file system path to the plugin directory
	 *
	 */
	define( 'RTMEDIA_PATH', plugin_dir_path( __FILE__ ) );
}

if ( ! defined( 'BP_MEDIA_PATH' ) ) {

	/**
	 *  Legacy support
	 *
	 */
	define( 'BP_MEDIA_PATH', RTMEDIA_PATH );
}


if ( ! defined( 'RTMEDIA_URL' ) ) {

	/**
	 * The url to the plugin directory
	 *
	 */
	define( 'RTMEDIA_URL', plugin_dir_url( __FILE__ ) );
}

if ( ! defined( 'RTMEDIA_BASE_NAME' ) ) {

	/**
	 * The url to the plugin directory
	 *
	 */
	define( 'RTMEDIA_BASE_NAME', plugin_basename( __FILE__ ) );
}

/**
 * Start session here to avoid header notice
 */
if ( ! session_id() ) {
	session_start();
}

/**
 * Auto Loader Function
 *
 * Autoloads classes on instantiation. Used by spl_autoload_register.
 *
 * @param string $class_name The name of the class to autoload
 */
function rtmedia_autoloader( $class_name ) {
	$rtlibpath = array(
		'app/services/' . $class_name . '.php',
		'app/helper/' . $class_name . '.php',
		'app/helper/db/' . $class_name . '.php',
		'app/admin/' . $class_name . '.php',
		'app/main/interactions/' . $class_name . '.php',
		'app/main/routers/' . $class_name . '.php',
		'app/main/routers/query/' . $class_name . '.php',
		'app/main/controllers/upload/' . $class_name . '.php',
		'app/main/controllers/upload/processors/' . $class_name . '.php',
		'app/main/controllers/shortcodes/' . $class_name . '.php',
		'app/main/controllers/template/' . $class_name . '.php',
		'app/main/controllers/media/' . $class_name . '.php',
		'app/main/controllers/group/' . $class_name . '.php',
		'app/main/controllers/privacy/' . $class_name . '.php',
		'app/main/controllers/activity/' . $class_name . '.php',
		'app/main/deprecated/' . $class_name . '.php',
		'app/main/contexts/' . $class_name . '.php',
		'app/main/' . $class_name . '.php',
		'app/main/includes/' . $class_name . '.php',
		'app/main/widgets/' . $class_name . '.php',
		'app/main/upload/' . $class_name . '.php',
		'app/main/upload/processors/' . $class_name . '.php',
		'app/main/template/' . $class_name . '.php',
		'app/log/' . $class_name . '.php',
		'app/importers/' . $class_name . '.php',
		'app/main/controllers/api/' . $class_name . '.php',
	);
	foreach ( $rtlibpath as $path ) {
		$path = RTMEDIA_PATH . $path;
		if ( file_exists( $path ) ) {
			include $path;
			break;
		}
	}
}

/**
 * Register the autoloader function into spl_autoload
 */
spl_autoload_register( 'rtmedia_autoloader' );

/**
 * Instantiate the BuddyPressMedia class.
 */
global $rtmedia;
$rtmedia = new RTMedia();


/*
 * Look Ma! Very few includes! Next File: /app/main/RTMedia.php
 */
