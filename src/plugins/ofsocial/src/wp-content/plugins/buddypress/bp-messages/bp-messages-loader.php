<?php
/**
 * BuddyPress Messages Loader.
 *
 * A private messages component, for users to send messages to each other.
 *
 * @package BuddyPress
 * @subpackage MessagesLoader
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Implementation of BP_Component for the Messages component.
 *
 * @since 1.5.0
 */
class BP_Messages_Component extends BP_Component {

	/**
	 * If this is true, the Message autocomplete will return friends only, unless
	 * this is set to false, in which any matching users will be returned.
	 *
	 * @since 1.5.0
	 * @var bool
	 */
	public $autocomplete_all;

	/**
	 * Start the messages component creation process.
	 *
	 * @since 1.5.0
	 */
	public function __construct() {
		parent::start(
			'messages',
			__( 'Private Messages', 'buddypress' ),
			buddypress()->plugin_dir,
			array(
				'adminbar_myaccount_order' => 50,
				'features'                 => array( 'star' )
			)
		);
	}

	/**
	 * Include files.
	 *
	 * @since 1.5.0
	 *
	 * @param array $includes See {BP_Component::includes()} for details.
	 */
	public function includes( $includes = array() ) {

		// Files to include.
		$includes = array(
			'cssjs',
			'cache',
			'actions',
			'screens',
			'classes',
			'filters',
			'template',
			'functions',
			'notifications',
			'widgets',
		);

		// Conditional includes.
		if ( bp_is_active( $this->id, 'star' ) ) {
			$includes[] = 'star';
		}

		parent::includes( $includes );
	}

	/**
	 * Set up globals for the Messages component.
	 *
	 * The BP_MESSAGES_SLUG constant is deprecated, and only used here for
	 * backwards compatibility.
	 *
	 * @since 1.5.0
	 *
	 * @param array $args Not used.
	 */
	public function setup_globals( $args = array() ) {
		$bp = buddypress();

		// Define a slug, if necessary.
		if ( ! defined( 'BP_MESSAGES_SLUG' ) ) {
			define( 'BP_MESSAGES_SLUG', $this->id );
		}

		// Global tables for messaging component.
		$global_tables = array(
			'table_name_notices'    => $bp->table_prefix . 'bp_messages_notices',
			'table_name_messages'   => $bp->table_prefix . 'bp_messages_messages',
			'table_name_recipients' => $bp->table_prefix . 'bp_messages_recipients',
			'table_name_meta'       => $bp->table_prefix . 'bp_messages_meta',
		);

		// Metadata tables for messaging component.
		$meta_tables = array(
			'message' => $bp->table_prefix . 'bp_messages_meta',
		);

		$this->autocomplete_all = defined( 'BP_MESSAGES_AUTOCOMPLETE_ALL' );

		// All globals for messaging component.
		// Note that global_tables is included in this array.
		parent::setup_globals( array(
			'slug'                  => BP_MESSAGES_SLUG,
			'has_directory'         => false,
			'notification_callback' => 'messages_format_notifications',
			'search_string'         => __( 'Search Messages...', 'buddypress' ),
			'global_tables'         => $global_tables,
			'meta_tables'           => $meta_tables
		) );
	}

	/**
	 * Set up navigation for user pages.
	 *
	 * @param array $main_nav See {BP_Component::setup_nav()} for details.
	 * @param array $sub_nav  See {BP_Component::setup_nav()} for details.
	 */
	public function setup_nav( $main_nav = array(), $sub_nav = array() ) {

		// Determine user to use.
		if ( bp_displayed_user_domain() ) {
			$user_domain = bp_displayed_user_domain();
		} elseif ( bp_loggedin_user_domain() ) {
			$user_domain = bp_loggedin_user_domain();
		} else {
			return;
		}

		$access        = bp_core_can_edit_settings();
		$slug          = bp_get_messages_slug();
		$messages_link = trailingslashit( $user_domain . $slug );

		// Only grab count if we're on a user page and current user has access.
		if ( bp_is_user() && bp_user_has_access() ) {
			$count    = bp_get_total_unread_messages_count();
			$class    = ( 0 === $count ) ? 'no-count' : 'count';
			$nav_name = sprintf( __( 'Messages <span class="%s">%s</span>', 'buddypress' ), esc_attr( $class ), bp_core_number_format( $count ) );
		} else {
			$nav_name = __( 'Messages', 'buddypress' );
		}

		// Add 'Messages' to the main navigation.
		$main_nav = array(
			'name'                    => $nav_name,
			'slug'                    => $slug,
			'position'                => 50,
			'show_for_displayed_user' => $access,
			'screen_function'         => 'messages_screen_inbox',
			'default_subnav_slug'     => 'inbox',
			'item_css_id'             => $this->id
		);

		// Add the subnav items to the profile.
		$sub_nav[] = array(
			'name'            => __( 'Inbox', 'buddypress' ),
			'slug'            => 'inbox',
			'parent_url'      => $messages_link,
			'parent_slug'     => $slug,
			'screen_function' => 'messages_screen_inbox',
			'position'        => 10,
			'user_has_access' => $access
		);

		if ( bp_is_active( $this->id, 'star' ) ) {
			$sub_nav[] = array(
				'name'            => __( 'Starred', 'buddypress' ),
				'slug'            => bp_get_messages_starred_slug(),
				'parent_url'      => $messages_link,
				'parent_slug'     => $slug,
				'screen_function' => 'bp_messages_star_screen',
				'position'        => 11,
				'user_has_access' => $access
			);
		}

		$sub_nav[] = array(
			'name'            => __( 'Sent', 'buddypress' ),
			'slug'            => 'sentbox',
			'parent_url'      => $messages_link,
			'parent_slug'     => $slug,
			'screen_function' => 'messages_screen_sentbox',
			'position'        => 20,
			'user_has_access' => $access
		);

		$sub_nav[] = array(
			'name'            => __( 'Compose', 'buddypress' ),
			'slug'            => 'compose',
			'parent_url'      => $messages_link,
			'parent_slug'     => $slug,
			'screen_function' => 'messages_screen_compose',
			'position'        => 30,
			'user_has_access' => $access
		);

		if ( bp_current_user_can( 'bp_moderate' ) ) {
			$sub_nav[] = array(
				'name'            => __( 'Notices', 'buddypress' ),
				'slug'            => 'notices',
				'parent_url'      => $messages_link,
				'parent_slug'     => $slug,
				'screen_function' => 'messages_screen_notices',
				'position'        => 90,
				'user_has_access' => true
			);
		}

		parent::setup_nav( $main_nav, $sub_nav );
	}

