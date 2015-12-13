<?php
class WP_Auth
{
	var $db; // BBPDB object
	var $users; // WP_Users object

	var $cookies;

	var $current = 0;

	function WP_Auth( &$db, &$users, $cookies )
	{
		$this->__construct( $db, $users, $cookies );
	}

	/**
	 * @param array $cookies Array indexed by internal name of cookie.  Values are arrays of array defining cookie parameters.
	 * $cookies = array(
	 *	'auth' => array(
	 *		0 => array(
	 *			'domain' => (string) cookie domain
	 *			'path' => (string) cookie path
	 *			'name' => (string) cookie name
	 *			'secure' => (bool) restrict cookie to HTTPS only
	 *		)
	 *	)
	 * );
	 *
	 * At least one cookie is required.  Give it an internal name of 'auth'.
	 *
	 * Suggested cookie structure:
	 * 	logged_in: whether or not a user is logged in.  Send everywhere.
	 *	auth: used to authorize user's actions.  Send only to domains/paths that need it (e.g. wp-admin/)
	 *	secure_auth: used to authorize user's actions.  Send only to domains/paths that need it and only over HTTPS
	 *
	 * You should be very careful when setting cookie domain to ensure that it always follows the rules set out in
	 * the {@link http://curl.haxx.se/rfc/cookie_spec.html Set Cookie spec}.  In most cases it is best to leave cookie
	 * set to false and allow for user configuration to define a cookie domain in a configuration file when
	 * cross subdomain cookies are required.
	 * 
	 * @link 
	 */
	function __construct( &$db, &$users, $cookies )
	{
		$this->db =& $db;
		$this->users =& $users;
		
		$cookies = wp_parse_args( $cookies, array( 'auth' => null ) );
		$_cookies = array();
		foreach ( $cookies as $_scheme => $_scheme_cookies ) {
			foreach ( $_scheme_cookies as $_scheme_cookie ) {
				$_cookies[$_scheme][] = wp_parse_args( $_scheme_cookie, array( 'domain' => null, 'path' => null, 'name' => '' ) );
			}
			unset( $_scheme_cookie );
		}
		unset( $_scheme, $_scheme_cookies );
		$this->cookies = $_cookies;
		unset( $_cookies );
	}

	/**
	 * Changes the current user by ID or name
	 *
	 * Set $id to null and specify a name if you do not know a user's ID
	 *
	 * Some WordPress functionality is based on the current user and
	 * not based on the signed in user. Therefore, it opens the ability
	 * to edit and perform actions on users who aren't signed in.
	 *
	 * @since 2.0.4
	 * @uses do_action() Calls 'set_current_user' hook after setting the current user.
	 *
	 * @param int $id User ID
	 * @param string $name User's username
	 * @return BP_User Current user User object
	 */
	function set_current_user( $user_id )
	{
		$user = $this->users->get_user( $user_id );
		if ( !$user || is_wp_error( $user ) ) {
			$this->current = 0;
			return $this->current;
		}

		$user_id = $user->ID;

		if ( isset( $this->current->ID ) && $user_id == $this->current->ID ) {
			return $this->current;
		}

		if ( class_exists( 'BP_User' ) ) {
			$this->current = new BP_User( $user_id );
		} else {
			$this->current =& $user;
		}

		// WP add_action( 'set_current_user', 'setup_userdata', 1 );

		do_action( 'set_current_user', $user_id );

		return $this->current;
	}

	/**
	 * Populate variables with information about the currently logged in user
	 *
	 * Will set the current user, if the current user is not set. The current
	 * user will be set to the logged in person. If no user is logged in, then
	 * it will set the current user to 0, which is invalid and won't have any
	 * permissions.
 	 *
	 * @since 0.71
 	 * @uses $current_user Checks if the current user is set
 	 * @uses wp_validate_auth_cookie() Retrieves current logged in user.
 	 *
 	 * @return bool|null False on XMLRPC Request and invalid auth cookie. Null when current user set
 	 */
	function get_current_user()
	{
		if ( !empty( $this->current ) ) {
			return $this->current;
		}

		if ( defined( 'XMLRPC_REQUEST' ) && XMLRPC_REQUEST ) {
			$this->set_current_user( 0 );
		} elseif ( $user_id = $this->validate_auth_cookie( null, 'logged_in' ) ) {
			$this->set_current_user( $user_id );
		} else {
			$this->set_current_user( 0 );
		}

		return $this->current;
	}

