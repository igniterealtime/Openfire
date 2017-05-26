<?php

if ( !function_exists( 'bb_auth' ) ) :
function bb_auth( $scheme = 'auth' ) { // Checks if a user has a valid cookie, if not redirects them to the main page
	if ( !bb_validate_auth_cookie( '', $scheme ) ) {
		nocache_headers();
		if ( 'auth' === $scheme && !bb_is_user_logged_in() ) {
			$protocol = 'http://';
			if ( is_ssl() ) {
				$protocol = 'https://';
			}
			wp_redirect( bb_get_uri( 'bb-login.php', array( 'redirect_to' => $protocol . $_SERVER['HTTP_HOST'] . $_SERVER['REQUEST_URI'] ), BB_URI_CONTEXT_HEADER + BB_URI_CONTEXT_BB_USER_FORMS ) );
		} else {
			wp_redirect( bb_get_uri( null, null, BB_URI_CONTEXT_HEADER ) );
		}
		exit;
	}
}
endif;

// $already_md5 variable is deprecated
if ( !function_exists('bb_check_login') ) :
function bb_check_login($user, $pass, $already_md5 = false) {
	global $wp_users_object;

	if ( !bb_get_option( 'email_login' ) || false === strpos( $user, '@' ) ) { // user_login
		$user = $wp_users_object->get_user( $user, array( 'by' => 'login' ) );
	} else { // maybe an email
		$email_user = $wp_users_object->get_user( $user, array( 'by' => 'email' ) );
		$user = $wp_users_object->get_user( $user, array( 'by' => 'login' ) );
		// 9 cases.  each can be FALSE, USER, or WP_ERROR
		if (
			( !$email_user && $user ) // FALSE && USER, FALSE && WP_ERROR
		||
			( is_wp_error( $email_user ) && $user && !is_wp_error( $user ) ) // WP_ERROR && USER
		) {
			// nope: it really was a user_login
			// [sic]: use $user
		} elseif (
			( $email_user && !$user ) // USER && FALSE, WP_ERROR && FALSE
		||
			( $email_user && !is_wp_error( $email_user ) && is_wp_error( $user ) ) // USER && WP_ERROR
		) {
			// yup: it was an email
			$user =& $email_user;
		} elseif ( !$email_user && !$user ) { // FALSE && FALSE
			// Doesn't matter what it was: neither worked
			return false;
		} elseif ( is_wp_error( $email_user ) && is_wp_error( $user ) ) { // WP_ERROR && WP_ERROR
			// This can't happen.  If it does, let's use the email error.  It's probably "multiple matches", so maybe logging in with a username will work
			$user =& $email_user;
		} elseif ( $email_user && $user ) { // USER && USER
			// both are user objects
			if ( $email_user->ID == $user->ID ); // [sic]: they are the same, use $user
			elseif ( bb_check_password($pass, $user->user_pass, $user->ID) ); // [sic]: use $user
			elseif ( bb_check_password($pass, $email_user->user_pass, $email_user->ID) )
				$user =& $email_user;
		} else { // This can't happen, that's all 9 cases.
			// [sic]: use $user
		}
	}

	if ( !$user )
		return false;

	if ( is_wp_error($user) )
		return $user;
	
	if ( !bb_check_password($pass, $user->user_pass, $user->ID) )
		return false;

	// User is logging in for the first time, update their user_status to normal
	if ( 1 == $user->user_status )
		bb_update_user_status( $user->ID, 0 );
	
	return $user;
}
endif;

if ( !function_exists('bb_get_current_user') ) :
function bb_get_current_user() {
	global $wp_auth_object;
	return $wp_auth_object->get_current_user();
}
endif;

if ( !function_exists('bb_set_current_user') ) :
function bb_set_current_user( $id ) {
	global $wp_auth_object;
	$current_user = $wp_auth_object->set_current_user( $id );
	
	do_action('bb_set_current_user', isset($current_user->ID) ? $current_user->ID : 0 );
	
	return $current_user;
}
endif;

