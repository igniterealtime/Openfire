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
 * BuddyPress User Query class.
 *
 * Used for querying users in a BuddyPress context, in situations where WP_User_Query won't do the trick:
 * Member directories, the Friends component, etc.
 *
 * @since 1.7.0
 *
 * @param array $query {
 *     Query arguments. All items are optional.
 *     @type string            $type                Determines sort order. Select from 'newest', 'active', 'online',
 *                                                  'random', 'popular', 'alphabetical'. Default: 'newest'.
 *     @type int               $per_page            Number of results to return. Default: 0 (no limit).
 *     @type int               $page                Page offset (together with $per_page). Default: 1.
 *     @type int               $user_id             ID of a user. If present, and if the friends component is activated,
 *                                                  results will be limited to the friends of that user. Default: 0.
 *     @type string|bool       $search_terms        Terms to search by. Search happens across xprofile fields. Requires
 *                                                  XProfile component. Default: false.
 *     @type string            $search_wildcard     When searching with $search_terms, set where wildcards around the
 *                                                  term should be positioned. Accepts 'both', 'left', 'right'.
 *                                                  Default: 'both'.
 *     @type array|string|bool $include             An array or comma-separated list of user IDs to which query should
 *                                                  be limited. Default: false.
 *     @type array|string|bool $exclude             An array or comma-separated list of user IDs that will be excluded
 *                                                  from query results. Default: false.
 *     @type array|string|bool $user_ids            An array or comma-separated list of IDs corresponding to the users
 *                                                  that should be returned. When this parameter is passed, it will
 *                                                  override all others; BP User objects will be constructed using these
 *                                                  IDs only. Default: false.
 *     @type array|string      $member_type         Array or comma-separated list of member types to limit results to.
 *     @type array|string      $member_type__in     Array or comma-separated list of member types to limit results to.
 *     @type array|string      $member_type__not_in Array or comma-separated list of member types that will be
 *			                                             excluded from results.
 *     @type string|bool       $meta_key            Limit results to users that have usermeta associated with this meta_key.
 *                                                  Usually used with $meta_value. Default: false.
 *     @type string|bool       $meta_value          When used with $meta_key, limits results to users whose usermeta value
 *                                                  associated with $meta_key matches $meta_value. Default: false.
 *     @type array             $xprofile_query      Filter results by xprofile data. Requires the xprofile component.
 *                                                  See {@see BP_XProfile_Query} for details.
 *     @type bool              $populate_extras     True if you want to fetch extra metadata
 *                                                  about returned users, such as total group and friend counts.
 *     @type string            $count_total         Determines how BP_User_Query will do a count of total users matching
 *                                                  the other filter criteria. Default value is 'count_query', which
 *                                                  does a separate SELECT COUNT query to determine the total.
 *                                                  'sql_count_found_rows' uses SQL_COUNT_FOUND_ROWS and
 *                                                  SELECT FOUND_ROWS(). Pass an empty string to skip the total user
 *                                                  count query.
 * }
 */
class BP_User_Query {

	/** Variables *************************************************************/

	/**
	 * Unaltered params as passed to the constructor.
	 *
	 * @since 1.8.0
	 * @var array
	 */
	public $query_vars_raw = array();

	/**
	 * Array of variables to query with.
	 *
	 * @since 1.7.0
	 * @var array
	 */
	public $query_vars = array();

	/**
	 * List of found users and their respective data.
	 *
	 * @since 1.7.0
	 * @var array
	 */
	public $results = array();

	/**
	 * Total number of found users for the current query.
	 *
	 * @since 1.7.0
	 * @var int
	 */
	public $total_users = 0;

	/**
	 * List of found user IDs.
	 *
	 * @since 1.7.0
	 * @var array
	 */
	public $user_ids = array();

	/**
	 * SQL clauses for the user ID query.
	 *
	 * @since 1.7.0
	 * @var array
	 */
	public $uid_clauses = array();

	/**
	 * SQL table where the user ID is being fetched from.
	 *
	 * @since 2.2.0
	 * @var string
	 */
	public $uid_table = '';

	/**
	 * SQL database column name to order by.
	 *
	 * @since 1.7.0
	 * @var string
	 */
	public $uid_name = '';

