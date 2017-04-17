<?php

// Load bbPress
require( './bb-load.php' );

// SSL redirect if required
bb_ssl_redirect();

// Don't cache this page at all
nocache_headers();

/** Look for redirection ******************************************************/

// Look for 'redirect_to'
if ( isset( $_REQUEST['redirect_to'] ) && is_string( $_REQUEST['redirect_to'] ) )
	$re = $_REQUEST['redirect_to'];

	// Look for 're'
	if ( empty( $re ) && isset( $_REQUEST['re'] )  && is_string( $_REQUEST['re'] ) )
		$re = $_REQUEST['re'];

		// Use referer
		if ( empty( $re ) )
			$re = wp_get_referer();

			// Don't redirect to register or password reset pages
			if ( empty( $re ) ) {
				// Grab home path and URL for comparison
				$home_url  = parse_url( bb_get_uri( null, null, BB_URI_CONTEXT_TEXT ) );
				$home_path = $home_url['path'];

				if ( false !== strpos( $re, $home_path . 'register.php' ) || false !== strpos( $re, $home_path . 'bb-reset-password.php' ) )
					$re = bb_get_uri( null, null, BB_URI_CONTEXT_HEADER );

			}

/**
 * If this page was accessed using SSL, make sure the redirect is a full URL so
 * that we don't end up on an SSL page again (unless the whole site is under SSL)
 */
if ( is_ssl() && 0 === strpos( $re, '/' ) )
	$re = bb_get_uri( $re , null, BB_URI_CONTEXT_HEADER );

// Clean the redirection destination
if ( !empty( $re ) ) {
	$re = esc_url( $re );
	$re = esc_attr( $re );
	$redirect_to = $re;
}

// Fallback to site root
if ( empty( $re ) )
	$re = bb_get_uri();

/** Handle logout *************************************************************/

// User is logged in
if ( bb_is_user_logged_in() ) {

	// Logout requested
	if ( isset( $_GET['logout'] ) )
		$_GET['action'] = 'logout';

	// Check logout action
	if ( isset( $_GET['action'] ) && 'logout' === $_GET['action'] )
		bb_logout();

	bb_safe_redirect( $re );
	exit;
}

/** Handle login **************************************************************/

// Do we allow login by email address
$email_login = bb_get_option( 'email_login' );

// Get the user from the login details
if ( empty( $_POST['log'] ) )
	$_POST['log'] = !empty( $_POST['user_login'] ) ? $_POST['user_login'] : '';

if ( empty( $_POST['pwd'] ) )
	$_POST['pwd'] = !empty( $_POST['password']   ) ? $_POST['password']   : '';

if ( empty( $_POST['rememberme'] ) )
	$_POST['rememberme'] = !empty( $_POST['remember']   ) ? 1                    : '';

// Attempt to log the user in
if ( $user = bb_login( @$_POST['log'], @$_POST['pwd'], @$_POST['rememberme'] ) ) {
	if ( !is_wp_error( $user ) ) {
		bb_safe_redirect( $re );
		exit;
	} else {
		$bb_login_error =& $user;
	}
	
// No login so prepare the error
} else {
	$bb_login_error = new WP_Error;
}

/** Handle errors *************************************************************/

// Get error data so we can provide feedback
$error_data = $bb_login_error->get_error_data();

// Does user actually exist
if ( isset( $error_data['unique'] ) && false === $error_data['unique'] )
	$user_exists = true;
else
	$user_exists = !empty( $_POST['log'] ) && (bool) bb_get_user( $_POST['log'], array( 'by' => 'login' ) );

// Check for errors on post method
if ( 'post' == strtolower( $_SERVER['REQUEST_METHOD'] ) ) {
	
	// If the user doesn't exist then add that error
	if ( empty( $user_exists ) ) {
		if ( !empty( $_POST['log'] ) ) {
			$bb_login_error->add( 'user_login', __( 'User does not exist.' ) );
		} else {
			$bb_login_error->add( 'user_login', $email_login ? __( 'Enter a username or email address.' ) : __( 'Enter a username.' ) );
		}
	}

	// If the password was wrong then add that error
	if ( !$bb_login_error->get_error_code() ) {
		$bb_login_error->add( 'password', __( 'Incorrect password.' ) );
	}
}

/**
 * If trying to log in with email address, don't leak whether or not email
 * address exists in the db. is_email() is not perfect. Usernames can be
 * valid email addresses potentially.
 */
if ( !empty( $email_login ) && $bb_login_error->get_error_codes() && false !== is_email( @$_POST['log'] ) )
	$bb_login_error = new WP_Error( 'user_login', __( 'Username and Password do not match.' ) );

/** Prepare for display *******************************************************/

// Sanitze variables for display
$remember_checked  = @$_POST['rememberme'] ? ' checked="checked"' : '';
$user_login        = esc_attr( sanitize_user( @$_POST['log'], true ) );

// Load the template
bb_load_template( 'login.php', array( 'user_exists', 'user_login', 'remember_checked', 'redirect_to', 're', 'bb_login_error' ) );

exit;

?>
