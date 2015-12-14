<?php
/**
 * Core bbPress functions.
 *
 * @package bbPress
 */



/**
 * Initialization functions mostly called in bb-settings.php
 */

/**
 * Marks things as deprecated and informs when they have been used.
 *
 * @since 0.9
 *
 * @param string $type The type of thing that was attempted: function, class::function, constant, variable or page.
 * @param string $name The thing that was called.
 * @param string $replacement Optional. The thing that should have been called.
 * @uses $bb_log BP_Log logging object.
 */
function bb_log_deprecated( $type, $name, $replacement = 'none' ) {
	global $bb_log;
	$bb_log->notice( sprintf( __( 'Using deprecated bbPress %1$s - %2$s - replace with - %3$s' ), $type, $name, $replacement ) );

	if ( $bb_log->level & BP_LOG_DEBUG && $bb_log->level & BP_LOG_NOTICE ) { // Only compute the location if we're going to log it.
		$backtrace = debug_backtrace();

		$file = $backtrace[2]['file'];

		if ( substr( $file, 0, strlen( BB_PATH ) - 1 ) == rtrim( BB_PATH, '\\/') )
			$file = substr( $file, strlen( BB_PATH ) );

		$file = str_replace( '\\', '/', $file );

		// 0 = this function, 1 = the deprecated function
		$bb_log->notice( '    ' . sprintf( __( 'on line %1$d of file %2$s' ), $backtrace[2]['line'], $file ) );
	}
}

/**
 * Sanitizes user input en-masse.
 *
 * @param mixed $array The array of values or a single value to sanitize, usually a global variable like $_GET or $_POST.
 * @param boolean $trim Optional. Whether to trim the value or not. Default is true.
 * @return mixed The sanitized data.
 */
function bb_global_sanitize( $array, $trim = true )
{
	foreach ( $array as $k => $v ) {
		if ( is_array( $v ) ) {
			$array[$k] = bb_global_sanitize( $v );
		} else {
			if ( !get_magic_quotes_gpc() ) {
				$array[$k] = addslashes( $v );
			}
			if ( $trim ) {
				$array[$k] = trim( $array[$k] );
			}
		}
	}

	return $array;
}

/**
 * Reports whether bbPress is installed by getting forums.
 *
 * @return boolean True if there are forums, otherwise false.
 */
function bb_is_installed()
{
	// Maybe grab all the forums and cache them
	global $bbdb;
	$bbdb->suppress_errors();
	$forums = (array) @bb_get_forums();
	$bbdb->suppress_errors(false);

	if ( !$forums ) {
		return false;
	}

	return true;
}

/**
 * Sets the required variables to connect to custom user tables.
 *
 * @return boolean Always returns true.
 */
function bb_set_custom_user_tables()
{
	global $bb;

	// Check for older style custom user table
	if ( !isset( $bb->custom_tables['users'] ) ) { // Don't stomp new setting style
		if ( $bb->custom_user_table = bb_get_option( 'custom_user_table' ) ) {
			if ( !isset( $bb->custom_tables ) ) {
				$bb->custom_tables = array();
			}
			$bb->custom_tables['users'] = $bb->custom_user_table;
		}
	}

	// Check for older style custom user meta table
	if ( !isset( $bb->custom_tables['usermeta'] ) ) { // Don't stomp new setting style
		if ( $bb->custom_user_meta_table = bb_get_option( 'custom_user_meta_table' ) ) {
			if ( !isset( $bb->custom_tables ) ) {
				$bb->custom_tables = array();
			}
			$bb->custom_tables['usermeta'] = $bb->custom_user_meta_table;
		}
	}

	// Check for older style wp_table_prefix
	if ( $bb->wp_table_prefix = bb_get_option( 'wp_table_prefix' ) ) { // User has set old constant
		if ( !isset( $bb->custom_tables ) ) {
			$bb->custom_tables = array(
				'users'    => $bb->wp_table_prefix . 'users',
				'usermeta' => $bb->wp_table_prefix . 'usermeta'
			);
		} else {
			if ( !isset( $bb->custom_tables['users'] ) ) { // Don't stomp new setting style
				$bb->custom_tables['users'] = $bb->wp_table_prefix . 'users';
			}
			if ( !isset( $bb->custom_tables['usermeta'] ) ) {
				$bb->custom_tables['usermeta'] = $bb->wp_table_prefix . 'usermeta';
			}
		}
	}

	if ( bb_get_option( 'wordpress_mu_primary_blog_id' ) ) {
		$bb->wordpress_mu_primary_blog_id = bb_get_option( 'wordpress_mu_primary_blog_id' );
	}

	// Check for older style user database
	if ( !isset( $bb->custom_databases ) ) {
		$bb->custom_databases = array();
	}
	if ( !isset( $bb->custom_databases['user'] ) ) {
		if ( $bb->user_bbdb_name = bb_get_option( 'user_bbdb_name' ) ) {
			$bb->custom_databases['user']['name'] = $bb->user_bbdb_name;
		}
		if ( $bb->user_bbdb_user = bb_get_option( 'user_bbdb_user' ) ) {
			$bb->custom_databases['user']['user'] = $bb->user_bbdb_user;
		}
		if ( $bb->user_bbdb_password = bb_get_option( 'user_bbdb_password' ) ) {
			$bb->custom_databases['user']['password'] = $bb->user_bbdb_password;
		}
		if ( $bb->user_bbdb_host = bb_get_option( 'user_bbdb_host' ) ) {
			$bb->custom_databases['user']['host'] = $bb->user_bbdb_host;
		}
		if ( $bb->user_bbdb_charset = bb_get_option( 'user_bbdb_charset' ) ) {
			$bb->custom_databases['user']['charset'] = $bb->user_bbdb_charset;
		}
		if ( $bb->user_bbdb_collate = bb_get_option( 'user_bbdb_collate' ) ) {
			$bb->custom_databases['user']['collate'] = $bb->user_bbdb_collate;
		}
		if ( isset( $bb->custom_databases['user'] ) ) {
			if ( isset( $bb->custom_tables['users'] ) ) {
				$bb->custom_tables['users'] = array( 'user', $bb->custom_tables['users'] );
			}
			if ( isset( $bb->custom_tables['usermeta'] ) ) {
				$bb->custom_tables['usermeta'] = array( 'user', $bb->custom_tables['usermeta'] );
			}
		}
	}

	return true;
}


/* Pagination */

/**
 * Retrieve paginated links for pages.
 *
 * Technically, the function can be used to create paginated link list for any
 * area. The 'base' argument is used to reference the url, which will be used to
 * create the paginated links. The 'format' argument is then used for replacing
 * the page number. It is however, most likely and by default, to be used on the
 * archive post pages.
 *
 * The 'type' argument controls format of the returned value. The default is
 * 'plain', which is just a string with the links separated by a newline
 * character. The other possible values are either 'array' or 'list'. The
 * 'array' value will return an array of the paginated link list to offer full
 * control of display. The 'list' value will place all of the paginated links in
 * an unordered HTML list.
 *
 * The 'total' argument is the total amount of pages and is an integer. The
 * 'current' argument is the current page number and is also an integer.
 *
 * An example of the 'base' argument is "http://example.com/all_posts.php%_%"
 * and the '%_%' is required. The '%_%' will be replaced by the contents of in
 * the 'format' argument. An example for the 'format' argument is "?page=%#%"
 * and the '%#%' is also required. The '%#%' will be replaced with the page
 * number.
 *
 * You can include the previous and next links in the list by setting the
 * 'prev_next' argument to true, which it is by default. You can set the
 * previous text, by using the 'prev_text' argument. You can set the next text
 * by setting the 'next_text' argument.
 *
 * If the 'show_all' argument is set to true, then it will show all of the pages
 * instead of a short list of the pages near the current page. By default, the
 * 'show_all' is set to false and controlled by the 'end_size' and 'mid_size'
 * arguments. The 'end_size' argument is how many numbers on either the start
 * and the end list edges, by default is 1. The 'mid_size' argument is how many
 * numbers to either side of current page, but not including current page.
 *
 * It is possible to add query vars to the link by using the 'add_args' argument
 * and see {@link add_query_arg()} for more information.
 *
 * @since 1.0
 *
 * @param string|array $args Optional. Override defaults.
 * @return array|string String of page links or array of page links.
 */