	/**
	 * Standard response when the query should not return any rows.
	 *
	 * @since 1.7.0
	 * @var string
	 */
	protected $no_results = array( 'join' => '', 'where' => '0 = 1' );


	/** Methods ***************************************************************/

	/**
	 * Constructor.
	 *
	 * @since 1.7.0
	 *
	 * @param string|array $query See {@link BP_User_Query}.
	 */
	public function __construct( $query = null ) {

		// Store the raw query vars for later access
		$this->query_vars_raw = $query;

		// Allow extending classes to register action/filter hooks
		$this->setup_hooks();

		if ( ! empty( $this->query_vars_raw ) ) {
			$this->query_vars = wp_parse_args( $this->query_vars_raw, array(
				'type'                => 'newest',
				'per_page'            => 0,
				'page'                => 1,
				'user_id'             => 0,
				'search_terms'        => false,
				'search_wildcard'     => 'both',
				'include'             => false,
				'exclude'             => false,
				'user_ids'            => false,
				'member_type'         => '',
				'member_type__in'     => '',
				'member_type__not_in' => '',
				'meta_key'            => false,
				'meta_value'          => false,
				'xprofile_query'      => false,
				'populate_extras'     => true,
				'count_total'         => 'count_query'
			) );

			/**
			 * Fires before the construction of the BP_User_Query query.
			 *
			 * @since 1.7.0
			 *
			 * @param BP_User_Query $this Current instance of the BP_User_Query. Passed by reference.
			 */
			do_action_ref_array( 'bp_pre_user_query_construct', array( &$this ) );

			// Get user ids
			// If the user_ids param is present, we skip the query
			if ( false !== $this->query_vars['user_ids'] ) {
				$this->user_ids = wp_parse_id_list( $this->query_vars['user_ids'] );
			} else {
				$this->prepare_user_ids_query();
				$this->do_user_ids_query();
			}
		}

		// Bail if no user IDs were found
		if ( empty( $this->user_ids ) ) {
			return;
		}

		// Fetch additional data. First, using WP_User_Query
		$this->do_wp_user_query();

		// Get BuddyPress specific user data
		$this->populate_extras();
	}

	/**
	 * Allow extending classes to set up action/filter hooks.
	 *
	 * When extending BP_User_Query, you may need to use some of its
	 * internal hooks to modify the output. It's not convenient to call
	 * add_action() or add_filter() in your class constructor, because
	 * BP_User_Query::__construct() contains a fair amount of logic that
	 * you may not want to override in your class. Define this method in
	 * your own class if you need a place where your extending class can
	 * add its hooks early in the query-building process. See
	 * {@link BP_Group_Member_Query::setup_hooks()} for an example.
	 *
	 * @since 1.8.0
	 */
	public function setup_hooks() {}

