<?php
/**
 * BuddyPress Friends Classes.
 *
 * @package BuddyPress
 * @subpackage FriendsClasses
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * BuddyPress Friendship object.
 */
class BP_Friends_Friendship {

	/**
	 * ID of the friendship.
	 *
	 * @var int
	 */
	public $id;

	/**
	 * User ID of the friendship initiator.
	 *
	 * @var int
	 */
	public $initiator_user_id;

	/**
	 * User ID of the 'friend' - the one invited to the friendship.
	 *
	 * @var int
	 */
	public $friend_user_id;

	/**
	 * Has the friendship been confirmed/accepted?
	 *
	 * @var int
	 */
	public $is_confirmed;

	/**
	 * Is this a "limited" friendship?
	 *
	 * Not currently used by BuddyPress.
	 *
	 * @var int
	 */
	public $is_limited;

	/**
	 * Date the friendship was created.
	 *
	 * @var string
	 */
	public $date_created;

	/**
	 * Is this a request?
	 *
	 * Not currently used in BuddyPress.
	 *
	 * @var bool
	 */
	public $is_request;

	/**
	 * Should additional friend details be queried?
	 *
	 * @var bool
	 */
	public $populate_friend_details;

	/**
	 * Details about the friend.
	 *
	 * @var BP_Core_User
	 */
	public $friend;

	/**
	 * Constructor method.
	 *
	 * @param int  $id                      Optional. The ID of an existing friendship.
	 * @param bool $is_request              Deprecated.
	 * @param bool $populate_friend_details True if friend details should be queried.
	 */
	public function __construct( $id = null, $is_request = false, $populate_friend_details = true ) {
		$this->is_request = $is_request;

		if ( !empty( $id ) ) {
			$this->id                      = $id;
			$this->populate_friend_details = $populate_friend_details;
			$this->populate( $this->id );
		}
	}

	/**
	 * Set up data about the current friendship.
	 */
	public function populate() {
		global $wpdb;

		$bp = buddypress();

		if ( $friendship = $wpdb->get_row( $wpdb->prepare( "SELECT * FROM {$bp->friends->table_name} WHERE id = %d", $this->id ) ) ) {
			$this->initiator_user_id = $friendship->initiator_user_id;
			$this->friend_user_id    = $friendship->friend_user_id;
			$this->is_confirmed      = $friendship->is_confirmed;
			$this->is_limited        = $friendship->is_limited;
			$this->date_created      = $friendship->date_created;
		}

		if ( !empty( $this->populate_friend_details ) ) {
			if ( $this->friend_user_id == bp_displayed_user_id() ) {
				$this->friend = new BP_Core_User( $this->initiator_user_id );
			} else {
				$this->friend = new BP_Core_User( $this->friend_user_id );
			}
		}
	}

	/**
	 * Save the current friendship to the database.
	 *
	 * @return bool True on success, false on failure.
	 */
	public function save() {
		global $wpdb;

		$bp = buddypress();

		$this->initiator_user_id = apply_filters( 'friends_friendship_initiator_user_id_before_save', $this->initiator_user_id, $this->id );
		$this->friend_user_id    = apply_filters( 'friends_friendship_friend_user_id_before_save',    $this->friend_user_id,    $this->id );
		$this->is_confirmed      = apply_filters( 'friends_friendship_is_confirmed_before_save',      $this->is_confirmed,      $this->id );
		$this->is_limited        = apply_filters( 'friends_friendship_is_limited_before_save',        $this->is_limited,        $this->id );
		$this->date_created      = apply_filters( 'friends_friendship_date_created_before_save',      $this->date_created,      $this->id );

		/**
		 * Fires before processing and saving the current friendship request.
		 *
		 * @since 1.0.0
		 *
		 * @param Object $value Current friendship request object.
		 */
		do_action_ref_array( 'friends_friendship_before_save', array( &$this ) );

		// Update.
		if (!empty( $this->id ) ) {
			$result = $wpdb->query( $wpdb->prepare( "UPDATE {$bp->friends->table_name} SET initiator_user_id = %d, friend_user_id = %d, is_confirmed = %d, is_limited = %d, date_created = %s WHERE id = %d", $this->initiator_user_id, $this->friend_user_id, $this->is_confirmed, $this->is_limited, $this->date_created, $this->id ) );

		// Save.
		} else {
			$result = $wpdb->query( $wpdb->prepare( "INSERT INTO {$bp->friends->table_name} ( initiator_user_id, friend_user_id, is_confirmed, is_limited, date_created ) VALUES ( %d, %d, %d, %d, %s )", $this->initiator_user_id, $this->friend_user_id, $this->is_confirmed, $this->is_limited, $this->date_created ) );
			$this->id = $wpdb->insert_id;
		}

		/**
		 * Fires after processing and saving the current friendship request.
		 *
		 * @since 1.0.0
		 *
		 * @param Object $value Current friendship request object.
		 */
		do_action( 'friends_friendship_after_save', array( &$this ) );

		return $result;
	}

