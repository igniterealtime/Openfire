<?php
/**
 * BuddyPress Groups Classes.
 *
 * @package BuddyPress
 * @subpackage GroupsClasses
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * BuddyPress Group object.
 */
class BP_Groups_Group {

	/**
	 * ID of the group.
	 *
	 * @var int
	 */
	public $id;

	/**
	 * User ID of the group's creator.
	 *
	 * @var int
	 */
	public $creator_id;

	/**
	 * Name of the group.
	 *
	 * @var string
	 */
	public $name;

	/**
	 * Group slug.
	 *
	 * @var string
	 */
	public $slug;

	/**
	 * Group description.
	 *
	 * @var string
	 */
	public $description;

	/**
	 * Group status.
	 *
	 * Core statuses are 'public', 'private', and 'hidden'.
	 *
	 * @var string
	 */
	public $status;

	/**
	 * Should (legacy) bbPress forums be enabled for this group?
	 *
	 * @var int
	 */
	public $enable_forum;

	/**
	 * Date the group was created.
	 *
	 * @var string
	 */
	public $date_created;

	/**
	 * Data about the group's admins.
	 *
	 * @var array
	 */
	public $admins;

	/**
	 * Data about the group's moderators.
	 *
	 * @var array
	 */
	public $mods;

	/**
	 * Total count of group members.
	 *
	 * @var int
	 */
	public $total_member_count;

	/**
	 * Is the current user a member of this group?
	 *
	 * @since 1.2.0
	 * @var bool
	 */
	public $is_member;

	/**
	 * Does the current user have an outstanding invitation to this group?
	 *
	 * @since 1.9.0
	 * @var bool
	 */
	public $is_invited;

	/**
	 * Does the current user have a pending membership request to this group?
	 *
	 * @since 1.9.0
	 * @var bool
	 */
	public $is_pending;

	/**
	 * Timestamp of the last activity that happened in this group.
	 *
	 * @since 1.2.0
	 * @var string
	 */
	public $last_activity;

	/**
	 * If this is a private or hidden group, does the current user have access?
	 *
	 * @since 1.6.0
	 * @var bool
	 */
	public $user_has_access;

	/**
	 * Raw arguments passed to the constructor.
	 *
	 * @since 2.0.0
	 * @var array
	 */
	public $args;

	/**
	 * Constructor method.
	 *
	 * @param int|null $id Optional. If the ID of an existing group is provided,
	 *                     the object will be pre-populated with info about that group.
	 * @param array    $args {
	 *     Array of optional arguments.
	 *     @type bool $populate_extras Whether to fetch "extra" data about the group
	 *                                 (group admins/mods, access for the current user).
	 *                                 Default: false.
	 * }
	 */
	public function __construct( $id = null, $args = array() ) {
		$this->args = wp_parse_args( $args, array(
			'populate_extras' => false,
		) );

		if ( !empty( $id ) ) {
			$this->id = $id;
			$this->populate();
		}
	}

	/**
	 * Set up data about the current group.
	 */
	public function populate() {
		global $wpdb;

		// Get BuddyPress
		$bp    = buddypress();

		// Check cache for group data
		$group = wp_cache_get( $this->id, 'bp_groups' );

		// Cache missed, so query the DB
		if ( false === $group ) {
			$group = $wpdb->get_row( $wpdb->prepare( "SELECT g.* FROM {$bp->groups->table_name} g WHERE g.id = %d", $this->id ) );

			wp_cache_set( $this->id, $group, 'bp_groups' );
		}

		// No group found so set the ID and bail
		if ( empty( $group ) || is_wp_error( $group ) ) {
			$this->id = 0;
			return;
		}

		// Group found so setup the object variables
		$this->id           = $group->id;
		$this->creator_id   = $group->creator_id;
		$this->name         = stripslashes( $group->name );
		$this->slug         = $group->slug;
		$this->description  = stripslashes( $group->description );
		$this->status       = $group->status;
		$this->enable_forum = $group->enable_forum;
		$this->date_created = $group->date_created;

		// Are we getting extra group data?
		if ( ! empty( $this->args['populate_extras'] ) ) {

			/**
			 * Filters the SQL prepared statement used to fetch group admins and mods.
			 *
			 * @since 1.5.0
			 *
			 * @param string $value SQL select statement used to fetch admins and mods.
			 */
			$admin_mods = $wpdb->get_results( apply_filters( 'bp_group_admin_mods_user_join_filter', $wpdb->prepare( "SELECT u.ID as user_id, u.user_login, u.user_email, u.user_nicename, m.is_admin, m.is_mod FROM {$wpdb->users} u, {$bp->groups->table_name_members} m WHERE u.ID = m.user_id AND m.group_id = %d AND ( m.is_admin = 1 OR m.is_mod = 1 )", $this->id ) ) );

			// Add admins and moderators to their respective arrays
			foreach ( (array) $admin_mods as $user ) {
				if ( !empty( $user->is_admin ) ) {
					$this->admins[] = $user;
				} else {
					$this->mods[] = $user;
				}
			}

			// Set up some specific group vars from meta. Excluded
			// from the bp_groups cache because it's cached independently
			$this->last_activity      = groups_get_groupmeta( $this->id, 'last_activity' );
			$this->total_member_count = groups_get_groupmeta( $this->id, 'total_member_count' );

			// Set user-specific data
			$user_id          = bp_loggedin_user_id();
			$this->is_member  = BP_Groups_Member::check_is_member( $user_id, $this->id );
			$this->is_invited = BP_Groups_Member::check_has_invite( $user_id, $this->id );
			$this->is_pending = BP_Groups_Member::check_for_membership_request( $user_id, $this->id );

			// If this is a private or hidden group, does the current user have access?
			if ( ( 'private' === $this->status ) || ( 'hidden' === $this->status ) ) {

				// Assume user does not have access to hidden/private groups
				$this->user_has_access = false;

				// Group members or community moderators have access
				if ( ( $this->is_member && is_user_logged_in() ) || bp_current_user_can( 'bp_moderate' ) ) {
					$this->user_has_access = true;
				}
			} else {
				$this->user_has_access = true;
			}
		}
	}

	/**
	 * Save the current group to the database.
	 *
	 * @return bool True on success, false on failure.
	 */
	public function save() {
		global $wpdb;

		$bp = buddypress();

		$this->creator_id   = apply_filters( 'groups_group_creator_id_before_save',   $this->creator_id,   $this->id );
		$this->name         = apply_filters( 'groups_group_name_before_save',         $this->name,         $this->id );
 		$this->slug         = apply_filters( 'groups_group_slug_before_save',         $this->slug,         $this->id );
		$this->description  = apply_filters( 'groups_group_description_before_save',  $this->description,  $this->id );
 		$this->status       = apply_filters( 'groups_group_status_before_save',       $this->status,       $this->id );
		$this->enable_forum = apply_filters( 'groups_group_enable_forum_before_save', $this->enable_forum, $this->id );
		$this->date_created = apply_filters( 'groups_group_date_created_before_save', $this->date_created, $this->id );

		/**
		 * Fires before the current group item gets saved.
		 *
		 * Please use this hook to filter the properties above. Each part will be passed in.
		 *
		 * @since 1.0.0
		 *
		 * @param BP_Groups_Group $this Current instance of the group item being saved. Passed by reference.
		 */
		do_action_ref_array( 'groups_group_before_save', array( &$this ) );

		// Groups need at least a name
		if ( empty( $this->name ) ) {
			return false;
		}

		// Set slug with group title if not passed
		if ( empty( $this->slug ) ) {
			$this->slug = sanitize_title( $this->name );
		}

		// Sanity check
		if ( empty( $this->slug ) ) {
			return false;
		}

		// Check for slug conflicts if creating new group
		if ( empty( $this->id ) ) {
			$this->slug = groups_check_slug( $this->slug );
		}

		if ( !empty( $this->id ) ) {
			$sql = $wpdb->prepare(
				"UPDATE {$bp->groups->table_name} SET
					creator_id = %d,
					name = %s,
					slug = %s,
					description = %s,
					status = %s,
					enable_forum = %d,
					date_created = %s
				WHERE
					id = %d
				",
					$this->creator_id,
					$this->name,
					$this->slug,
					$this->description,
					$this->status,
					$this->enable_forum,
					$this->date_created,
					$this->id
			);
		} else {
			$sql = $wpdb->prepare(
				"INSERT INTO {$bp->groups->table_name} (
					creator_id,
					name,
					slug,
					description,
					status,
					enable_forum,
					date_created
				) VALUES (
					%d, %s, %s, %s, %s, %d, %s
				)",
					$this->creator_id,
					$this->name,
					$this->slug,
					$this->description,
					$this->status,
					$this->enable_forum,
					$this->date_created
			);
		}