	/**
	 * Prepare the query for user_ids.
	 *
	 * @since 1.7.0
	 */
	public function prepare_user_ids_query() {
		global $wpdb;

		$bp = buddypress();

		// Default query variables used here
		$type         = '';
		$per_page     = 0;
		$page         = 1;
		$user_id      = 0;
		$include      = false;
		$search_terms = false;
		$exclude      = false;
		$meta_key     = false;
		$meta_value   = false;

		extract( $this->query_vars );

		// Setup the main SQL query container
		$sql = array(
			'select'  => '',
			'where'   => array(),
			'orderby' => '',
			'order'   => '',
			'limit'   => ''
		);

		/** TYPE **************************************************************/

		// Determines the sort order, which means it also determines where the
		// user IDs are drawn from (the SELECT and WHERE statements)
		switch ( $type ) {

			// 'online' query happens against the last_activity usermeta key
			// Filter 'bp_user_query_online_interval' to modify the
			// number of minutes used as an interval
			case 'online' :
				$this->uid_name = 'user_id';
				$this->uid_table = $bp->members->table_name_last_activity;
				$sql['select']  = "SELECT u.{$this->uid_name} as id FROM {$this->uid_table} u";
				$sql['where'][] = $wpdb->prepare( "u.component = %s AND u.type = 'last_activity'", buddypress()->members->id );

				/**
				 * Filters the threshold for activity timestamp minutes since to indicate online status.
				 *
				 * @since 1.8.0
				 *
				 * @param int $value Amount of minutes for threshold. Default 15.
				 */
				$sql['where'][] = $wpdb->prepare( "u.date_recorded >= DATE_SUB( UTC_TIMESTAMP(), INTERVAL %d MINUTE )", apply_filters( 'bp_user_query_online_interval', 15 ) );
				$sql['orderby'] = "ORDER BY u.date_recorded";
				$sql['order']   = "DESC";

				break;

			// 'active', 'newest', and 'random' queries
			// all happen against the last_activity usermeta key
			case 'active' :
			case 'newest' :
			case 'random' :
				$this->uid_name = 'user_id';
				$this->uid_table = $bp->members->table_name_last_activity;
				$sql['select']  = "SELECT u.{$this->uid_name} as id FROM {$this->uid_table} u";
				$sql['where'][] = $wpdb->prepare( "u.component = %s AND u.type = 'last_activity'", buddypress()->members->id );

				if ( 'newest' == $type ) {
					$sql['orderby'] = "ORDER BY u.user_id";
					$sql['order'] = "DESC";
				} elseif ( 'random' == $type ) {
					$sql['orderby'] = "ORDER BY rand()";
				} else {
					$sql['orderby'] = "ORDER BY u.date_recorded";
					$sql['order'] = "DESC";
				}

				break;

			// 'popular' sorts by the 'total_friend_count' usermeta
			case 'popular' :
				$this->uid_name = 'user_id';
				$this->uid_table = $wpdb->usermeta;
				$sql['select']  = "SELECT u.{$this->uid_name} as id FROM {$this->uid_table} u";
				$sql['where'][] = $wpdb->prepare( "u.meta_key = %s", bp_get_user_meta_key( 'total_friend_count' ) );
				$sql['orderby'] = "ORDER BY CONVERT(u.meta_value, SIGNED)";
				$sql['order']   = "DESC";

				break;

			// 'alphabetical' sorts depend on the xprofile setup
			case 'alphabetical' :

				// We prefer to do alphabetical sorts against the display_name field
				// of wp_users, because the table is smaller and better indexed. We
				// can do so if xprofile sync is enabled, or if xprofile is inactive.
				//
				// @todo remove need for bp_is_active() check
				if ( ! bp_disable_profile_sync() || ! bp_is_active( 'xprofile' ) ) {
					$this->uid_name = 'ID';
					$this->uid_table = $wpdb->users;
					$sql['select']  = "SELECT u.{$this->uid_name} as id FROM {$this->uid_table} u";
					$sql['orderby'] = "ORDER BY u.display_name";
					$sql['order']   = "ASC";

				// When profile sync is disabled, alphabetical sorts must happen against
				// the xprofile table
				} else {
					$this->uid_name = 'user_id';
					$this->uid_table = $bp->profile->table_name_data;
					$sql['select']  = "SELECT u.{$this->uid_name} as id FROM {$this->uid_table} u";
					$sql['where'][] = $wpdb->prepare( "u.field_id = %d", bp_xprofile_fullname_field_id() );
					$sql['orderby'] = "ORDER BY u.value";
					$sql['order']   = "ASC";
				}

				// Alphabetical queries ignore last_activity, while BP uses last_activity
				// to infer spam/deleted/non-activated users. To ensure that these users
				// are filtered out, we add an appropriate sub-query.
				$sql['where'][] = "u.{$this->uid_name} IN ( SELECT ID FROM {$wpdb->users} WHERE " . bp_core_get_status_sql( '' ) . " )";

				break;

			// Any other 'type' falls through
			default :
				$this->uid_name = 'ID';
				$this->uid_table = $wpdb->users;
				$sql['select']  = "SELECT u.{$this->uid_name} as id FROM {$this->uid_table} u";

				// In this case, we assume that a plugin is
				// handling order, so we leave those clauses
				// blank

				break;
		}

		/** WHERE *************************************************************/

		// 'include' - User ids to include in the results
		$include     = false !== $include ? wp_parse_id_list( $include ) : array();
		$include_ids = $this->get_include_ids( $include );
		if ( ! empty( $include_ids ) ) {
			$include_ids    = implode( ',', wp_parse_id_list( $include_ids ) );
			$sql['where'][] = "u.{$this->uid_name} IN ({$include_ids})";
		}

		// 'exclude' - User ids to exclude from the results
		if ( false !== $exclude ) {
			$exclude_ids    = implode( ',', wp_parse_id_list( $exclude ) );
			$sql['where'][] = "u.{$this->uid_name} NOT IN ({$exclude_ids})";
		}

		// 'user_id' - When a user id is passed, limit to the friends of the user
		// @todo remove need for bp_is_active() check
		if ( ! empty( $user_id ) && bp_is_active( 'friends' ) ) {
			$friend_ids = friends_get_friend_user_ids( $user_id );
			$friend_ids = implode( ',', wp_parse_id_list( $friend_ids ) );

			if ( ! empty( $friend_ids ) ) {
				$sql['where'][] = "u.{$this->uid_name} IN ({$friend_ids})";

			// If the user has no friends, the query should always
			// return no users
			} else {
				$sql['where'][] = $this->no_results['where'];
			}
		}

		/** Search Terms ******************************************************/

		// 'search_terms' searches user_login and user_nicename
		// xprofile field matches happen in bp_xprofile_bp_user_query_search()
		if ( false !== $search_terms ) {
			$search_terms = bp_esc_like( wp_kses_normalize_entities( $search_terms ) );

			if ( $search_wildcard === 'left' ) {
				$search_terms_nospace = '%' . $search_terms;
				$search_terms_space   = '%' . $search_terms . ' %';
			} elseif ( $search_wildcard === 'right' ) {
				$search_terms_nospace =        $search_terms . '%';
				$search_terms_space   = '% ' . $search_terms . '%';
			} else {
				$search_terms_nospace = '%' . $search_terms . '%';
				$search_terms_space   = '%' . $search_terms . '%';
			}

			$sql['where']['search'] = $wpdb->prepare(
				"u.{$this->uid_name} IN ( SELECT ID FROM {$wpdb->users} WHERE ( user_login LIKE %s OR user_login LIKE %s OR user_nicename LIKE %s OR user_nicename LIKE %s ) )",
				$search_terms_nospace,
				$search_terms_space,
				$search_terms_nospace,
				$search_terms_space
			);
		}

		// Only use $member_type__in if $member_type is not set.
		if ( empty( $member_type ) && ! empty( $member_type__in ) ) {
			$member_type = $member_type__in;
		}

		// Member types to exclude. Note that this takes precedence over inclusions.
		if ( ! empty( $member_type__not_in ) ) {
			$member_type_clause = $this->get_sql_clause_for_member_types( $member_type__not_in, 'NOT IN' );

		// Member types to include.
		} elseif ( ! empty( $member_type ) ) {
			$member_type_clause = $this->get_sql_clause_for_member_types( $member_type, 'IN' );
		}

		if ( ! empty( $member_type_clause ) ) {
			$sql['where']['member_type'] = $member_type_clause;
		}

		// 'meta_key', 'meta_value' allow usermeta search
		// To avoid global joins, do a separate query
		if ( false !== $meta_key ) {
			$meta_sql = $wpdb->prepare( "SELECT user_id FROM {$wpdb->usermeta} WHERE meta_key = %s", $meta_key );

			if ( false !== $meta_value ) {
				$meta_sql .= $wpdb->prepare( " AND meta_value = %s", $meta_value );
			}

			$found_user_ids = $wpdb->get_col( $meta_sql );

			if ( ! empty( $found_user_ids ) ) {
				$sql['where'][] = "u.{$this->uid_name} IN (" . implode( ',', wp_parse_id_list( $found_user_ids ) ) . ")";
			} else {
				$sql['where'][] = '1 = 0';
			}
		}

		// 'per_page', 'page' - handles LIMIT
		if ( !empty( $per_page ) && !empty( $page ) ) {
			$sql['limit'] = $wpdb->prepare( "LIMIT %d, %d", intval( ( $page - 1 ) * $per_page ), intval( $per_page ) );
		} else {
			$sql['limit'] = '';
		}

		/**
		 * Filters the clauses for the user query.
		 *
		 * @since 2.0.0
		 *
		 * @param array         $sql  Array of SQL clauses to be used in the query.
		 * @param BP_User_Query $this Current BP_User_Query instance.
		 */
		$sql = apply_filters_ref_array( 'bp_user_query_uid_clauses', array( $sql, &$this ) );

		// Assemble the query chunks
		$this->uid_clauses['select']  = $sql['select'];
		$this->uid_clauses['where']   = ! empty( $sql['where'] ) ? 'WHERE ' . implode( ' AND ', $sql['where'] ) : '';
		$this->uid_clauses['orderby'] = $sql['orderby'];
		$this->uid_clauses['order']   = $sql['order'];
		$this->uid_clauses['limit']   = $sql['limit'];

		/**
		 * Fires before the BP_User_Query query is made.
		 *
		 * @since 1.7.0
		 *
		 * @param BP_User_Query $this Current BP_User_Query instance. Passed by reference.
		 */
		do_action_ref_array( 'bp_pre_user_query', array( &$this ) );
	}

