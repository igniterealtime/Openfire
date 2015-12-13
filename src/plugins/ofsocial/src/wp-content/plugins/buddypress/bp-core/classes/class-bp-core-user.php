<?php
/**
 * Core component classes.
 *
 * @package BuddyPress
 * @subpackage Core
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Fetch data about a BuddyPress user.
 *
 * BP_Core_User class can be used by any component. It will fetch useful
 * details for any user when provided with a user_id.
 *
 * Example:
 *    $user = new BP_Core_User( $user_id );
 *    $user_avatar = $user->avatar;
 *	  $user_email = $user->email;
 *    $user_status = $user->status;
 *    etc.
 */
class BP_Core_User {

	/**
	 * ID of the user which the object relates to.
	 *
	 * @var integer
	 */
	public $id;

	/**
	 * The URL to the full size of the avatar for the user.
	 *
	 * @var string
	 */
	public $avatar;

	/**
	 * The URL to the thumb size of the avatar for the user.
	 *
	 * @var string
	 */
	public $avatar_thumb;

	/**
	 * The URL to the mini size of the avatar for the user.
	 *
	 * @var string
	 */
	public $avatar_mini;

	/**
	 * The full name of the user.
	 *
	 * @var string
	 */
	public $fullname;

	/**
	 * The email for the user.
	 *
	 * @var string
	 */
	public $email;

	/**
	 * The absolute url for the user's profile.
	 *
	 * @var string
	 */
	public $user_url;

	/**
	 * The HTML for the user link, with the link text being the user's full name.
	 *
	 * @var string
	 */
	public $user_link;

	/**
	 * Contains a formatted string when the last time the user was active.
	 *
	 * Example: "active 2 hours and 50 minutes ago"
	 *
	 * @var string
	 */
	public $last_active;

	/* Extras */

	/**
	 * The total number of "Friends" the user has on site.
	 *
	 * @var integer
	 */
	public $total_friends;

	/**
	 * The total number of blog posts posted by the user.
	 *
	 * @var integer
	 * @deprecated No longer used
	 */
	public $total_blogs;

	/**
	 * The total number of groups the user is a part of.
	 *
	 * Example: "1 group", "2 groups"
	 *
	 * @var string
	 */
	public $total_groups;

	/**
	 * Profile information for the specific user.
	 *
	 * @since 1.2.0
	 * @var array
	 */
	public $profile_data;

	/** Public Methods *******************************************************/

	/**
	 * Class constructor.
	 *
	 * @param integer $user_id         The ID for the user being queried.
	 * @param bool    $populate_extras Whether to fetch extra information such as
	 *                                 group/friendship counts or not. Default: false.
	 */
	public function __construct( $user_id, $populate_extras = false ) {
		if ( !empty( $user_id ) ) {
			$this->id = $user_id;
			$this->populate();

			if ( !empty( $populate_extras ) ) {
				$this->populate_extras();
			}
		}
	}

	/**
	 * Populate the instantiated class with data based on the User ID provided.
	 *
	 * @uses bp_core_get_userurl() Returns the URL with no HTML markup for
	 *       a user based on their user id.
	 * @uses bp_core_get_userlink() Returns a HTML formatted link for a
	 *       user with the user's full name as the link text.
	 * @uses bp_core_get_user_email() Returns the email address for the
	 *       user based on user ID.
	 * @uses bp_get_user_meta() BP function returns the value of passed
	 *       usermeta name from usermeta table.
	 * @uses bp_core_fetch_avatar() Returns HTML formatted avatar for a user
	 * @uses bp_profile_last_updated_date() Returns the last updated date
	 *       for a user.
	 */
	public function populate() {

		if ( bp_is_active( 'xprofile' ) )
			$this->profile_data = $this->get_profile_data();

		if ( !empty( $this->profile_data ) ) {
			$full_name_field_name = bp_xprofile_fullname_field_name();

			$this->user_url  = bp_core_get_user_domain( $this->id, $this->profile_data['user_nicename'], $this->profile_data['user_login'] );
			$this->fullname  = esc_attr( $this->profile_data[$full_name_field_name]['field_data'] );
			$this->user_link = "<a href='{$this->user_url}' title='{$this->fullname}'>{$this->fullname}</a>";
			$this->email     = esc_attr( $this->profile_data['user_email'] );
		} else {
			$this->user_url  = bp_core_get_user_domain( $this->id );
			$this->user_link = bp_core_get_userlink( $this->id );
			$this->fullname  = esc_attr( bp_core_get_user_displayname( $this->id ) );
			$this->email     = esc_attr( bp_core_get_user_email( $this->id ) );
		}

		// Cache a few things that are fetched often
		wp_cache_set( 'bp_user_fullname_' . $this->id, $this->fullname, 'bp' );
		wp_cache_set( 'bp_user_email_' . $this->id, $this->email, 'bp' );
		wp_cache_set( 'bp_user_url_' . $this->id, $this->user_url, 'bp' );

		$this->avatar       = bp_core_fetch_avatar( array( 'item_id' => $this->id, 'type' => 'full', 'alt' => sprintf( __( 'Profile photo of %s', 'buddypress' ), $this->fullname ) ) );
		$this->avatar_thumb = bp_core_fetch_avatar( array( 'item_id' => $this->id, 'type' => 'thumb', 'alt' => sprintf( __( 'Profile photo of %s', 'buddypress' ), $this->fullname ) ) );
		$this->avatar_mini  = bp_core_fetch_avatar( array( 'item_id' => $this->id, 'type' => 'thumb', 'alt' => sprintf( __( 'Profile photo of %s', 'buddypress' ), $this->fullname ), 'width' => 30, 'height' => 30 ) );
		$this->last_active  = bp_core_get_last_activity( bp_get_user_last_activity( $this->id ), __( 'active %s', 'buddypress' ) );
	}

