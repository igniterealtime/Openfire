<?php
/**
 * The BuddyPress Plugin.
 *
 * BuddyPress is social networking software with a twist from the creators of WordPress.
 *
 * @package BuddyPress
 * @subpackage Main
 */

/**
 * Plugin Name: BuddyPress
 * Plugin URI:  https://buddypress.org/
 * Description: BuddyPress helps you run any kind of social network on your WordPress, with member profiles, activity streams, user groups, messaging, and more.
 * Author:      The BuddyPress Community
 * Author URI:  https://buddypress.org/
 * Version:     2.4.2
 * Text Domain: buddypress
 * Domain Path: /bp-languages/
 * License:     GPLv2 or later (license.txt)
 */

// Exit if accessed directly
defined( 'ABSPATH' ) || exit;

/** Constants *****************************************************************/

if ( !class_exists( 'BuddyPress' ) ) :
/**
 * Main BuddyPress Class.
 *
 * Tap tap tap... Is this thing on?
 *
 * @since 1.6.0
 */
class BuddyPress {

	/** Magic *****************************************************************/

	/**
	 * BuddyPress uses many variables, most of which can be filtered to
	 * customize the way that it works. To prevent unauthorized access,
	 * these variables are stored in a private array that is magically
	 * updated using PHP 5.2+ methods. This is to prevent third party
	 * plugins from tampering with essential information indirectly, which
	 * would cause issues later.
	 *
	 * @see BuddyPress::setup_globals()
	 * @var array
	 */
	private $data;

	/** Not Magic *************************************************************/

	/**
	 * @var array Primary BuddyPress navigation.
	 */
	public $bp_nav = array();

	/**
	 * @var array Secondary BuddyPress navigation to $bp_nav.
	 */
	public $bp_options_nav = array();

	/**
	 * @var array The unfiltered URI broken down into chunks.
	 * @see bp_core_set_uri_globals()
	 */
	public $unfiltered_uri = array();

	/**
	 * @var array The canonical URI stack.
	 * @see bp_redirect_canonical()
	 * @see bp_core_new_nav_item()
	 */
	public $canonical_stack = array();

	/**
	 * @var array Additional navigation elements (supplemental).
	 */
	public $action_variables = array();

	/**
	 * @var string Current member directory type.
	 */
	public $current_member_type = '';

	/**
	 * @var array Required components (core, members).
	 */
	public $required_components = array();

	/**
	 * @var array Additional active components.
	 */
	public $loaded_components = array();

	/**
	 * @var array Active components.
	 */
	public $active_components = array();

	/** Option Overload *******************************************************/

	/**
	 * @var array Optional Overloads default options retrieved from get_option().
	 */
	public $options = array();

	/** Singleton *************************************************************/

	/**
	 * Main BuddyPress Instance.
	 *
	 * BuddyPress is great.
	 * Please load it only one time.
	 * For this, we thank you.
	 *
	 * Insures that only one instance of BuddyPress exists in memory at any
	 * one time. Also prevents needing to define globals all over the place.
	 *
	 * @since 1.7.0
	 *
	 * @static object $instance
	 * @uses BuddyPress::constants() Setup the constants (mostly deprecated).
	 * @uses BuddyPress::setup_globals() Setup the globals needed.
	 * @uses BuddyPress::legacy_constants() Setup the legacy constants (deprecated).
	 * @uses BuddyPress::includes() Include the required files.
	 * @uses BuddyPress::setup_actions() Setup the hooks and actions.
	 * @see buddypress()
	 *
	 * @return BuddyPress The one true BuddyPress.
	 */
	public static function instance() {

		// Store the instance locally to avoid private static replication
		static $instance = null;

		// Only run these methods if they haven't been run previously
		if ( null === $instance ) {
			$instance = new BuddyPress;
			$instance->constants();
			$instance->setup_globals();
			$instance->legacy_constants();
			$instance->includes();
			$instance->setup_actions();
		}

		// Always return the instance
		return $instance;

		// The last metroid is in captivity. The galaxy is at peace.
	}

	/** Magic Methods *********************************************************/

	/**
	 * A dummy constructor to prevent BuddyPress from being loaded more than once.
	 *
	 * @since 1.7.0
	 * @see BuddyPress::instance()
	 * @see buddypress()
	 */
	private function __construct() { /* Do nothing here */ }

	/**
	 * A dummy magic method to prevent BuddyPress from being cloned.
	 *
	 * @since 1.7.0
	 */
	public function __clone() { _doing_it_wrong( __FUNCTION__, __( 'Cheatin&#8217; huh?', 'buddypress' ), '1.7' ); }