	/**
	 * Query for IDs of users that match the query parameters.
	 *
	 * Perform a database query to specifically get only user IDs, using
	 * existing query variables set previously in the constructor.
	 *
	 * Also used to quickly perform user total counts.
	 *
	 * @since 1.7.0
	 */
	public function do_user_ids_query() {
		global $wpdb;

		// If counting using SQL_CALC_FOUND_ROWS, set it up here
		if ( 'sql_calc_found_rows' == $this->query_vars['count_total'] ) {
			$this->uid_clauses['select'] = str_replace( 'SELECT', 'SELECT SQL_CALC_FOUND_ROWS', $this->uid_clauses['select'] );
		}

		// Get the specific user ids
		$this->user_ids = $wpdb->get_col( "{$this->uid_clauses['select']} {$this->uid_clauses['where']} {$this->uid_clauses['orderby']} {$this->uid_clauses['order']} {$this->uid_clauses['limit']}" );

		// Get the total user count
		if ( 'sql_calc_found_rows' == $this->query_vars['count_total'] ) {

			/**
			 * Filters the found user SQL statements before query.
			 *
			 * If "sql_calc_found_rows" is the provided count_total query var
			 * then the value will be "SELECT FOUND_ROWS()". Otherwise it will
			 * use a "SELECT COUNT()" query statement.
			 *
			 * @since 1.7.0
			 *
			 * @param string        $value SQL statement to select FOUND_ROWS().
			 * @param BP_User_Query $this  Current BP_User_Query instance.
			 */
			$this->total_users = $wpdb->get_var( apply_filters( 'bp_found_user_query', "SELECT FOUND_ROWS()", $this ) );
		} elseif ( 'count_query' == $this->query_vars['count_total'] ) {
			$count_select      = preg_replace( '/^SELECT.*?FROM (\S+) u/', "SELECT COUNT(u.{$this->uid_name}) FROM $1 u", $this->uid_clauses['select'] );

			/** This filter is documented in bp-core/classes/class-bp-user-query.php */
			$this->total_users = $wpdb->get_var( apply_filters( 'bp_found_user_query', "{$count_select} {$this->uid_clauses['where']}", $this ) );
		}
	}