	/**
	 * Populates extra fields such as group and friendship counts.
	 */
	public function populate_extras() {

		if ( bp_is_active( 'friends' ) ) {
			$this->total_friends = BP_Friends_Friendship::total_friend_count( $this->id );
		}

		if ( bp_is_active( 'groups' ) ) {
			$this->total_groups = BP_Groups_Member::total_group_count( $this->id );
			$this->total_groups = sprintf( _n( '%d group', '%d groups', $this->total_groups, 'buddypress' ), $this->total_groups );
		}
	}

	/**
	 * Fetch xprofile data for the current user.
	 *
	 * @see BP_XProfile_ProfileData::get_all_for_user() for description of
	 *      return value.
	 *
	 * @return array See {@link BP_XProfile_Profile_Data::get_all_for_user()}.
	 */
	public function get_profile_data() {
		return BP_XProfile_ProfileData::get_all_for_user( $this->id );
	}

	/** Static Methods ********************************************************/

	/**
	 * Get a list of users that match the query parameters.
	 *
	 * Since BuddyPress 1.7, use {@link BP_User_Query} instead.
	 *
	 * @deprecated 1.7.0 Use {@link BP_User_Query}.
	 *
	 * @see BP_User_Query for a description of parameters, most of which
	 *      are used there in the same way.
	 *
	 * @param string      $type            See {@link BP_User_Query}.
	 * @param int         $limit           See {@link BP_User_Query}. Default: 0.
	 * @param int         $page            See {@link BP_User_Query}. Default: 1.
	 * @param int         $user_id         See {@link BP_User_Query}. Default: 0.
	 * @param mixed       $include         See {@link BP_User_Query}. Default: false.
	 * @param string|bool $search_terms    See {@link BP_User_Query}.
	 *                                     Default: false.
	 * @param bool        $populate_extras See {@link BP_User_Query}.
	 *                                     Default: true.
	 * @param mixed       $exclude         See {@link BP_User_Query}. Default: false.
	 * @param string|bool $meta_key        See {@link BP_User_Query}.
	 *                                     Default: false.
	 * @param string|bool $meta_value      See {@link BP_User_Query}.
	 *                                     Default: false.
	 * @return array {
	 *     @type int   $total_users Total number of users matched by query
	 *                              params.
	 *     @type array $paged_users The current page of users matched by
	 *                              query params.
	 * }
	 */
	public static function get_users( $type, $limit = 0, $page = 1, $user_id = 0, $include = false, $search_terms = false, $populate_extras = true, $exclude = false, $meta_key = false, $meta_value = false ) {
		global $wpdb;

		_deprecated_function( __METHOD__, '1.7', 'BP_User_Query' );

		$bp = buddypress();

		$sql = array();

		$sql['select_main'] = "SELECT DISTINCT u.ID as id, u.user_registered, u.user_nicename, u.user_login, u.display_name, u.user_email";

		if ( 'active' == $type || 'online' == $type || 'newest' == $type  ) {
			$sql['select_active'] = ", um.meta_value as last_activity";
		}

		if ( 'popular' == $type ) {
			$sql['select_popular'] = ", um.meta_value as total_friend_count";
		}

		if ( 'alphabetical' == $type ) {
			$sql['select_alpha'] = ", pd.value as fullname";
		}

		if ( $meta_key ) {
			$sql['select_meta'] = ", umm.meta_key";

			if ( $meta_value ) {
				$sql['select_meta'] .= ", umm.meta_value";
			}
		}

		$sql['from'] = "FROM {$wpdb->users} u LEFT JOIN {$wpdb->usermeta} um ON um.user_id = u.ID";

		// We search against xprofile fields, so we must join the table
		if ( $search_terms && bp_is_active( 'xprofile' ) ) {
			$sql['join_profiledata_search'] = "LEFT JOIN {$bp->profile->table_name_data} spd ON u.ID = spd.user_id";
		}

		// Alphabetical sorting is done by the xprofile Full Name field
		if ( 'alphabetical' == $type ) {
			$sql['join_profiledata_alpha'] = "LEFT JOIN {$bp->profile->table_name_data} pd ON u.ID = pd.user_id";
		}

		if ( $meta_key ) {
			$sql['join_meta'] = "LEFT JOIN {$wpdb->usermeta} umm ON umm.user_id = u.ID";
		}

		$sql['where'] = 'WHERE ' . bp_core_get_status_sql( 'u.' );

		if ( 'active' == $type || 'online' == $type || 'newest' == $type ) {
			$sql['where_active'] = $wpdb->prepare( "AND um.meta_key = %s", bp_get_user_meta_key( 'last_activity' ) );
		}

		if ( 'popular' == $type ) {
			$sql['where_popular'] = $wpdb->prepare( "AND um.meta_key = %s", bp_get_user_meta_key( 'total_friend_count' ) );
		}

		if ( 'online' == $type ) {
			$sql['where_online'] = "AND DATE_ADD( um.meta_value, INTERVAL 5 MINUTE ) >= UTC_TIMESTAMP()";
		}

		if ( 'alphabetical' == $type ) {
			$sql['where_alpha'] = "AND pd.field_id = 1";
		}

		if ( !empty( $exclude ) ) {
			$exclude              = implode( ',', wp_parse_id_list( $exclude ) );
			$sql['where_exclude'] = "AND u.ID NOT IN ({$exclude})";
		}

		// Passing an $include value of 0 or '0' will necessarily result in an empty set
		// returned. The default value of false will hit the 'else' clause.
		if ( 0 === $include || '0' === $include ) {
			$sql['where_users'] = "AND 0 = 1";
		} else {
			if ( !empty( $include ) ) {
				$include = implode( ',',  wp_parse_id_list( $include ) );
				$sql['where_users'] = "AND u.ID IN ({$include})";
			} elseif ( !empty( $user_id ) && bp_is_active( 'friends' ) ) {
				$friend_ids = friends_get_friend_user_ids( $user_id );

				if ( !empty( $friend_ids ) ) {
					$friend_ids = implode( ',', wp_parse_id_list( $friend_ids ) );
					$sql['where_friends'] = "AND u.ID IN ({$friend_ids})";

				// User has no friends, return false since there will be no users to fetch.
				} else {
					return false;
				}
			}
		}

		if ( !empty( $search_terms ) && bp_is_active( 'xprofile' ) ) {
			$search_terms_like        = '%' . bp_esc_like( $search_terms ) . '%';
			$sql['where_searchterms'] = $wpdb->prepare( "AND spd.value LIKE %s", $search_terms_like );
		}

		if ( !empty( $meta_key ) ) {
			$sql['where_meta'] = $wpdb->prepare( " AND umm.meta_key = %s", $meta_key );

			// If a meta value is provided, match it
			if ( $meta_value ) {
				$sql['where_meta'] .= $wpdb->prepare( " AND umm.meta_value = %s", $meta_value );
			}
		}

		switch ( $type ) {
			case 'active': case 'online': default:
				$sql[] = "ORDER BY um.meta_value DESC";
				break;
			case 'newest':
				$sql[] = "ORDER BY u.ID DESC";
				break;
			case 'alphabetical':
				$sql[] = "ORDER BY pd.value ASC";
				break;
			case 'random':
				$sql[] = "ORDER BY rand()";
				break;
			case 'popular':
				$sql[] = "ORDER BY CONVERT(um.meta_value, SIGNED) DESC";
				break;
		}

		if ( !empty( $limit ) && !empty( $page ) ) {
			$sql['pagination'] = $wpdb->prepare( "LIMIT %d, %d", intval( ( $page - 1 ) * $limit), intval( $limit ) );
		}

		/**
		 * Filters the SQL used to query for paged users.
		 *
		 * @since 1.2.6
		 *
		 * @param string $value Concatenated SQL statement for the query.
		 * @param array  $sql   Array of SQL statement parts for the query.
		 */
		$paged_users_sql = apply_filters( 'bp_core_get_paged_users_sql', join( ' ', (array) $sql ), $sql );
		$paged_users     = $wpdb->get_results( $paged_users_sql );

		// Re-jig the SQL so we can get the total user count
		unset( $sql['select_main'] );

		if ( !empty( $sql['select_active'] ) ) {
			unset( $sql['select_active'] );
		}

		if ( !empty( $sql['select_popular'] ) ) {
			unset( $sql['select_popular'] );
		}

		if ( !empty( $sql['select_alpha'] ) ) {
			unset( $sql['select_alpha'] );
		}

		if ( !empty( $sql['pagination'] ) ) {
			unset( $sql['pagination'] );
		}

		array_unshift( $sql, "SELECT COUNT(u.ID)" );

		/**
		 * Filters the SQL used to query for total users.
		 *
		 * @since 1.2.6
		 *
		 * @param string $value Concatenated SQL statement for the query.
		 * @param array  $sql   Array of SQL statement parts for the query.
		 */
		$total_users_sql = apply_filters( 'bp_core_get_total_users_sql', join( ' ', (array) $sql ), $sql );
		$total_users     = $wpdb->get_var( $total_users_sql );

		/***
		 * Lets fetch some other useful data in a separate queries, this will be faster than querying the data for every user in a list.
		 * We can't add these to the main query above since only users who have this information will be returned (since the much of the data is in usermeta and won't support any type of directional join)
		 */
		if ( !empty( $populate_extras ) ) {
			$user_ids = array();

			foreach ( (array) $paged_users as $user ) {
				$user_ids[] = $user->id;
			}

			// Add additional data to the returned results
			$paged_users = BP_Core_User::get_user_extras( $paged_users, $user_ids, $type );
		}

		return array( 'users' => $paged_users, 'total' => $total_users );
	}


