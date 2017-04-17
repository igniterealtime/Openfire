<?php
/**
 * BuddyPress Activity Streams Loader.
 *
 * An activity stream component, for users, groups, and site tracking.
 *
 * @package BuddyPress
 * @subpackage ActivityCore
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Main Activity Class.
 *
 * @since 1.5.0
 */
class BP_Activity_Component extends BP_Component {

	/**
	 * Start the activity component setup process.
	 *
	 * @since 1.5.0
	 */
	public function __construct() {
		parent::start(
			'activity',
			__( 'Activity Streams', 'buddypress' ),
			buddypress()->plugin_dir,
			array(
				'adminbar_myaccount_order' => 10,
				'search_query_arg' => 'activity_search',
			)
		);
	}

	/**
	 * Include component files.
	 *
	 * @since 1.5.0
	 *
	 * @see BP_Component::includes() for a description of arguments.
	 *
	 * @param array $includes See BP_Component::includes() for a description.
	 */
	public function includes( $includes = array() ) {

		// Files to include.
		$includes = array(
			'cssjs',
			'actions',
			'screens',
			'filters',
			'classes',
			'template',
			'functions',
			'notifications',
			'cache'
		);

		// Load Akismet support if Akismet is configured.
		$akismet_key = bp_get_option( 'wordpress_api_key' );

		/** This filter is documented in bp-activity/bp-activity-actions.php */
		if ( defined( 'AKISMET_VERSION' ) && class_exists( 'Akismet' ) && ( ! empty( $akismet_key ) || defined( 'WPCOM_API_KEY' ) ) && apply_filters( 'bp_activity_use_akismet', bp_is_akismet_active() ) ) {
			$includes[] = 'akismet';
		}

		if ( is_admin() ) {
			$includes[] = 'admin';
		}

		parent::includes( $includes );
	}

	/**
	 * Set up component global variables.
	 *
	 * The BP_ACTIVITY_SLUG constant is deprecated, and only used here for
	 * backwards compatibility.
	 *
	 * @since 1.5.0
	 *
	 * @see BP_Component::setup_globals() for a description of arguments.
	 *
	 * @param array $args See BP_Component::setup_globals() for a description.
	 */
	public function setup_globals( $args = array() ) {
		$bp = buddypress();

		// Define a slug, if necessary.
		if ( ! defined( 'BP_ACTIVITY_SLUG' ) ) {
			define( 'BP_ACTIVITY_SLUG', $this->id );
		}

		// Global tables for activity component.
		$global_tables = array(
			'table_name'      => $bp->table_prefix . 'bp_activity',
			'table_name_meta' => $bp->table_prefix . 'bp_activity_meta',
		);

		// Metadata tables for groups component.
		$meta_tables = array(
			'activity' => $bp->table_prefix . 'bp_activity_meta',
		);

		// All globals for activity component.
		// Note that global_tables is included in this array.
		$args = array(
			'slug'                  => BP_ACTIVITY_SLUG,
			'root_slug'             => isset( $bp->pages->activity->slug ) ? $bp->pages->activity->slug : BP_ACTIVITY_SLUG,
			'has_directory'         => true,
			'directory_title'       => _x( 'Site-Wide Activity', 'component directory title', 'buddypress' ),
			'notification_callback' => 'bp_activity_format_notifications',
			'search_string'         => __( 'Search Activity...', 'buddypress' ),
			'global_tables'         => $global_tables,
			'meta_tables'           => $meta_tables,
		);

		parent::setup_globals( $args );
	}