	/**
	 * Use WP_User_Query() to pull data for the user IDs retrieved in the main query.
	 *
	 * @since 1.7.0
	 */
	public function do_wp_user_query() {
		$fields = array( 'ID', 'user_login', 'user_pass', 'user_nicename', 'user_email', 'user_url', 'user_registered', 'user_activation_key', 'user_status', 'display_name' );

		if ( is_multisite() ) {
			$fields[] = 'spam';
			$fields[] = 'deleted';
		}

		/**
		 * Filters the WP User Query arguments before passing into the class.
		 *
		 * @since 1.7.0
		 *
		 * @param array         $value Array of arguments for the user query.
		 * @param BP_User_Query $this  Current BP_User_Query instance.
		 */
		$wp_user_query = new WP_User_Query( apply_filters( 'bp_wp_user_query_args', array(

			// Relevant
			'fields'      => $fields,
			'include'     => $this->user_ids,

			// Overrides
			'blog_id'     => 0,    // BP does not require blog roles
			'count_total' => false // We already have a count

		), $this ) );

		// WP_User_Query doesn't cache the data it pulls from wp_users,
		// and it does not give us a way to save queries by fetching
		// only uncached users. However, BP does cache this data, so
		// we set it here.
		foreach ( $wp_user_query->results as $u ) {
			wp_cache_set( 'bp_core_userdata_' . $u->ID, $u, 'bp' );
		}

		// We calculate total_users using a standalone query, except
		// when a whitelist of user_ids is passed to the constructor.
		// This clause covers the latter situation, and ensures that
		// pagination works when querying by $user_ids.
		if ( empty( $this->total_users ) ) {
			$this->total_users = count( $wp_user_query->results );
		}

		// Reindex for easier matching
		$r = array();
		foreach ( $wp_user_query->results as $u ) {
			$r[ $u->ID ] = $u;
		}

		// Match up to the user ids from the main query
		foreach ( $this->user_ids as $key => $uid ) {
			if ( isset( $r[ $uid ] ) ) {
				$this->results[ $uid ] = $r[ $uid ];

				// The BP template functions expect an 'id'
				// (as opposed to 'ID') property
				$this->results[ $uid ]->id = $uid;

			// remove user ID from original user_ids property
			} else {
				unset( $this->user_ids[ $key ] );
			}
		}
	}