	/**
	 * Fetch the details for all users whose usernames start with the given letter.
	 *
	 * @global wpdb $wpdb WordPress database object.
	 *
	 * @param string $letter          The letter the users names are to start with.
	 * @param int    $limit           The number of users we wish to retrive.
	 * @param int    $page            The page number we are currently on, used in conjunction
	 *                                with $limit to get the start position for the limit.
	 * @param bool   $populate_extras Populate extra user fields?
	 * @param string $exclude         Comma-separated IDs of users whose results
	 *                                aren't to be fetched.
	 *
	 * @return mixed False on error, otherwise associative array of results.
	 */
	public static function get_users_by_letter( $letter, $limit = null, $page = 1, $populate_extras = true, $exclude = '' ) {
		global $wpdb;

		$pag_sql = '';
		if ( $limit && $page ) {
			$pag_sql = $wpdb->prepare( " LIMIT %d, %d", intval( ( $page - 1 ) * $limit), intval( $limit ) );
		}

		// Multibyte compliance
		if ( function_exists( 'mb_strlen' ) ) {
			if ( mb_strlen( $letter, 'UTF-8' ) > 1 || is_numeric( $letter ) || !$letter ) {
				return false;
			}
		} else {
			if ( strlen( $letter ) > 1 || is_numeric( $letter ) || !$letter ) {
				return false;
			}
		}

		$bp = buddypress();

		$letter_like = bp_esc_like( $letter ) . '%';
		$status_sql  = bp_core_get_status_sql( 'u.' );

		if ( !empty( $exclude ) ) {
			$exclude     = implode( ',', wp_parse_id_list( $exclude ) );
			$exclude_sql = " AND u.id NOT IN ({$exclude})";
		} else {
			$exclude_sql = '';
		}

		/**
		 * Filters the SQL used to query for total user count by first letter.
		 *
		 * @since 1.0.0
		 *
		 * @param string $value SQL prepared statement for the user count query.
		 */
		$total_users_sql = apply_filters( 'bp_core_users_by_letter_count_sql', $wpdb->prepare( "SELECT COUNT(DISTINCT u.ID) FROM {$wpdb->users} u LEFT JOIN {$bp->profile->table_name_data} pd ON u.ID = pd.user_id LEFT JOIN {$bp->profile->table_name_fields} pf ON pd.field_id = pf.id WHERE {$status_sql} AND pf.name = %s {$exclude_sql} AND pd.value LIKE %s ORDER BY pd.value ASC", bp_xprofile_fullname_field_name(), $letter_like ) );

		/**
		 * Filters the SQL used to query for users by first letter.
		 *
		 * @since 1.0.0
		 *
		 * @param string $value SQL prepared statement for the user query.
		 */
		$paged_users_sql = apply_filters( 'bp_core_users_by_letter_sql',       $wpdb->prepare( "SELECT DISTINCT u.ID as id, u.user_registered, u.user_nicename, u.user_login, u.user_email FROM {$wpdb->users} u LEFT JOIN {$bp->profile->table_name_data} pd ON u.ID = pd.user_id LEFT JOIN {$bp->profile->table_name_fields} pf ON pd.field_id = pf.id WHERE {$status_sql} AND pf.name = %s {$exclude_sql} AND pd.value LIKE %s ORDER BY pd.value ASC{$pag_sql}", bp_xprofile_fullname_field_name(), $letter_like ) );

		$total_users = $wpdb->get_var( $total_users_sql );
		$paged_users = $wpdb->get_results( $paged_users_sql );

		/***
		 * Lets fetch some other useful data in a separate queries, this will be
		 * faster than querying the data for every user in a list. We can't add
		 * these to the main query above since only users who have this
		 * information will be returned (since the much of the data is in
		 * usermeta and won't support any type of directional join)
		 */
		$user_ids = array();
		foreach ( (array) $paged_users as $user )
			$user_ids[] = (int) $user->id;

		// Add additional data to the returned results
		if ( $populate_extras ) {
			$paged_users = BP_Core_User::get_user_extras( $paged_users, $user_ids );
		}

		return array( 'users' => $paged_users, 'total' => $total_users );
	}

