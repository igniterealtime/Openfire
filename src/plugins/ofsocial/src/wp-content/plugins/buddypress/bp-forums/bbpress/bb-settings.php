<?php
/**
 * Used to setup and fix common variables and include
 * the bbPress and BackPress procedural and class libraries.
 *
 * You should not have to change this file, all configuration
 * should be possible in bb-config.php
 *
 * @package bbPress
 */



// Die if called directly
if ( !defined( 'BB_PATH' ) ) {
	die( 'This file cannot be called directly.' );
}

// Set default timezone in PHP 5.
if ( function_exists( 'date_default_timezone_set' ) )
        date_default_timezone_set( 'UTC' );

/**
 * bb_unregister_GLOBALS() - Turn register globals off
 *
 * @access private
 * @return null Will return null if register_globals PHP directive was disabled
 */
function bb_unregister_GLOBALS()
{
	if ( !ini_get( 'register_globals' ) ) {
		return;
	}

	if ( isset($_REQUEST['GLOBALS']) ) {
		die( 'GLOBALS overwrite attempt detected' );
	}

	// Variables that shouldn't be unset
	$noUnset = array( 'GLOBALS', '_GET', '_POST', '_COOKIE', '_REQUEST', '_SERVER', '_ENV', '_FILES', 'bb_table_prefix', 'bb' );

	$input = array_merge( $_GET, $_POST, $_COOKIE, $_SERVER, $_ENV, $_FILES, isset( $_SESSION ) && is_array( $_SESSION ) ? $_SESSION : array() );
	foreach ( $input as $k => $v ) {
		if ( !in_array( $k, $noUnset ) && isset( $GLOBALS[$k] ) ) {
			$GLOBALS[$k] = NULL;
			unset( $GLOBALS[$k] );
		}
	}
}
bb_unregister_GLOBALS();



/**
 * Let bbPress know what we are up to at the moment
 */

/**
 * Whether the current script is in the admin area or not
 */
if ( !defined( 'BB_IS_ADMIN' ) ) {
	define( 'BB_IS_ADMIN', false );
} elseif ( BB_IS_ADMIN ) {
	$bb_hardcoded = (array) $bb;
}

/**
 * Whether the current script is part of the installation process or not
 * @since 1.0
 */
if ( !defined( 'BB_INSTALLING' ) ) {
	define( 'BB_INSTALLING', false );
}

/**
 * Whether to load deprecated routines, constants and functions
 * @since 1.0
 */
if ( !defined( 'BB_LOAD_DEPRECATED' ) ) {
	define( 'BB_LOAD_DEPRECATED', true );
}



/**
 * Remove filters and action that have been set by an included WordPress install
 */
if ( defined( 'ABSPATH' ) ) {
	$wp_filter = array();
	$wp_actions = array();
	$merged_filters = array();
	$wp_current_filter = array();
}



/**
 * Define include paths and load core BackPress libraries
 */

/**
 * The full path to the BackPress libraries
 */
if ( !defined( 'BACKPRESS_PATH' ) ) {
	define( 'BACKPRESS_PATH', BB_PATH . BB_INC . 'backpress/' );
}

// Load logging class
require_once( BACKPRESS_PATH . 'class.bp-log.php' );
$bb_log = new BP_Log();
if ( defined( 'BB_LOG_LEVEL' ) ) {
	$bb_log->set_level( BB_LOG_LEVEL );
}
if ( defined( 'BB_LOG_TYPE' ) ) {
	$bb_log->set_type( BB_LOG_TYPE );
}
if ( defined( 'BB_LOG_FILENAME' ) ) {
	$bb_log->set_filename( BB_LOG_FILENAME );
}
$bb_log->notice('Logging started');

// Load core BackPress functions
require_once( BACKPRESS_PATH . 'functions.core.php' );
require_once( BACKPRESS_PATH . 'functions.compat.php' );
require_once( BACKPRESS_PATH . 'functions.formatting.php' );

// WP_Error
if ( !class_exists( 'WP_Error' ) ) {
	require_once( BACKPRESS_PATH . 'class.wp-error.php' );
}



/**
 * Set up database parameters based on config and initialise
 */

/**
 * Define the full path to the database class
 */
if ( !defined( 'BB_DATABASE_CLASS_INCLUDE' ) ) {
	define( 'BB_DATABASE_CLASS_INCLUDE', BACKPRESS_PATH . 'class.bpdb-multi.php' );
}

/**
 * Define the name of the database class
 */
if ( !defined( 'BB_DATABASE_CLASS' ) ) {
	define( 'BB_DATABASE_CLASS', 'BPDB_Multi' );
}

if ( in_array( BB_DATABASE_CLASS, array( 'BPDB', 'BPDB_Multi' ) ) ) {
	/**
	 * Define BackPress Database errors if not already done - no localisation at this stage
	 */
	if ( !defined( 'BPDB__CONNECT_ERROR_MESSAGE' ) ) {
		define( 'BPDB__CONNECT_ERROR_MESSAGE', 'ERROR: Could not establish a database connection' );
	}
	if ( !defined( 'BPDB__CONNECT_ERROR_MESSAGE' ) ) {
		define( 'BPDB__SELECT_ERROR_MESSAGE', 'ERROR: Can\'t select database.' );
	}
	if ( !defined( 'BPDB__ERROR_STRING' ) ) {
		define( 'BPDB__ERROR_STRING', 'ERROR: bbPress database error - "%s" for query "%s" via caller "%s"' );
	}
	if ( !defined( 'BPDB__ERROR_HTML' ) ) {
		define( 'BPDB__ERROR_HTML', '<div id="error"><p class="bpdberror"><strong>Database error:</strong> [%s]<br /><code>%s</code><br />Caller: %s</p></div>' );
	}
	if ( !defined( 'BPDB__DB_VERSION_ERROR' ) ) {
		define( 'BPDB__DB_VERSION_ERROR', 'ERROR: bbPress requires MySQL 4.0.0 or higher' );
	}
	if ( !defined( 'BPDB__PHP_EXTENSION_MISSING' ) ) {
		define( 'BPDB__PHP_EXTENSION_MISSING', 'ERROR: bbPress requires The MySQL PHP extension' );
	}
}

