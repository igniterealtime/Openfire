<?php
// Last sync [WP11544]

/**
 * From WP wp-includes/functions.php
 *
 * Missing functions are indicated in comments
 */

/**
 * Main BackPress API
 *
 * @package BackPress
 */

// ! function mysql2date()

if ( !function_exists('current_time') ) :
/**
 * Retrieve the current time based on specified type.
 *
 * The 'mysql' type will return the time in the format for MySQL DATETIME field.
 * The 'timestamp' type will return the current timestamp.
 *
 * If $gmt is set to either '1' or 'true', then both types will use GMT time.
 * if $gmt is false, the output is adjusted with the GMT offset in the WordPress option.
 *
 * @since 1.0.0
 *
 * @param string $type Either 'mysql' or 'timestamp'.
 * @param int|bool $gmt Optional. Whether to use GMT timezone. Default is false.
 * @return int|string String if $type is 'gmt', int if $type is 'timestamp'.
 */
function current_time( $type, $gmt = 0 ) {
	switch ( $type ) {
		case 'mysql':
			return ( $gmt ) ? gmdate( 'Y-m-d H:i:s' ) : gmdate( 'Y-m-d H:i:s', ( time() + ( backpress_get_option( 'gmt_offset' ) * 3600 ) ) );
			break;
		case 'timestamp':
			return ( $gmt ) ? time() : time() + ( backpress_get_option( 'gmt_offset' ) * 3600 );
			break;
	}
}
endif;

// ! function date_i18n()
// ! function number_format_i18n()
// ! function size_format()
// ! function get_weekstartend()

if ( !function_exists('maybe_unserialize') ) :
/**
 * Unserialize value only if it was serialized.
 *
 * @since 2.0.0
 *
 * @param string $original Maybe unserialized original, if is needed.
 * @return mixed Unserialized data can be any type.
 */
function maybe_unserialize( $original ) {
	if ( is_serialized( $original ) ) // don't attempt to unserialize data that wasn't serialized going in
		return @unserialize( $original );
	return $original;
}
endif;

if ( !function_exists('is_serialized') ) :
/**
 * Check value to find if it was serialized.
 *
 * If $data is not an string, then returned value will always be false.
 * Serialized data is always a string.
 *
 * @since 2.0.5
 *
 * @param mixed $data Value to check to see if was serialized.
 * @return bool False if not serialized and true if it was.
 */
function is_serialized( $data ) {
	// if it isn't a string, it isn't serialized
	if ( !is_string( $data ) )
		return false;
	$data = trim( $data );
	if ( 'N;' == $data )
		return true;
	if ( !preg_match( '/^([adObis]):/', $data, $badions ) )
		return false;
	switch ( $badions[1] ) {
		case 'a' :
		case 'O' :
		case 's' :
			if ( preg_match( "/^{$badions[1]}:[0-9]+:.*[;}]\$/s", $data ) )
				return true;
			break;
		case 'b' :
		case 'i' :
		case 'd' :
			if ( preg_match( "/^{$badions[1]}:[0-9.E-]+;\$/", $data ) )
				return true;
			break;
	}
	return false;
}
endif;

if ( !function_exists('is_serialized_string') ) :
/**
 * Check whether serialized data is of string type.
 *
 * @since 2.0.5
 *
 * @param mixed $data Serialized data
 * @return bool False if not a serialized string, true if it is.
 */
function is_serialized_string( $data ) {
	// if it isn't a string, it isn't a serialized string
	if ( !is_string( $data ) )
		return false;
	$data = trim( $data );
	if ( preg_match( '/^s:[0-9]+:.*;$/s', $data ) ) // this should fetch all serialized strings
		return true;
	return false;
}
endif;

// ! function get_option()
// ! function wp_protect_special_option()
// ! function form_option()
// ! function get_alloptions()
// ! function wp_load_alloptions()
// ! function update_option()
// ! function add_option()
// ! function delete_option()
// ! function delete_transient()
// ! function get_transient()
// ! function set_transient()
// ! function wp_user_settings()
// ! function get_user_setting()
// ! function set_user_setting()
// ! function delete_user_setting()
// ! function get_all_user_settings()
// ! function wp_set_all_user_settings()
// ! function delete_all_user_settings()

if ( !function_exists('maybe_serialize') ) :
/**
 * Serialize data, if needed.
 *
 * @since 2.0.5
 *
 * @param mixed $data Data that might be serialized.
 * @return mixed A scalar data
 */
function maybe_serialize( $data ) {
	if ( is_array( $data ) || is_object( $data ) )
		return serialize( $data );

	if ( is_serialized( $data ) )
		return serialize( $data );

	return $data;
}
endif;