	/**
	 * A dummy magic method to prevent BuddyPress from being unserialized.
	 *
	 * @since 1.7.0
	 */
	public function __wakeup() { _doing_it_wrong( __FUNCTION__, __( 'Cheatin&#8217; huh?', 'buddypress' ), '1.7' ); }

	/**
	 * Magic method for checking the existence of a certain custom field.
	 *
	 * @since 1.7.0
	 *
	 * @param string $key Key to check the set status for.
	 *
	 * @return bool
	 */
	public function __isset( $key ) { return isset( $this->data[$key] ); }

	/**
	 * Magic method for getting BuddyPress variables.
	 *
	 * @since 1.7.0
	 *
	 * @param string $key Key to return the value for.
	 *
	 * @return mixed
	 */
	public function __get( $key ) { return isset( $this->data[$key] ) ? $this->data[$key] : null; }

	/**
	 * Magic method for setting BuddyPress variables.
	 *
	 * @since 1.7.0
	 *
	 * @param string $key   Key to set a value for.
	 * @param mixed  $value Value to set.
	 */
	public function __set( $key, $value ) { $this->data[$key] = $value; }

	/**
	 * Magic method for unsetting BuddyPress variables.
	 *
	 * @since 1.7.0
	 *
	 * @param string $key Key to unset a value for.
	 */
	public function __unset( $key ) { if ( isset( $this->data[$key] ) ) unset( $this->data[$key] ); }

	/**
	 * Magic method to prevent notices and errors from invalid method calls.
	 *
	 * @since 1.7.0
	 *
	 * @param string $name
	 * @param array  $args
	 *
	 * @return null
	 */
	public function __call( $name = '', $args = array() ) { unset( $name, $args ); return null; }

	/** Private Methods *******************************************************/

	/**
	 * Bootstrap constants.
	 *
	 * @since 1.6.0
	 *
	 * @uses is_multisite()
	 * @uses get_current_site()
	 * @uses get_current_blog_id()
	 * @uses plugin_dir_path()
	 * @uses plugin_dir_url()
	 */
	private function constants() {

		// Place your custom code (actions/filters) in a file called
		// '/plugins/bp-custom.php' and it will be loaded before anything else.
		if ( file_exists( WP_PLUGIN_DIR . '/bp-custom.php' ) ) {
			require( WP_PLUGIN_DIR . '/bp-custom.php' );
		}

		// Path and URL
		if ( ! defined( 'BP_PLUGIN_DIR' ) ) {
			define( 'BP_PLUGIN_DIR', plugin_dir_path( __FILE__ ) );
		}

		if ( ! defined( 'BP_PLUGIN_URL' ) ) {
			define( 'BP_PLUGIN_URL', plugin_dir_url( __FILE__ ) );
		}

		// Only applicable to those running trunk
		if ( ! defined( 'BP_SOURCE_SUBDIRECTORY' ) ) {
			define( 'BP_SOURCE_SUBDIRECTORY', '' );
		}

		// Define on which blog ID BuddyPress should run
		if ( ! defined( 'BP_ROOT_BLOG' ) ) {

			// Default to use current blog ID
			// Fulfills non-network installs and BP_ENABLE_MULTIBLOG installs
			$root_blog_id = get_current_blog_id();

			// Multisite check
			if ( is_multisite() ) {

				// Multiblog isn't enabled
				if ( ! defined( 'BP_ENABLE_MULTIBLOG' ) || ( defined( 'BP_ENABLE_MULTIBLOG' ) && (int) constant( 'BP_ENABLE_MULTIBLOG' ) === 0 ) ) {
					// Check to see if BP is network-activated
					// We're not using is_plugin_active_for_network() b/c you need to include the
					// /wp-admin/includes/plugin.php file in order to use that function.

					// get network-activated plugins
					$plugins = get_site_option( 'active_sitewide_plugins');

					// basename
					$basename = basename( constant( 'BP_PLUGIN_DIR' ) ) . '/bp-loader.php';

					// plugin is network-activated; use main site ID instead
					if ( isset( $plugins[ $basename ] ) ) {
						$current_site = get_current_site();
						$root_blog_id = $current_site->blog_id;
					}
				}

			}

			define( 'BP_ROOT_BLOG', $root_blog_id );
		}

		// Whether to refrain from loading deprecated functions
		if ( ! defined( 'BP_IGNORE_DEPRECATED' ) ) {
			define( 'BP_IGNORE_DEPRECATED', false );
		}

		// The search slug has to be defined nice and early because of the way
		// search requests are loaded
		//
		// @todo Make this better
		if ( ! defined( 'BP_SEARCH_SLUG' ) ) {
			define( 'BP_SEARCH_SLUG', 'search' );
		}
	}