function bb_paginate_links( $args = '' ) {
	$defaults = array(
		'base'         => '%_%', // http://example.com/all_posts.php%_% : %_% is replaced by format (below)
		'format'       => '?page=%#%', // ?page=%#% : %#% is replaced by the page number
		'total'        => 1,
		'current'      => 0,
		'show_all'     => false,
		'prev_next'    => true,
		'prev_text'    => __( '&laquo; Previous' ),
		'next_text'    => __( 'Next &raquo;' ),
		'end_size'     => 1, // How many numbers on either end including the end
		'mid_size'     => 2, // How many numbers to either side of current not including current
		'type'         => 'plain',
		'add_args'     => false, // array of query args to add
		'add_fragment' => '',
		'n_title'      => __( 'Page %d' ), // Not from WP version
		'prev_title'   => __( 'Previous page' ), // Not from WP version
		'next_title'   => __( 'Next page' ) // Not from WP version
	);

	$args = wp_parse_args( $args, $defaults );
	extract( $args, EXTR_SKIP );

	// Who knows what else people pass in $args
	$total = (int) $total;
	if ( $total < 2 )
		return;
	$current  = (int) $current;
	$end_size = 0 < (int) $end_size ? (int) $end_size : 1; // Out of bounds?  Make it the default.
	$mid_size = 0 <= (int) $mid_size ? (int) $mid_size : 2;
	$add_args = is_array($add_args) ? $add_args : false;
	$r = '';
	$page_links = array();
	$n = 0;
	$dots = false;

	$empty_format = '';
	if ( strpos( $format, '?' ) === 0 ) {
		$empty_format = '?';
	}

	if ( $prev_next && $current && 1 < $current ) {
		$link = str_replace( '%_%', 2 == $current ? $empty_format : $format, $base );
		$link = str_replace( '%#%', $current - 1, $link );
		$link = str_replace( '?&', '?', $link );
		if ( $add_args )
			$link = add_query_arg( $add_args, $link );
		$link .= $add_fragment;
		$page_links[] = '<a class="prev page-numbers" href="' . esc_url( $link ) . '" title="' . esc_attr( $prev_title ) . '">' . $prev_text . '</a>';
	}

	for ( $n = 1; $n <= $total; $n++ ) {
		if ( $n == $current ) {
			$n_display = bb_number_format_i18n( $n );
			$n_display_title =  esc_attr( sprintf( $n_title, $n ) );
			$page_links[] = '<span class="page-numbers current" title="' . $n_display_title . '">' . $n_display . '</span>';
			$dots = true;
		} else {
			if ( $show_all || ( $n <= $end_size || ( $current && $n >= $current - $mid_size && $n <= $current + $mid_size ) || $n > $total - $end_size ) ) {
				$n_display = bb_number_format_i18n( $n );
				$n_display_title =  esc_attr( sprintf( $n_title, $n ) );
				$link = str_replace( '%_%', 1 == $n ? $empty_format : $format, $base );
				$link = str_replace( '%#%', $n, $link );
				$link = str_replace( '?&', '?', $link );
				if ( $add_args )
					$link = add_query_arg( $add_args, $link );
				$link .= $add_fragment;
				$page_links[] = '<a class="page-numbers" href="' . esc_url( $link ) . '" title="' . $n_display_title . '">' . $n_display . '</a>';
				$dots = true;
			} elseif ( $dots && !$show_all ) {
				$page_links[] = '<span class="page-numbers dots">&hellip;</span>';
				$dots = false;
			}
		}
	}
	if ( $prev_next && $current && ( $current < $total || -1 == $total ) ) {
		$link = str_replace( '%_%', $format, $base );
		$link = str_replace( '%#%', $current + 1, $link );
		if ( $add_args )
			$link = add_query_arg( $add_args, $link );
		$link .= $add_fragment;
		$page_links[] = '<a class="next page-numbers" href="' . esc_url( $link ) . '" title="' . esc_attr( $next_title ) . '">' . $next_text . '</a>';
	}
	switch ( $type ) {
		case 'array':
			return $page_links;
			break;
		case 'list':
			$r .= '<ul class="page-numbers">' . "\n\t" . '<li>';
			$r .= join( '</li>' . "\n\t" . '<li>', $page_links );
			$r .= '</li>' . "\n" . '</ul>' . "\n";
			break;
		default:
			$r = join( "\n", $page_links );
			break;
	}
	return $r;
}

function bb_get_uri_page() {
	if ( isset($_GET['page']) && is_numeric($_GET['page']) && 1 < (int) $_GET['page'] )
		return (int) $_GET['page'];

	if ( isset($_SERVER['PATH_INFO']) )
		$path = $_SERVER['PATH_INFO'];
	else
		if ( !$path = strtok($_SERVER['REQUEST_URI'], '?') )
			return 1;

	if ( preg_match( '/^\/([0-9]+)\/?$/', $path, $matches ) ) {
		$page = (int) $matches[1];
		if ( 1 < $page ) {
			return $page;
		}
	}

	if ( $page = strstr($path, '/page/') ) {
		$page = (int) substr($page, 6);
		if ( 1 < $page )
			return $page;
	}
	return 1;
}

//expects $item = 1 to be the first, not 0
function bb_get_page_number( $item, $per_page = 0 ) {
	if ( !$per_page )
		$per_page = bb_get_option('page_topics');
	return intval( ceil( $item / $per_page ) ); // page 1 is the first page
}



/* Time */

function bb_timer_stop($display = 0, $precision = 3) { //if called like bb_timer_stop(1), will echo $timetotal
	global $bb_timestart, $timeend;
	$mtime = explode(' ', microtime());
	$timeend = $mtime[1] + $mtime[0];
	$timetotal = $timeend - $bb_timestart;
	if ($display)
		echo bb_number_format_i18n($timetotal, $precision);
	return bb_number_format_i18n($timetotal, $precision);
}

// GMT -> so many minutes ago
function bb_since( $original, $args = '' )
{
	$defaults = array(
		'levels'    => 1,
		'separator' => ', '
	);

	// $args used to be $do_more
	// $do_more = 0 is equivalent to $args['levels'] = 1
	// $do_more = 1 is equivalent to $args['levels'] = 2
	if ( !is_array( $args ) ) {
		$args = array(
			'levels' => abs( (integer) $args ) + 1
		);
	}

	$args = wp_parse_args( $args, $defaults );
	extract( $args, EXTR_SKIP );

	$today = (integer) time();

	if ( !is_numeric( $original ) ) {
		if ( $today < $_original = bb_gmtstrtotime( str_replace( ',', ' ', $original ) ) ) { // Looks like bb_since was called twice
			return $original;
		} else {
			$original = $_original;
		}
	}

	$seconds = $today - ( (integer) $original );
	if ( 0 === $seconds ) {
		return sprintf( _n( '%d second', '%d seconds', 0 ), 0 );
	}

	$levels = abs( (integer) $levels );
	if ( 0 === $levels ) {
		return '';
	}

	// array of time period chunks
	$chunks = array(
		( 60 * 60 * 24 * 365 ), // years
		( 60 * 60 * 24 * 30 ),  // months
		( 60 * 60 * 24 * 7 ),   // weeks
		( 60 * 60 * 24 ),       // days
		( 60 * 60 ),            // hours
		( 60 ),                 // minutes
		( 1 )                   // seconds
	);

	$caught = 0;
	$parts = array();
	for ( $i = 0; $i < count( $chunks ); $i++ ) {
		if ( ( $count = floor( $seconds / $chunks[$i] ) ) || $caught ) {
			if ( $count ) {
				$trans = array(
					_n( '%d year', '%d years', $count ),
					_n( '%d month', '%d months', $count ),
					_n( '%d week', '%d weeks', $count ),
					_n( '%d day', '%d days', $count ),
					_n( '%d hour', '%d hours', $count ),
					_n( '%d minute', '%d minutes', $count ),
					_n( '%d second', '%d seconds', $count )
				);
				$parts[] = sprintf( $trans[$i], $count );
			}
			$caught++;
			$seconds = $seconds - ( $count * $chunks[$i] );
		}
		if ( $caught === $levels ) {
			break;
		}
	}

	if ( empty( $parts ) ) {
		return sprintf( _n( '%d second', '%d seconds', 0 ), 0 );
	}

	return join( $separator, $parts );
}