// ! function make_url_footnote()
// ! function xmlrpc_getposttitle()
// ! function xmlrpc_getpostcategory()
// ! function xmlrpc_removepostdata()
// ! function debug_fopen()
// ! function debug_fwrite()
// ! function debug_fclose()
// ! function do_enclose()
// ! function wp_get_http()
// ! function wp_get_http_headers()
// ! function is_new_day()

if ( !function_exists( 'build_query' ) ) :
/**
 * Build URL query based on an associative and, or indexed array.
 *
 * This is a convenient function for easily building url queries. It sets the
 * separator to '&' and uses _http_build_query() function.
 *
 * @see _http_build_query() Used to build the query
 * @link http://us2.php.net/manual/en/function.http-build-query.php more on what
 *		http_build_query() does.
 *
 * @since 2.3.0
 *
 * @param array $data URL-encode key/value pairs.
 * @return string URL encoded string
 */
function build_query( $data ) {
	return _http_build_query( $data, null, '&', '', false );
}
endif;

if ( !function_exists( 'add_query_arg' ) ) :
/**
 * Retrieve a modified URL query string.
 *
 * You can rebuild the URL and append a new query variable to the URL query by
 * using this function. You can also retrieve the full URL with query data.
 *
 * Adding a single key & value or an associative array. Setting a key value to
 * emptystring removes the key. Omitting oldquery_or_uri uses the $_SERVER
 * value.
 *
 * @since 1.5.0
 *
 * @param mixed $param1 Either newkey or an associative_array
 * @param mixed $param2 Either newvalue or oldquery or uri
 * @param mixed $param3 Optional. Old query or uri
 * @return string New URL query string.
 */
function add_query_arg() {
	$ret = '';
	if ( is_array( func_get_arg(0) ) ) {
		if ( @func_num_args() < 2 || false === @func_get_arg( 1 ) )
			$uri = $_SERVER['REQUEST_URI'];
		else
			$uri = @func_get_arg( 1 );
	} else {
		if ( @func_num_args() < 3 || false === @func_get_arg( 2 ) )
			$uri = $_SERVER['REQUEST_URI'];
		else
			$uri = @func_get_arg( 2 );
	}

	if ( $frag = strstr( $uri, '#' ) )
		$uri = substr( $uri, 0, -strlen( $frag ) );
	else
		$frag = '';

	if ( preg_match( '|^https?://|i', $uri, $matches ) ) {
		$protocol = $matches[0];
		$uri = substr( $uri, strlen( $protocol ) );
	} else {
		$protocol = '';
	}

	if ( strpos( $uri, '?' ) !== false ) {
		$parts = explode( '?', $uri, 2 );
		if ( 1 == count( $parts ) ) {
			$base = '?';
			$query = $parts[0];
		} else {
			$base = $parts[0] . '?';
			$query = $parts[1];
		}
	} elseif ( !empty( $protocol ) || strpos( $uri, '=' ) === false ) {
		$base = $uri . '?';
		$query = '';
	} else {
		$base = '';
		$query = $uri;
	}

	wp_parse_str( $query, $qs );
	$qs = urlencode_deep( $qs ); // this re-URL-encodes things that were already in the query string
	if ( is_array( func_get_arg( 0 ) ) ) {
		$kayvees = func_get_arg( 0 );
		$qs = array_merge( $qs, $kayvees );
	} else {
		$qs[func_get_arg( 0 )] = func_get_arg( 1 );
	}

	foreach ( (array) $qs as $k => $v ) {
		if ( $v === false )
			unset( $qs[$k] );
	}

	$ret = build_query( $qs );
	$ret = trim( $ret, '?' );
	$ret = preg_replace( '#=(&|$)#', '$1', $ret );
	$ret = $protocol . $base . $ret . $frag;
	$ret = rtrim( $ret, '?' );
	return $ret;
}
endif;

if ( !function_exists( 'remove_query_arg' ) ) :
/**
 * Removes an item or list from the query string.
 *
 * @since 1.5.0
 *
 * @param string|array $key Query key or keys to remove.
 * @param bool $query When false uses the $_SERVER value.
 * @return string New URL query string.
 */
function remove_query_arg( $key, $query=false ) {
	if ( is_array( $key ) ) { // removing multiple keys
		foreach ( $key as $k )
			$query = add_query_arg( $k, false, $query );
		return $query;
	}
	return add_query_arg( $key, false, $query );
}
endif;

// ! function add_magic_quotes()

if ( !function_exists( 'wp_remote_fopen' ) ) :
/**
 * HTTP request for URI to retrieve content.
 *
 * @since 1.5.1
 * @uses wp_remote_get()
 *
 * @param string $uri URI/URL of web page to retrieve.
 * @return bool|string HTTP content. False on failure.
 */
function wp_remote_fopen( $uri ) {
	$parsed_url = @parse_url( $uri );

	if ( !$parsed_url || !is_array( $parsed_url ) )
		return false;

	$options = array();
	$options['timeout'] = 10;

	$response = wp_remote_get( $uri, $options );

	if ( is_wp_error( $response ) )
		return false;

	return $response['body'];
}
endif;