// Load the database class
if ( BB_DATABASE_CLASS_INCLUDE ) {
	require_once( BB_DATABASE_CLASS_INCLUDE );
}

// Die if there is no database table prefix
if ( !$bb_table_prefix ) {
	die( 'You must specify a table prefix in your <code>bb-config.php</code> file.' );
}

// Setup the global database connection
$bbdb_class = BB_DATABASE_CLASS;
$bbdb =& new $bbdb_class( array(
	'name' => BBDB_NAME,
	'user' => BBDB_USER,
	'password' => BBDB_PASSWORD,
	'host' => BBDB_HOST,
	'charset' => defined( 'BBDB_CHARSET' ) ? BBDB_CHARSET : false,
	'collate' => defined( 'BBDB_COLLATE' ) ? BBDB_COLLATE : false
) );
unset( $bbdb_class );

/**
 * bbPress tables
 */
$bbdb->tables = array(
	'forums'             => false,
	'meta'               => false,
	'posts'              => false,
	'tagged'             => false, // Deprecated
	'tags'               => false, // Deprecated
	'terms'              => false,
	'term_relationships' => false,
	'term_taxonomy'      => false,
	'topics'             => false,
	'topicmeta'          => false, // Deprecated
	'users'              => false,
	'usermeta'           => false
);

// Set the prefix on the tables
if ( is_wp_error( $bbdb->set_prefix( $bb_table_prefix ) ) ) {
	die( 'Your table prefix may only contain letters, numbers and underscores.' );
}

// Set a site id if there isn't one already
if ( !isset( $bb->site_id ) ) {
	$bb->site_id = 1;
}



/**
 * Load core bbPress libraries
 */

require_once( BB_PATH . BB_INC . 'functions.bb-core.php' );
require_once( BB_PATH . BB_INC . 'functions.bb-forums.php' );
require_once( BB_PATH . BB_INC . 'functions.bb-topics.php' );
require_once( BB_PATH . BB_INC . 'functions.bb-posts.php' );
require_once( BB_PATH . BB_INC . 'functions.bb-topic-tags.php' );
require_once( BB_PATH . BB_INC . 'functions.bb-users.php' );
require_once( BB_PATH . BB_INC . 'functions.bb-meta.php' );
require_once( BB_PATH . BB_INC . 'class.bb-query.php' );
require_once( BB_PATH . BB_INC . 'class.bb-walker.php' );



/**
 * Load API and object handling BackPress libraries
 */

// Plugin API
if ( !function_exists( 'add_filter' ) ) {
	require_once( BACKPRESS_PATH . 'functions.plugin-api.php' );
}

// Shortcodes API
if ( !function_exists( 'add_shortcode' ) ) {
	require_once( BACKPRESS_PATH . 'functions.shortcodes.php' );
} else {
	remove_all_shortcodes();
}



/**
 * Define the full path to the object cache functions include
 */
$_internal_object_cache_functions_include = BACKPRESS_PATH . 'loader.wp-object-cache.php';
$_memcached_object_cache_functions_include = BACKPRESS_PATH . 'loader.wp-object-cache-memcached.php';
if ( !defined( 'BB_OBJECT_CACHE_FUNCTIONS_INCLUDE' ) ) {
	if ( defined( 'BB_OBJECT_CACHE_TYPE' ) && 'memcached' === BB_OBJECT_CACHE_TYPE ) {
		define( 'BB_OBJECT_CACHE_FUNCTIONS_INCLUDE', $_memcached_object_cache_functions_include );
	} else {
		define( 'BB_OBJECT_CACHE_FUNCTIONS_INCLUDE', $_internal_object_cache_functions_include );
	}
}

// See if a caching class is already loaded (by WordPress)
if ( function_exists( 'wp_cache_init' ) ) {
	if ( isset( $_wp_using_ext_object_cache ) ) {
		$_bb_using_ext_object_cache = $_wp_using_ext_object_cache;
	} else {
		$_bb_using_ext_object_cache = false;
	}
} elseif ( BB_OBJECT_CACHE_FUNCTIONS_INCLUDE ) {
	// Load the object cache class
	require_once( BB_OBJECT_CACHE_FUNCTIONS_INCLUDE );
	if ( BB_OBJECT_CACHE_FUNCTIONS_INCLUDE === $_internal_object_cache_functions_include ) {
		$_bb_using_ext_object_cache = false;
	} else {
		$_bb_using_ext_object_cache = true;
	}
}
unset( $_internal_object_cache_functions_include );

// Instantiate the $wp_object_cache object using wp_cache_init()
if ( function_exists( 'wp_cache_init' ) ) {
	// Clear WordPress cache if it exists already - maybe should save and re-load?
	unset( $wp_object_cache );
	wp_cache_init();
	if ( function_exists( 'wp_cache_add_global_groups' ) ) {
		wp_cache_add_global_groups( array( 'users', 'userlogins', 'usermeta', 'useremail', 'usernicename' ) );
	}
}



/**
 * Load mapping class for BackPress to store options
 */
require_once( BB_PATH . BB_INC . 'class.bp-options.php' );
require_once( BACKPRESS_PATH . 'functions.bp-options.php' );



