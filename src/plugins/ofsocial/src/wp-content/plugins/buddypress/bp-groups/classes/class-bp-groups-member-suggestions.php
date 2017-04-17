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
 * Adds support for user at-mentions (for users in a specific Group) to the Suggestions API.
 *
 * @since 2.1.0
 */
class BP_Groups_Member_Suggestions extends BP_Members_Suggestions {

	/**
	 * Default arguments for this suggestions service.
	 *
	 * @since 2.1.0
	 * @var array $args {
	 *     @type int    $group_id     Positive integers will restrict the search to members in that group.
	 *                                Negative integers will restrict the search to members in every other group.
	 *     @type int    $limit        Maximum number of results to display. Default: 16.
	 *     @type bool   $only_friends If true, only match the current user's friends. Default: false.
	 *     @type string $term         The suggestion service will try to find results that contain this string.
	 *                                Mandatory.
	 * }
	 */
	protected $default_args = array(
		'group_id'     => 0,
		'limit'        => 16,
		'only_friends' => false,
		'term'         => '',
		'type'         => '',
	);


	/**
	 * Validate and sanitise the parameters for the suggestion service query.
	 *
	 * @since 2.1.0
	 *
	 * @return true|WP_Error If validation fails, return a WP_Error object. On success, return true (bool).
	 */
	public function validate() {
		$this->args['group_id'] = (int) $this->args['group_id'];

		/**
		 * Filters the arguments used to validate and sanitize suggestion service query.
		 *
		 * @since 2.1.0
		 *
		 * @param array                        $args  Array of arguments for the suggestion service query.
		 * @param BP_Groups_Member_Suggestions $this  Instance of the current suggestion class.
		 */
		$this->args             = apply_filters( 'bp_groups_member_suggestions_args', $this->args, $this );

		// Check for invalid or missing mandatory parameters.
		if ( ! $this->args['group_id'] || ! bp_is_active( 'groups' ) ) {
			return new WP_Error( 'missing_requirement' );
		}

		// Check that the specified group_id exists, and that the current user can access it.
		$the_group = groups_get_group( array(
			'group_id'        => absint( $this->args['group_id'] ),
			'populate_extras' => true,
		) );

		if ( $the_group->id === 0 || ! $the_group->user_has_access ) {
			return new WP_Error( 'access_denied' );
		}

		/**
		 * Filters the validation results for the suggestion service query.
		 *
		 * @since 2.1.0
		 *
		 * @param bool|WP_Error                $value True if valid, WP_Error if not.
		 * @param BP_Groups_Member_Suggestions $this  Instance of the current suggestion class.
		 */
		return apply_filters( 'bp_groups_member_suggestions_validate_args', parent::validate(), $this );
	}

	/**
	 * Find and return a list of username suggestions that match the query.
	 *
	 * @since 2.1.0
	 *
	 * @return array|WP_Error Array of results. If there were problems, returns a WP_Error object.
	 */
	public function get_suggestions() {
		$user_query = array(
			'count_total'     => '',  // Prevents total count
			'populate_extras' => false,
			'type'            => 'alphabetical',

			'group_role'      => array( 'admin', 'member', 'mod' ),
			'page'            => 1,
			'per_page'        => $this->args['limit'],
			'search_terms'    => $this->args['term'],
			'search_wildcard' => 'right',
		);

		// Only return matches of friends of this user.
		if ( $this->args['only_friends'] && is_user_logged_in() ) {
			$user_query['user_id'] = get_current_user_id();
		}

		// Positive Group IDs will restrict the search to members in that group.
		if ( $this->args['group_id'] > 0 ) {
			$user_query['group_id'] = $this->args['group_id'];

		// Negative Group IDs will restrict the search to members in every other group.
		} else {
			$group_query = array(
				'count_total'     => '',  // Prevents total count
				'populate_extras' => false,
				'type'            => 'alphabetical',

				'group_id'        => absint( $this->args['group_id'] ),
				'group_role'      => array( 'admin', 'member', 'mod' ),
				'page'            => 1,
			);
			$group_users = new BP_Group_Member_Query( $group_query );

			if ( $group_users->results ) {
				$user_query['exclude'] = wp_list_pluck( $group_users->results, 'ID' );
			} else {
				$user_query['include'] = array( 0 );
			}
		}

		/**
		 * Filters the arguments for the user query for the Suggestion API.
		 *
		 * @since 2.1.0
		 *
		 * @param array                        $user_query Array of arguments for the query.
		 * @param BP_Groups_Member_Suggestions $this       Instance of the current suggestion class.
		 */
		$user_query = apply_filters( 'bp_groups_member_suggestions_query_args', $user_query, $this );
		if ( is_wp_error( $user_query ) ) {
			return $user_query;
		}


		if ( isset( $user_query['group_id'] ) ) {
			$user_query = new BP_Group_Member_Query( $user_query );
		} else {
			$user_query = new BP_User_Query( $user_query );
		}

		$results = array();
		foreach ( $user_query->results as $user ) {
			$result        = new stdClass();
			$result->ID    = $user->user_nicename;
			$result->image = bp_core_fetch_avatar( array( 'html' => false, 'item_id' => $user->ID ) );
			$result->name  = bp_core_get_user_displayname( $user->ID );

			$results[] = $result;
		}

		/**
		 * Filters the results of the member suggestions user query.
		 *
		 * @since 2.1.0
		 *
		 * @param array                        $results Array of member suggestions.
		 * @param BP_Groups_Member_Suggestions $this    Instance of the current suggestion class.
		 */
		return apply_filters( 'bp_groups_member_suggestions_get_suggestions', $results, $this );
	}
}
