<?php
// Last sync [WP10712] - Refactored into a class from wp-incldues/pluggable.php

class WP_Pass {
	/**
	 * Create a hash (encrypt) of a plain text password.
	 *
	 * For integration with other applications, this function can be overwritten to
	 * instead use the other package password checking algorithm.
	 *
	 * @since WP 2.5
	 * @global object $wp_hasher PHPass object
	 * @uses PasswordHash::HashPassword
	 *
	 * @param string $password Plain text user password to hash
	 * @return string The hash string of the password
	 */
	function hash_password($password) {
		global $wp_hasher;

		if ( empty($wp_hasher) ) {
			require_once( BACKPRESS_PATH . 'class.passwordhash.php');
			// By default, use the portable hash from phpass
			$wp_hasher = new PasswordHash(8, TRUE);
		}

		return $wp_hasher->HashPassword($password);
	}

	/**
	 * Checks the plaintext password against the encrypted Password.
	 *
	 * Maintains compatibility between old version and the new cookie authentication
	 * protocol using PHPass library. The $hash parameter is the encrypted password
	 * and the function compares the plain text password when encypted similarly
	 * against the already encrypted password to see if they match.
	 *
	 * For integration with other applications, this function can be overwritten to
	 * instead use the other package password checking algorithm.
	 *
	 * @since WP 2.5
	 * @global object $wp_hasher PHPass object used for checking the password
	 *	against the $hash + $password
	 * @uses PasswordHash::CheckPassword
	 *
	 * @param string $password Plaintext user's password
	 * @param string $hash Hash of the user's password to check against.
	 * @return bool False, if the $password does not match the hashed password
	 */
	function check_password($password, $hash, $user_id = '') {
		global $wp_hasher, $wp_users_object;

		list($hash, $broken) = array_pad( explode( '---', $hash ), 2, '' );

		// If the hash is still md5...
		if ( strlen($hash) <= 32 ) {
			$check = ( $hash == md5($password) );
			if ( $check && $user_id && !$broken ) {
				// Rehash using new hash.
				$wp_users_object->set_password($password, $user_id);
				$hash = WP_Pass::hash_password($password);
			}

			return apply_filters('check_password', $check, $password, $hash, $user_id);
		}

		// If the stored hash is longer than an MD5, presume the
		// new style phpass portable hash.
		if ( empty($wp_hasher) ) {
			require_once( BACKPRESS_PATH . 'class.passwordhash.php');
			// By default, use the portable hash from phpass
			$wp_hasher = new PasswordHash(8, TRUE);
		}

		$check = $wp_hasher->CheckPassword($password, $hash);

		return apply_filters('check_password', $check, $password, $hash, $user_id);
	}

	/**
	 * Generates a random password drawn from the defined set of characters
	 *
	 * @since WP 2.5
	 *
	 * @param int $length The length of password to generate
	 * @param bool $special_chars Whether to include standard special characters 
	 * @return string The random password
	 */
	function generate_password($length = 12, $special_chars = true) {
		$chars = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
		if ( $special_chars )
			$chars .= '!@#$%^&*()';

		$password = '';
		for ( $i = 0; $i < $length; $i++ )
			$password .= substr($chars, WP_Pass::rand(0, strlen($chars) - 1), 1);
		return $password;
	}

	/**
	 * Generates a random number
	 *
	 * Not verbatim WordPress, keeps seed value in backpress options.
	 *
	 * @since WP 2.6.2
	 *
	 * @param int $min Lower limit for the generated number (optional, default is 0)
	 * @param int $max Upper limit for the generated number (optional, default is 4294967295)
	 * @return int A random number between min and max
	 */
	function rand( $min = 0, $max = 0 ) {
		global $rnd_value;

		$seed = backpress_get_transient('random_seed');

		// Reset $rnd_value after 14 uses
		// 32(md5) + 40(sha1) + 40(sha1) / 8 = 14 random numbers from $rnd_value
		if ( strlen($rnd_value) < 8 ) {
			$rnd_value = md5( uniqid(microtime() . mt_rand(), true ) . $seed );
			$rnd_value .= sha1($rnd_value);
			$rnd_value .= sha1($rnd_value . $seed);
			$seed = md5($seed . $rnd_value);
			backpress_set_transient('random_seed', $seed);
		}

		// Take the first 8 digits for our value
		$value = substr($rnd_value, 0, 8);

		// Strip the first eight, leaving the remainder for the next call to wp_rand().
		$rnd_value = substr($rnd_value, 8);

		$value = abs(hexdec($value));

		// Reduce the value to be within the min - max range
		// 4294967295 = 0xffffffff = max random number
		if ( $max != 0 )
			$value = $min + (($max - $min + 1) * ($value / (4294967295 + 1)));

		return abs(intval($value));
	}
}