	/**
	 * Component global variables.
	 *
	 * @since 1.6.0
	 *
	 * @uses plugin_dir_path() To generate BuddyPress plugin path.
	 * @uses plugin_dir_url() To generate BuddyPress plugin url.
	 * @uses apply_filters() Calls various filters.
	 */
	private function setup_globals() {

		/** Versions **********************************************************/

		$this->version    = '2.4.2';
		$this->db_version = 10071;

		/** Loading ***********************************************************/

		/**
		 * Filters the load_deprecated property value.
		 *
		 * @since 2.0.0
		 *
		 * @const constant BP_IGNORE_DEPRECATED Whether or not to ignore deprecated functionality.
		 */
		$this->load_deprecated = ! apply_filters( 'bp_ignore_deprecated', BP_IGNORE_DEPRECATED );

		/** Toolbar ***********************************************************/

		/**
		 * @var string The primary toolbar ID.
		 */
		$this->my_account_menu_id = '';

		/** URIs **************************************************************/

		/**
		 * @var int The current offset of the URI.
		 * @see bp_core_set_uri_globals()
		 */
		$this->unfiltered_uri_offset = 0;

		/**
		 * @var bool Are status headers already sent?
		 */
		$this->no_status_set = false;

		/** Components ********************************************************/

		/**
		 * @var string Name of the current BuddyPress component (primary).
		 */
		$this->current_component = '';

		/**
		 * @var string Name of the current BuddyPress item (secondary).
		 */
		$this->current_item = '';

		/**
		 * @var string Name of the current BuddyPress action (tertiary).
		 */
		$this->current_action = '';

		/**
		 * @var bool Displaying custom 2nd level navigation menu (I.E a group).
		 */
		$this->is_single_item = false;

		/** Root **************************************************************/

		/**
		 * Filters the BuddyPress Root blog ID.
		 *
		 * @since 1.5.0
		 *
		 * @const constant BP_ROOT_BLOG BuddyPress Root blog ID.
		 */
		$this->root_blog_id = (int) apply_filters( 'bp_get_root_blog_id', BP_ROOT_BLOG );

		/** Paths**************************************************************/

		// BuddyPress root directory
		$this->file           = constant( 'BP_PLUGIN_DIR' ) . 'bp-loader.php';
		$this->basename       = basename( constant( 'BP_PLUGIN_DIR' ) ) . '/bp-loader.php';
		$this->plugin_dir     = trailingslashit( constant( 'BP_PLUGIN_DIR' ) . constant( 'BP_SOURCE_SUBDIRECTORY' ) );
		$this->plugin_url     = trailingslashit( constant( 'BP_PLUGIN_URL' ) . constant( 'BP_SOURCE_SUBDIRECTORY' ) );

		// Languages
		$this->lang_dir       = $this->plugin_dir . 'bp-languages';

		// Templates (theme compatibility)
		$this->themes_dir     = $this->plugin_dir . 'bp-templates';
		$this->themes_url     = $this->plugin_url . 'bp-templates';

		// Themes (for bp-default)
		$this->old_themes_dir = $this->plugin_dir . 'bp-themes';
		$this->old_themes_url = $this->plugin_url . 'bp-themes';

		/** Theme Compat ******************************************************/

		$this->theme_compat   = new stdClass(); // Base theme compatibility class
		$this->filters        = new stdClass(); // Used when adding/removing filters

		/** Users *************************************************************/

		$this->current_user   = new stdClass();
		$this->displayed_user = new stdClass();
	}

	/**
	 * Legacy BuddyPress constants.
	 *
	 * Try to avoid using these. Their values have been moved into variables
	 * in the instance, and have matching functions to get/set their values.
	 *
	 * @since 1.7.0
	 */
	private function legacy_constants() {

		// Define the BuddyPress version
		if ( ! defined( 'BP_VERSION' ) ) {
			define( 'BP_VERSION', $this->version );
		}

		// Define the database version
		if ( ! defined( 'BP_DB_VERSION' ) ) {
			define( 'BP_DB_VERSION', $this->db_version );
		}
	}