// ! function wp()

if ( !function_exists( 'get_status_header_desc' ) ) :
/**
 * Retrieve the description for the HTTP status.
 *
 * @since 2.3.0
 *
 * @param int $code HTTP status code.
 * @return string Empty string if not found, or description if found.
 */
function get_status_header_desc( $code ) {
	global $wp_header_to_desc;

	$code = absint( $code );

	if ( !isset( $wp_header_to_desc ) ) {
		$wp_header_to_desc = array(
			100 => 'Continue',
			101 => 'Switching Protocols',
			102 => 'Processing',

			200 => 'OK',
			201 => 'Created',
			202 => 'Accepted',
			203 => 'Non-Authoritative Information',
			204 => 'No Content',
			205 => 'Reset Content',
			206 => 'Partial Content',
			207 => 'Multi-Status',
			226 => 'IM Used',

			300 => 'Multiple Choices',
			301 => 'Moved Permanently',
			302 => 'Found',
			303 => 'See Other',
			304 => 'Not Modified',
			305 => 'Use Proxy',
			306 => 'Reserved',
			307 => 'Temporary Redirect',

			400 => 'Bad Request',
			401 => 'Unauthorized',
			402 => 'Payment Required',
			403 => 'Forbidden',
			404 => 'Not Found',
			405 => 'Method Not Allowed',
			406 => 'Not Acceptable',
			407 => 'Proxy Authentication Required',
			408 => 'Request Timeout',
			409 => 'Conflict',
			410 => 'Gone',
			411 => 'Length Required',
			412 => 'Precondition Failed',
			413 => 'Request Entity Too Large',
			414 => 'Request-URI Too Long',
			415 => 'Unsupported Media Type',
			416 => 'Requested Range Not Satisfiable',
			417 => 'Expectation Failed',
			422 => 'Unprocessable Entity',
			423 => 'Locked',
			424 => 'Failed Dependency',
			426 => 'Upgrade Required',

			500 => 'Internal Server Error',
			501 => 'Not Implemented',
			502 => 'Bad Gateway',
			503 => 'Service Unavailable',
			504 => 'Gateway Timeout',
			505 => 'HTTP Version Not Supported',
			506 => 'Variant Also Negotiates',
			507 => 'Insufficient Storage',
			510 => 'Not Extended'
		);
	}

	if ( isset( $wp_header_to_desc[$code] ) )
		return $wp_header_to_desc[$code];
	else
		return '';
}
endif;

if ( !function_exists( 'status_header' ) ) :
/**
 * Set HTTP status header.
 *
 * @since 2.0.0
 * @uses apply_filters() Calls 'status_header' on status header string, HTTP
 *		HTTP code, HTTP code description, and protocol string as separate
 *		parameters.
 *
 * @param int $header HTTP status code
 * @return null Does not return anything.
 */
function status_header( $header ) {
	$text = get_status_header_desc( $header );

	if ( empty( $text ) )
		return false;

	$protocol = $_SERVER["SERVER_PROTOCOL"];
	if ( 'HTTP/1.1' != $protocol && 'HTTP/1.0' != $protocol )
		$protocol = 'HTTP/1.0';
	$status_header = "$protocol $header $text";
	if ( function_exists( 'apply_filters' ) )
		$status_header = apply_filters( 'status_header', $status_header, $header, $text, $protocol );

	return @header( $status_header, true, $header );
}
endif;

if ( !function_exists( 'wp_get_nocache_headers' ) ) :
/**
 * Gets the header information to prevent caching.
 *
 * The several different headers cover the different ways cache prevention is handled
 * by different browsers
 *
 * @since 2.8
 *
 * @uses apply_filters()
 * @return array The associative array of header names and field values.
 */
function wp_get_nocache_headers() {
	$headers = array(
		'Expires' => 'Wed, 11 Jan 1984 05:00:00 GMT',
		'Last-Modified' => gmdate( 'D, d M Y H:i:s' ) . ' GMT',
		'Cache-Control' => 'no-cache, must-revalidate, max-age=0',
		'Pragma' => 'no-cache',
	);

	if ( function_exists('apply_filters') ) {
		$headers = apply_filters('nocache_headers', $headers);
	}
	return $headers;
}
endif;

if ( !function_exists( 'nocache_headers' ) ) :
/**
 * Sets the headers to prevent caching for the different browsers.
 *
 * Different browsers support different nocache headers, so several headers must
 * be sent so that all of them get the point that no caching should occur.
 *
 * @since 2.0.0
 * @uses wp_get_nocache_headers()
 */
function nocache_headers() {
	$headers = wp_get_nocache_headers();
	foreach( (array) $headers as $name => $field_value )
		@header("{$name}: {$field_value}");
}
endif;