if ( !function_exists('bb_current_user') ) :
//This is only used at initialization.  Use bb_get_current_user_info() (or $bb_current_user global if really needed) to grab user info.
function bb_current_user() {
	if (BB_INSTALLING)
		return false;

	return bb_get_current_user();
}
endif;

if ( !function_exists('bb_is_user_authorized') ) :
function bb_is_user_authorized() {
	return bb_is_user_logged_in();
}
endif;

if ( !function_exists('bb_is_user_logged_in') ) :
function bb_is_user_logged_in() {
	$current_user = bb_get_current_user();

	if ( empty($current_user) )
		return false;
	
	return true;
}
endif;

if ( !function_exists('bb_login') ) :
function bb_login( $login, $password, $remember = false ) {
	$user = bb_check_login( $login, $password );
	if ( $user && !is_wp_error( $user ) ) {
		bb_set_auth_cookie( $user->ID, $remember );
		do_action('bb_user_login', (int) $user->ID );
	}
	
	return $user;
}
endif;

if ( !function_exists('bb_logout') ) :
function bb_logout() {
	bb_clear_auth_cookie();
	
	do_action('bb_user_logout');
}
endif;

if ( !function_exists( 'bb_validate_auth_cookie' ) ) :
function bb_validate_auth_cookie( $cookie = '', $scheme = 'auth' ) {
	global $wp_auth_object;
	if ( empty($cookie) && $scheme == 'auth' ) {
		if ( is_ssl() ) {
			$scheme = 'secure_auth';
		} else {
			$scheme = 'auth';
		}
	}
	return $wp_auth_object->validate_auth_cookie( $cookie, $scheme );
}
endif;

if ( !function_exists( 'bb_set_auth_cookie' ) ) :
function bb_set_auth_cookie( $user_id, $remember = false, $schemes = false ) {
	global $wp_auth_object;

	if ( $remember ) {
		$expiration = $expire = time() + 1209600;
	} else {
		$expiration = time() + 172800;
		$expire = 0;
	}

	if ( true === $schemes ) {
		$schemes = array( 'secure_auth', 'logged_in' );
	} elseif ( !is_array( $schemes ) ) {
		$schemes = array();
		if ( force_ssl_login() || force_ssl_admin() ) {
			$schemes[] = 'secure_auth';
		}
		if ( !( force_ssl_login() && force_ssl_admin() ) ) {
			$schemes[] = 'auth';
		}
		$schemes[] = 'logged_in';
	}
	$schemes = array_unique( $schemes );

	foreach ( $schemes as $scheme ) {
		$wp_auth_object->set_auth_cookie( $user_id, $expiration, $expire, $scheme );
	}
}
endif;

if ( !function_exists('bb_clear_auth_cookie') ) :
function bb_clear_auth_cookie() {
	global $bb, $wp_auth_object;
	
	$wp_auth_object->clear_auth_cookie();
	
	// Old cookies
	setcookie($bb->authcookie, ' ', time() - 31536000, $bb->cookiepath, $bb->cookiedomain);
	setcookie($bb->authcookie, ' ', time() - 31536000, $bb->sitecookiepath, $bb->cookiedomain);
	
	// Even older cookies
	setcookie($bb->usercookie, ' ', time() - 31536000, $bb->cookiepath, $bb->cookiedomain);
	setcookie($bb->usercookie, ' ', time() - 31536000, $bb->sitecookiepath, $bb->cookiedomain);
	setcookie($bb->passcookie, ' ', time() - 31536000, $bb->cookiepath, $bb->cookiedomain);
	setcookie($bb->passcookie, ' ', time() - 31536000, $bb->sitecookiepath, $bb->cookiedomain);
}
endif;

if ( !function_exists('wp_redirect') ) : // [WP11537]
/**
 * Redirects to another page, with a workaround for the IIS Set-Cookie bug.
 *
 * @link http://support.microsoft.com/kb/q176113/
 * @since 1.5.1
 * @uses apply_filters() Calls 'wp_redirect' hook on $location and $status.
 *
 * @param string $location The path to redirect to
 * @param int $status Status code to use
 * @return bool False if $location is not set
 */
