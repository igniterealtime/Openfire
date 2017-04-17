<?php

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * Description of RTMediaFriends
 *
 * @author saurabh
 */
class RTMediaFriends {

	/**
	 *
	 */
	function __construct() {
		if ( ! class_exists( 'BuddyPress' ) ) {
			return;
		}
		if ( ! bp_is_active( 'friend' ) ) {
			return;
		}
	}

	function get_friends_cache( $user ) {

		if ( ! class_exists( 'BuddyPress' ) ) {
			return array();
		}
		if ( ! bp_is_active( 'friends' ) ) {
			return array();
		}

		if ( ! $user ) {
			return array();
		}

		$friends = wp_cache_get( 'rtmedia-user-friends-' . $user );
		if ( empty( $friends ) ) {
			$friends = self::refresh_friends_cache( $user );
		}

		return $friends;
	}

	static function refresh_friends_cache( $user ) {
		if ( ! class_exists( 'BuddyPress' ) ) {
			return;
		}
		if ( ! bp_is_active( 'friends' ) ) {
			return;
		}

		$friends = friends_get_friend_user_ids( $user );
		wp_cache_set( 'rtmedia-user-friends-' . $user, $friends );

		return $friends;
	}

}