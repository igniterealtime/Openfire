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
 * Base class for the BuddyPress Suggestions API.
 *
 * Originally built to power BuddyPress' at-mentions suggestions, it's flexible enough to be used
 * for similar kinds of future core requirements, or those desired by third-party developers.
 *
 * To implement a new suggestions service, create a new class that extends this one, and update
 * the list of default services in {@link bp_core_get_suggestions()}. If you're building a plugin,
 * it's recommend that you use the `bp_suggestions_services` filter to do this. :)
 *
 * While the implementation of the query logic is left to you, it should be as quick and efficient
 * as possible. When implementing the abstract methods in this class, pay close attention to the
 * recommendations provided in the phpDoc blocks, particularly the expected return types.
 *
 * @since 2.1.0
 */
abstract class BP_Suggestions {

	/**
	 * Default arguments common to all suggestions services.
	 *
	 * If your custom service requires further defaults, add them here.
	 *
	 * @since 2.1.0
	 * @var array
	 */
	protected $default_args = array(
		'limit' => 16,
		'term'  => '',
		'type'  => '',
	);

	/**
	 * Holds the arguments for the query (about to made to the suggestions service).
	 *
	 * This includes `$default_args`, as well as the user-supplied values.
	 *
	 * @since 2.1.0
	 * @var array
	 */
	protected $args = array(
	);


	/**
	 * Constructor.
	 *
	 * @param array $args Optional. If set, used as the parameters for the suggestions service query.
	 * @since 2.1.0
	 */
	public function __construct( array $args = array() ) {
		if ( ! empty( $args ) ) {
			$this->set_query( $args );
		}
	}

	/**
	 * Set the parameters for the suggestions service query.
	 *
	 * @param array $args {
	 *     @type int    $limit Maximum number of results to display. Optional, default: 16.
	 *     @type string $type  The name of the suggestion service to use for the request. Mandatory.
	 *     @type string $term  The suggestion service will try to find results that contain this string.
	 *                         Mandatory.
	 * }
	 * @since 2.1.0
	 */
	public function set_query( array $args = array() ) {
		$this->args = wp_parse_args( $args, $this->default_args );
	}

	/**
	 * Validate and sanitise the parameters for the suggestion service query.
	 *
	 * Be sure to call this class' version of this method when implementing it in your own service.
	 * If validation fails, you must return a WP_Error object.
	 *
	 * @since 2.1.0
	 *
	 * @return true|WP_Error If validation fails, return a WP_Error object. On success, return true (bool).
	 */
	public function validate() {
		$this->args['limit'] = absint( $this->args['limit'] );
		$this->args['term']  = trim( sanitize_text_field( $this->args['term'] ) );

		/**
		 * Filters the arguments to be validated for the BP_Suggestions query.
		 *
		 * @since 2.1.0
		 *
		 * @param BP_Suggestions $value Arguments to be validated.
		 * @param BP_Suggestions $this  Current BP_Suggestions instance.
		 */
		$this->args          = apply_filters( 'bp_suggestions_args', $this->args, $this );

		// Check for invalid or missing mandatory parameters.
		if ( ! $this->args['limit'] || ! $this->args['term'] ) {
			return new WP_Error( 'missing_parameter' );
		}

		// Check for blocked users (e.g. deleted accounts, or spammers).
		if ( is_user_logged_in() && ! bp_is_user_active( get_current_user_id() ) ) {
			return new WP_Error( 'invalid_user' );
		}

		/**
		 * Filters the status of validation for the BP_Suggestions query.
		 *
		 * @since 2.1.0
		 *
		 * @param bool           $value Whether or not the values are valid.
		 * @param BP_Suggestions $this  Current BP_Suggestions instance.
		 */
		return apply_filters( 'bp_suggestions_validate_args', true, $this );
	}

	/**
	 * Find and return a list of suggestions that match the query.
	 *
	 * The return type is important. If no matches are found, an empty array must be returned.
	 * Matches must be returned as objects in an array.
	 *
	 * The object format for each match must be: { 'ID': string, 'image': string, 'name': string }
	 * For example: { 'ID': 'admin', 'image': 'http://example.com/logo.png', 'name': 'Name Surname' }
	 *
	 * @since 2.1.0
	 *
	 * @return array|WP_Error Array of results. If there were problems, returns a WP_Error object.
	 */
	abstract public function get_suggestions();
}