function bb_current_time( $type = 'timestamp' ) {
	return current_time( $type, true );
}

// GMT -> Local
// in future versions this could eaily become a user option.
function bb_offset_time( $time, $args = null ) {
	if ( isset($args['format']) && 'since' == $args['format'] )
		return $time;
	if ( !is_numeric($time) ) {
		if ( -1 !== $_time = bb_gmtstrtotime( $time ) )
			return gmdate('Y-m-d H:i:s', $_time + bb_get_option( 'gmt_offset' ) * 3600);
		else
			return $time; // Perhaps should return -1 here
	} else {
		return $time + bb_get_option( 'gmt_offset' ) * 3600;
	}
}



/* Permalinking / URLs / Paths */

/**
 * BB_URI_CONTEXT_* - Bitwise definitions for bb_uri() and bb_get_uri() contexts
 *
 * @since 1.0
 */
define( 'BB_URI_CONTEXT_NONE',                 0 );
define( 'BB_URI_CONTEXT_HEADER',               1 );
define( 'BB_URI_CONTEXT_TEXT',                 2 );
define( 'BB_URI_CONTEXT_A_HREF',               4 );
define( 'BB_URI_CONTEXT_FORM_ACTION',          8 );
define( 'BB_URI_CONTEXT_IMG_SRC',              16 );
define( 'BB_URI_CONTEXT_LINK_STYLESHEET_HREF', 32 );
define( 'BB_URI_CONTEXT_LINK_ALTERNATE_HREF',  64 );
define( 'BB_URI_CONTEXT_LINK_OTHER',           128 );
define( 'BB_URI_CONTEXT_SCRIPT_SRC',           256 );
define( 'BB_URI_CONTEXT_IFRAME_SRC',           512 );
define( 'BB_URI_CONTEXT_BB_FEED',              1024 );
define( 'BB_URI_CONTEXT_BB_USER_FORMS',        2048 );
define( 'BB_URI_CONTEXT_BB_ADMIN',             4096 );
define( 'BB_URI_CONTEXT_BB_XMLRPC',            8192 );
define( 'BB_URI_CONTEXT_WP_HTTP_REQUEST',      16384 );
//define( 'BB_URI_CONTEXT_*',                    32768 );  // Reserved for future definitions
//define( 'BB_URI_CONTEXT_*',                    65536 );  // Reserved for future definitions
//define( 'BB_URI_CONTEXT_*',                    131072 ); // Reserved for future definitions
//define( 'BB_URI_CONTEXT_*',                    262144 ); // Reserved for future definitions
define( 'BB_URI_CONTEXT_AKISMET',              524288 );

/**
 * Echo a URI based on the URI setting
 *
 * @since 1.0
 *
 * @param $resource string The directory, may include a querystring
 * @param $query mixed The query arguments as a querystring or an associative array
 * @param $context integer The context of the URI, use BB_URI_CONTEXT_*
 * @return void
 */
function bb_uri( $resource = null, $query = null, $context = BB_URI_CONTEXT_A_HREF )
{
	echo apply_filters( 'bb_uri', bb_get_uri( $resource, $query, $context ), $resource, $query, $context );
}

/**
 * Return a URI based on the URI setting
 *
 * @since 1.0
 *
 * @param $resource string The directory, may include a querystring
 * @param $query mixed The query arguments as a querystring or an associative array
 * @param $context integer The context of the URI, use BB_URI_CONTEXT_*
 * @return string The complete URI
 */
function bb_get_uri( $resource = null, $query = null, $context = BB_URI_CONTEXT_A_HREF )
{
	// If there is a querystring in the resource then extract it
	if ( $resource && strpos( $resource, '?' ) !== false ) {
		list( $_resource, $_query ) = explode( '?', trim( $resource ), 2 );
		$resource = $_resource;
		$_query = wp_parse_args( $_query );
	} else {
		// Make sure $_query is an array for array_merge()
		$_query = array();
	}

	// $query can be an array as well as a string
	if ( $query ) {
		if ( is_string( $query ) ) {
			$query = ltrim( trim( $query ), '?' );
		}
		$query = wp_parse_args( $query );
	}

	// Make sure $query is an array for array_merge()
	if ( !$query ) {
		$query = array();
	}

	// Merge the queries into a single array
	$query = array_merge( $_query, $query );

	// Make sure context is an integer
	if ( !$context || !is_integer( $context ) ) {
		$context = BB_URI_CONTEXT_A_HREF;
	}

	// Get the base URI
	static $_uri;
	if( !isset( $_uri ) ) {
		$_uri = bb_get_option( 'uri' );
	}
	$uri = $_uri;

	// Use https?
	if (
		( ( $context & BB_URI_CONTEXT_BB_USER_FORMS ) && force_ssl_login() ) // Force https when required on user forms
	||
		( ( $context & BB_URI_CONTEXT_BB_ADMIN ) && force_ssl_admin() ) // Force https when required in admin
	) {
		static $_uri_ssl;
		if( !isset( $_uri_ssl ) ) {
			$_uri_ssl = bb_get_option( 'uri_ssl' );
		}
		$uri = $_uri_ssl;
	}

	// Add the directory
	$uri .= ltrim( $resource, '/' );

	// Add the query string to the URI
	$uri = add_query_arg( $query, $uri );

	return apply_filters( 'bb_get_uri', $uri, $resource, $context );
}

/**
 * Forces redirection to an SSL page when required
 *
 * @since 1.0
 *
 * @return void
 */
function bb_ssl_redirect()
{
	$page = bb_get_location();

	do_action( 'bb_ssl_redirect' );

	if ( BB_IS_ADMIN ) {
		if ( !force_ssl_admin() ) {
			return;
		}
	} else {
		switch ( $page ) {
			case 'login-page':
			case 'register-page':
				if ( !force_ssl_login() ) {
					return;
				}
				break;
			case 'profile-page':
				global $self;
				if ( $self == 'profile-edit.php' ) {
					if ( !force_ssl_login() ) {
						return;
					}
				} else {
					return;
				}
				break;
			default:
				return;
				break;
		}
	}

	if ( is_ssl() ) {
		return;
	}

	$uri_ssl = parse_url( bb_get_option( 'uri_ssl' ) );
	$uri = $uri_ssl['scheme'] . '://' . $uri_ssl['host'] . $_SERVER['REQUEST_URI'];
	bb_safe_redirect( $uri );
	exit;
}

function bb_get_path( $level = 1, $base = false, $request = false ) {
	if ( !$request )
		$request = $_SERVER['REQUEST_URI'];
	if ( is_string($request) )
		$request = parse_url($request);
	if ( !is_array($request) || !isset($request['path']) )
		return '';

	$path = rtrim($request['path'], " \t\n\r\0\x0B/");
	if ( !$base )
		$base = rtrim(bb_get_option('path'), " \t\n\r\0\x0B/");
	$path = preg_replace('|' . preg_quote($base, '|') . '/?|','',$path,1);
	if ( !$path )
		return '';
	if ( strpos($path, '/') === false )
		return '';

	$url = explode('/',$path);
	if ( !isset($url[$level]) )
		return '';

	return urldecode($url[$level]);
}

function bb_find_filename( $text ) {
	if ( preg_match('|.*?/([a-z\-]+\.php)/?.*|', $text, $matches) )
		return $matches[1];
	else {
		$path = bb_get_option( 'path' );
		$text = preg_replace("#^$path#", '', $text);
		$text = preg_replace('#/.+$#', '', $text);
		return $text . '.php';
	}
	return false;
}

function bb_send_headers() {
	if ( bb_is_user_logged_in() )
		nocache_headers();
	@header('Content-Type: ' . bb_get_option( 'html_type' ) . '; charset=' . bb_get_option( 'charset' ));
	do_action( 'bb_send_headers' );
}

