<?php
/**
 * BuddyPress Member Loader.
 *
 * @package BuddyPress
 * @subpackage Members
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Defines the BuddyPress Members Component.
 */
class BP_Members_Component extends BP_Component {

	/**
	 * Member types.
	 *
	 * @see bp_register_member_type()
	 *
	 * @since 2.2.0
	 * @var array
	 */
	public $types = array();

	/**
	 * Start the members component creation process.
	 *
	 * @since 1.5.0
	 */
	public function __construct() {
		parent::start(
			'members',
			__( 'Members', 'buddypress' ),
			buddypress()->plugin_dir,
			array(
				'adminbar_myaccount_order' => 20,
				'search_query_arg' => 'members_search',
			)
		);
	}

	/**
	 * Include bp-members files.
	 *
	 * @see BP_Component::includes() for description of parameters.
	 *
	 * @param array $includes See {@link BP_Component::includes()}.
	 */
	public function includes( $includes = array() ) {

		// Always include these files.
		$includes = array(
			'actions',
			'classes',
			'filters',
			'screens',
			'template',
			'adminbar',
			'functions',
			'widgets',
			'cache',
		);

		if ( bp_is_active( 'activity' ) ) {
			$includes[] = 'activity';
		}

		// Include these only if in admin.
		if ( is_admin() ) {
			$includes[] = 'admin';
		}

		parent::includes( $includes );
	}

	/**
	 * Set up bp-members global settings.
	 *
	 * The BP_MEMBERS_SLUG constant is deprecated, and only used here for
	 * backwards compatibility.
	 *
	 * @since 1.5.0
	 *
	 * @see BP_Component::setup_globals() for description of parameters.
	 *
	 * @param array $args See {@link BP_Component::setup_globals()}.
	 */
	public function setup_globals( $args = array() ) {
		$bp = buddypress();

		/** Component Globals ************************************************
		 */

		// Define a slug, as a fallback for backpat.
		if ( !defined( 'BP_MEMBERS_SLUG' ) ) {
			define( 'BP_MEMBERS_SLUG', $this->id );
		}

		// Override any passed args.
		$args = array(
			'slug'            => BP_MEMBERS_SLUG,
			'root_slug'       => isset( $bp->pages->members->slug ) ? $bp->pages->members->slug : BP_MEMBERS_SLUG,
			'has_directory'   => true,
			'directory_title' => _x( 'Members', 'component directory title', 'buddypress' ),
			'search_string'   => __( 'Search Members...', 'buddypress' ),
			'global_tables'   => array(
				'table_name_last_activity' => bp_core_get_table_prefix() . 'bp_activity',
				'table_name_signups'       => bp_core_get_table_prefix() . 'signups',
			)
		);

		parent::setup_globals( $args );

		/** Logged in user ***************************************************
		 */

		// The core userdata of the user who is currently logged in.
		$bp->loggedin_user->userdata       = bp_core_get_core_userdata( bp_loggedin_user_id() );

		// Fetch the full name for the logged in user.
		$bp->loggedin_user->fullname       = isset( $bp->loggedin_user->userdata->display_name ) ? $bp->loggedin_user->userdata->display_name : '';

		// Hits the DB on single WP installs so get this separately.
		$bp->loggedin_user->is_super_admin = $bp->loggedin_user->is_site_admin = is_super_admin( bp_loggedin_user_id() );

		// The domain for the user currently logged in. eg: http://example.com/members/andy.
		$bp->loggedin_user->domain         = bp_core_get_user_domain( bp_loggedin_user_id() );

		/** Displayed user ***************************************************
		 */

		// The core userdata of the user who is currently being displayed.
		$bp->displayed_user->userdata = bp_core_get_core_userdata( bp_displayed_user_id() );

		// Fetch the full name displayed user.
		$bp->displayed_user->fullname = isset( $bp->displayed_user->userdata->display_name ) ? $bp->displayed_user->userdata->display_name : '';

		// The domain for the user currently being displayed.
		$bp->displayed_user->domain   = bp_core_get_user_domain( bp_displayed_user_id() );

		/** Signup ***********************************************************
		 */

		$bp->signup = new stdClass;

		/** Profiles Fallback ************************************************
		 */

		if ( ! bp_is_active( 'xprofile' ) ) {
			$bp->profile       = new stdClass;
			$bp->profile->slug = 'profile';
			$bp->profile->id   = 'profile';
		}
	}