function wp_redirect($location, $status = 302) {
	global $is_IIS;

	$location = apply_filters('wp_redirect', $location, $status);
	$status = apply_filters('wp_redirect_status', $status, $location);

	if ( !$location ) // allows the wp_redirect filter to cancel a redirect
		return false;

	$location = wp_sanitize_redirect($location);

	if ( $is_IIS ) {
		header("Refresh: 0;url=$location");
	} else {
		if ( php_sapi_name() != 'cgi-fcgi' )
			status_header($status); // This causes problems on IIS and some FastCGI setups
		header("Location: $location");
	}
}
endif;

if ( !function_exists('wp_sanitize_redirect') ) : // [WP11537]
/**
 * Sanitizes a URL for use in a redirect.
 *
 * @since 2.3
 *
 * @return string redirect-sanitized URL
 **/
function wp_sanitize_redirect($location) {
	$location = preg_replace('|[^a-z0-9-~+_.?#=&;,/:%!]|i', '', $location);
	$location = wp_kses_no_null($location);

	// remove %0d and %0a from location
	$strip = array('%0d', '%0a');
	$found = true;
	while($found) {
		$found = false;
		foreach( (array) $strip as $val ) {
			while(strpos($location, $val) !== false) {
				$found = true;
				$location = str_replace($val, '', $location);
			}
		}
	}
	return $location;
}
endif;

if ( !function_exists('bb_safe_redirect') ) : // based on [WP6145] (home is different)
/**
 * Performs a safe (local) redirect, using wp_redirect().
 *
 * Checks whether the $location is using an allowed host, if it has an absolute
 * path. A plugin can therefore set or remove allowed host(s) to or from the
 * list.
 *
 * If the host is not allowed, then the redirect is to the site url
 * instead. This prevents malicious redirects which redirect to another host,
 * but only used in a few places.
 *
 * @uses apply_filters() Calls 'allowed_redirect_hosts' on an array containing
 *		bbPress host string and $location host string.
 *
 * @return void Does not return anything
 **/
function bb_safe_redirect( $location, $status = 302 ) {

	// Need to look at the URL the way it will end up in wp_redirect()
	$location = wp_sanitize_redirect($location);

	// browsers will assume 'http' is your protocol, and will obey a redirect to a URL starting with '//'
	if ( substr($location, 0, 2) == '//' )
		$location = 'http:' . $location;

	// In php 5 parse_url may fail if the URL query part contains http://, bug #38143
	$test = ( $cut = strpos($location, '?') ) ? substr( $location, 0, $cut ) : $location;

	$lp = parse_url($test);
	$bp = parse_url(bb_get_uri());

	$allowed_hosts = (array) apply_filters('allowed_redirect_hosts', array($bp['host']), isset($lp['host']) ? $lp['host'] : '');

	if ( isset($lp['host']) && ( !in_array($lp['host'], $allowed_hosts) && $lp['host'] != strtolower($bp['host'])) )
		$location = bb_get_uri(null, null, BB_URI_CONTEXT_HEADER);

	return wp_redirect($location, $status);
}
endif;

if ( !function_exists('bb_nonce_tick') ) :
/**
 * Get the time-dependent variable for nonce creation.
 *
 * A nonce has a lifespan of two ticks. Nonces in their second tick may be
 * updated, e.g. by autosave.
 *
 * @since 1.0
 *
 * @return int
 */
function bb_nonce_tick() {
	$nonce_life = apply_filters('bb_nonce_life', 86400);

	return ceil(time() / ( $nonce_life / 2 ));
}
endif;

if ( !function_exists('bb_verify_nonce') ) :
/**
 * Verify that correct nonce was used with time limit.
 *
 * The user is given an amount of time to use the token, so therefore, since the
 * UID and $action remain the same, the independent variable is the time.
 *
 * @param string $nonce Nonce that was used in the form to verify
 * @param string|int $action Should give context to what is taking place and be the same when nonce was created.
 * @return bool Whether the nonce check passed or failed.
 */
