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
 * Adds support for user at-mentions to the Suggestions API.
 *
 * This class is in the Core component because it's required by a class in the Groups component,
 * and Groups is loaded before Members (alphabetical order).
 *
 * @since 2.1.0
 */
class BP_Members_Suggestions extends BP_Suggestions {

	/**
	 * Default arguments for this suggestions service.
	 *
	 * @since 2.1.0
	 * @var array $args {
	 *     @type int    $limit        Maximum number of results to display. Default: 16.
	 *     @type bool   $only_friends If true, only match the current user's friends. Default: false.
	 *     @type string $term         The suggestion service will try to find results that contain this string.
	 *                                Mandatory.
	 * }
	 */
	protected $default_args = array(
		'limit'        => 10,
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
		$this->args['only_friends'] = (bool) $this->args['only_friends'];

		/**
		 * Filters the members suggestions args for the current user.
		 *
		 * @since 2.1.0
		 *
		 * @param array                  $args Array of arguments for the member suggestions.
		 * @param BP_Members_Suggestions $this Current BP_Members_Suggestions instance.
		 */
		$this->args                 = apply_filters( 'bp_members_suggestions_args', $this->args, $this );

		// Check for invalid or missing mandatory parameters.
		if ( $this->args['only_friends'] && ( ! bp_is_active( 'friends' ) || ! is_user_logged_in() ) ) {
			return new WP_Error( 'missing_requirement' );
		}

		/**
		 * Filters the validation status for the suggestion service query.
		 *
		 * @since 2.1.0
		 *
		 * @param bool|WP_Error          $value Results of validation check.
		 * @param BP_Members_Suggestions $this  Current BP_Members_Suggestions instance.
		 */
		return apply_filters( 'bp_members_suggestions_validate_args', parent::validate(), $this );
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

			'page'            => 1,
			'per_page'        => $this->args['limit'],
			'search_terms'    => $this->args['term'],
			'search_wildcard' => 'right',
		);

		// Only return matches of friends of this user.
		if ( $this->args['only_friends'] && is_user_logged_in() ) {
			$user_query['user_id'] = get_current_user_id();
		}

		/**
		 * Filters the members suggestions query args.
		 *
		 * @since 2.1.0
		 *
		 * @param array                  $user_query Array of query arguments.
		 * @param BP_Members_Suggestions $this       Current BP_Members_Suggestions instance.
		 */
		$user_query = apply_filters( 'bp_members_suggestions_query_args', $user_query, $this );
		if ( is_wp_error( $user_query ) ) {
			return $user_query;
		}


		$user_query = new BP_User_Query( $user_query );
		$results    = array();

		foreach ( $user_query->results as $user ) {
			$result        = new stdClass();
			$result->ID    = $user->user_nicename;
			$result->image = bp_core_fetch_avatar( array( 'html' => false, 'item_id' => $user->ID ) );
			$result->name  = bp_core_get_user_displayname( $user->ID );

			$results[] = $result;
		}

		/**
		 * Filters the members suggestions results.
		 *
		 * @since 2.1.0
		 *
		 * @param array                  $results Array of users to suggest.
		 * @param BP_Members_Suggestions $this    Current BP_Members_Suggestions instance.
		 */
		return apply_filters( 'bp_members_suggestions_get_suggestions', $results, $this );
	}
}