	/**
	 * Delete the current friendship from the database.
	 *
	 * @return bool|int
	 */
	public function delete() {
		global $wpdb;

		$bp = buddypress();

		return $wpdb->query( $wpdb->prepare( "DELETE FROM {$bp->friends->table_name} WHERE id = %d", $this->id ) );
	}

	/** Static Methods ********************************************************/

	/**
	 * Get the IDs of a given user's friends.
	 *
	 * @param int  $user_id              ID of the user whose friends are being retrieved.
	 * @param bool $friend_requests_only Optional. Whether to fetch
	 *                                   unaccepted requests only. Default: false.
	 * @param bool $assoc_arr            Optional. True to receive an array of arrays
	 *                                   keyed as 'user_id' => $user_id; false to get a one-dimensional
	 *                                   array of user IDs. Default: false.
	 * @return array $fids IDs of friends for provided user.
	 */
	public static function get_friend_user_ids( $user_id, $friend_requests_only = false, $assoc_arr = false ) {
		global $wpdb;

		if ( !empty( $friend_requests_only ) ) {
			$oc_sql = 'AND is_confirmed = 0';
			$friend_sql = $wpdb->prepare( " WHERE friend_user_id = %d", $user_id );
		} else {
			$oc_sql = 'AND is_confirmed = 1';
			$friend_sql = $wpdb->prepare( " WHERE (initiator_user_id = %d OR friend_user_id = %d)", $user_id, $user_id );
		}

		$bp = buddypress();
		$friends = $wpdb->get_results( "SELECT friend_user_id, initiator_user_id FROM {$bp->friends->table_name} {$friend_sql} {$oc_sql} ORDER BY date_created DESC" );
		$fids = array();

		for ( $i = 0, $count = count( $friends ); $i < $count; ++$i ) {
			if ( !empty( $assoc_arr ) ) {
				$fids[] = array( 'user_id' => ( $friends[$i]->friend_user_id == $user_id ) ? $friends[$i]->initiator_user_id : $friends[$i]->friend_user_id );
			} else {
				$fids[] = ( $friends[$i]->friend_user_id == $user_id ) ? $friends[$i]->initiator_user_id : $friends[$i]->friend_user_id;
			}
		}

		return $fids;
	}

	/**
	 * Get the ID of the friendship object, if any, between a pair of users.
	 *
	 * @param int $user_id   The ID of the first user.
	 * @param int $friend_id The ID of the second user.
	 * @return int|bool The ID of the friendship object if found, otherwise false.
	 */
	public static function get_friendship_id( $user_id, $friend_id ) {
		global $wpdb;

		$bp = buddypress();

		return $wpdb->get_var( $wpdb->prepare( "SELECT id FROM {$bp->friends->table_name} WHERE ( initiator_user_id = %d AND friend_user_id = %d ) OR ( initiator_user_id = %d AND friend_user_id = %d ) AND is_confirmed = 1", $user_id, $friend_id, $friend_id, $user_id ) );
	}

	/**
	 * Get a list of IDs of users who have requested friendship of a given user.
	 *
	 * @param int $user_id The ID of the user who has received the
	 *                     friendship requests.
	 * @return array|bool An array of user IDs, or false if none are found.
	 */
	public static function get_friendship_request_user_ids( $user_id ) {
		$friend_requests = wp_cache_get( $user_id, 'bp_friends_requests' );

		if ( false === $friend_requests ) {
			global $wpdb;

			$bp = buddypress();

			$friend_requests = $wpdb->get_col( $wpdb->prepare( "SELECT initiator_user_id FROM {$bp->friends->table_name} WHERE friend_user_id = %d AND is_confirmed = 0", $user_id ) );

			wp_cache_set( $user_id, $friend_requests, 'bp_friends_requests' );
		}

		return $friend_requests;
	}