if ( !function_exists( 'cache_javascript_headers' ) ) :
/**
 * Set the headers for caching for 10 days with JavaScript content type.
 *
 * @since 2.1.0
 */
function cache_javascript_headers() {
	$expiresOffset = 864000; // 10 days
	header( "Content-Type: text/javascript; charset=" . backpress_get_option( 'charset' ) );
	header( "Vary: Accept-Encoding" ); // Handle proxies
	header( "Expires: " . gmdate( "D, d M Y H:i:s", time() + $expiresOffset ) . " GMT" );
}
endif;

// ! function get_num_queries()
// ! function bool_from_yn()
// ! function do_feed()
// ! function do_feed_rdf()
// ! function do_feed_rss()
// ! function do_feed_rss2()
// ! function do_feed_atom()
// ! function do_robots()
// ! function is_blog_installed()
// ! function wp_nonce_url()
// ! function wp_nonce_field()

if ( !function_exists( 'wp_referer_field' ) ) :
/**
 * Retrieve or display referer hidden field for forms.
 *
 * The referer link is the current Request URI from the server super global. The
 * input name is '_wp_http_referer', in case you wanted to check manually.
 *
 * @package WordPress
 * @subpackage Security
 * @since 2.0.4
 *
 * @param bool $echo Whether to echo or return the referer field.
 * @return string Referer field.
 */
function wp_referer_field( $echo = true) {
	$ref = esc_attr( $_SERVER['REQUEST_URI'] );
	$referer_field = '<input type="hidden" name="_wp_http_referer" value="'. $ref . '" />';

	if ( $echo )
		echo $referer_field;
	return $referer_field;
}
endif;

if ( !function_exists( 'wp_original_referer_field' ) ) :
/**
 * Retrieve or display original referer hidden field for forms.
 *
 * The input name is '_wp_original_http_referer' and will be either the same
 * value of {@link wp_referer_field()}, if that was posted already or it will
 * be the current page, if it doesn't exist.
 *
 * @package WordPress
 * @subpackage Security
 * @since 2.0.4
 *
 * @param bool $echo Whether to echo the original http referer
 * @param string $jump_back_to Optional, default is 'current'. Can be 'previous' or page you want to jump back to.
 * @return string Original referer field.
 */
function wp_original_referer_field( $echo = true, $jump_back_to = 'current' ) {
	$jump_back_to = ( 'previous' == $jump_back_to ) ? wp_get_referer() : $_SERVER['REQUEST_URI'];
	$ref = ( wp_get_original_referer() ) ? wp_get_original_referer() : $jump_back_to;
	$orig_referer_field = '<input type="hidden" name="_wp_original_http_referer" value="' . esc_attr( stripslashes( $ref ) ) . '" />';
	if ( $echo )
		echo $orig_referer_field;
	return $orig_referer_field;
}
endif;

if ( !function_exists( 'wp_get_referer' ) ) :
/**
 * Retrieve referer from '_wp_http_referer', HTTP referer, or current page respectively.
 *
 * @package WordPress
 * @subpackage Security
 * @since 2.0.4
 *
 * @return string|bool False on failure. Referer URL on success.
 */
function wp_get_referer() {
	$ref = '';
	if ( ! empty( $_REQUEST['_wp_http_referer'] ) )
		$ref = $_REQUEST['_wp_http_referer'];
	else if ( ! empty( $_SERVER['HTTP_REFERER'] ) )
		$ref = $_SERVER['HTTP_REFERER'];

	if ( $ref !== $_SERVER['REQUEST_URI'] )
		return $ref;
	return false;
}
endif;

if ( !function_exists( 'wp_get_original_referer' ) ) :
/**
 * Retrieve original referer that was posted, if it exists.
 *
 * @package WordPress
 * @subpackage Security
 * @since 2.0.4
 *
 * @return string|bool False if no original referer or original referer if set.
 */
function wp_get_original_referer() {
	if ( !empty( $_REQUEST['_wp_original_http_referer'] ) )
		return $_REQUEST['_wp_original_http_referer'];
	return false;
}
endif;

// ! function wp_mkdir_p()
// ! function path_is_absolute()
// ! function path_join()
// ! function wp_upload_dir()
// ! function wp_unique_filename()
// ! function wp_upload_bits()
// ! function wp_ext2type()
// ! function wp_check_filetype()
// ! function wp_explain_nonce()
// ! function wp_nonce_ays()
// ! function wp_die()
// ! function _config_wp_home()
// ! function _config_wp_siteurl()
// ! function _mce_set_direction()
// ! function smilies_init()

if ( !function_exists('wp_parse_args') ) :
/**
 * Merge user defined arguments into defaults array.
 *
 * This function is used throughout WordPress to allow for both string or array
 * to be merged into another array.
 *
 * @since 2.2.0
 *
 * @param string|array $args Value to merge with $defaults
 * @param array $defaults Array that serves as the defaults.
 * @return array Merged user defined values with defaults.
 */