/**
 * Load WP_Http class
 */
if ( !class_exists( 'WP_Http' ) ) {
	require_once( BACKPRESS_PATH . 'class.wp-http.php' );
}



/**
 * Determine language settings and load i10n libraries as required
 */

/**
 * The full path to the directory containing language files
 */
if ( !defined( 'BB_LANG_DIR' ) ) {
	if ( BB_LOAD_DEPRECATED && defined( 'BBLANGDIR' ) ) {
		// User has set old constant
		bb_log_deprecated( 'constant', 'BBLANGDIR', 'BB_LANG_DIR' );
		define( 'BB_LANG_DIR', BBLANGDIR );
	} else {
		define( 'BB_LANG_DIR', BB_PATH . 'my-languages/' ); // absolute path with trailing slash
	}
}

/**
 * The language in which to display bbPress
 */
if ( BB_LOAD_DEPRECATED && !defined( 'BB_LANG' ) && defined( 'BBLANG' ) && '' != BBLANG ) {
	// User has set old constant
	bb_log_deprecated( 'constant', 'BBLANG', 'BB_LANG' );
	define( 'BB_LANG', BBLANG );
}
if ( !class_exists( 'MO' ) ) {
	require_once( BACKPRESS_PATH . 'pomo/mo.php' );
}

// Is WordPress loaded
if ( !defined( 'BB_IS_WP_LOADED') ) {
	define( 'BB_IS_WP_LOADED', defined( 'DB_NAME' ) );
}

// Only load this if WordPress isn't loaded
if ( !BB_IS_WP_LOADED ) {
	require_once( BACKPRESS_PATH . 'functions.kses.php' );
}
require_once( BB_PATH . BB_INC . 'functions.bb-l10n.php' );



/**
 * Routines related to installation
 */

// Load BB_CHANNELS_INCLUDE if it exists, must be done before the install is completed
if ( defined( 'BB_CHANNELS_INCLUDE' ) && file_exists( BB_CHANNELS_INCLUDE ) && !is_dir( BB_CHANNELS_INCLUDE ) ) {
	require_once( BB_CHANNELS_INCLUDE );
}

// If there is no forum table in the database then redirect to the installer
if ( !BB_INSTALLING && !bb_is_installed() ) {
	$link = preg_replace( '|(/bb-admin)?/[^/]+?$|', '/', $_SERVER['PHP_SELF'] ) . 'bb-admin/install.php';
	require_once( BB_PATH . BB_INC . 'functions.bb-pluggable.php' );
	wp_redirect( $link );
	die();
}

// Setup some variables in the $bb class if they don't exist - some of these are deprecated
if ( BB_LOAD_DEPRECATED ) {
	foreach ( array( 'use_cache' => false, 'debug' => false ) as $o => $oo ) {
		if ( !isset( $bb->$o ) ) {
			$bb->$o = $oo;
		} else {
			bb_log_deprecated( 'variable', '$bb->' . $o );
		}
	}
	unset( $o, $oo );
}

// Disable plugins during installation
if ( BB_INSTALLING ) {
	foreach ( array('active_plugins') as $i ) {
		$bb->$i = false;
	}
	unset( $i );
}



/**
 * Load additional bbPress libraries
 */

require_once( BB_PATH . BB_INC . 'functions.bb-formatting.php' );
require_once( BB_PATH . BB_INC . 'functions.bb-template.php' );
require_once( BB_PATH . BB_INC . 'functions.bb-capabilities.php' );
require_once( BB_PATH . BB_INC . 'class.bb-pingbacks.php' );



// Cache options from the database
if ( !isset( $bb->load_options ) ) {
	$bb->load_options = true;
}
if ( $bb->load_options ) {
	$bbdb->suppress_errors();
	bb_cache_all_options();
	$bbdb->suppress_errors( false );
}

/**
 * Set the URI and derivitaves
 */
if ( $bb->uri = bb_get_option( 'uri' ) ) {
	$bb->uri = rtrim( trim( $bb->uri ), " \t\n\r\0\x0B/" ) . '/';
	
	if ( preg_match( '@^(https?://[^/]+)((?:/.*)*/{1,1})$@i', $bb->uri, $matches ) ) {
		// Used when setting up cookie domain
		$bb->domain = $matches[1];
		// Used when setting up cookie paths
		$bb->path = $matches[2];
	}
	unset( $matches );
} elseif ( BB_LOAD_DEPRECATED ) {
	// Backwards compatibility
	// These were never set in the database
	if ( isset( $bb->domain ) ) {
		bb_log_deprecated( 'variable', '$bb->domain', '$bb->uri' );
		$bb->domain = rtrim( trim( $bb->domain ), " \t\n\r\0\x0B/" );
	}
	if ( isset( $bb->path ) ) {
		bb_log_deprecated( 'variable', '$bb->path', '$bb->uri' );
		$bb->path = trim( $bb->path );
		if ( $bb->path != '/' ) $bb->path = '/' . trim( $bb->path, " \t\n\r\0\x0B/" ) . '/';
	}
	// We need both to build a uri
	if ( $bb->domain && $bb->path ) {
		$bb->uri = $bb->domain . $bb->path;
	}
}

// Die if no URI
if ( !BB_INSTALLING && !$bb->uri ) {
	bb_die( __( 'Could not determine site URI' ) );
}

/**
 * BB_FORCE_SSL_USER_FORMS - Whether to force use of ssl on user forms like login, registration and profile editing
 */
if ( !defined( 'BB_FORCE_SSL_USER_FORMS' ) ) {
	define( 'BB_FORCE_SSL_USER_FORMS', false );
}
force_ssl_login( BB_FORCE_SSL_USER_FORMS );

