<?php
/**
 * Initialises the most fundamental parts of bbPress
 *
 * You should not have to change this file, all configuration
 * should be possible in bb-config.php
 *
 * @package bbPress
 */



/**
 * Low level reasons to die
 */

// Die if PHP is not new enough
if ( version_compare( PHP_VERSION, '4.3', '<' ) ) {
	die( sprintf( 'Your server is running PHP version %s but bbPress requires at least 4.3', PHP_VERSION ) );
}



// Modify error reporting levels to exclude PHP notices
error_reporting(E_ERROR | E_WARNING | E_PARSE | E_USER_ERROR | E_USER_WARNING);


/**
 * bb_timer_start() - PHP 4 standard microtime start capture
 *
 * @access private
 * @global int $bb_timestart Seconds and Microseconds added together from when function is called
 * @return bool Always returns true
 */
function bb_timer_start()
{
	global $bb_timestart;
	$mtime = explode( ' ', microtime() );
	$bb_timestart = $mtime[1] + $mtime[0];
	return true;
}
bb_timer_start();



// Server detection

/**
 * Whether the server software is Apache or something else
 * @global bool $is_apache
 */
$is_apache = ( strpos( $_SERVER['SERVER_SOFTWARE'], 'Apache' ) !== false || strpos( $_SERVER['SERVER_SOFTWARE'], 'LiteSpeed' ) !== false);

/**
 * Whether the server software is IIS or something else
 * @global bool $is_IIS
 */
$is_IIS = ( strpos( $_SERVER['SERVER_SOFTWARE'], 'Microsoft-IIS' ) !== false || strpos( $_SERVER['SERVER_SOFTWARE'], 'ExpressionDevServer' ) !== false );

/**
 * Whether the server software is IIS 7.X
 * @global bool $is_iis7
 */
$is_iis7 = ( strpos( $_SERVER['SERVER_SOFTWARE'], 'Microsoft-IIS/7.' ) !== false );



/**
 * Stabilise $_SERVER variables in various PHP environments
 */

// Fix for IIS, which doesn't set REQUEST_URI
if ( empty( $_SERVER['REQUEST_URI'] ) ) {

	// IIS Mod-Rewrite
	if (isset($_SERVER['HTTP_X_ORIGINAL_URL'])) {
		$_SERVER['REQUEST_URI'] = $_SERVER['HTTP_X_ORIGINAL_URL'];
	}
	// IIS Isapi_Rewrite
	else if (isset($_SERVER['HTTP_X_REWRITE_URL'])) {
		$_SERVER['REQUEST_URI'] = $_SERVER['HTTP_X_REWRITE_URL'];
	}
	else
	{
		// Use ORIG_PATH_INFO if there is no PATH_INFO
		if ( !isset($_SERVER['PATH_INFO']) && isset($_SERVER['ORIG_PATH_INFO']) )
			$_SERVER['PATH_INFO'] = $_SERVER['ORIG_PATH_INFO'];

		// Some IIS + PHP configurations puts the script-name in the path-info (No need to append it twice)
		if ( isset($_SERVER['PATH_INFO']) ) {
			if ( $_SERVER['PATH_INFO'] == $_SERVER['SCRIPT_NAME'] )
				$_SERVER['REQUEST_URI'] = $_SERVER['PATH_INFO'];
			else
				$_SERVER['REQUEST_URI'] = $_SERVER['SCRIPT_NAME'] . $_SERVER['PATH_INFO'];
		}

		// Append the query string if it exists and isn't null
		if (isset($_SERVER['QUERY_STRING']) && !empty($_SERVER['QUERY_STRING'])) {
			$_SERVER['REQUEST_URI'] .= '?' . $_SERVER['QUERY_STRING'];
		}
	}
}

// Fix for PHP as CGI hosts that set SCRIPT_FILENAME to something ending in php.cgi for all requests
if ( isset($_SERVER['SCRIPT_FILENAME']) && ( strpos($_SERVER['SCRIPT_FILENAME'], 'php.cgi') == strlen($_SERVER['SCRIPT_FILENAME']) - 7 ) )
	$_SERVER['SCRIPT_FILENAME'] = $_SERVER['PATH_TRANSLATED'];

// Fix for Dreamhost and other PHP as CGI hosts
if (strpos($_SERVER['SCRIPT_NAME'], 'php.cgi') !== false)
	unset($_SERVER['PATH_INFO']);

// Fix empty PHP_SELF
$PHP_SELF = $_SERVER['PHP_SELF'];
if ( empty($PHP_SELF) )
	$_SERVER['PHP_SELF'] = $PHP_SELF = preg_replace("/(\?.*)?$/",'',$_SERVER["REQUEST_URI"]);



/**
 * bbPress logging level constants - same as constants from BP_Log class
 */
define( 'BB_LOG_NONE',    0 );
define( 'BB_LOG_FAIL',    1 );
define( 'BB_LOG_ERROR',   2 );
define( 'BB_LOG_WARNING', 4 );
define( 'BB_LOG_NOTICE',  8 );
define( 'BB_LOG_DEBUG',   16 );

/**
 * Combination of all errors (excluding none and debug)
 */
define( 'BB_LOG_ALL', BB_LOG_FAIL + BB_LOG_ERROR + BB_LOG_WARNING + BB_LOG_NOTICE );

/**
 * Define temporary $_bb_path as this files directory, then check for the special BB_PATH config file
 * which allows override of BB_PATH, but only outside of core files
 */
$_bb_path = dirname( __FILE__ ) . '/';
$_bb_config_path = dirname( $_bb_path ) . '/bb-config-path.php';
if ( file_exists( $_bb_config_path ) ) {
	include_once( $_bb_config_path );
}
if ( !defined( 'BB_PATH' ) ) {
	define( 'BB_PATH', $_bb_path );
}
unset( $_bb_path, $_bb_config_path );

/**
 * The bbPress includes path relative to BB_PATH
 */
define( 'BB_INC', 'bb-includes/' );

// Initialise $bb object
$bb = new StdClass();

if ( file_exists( BB_PATH . 'bb-config.php') ) {

	// The config file resides in BB_PATH
	require_once( BB_PATH . 'bb-config.php');

	// Load bb-settings.php
	require_once( BB_PATH . 'bb-settings.php' );

} elseif ( file_exists( dirname( BB_PATH ) . '/bb-config.php') ) {

	// The config file resides one level below BB_PATH
	require_once( dirname( BB_PATH ) . '/bb-config.php' );

	// Load bb-settings.php
	require_once( BB_PATH . 'bb-settings.php' );

} elseif ( !defined( 'BB_INSTALLING' ) || !BB_INSTALLING ) {

	// The config file doesn't exist and we aren't on the installation page

	// Cut to the chase, go to the installer and use it to deal with errors
	$install_uri = preg_replace( '|(/bb-admin)?/[^/]+?$|', '/', $_SERVER['PHP_SELF'] ) . 'bb-admin/install.php';
	header( 'Location: ' . $install_uri );
	die();

}

if ( isset( $_GET['doit'] ) && 'bb-subscribe' == $_GET['doit'] )
	require( BB_PATH . 'bb-includes/action.subscribe.php' );