function wp_parse_args( $args, $defaults = '' ) {
	if ( is_object( $args ) )
		$r = get_object_vars( $args );
	elseif ( is_array( $args ) )
		$r =& $args;
	else
		wp_parse_str( $args, $r );

	if ( is_array( $defaults ) )
		return array_merge( $defaults, $r );
	return $r;
}
endif;

// ! function wp_maybe_load_widgets()
// ! function wp_widgets_add_menu()
// ! function wp_ob_end_flush_all()
// ! function require_wp_db()
// ! function dead_db()

if ( !function_exists('absint') ) :
/**
 * Converts value to nonnegative integer.
 *
 * @since 2.5.0
 *
 * @param mixed $maybeint Data you wish to have convered to an nonnegative integer
 * @return int An nonnegative integer
 */
function absint( $maybeint ) {
	return abs( intval( $maybeint ) );
}
endif;

// ! function url_is_accessable_via_ssl()
// ! function atom_service_url_filter()
// ! function _deprecated_function()
// ! function _deprecated_file()

if ( !function_exists('is_lighttpd_before_150') ) :
/**
 * Is the server running earlier than 1.5.0 version of lighttpd
 *
 * @since 2.5.0
 *
 * @return bool Whether the server is running lighttpd < 1.5.0
 */
function is_lighttpd_before_150() {
	$server_parts = explode( '/', isset( $_SERVER['SERVER_SOFTWARE'] )? $_SERVER['SERVER_SOFTWARE'] : '' );
	$server_parts[1] = isset( $server_parts[1] )? $server_parts[1] : '';
	return  'lighttpd' == $server_parts[0] && -1 == version_compare( $server_parts[1], '1.5.0' );
}
endif;

if ( !function_exists('apache_mod_loaded') ) :
/**
 * Does the specified module exist in the apache config?
 *
 * @since 2.5.0
 *
 * @param string $mod e.g. mod_rewrite
 * @param bool $default The default return value if the module is not found
 * @return bool
 */
function apache_mod_loaded($mod, $default = false) {
	global $is_apache;

	if ( !$is_apache )
		return false;

	if ( function_exists('apache_get_modules') ) {
		$mods = apache_get_modules();
		if ( in_array($mod, $mods) )
			return true;
	} elseif ( function_exists('phpinfo') ) {
			ob_start();
			phpinfo(8);
			$phpinfo = ob_get_clean();
			if ( false !== strpos($phpinfo, $mod) )
				return true;
	}
	return $default;
}
endif;

if ( !function_exists('validate_file') ) :
/**
 * File validates against allowed set of defined rules.
 *
 * A return value of '1' means that the $file contains either '..' or './'. A
 * return value of '2' means that the $file contains ':' after the first
 * character. A return value of '3' means that the file is not in the allowed
 * files list.
 *
 * @since 1.2.0
 *
 * @param string $file File path.
 * @param array $allowed_files List of allowed files.
 * @return int 0 means nothing is wrong, greater than 0 means something was wrong.
 */
function validate_file( $file, $allowed_files = '' ) {
	if ( false !== strpos( $file, '..' ))
		return 1;

	if ( false !== strpos( $file, './' ))
		return 1;

	if (':' == substr( $file, 1, 1 ))
		return 2;

	if (!empty ( $allowed_files ) && (!in_array( $file, $allowed_files ) ) )
		return 3;

	return 0;
}
endif;

if ( !function_exists('is_ssl') ) :
/**
 * Determine if SSL is used.
 *
 * @since 2.6.0
 *
 * @return bool True if SSL, false if not used.
 */
function is_ssl() {
	if ( isset($_SERVER['HTTPS']) ) {
		if ( 'on' == strtolower($_SERVER['HTTPS']) )
			return true;
		if ( '1' == $_SERVER['HTTPS'] )
			return true;
	} elseif ( isset($_SERVER['SERVER_PORT']) && ( '443' == $_SERVER['SERVER_PORT'] ) ) {
		return true;
	}
	return false;
}
endif;

if ( !function_exists('force_ssl_login') ) :
/**
 * Whether SSL login should be forced.
 *
 * @since 2.6.0
 *
 * @param string|bool $force Optional.
 * @return bool True if forced, false if not forced.
 */
function force_ssl_login($force = '') {
	static $forced;

	if ( '' != $force ) {
		$old_forced = $forced;
		$forced = $force;
		return $old_forced;
	}

	return $forced;
}
endif;

if ( !function_exists('force_ssl_admin') ) :
/**
 * Whether to force SSL used for the Administration Panels.
 *
 * @since 2.6.0
 *
 * @param string|bool $force
 * @return bool True if forced, false if not forced.
 */