function bb_verify_nonce($nonce, $action = -1) {
	$user = bb_get_current_user();
	$uid = (int) $user->ID;

	$i = bb_nonce_tick();

	// Nonce generated 0-12 hours ago
	if ( substr(bb_hash($i . $action . $uid, 'nonce'), -12, 10) == $nonce )
		return 1;
	// Nonce generated 12-24 hours ago
	if ( substr(bb_hash(($i - 1) . $action . $uid, 'nonce'), -12, 10) == $nonce )
		return 2;
	// Invalid nonce
	return false;
}
endif;

if ( !function_exists('bb_create_nonce') ) :
/**
 * Creates a random, one time use token.
 *
 * @since 2.0.4
 *
 * @param string|int $action Scalar value to add context to the nonce.
 * @return string The one use form token
 */
function bb_create_nonce($action = -1) {
	$user = bb_get_current_user();
	$uid = (int) $user->ID;

	$i = bb_nonce_tick();
	
	return substr(bb_hash($i . $action . $uid, 'nonce'), -12, 10);
}
endif;

function _bb_get_key( $key, $default_key = false )
{
	global $bb_default_secret_key;

	if ( defined( $key ) && '' != constant( $key ) && $bb_default_secret_key != constant( $key ) ) {
		return constant( $key );
	}

	return '';
}

function _bb_get_salt( $constants, $option = false )
{
	if ( !is_array( $constants ) ) {
		$constants = array( $constants );
	}

	foreach ($constants as $constant ) {
		if ( defined( $constant ) ) {
			return constant( $constant );
		}
	}

	if ( !defined( 'BB_INSTALLING' ) || !BB_INSTALLING ) {
		if ( !$option ) {
			$option = strtolower( $constants[0] );
		}
		$salt = bb_get_option( $option );
		if ( empty( $salt ) ) {
			$salt = bb_generate_password( 64 );
			bb_update_option( $option, $salt );
		}
		return $salt;
	}

	return '';
}

// Not verbatim WP, constants have different names, uses helper functions.
if ( !function_exists( 'bb_salt' ) ) :
/**
 * Get salt to add to hashes to help prevent attacks.
 *
 * @since 0.9
 * @link http://api.wordpress.org/secret-key/1.1/bbpress/ Create a set of keys for bb-config.php
 * @uses _bb_get_key()
 * @uses _bb_get_salt()
 *
 * @return string Salt value for the given scheme
 */
function bb_salt( $scheme = 'auth' )
{
	// Deprecated
	$secret_key = _bb_get_key( 'BB_SECRET_KEY' );

	switch ($scheme) {
		case 'auth':
			$secret_key = _bb_get_key( 'BB_AUTH_KEY' );
			$salt = _bb_get_salt( array( 'BB_AUTH_SALT', 'BB_SECRET_SALT' ) );
			break;

		case 'secure_auth':
			$secret_key = _bb_get_key( 'BB_SECURE_AUTH_KEY' );
			$salt = _bb_get_salt( 'BB_SECURE_AUTH_SALT' );
			break;

		case 'logged_in':
			$secret_key = _bb_get_key( 'BB_LOGGED_IN_KEY' );
			$salt = _bb_get_salt( 'BB_LOGGED_IN_SALT' );
			break;

		case 'nonce':
			$secret_key = _bb_get_key( 'BB_NONCE_KEY' );
			$salt = _bb_get_salt( 'BB_NONCE_SALT' );
			break;

		default:
			// ensure each auth scheme has its own unique salt
			$salt = hash_hmac( 'md5', $scheme, $secret_key );
			break;
	}

	return apply_filters( 'salt', $secret_key . $salt, $scheme );
}
endif;

if ( !function_exists( 'bb_hash' ) ) :
function bb_hash( $data, $scheme = 'auth' ) { 
	$salt = bb_salt( $scheme );

	return hash_hmac( 'md5', $data, $salt );
}
endif;

if ( !function_exists( 'bb_hash_password' ) ) :
function bb_hash_password( $password ) {
	return WP_Pass::hash_password( $password );
}
endif;

if ( !function_exists( 'bb_check_password') ) :
function bb_check_password( $password, $hash, $user_id = '' ) {
	return WP_Pass::check_password( $password, $hash, $user_id );
}
endif;