		if ( false === $wpdb->query($sql) )
			return false;

		if ( empty( $this->id ) )
			$this->id = $wpdb->insert_id;

		/**
		 * Fires after the current group item has been saved.
		 *
		 * @since 1.0.0
		 *
		 * @param BP_Groups_Group $this Current instance of the group item that was saved. Passed by reference.
		 */
		do_action_ref_array( 'groups_group_after_save', array( &$this ) );

		wp_cache_delete( $this->id, 'bp_groups' );

		return true;
	}

	/**
	 * Delete the current group.
	 *
	 * @return bool True on success, false on failure.
	 */
	public function delete() {
		global $wpdb;

		// Delete groupmeta for the group
		groups_delete_groupmeta( $this->id );

		// Fetch the user IDs of all the members of the group
		$user_ids    = BP_Groups_Member::get_group_member_ids( $this->id );
		$user_id_str = esc_sql( implode( ',', wp_parse_id_list( $user_ids ) ) );

		// Modify group count usermeta for members
		$wpdb->query( "UPDATE {$wpdb->usermeta} SET meta_value = meta_value - 1 WHERE meta_key = 'total_group_count' AND user_id IN ( {$user_id_str} )" );

		// Now delete all group member entries
		BP_Groups_Member::delete_all( $this->id );

		/**
		 * Fires before the deletion of a group.
		 *
		 * @since 1.2.0
		 *
		 * @param BP_Groups_Group $this     Current instance of the group item being deleted. Passed by reference.
		 * @param array           $user_ids Array of user IDs that were members of the group.
		 */
		do_action_ref_array( 'bp_groups_delete_group', array( &$this, $user_ids ) );

		wp_cache_delete( $this->id, 'bp_groups' );

		$bp = buddypress();

		// Finally remove the group entry from the DB
		if ( !$wpdb->query( $wpdb->prepare( "DELETE FROM {$bp->groups->table_name} WHERE id = %d", $this->id ) ) )
			return false;

		return true;
	}

	/** Static Methods ****************************************************/

	/**
	 * Get whether a group exists for a given slug.
	 *
	 * @param string      $slug       Slug to check.
	 * @param string|bool $table_name Optional. Name of the table to check
	 *                                against. Default: $bp->groups->table_name.
	 *
	 * @return string|null ID of the group, if one is found, else null.
	 */
	public static function group_exists( $slug, $table_name = false ) {
		global $wpdb;

		if ( empty( $table_name ) )
			$table_name = buddypress()->groups->table_name;

		if ( empty( $slug ) )
			return false;

		return $wpdb->get_var( $wpdb->prepare( "SELECT id FROM {$table_name} WHERE slug = %s", strtolower( $slug ) ) );
	}

	/**
	 * Get the ID of a group by the group's slug.
	 *
	 * Alias of {@link BP_Groups_Group::group_exists()}.
	 *
	 * @param string $slug See {@link BP_Groups_Group::group_exists()}.
	 *
	 * @return string|null See {@link BP_Groups_Group::group_exists()}.
	 */
	public static function get_id_from_slug( $slug ) {
		return BP_Groups_Group::group_exists( $slug );
	}

	/**
	 * Get IDs of users with outstanding invites to a given group from a specified user.
	 *
	 * @param int $user_id ID of the inviting user.
	 * @param int $group_id ID of the group.
	 *
	 * @return array IDs of users who have been invited to the group by the
	 *               user but have not yet accepted.
	 */
	public static function get_invites( $user_id, $group_id ) {
		global $wpdb;

		$bp = buddypress();

		return $wpdb->get_col( $wpdb->prepare( "SELECT user_id FROM {$bp->groups->table_name_members} WHERE group_id = %d and is_confirmed = 0 AND inviter_id = %d", $group_id, $user_id ) );
	}

	/**
	 * Get a list of a user's groups, filtered by a search string.
	 *
	 * @param string   $filter  Search term. Matches against 'name' and
	 *                          'description' fields.
	 * @param int      $user_id ID of the user whose groups are being searched.
	 *                          Default: the displayed user.
	 * @param mixed    $order   Not used.
	 * @param int|null $limit   Optional. The max number of results to return.
	 *                          Default: null (no limit).
	 * @param int|null $page    Optional. The page offset of results to return.
	 *                          Default: null (no limit).
	 * @return false|array {
	 *     @type array $groups Array of matched and paginated group objects.
	 *     @type int   $total  Total count of groups matching the query.
	 * }
	 */
	public static function filter_user_groups( $filter, $user_id = 0, $order = false, $limit = null, $page = null ) {
		global $wpdb;

		if ( empty( $user_id ) )
			$user_id = bp_displayed_user_id();

		$search_terms_like = bp_esc_like( $filter ) . '%';

		$pag_sql = $order_sql = $hidden_sql = '';

		if ( !empty( $limit ) && !empty( $page ) )
			$pag_sql = $wpdb->prepare( " LIMIT %d, %d", intval( ( $page - 1 ) * $limit), intval( $limit ) );

		// Get all the group ids for the current user's groups.
		$gids = BP_Groups_Member::get_group_ids( $user_id );

		if ( empty( $gids['groups'] ) )
			return false;

		$bp = buddypress();

		$gids = esc_sql( implode( ',', wp_parse_id_list( $gids['groups'] ) ) );

		$paged_groups = $wpdb->get_results( $wpdb->prepare( "SELECT id as group_id FROM {$bp->groups->table_name} WHERE ( name LIKE %s OR description LIKE %s ) AND id IN ({$gids}) {$pag_sql}", $search_terms_like, $search_terms_like ) );
		$total_groups = $wpdb->get_var( $wpdb->prepare( "SELECT COUNT(id) FROM {$bp->groups->table_name} WHERE ( name LIKE %s OR description LIKE %s ) AND id IN ({$gids})", $search_terms_like, $search_terms_like ) );

		return array( 'groups' => $paged_groups, 'total' => $total_groups );
	}

	/**
	 * Get a list of groups, filtered by a search string.
	 *
	 * @param string      $filter  Search term. Matches against 'name' and
	 *                             'description' fields.
	 * @param int|null    $limit   Optional. The max number of results to return.
	 *                             Default: null (no limit).
	 * @param int|null    $page    Optional. The page offset of results to return.
	 *                             Default: null (no limit).
	 * @param string|bool $sort_by Column to sort by. Default: false (default
	 *        sort).
	 * @param string|bool $order   ASC or DESC. Default: false (default sort).
	 *
	 * @return array {
	 *     @type array $groups Array of matched and paginated group objects.
	 *     @type int   $total  Total count of groups matching the query.
	 * }
	 */
	public static function search_groups( $filter, $limit = null, $page = null, $sort_by = false, $order = false ) {
		global $wpdb;

		$search_terms_like = '%' . bp_esc_like( $filter ) . '%';

		$pag_sql = $order_sql = $hidden_sql = '';

		if ( !empty( $limit ) && !empty( $page ) )
			$pag_sql = $wpdb->prepare( " LIMIT %d, %d", intval( ( $page - 1 ) * $limit), intval( $limit ) );

		if ( !empty( $sort_by ) && !empty( $order ) ) {
			$sort_by   = esc_sql( $sort_by );
			$order     = esc_sql( $order );
			$order_sql = "ORDER BY {$sort_by} {$order}";
		}

		if ( !bp_current_user_can( 'bp_moderate' ) )
			$hidden_sql = "AND status != 'hidden'";

		$bp = buddypress();

		$paged_groups = $wpdb->get_results( $wpdb->prepare( "SELECT id as group_id FROM {$bp->groups->table_name} WHERE ( name LIKE %s OR description LIKE %s ) {$hidden_sql} {$order_sql} {$pag_sql}", $search_terms_like, $search_terms_like ) );
		$total_groups = $wpdb->get_var( $wpdb->prepare( "SELECT COUNT(id) FROM {$bp->groups->table_name} WHERE ( name LIKE %s OR description LIKE %s ) {$hidden_sql}", $search_terms_like, $search_terms_like ) );

		return array( 'groups' => $paged_groups, 'total' => $total_groups );
	}

	/**
	 * Check for the existence of a slug.
	 *
	 * @param string $slug Slug to check.
	 *
	 * @return string|null The slug, if found. Otherwise null.
	 */
	public static function check_slug( $slug ) {
		global $wpdb;

		$bp = buddypress();

		return $wpdb->get_var( $wpdb->prepare( "SELECT slug FROM {$bp->groups->table_name} WHERE slug = %s", $slug ) );
	}

	/**
	 * Get the slug for a given group ID.
	 *
	 * @param int $group_id ID of the group.
	 *
	 * @return string|null The slug, if found. Otherwise null.
	 */
	public static function get_slug( $group_id ) {
		global $wpdb;

		$bp = buddypress();

		return $wpdb->get_var( $wpdb->prepare( "SELECT slug FROM {$bp->groups->table_name} WHERE id = %d", $group_id ) );
	}

	/**
	 * Check whether a given group has any members.
	 *
	 * @param int $group_id ID of the group.
	 *
	 * @return bool True if the group has members, otherwise false.
	 */
	public static function has_members( $group_id ) {
		global $wpdb;

		$bp = buddypress();

		$members = $wpdb->get_var( $wpdb->prepare( "SELECT COUNT(id) FROM {$bp->groups->table_name_members} WHERE group_id = %d", $group_id ) );

		if ( empty( $members ) )
			return false;

		return true;
	}

	/**
	 * Check whether a group has outstanding membership requests.
	 *
	 * @param int $group_id ID of the group.
	 *
	 * @return int|null The number of outstanding requests, or null if
	 *                  none are found.
	 */
	public static function has_membership_requests( $group_id ) {
		global $wpdb;

		$bp = buddypress();

		return $wpdb->get_var( $wpdb->prepare( "SELECT COUNT(id) FROM {$bp->groups->table_name_members} WHERE group_id = %d AND is_confirmed = 0", $group_id ) );
	}

	/**
	 * Get outstanding membership requests for a group.
	 *
	 * @param int      $group_id ID of the group.
	 * @param int|null $limit    Optional. Max number of results to return.
	 *                           Default: null (no limit).
	 * @param int|null $page     Optional. Page offset of results returned. Default:
	 *                           null (no limit).
	 * @return array {
	 *     @type array $requests The requested page of located requests.
	 *     @type int   $total    Total number of requests outstanding for the
	 *                           group.
	 * }
	 */
	public static function get_membership_requests( $group_id, $limit = null, $page = null ) {
		global $wpdb;

		if ( !empty( $limit ) && !empty( $page ) ) {
			$pag_sql = $wpdb->prepare( " LIMIT %d, %d", intval( ( $page - 1 ) * $limit), intval( $limit ) );
		}

		$bp = buddypress();

		$paged_requests = $wpdb->get_results( $wpdb->prepare( "SELECT * FROM {$bp->groups->table_name_members} WHERE group_id = %d AND is_confirmed = 0 AND inviter_id = 0{$pag_sql}", $group_id ) );
		$total_requests = $wpdb->get_var( $wpdb->prepare( "SELECT COUNT(id) FROM {$bp->groups->table_name_members} WHERE group_id = %d AND is_confirmed = 0 AND inviter_id = 0", $group_id ) );

		return array( 'requests' => $paged_requests, 'total' => $total_requests );
	}

	/**
	 * Query for groups.
	 *
	 * @see WP_Meta_Query::queries for a description of the 'meta_query'
	 *      parameter format.
	 *
	 * @param array {
	 *     Array of parameters. All items are optional.
	 *     @type string       $type              Optional. Shorthand for certain orderby/
	 *                                           order combinations. 'newest', 'active', 'popular',
	 *                                           'alphabetical', 'random'. When present, will override
	 *                                           orderby and order params. Default: null.
	 *     @type string       $orderby           Optional. Property to sort by.
	 *                                           'date_created', 'last_activity', 'total_member_count',
	 *                                           'name', 'random'. Default: 'date_created'.
	 *     @type string       $order             Optional. Sort order. 'ASC' or 'DESC'.
	 *                                           Default: 'DESC'.
	 *     @type int          $per_page          Optional. Number of items to return per page
	 *                                           of results. Default: null (no limit).
	 *     @type int          $page              Optional. Page offset of results to return.
	 *                                           Default: null (no limit).
	 *     @type int          $user_id           Optional. If provided, results will be limited to groups
	 *                                           of which the specified user is a member. Default: null.
	 *     @type string       $search_terms      Optional. If provided, only groups whose names
	 *                                           or descriptions match the search terms will be
	 *                                           returned. Default: false.
	 *     @type array        $meta_query        Optional. An array of meta_query conditions.
	 *                                           See {@link WP_Meta_Query::queries} for description.
	 *     @type array|string $value             Optional. Array or comma-separated list of group IDs.
	 *                                           Results will be limited to groups within the
	 *                                           list. Default: false.
	 *     @type bool         $populate_extras   Whether to fetch additional information
	 *                                           (such as member count) about groups. Default: true.
	 *     @type array|string $exclude           Optional. Array or comma-separated list of group IDs.
	 *                                           Results will exclude the listed groups. Default: false.
	 *     @type bool         $update_meta_cache Whether to pre-fetch groupmeta for
	 *                                           the returned groups. Default: true.
	 *     @type bool         $show_hidden       Whether to include hidden groups in results. Default: false.
	 * }
	 * @return array {
	 *     @type array $groups Array of group objects returned by the
	 *                         paginated query.
	 *     @type int   $total  Total count of all groups matching non-
	 *                         paginated query params.
	 * }
	 */
	public static function get( $args = array() ) {
		global $wpdb;

		// Backward compatibility with old method of passing arguments
		if ( ! is_array( $args ) || func_num_args() > 1 ) {
			_deprecated_argument( __METHOD__, '1.7', sprintf( __( 'Arguments passed to %1$s should be in an associative array. See the inline documentation at %2$s for more details.', 'buddypress' ), __METHOD__, __FILE__ ) );

			$old_args_keys = array(
				0 => 'type',
				1 => 'per_page',
				2 => 'page',
				3 => 'user_id',
				4 => 'search_terms',
				5 => 'include',
				6 => 'populate_extras',
				7 => 'exclude',
				8 => 'show_hidden',
			);

			$func_args = func_get_args();
			$args      = bp_core_parse_args_array( $old_args_keys, $func_args );
		}

		$defaults = array(
			'type'              => null,
			'orderby'           => 'date_created',
			'order'             => 'DESC',
			'per_page'          => null,
			'page'              => null,
			'user_id'           => 0,
			'search_terms'      => false,
			'meta_query'        => false,
			'include'           => false,
			'populate_extras'   => true,
			'update_meta_cache' => true,
			'exclude'           => false,
			'show_hidden'       => false,
		);

		$r = wp_parse_args( $args, $defaults );

		$bp = buddypress();

		$sql       = array();
		$total_sql = array();

		$sql['select'] = "SELECT DISTINCT g.id, g.*, gm1.meta_value AS total_member_count, gm2.meta_value AS last_activity";
		$sql['from']   = " FROM {$bp->groups->table_name_groupmeta} gm1, {$bp->groups->table_name_groupmeta} gm2,";

		if ( ! empty( $r['user_id'] ) ) {
			$sql['members_from'] = " {$bp->groups->table_name_members} m,";
		}

		$sql['group_from'] = " {$bp->groups->table_name} g WHERE";

		if ( ! empty( $r['user_id'] ) ) {
			$sql['user_where'] = " g.id = m.group_id AND";
		}

		$sql['where'] = " g.id = gm1.group_id AND g.id = gm2.group_id AND gm2.meta_key = 'last_activity' AND gm1.meta_key = 'total_member_count'";

		if ( empty( $r['show_hidden'] ) ) {
			$sql['hidden'] = " AND g.status != 'hidden'";
		}

		if ( ! empty( $r['search_terms'] ) ) {
			$search_terms_like = '%' . bp_esc_like( $r['search_terms'] ) . '%';
			$sql['search'] = $wpdb->prepare( " AND ( g.name LIKE %s OR g.description LIKE %s )", $search_terms_like, $search_terms_like );
		}

		$meta_query_sql = self::get_meta_query_sql( $r['meta_query'] );

		if ( ! empty( $meta_query_sql['join'] ) ) {
			$sql['from'] .= $meta_query_sql['join'];
		}

		if ( ! empty( $meta_query_sql['where'] ) ) {
			$sql['meta'] = $meta_query_sql['where'];
		}

		if ( ! empty( $r['user_id'] ) ) {
			$sql['user'] = $wpdb->prepare( " AND m.user_id = %d AND m.is_confirmed = 1 AND m.is_banned = 0", $r['user_id'] );
		}

		if ( ! empty( $r['include'] ) ) {
			$include        = implode( ',', wp_parse_id_list( $r['include'] ) );
			$sql['include'] = " AND g.id IN ({$include})";
		}

		if ( ! empty( $r['exclude'] ) ) {
			$exclude        = implode( ',', wp_parse_id_list( $r['exclude'] ) );
			$sql['exclude'] = " AND g.id NOT IN ({$exclude})";
		}

		/** Order/orderby ********************************************/

		$order   = $r['order'];
		$orderby = $r['orderby'];

		// If a 'type' parameter was passed, parse it and overwrite
		// 'order' and 'orderby' params passed to the function
		if (  ! empty( $r['type'] ) ) {

			/**
			 * Filters the 'type' parameter used to overwrite 'order' and 'orderby' values.
			 *
			 * @since 2.1.0
			 *
			 * @param array  $value Converted 'type' value for order and orderby.
			 * @param string $value Parsed 'type' value for the get method.
			 */
			$order_orderby = apply_filters( 'bp_groups_get_orderby', self::convert_type_to_order_orderby( $r['type'] ), $r['type'] );

			// If an invalid type is passed, $order_orderby will be
			// an array with empty values. In this case, we stick
			// with the default values of $order and $orderby
			if ( ! empty( $order_orderby['order'] ) ) {
				$order = $order_orderby['order'];
			}

			if ( ! empty( $order_orderby['orderby'] ) ) {
				$orderby = $order_orderby['orderby'];
			}
		}

		// Sanitize 'order'
		$order = bp_esc_sql_order( $order );

		/**
		 * Filters the converted 'orderby' term.
		 *
		 * @since 2.1.0
		 *
		 * @param string $value   Converted 'orderby' term.
		 * @param string $orderby Original orderby value.
		 * @param string $value   Parsed 'type' value for the get method.
		 */
		$orderby = apply_filters( 'bp_groups_get_orderby_converted_by_term', self::convert_orderby_to_order_by_term( $orderby ), $orderby, $r['type'] );

		// Random order is a special case
		if ( 'rand()' === $orderby ) {
			$sql[] = "ORDER BY rand()";
		} else {
			$sql[] = "ORDER BY {$orderby} {$order}";
		}

		if ( ! empty( $r['per_page'] ) && ! empty( $r['page'] ) && $r['per_page'] != -1 ) {
			$sql['pagination'] = $wpdb->prepare( "LIMIT %d, %d", intval( ( $r['page'] - 1 ) * $r['per_page']), intval( $r['per_page'] ) );
		}

		/**
		 * Filters the pagination SQL statement.
		 *
		 * @since 1.5.0
		 *
		 * @param string $value Concatenated SQL statement.
		 * @param array  $sql   Array of SQL parts before concatenation.
		 * @param array  $r     Array of parsed arguments for the get method.
		 */
		$paged_groups_sql = apply_filters( 'bp_groups_get_paged_groups_sql', join( ' ', (array) $sql ), $sql, $r );
		$paged_groups     = $wpdb->get_results( $paged_groups_sql );

		$total_sql['select'] = "SELECT COUNT(DISTINCT g.id) FROM {$bp->groups->table_name} g, {$bp->groups->table_name_groupmeta} gm";

		if ( ! empty( $r['user_id'] ) ) {
			$total_sql['select'] .= ", {$bp->groups->table_name_members} m";
		}

		if ( ! empty( $sql['hidden'] ) ) {
			$total_sql['where'][] = "g.status != 'hidden'";
		}

		if ( ! empty( $sql['search'] ) ) {
			$total_sql['where'][] = $wpdb->prepare( "( g.name LIKE %s OR g.description LIKE %s )", $search_terms_like, $search_terms_like );
		}

		if ( ! empty( $r['user_id'] ) ) {
			$total_sql['where'][] = $wpdb->prepare( "m.group_id = g.id AND m.user_id = %d AND m.is_confirmed = 1 AND m.is_banned = 0", $r['user_id'] );
		}

		// Temporary implementation of meta_query for total count
		// See #5099
		if ( ! empty( $meta_query_sql['where'] ) ) {
			// Join the groupmeta table
			$total_sql['select'] .= ", ". substr( $meta_query_sql['join'], 0, -2 );

			// Modify the meta_query clause from paged_sql for our syntax
			$meta_query_clause = preg_replace( '/^\s*AND/', '', $meta_query_sql['where'] );
			$total_sql['where'][] = $meta_query_clause;
		}

		// Already escaped in the paginated results block
		if ( ! empty( $include ) ) {
			$total_sql['where'][] = "g.id IN ({$include})";
		}

		// Already escaped in the paginated results block
		if ( ! empty( $exclude ) ) {
			$total_sql['where'][] = "g.id NOT IN ({$exclude})";
		}

		$total_sql['where'][] = "g.id = gm.group_id";
		$total_sql['where'][] = "gm.meta_key = 'last_activity'";

		$t_sql = $total_sql['select'];

		if ( ! empty( $total_sql['where'] ) ) {
			$t_sql .= " WHERE " . join( ' AND ', (array) $total_sql['where'] );
		}

		/**
		 * Filters the SQL used to retrieve total group results.
		 *
		 * @since 1.5.0
		 *
		 * @param string $t_sql     Concatenated SQL statement used for retrieving total group results.
		 * @param array  $total_sql Array of SQL parts for the query.
		 * @param array  $r         Array of parsed arguments for the get method.
		 */
		$total_groups_sql = apply_filters( 'bp_groups_get_total_groups_sql', $t_sql, $total_sql, $r );
		$total_groups     = $wpdb->get_var( $total_groups_sql );

		$group_ids = array();
		foreach ( (array) $paged_groups as $group ) {
			$group_ids[] = $group->id;
		}

		// Populate some extra information instead of querying each time in the loop
		if ( !empty( $r['populate_extras'] ) ) {
			$paged_groups = BP_Groups_Group::get_group_extras( $paged_groups, $group_ids, $r['type'] );
		}

		// Grab all groupmeta
		if ( ! empty( $r['update_meta_cache'] ) ) {
			bp_groups_update_meta_cache( $group_ids );
		}

		unset( $sql, $total_sql );

		return array( 'groups' => $paged_groups, 'total' => $total_groups );
	}

	/**
	 * Get the SQL for the 'meta_query' param in BP_Activity_Activity::get()
	 *
	 * We use WP_Meta_Query to do the heavy lifting of parsing the
	 * meta_query array and creating the necessary SQL clauses. However,
	 * since BP_Activity_Activity::get() builds its SQL differently than
	 * WP_Query, we have to alter the return value (stripping the leading
	 * AND keyword from the 'where' clause).
	 *
	 * @since 1.8.0
	 *
	 * @param array $meta_query An array of meta_query filters. See the
	 *                          documentation for {@link WP_Meta_Query} for details.
	 *
	 * @return array $sql_array 'join' and 'where' clauses.
	 */
	protected static function get_meta_query_sql( $meta_query = array() ) {
		global $wpdb;

		$sql_array = array(
			'join'  => '',
			'where' => '',
		);

		if ( ! empty( $meta_query ) ) {
			$groups_meta_query = new WP_Meta_Query( $meta_query );

			// WP_Meta_Query expects the table name at
			// $wpdb->group
			$wpdb->groupmeta = buddypress()->groups->table_name_groupmeta;

			$meta_sql = $groups_meta_query->get_sql( 'group', 'g', 'id' );

			// BP_Groups_Group::get uses the comma syntax for table
			// joins, which means that we have to do some regex to
			// convert the INNER JOIN and move the ON clause to a
			// WHERE condition
			//
			// @todo It may be better in the long run to refactor
			// the more general query syntax to accord better with
			// BP/WP convention
			preg_match_all( '/JOIN (.+?) ON/', $meta_sql['join'], $matches_a );
			preg_match_all( '/ON \((.+?)\)/', $meta_sql['join'], $matches_b );

			if ( ! empty( $matches_a[1] ) && ! empty( $matches_b[1] ) ) {
				$sql_array['join']  = implode( ',', $matches_a[1] ) . ', ';
				$sql_array['where'] = $meta_sql['where'] . ' AND ' . implode( ' AND ', $matches_b[1] );
			}
		}

		return $sql_array;
	}

	/**
	 * Convert the 'type' parameter to 'order' and 'orderby'.
	 *
	 * @since 1.8.0
	 *
	 * @param string $type The 'type' shorthand param.
	 *
	 * @return array {
	 *	@type string $order   SQL-friendly order string.
	 *	@type string $orderby SQL-friendly orderby column name.
	 * }
	 */
	protected static function convert_type_to_order_orderby( $type = '' ) {
		$order = $orderby = '';

		switch ( $type ) {
			case 'newest' :
				$order   = 'DESC';
				$orderby = 'date_created';
				break;

			case 'active' :
				$order   = 'DESC';
				$orderby = 'last_activity';
				break;

			case 'popular' :
				$order   = 'DESC';
				$orderby = 'total_member_count';
				break;

			case 'alphabetical' :
				$order   = 'ASC';
				$orderby = 'name';
				break;

			case 'random' :
				$order   = '';
				$orderby = 'random';
				break;
		}

		return array( 'order' => $order, 'orderby' => $orderby );
	}

	/**
	 * Get a list of groups, sorted by those that have the most legacy forum topics.
	 *
	 * @param int|null          $limit           Optional. The max number of results to return.
	 *                                           Default: null (no limit).
	 * @param int|null          $page            Optional. The page offset of results to return.
	 *                                           Default: null (no limit).
	 * @param int               $user_id         Optional. If present, groups will be limited to
	 *                                           those of which the specified user is a member.
	 * @param string|bool       $search_terms    Optional. Limit groups to those whose name
	 *                                           or description field contain the search string.
	 * @param bool              $populate_extras Optional. Whether to fetch extra
	 *                                           information about the groups. Default: true.
	 * @param string|array|bool $exclude         Optional. Array or comma-separated list of group
	 *                                           IDs to exclude from results.
	 *
	 * @return array {
	 *     @type array $groups Array of group objects returned by the
	 *                         paginated query.
	 *     @type int   $total  Total count of all groups matching non-
	 *                         paginated query params.
	 * }
	 */
	public static function get_by_most_forum_topics( $limit = null, $page = null, $user_id = 0, $search_terms = false, $populate_extras = true, $exclude = false ) {
		global $wpdb, $bbdb;

		if ( empty( $bbdb ) ) {

			/** This action is documented in bp-forums/bp-forums-screens */
			do_action( 'bbpress_init' );
		}

		if ( !empty( $limit ) && !empty( $page ) ) {
			$pag_sql = $wpdb->prepare( " LIMIT %d, %d", intval( ( $page - 1 ) * $limit), intval( $limit ) );
		}

		if ( !is_user_logged_in() || ( !bp_current_user_can( 'bp_moderate' ) && ( $user_id != bp_loggedin_user_id() ) ) )
			$hidden_sql = " AND g.status != 'hidden'";

		if ( !empty( $search_terms ) ) {
			$search_terms_like = '%' . bp_esc_like( $search_terms ) . '%';
			$search_sql        = $wpdb->prepare( ' AND ( g.name LIKE %s OR g.description LIKE %s ) ', $search_terms_like, $search_terms_like );
		}

		if ( !empty( $exclude ) ) {
			$exclude     = implode( ',', wp_parse_id_list( $exclude ) );
			$exclude_sql = " AND g.id NOT IN ({$exclude})";
		}

		$bp = buddypress();

		if ( !empty( $user_id ) ) {
			$user_id      = absint( esc_sql( $user_id ) );
			$paged_groups = $wpdb->get_results( "SELECT DISTINCT g.*, gm1.meta_value as total_member_count, gm2.meta_value as last_activity FROM {$bp->groups->table_name_groupmeta} gm1, {$bp->groups->table_name_groupmeta} gm2, {$bp->groups->table_name_groupmeta} gm3, {$bp->groups->table_name_members} m, {$bbdb->forums} f, {$bp->groups->table_name} g WHERE g.id = m.group_id AND g.id = gm1.group_id AND g.id = gm2.group_id AND g.id = gm3.group_id AND gm2.meta_key = 'last_activity' AND gm1.meta_key = 'total_member_count' AND (gm3.meta_key = 'forum_id' AND gm3.meta_value = f.forum_id) AND f.topics > 0 {$hidden_sql} {$search_sql} AND m.user_id = {$user_id} AND m.is_confirmed = 1 AND m.is_banned = 0 {$exclude_sql} ORDER BY f.topics DESC {$pag_sql}" );
			$total_groups = $wpdb->get_var( "SELECT COUNT(DISTINCT g.id) FROM {$bp->groups->table_name_groupmeta} gm1, {$bp->groups->table_name_groupmeta} gm2, {$bp->groups->table_name_groupmeta} gm3, {$bbdb->forums} f, {$bp->groups->table_name} g WHERE g.id = gm1.group_id AND g.id = gm2.group_id AND g.id = gm3.group_id AND gm2.meta_key = 'last_activity' AND gm1.meta_key = 'total_member_count' AND (gm3.meta_key = 'forum_id' AND gm3.meta_value = f.forum_id) AND f.topics > 0 {$hidden_sql} {$search_sql} AND m.user_id = {$user_id} AND m.is_confirmed = 1 AND m.is_banned = 0 {$exclude_sql}" );
		} else {
			$paged_groups = $wpdb->get_results( "SELECT DISTINCT g.*, gm1.meta_value as total_member_count, gm2.meta_value as last_activity FROM {$bp->groups->table_name_groupmeta} gm1, {$bp->groups->table_name_groupmeta} gm2, {$bp->groups->table_name_groupmeta} gm3, {$bbdb->forums} f, {$bp->groups->table_name} g WHERE g.id = gm1.group_id AND g.id = gm2.group_id AND g.id = gm3.group_id AND gm2.meta_key = 'last_activity' AND gm1.meta_key = 'total_member_count' AND (gm3.meta_key = 'forum_id' AND gm3.meta_value = f.forum_id) AND f.topics > 0 {$hidden_sql} {$search_sql} {$exclude_sql} ORDER BY f.topics DESC {$pag_sql}" );
			$total_groups = $wpdb->get_var( "SELECT COUNT(DISTINCT g.id) FROM {$bp->groups->table_name_groupmeta} gm1, {$bp->groups->table_name_groupmeta} gm2, {$bp->groups->table_name_groupmeta} gm3, {$bbdb->forums} f, {$bp->groups->table_name} g WHERE g.id = gm1.group_id AND g.id = gm2.group_id AND g.id = gm3.group_id AND gm2.meta_key = 'last_activity' AND gm1.meta_key = 'total_member_count' AND (gm3.meta_key = 'forum_id' AND gm3.meta_value = f.forum_id) AND f.topics > 0 {$hidden_sql} {$search_sql} {$exclude_sql}" );
		}

		if ( !empty( $populate_extras ) ) {
			foreach ( (array) $paged_groups as $group ) {
				$group_ids[] = $group->id;
			}
			$paged_groups = BP_Groups_Group::get_group_extras( $paged_groups, $group_ids, 'newest' );
		}

		return array( 'groups' => $paged_groups, 'total' => $total_groups );
	}

	/**
	 * Convert the 'orderby' param into a proper SQL term/column.
	 *
	 * @since 1.8.0
	 *
	 * @param string $orderby Orderby term as passed to get().
	 *
	 * @return string $order_by_term SQL-friendly orderby term.
	 */
	protected static function convert_orderby_to_order_by_term( $orderby ) {
		$order_by_term = '';

		switch ( $orderby ) {
			case 'date_created' :
			default :
				$order_by_term = 'g.date_created';
				break;

			case 'last_activity' :
				$order_by_term = 'last_activity';
				break;

			case 'total_member_count' :
				$order_by_term = 'CONVERT(gm1.meta_value, SIGNED)';
				break;

			case 'name' :
				$order_by_term = 'g.name';
				break;

			case 'random' :
				$order_by_term = 'rand()';
				break;
		}

		return $order_by_term;
	}

	/**
	 * Get a list of groups, sorted by those that have the most legacy forum posts.
	 *
	 * @param int|null          $limit           Optional. The max number of results to return.
	 *                                           Default: null (no limit).
	 * @param int|null          $page            Optional. The page offset of results to return.
	 *                                           Default: null (no limit).
	 * @param string|bool       $search_terms    Optional. Limit groups to those whose name
	 *                                           or description field contain the search string.
	 * @param bool              $populate_extras Optional. Whether to fetch extra
	 *                                           information about the groups. Default: true.
	 * @param string|array|bool $exclude         Optional. Array or comma-separated list of group
	 *                                           IDs to exclude from results.
	 *
	 * @return array {
	 *     @type array $groups Array of group objects returned by the
	 *                         paginated query.
	 *     @type int   $total  Total count of all groups matching non-
	 *                         paginated query params.
	 * }
	 */
	public static function get_by_most_forum_posts( $limit = null, $page = null, $search_terms = false, $populate_extras = true, $exclude = false ) {
		global $wpdb, $bbdb;

		if ( empty( $bbdb ) ) {

			/** This action is documented in bp-forums/bp-forums-screens */
			do_action( 'bbpress_init' );
		}

		if ( !empty( $limit ) && !empty( $page ) ) {
			$pag_sql = $wpdb->prepare( " LIMIT %d, %d", intval( ( $page - 1 ) * $limit), intval( $limit ) );
		}

		if ( !is_user_logged_in() || ( !bp_current_user_can( 'bp_moderate' ) && ( $user_id != bp_loggedin_user_id() ) ) )
			$hidden_sql = " AND g.status != 'hidden'";

		if ( !empty( $search_terms ) ) {
			$search_terms_like = '%' . bp_esc_like( $search_terms ) . '%';
			$search_sql        = $wpdb->prepare( ' AND ( g.name LIKE %s OR g.description LIKE %s ) ', $search_terms_like, $search_terms_like );
		}

		if ( !empty( $exclude ) ) {
			$exclude     = implode( ',', wp_parse_id_list( $exclude ) );
			$exclude_sql = " AND g.id NOT IN ({$exclude})";
		}

		$bp = buddypress();

		if ( !empty( $user_id ) ) {
			$user_id = esc_sql( $user_id );
			$paged_groups = $wpdb->get_results( "SELECT DISTINCT g.*, gm1.meta_value as total_member_count, gm2.meta_value as last_activity FROM {$bp->groups->table_name_groupmeta} gm1, {$bp->groups->table_name_groupmeta} gm2, {$bp->groups->table_name_groupmeta} gm3, {$bp->groups->table_name_members} m, {$bbdb->forums} f, {$bp->groups->table_name} g WHERE g.id = m.group_id AND g.id = gm1.group_id AND g.id = gm2.group_id AND g.id = gm3.group_id AND gm2.meta_key = 'last_activity' AND gm1.meta_key = 'total_member_count' AND (gm3.meta_key = 'forum_id' AND gm3.meta_value = f.forum_id) {$hidden_sql} {$search_sql} AND m.user_id = {$user_id} AND m.is_confirmed = 1 AND m.is_banned = 0 {$exclude_sql} ORDER BY f.posts ASC {$pag_sql}" );
			$total_groups = $wpdb->get_results( "SELECT COUNT(DISTINCT g.id) FROM {$bp->groups->table_name_groupmeta} gm1, {$bp->groups->table_name_groupmeta} gm2, {$bp->groups->table_name_groupmeta} gm3, {$bp->groups->table_name_members} m, {$bbdb->forums} f, {$bp->groups->table_name} g WHERE g.id = m.group_id AND g.id = gm1.group_id AND g.id = gm2.group_id AND g.id = gm3.group_id AND gm2.meta_key = 'last_activity' AND gm1.meta_key = 'total_member_count' AND (gm3.meta_key = 'forum_id' AND gm3.meta_value = f.forum_id) AND f.posts > 0 {$hidden_sql} {$search_sql} AND m.user_id = {$user_id} AND m.is_confirmed = 1 AND m.is_banned = 0 {$exclude_sql} " );
		} else {
			$paged_groups = $wpdb->get_results( "SELECT DISTINCT g.*, gm1.meta_value as total_member_count, gm2.meta_value as last_activity FROM {$bp->groups->table_name_groupmeta} gm1, {$bp->groups->table_name_groupmeta} gm2, {$bp->groups->table_name_groupmeta} gm3, {$bbdb->forums} f, {$bp->groups->table_name} g WHERE g.id = gm1.group_id AND g.id = gm2.group_id AND g.id = gm3.group_id AND gm2.meta_key = 'last_activity' AND gm1.meta_key = 'total_member_count' AND (gm3.meta_key = 'forum_id' AND gm3.meta_value = f.forum_id) AND f.posts > 0 {$hidden_sql} {$search_sql} {$exclude_sql} ORDER BY f.posts ASC {$pag_sql}" );
			$total_groups = $wpdb->get_var( "SELECT COUNT(DISTINCT g.id) FROM {$bp->groups->table_name_groupmeta} gm1, {$bp->groups->table_name_groupmeta} gm2, {$bp->groups->table_name_groupmeta} gm3, {$bbdb->forums} f, {$bp->groups->table_name} g WHERE g.id = gm1.group_id AND g.id = gm2.group_id AND g.id = gm3.group_id AND gm2.meta_key = 'last_activity' AND gm1.meta_key = 'total_member_count' AND (gm3.meta_key = 'forum_id' AND gm3.meta_value = f.forum_id) {$hidden_sql} {$search_sql} {$exclude_sql}" );
		}

		if ( !empty( $populate_extras ) ) {
			foreach ( (array) $paged_groups as $group ) {
				$group_ids[] = $group->id;
			}
			$paged_groups = BP_Groups_Group::get_group_extras( $paged_groups, $group_ids, 'newest' );
		}

		return array( 'groups' => $paged_groups, 'total' => $total_groups );
	}

	/**
	 * Get a list of groups whose names start with a given letter.
	 *
	 * @param string            $letter          The letter.
	 * @param int|null          $limit           Optional. The max number of results to return.
	 *                                           Default: null (no limit).
	 * @param int|null          $page            Optional. The page offset of results to return.
	 *                                           Default: null (no limit).
	 * @param bool              $populate_extras Optional. Whether to fetch extra
	 *                                           information about the groups. Default: true.
	 * @param string|array|bool $exclude         Optional. Array or comma-separated list of group
	 *                                           IDs to exclude from results.
	 * @return false|array {
	 *     @type array $groups Array of group objects returned by the
	 *                         paginated query.
	 *     @type int   $total  Total count of all groups matching non-
	 *                         paginated query params.
	 * }
	 */
	public static function get_by_letter( $letter, $limit = null, $page = null, $populate_extras = true, $exclude = false ) {
		global $wpdb;

		$pag_sql = $hidden_sql = $exclude_sql = '';

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

		if ( !empty( $exclude ) ) {
			$exclude     = implode( ',', wp_parse_id_list( $exclude ) );
			$exclude_sql = " AND g.id NOT IN ({$exclude})";
		}

		if ( !bp_current_user_can( 'bp_moderate' ) )
			$hidden_sql = " AND status != 'hidden'";

		$letter_like = bp_esc_like( $letter ) . '%';

		if ( !empty( $limit ) && !empty( $page ) ) {
			$pag_sql      = $wpdb->prepare( " LIMIT %d, %d", intval( ( $page - 1 ) * $limit), intval( $limit ) );
		}

		$total_groups = $wpdb->get_var( $wpdb->prepare( "SELECT COUNT(DISTINCT g.id) FROM {$bp->groups->table_name_groupmeta} gm1, {$bp->groups->table_name_groupmeta} gm2, {$bp->groups->table_name} g WHERE g.id = gm1.group_id AND g.id = gm2.group_id AND gm2.meta_key = 'last_activity' AND gm1.meta_key = 'total_member_count' AND g.name LIKE %s {$hidden_sql} {$exclude_sql}", $letter_like ) );

		$paged_groups = $wpdb->get_results( $wpdb->prepare( "SELECT g.*, gm1.meta_value as total_member_count, gm2.meta_value as last_activity FROM {$bp->groups->table_name_groupmeta} gm1, {$bp->groups->table_name_groupmeta} gm2, {$bp->groups->table_name} g WHERE g.id = gm1.group_id AND g.id = gm2.group_id AND gm2.meta_key = 'last_activity' AND gm1.meta_key = 'total_member_count' AND g.name LIKE %s {$hidden_sql} {$exclude_sql} ORDER BY g.name ASC {$pag_sql}", $letter_like ) );

		if ( !empty( $populate_extras ) ) {
			foreach ( (array) $paged_groups as $group ) {
				$group_ids[] = $group->id;
			}
			$paged_groups = BP_Groups_Group::get_group_extras( $paged_groups, $group_ids, 'newest' );
		}

		return array( 'groups' => $paged_groups, 'total' => $total_groups );
	}

	/**
	 * Get a list of random groups.
	 *
	 * Use BP_Groups_Group::get() with 'type' = 'random' instead.
	 *
	 * @param int|null          $limit           Optional. The max number of results to return.
	 *                                           Default: null (no limit).
	 * @param int|null          $page            Optional. The page offset of results to return.
	 *                                           Default: null (no limit).
	 * @param int               $user_id         Optional. If present, groups will be limited to
	 *                                           those of which the specified user is a member.
	 * @param string|bool       $search_terms    Optional. Limit groups to those whose name
	 *                                           or description field contain the search string.
	 * @param bool              $populate_extras Optional. Whether to fetch extra
	 *                                           information about the groups. Default: true.
	 * @param string|array|bool $exclude         Optional. Array or comma-separated list of group
	 *                                           IDs to exclude from results.
	 *
	 * @return array {
	 *     @type array $groups Array of group objects returned by the
	 *                         paginated query.
	 *     @type int   $total  Total count of all groups matching non-
	 *                         paginated query params.
	 * }
	 */
	public static function get_random( $limit = null, $page = null, $user_id = 0, $search_terms = false, $populate_extras = true, $exclude = false ) {
		global $wpdb;

		$pag_sql = $hidden_sql = $search_sql = $exclude_sql = '';

		if ( !empty( $limit ) && !empty( $page ) )
			$pag_sql = $wpdb->prepare( " LIMIT %d, %d", intval( ( $page - 1 ) * $limit), intval( $limit ) );

		if ( !is_user_logged_in() || ( !bp_current_user_can( 'bp_moderate' ) && ( $user_id != bp_loggedin_user_id() ) ) )
			$hidden_sql = "AND g.status != 'hidden'";

		if ( !empty( $search_terms ) ) {
			$search_terms_like = '%' . bp_esc_like( $search_terms ) . '%';
			$search_sql = $wpdb->prepare( " AND ( g.name LIKE %s OR g.description LIKE %s )", $search_terms_like, $search_terms_like );
		}

		if ( !empty( $exclude ) ) {
			$exclude     = wp_parse_id_list( $exclude );
			$exclude     = esc_sql( implode( ',', $exclude ) );
			$exclude_sql = " AND g.id NOT IN ({$exclude})";
		}

		$bp = buddypress();

		if ( !empty( $user_id ) ) {
			$user_id = esc_sql( $user_id );
			$paged_groups = $wpdb->get_results( "SELECT g.*, gm1.meta_value as total_member_count, gm2.meta_value as last_activity FROM {$bp->groups->table_name_groupmeta} gm1, {$bp->groups->table_name_groupmeta} gm2, {$bp->groups->table_name_members} m, {$bp->groups->table_name} g WHERE g.id = m.group_id AND g.id = gm1.group_id AND g.id = gm2.group_id AND gm2.meta_key = 'last_activity' AND gm1.meta_key = 'total_member_count' {$hidden_sql} {$search_sql} AND m.user_id = {$user_id} AND m.is_confirmed = 1 AND m.is_banned = 0 {$exclude_sql} ORDER BY rand() {$pag_sql}" );
			$total_groups = $wpdb->get_var( "SELECT COUNT(DISTINCT m.group_id) FROM {$bp->groups->table_name_members} m LEFT JOIN {$bp->groups->table_name_groupmeta} gm ON m.group_id = gm.group_id INNER JOIN {$bp->groups->table_name} g ON m.group_id = g.id WHERE gm.meta_key = 'last_activity'{$hidden_sql} {$search_sql} AND m.user_id = {$user_id} AND m.is_confirmed = 1 AND m.is_banned = 0 {$exclude_sql}" );
		} else {
			$paged_groups = $wpdb->get_results( "SELECT g.*, gm1.meta_value as total_member_count, gm2.meta_value as last_activity FROM {$bp->groups->table_name_groupmeta} gm1, {$bp->groups->table_name_groupmeta} gm2, {$bp->groups->table_name} g WHERE g.id = gm1.group_id AND g.id = gm2.group_id AND gm2.meta_key = 'last_activity' AND gm1.meta_key = 'total_member_count' {$hidden_sql} {$search_sql} {$exclude_sql} ORDER BY rand() {$pag_sql}" );
			$total_groups = $wpdb->get_var( "SELECT COUNT(DISTINCT g.id) FROM {$bp->groups->table_name_groupmeta} gm INNER JOIN {$bp->groups->table_name} g ON gm.group_id = g.id WHERE gm.meta_key = 'last_activity'{$hidden_sql} {$search_sql} {$exclude_sql}" );
		}

		if ( !empty( $populate_extras ) ) {
			foreach ( (array) $paged_groups as $group ) {
				$group_ids[] = $group->id;
			}
			$paged_groups = BP_Groups_Group::get_group_extras( $paged_groups, $group_ids, 'newest' );
		}

		return array( 'groups' => $paged_groups, 'total' => $total_groups );
	}

	/**
	 * Fetch extra data for a list of groups.
	 *
	 * This method is used throughout the class, by methods that take a
	 * $populate_extras parameter.
	 *
	 * Data fetched:
	 *
	 *     - Logged-in user's status within each group (is_member,
	 *       is_confirmed, is_pending, is_banned)
	 *
	 * @param array        $paged_groups Array of groups.
	 * @param string|array $group_ids    Array or comma-separated list of IDs matching
	 *                                   $paged_groups.
	 * @param string|bool  $type         Not used.
	 *
	 * @return array $paged_groups
	 */
	public static function get_group_extras( &$paged_groups, &$group_ids, $type = false ) {
		global $wpdb;

		if ( empty( $group_ids ) )
			return $paged_groups;

		$bp = buddypress();

		// Sanitize group IDs
		$group_ids = implode( ',', wp_parse_id_list( $group_ids ) );

		// Fetch the logged-in user's status within each group
		if ( is_user_logged_in() ) {
			$user_status_results = $wpdb->get_results( $wpdb->prepare( "SELECT group_id, is_confirmed, invite_sent FROM {$bp->groups->table_name_members} WHERE user_id = %d AND group_id IN ( {$group_ids} ) AND is_banned = 0", bp_loggedin_user_id() ) );
		} else {
			$user_status_results = array();
		}

		// Reindex
		$user_status = array();
		foreach ( $user_status_results as $user_status_result ) {
			$user_status[ $user_status_result->group_id ] = $user_status_result;
		}

		for ( $i = 0, $count = count( $paged_groups ); $i < $count; ++$i ) {
			$is_member = $is_invited = $is_pending = '0';
			$gid = $paged_groups[ $i ]->id;

			if ( isset( $user_status[ $gid ] ) ) {

				// is_confirmed means the user is a member
				if ( $user_status[ $gid ]->is_confirmed ) {
					$is_member = '1';

				// invite_sent means the user has been invited
				} elseif ( $user_status[ $gid ]->invite_sent ) {
					$is_invited = '1';

				// User has sent request, but has not been confirmed
				} else {
					$is_pending = '1';
				}
			}

			$paged_groups[ $i ]->is_member = $is_member;
			$paged_groups[ $i ]->is_invited = $is_invited;
			$paged_groups[ $i ]->is_pending = $is_pending;
		}

		if ( is_user_logged_in() ) {
			$user_banned = $wpdb->get_col( $wpdb->prepare( "SELECT group_id FROM {$bp->groups->table_name_members} WHERE is_banned = 1 AND user_id = %d AND group_id IN ( {$group_ids} )", bp_loggedin_user_id() ) );
		} else {
			$user_banned = array();
		}

		for ( $i = 0, $count = count( $paged_groups ); $i < $count; ++$i ) {
			$paged_groups[$i]->is_banned = false;

			foreach ( (array) $user_banned as $group_id ) {
				if ( $group_id == $paged_groups[$i]->id ) {
					$paged_groups[$i]->is_banned = true;
				}
			}
		}

		return $paged_groups;
	}

	/**
	 * Delete all invitations to a given group.
	 *
	 * @param int $group_id ID of the group whose invitations are being deleted.
	 *
	 * @return int|null Number of rows records deleted on success, null on
	 *                  failure.
	 */
	public static function delete_all_invites( $group_id ) {
		global $wpdb;

		$bp = buddypress();

		return $wpdb->query( $wpdb->prepare( "DELETE FROM {$bp->groups->table_name_members} WHERE group_id = %d AND invite_sent = 1", $group_id ) );
	}

	/**
	 * Get a total group count for the site.
	 *
	 * Will include hidden groups in the count only if
	 * current_user_can( 'bp_moderate' ).
	 *
	 * @return int Group count.
	 */
	public static function get_total_group_count() {
		global $wpdb;

		$hidden_sql = '';
		if ( !bp_current_user_can( 'bp_moderate' ) )
			$hidden_sql = "WHERE status != 'hidden'";

		$bp = buddypress();

		return $wpdb->get_var( "SELECT COUNT(id) FROM {$bp->groups->table_name} {$hidden_sql}" );
	}

	/**
	 * Get global count of forum topics in public groups (legacy forums).
	 *
	 * @param string $type Optional. If 'unreplied', count will be limited to
	 *                     those topics that have received no replies.
	 *
	 * @return int Forum topic count.
	 */
	public static function get_global_forum_topic_count( $type ) {
		global $bbdb, $wpdb;

		$bp = buddypress();

		if ( 'unreplied' == $type )
			$bp->groups->filter_sql = ' AND t.topic_posts = 1';

		/**
		 * Filters the portion of the SQL related to global count of forum topics in public groups.
		 *
		 * https://buddypress.trac.wordpress.org/ticket/4306.
		 *
		 * @since 1.6.0
		 *
		 * @param string $filter_sql SQL portion for the query.
		 * @param string $type       Type of forum topics to query for.
		 */
		$extra_sql = apply_filters( 'get_global_forum_topic_count_extra_sql', $bp->groups->filter_sql, $type );

		// Make sure the $extra_sql begins with an AND
		if ( 'AND' != substr( trim( strtoupper( $extra_sql ) ), 0, 3 ) )
			$extra_sql = ' AND ' . $extra_sql;

		return $wpdb->get_var( "SELECT COUNT(t.topic_id) FROM {$bbdb->topics} AS t, {$bp->groups->table_name} AS g LEFT JOIN {$bp->groups->table_name_groupmeta} AS gm ON g.id = gm.group_id WHERE (gm.meta_key = 'forum_id' AND gm.meta_value = t.forum_id) AND g.status = 'public' AND t.topic_status = '0' AND t.topic_sticky != '2' {$extra_sql} " );
	}

	/**
	 * Get the member count for a group.
	 *
	 * @param int $group_id Group ID.
	 *
	 * @return int Count of confirmed members for the group.
	 */
	public static function get_total_member_count( $group_id ) {
		global $wpdb;

		$bp = buddypress();

		return $wpdb->get_var( $wpdb->prepare( "SELECT COUNT(id) FROM {$bp->groups->table_name_members} WHERE group_id = %d AND is_confirmed = 1 AND is_banned = 0", $group_id ) );
	}

	/**
	 * Get a total count of all topics of a given status, across groups/forums.
	 *
	 * @since 1.5.0
	 *
	 * @param string      $status       Which group type to count. 'public', 'private',
	 *                                  'hidden', or 'all'. Default: 'public'.
	 * @param string|bool $search_terms Provided search terms.
	 *
	 * @return int The topic count
	 */
	public static function get_global_topic_count( $status = 'public', $search_terms = false ) {
		global $bbdb, $wpdb;

		switch ( $status ) {
			case 'all' :
				$status_sql = '';
				break;

			case 'hidden' :
				$status_sql = "AND g.status = 'hidden'";
				break;

			case 'private' :
				$status_sql = "AND g.status = 'private'";
				break;

			case 'public' :
			default :
				$status_sql = "AND g.status = 'public'";
				break;
		}

		$bp = buddypress();

		$sql = array();

		$sql['select'] = "SELECT COUNT(t.topic_id)";
		$sql['from']   = "FROM {$bbdb->topics} AS t INNER JOIN {$bp->groups->table_name_groupmeta} AS gm ON t.forum_id = gm.meta_value INNER JOIN {$bp->groups->table_name} AS g ON gm.group_id = g.id";
		$sql['where']  = "WHERE gm.meta_key = 'forum_id' {$status_sql} AND t.topic_status = '0' AND t.topic_sticky != '2'";

		if ( !empty( $search_terms ) ) {
			$search_terms_like = '%' . bp_esc_like( $search_terms ) . '%';
			$sql['where'] .= $wpdb->prepare( " AND ( t.topic_title LIKE %s )", $search_terms_like );
		}

		return $wpdb->get_var( implode( ' ', $sql ) );
	}

	/**
	 * Get an array containing ids for each group type.
	 *
	 * A bit of a kludge workaround for some issues
	 * with bp_has_groups().
	 *
	 * @since 1.7.0
	 *
	 * @return array
	 */
	public static function get_group_type_ids() {
		global $wpdb;

		$bp  = buddypress();
		$ids = array();

		$ids['all']     = $wpdb->get_col( "SELECT id FROM {$bp->groups->table_name}" );
		$ids['public']  = $wpdb->get_col( "SELECT id FROM {$bp->groups->table_name} WHERE status = 'public'" );
		$ids['private'] = $wpdb->get_col( "SELECT id FROM {$bp->groups->table_name} WHERE status = 'private'" );
		$ids['hidden']  = $wpdb->get_col( "SELECT id FROM {$bp->groups->table_name} WHERE status = 'hidden'" );

		return $ids;
	}
}