function force_ssl_admin($force = '') {
	static $forced;

	if ( '' != $force ) {
		$old_forced = $forced;
		$forced = $force;
		return $old_forced;
	}

	return $forced;
}
endif;

// ! function wp_guess_url()
// ! function wp_suspend_cache_invalidation()
// ! function get_site_option()
// ! function add_site_option()
// ! function update_site_option()

if ( !function_exists('wp_timezone_override_offset') ) :
/**
 * gmt_offset modification for smart timezone handling
 *
 * Overrides the gmt_offset option if we have a timezone_string available
 */
function wp_timezone_override_offset() {
	if ( !wp_timezone_supported() ) {
		return false;
	}
	if ( !$timezone_string = backpress_get_option( 'timezone_string' ) ) {
		return false;
	}

	@date_default_timezone_set( $timezone_string );
	$timezone_object = timezone_open( $timezone_string );
	$datetime_object = date_create();
	if ( false === $timezone_object || false === $datetime_object ) {
		return false;
	}
	return round( timezone_offset_get( $timezone_object, $datetime_object ) / 3600, 2 );
}
endif;

if ( !function_exists('wp_timezone_supported') ) :
/**
 * Check for PHP timezone support
 */
function wp_timezone_supported() {
	$support = false;
	if (
		function_exists( 'date_default_timezone_set' ) &&
		function_exists( 'timezone_identifiers_list' ) &&
		function_exists( 'timezone_open' ) &&
		function_exists( 'timezone_offset_get' )
	) {
		$support = true;
	}
	return apply_filters( 'timezone_support', $support );
}
endif;

if ( !function_exists('_wp_timezone_choice_usort_callback') ) :
function _wp_timezone_choice_usort_callback( $a, $b ) {
	// Don't use translated versions of Etc
	if ( 'Etc' === $a['continent'] && 'Etc' === $b['continent'] ) {
		// Make the order of these more like the old dropdown
		if ( 'GMT+' === substr( $a['city'], 0, 4 ) && 'GMT+' === substr( $b['city'], 0, 4 ) ) {
			return -1 * ( strnatcasecmp( $a['city'], $b['city'] ) );
		}
		if ( 'UTC' === $a['city'] ) {
			if ( 'GMT+' === substr( $b['city'], 0, 4 ) ) {
				return 1;
			}
			return -1;
		}
		if ( 'UTC' === $b['city'] ) {
			if ( 'GMT+' === substr( $a['city'], 0, 4 ) ) {
				return -1;
			}
			return 1;
		}
		return strnatcasecmp( $a['city'], $b['city'] );
	}
	if ( $a['t_continent'] == $b['t_continent'] ) {
		if ( $a['t_city'] == $b['t_city'] ) {
			return strnatcasecmp( $a['t_subcity'], $b['t_subcity'] );
		}
		return strnatcasecmp( $a['t_city'], $b['t_city'] );
	} else {
		// Force Etc to the bottom of the list
		if ( 'Etc' === $a['continent'] ) {
			return 1;
		}
		if ( 'Etc' === $b['continent'] ) {
			return -1;
		}
		return strnatcasecmp( $a['t_continent'], $b['t_continent'] );
	}
}
endif;

if ( !function_exists('wp_timezone_choice') ) :
/**
 * Gives a nicely formatted list of timezone strings // temporary! Not in final
 *
 * @param $selected_zone string Selected Zone
 *
 */