	/**
	 * Set up component navigation.
	 *
	 * @since 1.5.0
	 *
	 * @see BP_Component::setup_nav() for a description of arguments.
	 * @uses bp_is_active()
	 * @uses is_user_logged_in()
	 * @uses bp_get_friends_slug()
	 * @uses bp_get_groups_slug()
	 *
	 * @param array $main_nav Optional. See BP_Component::setup_nav() for description.
	 * @param array $sub_nav  Optional. See BP_Component::setup_nav() for description.
	 */
	public function setup_nav( $main_nav = array(), $sub_nav = array() ) {

		// Stop if there is no user displayed or logged in.
		if ( ! is_user_logged_in() && ! bp_displayed_user_id() ) {
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

		$slug          = bp_get_activity_slug();
		$activity_link = trailingslashit( $user_domain . $slug );

		// Add 'Activity' to the main navigation.
		$main_nav = array(
			'name'                => _x( 'Activity', 'Profile activity screen nav', 'buddypress' ),
			'slug'                => $slug,
			'position'            => 10,
			'screen_function'     => 'bp_activity_screen_my_activity',
			'default_subnav_slug' => 'just-me',
			'item_css_id'         => $this->id
		);

		// Add the subnav items to the activity nav item if we are using a theme that supports this.
		$sub_nav[] = array(
			'name'            => _x( 'Personal', 'Profile activity screen sub nav', 'buddypress' ),
			'slug'            => 'just-me',
			'parent_url'      => $activity_link,
			'parent_slug'     => $slug,
			'screen_function' => 'bp_activity_screen_my_activity',
			'position'        => 10
		);

		// Check @mentions.
		if ( bp_activity_do_mentions() ) {
			$sub_nav[] = array(
				'name'            => _x( 'Mentions', 'Profile activity screen sub nav', 'buddypress' ),
				'slug'            => 'mentions',
				'parent_url'      => $activity_link,
				'parent_slug'     => $slug,
				'screen_function' => 'bp_activity_screen_mentions',
				'position'        => 20,
				'item_css_id'     => 'activity-mentions'
			);
		}

		// Favorite activity items.
		if ( bp_activity_can_favorite() ) {
			$sub_nav[] = array(
				'name'            => _x( 'Favorites', 'Profile activity screen sub nav', 'buddypress' ),
				'slug'            => 'favorites',
				'parent_url'      => $activity_link,
				'parent_slug'     => $slug,
				'screen_function' => 'bp_activity_screen_favorites',
				'position'        => 30,
				'item_css_id'     => 'activity-favs'
			);
		}

		// Additional menu if friends is active.
		if ( bp_is_active( 'friends' ) ) {
			$sub_nav[] = array(
				'name'            => _x( 'Friends', 'Profile activity screen sub nav', 'buddypress' ),
				'slug'            => bp_get_friends_slug(),
				'parent_url'      => $activity_link,
				'parent_slug'     => $slug,
				'screen_function' => 'bp_activity_screen_friends',
				'position'        => 40,
				'item_css_id'     => 'activity-friends'
			) ;
		}

		// Additional menu if groups is active.
		if ( bp_is_active( 'groups' ) ) {
			$sub_nav[] = array(
				'name'            => _x( 'Groups', 'Profile activity screen sub nav', 'buddypress' ),
				'slug'            => bp_get_groups_slug(),
				'parent_url'      => $activity_link,
				'parent_slug'     => $slug,
				'screen_function' => 'bp_activity_screen_groups',
				'position'        => 50,
				'item_css_id'     => 'activity-groups'
			);
		}

		parent::setup_nav( $main_nav, $sub_nav );
	}

	/**
	 * Set up the component entries in the WordPress Admin Bar.
	 *
	 * @since 1.5.0
	 *
	 * @see BP_Component::setup_nav() for a description of the $wp_admin_nav
	 *      parameter array.
	 * @uses is_user_logged_in()
	 * @uses trailingslashit()
	 * @uses bp_get_total_mention_count_for_user()
	 * @uses bp_loggedin_user_id()
	 * @uses bp_is_active()
	 * @uses bp_get_friends_slug()
	 * @uses bp_get_groups_slug()
	 *
	 * @param array $wp_admin_nav See BP_Component::setup_admin_bar() for a
	 *                            description.
	 */
	public function setup_admin_bar( $wp_admin_nav = array() ) {

		// Menus for logged in user.
		if ( is_user_logged_in() ) {

			// Setup the logged in user variables.
			$activity_link = trailingslashit( bp_loggedin_user_domain() . bp_get_activity_slug() );

			// Unread message count.
			if ( bp_activity_do_mentions() ) {
				$count = bp_get_total_mention_count_for_user( bp_loggedin_user_id() );
				if ( !empty( $count ) ) {
					$title = sprintf( _x( 'Mentions <span class="count">%s</span>', 'Toolbar Mention logged in user', 'buddypress' ), bp_core_number_format( $count ) );
				} else {
					$title = _x( 'Mentions', 'Toolbar Mention logged in user', 'buddypress' );
				}
			}

			// Add the "Activity" sub menu.
			$wp_admin_nav[] = array(
				'parent' => buddypress()->my_account_menu_id,
				'id'     => 'my-account-' . $this->id,
				'title'  => _x( 'Activity', 'My Account Activity sub nav', 'buddypress' ),
				'href'   => $activity_link
			);

			// Personal.
			$wp_admin_nav[] = array(
				'parent' => 'my-account-' . $this->id,
				'id'     => 'my-account-' . $this->id . '-personal',
				'title'  => _x( 'Personal', 'My Account Activity sub nav', 'buddypress' ),
				'href'   => $activity_link
			);

			// Mentions.
			if ( bp_activity_do_mentions() ) {
				$wp_admin_nav[] = array(
					'parent' => 'my-account-' . $this->id,
					'id'     => 'my-account-' . $this->id . '-mentions',
					'title'  => $title,
					'href'   => trailingslashit( $activity_link . 'mentions' )
				);
			}

			// Favorite activity items.
			if ( bp_activity_can_favorite() ) {
				$wp_admin_nav[] = array(
					'parent' => 'my-account-' . $this->id,
					'id'     => 'my-account-' . $this->id . '-favorites',
					'title'  => _x( 'Favorites', 'My Account Activity sub nav', 'buddypress' ),
					'href'   => trailingslashit( $activity_link . 'favorites' )
				);
			}

			// Friends?
			if ( bp_is_active( 'friends' ) ) {
				$wp_admin_nav[] = array(
					'parent' => 'my-account-' . $this->id,
					'id'     => 'my-account-' . $this->id . '-friends',
					'title'  => _x( 'Friends', 'My Account Activity sub nav', 'buddypress' ),
					'href'   => trailingslashit( $activity_link . bp_get_friends_slug() )
				);
			}

			// Groups?
			if ( bp_is_active( 'groups' ) ) {
				$wp_admin_nav[] = array(
					'parent' => 'my-account-' . $this->id,
					'id'     => 'my-account-' . $this->id . '-groups',
					'title'  => _x( 'Groups', 'My Account Activity sub nav', 'buddypress' ),
					'href'   => trailingslashit( $activity_link . bp_get_groups_slug() )
				);
			}
		}

		parent::setup_admin_bar( $wp_admin_nav );
	}

	/**
	 * Set up the title for pages and <title>.
	 *
	 * @since 1.5.0
	 *
	 * @uses bp_is_activity_component()
	 * @uses bp_is_my_profile()
	 * @uses bp_core_fetch_avatar()
	 */
	public function setup_title() {

		// Adjust title based on view.
		if ( bp_is_activity_component() ) {
			$bp = buddypress();

			if ( bp_is_my_profile() ) {
				$bp->bp_options_title = _x( 'My Activity', 'Page and <title>', 'buddypress' );
			} else {
				$bp->bp_options_avatar = bp_core_fetch_avatar( array(
					'item_id' => bp_displayed_user_id(),
					'type'    => 'thumb',
					'alt'	  => sprintf( __( 'Profile picture of %s', 'buddypress' ), bp_get_displayed_user_fullname() )
				) );
				$bp->bp_options_title  = bp_get_displayed_user_fullname();
			}
		}

		parent::setup_title();
	}

	/**
	 * Set up actions necessary for the component.
	 *
	 * @since 1.6.0
	 */
	public function setup_actions() {

		// Spam prevention.
		add_action( 'bp_include', 'bp_activity_setup_akismet' );

		parent::setup_actions();
	}

	/**
	 * Setup cache groups.
	 *
	 * @since 2.2.0
	 */
	public function setup_cache_groups() {

		// Global groups.
		wp_cache_add_global_groups( array(
			'bp_activity',
			'bp_activity_comments',
			'activity_meta'
		) );

		parent::setup_cache_groups();
	}
}

/**
 * Bootstrap the Activity component.
 */
function bp_setup_activity() {
	buddypress()->activity = new BP_Activity_Component();
}
add_action( 'bp_setup_components', 'bp_setup_activity', 6 );