if ( !function_exists( 'bb_generate_password' ) ) :
/**
 * Generates a random password drawn from the defined set of characters
 * @return string the password
 */
function bb_generate_password( $length = 12, $special_chars = true ) {
	return WP_Pass::generate_password( $length, $special_chars );
}
endif;

if ( !function_exists('bb_check_admin_referer') ) :
function bb_check_admin_referer( $action = -1, $query_arg = '_wpnonce' ) {
	$nonce = '';
	if ( isset( $_POST[$query_arg] ) && $_POST[$query_arg] ) {
		$nonce = $_POST[$query_arg];
	} elseif ( isset( $_GET[$query_arg] ) && $_GET[$query_arg] ) {
		$nonce = $_GET[$query_arg];
	}
	if ( !bb_verify_nonce($nonce, $action) ) {
		bb_nonce_ays($action);
		die();
	}
	do_action('bb_check_admin_referer', $action);
}
endif;

if ( !function_exists('bb_check_ajax_referer') ) :
function bb_check_ajax_referer( $action = -1, $query_arg = false, $die = true ) {
	$requests = array();
	if ( $query_arg ) {
		$requests[] = $query_arg;
	}
	$requests[] = '_ajax_nonce';
	$requests[] = '_wpnonce';

	$nonce = '';
	foreach ( $requests as $request ) {
		if ( isset( $_POST[$request] ) && $_POST[$request] ) {
			$nonce = $_POST[$request];
			break;
		} elseif ( isset( $_GET[$request] ) && $_GET[$request] ) {
			$nonce = $_GET[$request];
			break;
		}
	}

	$result = bb_verify_nonce( $nonce, $action );

	if ( $die && false == $result )
		die('-1');

	do_action('bb_check_ajax_referer', $action, $result);
	return $result;
}
endif;

if ( !function_exists('bb_break_password') ) :
function bb_break_password( $user_id ) {
	global $bbdb;
	$user_id = (int) $user_id;
	if ( !$user = bb_get_user( $user_id ) )
		return false;
	$secret = substr(bb_hash( 'bb_break_password' ), 0, 13);
	if ( false === strpos( $user->user_pass, '---' ) )
		return $bbdb->query( $bbdb->prepare(
			"UPDATE $bbdb->users SET user_pass = CONCAT(user_pass, '---', %s) WHERE ID = %d",
			$secret, $user_id
		) );
	else
		return true;
}
endif;

if ( !function_exists('bb_fix_password') ) :
function bb_fix_password( $user_id ) {
	global $bbdb;
	$user_id = (int) $user_id;
	if ( !$user = bb_get_user( $user_id ) )
		return false;
	if ( false === strpos( $user->user_pass, '---' ) )
		return true;
	else
		return $bbdb->query( $bbdb->prepare(
			"UPDATE $bbdb->users SET user_pass = SUBSTRING_INDEX(user_pass, '---', 1) WHERE ID = %d",
			$user_id
		) );
}
endif;

if ( !function_exists('bb_has_broken_pass') ) :
function bb_has_broken_pass( $user_id = 0 ) {
	global $bb_current_user;
	if ( !$user_id )
		$user =& $bb_current_user->data;
	else
		$user = bb_get_user( $user_id );

	return ( false !== strpos($user->user_pass, '---' ) );
}
endif;