function wp_timezone_choice( $selected_zone ) {
	static $mo_loaded = false;

	$continents = array( 'Africa', 'America', 'Antarctica', 'Arctic', 'Asia', 'Atlantic', 'Australia', 'Europe', 'Indian', 'Pacific', 'Etc' );

	// Load translations for continents and cities
	if ( !$mo_loaded ) {
		$locale = backpress_get_option( 'language_locale' );
		$mofile = backpress_get_option( 'language_directory' ) . 'continents-cities-' . $locale . '.mo';
		load_textdomain( 'continents-cities', $mofile );
		$mo_loaded = true;
	}

	$zonen = array();
	foreach ( timezone_identifiers_list() as $zone ) {
		$zone = explode( '/', $zone );
		if ( !in_array( $zone[0], $continents ) ) {
			continue;
		}
		if ( 'Etc' === $zone[0] && in_array( $zone[1], array( 'UCT', 'GMT', 'GMT0', 'GMT+0', 'GMT-0', 'Greenwich', 'Universal', 'Zulu' ) ) ) {
			continue;
		}

		// This determines what gets set and translated - we don't translate Etc/* strings here, they are done later
		$exists = array(
			0 => ( isset( $zone[0] ) && $zone[0] ) ? true : false,
			1 => ( isset( $zone[1] ) && $zone[1] ) ? true : false,
			2 => ( isset( $zone[2] ) && $zone[2] ) ? true : false
		);
		$exists[3] = ( $exists[0] && 'Etc' !== $zone[0] ) ? true : false;
		$exists[4] = ( $exists[1] && $exists[3] ) ? true : false;
		$exists[5] = ( $exists[2] && $exists[3] ) ? true : false;

		$zonen[] = array(
			'continent'   => ( $exists[0] ? $zone[0] : '' ),
			'city'        => ( $exists[1] ? $zone[1] : '' ),
			'subcity'     => ( $exists[2] ? $zone[2] : '' ),
			't_continent' => ( $exists[3] ? translate( str_replace( '_', ' ', $zone[0] ), 'continents-cities' ) : '' ),
			't_city'      => ( $exists[4] ? translate( str_replace( '_', ' ', $zone[1] ), 'continents-cities' ) : '' ),
			't_subcity'   => ( $exists[5] ? translate( str_replace( '_', ' ', $zone[2] ), 'continents-cities' ) : '' )
		);
	}
	usort( $zonen, '_wp_timezone_choice_usort_callback' );

	$structure = array();

	if ( empty( $selected_zone ) ) {
		$structure[] = '<option selected="selected" value="">' . __( 'Select a city' ) . '</option>';
	}

	foreach ( $zonen as $key => $zone ) {
		// Build value in an array to join later
		$value = array( $zone['continent'] );

		if ( empty( $zone['city'] ) ) {
			// It's at the continent level (generally won't happen)
			$display = $zone['t_continent'];
		} else {
			// It's inside a continent group
			
			// Continent optgroup
			if ( !isset( $zonen[$key - 1] ) || $zonen[$key - 1]['continent'] !== $zone['continent'] ) {
				$label = ( 'Etc' === $zone['continent'] ) ? __( 'Manual offsets' ) : $zone['t_continent'];
				$structure[] = '<optgroup label="'. esc_attr( $label ) .'">';
			}
			
			// Add the city to the value
			$value[] = $zone['city'];
			if ( 'Etc' === $zone['continent'] ) {
				if ( 'UTC' === $zone['city'] ) {
					$display = '';
				} else {
					$display = str_replace( 'GMT', '', $zone['city'] );
					$display = strtr( $display, '+-', '-+' ) . ':00';
				}
				$display = sprintf( __( 'UTC %s' ), $display );
			} else {
				$display = $zone['t_city'];
				if ( !empty( $zone['subcity'] ) ) {
					// Add the subcity to the value
					$value[] = $zone['subcity'];
					$display .= ' - ' . $zone['t_subcity'];
				}
			}
		}

		// Build the value
		$value = join( '/', $value );
		$selected = '';
		if ( $value === $selected_zone ) {
			$selected = 'selected="selected" ';
		}
		$structure[] = '<option ' . $selected . 'value="' . esc_attr( $value ) . '">' . esc_html( $display ) . "</option>";
		
		// Close continent optgroup
		if ( !empty( $zone['city'] ) && ( !isset($zonen[$key + 1]) || (isset( $zonen[$key + 1] ) && $zonen[$key + 1]['continent'] !== $zone['continent']) ) ) {
			$structure[] = '</optgroup>';
		}
	}

	return join( "\n", $structure );
}
endif;

if ( !function_exists('_cleanup_header_comment') ) :
/**
 * Strip close comment and close php tags from file headers used by WP
 * See http://core.trac.wordpress.org/ticket/8497
 *
 * @since 2.8
 */
function _cleanup_header_comment($str) {
	return trim(preg_replace("/\s*(?:\*\/|\?>).*/", '', $str));
}
endif;



/**
 * From WP wp-settings.php
 */

if ( !function_exists( 'wp_clone' ) ) :
/**
 * Copy an object.
 *
 * Returns a cloned copy of an object.
 *
 * @since 2.7.0
 *
 * @param object $object The object to clone
 * @return object The cloned object
 */
function wp_clone( $object ) {
	static $can_clone;
	if ( !isset( $can_clone ) ) {
		$can_clone = version_compare( phpversion(), '5.0', '>=' );
	}
	return $can_clone ? clone( $object ) : $object;
}
endif;



/**
 * BackPress only below
 */

if ( !function_exists('backpress_gmt_strtotime') ) :
function backpress_gmt_strtotime( $string ) {
	if ( is_numeric($string) )
		return $string;
	if ( !is_string($string) )
		return -1;

	if ( stristr($string, 'utc') || stristr($string, 'gmt') || stristr($string, '+0000') )
		return strtotime($string);

	if ( -1 == $time = strtotime($string . ' +0000') )
		return strtotime($string);

	return $time;
}
endif;