function bb_pingback_header() {
	if (bb_get_option('enable_pingback'))
		@header('X-Pingback: '. bb_get_uri('xmlrpc.php', null, BB_URI_CONTEXT_HEADER + BB_URI_CONTEXT_BB_XMLRPC));
}

// Inspired by and adapted from Yung-Lung Scott YANG's http://scott.yang.id.au/2005/05/permalink-redirect/ (GPL)
function bb_repermalink() {
	global $page;
	$location = bb_get_location();
	$uri = $_SERVER['REQUEST_URI'];
	if ( isset($_GET['id']) )
		$id = $_GET['id'];
	else
		$id = bb_get_path();
	$_original_id = $id;

	do_action( 'pre_permalink', $id );

	$id = apply_filters( 'bb_repermalink', $id );

	switch ($location) {
		case 'front-page':
			$path = null;
			$querystring = null;
			if ($page > 1) {
				if (bb_get_option( 'mod_rewrite' )) {
					$path = 'page/' . $page;
				} else {
					$querystring = array('page' => $page);
				}
			}
			$permalink = bb_get_uri($path, $querystring, BB_URI_CONTEXT_HEADER);
			$issue_404 = true;
			break;
		case 'forum-page':
			if (empty($id)) {
				$permalink = bb_get_uri(null, null, BB_URI_CONTEXT_HEADER);
				break;
			}
			global $forum_id, $forum;
			$forum     = bb_get_forum( $id );
			$forum_id  = $forum->forum_id;
			$permalink = get_forum_link( $forum->forum_id, $page );
			break;
		case 'topic-edit-page':
		case 'topic-page':
			if (empty($id)) {
				$permalink = bb_get_uri(null, null, BB_URI_CONTEXT_HEADER);
				break;
			}
			global $topic_id, $topic;
			$topic     = get_topic( $id );
			$topic_id  = $topic->topic_id;
			$permalink = get_topic_link( $topic->topic_id, $page );
			break;
		case 'profile-page': // This handles the admin side of the profile as well.
			global $user_id, $user, $profile_hooks, $self;
			if ( isset($_GET['id']) )
				$id = $_GET['id'];
			elseif ( isset($_GET['username']) )
				$id = $_GET['username'];
			else
				$id = bb_get_path();
			$_original_id = $id;
			
			if ( !$id ) {
				$user = bb_get_current_user(); // Attempt to go to the current users profile
			} else {
				if ( bb_get_option( 'mod_rewrite' ) === 'slugs') {
					if ( !$user = bb_get_user_by_nicename( $id ) ) {
						$user = bb_get_user( $id );
					}
				} else {
					if ( !$user = bb_get_user( $id ) ) {
						$user = bb_get_user_by_nicename( $id );
					}
				}
			}

			if ( !$user || ( 1 == $user->user_status && !bb_current_user_can( 'moderate' ) ) )
				bb_die(__('User not found.'), '', 404);

			$user_id = $user->ID;
			bb_global_profile_menu_structure();
			$valid = false;
			if ( $tab = isset($_GET['tab']) ? $_GET['tab'] : bb_get_path(2) ) {
				foreach ( $profile_hooks as $valid_tab => $valid_file ) {
					if ( $tab == $valid_tab ) {
						$valid = true;
						$self = $valid_file;
					}
				}
			}
			if ( $valid ) {
				$permalink = get_profile_tab_link( $user->ID, $tab, $page );
			} else {
				$permalink = get_user_profile_link( $user->ID, $page );
				unset($self, $tab);
			}
			break;
		case 'favorites-page':
			$permalink = get_favorites_link();
			break;
		case 'tag-page': // It's not an integer and tags.php pulls double duty.
			$id = ( isset($_GET['tag']) ) ? $_GET['tag'] : false;
			if ( ! $id || ! bb_get_tag( (string) $id ) )
				$permalink = bb_get_tag_page_link();
			else {
				global $tag, $tag_name;
				$tag_name = $id;
				$tag = bb_get_tag( (string) $id );
				$permalink = bb_get_tag_link( 0, $page ); // 0 => grabs $tag from global.
			}
			break;
		case 'view-page': // Not an integer
			if ( isset($_GET['view']) )
				$id = $_GET['view'];
			else
				$id = bb_get_path();
			$_original_id = $id;
			global $view;
			$view = $id;
			$permalink = get_view_link( $view, $page );
			break;
		default:
			return;
			break;
	}
	
	wp_parse_str($_SERVER['QUERY_STRING'], $args);
	$args = urlencode_deep($args);
	if ( $args ) {
		$permalink = add_query_arg($args, $permalink);
		if ( bb_get_option('mod_rewrite') ) {
			$pretty_args = array('id', 'page', 'tag', 'tab', 'username'); // these are already specified in the path
			if ( $location == 'view-page' )
				$pretty_args[] = 'view';
			foreach ( $pretty_args as $pretty_arg )
				$permalink = remove_query_arg( $pretty_arg, $permalink );
		}
	}

	$permalink = apply_filters( 'bb_repermalink_result', $permalink, $location );

	$domain = bb_get_option('domain');
	$domain = preg_replace('/^https?/', '', $domain);
	$check = preg_replace( '|^.*' . trim($domain, ' /' ) . '|', '', $permalink, 1 );
	$uri = rtrim( $uri, " \t\n\r\0\x0B?" );
	$uri = str_replace( '/index.php', '/', $uri );

	global $bb_log;
	$bb_log->debug($uri, 'bb_repermalink() ' . __('REQUEST_URI'));
	$bb_log->debug($check, 'bb_repermalink() ' . __('should be'));
	$bb_log->debug($permalink, 'bb_repermalink() ' . __('full permalink'));
	$bb_log->debug(isset($_SERVER['PATH_INFO']) ? $_SERVER['PATH_INFO'] : null, 'bb_repermalink() ' . __('PATH_INFO'));

	if ( $check != $uri && $check != str_replace(urlencode($_original_id), $_original_id, $uri) ) {
		if ( $issue_404 && rtrim( $check, " \t\n\r\0\x0B/" ) !== rtrim( $uri, " \t\n\r\0\x0B/" ) ) {
			status_header( 404 );
			bb_load_template( '404.php' );
		} else {
			wp_redirect( $permalink );
		}
		exit;
	}

	do_action( 'post_permalink', $permalink );
}

/* Profile/Admin */

function bb_global_profile_menu_structure() {
	global $user_id, $profile_menu, $profile_hooks;
	// Menu item name
	// The capability required for own user to view the tab ('' to allow non logged in access)
	// The capability required for other users to view the tab ('' to allow non logged in access)
	// The URL of the item's file
	// Item name for URL (nontranslated)
	$profile_menu[0] = array(__('Edit'), 'edit_profile', 'edit_users', 'profile-edit.php', 'edit');
	$profile_menu[5] = array(__('Favorites'), '', '', 'favorites.php', 'favorites');

	// Create list of page plugin hook names the current user can access
	$profile_hooks = array();
	foreach ($profile_menu as $profile_tab)
		if ( bb_can_access_tab( $profile_tab, bb_get_current_user_info( 'id' ), $user_id ) )
			$profile_hooks[bb_sanitize_with_dashes($profile_tab[4])] = $profile_tab[3];

	do_action('bb_profile_menu');
	ksort($profile_menu);
}

function bb_add_profile_tab($tab_title, $users_cap, $others_cap, $file, $arg = false) {
	global $profile_menu, $profile_hooks, $user_id;

	$arg = $arg ? $arg : $tab_title;

	$profile_tab = array($tab_title, $users_cap, $others_cap, $file, $arg);
	$profile_menu[] = $profile_tab;
	if ( bb_can_access_tab( $profile_tab, bb_get_current_user_info( 'id' ), $user_id ) )
		$profile_hooks[bb_sanitize_with_dashes($arg)] = $file;
}

function bb_can_access_tab( $profile_tab, $viewer_id, $owner_id ) {
	global $bb_current_user;
	$viewer_id = (int) $viewer_id;
	$owner_id = (int) $owner_id;
	if ( $viewer_id == bb_get_current_user_info( 'id' ) )
		$viewer =& $bb_current_user;
	else
		$viewer = new BP_User( $viewer_id );
	if ( !$viewer )
		return '' === $profile_tab[2];

	if ( $owner_id == $viewer_id ) {
		if ( '' === $profile_tab[1] )
			return true;
		else
			return $viewer->has_cap($profile_tab[1]);
	} else {
		if ( '' === $profile_tab[2] )
			return true;
		else
			return $viewer->has_cap($profile_tab[2]);
	}
}

