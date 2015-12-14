<?php
/**
 * BuddyPress Forums Loader.
 *
 * A discussion forums component. Comes bundled with bbPress stand-alone.
 *
 * Note: The bp-forums component has been retired. Use the bbPress WordPress
 * plugin instead.
 *
 * @package BuddyPress
 * @subpackage ForumsLoader
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

class BP_Forums_Component extends BP_Component {

	/**
	 * Start the forums component creation process.
	 *
	 * @since 1.5.0
	 */
	public function __construct() {
		parent::start(
			'forums',
			__( 'Discussion Forums', 'buddypress' ),
			buddypress()->plugin_dir,
			array(
				'adminbar_myaccount_order' => 80
			)
		);
	}

	/**
	 * Set up bp-forums global settings.
	 *
	 * The BP_FORUMS_SLUG constant is deprecated, and only used here for
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

		// Define the parent forum ID.
		if ( ! defined( 'BP_FORUMS_PARENT_FORUM_ID' ) ) {
			define( 'BP_FORUMS_PARENT_FORUM_ID', 1 );
		}

		// Define a slug, if necessary.
		if ( ! defined( 'BP_FORUMS_SLUG' ) ) {
			define( 'BP_FORUMS_SLUG', $this->id );
		}

		// The location of the bbPress stand-alone config file.
		$bbconfig = bp_core_get_root_option( 'bb-config-location' );
		if ( '' !== $bbconfig ) {
			$this->bbconfig = $bbconfig;
		}

		// All globals for messaging component.
		// Note that global_tables is included in this array.
		$globals = array(
			'slug'                  => BP_FORUMS_SLUG,
			'root_slug'             => isset( $bp->pages->forums->slug ) ? $bp->pages->forums->slug : BP_FORUMS_SLUG,
			'has_directory'         => true,
			'notification_callback' => 'messages_format_notifications',
			'search_string'         => __( 'Search Forums...', 'buddypress' ),
		);

		parent::setup_globals( $globals );
	}

	/**
	 * Include bp-forums files.
	 *
	 * @see BP_Component::includes() for description of parameters.
	 *
	 * @param array $includes See {@link BP_Component::includes()}.
	 */
	public function includes( $includes = array() ) {

		// Files to include.
		$includes = array(
			'actions',
			'screens',
			'classes',
			'filters',
			'template',
			'functions',
		);

		// bbPress stand-alone.
		if ( ! defined( 'BB_PATH' ) ) {
			$includes[] = 'bbpress-sa';
		}

		// Admin-specific code.
		if ( is_admin() ) {
			$includes[] = 'deprecated/1.6';
			$includes[] = 'deprecated/1.7';
		}

		parent::includes( $includes );
	}

	/**
	 * Set up component navigation.
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

		// Stop if forums haven't been set up yet.
		if ( ! bp_forums_is_installed_correctly() ) {
			return;
		}

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

		// User link.
		$slug        = bp_get_forums_slug();
		$forums_link = trailingslashit( $user_domain . $slug );

		// Add 'Forums' to the main navigation.
		$main_nav = array(
			'name'                => __( 'Forums', 'buddypress' ),
			'slug'                => $slug,
			'position'            => 80,
			'screen_function'     => 'bp_member_forums_screen_topics',
			'default_subnav_slug' => 'topics',
			'item_css_id'         => $this->id
		);

		// Topics started.
		$sub_nav[] = array(
			'name'            => __( 'Topics Started', 'buddypress' ),
			'slug'            => 'topics',
			'parent_url'      => $forums_link,
			'parent_slug'     => $slug,
			'screen_function' => 'bp_member_forums_screen_topics',
			'position'        => 20,
			'item_css_id'     => 'topics'
		);

		// Topics replied to.
		$sub_nav[] = array(
			'name'            => __( 'Replied To', 'buddypress' ),
			'slug'            => 'replies',
			'parent_url'      => $forums_link,
			'parent_slug'     => $slug,
			'screen_function' => 'bp_member_forums_screen_replies',
			'position'        => 40,
			'item_css_id'     => 'replies'
		);

		parent::setup_nav( $main_nav, $sub_nav );
	}

	/**
	 * Set up bp-forums integration with the WordPress admin bar.
	 *
	 * @since 1.5.0
	 *
	 * @see BP_Component::setup_admin_bar() for a description of arguments.
	 *
	 * @param array $wp_admin_nav See BP_Component::setup_admin_bar()
	 *                            for description.
	 */
	public function setup_admin_bar( $wp_admin_nav = array() ) {

		// Menus for logged in user.
		if ( is_user_logged_in() ) {

			// Setup the logged in user variables.
			$forums_link = trailingslashit( bp_loggedin_user_domain() . bp_get_forums_slug() );

			// Add the "My Account" sub menus.
			$wp_admin_nav[] = array(
				'parent' => buddypress()->my_account_menu_id,
				'id'     => 'my-account-' . $this->id,
				'title'  => __( 'Forums', 'buddypress' ),
				'href'   => $forums_link
			);

			// Topics.
			$wp_admin_nav[] = array(
				'parent' => 'my-account-' . $this->id,
				'id'     => 'my-account-' . $this->id . '-topics-started',
				'title'  => __( 'Topics Started', 'buddypress' ),
				'href'   => $forums_link
			);

			// Replies.
			$wp_admin_nav[] = array(
				'parent' => 'my-account-' . $this->id,
				'id'     => 'my-account-' . $this->id . '-replies',
				'title'  => __( 'Replies', 'buddypress' ),
				'href'   => trailingslashit( $forums_link . 'replies' )
			);

			// Favorites.
			$wp_admin_nav[] = array(
				'parent' => 'my-account-' . $this->id,
				'id'     => 'my-account-' . $this->id . '-favorite-topics',
				'title'  => __( 'Favorite Topics', 'buddypress' ),
				'href'   => trailingslashit( $forums_link . 'favorites' )
			);
		}

		parent::setup_admin_bar( $wp_admin_nav );
	}

	/**
	 * Set up the title for pages and the <title> element.
	 */
	public function setup_title() {

		// Adjust title based on view.
		if ( bp_is_forums_component() ) {
			$bp = buddypress();

			if ( bp_is_my_profile() ) {
				$bp->bp_options_title = __( 'Forums', 'buddypress' );
			} else {
				$bp->bp_options_avatar = bp_core_fetch_avatar( array(
					'item_id' => bp_displayed_user_id(),
					'type'    => 'thumb',
					'alt'     => sprintf( __( 'Profile picture of %s', 'buddypress' ), bp_get_displayed_user_fullname() )
				) );
				$bp->bp_options_title  = bp_get_displayed_user_fullname();
			}
		}

		parent::setup_title();
	}
}

/**
 * Set up the bp-forums component.
 */
function bp_setup_forums() {
	buddypress()->forums = new BP_Forums_Component();
}
add_action( 'bp_setup_components', 'bp_setup_forums', 6 );