if ( !function_exists('bb_new_user') ) :
function bb_new_user( $user_login, $user_email, $user_url, $user_status = 1 ) {
	global $wp_users_object, $bbdb;

	// is_email check + dns
	if ( !$user_email = is_email( $user_email ) )
		return new WP_Error( 'user_email', __( 'Invalid email address' ), $user_email );

	if ( !$user_login = sanitize_user( $user_login, true ) )
		return new WP_Error( 'user_login', __( 'Invalid username' ), $user_login );
	
	// user_status = 1 means the user has not yet been verified
	$user_status = is_numeric($user_status) ? (int) $user_status : 1;
	if ( defined( 'BB_INSTALLING' ) )
		$user_status = 0;
	
	$user_nicename = $_user_nicename = bb_user_nicename_sanitize( $user_login );
	if ( strlen( $_user_nicename ) < 1 )
		return new WP_Error( 'user_login', __( 'Invalid username' ), $user_login );

	while ( is_numeric($user_nicename) || $existing_user = bb_get_user_by_nicename( $user_nicename ) )
		$user_nicename = bb_slug_increment($_user_nicename, $existing_user->user_nicename, 50);
	
	$user_url = $user_url ? bb_fix_link( $user_url ) : '';

	$user_pass = bb_generate_password();

	$user = $wp_users_object->new_user( compact( 'user_login', 'user_email', 'user_url', 'user_nicename', 'user_status', 'user_pass' ) );
	if ( is_wp_error($user) ) {
		if ( 'user_nicename' == $user->get_error_code() )
			return new WP_Error( 'user_login', $user->get_error_message() );
		return $user;
	}

	if (BB_INSTALLING) {
		bb_update_usermeta( $user['ID'], $bbdb->prefix . 'capabilities', array('keymaster' => true) );
	} else {
		bb_update_usermeta( $user['ID'], $bbdb->prefix . 'capabilities', array('member' => true) );
		bb_send_pass( $user['ID'], $user['plain_pass'] );
	}

	do_action('bb_new_user', $user['ID'], $user['plain_pass']);
	return $user['ID'];
}
endif;

if ( !function_exists( 'bb_mail' ) ) :
/**
 * Send mail, similar to PHP's mail
 *
 * A true return value does not automatically mean that the user received the
 * email successfully. It just only means that the method used was able to
 * process the request without any errors.
 *
 * Using the two 'bb_mail_from' and 'bb_mail_from_name' hooks allow from
 * creating a from address like 'Name <email@address.com>' when both are set. If
 * just 'bb_mail_from' is set, then just the email address will be used with no
 * name.
 *
 * The default content type is 'text/plain' which does not allow using HTML.
 * However, you can set the content type of the email by using the
 * 'bb_mail_content_type' filter.
 *
 * The default charset is based on the charset used on the blog. The charset can
 * be set using the 'bb_mail_charset' filter.
 *
 * @uses apply_filters() Calls 'bb_mail' hook on an array of all of the parameters.
 * @uses apply_filters() Calls 'bb_mail_from' hook to get the from email address.
 * @uses apply_filters() Calls 'bb_mail_from_name' hook to get the from address name.
 * @uses apply_filters() Calls 'bb_mail_content_type' hook to get the email content type.
 * @uses apply_filters() Calls 'bb_mail_charset' hook to get the email charset
 * @uses do_action_ref_array() Calls 'bb_phpmailer_init' hook on the reference to
 *		phpmailer object.
 * @uses PHPMailer
 *
 * @param string $to Email address to send message
 * @param string $subject Email subject
 * @param string $message Message contents
 * @param string|array $headers Optional. Additional headers.
 * @param string|array $attachments Optional. Files to attach.
 * @return bool Whether the email contents were sent successfully.
 */