//meta_key => (required?, Label, hCard property).  Don't use user_{anything} as the name of your meta_key.
function bb_get_profile_info_keys( $context = null ) {
	return apply_filters( 'get_profile_info_keys', array(
		'first_name' => array(0, __('First name')),
		'last_name' => array(0, __('Last name')),
		'display_name' => array(1, __('Display name as')),
		'user_email' => array(1, __('Email'), 'email'),
		'user_url' => array(0, __('Website'), 'url'),
		'from' => array(0, __('Location')),
		'occ' => array(0, __('Occupation'), 'role'),
		'interest' => array(0, __('Interests')),
	), $context );
}

function bb_get_profile_admin_keys( $context = null ) {
	global $bbdb;
	return apply_filters( 'get_profile_admin_keys', array(
		$bbdb->prefix . 'title' => array(0, __('Custom Title'))
	), $context );
}

function bb_get_assignable_caps() {
	$caps = array();
	if ( $throttle_time = bb_get_option( 'throttle_time' ) )
		$caps['throttle'] = sprintf( __('Ignore the %d second post throttling limit'), $throttle_time );
	return apply_filters( 'get_assignable_caps', $caps );
}

/* Views */

function bb_get_views() {
	global $bb_views;

	$views = array();
	foreach ( (array) $bb_views as $view => $array )
		$views[$view] = $array['title'];

	return $views;
}

function bb_register_view( $view, $title, $query_args = '', $feed = TRUE ) {
	global $bb_views;

	$view  = bb_slug_sanitize( $view );
	$title = esc_html( $title );

	if ( !$view || !$title )
		return false;

	$query_args = wp_parse_args( $query_args );

	if ( !$sticky_set = isset($query_args['sticky']) )
		$query_args['sticky'] = 'no';

	$bb_views[$view]['title']  = $title;
	$bb_views[$view]['query']  = $query_args;
	$bb_views[$view]['sticky'] = !$sticky_set; // No sticky set => split into stickies and not
	$bb_views[$view]['feed'] = $feed;
	return $bb_views[$view];
}

function bb_deregister_view( $view ) {
	global $bb_views;

	$view = bb_slug_sanitize( $view );
	if ( !isset($bb_views[$view]) )
		return false;

	unset($GLOBALS['bb_views'][$view]);
	return true;
}

function bb_view_query( $view, $new_args = '' ) {
	global $bb_views;

	$view = bb_slug_sanitize( $view );
	if ( !isset($bb_views[$view]) )
		return false;

	if ( $new_args ) {
		$new_args = wp_parse_args( $new_args );
		$query_args = array_merge( $bb_views[$view]['query'], $new_args );
	} else {
		$query_args = $bb_views[$view]['query'];
	}

	return new BB_Query( 'topic', $query_args, "bb_view_$view" );
}

function bb_get_view_query_args( $view ) {
	global $bb_views;

	$view = bb_slug_sanitize( $view );
	if ( !isset($bb_views[$view]) )
		return false;

	return $bb_views[$view]['query'];
}

function bb_register_default_views() {
	// no posts (besides the first one), older than 2 hours
	bb_register_view( 'no-replies', __('Topics with no replies'), array( 'post_count' => 1, 'started' => '<' . gmdate( 'YmdH', time() - 7200 ) ) );
	bb_register_view( 'untagged'  , __('Topics with no tags')   , array( 'tag_count'  => 0 ) );
}

/* Feeds */

/**
 * Send status headers for clients supporting Conditional Get
 *
 * The function sends the Last-Modified and ETag headers for all clients. It
 * then checks both the If-None-Match and If-Modified-Since headers to see if
 * the client has used them. If so, and the ETag does matches the client ETag
 * or the last modified date sent by the client is newer or the same as the
 * generated last modified, the function sends a 304 Not Modified and exits.
 *
 * @link http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.3
 * @param string $bb_last_modified Last modified time. Must be a HTTP-date
 */
function bb_send_304( $bb_last_modified ) {
	$bb_etag = '"' . md5($bb_last_modified) . '"';
	@header("Last-Modified: $bb_last_modified");
	@header("ETag: $bb_etag");

	// Support for Conditional GET
	if (isset($_SERVER['HTTP_IF_NONE_MATCH'])) $client_etag = stripslashes($_SERVER['HTTP_IF_NONE_MATCH']);
	else $client_etag = false;

	$client_last_modified = trim( $_SERVER['HTTP_IF_MODIFIED_SINCE']);
	// If string is empty, return 0. If not, attempt to parse into a timestamp
	$client_modified_timestamp = $client_last_modified ? bb_gmtstrtotime($client_last_modified) : 0;

	// Make a timestamp for our most recent modification...	
	$bb_modified_timestamp = bb_gmtstrtotime($bb_last_modified);

	if ( ($client_last_modified && $client_etag) ?
		 (($client_modified_timestamp >= $bb_modified_timestamp) && ($client_etag == $bb_etag)) :
		 (($client_modified_timestamp >= $bb_modified_timestamp) || ($client_etag == $bb_etag)) ) {
		status_header( 304 );
		exit;
	}
}

/* Nonce */

/**
 * Retrieve URL with nonce added to URL query.
 *
 * @package bbPress
 * @subpackage Security
 * @since 1.0
 *
 * @param string $actionurl URL to add nonce action
 * @param string $action Optional. Nonce action name
 * @return string URL with nonce action added.
 */
function bb_nonce_url( $actionurl, $action = -1 ) {
	$actionurl = str_replace( '&amp;', '&', $actionurl );
	$nonce = bb_create_nonce( $action );
	return esc_html( add_query_arg( '_wpnonce', $nonce, $actionurl ) );
}

/**
 * Retrieve or display nonce hidden field for forms.
 *
 * The nonce field is used to validate that the contents of the form came from
 * the location on the current site and not somewhere else. The nonce does not
 * offer absolute protection, but should protect against most cases. It is very
 * important to use nonce field in forms.
 *
 * If you set $echo to true and set $referer to true, then you will need to
 * retrieve the {@link wp_referer_field() wp referer field}. If you have the
 * $referer set to true and are echoing the nonce field, it will also echo the
 * referer field.
 *
 * The $action and $name are optional, but if you want to have better security,
 * it is strongly suggested to set those two parameters. It is easier to just
 * call the function without any parameters, because validation of the nonce
 * doesn't require any parameters, but since crackers know what the default is
 * it won't be difficult for them to find a way around your nonce and cause
 * damage.
 *
 * The input name will be whatever $name value you gave. The input value will be
 * the nonce creation value.
 *
 * @package bbPress
 * @subpackage Security
 * @since 1.0
 *
 * @param string $action Optional. Action name.
 * @param string $name Optional. Nonce name.
 * @param bool $referer Optional, default true. Whether to set the referer field for validation.
 * @param bool $echo Optional, default true. Whether to display or return hidden form field.
 * @return string Nonce field.
 */
function bb_nonce_field( $action = -1, $name = "_wpnonce", $referer = true , $echo = true ) {
	$name = esc_attr( $name );
	$nonce = bb_create_nonce( $action );
	$nonce_field = '<input type="hidden" id="' . $name . '" name="' . $name . '" value="' . $nonce . '" />';
	if ( $echo )
		echo $nonce_field;

	if ( $referer )
		wp_referer_field( $echo, 'previous' );

	return $nonce_field;
}

function bb_nonce_ays( $action )
{
	$title = __( 'bbPress Failure Notice' );
	$html .= "\t<div id='message' class='updated fade'>\n\t<p>" . esc_html( bb_explain_nonce( $action ) ) . "</p>\n\t<p>";
	if ( wp_get_referer() )
		$html .= "<a href='" . remove_query_arg( 'updated', esc_url( wp_get_referer() ) ) . "'>" . __( 'Please try again.' ) . "</a>";
	$html .= "</p>\n\t</div>\n";
	$html .= "</body>\n</html>";
	bb_die( $html, $title );
}