	/**
	 * Fetch the IDs of users to put in the IN clause of the main query.
	 *
	 * By default, returns the value passed to it
	 * ($this->query_vars['include']). Having this abstracted into a
	 * standalone method means that extending classes can override the
	 * logic, parsing together their own user_id limits with the 'include'
	 * ids passed to the class constructor. See {@link BP_Group_Member_Query}
	 * for an example.
	 *
	 * @since 1.8.0
	 *
	 * @param array $include Sanitized array of user IDs, as passed to the 'include'
	 *                       parameter of the class constructor.
	 *
	 * @return array The list of users to which the main query should be
	 *               limited.
	 */
	public function get_include_ids( $include = array() ) {
		return $include;
	}

	/**
	 * Perform a database query to populate any extra metadata we might need.
	 *
	 * Different components will hook into the 'bp_user_query_populate_extras'
	 * action to loop in the things they want.
	 *
	 * @since 1.7.0
	 *
	 * @global WPDB $wpdb Global WordPress database access object.
	 */
	public function populate_extras() {
		global $wpdb;

		// Bail if no users
		if ( empty( $this->user_ids ) || empty( $this->results ) ) {
			return;
		}

		// Bail if the populate_extras flag is set to false
		// In the case of the 'popular' sort type, we force
		// populate_extras to true, because we need the friend counts
		if ( 'popular' == $this->query_vars['type'] ) {
			$this->query_vars['populate_extras'] = 1;
		}

		if ( ! (bool) $this->query_vars['populate_extras'] ) {
			return;
		}

		// Turn user ID's into a query-usable, comma separated value
		$user_ids_sql = implode( ',', wp_parse_id_list( $this->user_ids ) );

		/**
		 * Allows users to independently populate custom extras.
		 *
		 * Note that anything you add here should query using $user_ids_sql, to
		 * avoid running multiple queries per user in the loop.
		 *
		 * Two BuddyPress components currently do this:
		 * - XProfile: To override display names.
		 * - Friends:  To set whether or not a user is the current users friend.
		 *
		 * @see bp_xprofile_filter_user_query_populate_extras()
		 * @see bp_friends_filter_user_query_populate_extras()
		 *
		 * @since 1.7.0
		 *
		 * @param BP_User_Query $this         Current BP_User_Query instance.
		 * @param string        $user_ids_sql Comma-separated string of user IDs.
		 */
		do_action_ref_array( 'bp_user_query_populate_extras', array( $this, $user_ids_sql ) );

		// Fetch last_active data from the activity table
		$last_activities = BP_Core_User::get_last_activity( $this->user_ids );

		// Set a last_activity value for each user, even if it's empty
		foreach ( $this->results as $user_id => $user ) {
			$user_last_activity = isset( $last_activities[ $user_id ] ) ? $last_activities[ $user_id ]['date_recorded'] : '';
			$this->results[ $user_id ]->last_activity = $user_last_activity;
		}

		// Fetch usermeta data
		// We want the three following pieces of info from usermeta:
		// - friend count
		// - latest update
		$total_friend_count_key = bp_get_user_meta_key( 'total_friend_count' );
		$bp_latest_update_key   = bp_get_user_meta_key( 'bp_latest_update'   );

		// total_friend_count must be set for each user, even if its
		// value is 0
		foreach ( $this->results as $uindex => $user ) {
			$this->results[$uindex]->total_friend_count = 0;
		}

		// Create, prepare, and run the separate usermeta query
		$user_metas = $wpdb->get_results( $wpdb->prepare( "SELECT user_id, meta_key, meta_value FROM {$wpdb->usermeta} WHERE meta_key IN (%s,%s) AND user_id IN ({$user_ids_sql})", $total_friend_count_key, $bp_latest_update_key ) );

		// The $members_template global expects the index key to be different
		// from the meta_key in some cases, so we rejig things here.
		foreach ( $user_metas as $user_meta ) {
			switch ( $user_meta->meta_key ) {
				case $total_friend_count_key :
					$key = 'total_friend_count';
					break;

				case $bp_latest_update_key :
					$key = 'latest_update';
					break;
			}

			if ( isset( $this->results[ $user_meta->user_id ] ) ) {
				$this->results[ $user_meta->user_id ]->{$key} = $user_meta->meta_value;
			}
		}

		// When meta_key or meta_value have been passed to the query,
		// fetch the resulting values for use in the template functions
		if ( ! empty( $this->query_vars['meta_key'] ) ) {
			$meta_sql = array(
				'select' => "SELECT user_id, meta_key, meta_value",
				'from'   => "FROM $wpdb->usermeta",
				'where'  => $wpdb->prepare( "WHERE meta_key = %s", $this->query_vars['meta_key'] )
			);

			if ( false !== $this->query_vars['meta_value'] ) {
				$meta_sql['where'] .= $wpdb->prepare( " AND meta_value = %s", $this->query_vars['meta_value'] );
			}

			$metas = $wpdb->get_results( "{$meta_sql['select']} {$meta_sql['from']} {$meta_sql['where']}" );

			if ( ! empty( $metas ) ) {
				foreach ( $metas as $meta ) {
					if ( isset( $this->results[ $meta->user_id ] ) ) {
						$this->results[ $meta->user_id ]->meta_key = $meta->meta_key;

						if ( ! empty( $meta->meta_value ) ) {
							$this->results[ $meta->user_id ]->meta_value = $meta->meta_value;
						}
					}
				}
			}
		}
	}

