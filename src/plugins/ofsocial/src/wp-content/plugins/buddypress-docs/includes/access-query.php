<?php

class BP_Docs_Access_Query {
	protected $user_id;
	protected $tax_query = array();
	protected $user_groups = array();
	protected $levels = array();

	public static function init( $user_id = 0 ) {
		static $instance;

		if ( empty( $instance ) ) {
			$instance = new BP_Docs_Access_Query( $user_id );
		}

		return $instance;
	}

	public function __construct( $user_id = 0 ) {
		$this->user_id = intval( $user_id );
		$this->set_up_levels();
		$this->prepare_tax_query();
	}

	protected function set_up_levels() {
		// Everyone can see 'anyone' docs
		$this->levels[] = bp_docs_get_access_term_anyone();

		// Logged-in users
		// Note that we're not verifying that the user actually exists
		// For now this kind of check is up to whoever's instantiating
		if ( $this->user_id != 0 ) {
			$this->levels[] = bp_docs_get_access_term_loggedin();

			if ( bp_is_active('groups') ) {

				$this->set_up_user_groups();

				if ( isset($this->user_groups['groups']) ) {
					foreach ( $this->user_groups['groups'] as $member_group ) {
						$this->levels[] = bp_docs_get_access_term_group_member( $member_group );
					}
				}

				// admins-mods
				if ( isset($this->user_groups['admin_mod_of']) ) {
					foreach ( $this->user_groups['admin_mod_of'] as $adminmod_group ) {
						$this->levels[] = bp_docs_get_access_term_group_adminmod( $adminmod_group );
					}
				}
			}

			// no-one
			// creator
			// @todo What's the difference?
			$this->levels[] = bp_docs_get_access_term_user( $this->user_id );
		}
	}

	protected function prepare_tax_query() {
		$this->tax_query[] = array(
			'terms'    => $this->levels,
			'taxonomy' => bp_docs_get_access_tax_name(),
			'field'    => 'slug',
			'operator' => 'IN',
		);
	}

	/**
	 * Get a list of a user's groups, as well as those groups of which
	 * the user is an admin or mod
	 *
	 * @since 1.2
	 */
	protected function set_up_user_groups() {
		$groups                      = BP_Groups_Member::get_group_ids( $this->user_id );
		$this->user_groups['groups'] = $groups['groups'];

		$admin_groups                      = BP_Groups_Member::get_is_admin_of( $this->user_id );
		$mod_groups                        = BP_Groups_Member::get_is_mod_of( $this->user_id );
		$this->user_groups['admin_mod_of'] = array_merge( wp_list_pluck( $admin_groups['groups'], 'id' ), wp_list_pluck( $mod_groups['groups'], 'id' ) );
	}

	/**
	 * Returns the tax_query param for the WP_Query args
	 *
	 * @since 1.2
	 * @return array
	 */
	public function get_tax_query() {
		// bp_moderate users can see anything, so no query needed
		if ( user_can( $this->user_id, 'bp_moderate' ) ) {
			return array();
		}

		return $this->tax_query;
	}

	/**
	 * Fetch a list of Doc IDs that are forbidden for the user
	 *
	 * @since 1.2.8
	 */
	public function get_doc_ids() {
		remove_action( 'pre_get_posts', 'bp_docs_general_access_protection', 28 );

		$tax_query = $this->get_tax_query();
		foreach ( $tax_query as &$tq ) {
			$tq['operator'] = "NOT IN";
		}

		// If the tax_query is empty, no docs are forbidden
		if ( empty( $tax_query ) ) {
			$forbidden_fruit_ids = array();
		} else {
			$forbidden_fruit = new WP_Query( array(
				'post_type' => bp_docs_get_post_type_name(),
				'posts_per_page' => -1,
				'nopaging' => true,
				'tax_query' => $tax_query,
				'update_post_term_cache' => false,
				'update_post_meta_cache' => false,
				'no_found_rows' => 1,
				'fields' => 'ids',
			) );
			$forbidden_fruit_ids = $forbidden_fruit->posts;
		}

		add_action( 'pre_get_posts', 'bp_docs_general_access_protection', 28 );

		return $forbidden_fruit_ids;
	}
}

/**
 * Wrapper function for BP_Docs_Access_Query singleton
 *
 * @since 1.2.8
 */
function bp_docs_access_query() {
	return BP_Docs_Access_Query::init( bp_loggedin_user_id() );
}

/**
 * Keep private Docs out of primary WP queries
 *
 * By catching the query at pre_get_posts, we ensure that all queries are
 * filtered appropriately, whether they originate with BuddyPress Docs or not
 * (as in the case of search)
 *
 * @since 1.2.8
 */
function bp_docs_general_access_protection( $query ) {
	// Access is unlimited when viewing your own profile, or when the
	// current user is a site admin
	if ( bp_is_my_profile() || current_user_can( 'bp_moderate' ) ) {
		return;
	}

	// We only need to filter when BP Docs could possibly show up in the
	// results, so we check the post type, and bail if the post_type rules
	// out Docs to begin with
	$queried_post_type = $query->get( 'post_type' );
	$pt = bp_docs_get_post_type_name();
	$is_bp_doc_query = is_array( $queried_post_type ) ? in_array( $pt, $queried_post_type ) : $pt == $queried_post_type;

	if ( ! $queried_post_type ||
	     'any' == $queried_post_type ||
	     $is_bp_doc_query
	) {

		$bp_docs_access_query = bp_docs_access_query();

		if ( $pt == $queried_post_type ) {
			// Use a tax query if possible
			$tax_query = $query->get( 'tax_query' );
			if ( ! $tax_query ) {
				$tax_query = array();
			}

			$query->set( 'tax_query', array_merge( $tax_query, $bp_docs_access_query->get_tax_query() ) );

		} else {
			// When it's not a straight bp_doc query, a tax_query
			// approach won't work (because the taxonomy in
			// question only applies to bp_docs, and conditional
			// tax_query is not supported by WP). Instead, get a
			// list of off-limits Docs and pass to post__not_in
			$exclude = $bp_docs_access_query->get_doc_ids();

			if ( ! empty( $exclude ) ) {
				$not_in = $query->get( 'post__not_in' );
				$query->set( 'post__not_in', array_merge( (array) $not_in, $exclude ) );
			}

		}
	}
}
// Hooked at an oddball priority to avoid conflicts with nested actions and
// other plugins using 'pre_get_posts'. See https://github.com/boonebgorges/buddypress-docs/issues/425
add_action( 'pre_get_posts', 'bp_docs_general_access_protection', 28 );