function bb_install_header( $title = '', $header = false, $logo = false )
{
	if ( empty($title) )
		if ( function_exists('__') )
			$title = __('bbPress');
		else
			$title = 'bbPress';
		
		$uri = false;
		if ( function_exists('bb_get_uri') && !BB_INSTALLING ) {
			$uri = bb_get_uri();
			$uri_stylesheet = bb_get_uri('bb-admin/install.css', null, BB_URI_CONTEXT_LINK_STYLESHEET_HREF + BB_URI_CONTEXT_BB_INSTALLER);
			$uri_stylesheet_rtl = bb_get_uri('bb-admin/install-rtl.css', null, BB_URI_CONTEXT_LINK_STYLESHEET_HREF + BB_URI_CONTEXT_BB_INSTALLER);
			$uri_logo = bb_get_uri('bb-admin/images/bbpress-logo.png', null, BB_URI_CONTEXT_IMG_SRC + BB_URI_CONTEXT_BB_INSTALLER);
		}
		
		if (!$uri) {
			$uri = preg_replace('|(/bb-admin)?/[^/]+?$|', '/', $_SERVER['PHP_SELF']);
			$uri_stylesheet = $uri . 'bb-admin/install.css';
			$uri_stylesheet_rtl = $uri . 'bb-admin/install-rtl.css';
			$uri_logo = $uri . 'bb-admin/images/bbpress-logo.png';
		}
	
	header('Content-Type: text/html; charset=utf-8');
?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml"<?php if ( function_exists( 'bb_language_attributes' ) ) bb_language_attributes(); ?>>
<head>
	<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
	<title><?php echo $title; ?></title>
	<meta name="robots" content="noindex, nofollow" />
	<link rel="stylesheet" href="<?php echo $uri_stylesheet; ?>" type="text/css" />
<?php
	if ( function_exists( 'bb_get_option' ) && 'rtl' == bb_get_option( 'text_direction' ) ) {
?>
	<link rel="stylesheet" href="<?php echo $uri_stylesheet_rtl; ?>" type="text/css" />
<?php
	}
?>
</head>
<body>
	<div id="container">
<?php
	if ( $logo ) {
?>
		<div class="logo">
			<img src="<?php echo $uri_logo; ?>" alt="bbPress" />
		</div>
<?php
	}

	if ( !empty($header) ) {
?>
		<h1>
			<?php echo $header; ?>
		</h1>
<?php
	}
}

function bb_install_footer() {
?>
	</div>
</body>
</html>
<?php
}

function bb_die( $message, $title = '', $header = 0 ) {
	global $bb_locale;

	if ( $header && !headers_sent() )
		status_header( $header );

	if ( function_exists( 'is_wp_error' ) && is_wp_error( $message ) ) {
		if ( empty( $title ) ) {
			$error_data = $message->get_error_data();
			if ( is_array( $error_data ) && isset( $error_data['title'] ) )
				$title = $error_data['title'];
		}
		$errors = $message->get_error_messages();
		switch ( count( $errors ) ) :
		case 0 :
			$message = '';
			break;
		case 1 :
			$message = "<p>{$errors[0]}</p>";
			break;
		default :
			$message = "<ul>\n\t\t<li>" . join( "</li>\n\t\t<li>", $errors ) . "</li>\n\t</ul>";
			break;
		endswitch;
	} elseif ( is_string( $message ) ) {
		$message = bb_autop( $message );
	}

	if ( empty($title) )
		$title = __('bbPress &rsaquo; Error');
	
	bb_install_header( $title );
?>
	<?php echo $message; ?>
<?php
	if ($uri = bb_get_uri()) {
?>
	<p class="last"><?php printf( __('Back to <a href="%s">%s</a>.'), $uri, bb_get_option( 'name' ) ); ?></p>
<?php
	}
	bb_install_footer();
	die();
}

function bb_explain_nonce($action) {
	if ( $action !== -1 && preg_match('/([a-z]+)-([a-z]+)(_(.+))?/', $action, $matches) ) {
		$verb = $matches[1];
		$noun = $matches[2];

		$trans = array();
		$trans['create']['post'] = array(__('Your attempt to submit this post has failed.'), false);
		$trans['edit']['post'] = array(__('Your attempt to edit this post has failed.'), false);
		$trans['delete']['post'] = array(__('Your attempt to delete this post has failed.'), false);

		$trans['create']['topic'] = array(__('Your attempt to create this topic has failed.'), false);
		$trans['resolve']['topic'] = array(__('Your attempt to change the resolution status of this topic has failed.'), false);
		$trans['delete']['topic'] = array(__('Your attempt to delete this topic has failed.'), false);
		$trans['close']['topic'] = array(__('Your attempt to change the status of this topic has failed.'), false);
		$trans['stick']['topic'] = array(__('Your attempt to change the sticky status of this topic has failed.'), false);
		$trans['move']['topic'] = array(__('Your attempt to move this topic has failed.'), false);

		$trans['add']['tag'] = array(__('Your attempt to add this tag to this topic has failed.'), false);
		$trans['rename']['tag'] = array(__('Your attempt to rename this tag has failed.'), false);
		$trans['merge']['tag'] = array(__('Your attempt to submit these tags has failed.'), false);
		$trans['destroy']['tag'] = array(__('Your attempt to destroy this tag has failed.'), false);
		$trans['remove']['tag'] = array(__('Your attempt to remove this tag from this topic has failed.'), false);

		$trans['toggle']['favorite'] = array(__('Your attempt to toggle your favorite status for this topic has failed.'), false);

		$trans['edit']['profile'] = array(__("Your attempt to edit this user's profile has failed."), false);

		$trans['add']['forum'] = array(__("Your attempt to add this forum has failed."), false);
		$trans['update']['forums'] = array(__("Your attempt to update your forums has failed."), false);
		$trans['delete']['forums'] = array(__("Your attempt to delete that forum has failed."), false);

		$trans['do']['counts'] = array(__("Your attempt to recount these items has failed."), false);

		$trans['switch']['theme'] = array(__("Your attempt to switch themes has failed."), false);

		if ( isset($trans[$verb][$noun]) ) {
			if ( !empty($trans[$verb][$noun][1]) ) {
				$lookup = $trans[$verb][$noun][1];
				$object = $matches[4];
				if ( 'use_id' != $lookup )
					$object = call_user_func($lookup, $object);
				return sprintf($trans[$verb][$noun][0], esc_html( $object ));
			} else {
				return $trans[$verb][$noun][0];
			}
		}
	}

	return apply_filters( 'bb_explain_nonce_' . $verb . '-' . $noun, __('Your attempt to do this has failed.'), $matches[4] );
}

/* DB Helpers */

function bb_count_last_query( $query = '' ) {
	global $bbdb, $bb_last_countable_query;

	if ( $query )
		$q = $query;
	elseif ( $bb_last_countable_query )
		$q = $bb_last_countable_query;
	else
		$q = $bbdb->last_query;

	if ( false === strpos($q, 'SELECT') )
		return false;

	if ( false !== strpos($q, 'SQL_CALC_FOUND_ROWS') )
		return (int) $bbdb->get_var( "SELECT FOUND_ROWS()" );

	$q_original = $q;

	$q = preg_replace(
		array('/SELECT.*?\s+FROM/', '/LIMIT [0-9]+(\s*,\s*[0-9]+)?/', '/ORDER BY\s+.*$/', '/DESC/', '/ASC/'),
		array('SELECT COUNT(*) FROM', ''),
		$q
	);

	if ( preg_match( '/GROUP BY\s+(\S+)/', $q, $matches ) )
		$q = str_replace( array( 'COUNT(*)', $matches[0] ), array( "COUNT(DISTINCT $matches[1])", '' ), $q );

	if ( !$query )
		$bb_last_countable_query = '';

	$q = apply_filters( 'bb_count_last_query', $q, $q_original );

	return (int) $bbdb->get_var($q);
}

function bb_no_where( $where ) {
	return;
}

/* Plugins/Themes utility */

