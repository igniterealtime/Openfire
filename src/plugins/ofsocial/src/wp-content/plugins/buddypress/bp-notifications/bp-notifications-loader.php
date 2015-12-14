<?php
/**
 * BuddyPress Member Notifications Loader.
 *
 * Initializes the Notifications component.
 *
 * @package BuddyPress
 * @subpackage NotificationsLoader
 * @since 1.9.0
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Extends the component class to set up the Notifications component.
 */
class BP_Notifications_Component extends BP_Component {

	/**
	 * Start the notifications component creation process.
	 *
	 * @since 1.9.0
	 */
	public function __construct() {
		parent::start(
			'notifications',
			_x( 'Notifications', 'Page <title>', 'buddypress' ),
			buddypress()->plugin_dir,
			array(
				'adminbar_myaccount_order' => 30
			)
		);
	}

	/**
	 * Include notifications component files.
	 *
	 * @since 1.9.0
	 *
	 * @see BP_Component::includes() for a description of arguments.
	 *
	 * @param array $includes See BP_Component::includes() for a description.
	 */
	public function includes( $includes = array() ) {
		$includes = array(
			'actions',
			'classes',
			'screens',
			'adminbar',
			'template',
			'functions',
			'cache',
		);

		parent::includes( $includes );
	}

	/**
	 * Set up component global data.
	 *
	 * @since 1.9.0
	 *
	 * @see BP_Component::setup_globals() for a description of arguments.
	 *
	 * @param array $args See BP_Component::setup_globals() for a description.
	 */
	public function setup_globals( $args = array() ) {
		$bp = buddypress();

		// Define a slug, if necessary.
		if ( ! defined( 'BP_NOTIFICATIONS_SLUG' ) ) {
			define( 'BP_NOTIFICATIONS_SLUG', $this->id );
		}

		// Global tables for the notifications component.
		$global_tables = array(
			'table_name'      => $bp->table_prefix . 'bp_notifications',
			'table_name_meta' => $bp->table_prefix . 'bp_notifications_meta',
		);

		// All globals for the notifications component.
		// Note that global_tables is included in this array.
		$args = array(
			'slug'          => BP_NOTIFICATIONS_SLUG,
			'has_directory' => false,
			'search_string' => __( 'Search Notifications...', 'buddypress' ),
			'global_tables' => $global_tables,
		);

		parent::setup_globals( $args );
	}