	/**
	 * Get details of specific users from the database.
	 *
	 * Use {@link BP_User_Query} with the 'user_ids' param instead.
	 *
	 * @global wpdb $wpdb WordPress database object.
	 *
	 * @param array $user_ids        The user IDs of the users who we wish to
	 *                               fetch information on.
	 * @param int   $limit           The limit of results we want.
	 * @param int   $page            The page we are on for pagination.
	 * @param bool  $populate_extras Populate extra user fields?
	 *
	 * @return array Associative array.
	 */
	public static function get_specific_users( $user_ids, $limit = null, $page = 1, $populate_extras = true ) {
		global $wpdb;

		$pag_sql = '';
		if ( $limit && $page )
			$pag_sql = $wpdb->prepare( " LIMIT %d, %d", intval( ( $page - 1 ) * $limit), intval( $limit ) );

		$user_ids   = implode( ',', wp_parse_id_list( $user_ids ) );
		$status_sql = bp_core_get_status_sql();

		/**
		 * Filter the SQL string used for querying specific user count.
		 *
		 * This same filter name is used for the paged user SQL, and so should be avoided.
		 * Use 'bp_core_user_get_specific_users_count_sql' instead.
		 *
		 * @deprecated 2.3.0
		 *
		 * @param string $sql SQL string.
		 */
		$total_users_sql = apply_filters( 'bp_core_get_specific_users_count_sql', "SELECT COUNT(ID) FROM {$wpdb->users} WHERE {$status_sql} AND ID IN ({$user_ids})" );

		/**
		 * Filter the SQL string used for querying specific user count results.
		 *
		 * Use this instead of the deprecated 'bp_core_get_specific_users_count_sql'.
		 *
		 * @since 2.3.0
		 *
		 * @param string   $sql             SQL string.
		 * @param array    $user_ids        Array of IDs of specific users to fetch.
		 * @param int|null $limit           Max number of records to return. Null for no limit.
		 * @param int      $page            The page we're on for pagination.
		 * @param bool     $populate_extras Whether to populate extra user fields.
		 */
		$total_users_sql = apply_filters( 'bp_core_user_get_specific_users_count_sql', $total_users_sql, $user_ids, $limit, $page, $populate_extras );

		/**
		 * Filter the SQL string used for querying specific user paged results.
		 *
		 * This same filter name is used for the user count SQL, and so should be avoided.
		 * Use 'bp_core_user_get_specific_users_paged_sql' instead.
		 *
		 * @deprecated 2.3.0
		 *
		 * @param string $sql SQL string.
		 */
		$paged_users_sql = apply_filters( 'bp_core_get_specific_users_count_sql', "SELECT ID as id, user_registered, user_nicename, user_login, user_email FROM {$wpdb->users} WHERE {$status_sql} AND ID IN ({$user_ids}) {$pag_sql}" );

		/**
		 * Filter the SQL string used for querying specific user paged results.
		 *
		 * Use this instead of the deprecated 'bp_core_get_specific_users_count_sql'.
		 *
		 * @since 2.3.0
		 *
		 * @param string   $sql             SQL string.
		 * @param array    $user_ids        Array of IDs of specific users to fetch.
		 * @param int|null $limit           Max number of records to return. Null for no limit.
		 * @param int      $page            The page we're on for pagination.
		 * @param bool     $populate_extras Whether to populate extra user fields.
		 */
		$paged_users_sql = apply_filters( 'bp_core_user_get_specific_users_paged_sql', $paged_users_sql, $user_ids, $limit, $page, $populate_extras );

		$total_users = $wpdb->get_var( $total_users_sql );
		$paged_users = $wpdb->get_results( $paged_users_sql );

		/***
		 * Lets fetch some other useful data in a separate queries, this will be
		 * faster than querying the data for every user in a list. We can't add
		 * these to the main query above since only users who have this
		 * information will be returned (since the much of the data is in
		 * usermeta and won't support any type of directional join)
		 */

		// Add additional data to the returned results
		if ( !empty( $populate_extras ) ) {
			$paged_users = BP_Core_User::get_user_extras( $paged_users, $user_ids );
		}

		return array( 'users' => $paged_users, 'total' => $total_users );
	}