function bb_basename( $file, $directories )
{
	if ( strpos( $file, '#' ) !== false ) {
		return $file; // It's already a basename
	}

	foreach ( $directories as $type => $directory ) {
		if ( strpos( $file, $directory ) !== false ) {
			break; // Keep the $file and $directory set and use them below, nifty huh?
		}
	}

	list( $file, $directory ) = str_replace( '\\','/', array( $file, $directory ) );
	list( $file, $directory ) = preg_replace( '|/+|','/', array( $file,$directory ) );
	$file = preg_replace( '|^.*' . preg_quote( $directory, '|' ) . '|', $type . '#', $file );

	return $file;
}

/* Plugins */

function bb_plugin_basename( $file )
{
	global $bb;
	$directories = array();
	foreach ( $bb->plugin_locations as $_name => $_data ) {
		$directories[$_name] = $_data['dir'];
	}
	return bb_basename( $file, $directories );
}

function bb_register_plugin_activation_hook( $file, $function )
{
	$file = bb_plugin_basename( $file );
	add_action( 'bb_activate_plugin_' . $file, $function );
}

function bb_register_plugin_deactivation_hook( $file, $function )
{
	$file = bb_plugin_basename( $file );
	add_action( 'bb_deactivate_plugin_' . $file, $function );
}

function bb_get_plugin_uri( $plugin = false )
{
	global $bb;
	if ( preg_match( '/^([a-z0-9_-]+)#((?:[a-z0-9\/\\_-]+.)+)(php)$/i', $plugin, $_matches ) ) {
		$plugin_uri = $bb->plugin_locations[$_matches[1]]['url'] . $_matches[2] . $_matches[3];
		$plugin_uri = dirname( $plugin_uri ) . '/';
	} else {
		$plugin_uri = $bb->plugin_locations['core']['url'];
	}
	return apply_filters( 'bb_get_plugin_uri', $plugin_uri, $plugin );
}

function bb_get_plugin_directory( $plugin = false, $path = false )
{
	global $bb;
	if ( preg_match( '/^([a-z0-9_-]+)#((?:[a-z0-9\/\\_-]+.)+)(php)$/i', $plugin, $_matches ) ) {
		$plugin_directory = $bb->plugin_locations[$_matches[1]]['dir'] . $_matches[2] . $_matches[3];
		if ( !$path ) {
			$plugin_directory = dirname( $plugin_directory ) . '/';
		}
	} else {
		$plugin_directory = $bb->plugin_locations['core']['dir'];
	}
	return apply_filters( 'bb_get_plugin_directory', $plugin_directory, $plugin, $path );
}

function bb_get_plugin_path( $plugin = false )
{
	$plugin_path = bb_get_plugin_directory( $plugin, true );
	return apply_filters( 'bb_get_plugin_path', $plugin_path, $plugin );
}

/* Themes / Templates */

function bb_get_active_theme_directory()
{
	return apply_filters( 'bb_get_active_theme_directory', bb_get_theme_directory() );
}

function bb_get_theme_directory( $theme = false )
{
	global $bb;
	if ( !$theme ) {
		$theme = bb_get_option( 'bb_active_theme' );
	}
	if ( preg_match( '/^([a-z0-9_-]+)#([\.a-z0-9_-]+)$/i', $theme, $_matches ) ) {
		$theme_directory = $bb->theme_locations[$_matches[1]]['dir'] . $_matches[2] . '/';
	} else {
		$theme_directory = BB_DEFAULT_THEME_DIR;
	}
	return $theme_directory;
}

function bb_get_themes()
{
	$r = array();
	global $bb;
	foreach ( $bb->theme_locations as $_name => $_data ) {
		if ( $themes_dir = @dir( $_data['dir'] ) ) {
			while( ( $theme_dir = $themes_dir->read() ) !== false ) {
				if ( is_file( $_data['dir'] . $theme_dir . '/style.css' ) && is_readable( $_data['dir'] . $theme_dir . '/style.css' ) && '.' != $theme_dir{0} ) {
					$r[$_name . '#' . $theme_dir] = $_name . '#' . $theme_dir;
				}
			}
		}
	}
	ksort( $r );
	return $r;
}

function bb_theme_basename( $file )
{
	global $bb;
	$directories = array();
	foreach ( $bb->theme_locations as $_name => $_data ) {
		$directories[$_name] = $_data['dir'];
	}
	$file = bb_basename( $file, $directories );
	$file = preg_replace( '|/+.*|', '', $file );
	return $file;
}

function bb_register_theme_activation_hook( $file, $function )
{
	$file = bb_theme_basename( $file );
	add_action( 'bb_activate_theme_' . $file, $function );
}

function bb_register_theme_deactivation_hook( $file, $function )
{
	$file = bb_theme_basename( $file );
	add_action( 'bb_deactivate_theme_' . $file, $function );
}

/* Search Functions */
// NOT bbdb::prepared
function bb_user_search( $args = '' ) {
	global $bbdb, $bb_last_countable_query;

	if ( $args && is_string( $args ) && false === strpos( $args, '=' ) ) {
		$args = array( 'query' => $args );
	}

	$defaults = array(
		'query' => '',
		'append_meta' => true,
		'user_login' => true,
		'display_name' => true,
		'user_nicename' => false,
		'user_url' => true,
		'user_email' => false,
		'user_meta' => false,
		'users_per_page' => false,
		'page' => false,
		'roles' => false
	);

	$args = wp_parse_args( $args, $defaults );
	extract( $args, EXTR_SKIP );

	$query = trim( $query );
	if ( $query && strlen( preg_replace( '/[^a-z0-9]/i', '', $query ) ) < 3 ) {
		return new WP_Error( 'invalid-query', __('Your search term was too short') );
	}
	$query = $bbdb->escape( $query );

	if ( !$page ) {
		$page = $GLOBALS['page'];
	}
	$page = (int) $page;

	$limit = 0 < (int) $users_per_page ? (int) $users_per_page : bb_get_option( 'page_topics' );
	if ( 1 < $page ) {
		$limit = ($limit * ($page - 1)) . ", $limit";
	}

	$likeit = preg_replace( '/\s+/', '%', like_escape( $query ) );

	$fields = array();
	foreach ( array( 'user_login', 'display_name', 'user_nicename', 'user_url', 'user_email' ) as $field ) {
		if ( $$field ) {
			$fields[] = $field;
		}
	}

	if ( $roles ) {
		$roles = (array) $roles;
	}

	if ( $roles && !empty( $roles ) && false === $role_user_ids = apply_filters( 'bb_user_search_role_user_ids', false, $roles, $args ) ) {
		$role_meta_key = $bbdb->escape( $bbdb->prefix . 'capabilities' );
		$role_sql_terms = array();
		foreach ( $roles as $role ) {
			$role_sql_terms[] = "`meta_value` LIKE '%" . $bbdb->escape( like_escape( $role ) ) . "%'";
		}
		$role_sql_terms = join( ' OR ', $role_sql_terms );
		$role_sql = "SELECT `user_id` FROM `$bbdb->usermeta` WHERE `meta_key` = '$role_meta_key' AND ($role_sql_terms);";
		$role_user_ids = $bbdb->get_col( $role_sql, 0 );
		if ( is_wp_error( $role_user_ids ) ) {
			return false;
		}
	}

	if ( is_array( $role_user_ids ) && empty( $role_user_ids ) ) {
		return false;
	}

	if ( $query && $user_meta && false === $meta_user_ids = apply_filters( 'bb_user_search_meta_user_ids', false, $args ) ) {
		$meta_sql = "SELECT `user_id` FROM `$bbdb->usermeta` WHERE `meta_value` LIKE ('%$likeit%')";
		if ( empty( $fields ) ) {
			$meta_sql .= " LIMIT $limit";
		}
		$meta_user_ids = $bbdb->get_col( $meta_sql, 0 );
		if ( is_wp_error( $meta_user_ids ) ) {
			$meta_user_ids = false;
		}
	}

	$user_ids = array();
	if ( $role_user_ids && $meta_user_ids ) {
		$user_ids = array_intersect( (array) $role_user_ids, (array) $meta_user_ids );
	} elseif ( $role_user_ids ) {
		$user_ids = (array) $role_user_ids;
	} elseif ( $meta_user_ids ) {
		$user_ids = (array) $meta_user_ids;
	}

	$sql = "SELECT * FROM $bbdb->users";

	$sql_terms = array();
	if ( $query && count( $fields ) ) {
		foreach ( $fields as $field ) {
			$sql_terms[] = "$field LIKE ('%$likeit%')";
		}
	}

	$user_ids_sql = '';
	if ( $user_ids ) {
		$user_ids_sql = "AND ID IN (". join(',', $user_ids) . ")";
	}

	if ( $query && empty( $sql_terms ) ) {
		return new WP_Error( 'invalid-query', __( 'Your query parameters are invalid' ) );
	}

	if ( count( $sql_terms ) || count( $user_ids ) ) {
		$sql .= ' WHERE ';
	}

	if ( count( $sql_terms ) ) {
		$sql .= '(' . implode( ' OR ', $sql_terms ) . ')';
	}

	if ( count( $sql_terms ) && count( $user_ids ) ) {
		$sql .= ' AND ';
	}

	if ( count( $user_ids ) ) {
		$sql .= '`ID` IN (' . join( ',', $user_ids ) . ')';
	}

	$sql .= " ORDER BY user_login LIMIT $limit";

	$bb_last_countable_query = $sql;

	do_action( 'bb_user_search', $sql, $args );

	if ( ( $users = $bbdb->get_results( $sql ) ) && $append_meta ) {
		return bb_append_meta( $users, 'user' );
	}

	return $users ? $users : false;
}