/**
 * BB_FORCE_SSL_ADMIN - Whether to force use of ssl in the admin area
 */
if ( !defined( 'BB_FORCE_SSL_ADMIN' ) ) {
	define( 'BB_FORCE_SSL_ADMIN', false );
}
force_ssl_admin( BB_FORCE_SSL_ADMIN );

// Load default filters
require_once( BB_PATH . BB_INC . 'defaults.bb-filters.php' );

// Load default scripts
require_once( BB_PATH . BB_INC . 'functions.bb-script-loader.php' );

/* Check if the globals have been sanitized by WordPress or not (else there would be extra slashes while deep integration) */
if ( !function_exists( 'wp_magic_quotes' ) ) {
	// Sanitise external input
	$_GET    = bb_global_sanitize( $_GET );
	$_POST   = bb_global_sanitize( $_POST );
	$_COOKIE = bb_global_sanitize( $_COOKIE, false );
	$_SERVER = bb_global_sanitize( $_SERVER );
}


/**
 * Define theme and plugin constants
 */

/**
 * Full path to the location of the core plugins directory
 */
define( 'BB_CORE_PLUGIN_DIR', BB_PATH . 'bb-plugins/' );

/**
 * Full URL of the core plugins directory
 */
define( 'BB_CORE_PLUGIN_URL', $bb->uri . 'bb-plugins/' );

/**
 * Full path to the location of the core themes directory
 */
define( 'BB_CORE_THEME_DIR', BB_PATH . 'bb-templates/' );

/**
 * Full URL of the core themes directory
 */
define( 'BB_CORE_THEME_URL', $bb->uri . 'bb-templates/' );

/**
 * The default theme
 */
define( 'BB_DEFAULT_THEME', 'core#kakumei' );

/**
 * Full path to the location of the default theme directory
 */
define( 'BB_DEFAULT_THEME_DIR', BB_CORE_THEME_DIR . 'kakumei/' );

/**
 * Full URL of the default theme directory
 */
define( 'BB_DEFAULT_THEME_URL', BB_CORE_THEME_URL . 'kakumei/' );

/**
 * Full path to the location of the user plugins directory
 */
if ( !defined( 'BB_PLUGIN_DIR' ) ) {
	if ( BB_LOAD_DEPRECATED && defined( 'BBPLUGINDIR' ) ) {
		// User has set old constant
		bb_log_deprecated( 'constant', 'BBPLUGINDIR', 'BB_PLUGIN_DIR' );
		define( 'BB_PLUGIN_DIR', BBPLUGINDIR );
	} else {
		define( 'BB_PLUGIN_DIR', BB_PATH . 'my-plugins/' );
	}
}

/**
 * Full URL of the user plugins directory
 */
if ( !defined( 'BB_PLUGIN_URL' ) ) {
	if ( BB_LOAD_DEPRECATED && defined( 'BBPLUGINURL' ) ) {
		// User has set old constant
		bb_log_deprecated( 'constant', 'BBPLUGINURL', 'BB_PLUGIN_URL' );
		define( 'BB_PLUGIN_URL', BBPLUGINURL );
	} else {
		define( 'BB_PLUGIN_URL', $bb->uri . 'my-plugins/' );
	}
}

/**
 * Full path to the location of the user themes directory
 */
if ( !defined( 'BB_THEME_DIR' ) ) {
	if ( BB_LOAD_DEPRECATED && defined( 'BBTHEMEDIR' ) ) {
		// User has set old constant
		bb_log_deprecated( 'constant', 'BBTHEMEDIR', 'BB_THEME_DIR' );
		define( 'BB_THEME_DIR', BBTHEMEDIR );
	} else {
		define( 'BB_THEME_DIR', BB_PATH . 'my-templates/' );
	}
}

/**
 * Full URL of the user themes directory
 */
if ( !defined( 'BB_THEME_URL' ) ) {
	if ( BB_LOAD_DEPRECATED && defined( 'BBTHEMEURL' ) ) {
		// User has set old constant
		bb_log_deprecated( 'constant', 'BBTHEMEURL', 'BB_THEME_URL' );
		define( 'BB_THEME_URL', BBTHEMEURL );
	} else {
		define( 'BB_THEME_URL', $bb->uri . 'my-templates/' );
	}
}

/**
 * Look-up arrays provide easier access to arbitrary plugin and theme locations
 */
$_default_plugin_locations = array(
	'core' => array(
		'dir' => BB_CORE_PLUGIN_DIR,
		'url' => BB_CORE_PLUGIN_URL,
		'cap' => 'manage_plugins'
	),
	'user' => array(
		'dir' => BB_PLUGIN_DIR,
		'url' => BB_PLUGIN_URL,
		'cap' => 'manage_plugins'
	)
);

if ( isset( $bb->plugin_locations ) && is_array( $bb->plugin_locations ) ) {
	$bb->plugin_locations = array_merge( $_default_plugin_locations, $bb->plugin_locations );
} else {
	$bb->plugin_locations = $_default_plugin_locations;
}

// Don't accept a plugin location called "all". Unlikely, but really not desirable.
if ( isset( $bb->plugin_locations['all'] ) ) {
	unset( $bb->plugin_locations['all'] );
}

$_default_theme_locations = array(
	'core' => array(
		'dir' => BB_CORE_THEME_DIR,
		'url' => BB_CORE_THEME_URL,
		'cap' => 'manage_themes'
	),
	'user' => array(
		'dir' => BB_THEME_DIR,
		'url' => BB_THEME_URL,
		'cap' => 'manage_themes'
	)
);