	/**
	 * Find users who match on the value of an xprofile data.
	 *
	 * @global wpdb $wpdb WordPress database object.
	 *
	 * @param string  $search_terms    The terms to search the profile table
	 *                                 value column for.
	 * @param int     $limit           The limit of results we want.
	 * @param int     $page            The page we are on for pagination.
	 * @param boolean $populate_extras Populate extra user fields?
	 *
	 * @return array Associative array.
	 */
	public static function search_users( $search_terms, $limit = null, $page = 1, $populate_extras = true ) {
		global $wpdb;

		$bp = buddypress();

		$user_ids = array();
		$pag_sql  = $limit && $page ? $wpdb->prepare( " LIMIT %d, %d", intval( ( $page - 1 ) * intval( $limit ) ), intval( $limit ) ) : '';

		$search_terms_like = '%' . bp_esc_like( $search_terms ) . '%';
		$status_sql        = bp_core_get_status_sql( 'u.' );

		/**
		 * Filters the SQL used to query for searched users count.
		 *
		 * @since 1.0.0
		 *
		 * @param string $value SQL statement for the searched users count query.
		 */
		$total_users_sql = apply_filters( 'bp_core_search_users_count_sql', $wpdb->prepare( "SELECT COUNT(DISTINCT u.ID) as id FROM {$wpdb->users} u LEFT JOIN {$bp->profile->table_name_data} pd ON u.ID = pd.user_id WHERE {$status_sql} AND pd.value LIKE %s ORDER BY pd.value ASC", $search_terms_like ), $search_terms );

		/**
		 * Filters the SQL used to query for searched users.
		 *
		 * @since 1.0.0
		 *
		 * @param string $value SQL statement for the searched users query.
		 */
		$paged_users_sql = apply_filters( 'bp_core_search_users_sql',       $wpdb->prepare( "SELECT DISTINCT u.ID as id, u.user_registered, u.user_nicename, u.user_login, u.user_email FROM {$wpdb->users} u LEFT JOIN {$bp->profile->table_name_data} pd ON u.ID = pd.user_id WHERE {$status_sql} AND pd.value LIKE %s ORDER BY pd.value ASC{$pag_sql}", $search_terms_like ), $search_terms, $pag_sql );

		$total_users = $wpdb->get_var( $total_users_sql );
		$paged_users = $wpdb->get_results( $paged_users_sql );

		/***
		 * Lets fetch some other useful data in a separate queries, this will be faster than querying the data for every user in a list.
		 * We can't add these to the main query above since only users who have this information will be returned (since the much of the data is in usermeta and won't support any type of directional join)
		 */
		foreach ( (array) $paged_users as $user )
			$user_ids[] = $user->id;

		// Add additional data to the returned results
		if ( $populate_extras )
			$paged_users = BP_Core_User::get_user_extras( $paged_users, $user_ids );

		return array( 'users' => $paged_users, 'total' => $total_users );
	}