	/**
	 * Get a SQL clause representing member_type include/exclusion.
	 *
	 * @since 2.4.0
	 *
	 * @param string|array $member_types Array or comma-separated list of member types.
	 * @param string       $operator     'IN' or 'NOT IN'.
	 *
	 * @return string
	 */
	protected function get_sql_clause_for_member_types( $member_types, $operator ) {
		global $wpdb;

		// Sanitize.
		if ( 'NOT IN' !== $operator ) {
			$operator = 'IN';
		}

		// Parse and sanitize types.
		if ( ! is_array( $member_types ) ) {
			$member_types = preg_split( '/[,\s+]/', $member_types );
		}

		$types = array();
		foreach ( $member_types as $mt ) {
			if ( bp_get_member_type_object( $mt ) ) {
				$types[] = $mt;
			}
		}

		$tax_query = new WP_Tax_Query( array(
			array(
				'taxonomy' => 'bp_member_type',
				'field'    => 'name',
				'operator' => $operator,
				'terms'    => $types,
			),
		) );

		// Switch to the root blog, where member type taxonomies live.
		$switched = false;
		if ( ! bp_is_root_blog() ) {
			switch_to_blog( bp_get_root_blog_id() );
			$switched = true;
		}

		$sql_clauses = $tax_query->get_sql( 'u', $this->uid_name );

		if ( $switched ) {
			restore_current_blog();
		}

		$clause = '';

		// no_results clauses are the same between IN and NOT IN.
		if ( false !== strpos( $sql_clauses['where'], '0 = 1' ) ) {
			$clause = $this->no_results['where'];

		// The tax_query clause generated for NOT IN can be used almost as-is. We just trim the leading 'AND'.
		} elseif ( 'NOT IN' === $operator ) {
			$clause = preg_replace( '/^\s*AND\s*/', '', $sql_clauses['where'] );

		// IN clauses must be converted to a subquery.
		} elseif ( preg_match( '/' . $wpdb->term_relationships . '\.term_taxonomy_id IN \([0-9, ]+\)/', $sql_clauses['where'], $matches ) ) {
			$clause = "u.{$this->uid_name} IN ( SELECT object_id FROM $wpdb->term_relationships WHERE {$matches[0]} )";
		}

		return $clause;
	}
}