	/**
	 * Get a total friend count for a given user.
	 *
	 * @param int $user_id Optional. ID of the user whose friendships you
	 *                     are counting. Default: displayed user (if any), otherwise
	 *                     logged-in user.
	 * @return int Friend count for the user.
	 */
	public static function total_friend_count( $user_id = 0 ) {
		global $wpdb;

		if ( empty( $user_id ) )
			$user_id = ( bp_displayed_user_id() ) ? bp_displayed_user_id() : bp_loggedin_user_id();

		$bp = buddypress();

		/*
		 * This is stored in 'total_friend_count' usermeta.
		 * This function will recalculate, update and return.
		 */

		$count = $wpdb->get_var( $wpdb->prepare( "SELECT COUNT(id) FROM {$bp->friends->table_name} WHERE (initiator_user_id = %d OR friend_user_id = %d) AND is_confirmed = 1", $user_id, $user_id ) );

		// Do not update meta if user has never had friends.
		if ( empty( $count ) && !bp_get_user_meta( $user_id, 'total_friend_count', true ) )
			return 0;

		bp_update_user_meta( $user_id, 'total_friend_count', (int) $count );

		return absint( $count );
	}

	/**
	 * Search the friends of a user by a search string.
	 *
	 * @param string $filter  The search string, matched against xprofile
	 *                        fields (if available), or usermeta 'nickname' field.
	 * @param int    $user_id ID of the user whose friends are being searched.
	 * @param int    $limit   Optional. Max number of friends to return.
	 * @param int    $page    Optional. The page of results to return. Default:
	 *                        null (no pagination - return all results).
	 *
	 * @return array|bool On success, an array: {
	 *     @type array $friends IDs of friends returned by the query.
	 *     @type int   $count   Total number of friends (disregarding
	 *                          pagination) who match the search.
	 * }. Returns false on failure.
	 */
	public static function search_friends( $filter, $user_id, $limit = null, $page = null ) {
		global $wpdb;

		/*
		 * TODO: Optimize this function.
		 */

		if ( empty( $user_id ) )
			$user_id = bp_loggedin_user_id();

		// Only search for matching strings at the beginning of the
		// name (@todo - figure out why this restriction).
		$search_terms_like = bp_esc_like( $filter ) . '%';

		$pag_sql = '';
		if ( !empty( $limit ) && !empty( $page ) )
			$pag_sql = $wpdb->prepare( " LIMIT %d, %d", intval( ( $page - 1 ) * $limit), intval( $limit ) );

		if ( !$friend_ids = BP_Friends_Friendship::get_friend_user_ids( $user_id ) )
			return false;

		// Get all the user ids for the current user's friends.
		$fids = implode( ',', wp_parse_id_list( $friend_ids ) );

		if ( empty( $fids ) )
			return false;

		$bp = buddypress();

		// Filter the user_ids based on the search criteria.
		if ( bp_is_active( 'xprofile' ) ) {
			$sql       = $wpdb->prepare( "SELECT DISTINCT user_id FROM {$bp->profile->table_name_data} WHERE user_id IN ({$fids}) AND value LIKE %s {$pag_sql}", $search_terms_like );
			$total_sql = $wpdb->prepare( "SELECT COUNT(DISTINCT user_id) FROM {$bp->profile->table_name_data} WHERE user_id IN ({$fids}) AND value LIKE %s", $search_terms_like );
		} else {
			$sql       = $wpdb->prepare( "SELECT DISTINCT user_id FROM {$wpdb->usermeta} WHERE user_id IN ({$fids}) AND meta_key = 'nickname' AND meta_value LIKE %s {$pag_sql}", $search_terms_like );
			$total_sql = $wpdb->prepare( "SELECT COUNT(DISTINCT user_id) FROM {$wpdb->usermeta} WHERE user_id IN ({$fids}) AND meta_key = 'nickname' AND meta_value LIKE %s", $search_terms_like );
		}

		$filtered_friend_ids = $wpdb->get_col( $sql );
		$total_friend_ids    = $wpdb->get_var( $total_sql );

		if ( empty( $filtered_friend_ids ) )
			return false;

		return array( 'friends' => $filtered_friend_ids, 'total' => (int) $total_friend_ids );
	}