	/**
	 * Fetch extra user information, such as friend count and last profile update message.
	 *
	 * Accepts multiple user IDs to fetch data for.
	 *
	 * @global wpdb $wpdb WordPress database object.
	 *
	 * @param array       $paged_users An array of stdClass containing the users.
	 * @param string      $user_ids    The user ids to select information about.
	 * @param string|bool $type        The type of fields we wish to get.
	 *
	 * @return mixed False on error, otherwise associative array of results.
	 */
	public static function get_user_extras( &$paged_users, &$user_ids, $type = false ) {
		global $wpdb;

		$bp = buddypress();

		if ( empty( $user_ids ) )
			return $paged_users;

		// Sanitize user IDs
		$user_ids = implode( ',', wp_parse_id_list( $user_ids ) );

		// Fetch the user's full name
		if ( bp_is_active( 'xprofile' ) && 'alphabetical' != $type ) {
			$names = $wpdb->get_results( $wpdb->prepare( "SELECT pd.user_id as id, pd.value as fullname FROM {$bp->profile->table_name_fields} pf, {$bp->profile->table_name_data} pd WHERE pf.id = pd.field_id AND pf.name = %s AND pd.user_id IN ( {$user_ids} )", bp_xprofile_fullname_field_name() ) );
			for ( $i = 0, $count = count( $paged_users ); $i < $count; ++$i ) {
				foreach ( (array) $names as $name ) {
					if ( $name->id == $paged_users[$i]->id )
						$paged_users[$i]->fullname = $name->fullname;
				}
			}
		}

		// Fetch the user's total friend count
		if ( 'popular' != $type ) {
			$friend_count = $wpdb->get_results( $wpdb->prepare( "SELECT user_id as id, meta_value as total_friend_count FROM {$wpdb->usermeta} WHERE meta_key = %s AND user_id IN ( {$user_ids} )", bp_get_user_meta_key( 'total_friend_count' ) ) );
			for ( $i = 0, $count = count( $paged_users ); $i < $count; ++$i ) {
				foreach ( (array) $friend_count as $fcount ) {
					if ( $fcount->id == $paged_users[$i]->id )
						$paged_users[$i]->total_friend_count = (int) $fcount->total_friend_count;
				}
			}
		}

		// Fetch whether or not the user is a friend
		if ( bp_is_active( 'friends' ) ) {
			$friend_status = $wpdb->get_results( $wpdb->prepare( "SELECT initiator_user_id, friend_user_id, is_confirmed FROM {$bp->friends->table_name} WHERE (initiator_user_id = %d AND friend_user_id IN ( {$user_ids} ) ) OR (initiator_user_id IN ( {$user_ids} ) AND friend_user_id = %d )", bp_loggedin_user_id(), bp_loggedin_user_id() ) );
			for ( $i = 0, $count = count( $paged_users ); $i < $count; ++$i ) {
				foreach ( (array) $friend_status as $status ) {
					if ( $status->initiator_user_id == $paged_users[$i]->id || $status->friend_user_id == $paged_users[$i]->id )
						$paged_users[$i]->is_friend = $status->is_confirmed;
				}
			}
		}

		if ( 'active' != $type ) {
			$user_activity = $wpdb->get_results( $wpdb->prepare( "SELECT user_id as id, meta_value as last_activity FROM {$wpdb->usermeta} WHERE meta_key = %s AND user_id IN ( {$user_ids} )", bp_get_user_meta_key( 'last_activity' ) ) );
			for ( $i = 0, $count = count( $paged_users ); $i < $count; ++$i ) {
				foreach ( (array) $user_activity as $activity ) {
					if ( $activity->id == $paged_users[$i]->id )
						$paged_users[$i]->last_activity = $activity->last_activity;
				}
			}
		}

		// Fetch the user's last_activity
		if ( 'active' != $type ) {
			$user_activity = $wpdb->get_results( $wpdb->prepare( "SELECT user_id as id, meta_value as last_activity FROM {$wpdb->usermeta} WHERE meta_key = %s AND user_id IN ( {$user_ids} )", bp_get_user_meta_key( 'last_activity' ) ) );
			for ( $i = 0, $count = count( $paged_users ); $i < $count; ++$i ) {
				foreach ( (array) $user_activity as $activity ) {
					if ( $activity->id == $paged_users[$i]->id )
						$paged_users[$i]->last_activity = $activity->last_activity;
				}
			}
		}

		// Fetch the user's latest update
		$user_update = $wpdb->get_results( $wpdb->prepare( "SELECT user_id as id, meta_value as latest_update FROM {$wpdb->usermeta} WHERE meta_key = %s AND user_id IN ( {$user_ids} )", bp_get_user_meta_key( 'bp_latest_update' ) ) );
		for ( $i = 0, $count = count( $paged_users ); $i < $count; ++$i ) {
			foreach ( (array) $user_update as $update ) {
				if ( $update->id == $paged_users[$i]->id )
					$paged_users[$i]->latest_update = $update->latest_update;
			}
		}

		return $paged_users;
	}