	/**
	 * Set up component navigation.
	 *
	 * @since 1.9.0
	 *
	 * @see BP_Component::setup_nav() for a description of arguments.
	 *
	 * @param array $main_nav Optional. See BP_Component::setup_nav() for
	 *                        description.
	 * @param array $sub_nav  Optional. See BP_Component::setup_nav() for
	 *                        description.
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

		$access             = bp_core_can_edit_settings();
		$slug               = bp_get_notifications_slug();
		$notifications_link = trailingslashit( $user_domain . $slug );

		// Only grab count if we're on a user page and current user has access.
		if ( bp_is_user() && bp_user_has_access() ) {
			$count    = bp_notifications_get_unread_notification_count( bp_displayed_user_id() );
			$class    = ( 0 === $count ) ? 'no-count' : 'count';
			$nav_name = sprintf( _x( 'Notifications <span class="%s">%s</span>', 'Profile screen nav', 'buddypress' ), esc_attr( $class ), bp_core_number_format( $count ) );
		} else {
			$nav_name = _x( 'Notifications', 'Profile screen nav', 'buddypress' );
		}

		// Add 'Notifications' to the main navigation.
		$main_nav = array(
			'name'                    => $nav_name,
			'slug'                    => $slug,
			'position'                => 30,
			'show_for_displayed_user' => $access,
			'screen_function'         => 'bp_notifications_screen_unread',
			'default_subnav_slug'     => 'unread',
			'item_css_id'             => $this->id,
		);

		// Add the subnav items to the notifications nav item.
		$sub_nav[] = array(
			'name'            => _x( 'Unread', 'Notification screen nav', 'buddypress' ),
			'slug'            => 'unread',
			'parent_url'      => $notifications_link,
			'parent_slug'     => $slug,
			'screen_function' => 'bp_notifications_screen_unread',
			'position'        => 10,
			'item_css_id'     => 'notifications-my-notifications',
			'user_has_access' => $access,
		);

		$sub_nav[] = array(
			'name'            => _x( 'Read', 'Notification screen nav', 'buddypress' ),
			'slug'            => 'read',
			'parent_url'      => $notifications_link,
			'parent_slug'     => $slug,
			'screen_function' => 'bp_notifications_screen_read',
			'position'        => 20,
			'user_has_access' => $access,
		);

		parent::setup_nav( $main_nav, $sub_nav );
	}

	/**
	 * Set up the component entries in the WordPress Admin Bar.
	 *
	 * @since 1.9.0
	 *
	 * @see BP_Component::setup_nav() for a description of the $wp_admin_nav
	 *      parameter array.
	 *
	 * @param array $wp_admin_nav See BP_Component::setup_admin_bar() for a
	 *                            description.
	 */
	public function setup_admin_bar( $wp_admin_nav = array() ) {

		// Menus for logged in user.
		if ( is_user_logged_in() ) {

			// Setup the logged in user variables.
			$notifications_link = trailingslashit( bp_loggedin_user_domain() . bp_get_notifications_slug() );

			// Pending notification requests.
			$count = bp_notifications_get_unread_notification_count( bp_loggedin_user_id() );
			if ( ! empty( $count ) ) {
				$title  = sprintf( _x( 'Notifications <span class="count">%s</span>', 'My Account Notification pending', 'buddypress' ), bp_core_number_format( $count ) );
				$unread = sprintf( _x( 'Unread <span class="count">%s</span>',        'My Account Notification pending', 'buddypress' ), bp_core_number_format( $count ) );
			} else {
				$title  = _x( 'Notifications', 'My Account Notification',         'buddypress' );
				$unread = _x( 'Unread',        'My Account Notification sub nav', 'buddypress' );
			}

			// Add the "My Account" sub menus.
			$wp_admin_nav[] = array(
				'parent' => buddypress()->my_account_menu_id,
				'id'     => 'my-account-' . $this->id,
				'title'  => $title,
				'href'   => $notifications_link
			);

			// Unread.
			$wp_admin_nav[] = array(
				'parent' => 'my-account-' . $this->id,
				'id'     => 'my-account-' . $this->id . '-unread',
				'title'  => $unread,
				'href'   => $notifications_link
			);

			// Read.
			$wp_admin_nav[] = array(
				'parent' => 'my-account-' . $this->id,
				'id'     => 'my-account-' . $this->id . '-read',
				'title'  => _x( 'Read', 'My Account Notification sub nav', 'buddypress' ),
				'href'   => trailingslashit( $notifications_link . 'read' ),
			);
		}

		parent::setup_admin_bar( $wp_admin_nav );
	}

	/**
	 * Set up the title for pages and <title>.
	 *
	 * @since 1.9.0
	 */
	public function setup_title() {

		// Adjust title.
		if ( bp_is_notifications_component() ) {
			$bp = buddypress();

			if ( bp_is_my_profile() ) {
				$bp->bp_options_title = __( 'Notifications', 'buddypress' );
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
	 * Setup cache groups.
	 *
	 * @since 2.2.0
	 */
	public function setup_cache_groups() {

		// Global groups.
		wp_cache_add_global_groups( array(
			'bp_notifications',
			'notification_meta'
		) );

		parent::setup_cache_groups();
	}
}

/**
 * Bootstrap the Notifications component.
 *
 * @since 1.9.0
 */
function bp_setup_notifications() {
	buddypress()->notifications = new BP_Notifications_Component();
}
add_action( 'bp_setup_components', 'bp_setup_notifications', 6 );