if ( isset( $bb->theme_locations ) && is_array( $bb->theme_locations ) ) {
	$bb->theme_locations = array_merge( $_default_theme_locations, $bb->theme_locations );
} else {
	$bb->theme_locations = $_default_theme_locations;
}



/**
 * Add custom tables if present
 */

// Resolve the various ways custom user tables might be setup
bb_set_custom_user_tables();

// Add custom databases if required
if ( isset( $bb->custom_databases ) ) {
	foreach ( $bb->custom_databases as $connection => $database ) {
		$bbdb->add_db_server( $connection, $database );
	}
}
unset( $connection, $database );

// Add custom tables if required
if ( isset( $bb->custom_tables ) ) {
	$bbdb->tables = array_merge( $bbdb->tables, $bb->custom_tables );
	if ( is_wp_error( $bbdb->set_prefix( $bbdb->prefix ) ) ) {
		die( __( 'Your user table prefix may only contain letters, numbers and underscores.' ) );
	}
}



/**
 * Sort out cookies so they work with WordPress (if required)
 * Note that database integration is no longer a pre-requisite for cookie integration
 */

$bb->wp_siteurl = bb_get_option( 'wp_siteurl' );
if ( $bb->wp_siteurl ) {
	$bb->wp_siteurl = rtrim( trim( $bb->wp_siteurl ), " \t\n\r\0\x0B/" );
}

$bb->wp_home = bb_get_option( 'wp_home' );
if ( $bb->wp_home ) {
	$bb->wp_home = rtrim( trim( $bb->wp_home ), " \t\n\r\0\x0B/" );
}

$bb->wp_cookies_integrated = false;
$bb->cookiedomain = bb_get_option( 'cookiedomain' );
$bb->cookiepath = bb_get_option( 'cookiepath' );
$bb->wordpress_mu_primary_blog_id = bb_get_option( 'wordpress_mu_primary_blog_id' );

if ( $bb->wp_siteurl && $bb->wp_home ) {
	if ( $bb->cookiedomain ) {
		$bb->wp_cookies_integrated = true;
	} else {
		$cookiedomain = bb_get_common_domains( $bb->uri, $bb->wp_home );
		if ( bb_match_domains( $bb->uri, $bb->wp_home ) ) {
			if ( $bb->wordpress_mu_primary_blog_id ) {
				$bb->cookiedomain = '.' . $cookiedomain;
			}
			if ( !$bb->cookiepath ) {
				$bb->cookiepath = bb_get_common_paths( $bb->uri, $bb->wp_home );
			}
			$bb->wp_cookies_integrated = true;
		} elseif ( $cookiedomain && strpos( $cookiedomain, '.' ) !== false ) {
			$bb->cookiedomain = '.' . $cookiedomain;
			if ( !$bb->cookiepath ) {
				$bb->cookiepath = bb_get_common_paths( $bb->uri, $bb->wp_home );
			}
			$bb->wp_cookies_integrated = true;
		}
		unset( $cookiedomain );
	}
}

define( 'BB_HASH', $bb->wp_cookies_integrated ? md5( $bb->wp_siteurl ) : md5( $bb->uri ) );

if ( BB_LOAD_DEPRECATED ) {
	// Deprecated setting
	$bb->usercookie = bb_get_option( 'usercookie' );
	if ( !$bb->usercookie ) {
		$bb->usercookie = ( $bb->wp_cookies_integrated ? 'wordpressuser_' : 'bb_user_' ) . BB_HASH;
	} else {
		bb_log_deprecated( 'variable', '$bb->usercookie' );
	}

	// Deprecated setting
	$bb->passcookie = bb_get_option( 'passcookie' );
	if ( !$bb->passcookie ) {
		$bb->passcookie = ( $bb->wp_cookies_integrated ? 'wordpresspass_' : 'bb_pass_' ) . BB_HASH;
	} else {
		bb_log_deprecated( 'variable', '$bb->passcookie' );
	}
}

$bb->authcookie = bb_get_option( 'authcookie' );
if ( !$bb->authcookie ) {
	$bb->authcookie = ( $bb->wp_cookies_integrated ? 'wordpress_' : 'bbpress_' ) . BB_HASH;
}

$bb->secure_auth_cookie = bb_get_option( 'secure_auth_cookie' );
if ( !$bb->secure_auth_cookie ) {
	$bb->secure_auth_cookie = ( $bb->wp_cookies_integrated ? 'wordpress_sec_' : 'bbpress_sec_' ) . BB_HASH;
}

$bb->logged_in_cookie = bb_get_option( 'logged_in_cookie' );
if ( !$bb->logged_in_cookie ) {
	$bb->logged_in_cookie = ( $bb->wp_cookies_integrated ? 'wordpress_logged_in_' : 'bbpress_logged_in_' ) . BB_HASH;
}

// Cookie path was set before integration logic above
if ( !$bb->cookiepath ) {
	$bb->cookiepath = $bb->wp_cookies_integrated ? preg_replace( '|https?://[^/]+|i', '', $bb->wp_home ) : $bb->path;
}
$bb->cookiepath = rtrim( trim( $bb->cookiepath ), " \t\n\r\0\x0B/" ) . '/';

$bb->admin_cookie_path = bb_get_option( 'admin_cookie_path' );
if ( !$bb->admin_cookie_path ) {
	$bb->admin_cookie_path = $bb->path . 'bb-admin';
}
if ( '/' !== $bb->admin_cookie_path = trim( $bb->admin_cookie_path ) ) {
	$bb->admin_cookie_path = rtrim( $bb->admin_cookie_path, " \t\n\r\0\x0B/" );
}