	/**
	 * Get WordPress user details for a specified user.
	 *
	 * @global wpdb $wpdb WordPress database object.
	 *
	 * @param int $user_id User ID.
	 *
	 * @return array Associative array.
	 */
	public static function get_core_userdata( $user_id ) {
		global $wpdb;

		if ( !$user = $wpdb->get_row( $wpdb->prepare( "SELECT * FROM {$wpdb->users} WHERE ID = %d LIMIT 1", $user_id ) ) )
			return false;

		return $user;
	}

	/**
	 * Get last activity data for a user or set of users.
	 *
	 * @param int|array User IDs or multiple user IDs.
	 *
	 * @return array
	 */
	public static function get_last_activity( $user_id ) {
		global $wpdb;

		// Sanitize and remove empty values
		$user_ids = array_filter( wp_parse_id_list( $user_id ) );

		if ( empty( $user_ids ) ) {
			return false;
		}

		$uncached_user_ids = bp_get_non_cached_ids( $user_ids, 'bp_last_activity' );
		if ( ! empty( $uncached_user_ids ) ) {
			$bp = buddypress();

			$user_ids_sql = implode( ',', $uncached_user_ids );
			$user_count   = count( $uncached_user_ids );

			$last_activities = $wpdb->get_results( $wpdb->prepare( "SELECT id, user_id, date_recorded FROM {$bp->members->table_name_last_activity} WHERE component = %s AND type = 'last_activity' AND user_id IN ({$user_ids_sql}) LIMIT {$user_count}", $bp->members->id ) );

			foreach ( $last_activities as $last_activity ) {
				wp_cache_set( $last_activity->user_id, array(
					'user_id'       => $last_activity->user_id,
					'date_recorded' => $last_activity->date_recorded,
					'activity_id'   => $last_activity->id,
				), 'bp_last_activity' );
			}
		}

		// Fetch all user data from the cache
		$retval = array();
		foreach ( $user_ids as $user_id ) {
			$retval[ $user_id ] = wp_cache_get( $user_id, 'bp_last_activity' );
		}

		return $retval;
	}