	/**
	 * Include required files.
	 *
	 * @since 1.6.0
	 *
	 * @uses is_admin() If in WordPress admin, load additional file.
	 */
	private function includes() {

		// Load the WP abstraction file so BuddyPress can run on all WordPress setups.
		require( $this->plugin_dir . 'bp-core/bp-core-wpabstraction.php' );

		// Setup the versions (after we include multisite abstraction above)
		$this->versions();

		/** Update/Install ****************************************************/

		// Theme compatibility
		require( $this->plugin_dir . 'bp-core/bp-core-template-loader.php'     );
		require( $this->plugin_dir . 'bp-core/bp-core-theme-compatibility.php' );

		// Require all of the BuddyPress core libraries
		require( $this->plugin_dir . 'bp-core/bp-core-dependency.php'  );
		require( $this->plugin_dir . 'bp-core/bp-core-actions.php'     );
		require( $this->plugin_dir . 'bp-core/bp-core-caps.php'        );
		require( $this->plugin_dir . 'bp-core/bp-core-cache.php'       );
		require( $this->plugin_dir . 'bp-core/bp-core-cssjs.php'       );
		require( $this->plugin_dir . 'bp-core/bp-core-update.php'      );
		require( $this->plugin_dir . 'bp-core/bp-core-options.php'     );
		require( $this->plugin_dir . 'bp-core/bp-core-classes.php'     );
		require( $this->plugin_dir . 'bp-core/bp-core-taxonomy.php'    );
		require( $this->plugin_dir . 'bp-core/bp-core-filters.php'     );
		require( $this->plugin_dir . 'bp-core/bp-core-attachments.php' );
		require( $this->plugin_dir . 'bp-core/bp-core-avatars.php'     );
		require( $this->plugin_dir . 'bp-core/bp-core-widgets.php'     );
		require( $this->plugin_dir . 'bp-core/bp-core-template.php'    );
		require( $this->plugin_dir . 'bp-core/bp-core-adminbar.php'    );
		require( $this->plugin_dir . 'bp-core/bp-core-buddybar.php'    );
		require( $this->plugin_dir . 'bp-core/bp-core-catchuri.php'    );
		require( $this->plugin_dir . 'bp-core/bp-core-component.php'   );
		require( $this->plugin_dir . 'bp-core/bp-core-functions.php'   );
		require( $this->plugin_dir . 'bp-core/bp-core-moderation.php'  );
		require( $this->plugin_dir . 'bp-core/bp-core-loader.php'      );

		// Skip or load deprecated content
		if ( false !== $this->load_deprecated ) {
			require( $this->plugin_dir . 'bp-core/deprecated/1.2.php' );
			require( $this->plugin_dir . 'bp-core/deprecated/1.5.php' );
			require( $this->plugin_dir . 'bp-core/deprecated/1.6.php' );
			require( $this->plugin_dir . 'bp-core/deprecated/1.7.php' );
			require( $this->plugin_dir . 'bp-core/deprecated/1.9.php' );
			require( $this->plugin_dir . 'bp-core/deprecated/2.0.php' );
			require( $this->plugin_dir . 'bp-core/deprecated/2.1.php' );
			require( $this->plugin_dir . 'bp-core/deprecated/2.2.php' );
			require( $this->plugin_dir . 'bp-core/deprecated/2.3.php' );
			require( $this->plugin_dir . 'bp-core/deprecated/2.4.php' );
		}
	}

	/**
	 * Set up the default hooks and actions.
	 *
	 * @since 1.6.0
	 *
	 * @uses register_activation_hook() To register the activation hook.
	 * @uses register_deactivation_hook() To register the deactivation hook.
	 * @uses add_action() To add various actions.
	 */
	private function setup_actions() {

		// Add actions to plugin activation and deactivation hooks
		add_action( 'activate_'   . $this->basename, 'bp_activation'   );
		add_action( 'deactivate_' . $this->basename, 'bp_deactivation' );

		// If BuddyPress is being deactivated, do not add any actions
		if ( bp_is_deactivation( $this->basename ) ) {
			return;
		}

		// Array of BuddyPress core actions
		$actions = array(
			'setup_theme',              // Setup the default theme compat
			'setup_current_user',       // Setup currently logged in user
			'register_post_types',      // Register post types
			'register_post_statuses',   // Register post statuses
			'register_taxonomies',      // Register taxonomies
			'register_views',           // Register the views
			'register_theme_directory', // Register the theme directory
			'register_theme_packages',  // Register bundled theme packages (bp-themes)
			'load_textdomain',          // Load textdomain
			'add_rewrite_tags',         // Add rewrite tags
			'generate_rewrite_rules'    // Generate rewrite rules
		);

		// Add the actions
		foreach( $actions as $class_action ) {
			if ( method_exists( $this, $class_action ) ) {
				add_action( 'bp_' . $class_action, array( $this, $class_action ), 5 );
			}
		}

		/**
		 * Fires after the setup of all BuddyPress actions.
		 *
		 * Includes bbp-core-hooks.php.
		 *
		 * @since 1.7.0
		 *
		 * @param BuddyPress $this. Current BuddyPress instance. Passed by reference.
		 */
		do_action_ref_array( 'bp_after_setup_actions', array( &$this ) );
	}