	/**
	 * Set up canonical stack for this component.
	 *
	 * @since 2.1.0
	 */
	public function setup_canonical_stack() {
		$bp = buddypress();

		/** Default Profile Component ****************************************
		 */

		if ( defined( 'BP_DEFAULT_COMPONENT' ) && BP_DEFAULT_COMPONENT ) {
			$bp->default_component = BP_DEFAULT_COMPONENT;
		} else {
			if ( bp_is_active( 'activity' ) && isset( $bp->pages->activity ) ) {
				$bp->default_component = bp_get_activity_slug();
			} else {
				$bp->default_component = ( 'xprofile' === $bp->profile->id ) ? 'profile' : $bp->profile->id;
			}
		}

		/** Canonical Component Stack ****************************************
		 */

		if ( bp_displayed_user_id() ) {
			$bp->canonical_stack['base_url'] = bp_displayed_user_domain();

			if ( bp_current_component() ) {
				$bp->canonical_stack['component'] = bp_current_component();
			}

			if ( bp_current_action() ) {
				$bp->canonical_stack['action'] = bp_current_action();
			}

			if ( !empty( $bp->action_variables ) ) {
				$bp->canonical_stack['action_variables'] = bp_action_variables();
			}

			// Looking at the single member root/home, so assume the default.
			if ( ! bp_current_component() ) {
				$bp->current_component = $bp->default_component;

			// The canonical URL will not contain the default component.
			} elseif ( bp_is_current_component( $bp->default_component ) && ! bp_current_action() ) {
				unset( $bp->canonical_stack['component'] );
			}

			// If we're on a spammer's profile page, only users with the 'bp_moderate' cap
			// can view subpages on the spammer's profile.
			//
			// users without the cap trying to access a spammer's subnav page will get
			// redirected to the root of the spammer's profile page.  this occurs by
			// by removing the component in the canonical stack.
			if ( bp_is_user_spammer( bp_displayed_user_id() ) && ! bp_current_user_can( 'bp_moderate' ) ) {
				unset( $bp->canonical_stack['component'] );
			}
		}
	}

	/**
	 * Set up fall-back component navigation if XProfile is inactive.
	 *
	 * @since 1.5.0
	 *
	 * @see BP_Component::setup_nav() for a description of arguments.
	 *
	 * @param array $main_nav Optional. See BP_Component::setup_nav() for
	 *                        description.
	 * @param array $sub_nav  Optional. See BP_Component::setup_nav() for
	 *                        description.
	 */
	public function setup_nav( $main_nav = array(), $sub_nav = array() ) {

		// Bail if XProfile component is active.
		if ( bp_is_active( 'xprofile' ) ) {
			return;
		}

		// Don't set up navigation if there's no member.
		if ( ! is_user_logged_in() && ! bp_is_user() ) {
			return;
		}

		// Determine user to use.
		if ( bp_displayed_user_domain() ) {
			$user_domain = bp_displayed_user_domain();
		} elseif ( bp_loggedin_user_domain() ) {
			$user_domain = bp_loggedin_user_domain();
		} else {
			return;
		}

		$slug         = bp_get_profile_slug();
		$profile_link = trailingslashit( $user_domain . $slug );

		// Setup the main navigation.
		$main_nav = array(
			'name'                => _x( 'Profile', 'Member profile main navigation', 'buddypress' ),
			'slug'                => $slug,
			'position'            => 20,
			'screen_function'     => 'bp_members_screen_display_profile',
			'default_subnav_slug' => 'public',
			'item_css_id'         => buddypress()->profile->id
		);

		// Setup the subnav items for the member profile.
		$sub_nav[] = array(
			'name'            => _x( 'View', 'Member profile view', 'buddypress' ),
			'slug'            => 'public',
			'parent_url'      => $profile_link,
			'parent_slug'     => $slug,
			'screen_function' => 'bp_members_screen_display_profile',
			'position'        => 10
		);

		parent::setup_nav( $main_nav, $sub_nav );
	}

	/**
	 * Set up the title for pages and <title>.
	 */
	public function setup_title() {
		$bp = buddypress();

		if ( bp_is_my_profile() ) {
			$bp->bp_options_title = __( 'You', 'buddypress' );
		} elseif ( bp_is_user() ) {
			$bp->bp_options_title  = bp_get_displayed_user_fullname();
			$bp->bp_options_avatar = bp_core_fetch_avatar( array(
				'item_id' => bp_displayed_user_id(),
				'type'    => 'thumb',
				'alt'     => sprintf( __( 'Profile picture of %s', 'buddypress' ), $bp->bp_options_title )
			) );
		}

		parent::setup_title();
	}

	/**
	 * Setup cache groups.
	 *
	 * @since 2.2.0
	 */
	public function setup_cache_groups() {

		// Global groups.
		wp_cache_add_global_groups( array(
			'bp_last_activity',
			'bp_member_type'
		) );

		parent::setup_cache_groups();
	}
}

/**
 * Set up the bp-members component.
 */
function bp_setup_members() {
	buddypress()->members = new BP_Members_Component();
}
add_action( 'bp_setup_components', 'bp_setup_members', 1 );