function bb_tag_search( $args = '' ) {
	global $page, $wp_taxonomy_object;

	if ( $args && is_string($args) && false === strpos($args, '=') )
		$args = array( 'search' => $args );

	$defaults = array( 'search' => '', 'number' => false );

	$args = wp_parse_args( $args );
	if ( isset( $args['query'] ) )
		$args['search'] = $args['query'];
	if ( isset( $args['tags_per_page'] ) )
		$args['number'] = $args['tags_per_page'];
	unset($args['query'], $args['tags_per_page']);
	$args = wp_parse_args( $args, $defaults );

	extract( $args, EXTR_SKIP );

	$number = (int) $number;
	$search = trim( $search );
	if ( strlen( $search ) < 3 )
		return new WP_Error( 'invalid-query', __('Your search term was too short') );

	$number = 0 < $number ? $number : bb_get_option( 'page_topics' );
	if ( 1 < $page )
		$offset = ( intval($page) - 1 ) * $number;

	$args = array_merge( $args, compact( 'number', 'offset', 'search' ) );

	$terms = $wp_taxonomy_object->get_terms( 'bb_topic_tag', $args );
	if ( is_wp_error( $terms ) )
		return false;

	for ( $i = 0; isset($terms[$i]); $i++ )
		_bb_make_tag_compat( $terms[$i] );

	return $terms;
}



/* Slugs */

function bb_slug_increment( $slug, $existing_slug, $slug_length = 255 ) {
	if ( preg_match('/^.*-([0-9]+)$/', $existing_slug, $m) )
		$number = (int) $m[1] + 1;
	else
		$number = 1;

	$r = bb_encoded_utf8_cut( $slug, $slug_length - 1 - strlen($number) );
	return apply_filters( 'bb_slug_increment', "$r-$number", $slug, $existing_slug, $slug_length );
}

function bb_get_id_from_slug( $table, $slug, $slug_length = 255 ) {
	global $bbdb;
	$tablename = $table . 's';

	list($_slug, $sql) = bb_get_sql_from_slug( $table, $slug, $slug_length );

	if ( !$_slug || !$sql )
		return 0;

	return (int) $bbdb->get_var( "SELECT ${table}_id FROM {$bbdb->$tablename} WHERE $sql" );
}

function bb_get_sql_from_slug( $table, $slug, $slug_length = 255 ) {
	global $bbdb;

	// Look for new style equiv of old style slug
	$_slug = bb_slug_sanitize( (string) $slug );
	if ( strlen( $_slug ) < 1 )
		return '';

	if ( strlen($_slug) > $slug_length && preg_match('/^.*-([0-9]+)$/', $_slug, $m) ) {
		$_slug = bb_encoded_utf8_cut( $_slug, $slug_length - 1 - strlen($number) );
		$number = (int) $m[1];
		$_slug =  "$_slug-$number";
	}

	return array( $_slug, $bbdb->prepare( "${table}_slug = %s", $_slug ) );
}	



/* Utility */

function bb_flatten_array( $array, $cut_branch = 0, $keep_child_array_keys = true ) {
	if ( !is_array($array) )
		return $array;
	
	if ( empty($array) )
		return null;
	
	$temp = array();
	foreach ( $array as $k => $v ) {
		if ( $cut_branch && $k == $cut_branch )
			continue;
		if ( is_array($v) ) {
			if ( $keep_child_array_keys ) {
				$temp[$k] = true;
			}
			$temp += bb_flatten_array($v, $cut_branch, $keep_child_array_keys);
		} else {
			$temp[$k] = $v;
		}
	}
	return $temp;
}

function bb_get_common_parts($string1 = false, $string2 = false, $delimiter = '', $reverse = false) {
	if (!$string1 || !$string2) {
		return false;
	}
	
	if ($string1 === $string2) {
		return $string1;
	}
	
	$string1_parts = explode( $delimiter, (string) $string1 );
	$string2_parts = explode( $delimiter, (string) $string2 );
	
	if ($reverse) {
		$string1_parts = array_reverse( $string1_parts );
		$string2_parts = array_reverse( $string2_parts );
		ksort( $string1_parts );
		ksort( $string2_parts );
	}
	
	$common_parts = array();
	foreach ( $string1_parts as $index => $part ) {
		if ( isset( $string2_parts[$index] ) && $string2_parts[$index] == $part ) {
			$common_parts[] = $part;
		} else {
			break;
		}
	}
	
	if (!count($common_parts)) {
		return false;
	}
	
	if ($reverse) {
		$common_parts = array_reverse( $common_parts );
	}
	
	return join( $delimiter, $common_parts );
}

function bb_get_common_domains($domain1 = false, $domain2 = false) {
	if (!$domain1 || !$domain2) {
		return false;
	}
	
	$domain1 = strtolower( preg_replace( '@^https?://([^/]+).*$@i', '$1', $domain1 ) );
	$domain2 = strtolower( preg_replace( '@^https?://([^/]+).*$@i', '$1', $domain2 ) );
	
	return bb_get_common_parts( $domain1, $domain2, '.', true );
}

function bb_get_common_paths($path1 = false, $path2 = false) {
	if (!$path1 || !$path2) {
		return false;
	}
	
	$path1 = preg_replace('@^https?://[^/]+(.*)$@i', '$1', $path1);
	$path2 = preg_replace('@^https?://[^/]+(.*)$@i', '$1', $path2);
	
	if ($path1 === $path2) {
		return $path1;
	}
	
	$path1 = trim( $path1, '/' );
	$path2 = trim( $path2, '/' );
	
	$common_path = bb_get_common_parts( $path1, $path2, '/' );
	
	if ($common_path) {
		return '/' . $common_path . '/';
	} else {
		return '/';
	}
}

function bb_match_domains($domain1 = false, $domain2 = false) {
	if (!$domain1 || !$domain2) {
		return false;
	}
	
	$domain1 = strtolower( preg_replace( '@^https?://([^/]+).*$@i', '$1', $domain1 ) );
	$domain2 = strtolower( preg_replace( '@^https?://([^/]+).*$@i', '$1', $domain2 ) );
	
	if ( (string) $domain1 === (string) $domain2 ) {
		return true;
	}
	
	return false;
}

function bb_glob($pattern) {
	// On fail return an empty array so that loops don't explode
	
	if (!$pattern)
		return array();
	
	// May break if pattern contains forward slashes
	$directory = dirname( $pattern );
	
	if (!$directory)
		return array();
	
	if (!file_exists($directory))
		return array();
	
	if (!is_dir($directory))
		return array();
	
	if (!function_exists('glob'))
		return array();
	
	if (!is_callable('glob'))
		return array();
	
	$glob = glob($pattern);
	
	if (!is_array($glob))
		$glob = array();
	
	return $glob;
}