	/**
	 * Private method to align the active and database versions.
	 *
	 * @since 1.7.0
	 */
	private function versions() {

		// Get the possible DB versions (boy is this gross)
		$versions               = array();
		$versions['1.6-single'] = get_blog_option( $this->root_blog_id, '_bp_db_version' );

		// 1.6-single exists, so trust it
		if ( !empty( $versions['1.6-single'] ) ) {
			$this->db_version_raw = (int) $versions['1.6-single'];

		// If no 1.6-single exists, use the max of the others
		} else {
			$versions['1.2']        = get_site_option(                      'bp-core-db-version' );
			$versions['1.5-multi']  = get_site_option(                           'bp-db-version' );
			$versions['1.6-multi']  = get_site_option(                          '_bp_db_version' );
			$versions['1.5-single'] = get_blog_option( $this->root_blog_id,      'bp-db-version' );

			// Remove empty array items
			$versions             = array_filter( $versions );
			$this->db_version_raw = (int) ( !empty( $versions ) ) ? (int) max( $versions ) : 0;
		}
	}

	/** Public Methods ********************************************************/

	/**
	 * Set up BuddyPress's legacy theme directory.
	 *
	 * Starting with version 1.2, and ending with version 1.8, BuddyPress
	 * registered a custom theme directory - bp-themes - which contained
	 * the bp-default theme. Since BuddyPress 1.9, bp-themes is no longer
	 * registered (and bp-default no longer offered) on new installations.
	 * Sites using bp-default (or a child theme of bp-default) will
	 * continue to have bp-themes registered as before.
	 *
	 * @since 1.5.0
	 *
	 * @todo Move bp-default to wordpress.org/extend/themes and remove this.
	 */
	public function register_theme_directory() {
		if ( ! bp_do_register_theme_directory() ) {
			return;
		}

		register_theme_directory( $this->old_themes_dir );
	}

	/**
	 * Register bundled theme packages.
	 *
	 * Note that since we currently have complete control over bp-themes and
	 * the bp-legacy folders, it's fine to hardcode these here. If at a
	 * later date we need to automate this, an API will need to be built.
	 *
	 * @since 1.7.0
	 */
	public function register_theme_packages() {

		// Register the default theme compatibility package
		bp_register_theme_package( array(
			'id'      => 'legacy',
			'name'    => __( 'BuddyPress Default', 'buddypress' ),
			'version' => bp_get_version(),
			'dir'     => trailingslashit( $this->themes_dir . '/bp-legacy' ),
			'url'     => trailingslashit( $this->themes_url . '/bp-legacy' )
		) );

		// Register the basic theme stack. This is really dope.
		bp_register_template_stack( 'get_stylesheet_directory', 10 );
		bp_register_template_stack( 'get_template_directory',   12 );
		bp_register_template_stack( 'bp_get_theme_compat_dir',  14 );
	}

	/**
	 * Set up the default BuddyPress theme compatibility location.
	 *
	 * @since 1.7.0
	 */
	public function setup_theme() {

		// Bail if something already has this under control
		if ( ! empty( $this->theme_compat->theme ) ) {
			return;
		}

		// Setup the theme package to use for compatibility
		bp_setup_theme_compat( bp_get_theme_package_id() );
	}
}

/**
 * The main function responsible for returning the one true BuddyPress Instance to functions everywhere.
 *
 * Use this function like you would a global variable, except without needing
 * to declare the global.
 *
 * Example: <?php $bp = buddypress(); ?>
 *
 * @return BuddyPress The one true BuddyPress Instance.
 */
function buddypress() {
	return BuddyPress::instance();
}

/**
 * Hook BuddyPress early onto the 'plugins_loaded' action.
 *
 * This gives all other plugins the chance to load before BuddyPress, to get
 * their actions, filters, and overrides setup without BuddyPress being in the
 * way.
 */
if ( defined( 'BUDDYPRESS_LATE_LOAD' ) ) {
	add_action( 'plugins_loaded', 'buddypress', (int) BUDDYPRESS_LATE_LOAD );

// "And now here's something we hope you'll really like!"
} else {
	$GLOBALS['bp'] = buddypress();
}

endif;