if ( BB_LOAD_DEPRECATED ) {
	$_plugin_cookie_paths = bb_get_option( 'plugin_cookie_paths' );

	// Deprecated settings
	if ( $_plugin_cookie_paths ) {
		if ( isset( $_plugin_cookie_paths['core'] ) && $_plugin_cookie_paths['core'] ) {
			$bb->core_plugins_cookie_path = $_plugin_cookie_paths['core'];
		}
		if ( isset( $_plugin_cookie_paths['user'] ) && $_plugin_cookie_paths['user'] ) {
			$bb->user_plugins_cookie_path = $_plugin_cookie_paths['user'];
		}
	} else {
		if ( $bb->core_plugins_cookie_path = bb_get_option( 'core_plugins_cookie_path' ) ) {
			bb_log_deprecated( 'variable', '$bb->core_plugins_cookie_path', '$bb->plugin_cookie_paths[\'core\']' );
		}
		if ( $bb->user_plugins_cookie_path = bb_get_option( 'user_plugins_cookie_path' ) ) {
			bb_log_deprecated( 'variable', '$bb->core_plugins_cookie_path', '$bb->plugin_cookie_paths[\'user\']' );
		}
	}

	if ( !$bb->core_plugins_cookie_path && isset( $bb->plugin_locations['core']['url'] ) && $bb->plugin_locations['core']['url'] ) {
		$bb->core_plugins_cookie_path = preg_replace( '|https?://[^/]+|i', '', $bb->plugin_locations['core']['url'] );
	}
	$bb->core_plugins_cookie_path = rtrim( trim( $bb->core_plugins_cookie_path ), " \t\n\r\0\x0B/" );

	if ( !$bb->user_plugins_cookie_path && isset( $bb->plugin_locations['user']['url'] ) && $bb->plugin_locations['user']['url'] ) {
		$bb->user_plugins_cookie_path = preg_replace( '|https?://[^/]+|i', '', $bb->plugin_locations['user']['url'] );
	}
	$bb->user_plugins_cookie_path = rtrim( trim( $bb->user_plugins_cookie_path ), " \t\n\r\0\x0B/" );

	if ( !$_plugin_cookie_paths ) {
		$bb->plugin_cookie_paths = array();
	}
	if ( !isset( $_plugin_cookie_paths['core'] ) ) {
		$bb->plugin_cookie_paths['core'] = $bb->core_plugins_cookie_path;
	}
	if ( !isset( $_plugin_cookie_paths['user'] ) ) {
		$bb->plugin_cookie_paths['user'] = $bb->user_plugins_cookie_path;
	}
}

$bb->sitecookiepath = bb_get_option( 'sitecookiepath' );
$_bb_sitecookiepath = $bb->sitecookiepath;
if ( !$bb->sitecookiepath && $bb->wp_cookies_integrated ) {
	$bb->sitecookiepath = preg_replace( '|https?://[^/]+|i', '', $bb->wp_siteurl );
	$_bb_sitecookiepath = $bb->sitecookiepath;
}
$bb->sitecookiepath = rtrim( trim( $bb->sitecookiepath ), " \t\n\r\0\x0B/" ) . '/';

$bb->wp_admin_cookie_path = bb_get_option( 'wp_admin_cookie_path' );
if ( !$bb->wp_admin_cookie_path && $bb->wp_cookies_integrated ) {
	if ( $bb->wordpress_mu_primary_blog_id ) {
		$bb->wp_admin_cookie_path = $_bb_sitecookiepath;
	} else {
		$bb->wp_admin_cookie_path = $_bb_sitecookiepath . '/wp-admin';
	}
}
if ( '/' !== $bb->wp_admin_cookie_path = trim( $bb->wp_admin_cookie_path ) ) {
	$bb->wp_admin_cookie_path = rtrim( $bb->wp_admin_cookie_path, " \t\n\r\0\x0B/" );
}

$bb->wp_plugins_cookie_path = bb_get_option( 'wp_plugins_cookie_path' );
if ( !$bb->wp_plugins_cookie_path && $bb->wp_cookies_integrated ) {
	// This is a best guess only, should be manually set to match WP_PLUGIN_URL
	$bb->wp_plugins_cookie_path = $_bb_sitecookiepath . '/wp-content/plugins';
}
if ( '/' !== $bb->wp_plugins_cookie_path = trim( $bb->wp_plugins_cookie_path ) ) {
	$bb->wp_plugins_cookie_path = rtrim( $bb->wp_plugins_cookie_path, " \t\n\r\0\x0B/" );
}
unset( $_bb_sitecookiepath );

/**
 * Should be exactly the same as the default value of the KEYS in bb-config-sample.php
 * @since 1.0
 */
$bb_default_secret_key = 'put your unique phrase here';



/**
 * Initialise localisation
 */

// Load the default text localization domain.
bb_load_default_textdomain();

// Pull in locale data after loading text domain.
require_once( BB_PATH . BB_INC . 'class.bb-locale.php' );

/**
 * Localisation object
 */
$bb_locale = new BB_Locale();



/**
 * Remaining BackPress
 */

// WP_Pass
if ( !class_exists( 'WP_Pass' ) ) {
	require_once( BACKPRESS_PATH . 'class.wp-pass.php' );
}

// WP_Users
if ( !class_exists( 'WP_Users' ) ) {
	require_once( BACKPRESS_PATH . 'class.wp-users.php' );
	$wp_users_object = new WP_Users( $bbdb );
}

if ( !class_exists( 'BP_Roles' ) ) {
	require_once( BACKPRESS_PATH . 'class.bp-roles.php' );
}

/**
 * BP_Roles object
 */
$wp_roles = new BP_Roles( $bbdb );

// BP_User
if ( !class_exists( 'BP_User' ) ) {
	require_once( BACKPRESS_PATH . 'class.bp-user.php' );
}

