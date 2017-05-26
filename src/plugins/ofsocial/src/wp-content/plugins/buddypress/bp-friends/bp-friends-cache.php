<?php
/**
 * BuddyPress Friends Caching.
 *
 * Caching functions handle the clearing of cached objects and pages on specific
 * actions throughout BuddyPress.
 *
 * @package BuddyPress
 * @subpackage FriendsCaching
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Clear friends-related cache for members of a specific friendship.
 *
 * @param int $friendship_id ID of the friendship whose two members should
 *                           have their friends cache busted.
 * @return bool
 */
function friends_clear_friend_object_cache( $friendship_id ) {
	if ( !$friendship = new BP_Friends_Friendship( $friendship_id ) )
		return false;

	wp_cache_delete( 'friends_friend_ids_' .    $friendship->initiator_user_id, 'bp' );
	wp_cache_delete( 'friends_friend_ids_' .    $friendship->friend_user_id,    'bp' );
}

// List actions to clear object caches on.
add_action( 'friends_friendship_accepted', 'friends_clear_friend_object_cache' );
add_action( 'friends_friendship_deleted',  'friends_clear_friend_object_cache' );

/**
 * Clear the friend request cache for the user not initiating the friendship.
 *
 * @since 2.0.0
 *
 * @param int $friend_user_id The user ID not initiating the friendship.
 */
function bp_friends_clear_request_cache( $friend_user_id ) {
	wp_cache_delete( $friend_user_id, 'bp_friends_requests' );
}

/**
 * Clear the friend request cache when a friendship is saved.
 *
 * A friendship is deemed saved when a friendship is requested or accepted.
 *
 * @since 2.0.0
 *
 * @param int $friendship_id     The friendship ID.
 * @param int $initiator_user_id The user ID initiating the friendship.
 * @param int $friend_user_id    The user ID not initiating the friendship.
 */
function bp_friends_clear_request_cache_on_save( $friendship_id, $initiator_user_id, $friend_user_id ) {
	bp_friends_clear_request_cache( $friend_user_id );
}
add_action( 'friends_friendship_requested', 'bp_friends_clear_request_cache_on_save', 10, 3 );
add_action( 'friends_friendship_accepted',  'bp_friends_clear_request_cache_on_save', 10, 3 );

/**
 * Clear the friend request cache when a friendship is removed.
 *
 * A friendship is deemed removed when a friendship is withdrawn or rejected.
 *
 * @since 2.0.0
 *
 * @param int                   $friendship_id The friendship ID.
 * @param BP_Friends_Friendship $friendship Friendship object.
 */
function bp_friends_clear_request_cache_on_remove( $friendship_id, BP_Friends_Friendship $friendship ) {
	bp_friends_clear_request_cache( $friendship->friend_user_id );
}
add_action( 'friends_friendship_withdrawn', 'bp_friends_clear_request_cache_on_remove', 10, 2 );
add_action( 'friends_friendship_rejected',  'bp_friends_clear_request_cache_on_remove', 10, 2 );

// List actions to clear super cached pages on, if super cache is installed.
add_action( 'friends_friendship_rejected',  'bp_core_clear_cache' );
add_action( 'friends_friendship_accepted',  'bp_core_clear_cache' );
add_action( 'friends_friendship_deleted',   'bp_core_clear_cache' );
add_action( 'friends_friendship_requested', 'bp_core_clear_cache' );