	/**
	 * Validates authentication cookie
	 *
	 * The checks include making sure that the authentication cookie
	 * is set and pulling in the contents (if $cookie is not used).
	 *
	 * Makes sure the cookie is not expired. Verifies the hash in
	 * cookie is what is should be and compares the two.
	 *
	 * @since 2.5
	 *
	 * @param string $cookie Optional. If used, will validate contents instead of cookie's
	 * @return bool|int False if invalid cookie, User ID if valid.
	 */
	function validate_auth_cookie( $cookie = null, $scheme = 'auth' )
	{
		if ( empty( $cookie ) ) {
			foreach ( $this->cookies[$scheme] as $_scheme_cookie ) {
				// Take the first cookie of type scheme that exists
				if ( !empty( $_COOKIE[$_scheme_cookie['name']] ) ) {
					$cookie = $_COOKIE[$_scheme_cookie['name']];
					break;
				}
			}
		}
		
		if ( !$cookie ) {
			return false;
		}

		$cookie_elements = explode( '|', $cookie );
		if ( count( $cookie_elements ) != 3 ) {
			do_action( 'auth_cookie_malformed', $cookie, $scheme );
			return false;
		}

		list( $username, $expiration, $hmac ) = $cookie_elements;

		$expired = $expiration;

		// Allow a grace period for POST and AJAX requests
		if ( defined( 'DOING_AJAX' ) || 'POST' == $_SERVER['REQUEST_METHOD'] ) {
			$expired += 3600;
		}

		if ( $expired < time() ) {
			do_action( 'auth_cookie_expired', $cookie_elements );
			return false;
		}

		$user = $this->users->get_user( $username, array( 'by' => 'login' ) );
		if ( !$user || is_wp_error( $user ) ) {
			do_action( 'auth_cookie_bad_username', $cookie_elements );
			return $user;
		}

		$pass_frag = '';
		if ( 1 < WP_AUTH_COOKIE_VERSION ) {
			$pass_frag = substr( $user->user_pass, 8, 4 );
		}

		$key  = call_user_func( backpress_get_option( 'hash_function_name' ), $username . $pass_frag . '|' . $expiration, $scheme );
		$hash = hash_hmac( 'md5', $username . '|' . $expiration, $key );
	
		if ( $hmac != $hash ) {
			do_action( 'auth_cookie_bad_hash', $cookie_elements );
			return false;
		}

		do_action( 'auth_cookie_valid', $cookie_elements, $user );

		return $user->ID;
	}

	/**
	 * Generate authentication cookie contents
	 *
	 * @since 2.5
	 * @uses apply_filters() Calls 'auth_cookie' hook on $cookie contents, User ID
	 *		and expiration of cookie.
	 *
	 * @param int $user_id User ID
	 * @param int $expiration Cookie expiration in seconds
	 * @return string Authentication cookie contents
	 */
	function generate_auth_cookie( $user_id, $expiration, $scheme = 'auth' )
	{
		$user = $this->users->get_user( $user_id );
		if ( !$user || is_wp_error( $user ) ) {
			return $user;
		}

		$pass_frag = '';
		if ( 1 < WP_AUTH_COOKIE_VERSION ) {
			$pass_frag = substr( $user->user_pass, 8, 4 );
		}

		$key  = call_user_func( backpress_get_option( 'hash_function_name' ), $user->user_login . $pass_frag . '|' . $expiration, $scheme );
		$hash = hash_hmac('md5', $user->user_login . '|' . $expiration, $key);

		$cookie = $user->user_login . '|' . $expiration . '|' . $hash;

		return apply_filters( 'auth_cookie', $cookie, $user_id, $expiration, $scheme );
	}

	/**
	 * Sets the authentication cookies based User ID
	 *
	 * The $remember parameter increases the time that the cookie will
	 * be kept. The default the cookie is kept without remembering is
	 * two days. When $remember is set, the cookies will be kept for
	 * 14 days or two weeks.
	 *
	 * @since 2.5
	 *
	 * @param int $user_id User ID
	 * @param int $expiration the UNIX time after which the cookie's authentication token is no longer valid
	 * @param int $expire the UNIX time at which the cookie expires
	 * @param int $scheme name of the 
	 */
	function set_auth_cookie( $user_id, $expiration = 0, $expire = 0, $scheme = 'auth' )
	{
		if ( !isset( $this->cookies[$scheme] ) ) {
			return;
		}

		if ( !$expiration = absint( $expiration ) ) {
			$expiration = time() + 172800; // 2 days
		}

		$expire = absint( $expire );

		foreach ( $this->cookies[$scheme] as $_cookie ) {
			$cookie = $this->generate_auth_cookie( $user_id, $expiration, $scheme );
			if ( is_wp_error( $cookie ) ) {
				return $cookie;
			}

			do_action( 'set_' . $scheme . '_cookie', $cookie, $expire, $expiration, $user_id, $scheme );

			$domain = $_cookie['domain'];
			$secure = isset($_cookie['secure']) ? (bool) $_cookie['secure'] : false;

			// Set httponly if the php version is >= 5.2.0
			if ( version_compare( phpversion(), '5.2.0', 'ge' ) ) {
				backpress_set_cookie( $_cookie['name'], $cookie, $expire, $_cookie['path'], $domain, $secure, true );
			} else {
				$domain = ( empty( $domain ) ) ? $domain : $domain . '; HttpOnly';
				backpress_set_cookie( $_cookie['name'], $cookie, $expire, $_cookie['path'], $domain, $secure );
			}
		}
		unset( $_cookie );
	}

	/**
	 * Deletes all of the cookies associated with authentication
	 *
	 * @since 2.5
	 */
	function clear_auth_cookie()
	{
		do_action( 'clear_auth_cookie' );
		foreach ( $this->cookies as $_scheme => $_scheme_cookies ) {
			foreach ( $_scheme_cookies as $_cookie ) {
				backpress_set_cookie( $_cookie['name'], ' ', time() - 31536000, $_cookie['path'], $_cookie['domain'] );
			}
			unset( $_cookie );
		}
		unset( $_scheme, $_scheme_cookies );
	}
}