if ( !function_exists('backpress_convert_object') ) :
function backpress_convert_object( &$object, $output ) {
	if ( is_array( $object ) ) {
		foreach ( array_keys( $object ) as $key )
			backpress_convert_object( $object[$key], $output );
	} else {
		switch ( $output ) {
			case OBJECT  : break;
			case ARRAY_A : $object = get_object_vars($object); break;
			case ARRAY_N : $object = array_values(get_object_vars($object)); break;
		}
	}
}
endif;

if ( !function_exists('backpress_die') ) :
/**
 * Kill BackPress execution and display HTML message with error message.
 *
 * This function calls the die() PHP function. The difference is that a message
 * in HTML will be displayed to the user. It is recommended to use this function
 * only when the execution should not continue any further. It is not
 * recommended to call this function very often and normally you should try to
 * handle as many errors as possible silently.
 *
 * @param string $message Error message.
 * @param string $title Error title.
 * @param string|array $args Optional arguments to control behaviour.
 */
function backpress_die( $message, $title = '', $args = array() )
{
	$defaults = array( 'response' => 500, 'language' => 'en-US', 'text_direction' => 'ltr' );
	$r = wp_parse_args( $args, $defaults );

	if ( function_exists( 'is_wp_error' ) && is_wp_error( $message ) ) {
		if ( empty( $title ) ) {
			$error_data = $message->get_error_data();
			if ( is_array( $error_data ) && isset( $error_data['title'] ) ) {
				$title = $error_data['title'];
			}
		}
		$errors = $message->get_error_messages();
		switch ( count( $errors ) ) {
			case 0:
				$message = '';
				break;
			case 1:
				$message = '<p>' . $errors[0] . '</p>';
				break;
			default:
				$message = "<ul>\n\t\t<li>" . join( "</li>\n\t\t<li>", $errors ) . "</li>\n\t</ul>";
				break;
		}
	} elseif ( is_string( $message ) ) {
		$message = '<p>' . $message . '</p>';
	}

	if ( !headers_sent() ) {
		status_header( $r['response'] );
		nocache_headers();
		header( 'Content-Type: text/html; charset=utf-8' );
	}

	if ( empty( $title ) ) {
		if ( function_exists( '__' ) ) {
			$title = __( 'BackPress &rsaquo; Error' );
		} else {
			$title = 'BackPress &rsaquo; Error';
		}
	}

	if ( $r['text_direction'] ) {
		$language_attributes = ' dir="' . $r['text_direction'] . '"';
	}

	if ( $r['language'] ) {
		// Always XHTML 1.1 style
		$language_attributes .= ' lang="' . $r['language'] . '"';
	}
?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
<html xmlns="http://www.w3.org/1999/xhtml"<?php echo $language_attributes; ?>>
<head>
	<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
	<title><?php echo $title ?></title>
	<style type="text/css" media="screen">
		html { background: #f7f7f7; }

		body {
			background: #fff;
			color: #333;
			font-family: "Lucida Grande", Verdana, Arial, "Bitstream Vera Sans", sans-serif;
			margin: 2em auto 0 auto;
			width: 700px;
			padding: 1em 2em;
			-moz-border-radius: 11px;
			-khtml-border-radius: 11px;
			-webkit-border-radius: 11px;
			border-radius: 11px;
			border: 1px solid #dfdfdf;
		}

		a { color: #2583ad; text-decoration: none; }

		a:hover { color: #d54e21; }

		h1 {
			border-bottom: 1px solid #dadada;
			clear: both;
			color: #666;
			font: 24px Georgia, "Times New Roman", Times, serif;
			margin: 5px 0 0 -4px;
			padding: 0;
			padding-bottom: 7px;
		}

		h2 { font-size: 16px; }

		p, li {
			padding-bottom: 2px;
			font-size: 12px;
			line-height: 18px;
		}

		code { font-size: 13px; }

		ul, ol { padding: 5px 5px 5px 22px; }

		#error-page { margin-top: 50px; }

		#error-page p {
			font-size: 12px;
			line-height: 18px;
			margin: 25px 0 20px;
		}

		#error-page code { font-family: Consolas, Monaco, Courier, monospace; }
<?php
	if ( $r['text_direction'] === 'rtl') {
?>
		body {
			font-family: Tahoma, "Times New Roman";
		}

		h1 {
			font-family: Tahoma, "Times New Roman";
			margin: 5px -4px 0 0;
		}

		ul, ol { padding: 5px 22px 5px 5px; }
<?php
	}
?>
	</style>
</head>
<body id="error-page">
	<?php echo $message; ?>
</body>
</html>
<?php
	die();
}
endif;

/**
 * Acts the same as core PHP setcookie() but its arguments are run through the backpress_set_cookie filter.
 * 
 * If the filter returns false, setcookie() isn't called.
 */
function backpress_set_cookie() {
	$args = func_get_args();
	$args = apply_filters( 'backpress_set_cookie', $args );
	if ( $args === false ) return;
	call_user_func_array( 'setcookie', $args );
}