function bb_mail( $to, $subject, $message, $headers = '', $attachments = array() ) {
	// Compact the input, apply the filters, and extract them back out
	extract( apply_filters( 'bb_mail', compact( 'to', 'subject', 'message', 'headers', 'attachments' ) ) );

	if ( !is_array($attachments) )
		$attachments = explode( "\n", $attachments );

	global $bb_phpmailer;

	// (Re)create it, if it's gone missing
	if ( !is_object( $bb_phpmailer ) || !is_a( $bb_phpmailer, 'PHPMailer' ) ) {
		require_once BACKPRESS_PATH . 'class.mailer.php';
		require_once BACKPRESS_PATH . 'class.mailer-smtp.php';
		$bb_phpmailer = new PHPMailer();
	}

	// Headers
	if ( empty( $headers ) ) {
		$headers = array();
	} else {
		if ( !is_array( $headers ) ) {
			// Explode the headers out, so this function can take both
			// string headers and an array of headers.
			$tempheaders = (array) explode( "\n", $headers );
		} else {
			$tempheaders = $headers;
		}
		$headers = array();

		// If it's actually got contents
		if ( !empty( $tempheaders ) ) {
			// Iterate through the raw headers
			foreach ( (array) $tempheaders as $header ) {
				if ( strpos($header, ':') === false ) {
					if ( false !== stripos( $header, 'boundary=' ) ) {
						$parts = preg_split('/boundary=/i', trim( $header ) );
						$boundary = trim( str_replace( array( "'", '"' ), '', $parts[1] ) );
					}
					continue;
				}
				// Explode them out
				list( $name, $content ) = explode( ':', trim( $header ), 2 );

				// Cleanup crew
				$name = trim( $name );
				$content = trim( $content );

				// Mainly for legacy -- process a From: header if it's there
				if ( 'from' == strtolower($name) ) {
					if ( strpos($content, '<' ) !== false ) {
						// So... making my life hard again?
						$from_name = substr( $content, 0, strpos( $content, '<' ) - 1 );
						$from_name = str_replace( '"', '', $from_name );
						$from_name = trim( $from_name );

						$from_email = substr( $content, strpos( $content, '<' ) + 1 );
						$from_email = str_replace( '>', '', $from_email );
						$from_email = trim( $from_email );
					} else {
						$from_email = trim( $content );
					}
				} elseif ( 'content-type' == strtolower($name) ) {
					if ( strpos( $content,';' ) !== false ) {
						list( $type, $charset ) = explode( ';', $content );
						$content_type = trim( $type );
						if ( false !== stripos( $charset, 'charset=' ) ) {
							$charset = trim( str_replace( array( 'charset=', '"' ), '', $charset ) );
						} elseif ( false !== stripos( $charset, 'boundary=' ) ) {
							$boundary = trim( str_replace( array( 'BOUNDARY=', 'boundary=', '"' ), '', $charset ) );
							$charset = '';
						}
					} else {
						$content_type = trim( $content );
					}
				} elseif ( 'cc' == strtolower($name) ) {
					$cc = explode(",", $content);
				} elseif ( 'bcc' == strtolower($name) ) {
					$bcc = explode(",", $content);
				} else {
					// Add it to our grand headers array
					$headers[trim( $name )] = trim( $content );
				}
			}
		}
	}

	// Empty out the values that may be set
	$bb_phpmailer->ClearAddresses();
	$bb_phpmailer->ClearAllRecipients();
	$bb_phpmailer->ClearAttachments();
	$bb_phpmailer->ClearBCCs();
	$bb_phpmailer->ClearCCs();
	$bb_phpmailer->ClearCustomHeaders();
	$bb_phpmailer->ClearReplyTos();

	// From email and name
	// If we don't have a name from the input headers
	if ( !isset( $from_name ) ) {
		$from_name = bb_get_option('name');
	}

	// If we don't have an email from the input headers
	if ( !isset( $from_email ) ) {
		$from_email = bb_get_option('from_email');
	}

	// If there is still no email address
	if ( !$from_email ) {
		// Get the site domain and get rid of www.
		$sitename = strtolower( $_SERVER['SERVER_NAME'] );
		if ( substr( $sitename, 0, 4 ) == 'www.' ) {
			$sitename = substr( $sitename, 4 );
		}

		$from_email = 'bbpress@' . $sitename;
	}

	// Plugin authors can override the potentially troublesome default
	$bb_phpmailer->From = apply_filters( 'bb_mail_from', $from_email );
	$bb_phpmailer->FromName = apply_filters( 'bb_mail_from_name', $from_name );

	// Set destination address
	$bb_phpmailer->AddAddress( $to );

	// Set mail's subject and body
	$bb_phpmailer->Subject = $subject;
	$bb_phpmailer->Body = $message;

	// Add any CC and BCC recipients
	if ( !empty($cc) ) {
		foreach ( (array) $cc as $recipient ) {
			$bb_phpmailer->AddCc( trim($recipient) );
		}
	}
	if ( !empty($bcc) ) {
		foreach ( (array) $bcc as $recipient) {
			$bb_phpmailer->AddBcc( trim($recipient) );
		}
	}

	// Set to use PHP's mail()
	$bb_phpmailer->IsMail();

	// Set Content-Type and charset
	// If we don't have a content-type from the input headers
	if ( !isset( $content_type ) ) {
		$content_type = 'text/plain';
	}

	$content_type = apply_filters( 'bb_mail_content_type', $content_type );

	$bb_phpmailer->ContentType = $content_type;

	// Set whether it's plaintext or not, depending on $content_type
	if ( $content_type == 'text/html' ) {
		$bb_phpmailer->IsHTML( true );
	}

	// If we don't have a charset from the input headers
	if ( !isset( $charset ) ) {
		$charset = bb_get_option( 'charset' );
	}

	// Set the content-type and charset
	$bb_phpmailer->CharSet = apply_filters( 'bb_mail_charset', $charset );

	// Set custom headers
	if ( !empty( $headers ) ) {
		foreach( (array) $headers as $name => $content ) {
			$bb_phpmailer->AddCustomHeader( sprintf( '%1$s: %2$s', $name, $content ) );
		}
		if ( false !== stripos( $content_type, 'multipart' ) && ! empty($boundary) ) {
			$bb_phpmailer->AddCustomHeader( sprintf( "Content-Type: %s;\n\t boundary=\"%s\"", $content_type, $boundary ) );
		}
	}

	if ( !empty( $attachments ) ) {
		foreach ( $attachments as $attachment ) {
			$bb_phpmailer->AddAttachment($attachment);
		}
	}

	do_action_ref_array( 'bb_phpmailer_init', array( &$bb_phpmailer ) );

	// Send!
	$result = @$bb_phpmailer->Send();

	return $result;
}
endif;