	/**
	 * Check friendship status between two users.
	 *
	 * Note that 'pending' means that $initiator_userid has sent a friend
	 * request to $possible_friend_userid that has not yet been approved,
	 * while 'awaiting_response' is the other way around ($possible_friend_userid
	 * sent the initial request).
	 *
	 * @param int $initiator_userid       The ID of the user who is the initiator
	 *                                    of the potential friendship/request.
	 * @param int $possible_friend_userid The ID of the user who is the
	 *                                    recipient of the potential friendship/request.
	 * @return string $value The friendship status, from among 'not_friends',
	 *                       'is_friend', 'pending', and 'awaiting_response'.
	 */
	public static function check_is_friend( $initiator_userid, $possible_friend_userid ) {
		global $wpdb;

		if ( empty( $initiator_userid ) || empty( $possible_friend_userid ) ) {
			return false;
		}

		$bp = buddypress();

		$result = $wpdb->get_results( $wpdb->prepare( "SELECT id, initiator_user_id, is_confirmed FROM {$bp->friends->table_name} WHERE (initiator_user_id = %d AND friend_user_id = %d) OR (initiator_user_id = %d AND friend_user_id = %d)", $initiator_userid, $possible_friend_userid, $possible_friend_userid, $initiator_userid ) );

		if ( ! empty( $result ) ) {
			if ( 0 == (int) $result[0]->is_confirmed ) {
				$status = $initiator_userid == $result[0]->initiator_user_id ? 'pending' : 'awaiting_response';
			} else {
				$status = 'is_friend';
			}
		} else {
			$status = 'not_friends';
		}

		return $status;
	}