	/**
	 * Set up the Toolbar.
	 *
	 * @param array $wp_admin_nav See {BP_Component::setup_admin_bar()} for details.
	 */
	public function setup_admin_bar( $wp_admin_nav = array() ) {

		// Menus for logged in user.
		if ( is_user_logged_in() ) {

			// Setup the logged in user variables.
			$messages_link = trailingslashit( bp_loggedin_user_domain() . bp_get_messages_slug() );

			// Unread message count.
			$count = messages_get_unread_count();
			if ( !empty( $count ) ) {
				$title = sprintf( __( 'Messages <span class="count">%s</span>', 'buddypress' ), bp_core_number_format( $count ) );
				$inbox = sprintf( __( 'Inbox <span class="count">%s</span>',    'buddypress' ), bp_core_number_format( $count ) );
			} else {
				$title = __( 'Messages', 'buddypress' );
				$inbox = __( 'Inbox',    'buddypress' );
			}

			// Add main Messages menu.
			$wp_admin_nav[] = array(
				'parent' => buddypress()->my_account_menu_id,
				'id'     => 'my-account-' . $this->id,
				'title'  => $title,
				'href'   => $messages_link
			);

			// Inbox.
			$wp_admin_nav[] = array(
				'parent' => 'my-account-' . $this->id,
				'id'     => 'my-account-' . $this->id . '-inbox',
				'title'  => $inbox,
				'href'   => $messages_link
			);

			// Starred.
			if ( bp_is_active( $this->id, 'star' ) ) {
				$wp_admin_nav[] = array(
					'parent' => 'my-account-' . $this->id,
					'id'     => 'my-account-' . $this->id . '-starred',
					'title'  => __( 'Starred', 'buddypress' ),
					'href'   => trailingslashit( $messages_link . bp_get_messages_starred_slug() )
				);
			}

			// Sent Messages.
			$wp_admin_nav[] = array(
				'parent' => 'my-account-' . $this->id,
				'id'     => 'my-account-' . $this->id . '-sentbox',
				'title'  => __( 'Sent', 'buddypress' ),
				'href'   => trailingslashit( $messages_link . 'sentbox' )
			);

			// Compose Message.
			$wp_admin_nav[] = array(
				'parent' => 'my-account-' . $this->id,
				'id'     => 'my-account-' . $this->id . '-compose',
				'title'  => __( 'Compose', 'buddypress' ),
				'href'   => trailingslashit( $messages_link . 'compose' )
			);

			// Site Wide Notices.
			if ( bp_current_user_can( 'bp_moderate' ) ) {
				$wp_admin_nav[] = array(
					'parent' => 'my-account-' . $this->id,
					'id'     => 'my-account-' . $this->id . '-notices',
					'title'  => __( 'All Member Notices', 'buddypress' ),
					'href'   => trailingslashit( $messages_link . 'notices' )
				);
			}
		}

		parent::setup_admin_bar( $wp_admin_nav );
	}

	/**
	 * Set up the title for pages and <title>.
	 */
	public function setup_title() {

		if ( bp_is_messages_component() ) {
			$bp = buddypress();

			if ( bp_is_my_profile() ) {
				$bp->bp_options_title = __( 'My Messages', 'buddypress' );
			} else {
				$bp->bp_options_avatar = bp_core_fetch_avatar( array(
					'item_id' => bp_displayed_user_id(),
					'type'    => 'thumb',
					'alt'     => sprintf( __( 'Profile picture of %s', 'buddypress' ), bp_get_displayed_user_fullname() )
				) );
				$bp->bp_options_title = bp_get_displayed_user_fullname();
			}
		}

		parent::setup_title();
	}

	/**
	 * Setup cache groups
	 *
	 * @since 2.2.0
	 */
	public function setup_cache_groups() {

		// Global groups.
		wp_cache_add_global_groups( array(
			'bp_messages',
			'bp_messages_threads',
			'bp_messages_unread_count',
			'message_meta'
		) );

		parent::setup_cache_groups();
	}
}

/**
 * Bootstrap the Messages component.
 */
function bp_setup_messages() {
	buddypress()->messages = new BP_Messages_Component();
}
add_action( 'bp_setup_components', 'bp_setup_messages', 6 );