// WP_Auth
if ( !class_exists( 'WP_Auth' ) ) {
	require_once( BACKPRESS_PATH . 'class.wp-auth.php' );

	$cookies = array();

	$cookies['logged_in'][] = array(
		'domain' => $bb->cookiedomain,
		'path' => $bb->cookiepath,
		'name' => $bb->logged_in_cookie
	);

	if ( $bb->sitecookiepath && $bb->cookiepath != $bb->sitecookiepath ) {
		$cookies['logged_in'][] = array(
			'domain' => $bb->cookiedomain,
			'path' => $bb->sitecookiepath,
			'name' => $bb->logged_in_cookie
		);
	}

	$cookies['auth'][] = array(
		'domain' => $bb->cookiedomain,
		'path' => $bb->admin_cookie_path,
		'name' => $bb->authcookie
	);

	$cookies['secure_auth'][] = array(
		'domain' => $bb->cookiedomain,
		'path' => $bb->admin_cookie_path,
		'name' => $bb->secure_auth_cookie,
		'secure' => true
	);

	$_plugin_cookie_paths = bb_get_option( 'plugin_cookie_paths' );
	foreach ( $bb->plugin_locations as $_name => $_data ) {
		if ( isset( $_data['cookie_path'] ) && !empty( $_data['cookie_path'] ) ) {
			$_cookie_path = $_data['cookie_path'];
		} elseif ( !$_plugin_cookie_paths || !isset( $_plugin_cookie_paths[$_name] ) || !$_plugin_cookie_paths[$_name] ) {
			$_cookie_path = preg_replace( '|https?://[^/]+|i', '', $_data['url'] );
		} else {
			$_cookie_path = $_plugin_cookie_paths[$_name];
		}
		if ( '/' !== $_cookie_path = trim( $_cookie_path ) ) {
			$_cookie_path = rtrim( $_cookie_path, " \t\n\r\0\x0B/" );
		}

		if ( !$_cookie_path ) {
			continue;
		}

		$_auth = array(
			'domain' => $bb->cookiedomain,
			'path' => $_cookie_path,
			'name' => $bb->authcookie
		);

		if ( !in_array( $_auth, $cookies['auth'] ) ) {
			$cookies['auth'][] = $_auth;
		}

		$_secure_auth = array(
			'domain' => $bb->cookiedomain,
			'path' => $_cookie_path,
			'name' => $bb->secure_auth_cookie,
			'secure' => true
		);

		if ( !in_array( $_secure_auth, $cookies['secure_auth'] ) ) {
			$cookies['secure_auth'][] = $_secure_auth;
		}
	}
	unset( $_plugin_cookie_paths, $_type, $_data, $_cookie_path, $_auth, $_secure_auth );

	if ( $bb->wp_admin_cookie_path ) {
		$cookies['auth'][] = array(
			'domain' => $bb->cookiedomain,
			'path' => $bb->wp_admin_cookie_path,
			'name' => $bb->authcookie
		);

		$cookies['secure_auth'][] = array(
			'domain' => $bb->cookiedomain,
			'path' => $bb->wp_admin_cookie_path,
			'name' => $bb->secure_auth_cookie,
			'secure' => true
		);
	}

	if ( $bb->wp_plugins_cookie_path ) {
		$cookies['auth'][] = array(
			'domain' => $bb->cookiedomain,
			'path' => $bb->wp_plugins_cookie_path,
			'name' => $bb->authcookie
		);

		$cookies['secure_auth'][] = array(
			'domain' => $bb->cookiedomain,
			'path' => $bb->wp_plugins_cookie_path,
			'name' => $bb->secure_auth_cookie,
			'secure' => true
		);
	}

	/**
	 * The current cookie version
	 *
	 * Version 1 is for WordPress >= 2.6 and < 2.8
	 * Version 2 is for Wordpress >= 2.8
	 */
	if ( !defined( 'WP_AUTH_COOKIE_VERSION' ) ) {
		define( 'WP_AUTH_COOKIE_VERSION', 2 );
	}

	/**
	 * WP_Auth object
	 */
	$wp_auth_object = new WP_Auth( $bbdb, $wp_users_object, $cookies );

	unset( $cookies );
}

/**
 * Current user object
 */
$bb_current_user =& $wp_auth_object->current;

// WP_Scripts/WP_Styles
if ( !class_exists( 'WP_Dependencies' ) ) {
	require_once( BACKPRESS_PATH . 'class.wp-dependencies.php' );
}

if ( !class_exists( 'WP_Scripts' ) ) {
	require_once( BACKPRESS_PATH . 'class.wp-scripts.php' );
	require_once( BACKPRESS_PATH . 'functions.wp-scripts.php' );
} else {
	unset( $wp_scripts );
}

if ( !class_exists( 'WP_Styles' ) ) {
	require_once( BACKPRESS_PATH . 'class.wp-styles.php' );
	require_once( BACKPRESS_PATH . 'functions.wp-styles.php' );
} else {
	unset( $wp_styles );
}



// WP_Taxonomy
if ( !class_exists( 'WP_Taxonomy' ) ) {
	require_once( BACKPRESS_PATH . 'class.wp-taxonomy.php' );
}
if ( !class_exists( 'BB_Taxonomy' ) ) {
	require_once( BB_PATH . BB_INC . 'class.bb-taxonomy.php' );
}
if ( !isset( $wp_taxonomy_object ) ) {
	// Clean slate
	$wp_taxonomy_object = new BB_Taxonomy( $bbdb );
} elseif ( !is_a( $wp_taxonomy_object, 'BB_Taxonomy' ) ) {
	// exists, but it's not good enough, translate it

	// preserve the references
	$tax =& $wp_taxonomy_object->taxonomies;
	$wp_taxonomy_object = new BB_Taxonomy( $bbdb );
	$wp_taxonomy_object->taxonomies =& $tax;
	unset( $tax );
}

