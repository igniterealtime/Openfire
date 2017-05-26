<?php
/**
 * BuddyPress Core Loader.
 *
 * Core contains the commonly used functions, classes, and APIs.
 *
 * @package BuddyPress
 * @subpackage Core
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

class BP_Core extends BP_Component {

	/**
	 * Start the members component creation process.
	 *
	 * @since 1.5.0
	 *
	 * @uses BP_Core::bootstrap()
	 */
	public function __construct() {
		parent::start(
			'core',
			__( 'BuddyPress Core', 'buddypress' ),
			buddypress()->plugin_dir
		);

		$this->bootstrap();
	}

	/**
	 * Populate the global data needed before BuddyPress can continue.
	 *
	 * This involves figuring out the currently required, activated, deactivated,
	 * and optional components.
	 *
	 * @since 1.5.0
	 */
	private function bootstrap() {
		$bp = buddypress();

		/**
		 * Fires before the loading of individual components and after BuddyPress Core.
		 *
		 * Allows plugins to run code ahead of the other components.
		 *
		 * @since 1.2.0
		 */
		do_action( 'bp_core_loaded' );

		/** Components ********************************************************/

		/**
		 * Filters the included and optional components.
		 *
		 * @since 1.5.0
		 *
		 * @param array $value Array of included and optional components.
		 */
		$bp->optional_components = apply_filters( 'bp_optional_components', array( 'activity', 'blogs', 'forums', 'friends', 'groups', 'messages', 'notifications', 'settings', 'xprofile' ) );

		/**
		 * Filters the required components.
		 *
		 * @since 1.5.0
		 *
		 * @param array $value Array of required components.
		 */
		$bp->required_components = apply_filters( 'bp_required_components', array( 'members' ) );

		// Get a list of activated components
		if ( $active_components = bp_get_option( 'bp-active-components' ) ) {

			/** This filter is documented in bp-core/admin/bp-core-admin-components.php */
			$bp->active_components      = apply_filters( 'bp_active_components', $active_components );

			/**
			 * Filters the deactivated components.
			 *
			 * @since 1.0.0
			 *
			 * @param array $value Array of deactivated components.
			 */
			$bp->deactivated_components = apply_filters( 'bp_deactivated_components', array_values( array_diff( array_values( array_merge( $bp->optional_components, $bp->required_components ) ), array_keys( $bp->active_components ) ) ) );

		// Pre 1.5 Backwards compatibility
		} elseif ( $deactivated_components = bp_get_option( 'bp-deactivated-components' ) ) {

			// Trim off namespace and filename
			foreach ( array_keys( (array) $deactivated_components ) as $component ) {
				$trimmed[] = str_replace( '.php', '', str_replace( 'bp-', '', $component ) );
			}

			/** This filter is documented in bp-core/bp-core-loader.php */
			$bp->deactivated_components = apply_filters( 'bp_deactivated_components', $trimmed );

			// Setup the active components
			$active_components     = array_fill_keys( array_diff( array_values( array_merge( $bp->optional_components, $bp->required_components ) ), array_values( $bp->deactivated_components ) ), '1' );

			/** This filter is documented in bp-core/admin/bp-core-admin-components.php */
			$bp->active_components = apply_filters( 'bp_active_components', $bp->active_components );

		// Default to all components active
		} else {

			// Set globals
			$bp->deactivated_components = array();

			// Setup the active components
			$active_components     = array_fill_keys( array_values( array_merge( $bp->optional_components, $bp->required_components ) ), '1' );

			/** This filter is documented in bp-core/admin/bp-core-admin-components.php */
			$bp->active_components = apply_filters( 'bp_active_components', $bp->active_components );
		}

		// Loop through optional components
		foreach( $bp->optional_components as $component ) {
			if ( bp_is_active( $component ) && file_exists( $bp->plugin_dir . '/bp-' . $component . '/bp-' . $component . '-loader.php' ) ) {
				include( $bp->plugin_dir . '/bp-' . $component . '/bp-' . $component . '-loader.php' );
			}
		}

		// Loop through required components
		foreach( $bp->required_components as $component ) {
			if ( file_exists( $bp->plugin_dir . '/bp-' . $component . '/bp-' . $component . '-loader.php' ) ) {
				include( $bp->plugin_dir . '/bp-' . $component . '/bp-' . $component . '-loader.php' );
			}
		}

		// Add Core to required components
		$bp->required_components[] = 'core';

		/**
		 * Fires after the loading of individual components.
		 *
		 * @since 2.0.0
		 */
		do_action( 'bp_core_components_included' );
	}

	/**
	 * Include bp-core files.
	 *
	 * @see BP_Component::includes() for description of parameters.
	 *
	 * @param array $includes See {@link BP_Component::includes()}.
	 */
	public function includes( $includes = array() ) {

		if ( ! is_admin() ) {
			return;
		}

		$includes = array(
			'admin'
		);

		parent::includes( $includes );
	}

	/**
	 * Set up bp-core global settings.
	 *
	 * Sets up a majority of the BuddyPress globals that require a minimal
	 * amount of processing, meaning they cannot be set in the BuddyPress class.
	 *
	 * @since 1.5.0
	 *
	 * @see BP_Component::setup_globals() for description of parameters.
	 *
	 * @param array $args See {@link BP_Component::setup_globals()}.
	 */
	public function setup_globals( $args = array() ) {
		$bp = buddypress();

		/** Database **********************************************************/

		// Get the base database prefix
		if ( empty( $bp->table_prefix ) ) {
			$bp->table_prefix = bp_core_get_table_prefix();
		}

		// The domain for the root of the site where the main blog resides
		if ( empty( $bp->root_domain ) ) {
			$bp->root_domain = bp_core_get_root_domain();
		}

		// Fetches all of the core BuddyPress settings in one fell swoop
		if ( empty( $bp->site_options ) ) {
			$bp->site_options = bp_core_get_root_options();
		}

		// The names of the core WordPress pages used to display BuddyPress content
		if ( empty( $bp->pages ) ) {
			$bp->pages = bp_core_get_directory_pages();
		}

		/** Basic current user data *******************************************/

		// Logged in user is the 'current_user'
		$current_user            = wp_get_current_user();

		// The user ID of the user who is currently logged in.
		$bp->loggedin_user       = new stdClass;
		$bp->loggedin_user->id   = isset( $current_user->ID ) ? $current_user->ID : 0;

		/** Avatars ***********************************************************/

		// Fetches the default Gravatar image to use if the user/group/blog has no avatar or gravatar
		$bp->grav_default        = new stdClass;

		/**
		 * Filters the default user Gravatar.
		 *
		 * @since 1.1.0
		 *
		 * @param string $value Default user Gravatar.
		 */
		$bp->grav_default->user  = apply_filters( 'bp_user_gravatar_default',  $bp->site_options['avatar_default'] );

		/**
		 * Filters the default group Gravatar.
		 *
		 * @since 1.1.0
		 *
		 * @param string $value Default group Gravatar.
		 */
		$bp->grav_default->group = apply_filters( 'bp_group_gravatar_default', $bp->grav_default->user );

		/**
		 * Filters the default blog Gravatar.
		 *
		 * @since 1.1.0
		 *
		 * @param string $value Default blog Gravatar.
		 */
		$bp->grav_default->blog  = apply_filters( 'bp_blog_gravatar_default',  $bp->grav_default->user );

		// Notifications table. Included here for legacy purposes. Use
		// bp-notifications instead.
		$bp->core->table_name_notifications = $bp->table_prefix . 'bp_notifications';

		/**
		 * Used to determine if user has admin rights on current content. If the
		 * logged in user is viewing their own profile and wants to delete
		 * something, is_item_admin is used. This is a generic variable so it
		 * can be used by other components. It can also be modified, so when
		 * viewing a group 'is_item_admin' would be 'true' if they are a group
		 * admin, and 'false' if they are not.
		 */
		bp_update_is_item_admin( bp_user_has_access(), 'core' );

		// Is the logged in user is a mod for the current item?
		bp_update_is_item_mod( false,                  'core' );

		/**
		 * Fires at the end of the setup of bp-core globals setting.
		 *
		 * @since 1.1.0
		 */
		do_action( 'bp_core_setup_globals' );
	}

	/**
	 * Setup cache groups
	 *
	 * @since 2.2.0
	 */
	public function setup_cache_groups() {

		// Global groups
		wp_cache_add_global_groups( array(
			'bp'
		) );

		parent::setup_cache_groups();
	}
}

/**
 * Set up the BuddyPress Core component.
 *
 * @since 1.6.0
 *
 * @global BuddyPress $bp BuddyPress global settings object.
 */
function bp_setup_core() {
	buddypress()->core = new BP_Core();
}
add_action( 'bp_loaded', 'bp_setup_core', 0 );