if ( !function_exists( 'bb_get_avatar' ) ) :
/**
 * Retrieve the avatar for a user provided a user ID or email address
 *
 * @since 0.9
 * @param int|string $id_or_email A user ID or email address
 * @param int $size Size of the avatar image
 * @param string $default URL to a default image to use if no avatar is available
 * @param string $alt Alternate text to use in image tag. Defaults to blank
 * @return string <img> tag for the user's avatar
*/
function bb_get_avatar( $id_or_email, $size = 80, $default = '', $alt = false ) {
	if ( !bb_get_option('avatars_show') )
		return false;

	if ( false === $alt)
		$safe_alt = '';
	else
		$safe_alt = esc_attr( $alt );

	if ( !is_numeric($size) )
		$size = 80;

	if ( $email = bb_get_user_email($id_or_email) ) {
		$class = 'photo ';
	} else {
		$class = '';
		$email = $id_or_email;
	}

	if ( !$email )
		$email = '';

	if ( empty($default) )
		$default = bb_get_option('avatars_default');

 	if ( is_ssl() )
		$host = 'https://secure.gravatar.com';
	else
		$host = 'http://www.gravatar.com';

	switch ($default) {
		case 'logo':
			$default = '';
			break;
		case 'blank':
			$default = bb_get_uri( 'bb-admin/images/blank.gif', null, BB_URI_CONTEXT_IMG_SRC );
			break;
		case 'monsterid':
		case 'wavatar':
		case 'identicon':
		case 'retro':
			break;
		case 'default':
		default:
			$default = $host . '/avatar/ad516503a11cd5ca435acc9bb6523536?s=' . $size;
			// ad516503a11cd5ca435acc9bb6523536 == md5('unknown@gravatar.com')
			break;
	}

	$src = $host . '/avatar/';
	$class .= 'avatar avatar-' . $size;

	if ( !empty($email) ) {
		$src .= md5( strtolower( $email ) );
	} else {
		$src .= 'd41d8cd98f00b204e9800998ecf8427e';
		// d41d8cd98f00b204e9800998ecf8427e == md5('')
		$class .= ' avatar-noemail';
	}

	$src .= '?s=' . $size;
	$src .= '&amp;d=' . urlencode( $default );

	$rating = bb_get_option('avatars_rating');
	if ( !empty( $rating ) )
		$src .= '&amp;r=' . $rating;

	$avatar = '<img alt="' . $safe_alt . '" src="' . $src . '" class="' . $class . '" style="height:' . $size . 'px; width:' . $size . 'px;" />';

	return apply_filters('bb_get_avatar', $avatar, $id_or_email, $size, $default, $alt);
}
endif;