$wp_taxonomy_object->register_taxonomy( 'bb_topic_tag', 'bb_topic' );

$wp_taxonomy_object->register_taxonomy( 'bb_subscribe', 'bb_user' );

do_action( 'bb_options_loaded' );



/**
 * Load deprecated constants and functions
 */

// Skip loading of deprecated stuff unless specifically requested
if ( BB_LOAD_DEPRECATED ) {
	/**
	 * Define deprecated constants for plugin compatibility
	 * $deprecated_constants below is a complete array of old constants and their replacements
	 */
	$deprecated_constants = array(
		'BBPATH'      => 'BB_PATH',
		'BBINC'       => 'BB_INC',
		'BBLANG'      => 'BB_LANG',
		'BBLANGDIR'   => 'BB_LANG_DIR',
		'BBPLUGINDIR' => 'BB_PLUGIN_DIR',
		'BBPLUGINURL' => 'BB_PLUGIN_URL',
		'BBTHEMEDIR'  => 'BB_THEME_DIR',
		'BBTHEMEURL'  => 'BB_THEME_URL',
		'BBHASH'      => 'BB_HASH'
	);
	foreach ( $deprecated_constants as $old => $new ) {
		// only define if new one is defined
		if ( !defined( $old ) && defined( $new ) ) {
			define( $old, constant( $new ) );
		} elseif ( defined( $old ) ) {
			bb_log_deprecated( 'constant', $old, $new );
		}
	}

	$deprecated_constants = array(
		'USER_BBDB_NAME'         => 'user_bbdb_name',
		'USER_BBDB_USER'         => 'user_bbdb_user',
		'USER_BBDB_PASSWORD'     => 'user_bbdb_password',
		'USER_BBDB_HOST'         => 'user_bbdb_host',
		'USER_BBDB_CHARSET'      => 'user_bbdb_charset',
		'CUSTOM_USER_TABLE'      => 'custom_user_table',
		'CUSTOM_USER_META_TABLE' => 'custom_user_meta_table',
	);
	foreach ( $deprecated_constants as $old => $new ) {
		if ( !defined( $old ) ) {
			define( $old, $bb->$new );
		} else {
			bb_log_deprecated( 'constant', $old, '$bb->' . $new );
		}
	}
	unset($deprecated_constants, $old, $new);

	/**
	 * Load deprecated functions
	 */
	require_once( BB_PATH . BB_INC . 'functions.bb-deprecated.php' );

	/**
	 * Old cache global object for backwards compatibility
	 */
	$bb_cache = new BB_Cache();
}



/**
 * Load active template functions.php file
 */
$template_functions_include = bb_get_active_theme_directory() . 'functions.php';
if ( file_exists( $template_functions_include ) ) {
	require_once( $template_functions_include );
}
unset( $template_functions_include );



/**
 * Load Plugins
 */

// Skip plugin loading in "safe" mode
if ( $bb->plugin_locations && ( !isset( $bb->safemode ) || $bb->safemode !== true ) ) {
	// Autoloaded "underscore" plugins
	foreach ( $bb->plugin_locations as $_name => $_data ) {
		foreach ( bb_glob( $_data['dir'] . '_*.php' ) as $_plugin ) {
			require_once( $_plugin );
		}
		unset( $_plugin );
	}
	unset( $_name, $_data );
	do_action( 'bb_underscore_plugins_loaded' );

	// Normal plugins
	if ( $_plugins = bb_get_option( 'active_plugins' ) ) {
		foreach ( (array) $_plugins as $_plugin ) {
			if ( !preg_match( '/^([a-z0-9_-]+)#((?:[a-z0-9\/\\_-]+.)+)(php)$/i', $_plugin, $_matches ) ) {
				// The plugin entry in the database is invalid
				continue;
			}

			$_directory = $bb->plugin_locations[$_matches[1]]['dir'];
			$_plugin = $_matches[2] . $_matches[3];

			if ( !$_plugin ) {
				// Not likely
				continue;
			}

			if ( validate_file( $_plugin ) ) {
				// $plugin has .., :, etc.
				continue;
			}

			if ( !file_exists( $_directory . $_plugin ) ) {
				// The plugin isn't there
				continue;
			}

			require_once( $_directory . $_plugin );
		}
	}
	unset( $_plugins, $_plugin, $_directory );
	do_action( 'bb_plugins_loaded' );
}

require_once( BB_PATH . BB_INC . 'functions.bb-pluggable.php' );



/**
 * Reference to $wp_roles
 */
$bb_roles =& $wp_roles;
do_action( 'bb_got_roles' );



/**
 * Create an API hook to run on shutdown
 */

function bb_shutdown_action_hook() {
	do_action( 'bb_shutdown' );
}
register_shutdown_function( 'bb_shutdown_action_hook' );


/**
 * Get details of the current user
 */

bb_current_user();



/**
 * Initialise CRON
 */

if ( !function_exists( 'wp_schedule_single_event' ) ) {
	require_once( BACKPRESS_PATH . 'functions.wp-cron.php' );
}
if ( ( !defined('DOING_CRON') || !DOING_CRON ) ) {
	wp_cron();
}



/**
 * The currently viewed page number
 */
$page = bb_get_uri_page();



/**
 * Initialisation complete API hook
 */

do_action( 'bb_init' );



/**
 * Block user if they deserve it
 */

if ( bb_is_user_logged_in() && bb_has_broken_pass() ) {
	bb_block_current_user();
}



/**
 * Send HTTP headers
 */

bb_send_headers();