	/**
	 * Set a user's last_activity value.
	 *
	 * Will create a new entry if it does not exist. Otherwise updates the
	 * existing entry.
	 *
	 * @since 2.0.0
	 *
	 * @param int    $user_id ID of the user whose last_activity you are updating.
	 * @param string $time    MySQL-formatted time string.
	 *
	 * @return bool True on success, false on failure.
	 */
	public static function update_last_activity( $user_id, $time ) {
		global $wpdb;

		$table_name = buddypress()->members->table_name_last_activity;

		$activity = self::get_last_activity( $user_id );

		if ( ! empty( $activity[ $user_id ] ) ) {
			$updated = $wpdb->update(
				$table_name,

				// Data to update
				array(
					'date_recorded' => $time,
				),

				// WHERE
				array(
					'id' => $activity[ $user_id ]['activity_id'],
				),

				// Data sanitization format
				array(
					'%s',
				),

				// WHERE sanitization format
				array(
					'%d',
				)
			);

			// add new date to existing activity entry for caching
			$activity[ $user_id ]['date_recorded'] = $time;

		} else {
			$updated = $wpdb->insert(
				$table_name,

				// Data
				array(
					'user_id'       => $user_id,
					'component'     => buddypress()->members->id,
					'type'          => 'last_activity',
					'action'        => '',
					'content'       => '',
					'primary_link'  => '',
					'item_id'       => 0,
					'date_recorded' => $time,
				),

				// Data sanitization format
				array(
					'%d',
					'%s',
					'%s',
					'%s',
					'%s',
					'%s',
					'%d',
					'%s',
				)
			);

			// set up activity array for caching
			// view the foreach loop in the get_last_activity() method for format
			$activity = array();
			$activity[ $user_id ] = array(
				'user_id'       => $user_id,
				'date_recorded' => $time,
				'activity_id'   => $wpdb->insert_id,
			);
		}

		// set cache
		wp_cache_set( $user_id, $activity[ $user_id ], 'bp_last_activity' );

		return $updated;
	}

	/**
	 * Delete a user's last_activity value.
	 *
	 * @since 2.0.0
	 *
	 * @param int $user_id
	 *
	 * @return bool True on success, false on failure or if no last_activity
	 *              is found for the user.
	 */
	public static function delete_last_activity( $user_id ) {
		global $wpdb;

		$existing = self::get_last_activity( $user_id );

		if ( empty( $existing ) ) {
			return false;
		}

		$deleted = $wpdb->delete(
			buddypress()->members->table_name_last_activity,

			// WHERE
			array(
				'id' => $existing[ $user_id ]['activity_id'],
			),

			// WHERE sanitization format
			array(
				'%s',
			)
		);

		wp_cache_delete( $user_id, 'bp_last_activity' );

		return $deleted;
	}
}