	/**
	 * Get the last active date of many users at once.
	 *
	 * @todo Why is this in the Friends component?
	 *
	 * @param array $user_ids IDs of users whose last_active meta is
	 *                        being queried.
	 * @return array $retval Array of last_active values + user_ids.
	 */
	public static function get_bulk_last_active( $user_ids ) {
		global $wpdb;

		$last_activities = BP_Core_User::get_last_activity( $user_ids );

		// Sort and structure as expected in legacy function.
		usort( $last_activities, create_function( '$a, $b', '
			if ( $a["date_recorded"] == $b["date_recorded"] ) {
				return 0;
			}

			return ( strtotime( $a["date_recorded"] ) < strtotime( $b["date_recorded"] ) ) ? 1 : -1;
		' ) );

		$retval = array();
		foreach ( $last_activities as $last_activity ) {
			$u = new stdClass;
			$u->last_activity = $last_activity['date_recorded'];
			$u->user_id       = $last_activity['user_id'];

			$retval[] = $u;
		}

		return $retval;
	}

	/**
	 * Mark a friendship as accepted.
	 *
	 * @param int $friendship_id ID of the friendship to be accepted.
	 * @return int Number of database rows updated.
	 */
	public static function accept($friendship_id) {
		global $wpdb;

		$bp = buddypress();

		return $wpdb->query( $wpdb->prepare( "UPDATE {$bp->friends->table_name} SET is_confirmed = 1, date_created = %s WHERE id = %d AND friend_user_id = %d", bp_core_current_time(), $friendship_id, bp_loggedin_user_id() ) );
	}

	/**
	 * Remove a friendship or a friendship request INITIATED BY the logged-in user.
	 *
	 * @param int $friendship_id ID of the friendship to be withdrawn.
	 * @return int Number of database rows deleted.
	 */
	public static function withdraw($friendship_id) {
		global $wpdb;

		$bp = buddypress();

		return $wpdb->query( $wpdb->prepare( "DELETE FROM {$bp->friends->table_name} WHERE id = %d AND initiator_user_id = %d", $friendship_id, bp_loggedin_user_id() ) );
	}

	/**
	 * Remove a friendship or a friendship request MADE OF the logged-in user.
	 *
	 * @param int $friendship_id ID of the friendship to be rejected.
	 * @return int Number of database rows deleted.
	 */
	public static function reject($friendship_id) {
		global $wpdb;

		$bp = buddypress();

		return $wpdb->query( $wpdb->prepare( "DELETE FROM {$bp->friends->table_name} WHERE id = %d AND friend_user_id = %d", $friendship_id, bp_loggedin_user_id() ) );
	}

	/**
	 * Search users.
	 *
	 * @todo Why does this exist, and why is it in bp-friends?
	 *
	 * @param string $filter  String to search by.
	 * @param int    $user_id A user ID param that is unused.
	 * @param int    $limit   Optional. Max number of records to return.
	 * @param int    $page    Optional. Number of the page to return. Default:
	 *                        false (no pagination - return all results).
	 * @return array $filtered_ids IDs of users who match the query.
	 */
	public static function search_users( $filter, $user_id, $limit = null, $page = null ) {
		global $wpdb;

		// Only search for matching strings at the beginning of the
		// name (@todo - figure out why this restriction).
		$search_terms_like = bp_esc_like( $filter ) . '%';

		$usermeta_table = $wpdb->base_prefix . 'usermeta';
		$users_table    = $wpdb->base_prefix . 'users';

		$pag_sql = '';
		if ( !empty( $limit ) && !empty( $page ) )
			$pag_sql = $wpdb->prepare( " LIMIT %d, %d", intval( ( $page - 1 ) * intval( $limit ) ), intval( $limit ) );

		$bp = buddypress();

		// Filter the user_ids based on the search criteria.
		if ( bp_is_active( 'xprofile' ) ) {
			$sql = $wpdb->prepare( "SELECT DISTINCT d.user_id as id FROM {$bp->profile->table_name_data} d, {$users_table} u WHERE d.user_id = u.id AND d.value LIKE %s ORDER BY d.value DESC {$pag_sql}", $search_terms_like );
		} else {
			$sql = $wpdb->prepare( "SELECT DISTINCT user_id as id FROM {$usermeta_table} WHERE meta_value LIKE %s ORDER BY d.value DESC {$pag_sql}", $search_terms_like );
		}

		$filtered_fids = $wpdb->get_col($sql);

		if ( empty( $filtered_fids ) )
			return false;

		return $filtered_fids;
	}

	/**
	 * Get a count of users who match a search term.
	 *
	 * @todo Why does this exist, and why is it in bp-friends?
	 *
	 * @param string $filter Search term.
	 * @return int Count of users matching the search term.
	 */
	public static function search_users_count( $filter ) {
		global $wpdb;

		// Only search for matching strings at the beginning of the
		// name (@todo - figure out why this restriction).
		$search_terms_like = bp_esc_like( $filter ) . '%';

		$usermeta_table = $wpdb->prefix . 'usermeta';
		$users_table    = $wpdb->base_prefix . 'users';

		$bp = buddypress();

		// Filter the user_ids based on the search criteria.
		if ( bp_is_active( 'xprofile' ) ) {
			$sql = $wpdb->prepare( "SELECT COUNT(DISTINCT d.user_id) FROM {$bp->profile->table_name_data} d, {$users_table} u WHERE d.user_id = u.id AND d.value LIKE %s", $search_terms_like );
		} else {
			$sql = $wpdb->prepare( "SELECT COUNT(DISTINCT user_id) FROM {$usermeta_table} WHERE meta_value LIKE %s", $search_terms_like );
		}

		$user_count = $wpdb->get_col($sql);

		if ( empty( $user_count ) )
			return false;

		return $user_count[0];
	}

	/**
	 * Sort a list of user IDs by their display names.
	 *
	 * @todo Why does this exist, and why is it in bp-friends?
	 *
	 * @param array $user_ids Array of user IDs.
	 * @return array User IDs, sorted by the associated display names.
	 */
	public static function sort_by_name( $user_ids ) {
		global $wpdb;

		if ( !bp_is_active( 'xprofile' ) )
			return false;

		$bp = buddypress();

		$user_ids = implode( ',', wp_parse_id_list( $user_ids ) );

		return $wpdb->get_results( $wpdb->prepare( "SELECT user_id FROM {$bp->profile->table_name_data} pd, {$bp->profile->table_name_fields} pf WHERE pf.id = pd.field_id AND pf.name = %s AND pd.user_id IN ( {$user_ids} ) ORDER BY pd.value ASC", bp_xprofile_fullname_field_name() ) );
	}

	/**
	 * Get a list of random friend IDs.
	 *
	 * @param int $user_id       ID of the user whose friends are being retrieved.
	 * @param int $total_friends Optional. Number of random friends to get.
	 *                           Default: 5.
	 * @return array|bool An array of random friend user IDs on success;
	 *                    false if none are found.
	 */
	public static function get_random_friends( $user_id, $total_friends = 5 ) {
		global $wpdb;

		$bp      = buddypress();
		$fids    = array();
		$sql     = $wpdb->prepare( "SELECT friend_user_id, initiator_user_id FROM {$bp->friends->table_name} WHERE (friend_user_id = %d || initiator_user_id = %d) && is_confirmed = 1 ORDER BY rand() LIMIT %d", $user_id, $user_id, $total_friends );
		$results = $wpdb->get_results( $sql );

		for ( $i = 0, $count = count( $results ); $i < $count; ++$i ) {
			$fids[] = ( $results[$i]->friend_user_id == $user_id ) ? $results[$i]->initiator_user_id : $results[$i]->friend_user_id;
		}

		// Remove duplicates.
		if ( count( $fids ) > 0 )
			return array_flip( array_flip( $fids ) );
		else
			return false;
	}

	/**
	 * Get a count of a user's friends who can be invited to a given group.
	 *
	 * Users can invite any of their friends except:
	 *
	 * - users who are already in the group
	 * - users who have a pending invite to the group
	 * - users who have been banned from the group
	 *
	 * @param int $user_id  ID of the user whose friends are being counted.
	 * @param int $group_id ID of the group friends are being invited to.
	 * @return int $invitable_count Eligible friend count.
	 */
	public static function get_invitable_friend_count( $user_id, $group_id ) {

		// Setup some data we'll use below.
		$is_group_admin  = BP_Groups_Member::check_is_admin( $user_id, $group_id );
		$friend_ids      = BP_Friends_Friendship::get_friend_user_ids( $user_id );
		$invitable_count = 0;

		for ( $i = 0, $count = count( $friend_ids ); $i < $count; ++$i ) {

			// If already a member, they cannot be invited again.
			if ( BP_Groups_Member::check_is_member( (int) $friend_ids[$i], $group_id ) )
				continue;

			// If user already has invite, they cannot be added.
			if ( BP_Groups_Member::check_has_invite( (int) $friend_ids[$i], $group_id )  )
				continue;

			// If user is not group admin and friend is banned, they cannot be invited.
			if ( ( false === $is_group_admin ) && BP_Groups_Member::check_is_banned( (int) $friend_ids[$i], $group_id ) )
				continue;

			$invitable_count++;
		}

		return $invitable_count;
	}

	/**
	 * Get the friend user IDs for a given friendship.
	 *
	 * @param int $friendship_id ID of the friendship.
	 * @return object friend_user_id and initiator_user_id.
	 */
	public static function get_user_ids_for_friendship( $friendship_id ) {
		global $wpdb;

		$bp = buddypress();

		return $wpdb->get_row( $wpdb->prepare( "SELECT friend_user_id, initiator_user_id FROM {$bp->friends->table_name} WHERE id = %d", $friendship_id ) );
	}

	/**
	 * Delete all friendships and friend notifications related to a user.
	 *
	 * @param int $user_id ID of the user being expunged.
	 */
	public static function delete_all_for_user( $user_id ) {
		global $wpdb;

		$bp = buddypress();

		// Get friends of $user_id.
		$friend_ids = BP_Friends_Friendship::get_friend_user_ids( $user_id );

		// Delete all friendships related to $user_id.
		$wpdb->query( $wpdb->prepare( "DELETE FROM {$bp->friends->table_name} WHERE friend_user_id = %d OR initiator_user_id = %d", $user_id, $user_id ) );

		// Delete friend request notifications for members who have a
		// notification from this user.
		if ( bp_is_active( 'notifications' ) ) {
			$wpdb->query( $wpdb->prepare( "DELETE FROM {$bp->notifications->table_name} WHERE component_name = 'friends' AND ( component_action = 'friendship_request' OR component_action = 'friendship_accepted' ) AND item_id = %d", $user_id ) );
		}

		// Loop through friend_ids and update their counts.
		foreach ( (array) $friend_ids as $friend_id ) {
			BP_Friends_Friendship::total_friend_count( $friend_id );
		}
	}
}
