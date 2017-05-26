<?php
/**
 * BuddyPress Groups Classes.
 *
 * @package BuddyPress
 * @subpackage GroupsClasses
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * API for creating group extensions without having to hardcode the content into
 * the theme.
 *
 * To implement, extend this class. In your constructor, pass an optional array
 * of arguments to parent::init() to configure your widget. The config array
 * supports the following values:
 *   - 'slug' A unique identifier for your extension. This value will be used
 *     to build URLs, so make it URL-safe.
 *   - 'name' A translatable name for your extension. This value is used to
 *     populate the navigation tab, as well as the default titles for admin/
 *     edit/create tabs.
 *   - 'visibility' Set to 'public' (default) for your extension (the main tab
 *     as well as the widget) to be available to anyone who can access the
 *     group, 'private' otherwise.
 *   - 'nav_item_position' An integer explaining where the nav item should
 *     appear in the tab list.
 *   - 'enable_nav_item' Set to true for your extension's main tab to be
 *     available to anyone who can access the group.
 *   - 'nav_item_name' The translatable text you want to appear in the nav tab.
 *     Defaults to the value of 'name'.
 *   - 'display_hook' The WordPress action that the widget_display() method is
 *     hooked to.
 *   - 'template_file' The template file that will be used to load the content
 *     of your main extension tab. Defaults to 'groups/single/plugins.php'.
 *   - 'screens' A multi-dimensional array, described below.
 * 	 - 'access' Which users can visit the plugin's tab.
 * 	 - 'show_tab' Which users can see the plugin's navigation tab.
 *
 * BP_Group_Extension uses the concept of "settings screens". There are three
 * contexts for settings screens:
 *   - 'create', which inserts a new step into the group creation process
 *   - 'edit', which adds a tab for your extension into the Admin section of
 *     a group
 *   - 'admin', which adds a metabox to the Groups administration panel in the
 *     WordPress Dashboard
 * Each of these settings screens is populated by a pair of methods: one that
 * creates the markup for the screen, and one that processes form data
 * submitted from the screen. If your plugin needs screens in all three
 * contexts, and if the markup and form processing logic will be the same in
 * each case, you can define two methods to handle all of the screens:
 *   function settings_screen() {}
 *   function settings_screen_save() {}
 * If one or more of your settings screen needs separate logic, you may define
 * context-specific methods, for example:
 *   function edit_screen() {}
 *   function edit_screen_save() {}
 * BP_Group_Extension will use the more specific methods if they are available.
 *
 * You can further customize the settings screens (tab names, etc) by passing
 * an optional 'screens' parameter to the init array. The format is as follows:
 *   'screens' => array(
 *       'create' => array(
 *	     'slug' => 'foo',
 *	     'name' => 'Foo',
 *	     'position' => 55,
 *	     'screen_callback' => 'my_create_screen_callback',
 *	     'screen_save_callback' => 'my_create_screen_save_callback',
 *	 ),
 *	 'edit' => array( // ...
 *   ),
 * Only provide those arguments that you actually want to change from the
 * default configuration. BP_Group_Extension will do the rest.
 *
 * Note that the 'edit' screen accepts an additional parameter: 'submit_text',
 * which defines the text of the Submit button automatically added to the Edit
 * screen of the extension (defaults to 'Save Changes'). Also, the 'admin'
 * screen accepts two additional parameters: 'metabox_priority' and
 * 'metabox_context'. See the docs for add_meta_box() for more details on these
 * arguments.
 *
 * Prior to BuddyPress 1.7, group extension configurations were set slightly
 * differently. The legacy method is still supported, though deprecated.
 *
 * @package BuddyPress
 * @subpackage Groups
 * @since 1.1.0
 */
class BP_Group_Extension {

	/** Public ************************************************************/

	/**
	 * Information about this extension's screens.
	 *
	 * @since 1.8.0
	 * @var array
	 */
	public $screens = array();

	/**
	 * The name of the extending class.
	 *
	 * @since 1.8.0
	 * @var string
	 */
	public $class_name = '';

	/**
	 * A ReflectionClass object of the current extension.
	 *
	 * @since 1.8.0
	 * @var ReflectionClass
	 */
	public $class_reflection = null;

	/**
	 * Parsed configuration parameters for the extension.
	 *
	 * @since 1.8.0
	 * @var array
	 */
	public $params = array();

	/**
	 * Raw config params, as passed by the extending class.
	 *
	 * @since 2.1.0
	 * @var array
	 */
	public $params_raw = array();

	/**
	 * The ID of the current group.
	 *
	 * @since 1.8.0
	 * @var int
	 */
	public $group_id = 0;

	/**
	 * The slug of the current extension.
	 *
	 * @var string
	 */
	public $slug = '';

	/**
	 * The translatable name of the current extension.
	 *
	 * @var string
	 */
	public $name = '';

	/**
	 * The visibility of the extension tab. 'public' or 'private'.
	 *
	 * @var string
	 */
	public $visibility = 'public';

	/**
	 * The numeric position of the main nav item.
	 *
	 * @var int
	 */
	public $nav_item_position = 81;

	/**
	 * Whether to show the nav item.
	 *
	 * @var bool
	 */
	public $enable_nav_item = true;

	/**
	 * Whether the current user should see the navigation item.
	 *
	 * @since 2.1.0
	 * @var bool
	 */
	public $user_can_see_nav_item;

	/**
	 * Whether the current user can visit the tab.
	 *
	 * @since 2.1.0
	 * @var bool
	 */
	public $user_can_visit;

	/**
	 * The text of the nav item. Defaults to self::name.
	 *
	 * @var string
	 */
	public $nav_item_name = '';

	/**
	 * The WP action that self::widget_display() is attached to.
	 *
	 * Default: 'groups_custom_group_boxes'.
	 *
	 * @var string
	 */
	public $display_hook = 'groups_custom_group_boxes';

	/**
	 * The template file used to load the plugin content.
	 *
	 * Default: 'groups/single/plugins'.
	 *
	 * @var string
	 */
	public $template_file = 'groups/single/plugins';

	/** Protected *********************************************************/

	/**
	 * Has the extension been initialized?
	 *
	 * @since 1.8.0
	 * @var bool
	 */
	protected $initialized = false;

	/**
	 * Extension properties as set by legacy extensions.
	 *
	 * @since 1.8.0
	 * @var array
	 */
	protected $legacy_properties = array();

	/**
	 * Converted legacy parameters.
	 *
	 * These are the extension properties as set by legacy extensions, but
	 * then converted to match the new format for params.
	 *
	 * @since 1.8.0
	 * @var array
	 */
	protected $legacy_properties_converted = array();

	/**
	 * Redirect location as defined by post-edit save callback.
	 *
	 * @since 2.1.0
	 * @var string
	 */
	protected $post_save_redirect;

	/**
	 * Miscellaneous data as set by the __set() magic method.
	 *
	 * @since 1.8.0
	 * @var array
	 */
	protected $data = array();

	/** Screen Overrides **************************************************/

	/*
	 * Screen override methods are how your extension will display content
	 * and handle form submits. Your extension should only override those
	 * methods that it needs for its purposes.
	 */

	/**
	 * The content of the group tab.
	 *
	 * @param int|null $group_id
	 */
	public function display( $group_id = null ) {}

	/**
	 * Content displayed in a widget sidebar, if applicable
	 */
	public function widget_display() {}

	// *_screen() displays the settings form for the given context
	// *_screen_save() processes data submitted via the settings form
	// The settings_* methods are generic fallbacks, which can optionally
	// be overridden by the more specific edit_*, create_*, and admin_*
	// versions.
	public function settings_screen( $group_id = null ) {}
	public function settings_screen_save( $group_id = null ) {}
	public function edit_screen( $group_id = null ) {}
	public function edit_screen_save( $group_id = null ) {}
	public function create_screen( $group_id = null ) {}
	public function create_screen_save( $group_id = null ) {}
	public function admin_screen( $group_id = null ) {}
	public function admin_screen_save( $group_id = null ) {}

	/** Setup *************************************************************/

	/**
	 * Initialize the extension, using your config settings.
	 *
	 * Your plugin should call this method at the very end of its
	 * constructor, like so:
	 *
	 *   public function __construct() {
	 *       $args = array(
	 *           'slug' => 'my-group-extension',
	 *           'name' => 'My Group Extension',
	 *           // ...
	 *       );
	 *
	 *       parent::init( $args );
	 *   }
	 *
	 * @since 1.8.0
	 * @since 2.1.0 Added 'access' and 'show_tab' arguments to `$args`.
	 *
	 * @param array $args {
	 *     Array of initialization arguments.
	 *     @type string       $slug              Unique, URL-safe identifier for your extension.
	 *     @type string       $name              Translatable name for your extension. Used to populate
	 *                                           navigation items.
	 *     @type string       $visibility        Optional. Set to 'public' for your extension (the main tab as well
	 *                                           as the widget) to be available to anyone who can access the group;
	 *                                           set to 'private' otherwise. Default: 'public'.
	 *     @type int          $nav_item_position Optional. Location of the nav item in the tab list.
	 *                                           Default: 81.
	 *     @type bool         $enable_nav_item   Optional. Whether the extension's tab should be accessible to
	 *                                           anyone who can view the group. Default: true.
	 *     @type string       $nav_item_name     Optional. The translatable text you want to appear in the nav tab.
	 *                                           Default: the value of `$name`.
	 *     @type string       $display_hook      Optional. The WordPress action that the widget_display() method is
	 *                                           hooked to. Default: 'groups_custom_group_boxes'.
	 *     @type string       $template_file     Optional. Theme-relative path to the template file BP should use
	 *                                           to load the content of your main extension tab.
	 *                                           Default: 'groups/single/plugins.php'.
	 *     @type array        $screens           A multi-dimensional array of configuration information for the
	 *                                           extension screens. See docblock of {@link BP_Group_Extension}
	 *                                           for more details.
	 *     @type string|array $access            Which users can visit the plugin's tab. Possible values: 'anyone',
	 *                                           'loggedin', 'member', 'mod', 'admin' or 'noone'. ('member', 'mod',
	 *                                           'admin' refer to user's role in group.) Note that 'mod' targets
	 *                                           only group moderators. If you want to allow access to group moderators
	 *                                           and admins, specify `array( 'mod', 'admin' )`. Defaults to 'anyone'
	 *                                           for public groups and 'member' for private groups.
	 *     @type string|array $show_tab          Which users can see the plugin's navigation tab. Possible values:
	 *                                           'anyone', 'loggedin', 'member', 'mod', 'admin' or 'noone'.
	 *                                           ('member', 'mod', 'admin' refer to user's role in group.) Note
	 *                                           that 'mod' targets only group moderators. If you want to show the
	 *                                           tab to group moderators and admins, specify
	 *                                           `array( 'mod', 'admin' )`. Defaults to 'anyone' for public groups
	 *                                           and 'member' for private groups.
	 * }
	 */
	public function init( $args = array() ) {
		// Store the raw arguments
		$this->params_raw = $args;

		// Before this init() method was introduced, plugins were
		// encouraged to set their config directly. For backward
		// compatibility with these plugins, we detect whether this is
		// one of those legacy plugins, and parse any legacy arguments
		// with those passed to init()
		$this->parse_legacy_properties();
		$args = $this->parse_args_r( $args, $this->legacy_properties_converted );

		// Parse with defaults
		$this->params = $this->parse_args_r( $args, array(
			'slug'              => $this->slug,
			'name'              => $this->name,
			'visibility'        => $this->visibility,
			'nav_item_position' => $this->nav_item_position,
			'enable_nav_item'   => (bool) $this->enable_nav_item,
			'nav_item_name'     => $this->nav_item_name,
			'display_hook'      => $this->display_hook,
			'template_file'     => $this->template_file,
			'screens'           => $this->get_default_screens(),
			'access'            => null,
			'show_tab'          => null,
		) );

		$this->initialized = true;
	}

	/**
	 * The main setup routine for the extension.
	 *
	 * This method contains the primary logic for setting up an extension's
	 * configuration, setting up backward compatibility for legacy plugins,
	 * and hooking the extension's screen functions into WP and BP.
	 *
	 * Marked 'public' because it must be accessible to add_action().
	 * However, you should never need to invoke this method yourself - it
	 * is called automatically at the right point in the load order by
	 * bp_register_group_extension().
	 *
	 * @since 1.1.0
	 */
	public function _register() {

		// Detect and parse properties set by legacy extensions
		$this->parse_legacy_properties();

		// Initialize, if necessary. This should only happen for
		// legacy extensions that don't call parent::init() themselves
		if ( true !== $this->initialized ) {
			$this->init();
		}

		// Set some config values, based on the parsed params
		$this->group_id          = $this->get_group_id();
		$this->slug              = $this->params['slug'];
		$this->name              = $this->params['name'];
		$this->visibility        = $this->params['visibility'];
		$this->nav_item_position = $this->params['nav_item_position'];
		$this->nav_item_name     = $this->params['nav_item_name'];
		$this->display_hook      = $this->params['display_hook'];
		$this->template_file     = $this->params['template_file'];

		// Configure 'screens': create, admin, and edit contexts
		$this->setup_screens();

		// Configure access-related settings
		$this->setup_access_settings();

		// Mirror configuration data so it's accessible to plugins
		// that look for it in its old locations
		$this->setup_legacy_properties();

		// Hook the extension into BuddyPress
		$this->setup_display_hooks();
		$this->setup_create_hooks();
		$this->setup_edit_hooks();
		$this->setup_admin_hooks();
	}

	/**
	 * Set up some basic info about the Extension.
	 *
	 * Here we collect the name of the extending class, as well as a
	 * ReflectionClass that is used in get_screen_callback() to determine
	 * whether your extension overrides certain callback methods.
	 *
	 * @since 1.8.0
	 */
	protected function setup_class_info() {
		if ( empty( $this->class_name ) ) {
			$this->class_name = get_class( $this );
		}

		if ( is_null( $this->class_reflection ) ) {
			$this->class_reflection = new ReflectionClass( $this->class_name );
		}
	}

	/**
	 * Get the current group ID.
	 *
	 * Check for:
	 *   - current group
	 *   - new group
	 *   - group admin
	 *
	 * @since 1.8.0
	 *
	 * @return int
	 */
	public static function get_group_id() {

		// Usually this will work
		$group_id = bp_get_current_group_id();

		// On the admin, get the group id out of the $_GET params
		if ( empty( $group_id ) && is_admin() && ( isset( $_GET['page'] ) && ( 'bp-groups' === $_GET['page'] ) ) && ! empty( $_GET['gid'] ) ) {
			$group_id = (int) $_GET['gid'];
		}

		// This fallback will only be hit when the create step is very
		// early
		if ( empty( $group_id ) && bp_get_new_group_id() ) {
			$group_id = bp_get_new_group_id();
		}

		// On some setups, the group id has to be fetched out of the
		// $_POST array
		// @todo Figure out why this is happening during group creation
		if ( empty( $group_id ) && isset( $_POST['group_id'] ) ) {
			$group_id = (int) $_POST['group_id'];
		}

		return $group_id;
	}

	/**
	 * Gather configuration data about your screens.
	 *
	 * @since 1.8.0
	 *
	 * @return array
	 */
	protected function get_default_screens() {
		$this->setup_class_info();

		$screens = array(
			'create' => array(
				'position' => 81,
			),
			'edit'   => array(
				'submit_text' => __( 'Save Changes', 'buddypress' ),
			),
			'admin'  => array(
				'metabox_context'  => 'normal',
				'metabox_priority' => 'core',
			),
		);

		foreach ( $screens as $context => &$screen ) {
			$screen['enabled']     = true;
			$screen['name']        = $this->name;
			$screen['slug']        = $this->slug;

			$screen['screen_callback']      = $this->get_screen_callback( $context, 'screen'      );
			$screen['screen_save_callback'] = $this->get_screen_callback( $context, 'screen_save' );
		}

		return $screens;
	}

	/**
	 * Set up screens array based on params.
	 *
	 * @since 1.8.0
	 */
	protected function setup_screens() {
		foreach ( (array) $this->params['screens'] as $context => $screen ) {
			if ( empty( $screen['slug'] ) ) {
				$screen['slug'] = $this->slug;
			}

			if ( empty( $screen['name'] ) ) {
				$screen['name'] = $this->name;
			}

			$this->screens[ $context ] = $screen;
		}
	}

	/**
	 * Set up access-related settings for this extension.
	 *
	 * @since 2.1.0
	 */
	protected function setup_access_settings() {
		// Bail if no group ID is available
		if ( empty( $this->group_id ) ) {
			return;
		}

		// Backward compatibility
		if ( isset( $this->params['enable_nav_item'] ) ) {
			$this->enable_nav_item = (bool) $this->params['enable_nav_item'];
		}

		// Tab Access
		$this->user_can_visit = false;

		// Backward compatibility for components that do not provide
		// explicit 'access' parameter
		if ( empty( $this->params['access'] ) ) {
			if ( false === $this->enable_nav_item ) {
				$this->params['access'] = 'noone';
			} else {
				$group = groups_get_group( array(
					'group_id' => $this->group_id,
				) );

				if ( ! empty( $group->status ) && 'public' === $group->status ) {
					// Tabs in public groups are accessible to anyone by default
					$this->params['access'] = 'anyone';
				} else {
					// All other groups have members-only as the default
					$this->params['access'] = 'member';
				}
			}
		}

		// Parse multiple access conditions into an array
		$access_conditions = $this->params['access'];
		if ( ! is_array( $access_conditions ) ) {
			$access_conditions = explode( ',', $access_conditions );
		}

		// If the current user meets at least one condition, the
		// get access
		foreach ( $access_conditions as $access_condition ) {
			if ( $this->user_meets_access_condition( $access_condition ) ) {
				$this->user_can_visit = true;
				break;
			}
		}

		// Tab Visibility
		$this->user_can_see_nav_item = false;

		// Backward compatibility for components that do not provide
		// explicit 'show_tab' parameter
		if ( empty( $this->params['show_tab'] ) ) {
			if ( false === $this->params['enable_nav_item'] ) {
				// enable_nav_item is only false if it's been
				// defined explicitly as such in the
				// constructor. So we always trust this value
				$this->params['show_tab'] = 'noone';

			} elseif ( isset( $this->params_raw['enable_nav_item'] ) || isset( $this->params_raw['visibility'] ) ) {
				// If enable_nav_item or visibility is passed,
				// we assume this  is a legacy extension.
				// Legacy behavior is that enable_nav_item=true +
				// visibility=private implies members-only
				if ( 'public' !== $this->visibility ) {
					$this->params['show_tab'] = 'member';
				} else {
					$this->params['show_tab'] = 'anyone';
				}

			} else {
				// No show_tab or enable_nav_item value is
				// available, so match the value of 'access'
				$this->params['show_tab'] = $this->params['access'];
			}
		}

		// Parse multiple access conditions into an array
		$access_conditions = $this->params['show_tab'];
		if ( ! is_array( $access_conditions ) ) {
			$access_conditions = explode( ',', $access_conditions );
		}

		// If the current user meets at least one condition, the
		// get access
		foreach ( $access_conditions as $access_condition ) {
			if ( $this->user_meets_access_condition( $access_condition ) ) {
				$this->user_can_see_nav_item = true;
				break;
			}
		}
	}

	/**
	 * Check whether the current user meets an access condition.
	 *
	 * @param string $access_condition 'anyone', 'loggedin', 'member',
	 *                                 'mod', 'admin' or 'noone'.
	 * @return bool
	 */
	protected function user_meets_access_condition( $access_condition ) {
		$group = groups_get_group( array(
			'group_id' => $this->group_id,
		) );

		switch ( $access_condition ) {
			case 'admin' :
				$meets_condition = groups_is_user_admin( bp_loggedin_user_id(), $this->group_id );
				break;

			case 'mod' :
				$meets_condition = groups_is_user_mod( bp_loggedin_user_id(), $this->group_id );
				break;

			case 'member' :
				$meets_condition = groups_is_user_member( bp_loggedin_user_id(), $this->group_id );
				break;

			case 'loggedin' :
				$meets_condition = is_user_logged_in();
				break;

			case 'noone' :
				$meets_condition = false;
				break;

			case 'anyone' :
			default :
				$meets_condition = true;
				break;
		}

		return $meets_condition;
	}

	/** Display ***********************************************************/

	/**
	 * Hook this extension's group tab into BuddyPress, if necessary.
	 *
	 * @since 1.8.0
	 */
	protected function setup_display_hooks() {

		// Bail if not a group
		if ( ! bp_is_group() ) {
			return;
		}

		// Backward compatibility only
		if ( ( 'public' !== $this->visibility ) && ! buddypress()->groups->current_group->user_has_access ) {
			return;
		}

		// If the user can see the nav item, we create it.
		$user_can_see_nav_item = $this->user_can_see_nav_item();

		if ( $user_can_see_nav_item ) {
			$group_permalink = bp_get_group_permalink( groups_get_current_group() );

			bp_core_create_subnav_link( array(
				'name'            => ! $this->nav_item_name ? $this->name : $this->nav_item_name,
				'slug'            => $this->slug,
				'parent_slug'     => bp_get_current_group_slug(),
				'parent_url'      => $group_permalink,
				'position'        => $this->nav_item_position,
				'item_css_id'     => 'nav-' . $this->slug,
				'screen_function' => array( &$this, '_display_hook' ),
				'user_has_access' => $user_can_see_nav_item,
				'no_access_url'   => $group_permalink,
			) );
		}

		// If the user can visit the screen, we register it.
		$user_can_visit = $this->user_can_visit();

		if ( $user_can_visit ) {
			$group_permalink = bp_get_group_permalink( groups_get_current_group() );

			bp_core_register_subnav_screen_function( array(
				'slug'            => $this->slug,
				'parent_slug'     => bp_get_current_group_slug(),
				'screen_function' => array( &$this, '_display_hook' ),
				'user_has_access' => $user_can_visit,
				'no_access_url'   => $group_permalink,
			) );

			// When we are viewing the extension display page, set the title and options title
			if ( bp_is_current_action( $this->slug ) ) {
				add_filter( 'bp_group_user_has_access',   array( $this, 'group_access_protection' ), 10, 2 );
				add_action( 'bp_template_content_header', create_function( '', 'echo "' . esc_attr( $this->name ) . '";' ) );
				add_action( 'bp_template_title',          create_function( '', 'echo "' . esc_attr( $this->name ) . '";' ) );
			}
		}

		// Hook the group home widget
		if ( ! bp_current_action() && bp_is_current_action( 'home' ) ) {
			add_action( $this->display_hook, array( &$this, 'widget_display' ) );
		}
	}

	/**
	 * Hook the main display method, and loads the template file.
	 */
	public function _display_hook() {
		add_action( 'bp_template_content', array( &$this, 'call_display' ) );

		/**
		 * Filters the template to load for the main display method.
		 *
		 * @since 1.0.0
		 *
		 * @param string $template_file Path to the template to load.
		 */
		bp_core_load_template( apply_filters( 'bp_core_template_plugin', $this->template_file ) );
	}

	/**
	 * Call the display() method.
	 *
	 * We use this wrapper so that we can pass the group_id to the
	 * display() callback.
	 *
	 * @since 2.1.1
	 */
	public function call_display() {
		$this->display( $this->group_id );
	}

	/**
	 * Determine whether the current user should see this nav tab.
	 *
	 * Note that this controls only the display of the navigation item.
	 * Access to the tab is controlled by the user_can_visit() check.
	 *
	 * @since 2.1.0
	 *
	 * @param bool $user_can_see_nav_item
	 *
	 * @return bool
	 */
	public function user_can_see_nav_item( $user_can_see_nav_item = false ) {
		if ( 'noone' !== $this->params['show_tab'] && current_user_can( 'bp_moderate' ) ) {
			return true;
		}

		return $this->user_can_see_nav_item;
	}

	/**
	 * Determine whether the current user has access to visit this tab.
	 *
	 * @since 2.1.0
	 *
	 * @param bool $user_can_visit
	 *
	 * @return bool
	 */
	public function user_can_visit( $user_can_visit = false ) {
		if ( 'noone' !== $this->params['access'] && current_user_can( 'bp_moderate' ) ) {
			return true;
		}

		return $this->user_can_visit;
	}

	/**
	 * Filter the access check in bp_groups_group_access_protection() for this extension.
	 *
	 * Note that $no_access_args is passed by reference, as there are some
	 * circumstances where the bp_core_no_access() arguments need to be
	 * modified before the redirect takes place.
	 *
	 * @since 2.1.0
	 *
	 * @param bool  $user_can_visit
	 * @param array $no_access_args
	 *
	 * @return bool
	 */
	public function group_access_protection( $user_can_visit, &$no_access_args ) {
		$user_can_visit = $this->user_can_visit();

		if ( ! $user_can_visit && is_user_logged_in() ) {
			$current_group = groups_get_group( array(
				'group_id' => $this->group_id,
			) );

			$no_access_args['message'] = __( 'You do not have access to this content.', 'buddypress' );
			$no_access_args['root'] = bp_get_group_permalink( $current_group ) . 'home/';
			$no_access_args['redirect'] = false;
		}

		return $user_can_visit;
	}


	/** Create ************************************************************/

	/**
	 * Hook this extension's Create step into BuddyPress, if necessary.
	 *
	 * @since 1.8.0
	 */
	protected function setup_create_hooks() {
		if ( ! $this->is_screen_enabled( 'create' ) ) {
			return;
		}

		$screen = $this->screens['create'];

		// Insert the group creation step for the new group extension
		buddypress()->groups->group_creation_steps[ $screen['slug'] ] = array(
			'name'     => $screen['name'],
			'slug'     => $screen['slug'],
			'position' => $screen['position'],
		);

		// The maybe_ methods check to see whether the create_*
		// callbacks should be invoked (ie, are we on the
		// correct group creation step). Hooked in separate
		// methods because current creation step info not yet
		// available at this point
		add_action( 'groups_custom_create_steps', array( $this, 'maybe_create_screen' ) );
		add_action( 'groups_create_group_step_save_' . $screen['slug'], array( $this, 'maybe_create_screen_save' ) );
	}

	/**
	 * Call the create_screen() method, if we're on the right page.
	 *
	 * @since 1.8.0
	 */
	public function maybe_create_screen() {
		if ( ! bp_is_group_creation_step( $this->screens['create']['slug'] ) ) {
			return;
		}

		call_user_func( $this->screens['create']['screen_callback'], $this->group_id );
		$this->nonce_field( 'create' );

		// The create screen requires an additional nonce field
		// due to a quirk in the way the templates are built
		wp_nonce_field( 'groups_create_save_' . bp_get_groups_current_create_step(), '_wpnonce', false );
	}

	/**
	 * Call the create_screen_save() method, if we're on the right page.
	 *
	 * @since 1.8.0
	 */
	public function maybe_create_screen_save() {
		if ( ! bp_is_group_creation_step( $this->screens['create']['slug'] ) ) {
			return;
		}

		$this->check_nonce( 'create' );
		call_user_func( $this->screens['create']['screen_save_callback'], $this->group_id );
	}

	/** Edit **************************************************************/

	/**
	 * Hook this extension's Edit panel into BuddyPress, if necessary.
	 *
	 * @since 1.8.0
	 */
	protected function setup_edit_hooks() {
		// Bail if not in a group
		if ( ! bp_is_group() ) {
			return;
		}

		// Bail if not an edit screen
		if ( ! $this->is_screen_enabled( 'edit' ) || ! bp_is_item_admin() ) {
			return;
		}

		$screen = $this->screens['edit'];

		$position = isset( $screen['position'] ) ? (int) $screen['position'] : 10;
		$position += 40;

		$current_group = groups_get_current_group();
		$admin_link = trailingslashit( bp_get_group_permalink( $current_group ) . 'admin' );

		$subnav_args = array(
			'name'            => $screen['name'],
			'slug'            => $screen['slug'],
			'parent_slug'     => $current_group->slug . '_manage',
			'parent_url'      => trailingslashit( bp_get_group_permalink( $current_group ) . 'admin' ),
			'user_has_access' => bp_is_item_admin(),
			'position'        => $position,
			'screen_function' => 'groups_screen_group_admin',
		);

		// Should we add a menu to the Group's WP Admin Bar
		if ( ! empty( $screen['show_in_admin_bar'] ) ) {
			$subnav_args['show_in_admin_bar'] = true;
		}

		// Add the tab to the manage navigation
		bp_core_new_subnav_item( $subnav_args );

		// Catch the edit screen and forward it to the plugin template
		if ( bp_is_groups_component() && bp_is_current_action( 'admin' ) && bp_is_action_variable( $screen['slug'], 0 ) ) {
			$this->call_edit_screen_save( $this->group_id );

			add_action( 'groups_custom_edit_steps', array( &$this, 'call_edit_screen' ) );

			// Determine the proper template and save for later
			// loading
			if ( '' !== bp_locate_template( array( 'groups/single/home.php' ), false ) ) {
				$this->edit_screen_template = '/groups/single/home';
			} else {
				add_action( 'bp_template_content_header', create_function( '', 'echo "<ul class=\"content-header-nav\">"; bp_group_admin_tabs(); echo "</ul>";' ) );
				add_action( 'bp_template_content', array( &$this, 'call_edit_screen' ) );
				$this->edit_screen_template = '/groups/single/plugins';
			}

			// We load the template at bp_screens, to give all
			// extensions a chance to load
			add_action( 'bp_screens', array( $this, 'call_edit_screen_template_loader' ) );
		}
	}

	/**
	 * Call the edit_screen() method.
	 *
	 * Previous versions of BP_Group_Extension required plugins to provide
	 * their own Submit button and nonce fields when building markup. In
	 * BP 1.8, this requirement was lifted - BP_Group_Extension now handles
	 * all required submit buttons and nonces.
	 *
	 * We put the edit screen markup into an output buffer before echoing.
	 * This is so that we can check for the presence of a hardcoded submit
	 * button, as would be present in legacy plugins; if one is found, we
	 * do not auto-add our own button.
	 *
	 * @since 1.8.0
	 */
	public function call_edit_screen() {
		ob_start();
		call_user_func( $this->screens['edit']['screen_callback'], $this->group_id );
		$screen = ob_get_contents();
		ob_end_clean();

		echo $this->maybe_add_submit_button( $screen );

		$this->nonce_field( 'edit' );
	}

	/**
	 * Check the nonce, and call the edit_screen_save() method.
	 *
	 * @since 1.8.0
	 */
	public function call_edit_screen_save() {
		if ( empty( $_POST ) ) {
			return;
		}

		// When DOING_AJAX, the POST global will be populated, but we
		// should assume it's a save
		if ( defined( 'DOING_AJAX' ) && DOING_AJAX ) {
			return;
		}

		$this->check_nonce( 'edit' );

		// Detect whether the screen_save_callback is performing a
		// redirect, so that we don't do one of our own
		add_filter( 'wp_redirect', array( $this, 'detect_post_save_redirect' ) );

		// Call the extension's save routine
		call_user_func( $this->screens['edit']['screen_save_callback'], $this->group_id );

		// Clean up detection filters
		remove_filter( 'wp_redirect', array( $this, 'detect_post_save_redirect' ) );

		// Perform a redirect only if one has not already taken place
		if ( empty( $this->post_save_redirect ) ) {

			/**
			 * Filters the URL to redirect to after group edit screen save.
			 *
			 * Only runs if a redirect has not already occurred.
			 *
			 * @since 2.1.0
			 *
			 * @param string $value URL to redirect to.
			 */
			$redirect_to = apply_filters( 'bp_group_extension_edit_screen_save_redirect', bp_get_requested_url( ) );

			bp_core_redirect( $redirect_to );
			die();
		}
	}

	/**
	 * Load the template that houses the Edit screen.
	 *
	 * Separated out into a callback so that it can run after all other
	 * Group Extensions have had a chance to register their navigation, to
	 * avoid missing tabs.
	 *
	 * Hooked to 'bp_screens'.
	 *
	 * @since 1.8.0
	 *
	 * @see BP_Group_Extension::setup_edit_hooks()
	 */
	public function call_edit_screen_template_loader() {
		bp_core_load_template( $this->edit_screen_template );
	}

	/**
	 * Add a submit button to the edit form, if it needs one.
	 *
	 * There's an inconsistency in the way that the group Edit and Create
	 * screens are rendered: the Create screen has a submit button built
	 * in, but the Edit screen does not. This function allows plugin
	 * authors to write markup that does not contain the submit button for
	 * use on both the Create and Edit screens - BP will provide the button
	 * if one is not found.
	 *
	 * @since 1.8.0
	 *
	 * @param string $screen The screen markup, captured in the output
	 *                       buffer.
	 *
	 * @return string $screen The same markup, with a submit button added.
	 */
	protected function maybe_add_submit_button( $screen = '' ) {
		if ( $this->has_submit_button( $screen ) ) {
			return $screen;
		}

		return $screen . sprintf(
			'<div id="%s"><input type="submit" name="save" value="%s" id="%s"></div>',
			'bp-group-edit-' . $this->slug . '-submit-wrapper',
			$this->screens['edit']['submit_text'],
			'bp-group-edit-' . $this->slug . '-submit'
		);
	}

	/**
	 * Does the given markup have a submit button?
	 *
	 * @since 1.8.0
	 *
	 * @param string $screen The markup to check.
	 *
	 * @return bool True if a Submit button is found, otherwise false.
	 */
	public static function has_submit_button( $screen = '' ) {
		$pattern = "/<input[^>]+type=[\'\"]submit[\'\"]/";
		preg_match( $pattern, $screen, $matches );
		return ! empty( $matches[0] );
	}

	/**
	 * Detect redirects hardcoded into edit_screen_save() callbacks.
	 *
	 * @since 2.1.0
	 *
	 * @param string $redirect
	 *
	 * @return string
	 */
	public function detect_post_save_redirect( $redirect = '' ) {
		if ( ! empty( $redirect ) ) {
			$this->post_save_redirect = $redirect;
		}

		return $redirect;
	}

	/** Admin *************************************************************/

	/**
	 * Hook this extension's Admin metabox into BuddyPress, if necessary.
	 *
	 * @since 1.8.0
	 */
	protected function setup_admin_hooks() {
		if ( ! $this->is_screen_enabled( 'admin' ) || ! is_admin() ) {
			return;
		}

		// Hook the admin screen markup function to the content hook
		add_action( 'bp_groups_admin_meta_box_content_' . $this->slug, array( $this, 'call_admin_screen' ) );

		// Initialize the metabox
		add_action( 'bp_groups_admin_meta_boxes', array( $this, '_meta_box_display_callback' ) );

		// Catch the metabox save
		add_action( 'bp_group_admin_edit_after', array( $this, 'call_admin_screen_save' ), 10 );
	}

	/**
	 * Call the admin_screen() method, and add a nonce field.
	 *
	 * @since 1.8.0
	 */
	public function call_admin_screen() {
		call_user_func( $this->screens['admin']['screen_callback'], $this->group_id );
		$this->nonce_field( 'admin' );
	}

	/**
	 * Check the nonce, and call the admin_screen_save() method.
	 *
	 * @since 1.8.0
	 */
	public function call_admin_screen_save() {
		$this->check_nonce( 'admin' );
		call_user_func( $this->screens['admin']['screen_save_callback'], $this->group_id );
	}

	/**
	 * Create the Dashboard meta box for this extension.
	 *
	 * @since 1.7.0
	 */
	public function _meta_box_display_callback() {
		$group_id = isset( $_GET['gid'] ) ? (int) $_GET['gid'] : 0;
		$screen   = $this->screens['admin'];

		add_meta_box(
			$screen['slug'],
			$screen['name'],
			create_function( '', 'do_action( "bp_groups_admin_meta_box_content_' . $this->slug . '", ' . $group_id . ' );' ),
			get_current_screen()->id,
			$screen['metabox_context'],
			$screen['metabox_priority']
		);
	}


	/** Utilities *********************************************************/

	/**
	 * Generate the nonce fields for a settings form.
	 *
	 * The nonce field name (the second param passed to wp_nonce_field)
	 * contains this extension's slug and is thus unique to this extension.
	 * This is necessary because in some cases (namely, the Dashboard),
	 * more than one extension may generate nonces on the same page, and we
	 * must avoid name clashes.
	 *
	 * @since 1.8.0
	 *
	 * @param string $context Screen context. 'create', 'edit', or 'admin'.
	 */
	public function nonce_field( $context = '' ) {
		wp_nonce_field( 'bp_group_extension_' . $this->slug . '_' . $context, '_bp_group_' . $context . '_nonce_' . $this->slug );
	}

	/**
	 * Check the nonce on a submitted settings form.
	 *
	 * @since 1.8.0
	 *
	 * @param string $context Screen context. 'create', 'edit', or 'admin'.
	 */
	public function check_nonce( $context = '' ) {
		check_admin_referer( 'bp_group_extension_' . $this->slug . '_' . $context, '_bp_group_' . $context . '_nonce_' . $this->slug );
	}

	/**
	 * Is the specified screen enabled?
	 *
	 * To be enabled, a screen must both have the 'enabled' key set to true
	 * (legacy: $this->enable_create_step, etc), and its screen_callback
	 * must also exist and be callable.
	 *
	 * @since 1.8.0
	 *
	 * @param string $context Screen context. 'create', 'edit', or 'admin'.
	 *
	 * @return bool True if the screen is enabled, otherwise false.
	 */
	public function is_screen_enabled( $context = '' ) {
		$enabled = false;

		if ( isset( $this->screens[ $context ] ) ) {
			$enabled = $this->screens[ $context ]['enabled'] && is_callable( $this->screens[ $context ]['screen_callback'] );
		}

		return (bool) $enabled;
	}

	/**
	 * Get the appropriate screen callback for the specified context/type.
	 *
	 * BP Group Extensions have three special "screen contexts": create,
	 * admin, and edit. Each of these contexts has a corresponding
	 * _screen() and _screen_save() method, which allow group extension
	 * plugins to define different markup and logic for each context.
	 *
	 * BP also supports fallback settings_screen() and
	 * settings_screen_save() methods, which can be used to define markup
	 * and logic that is shared between context. For each context, you may
	 * either provide context-specific methods, or you can let BP fall back
	 * on the shared settings_* callbacks.
	 *
	 * For example, consider a BP_Group_Extension implementation that looks
	 * like this:
	 *
	 *   // ...
	 *   function create_screen( $group_id ) { ... }
	 *   function create_screen_save( $group_id ) { ... }
	 *   function settings_screen( $group_id ) { ... }
	 *   function settings_screen_save( $group_id ) { ... }
	 *   // ...
	 *
	 * BP_Group_Extension will use your create_* methods for the Create
	 * steps, and will use your generic settings_* methods for the Edit
	 * and Admin contexts. This schema allows plugin authors maximum
	 * flexibility without having to repeat themselves.
	 *
	 * The get_screen_callback() method uses a ReflectionClass object to
	 * determine whether your extension has provided a given callback.
	 *
	 * @since 1.8.0
	 *
	 * @param string $context Screen context. 'create', 'edit', or 'admin'.
	 * @param string $type    Screen type. 'screen' or 'screen_save'. Default:
	 *                        'screen'.
	 *
	 * @return callable A callable function handle.
	 */
	public function get_screen_callback( $context = '', $type = 'screen' ) {
		$callback = '';

		// Try the context-specific callback first
		$method  = $context . '_' . $type;
		$rmethod = $this->class_reflection->getMethod( $method );
		if ( isset( $rmethod->class ) && $this->class_name === $rmethod->class ) {
			$callback = array( $this, $method );
		}

		if ( empty( $callback ) ) {
			$fallback_method  = 'settings_' . $type;
			$rfallback_method = $this->class_reflection->getMethod( $fallback_method );
			if ( isset( $rfallback_method->class ) && $this->class_name === $rfallback_method->class ) {
				$callback = array( $this, $fallback_method );
			}
		}

		return $callback;
	}

	/**
	 * Recursive argument parsing.
	 *
	 * This acts like a multi-dimensional version of wp_parse_args() (minus
	 * the querystring parsing - you must pass arrays).
	 *
	 * Values from $a override those from $b; keys in $b that don't exist
	 * in $a are passed through.
	 *
	 * This is different from array_merge_recursive(), both because of the
	 * order of preference ($a overrides $b) and because of the fact that
	 * array_merge_recursive() combines arrays deep in the tree, rather
	 * than overwriting the b array with the a array.
	 *
	 * The implementation of this function is specific to the needs of
	 * BP_Group_Extension, where we know that arrays will always be
	 * associative, and that an argument under a given key in one array
	 * will be matched by a value of identical depth in the other one. The
	 * function is NOT designed for general use, and will probably result
	 * in unexpected results when used with data in the wild. See, eg,
	 * https://core.trac.wordpress.org/ticket/19888
	 *
	 * @since 1.8.0
	 *
	 * @param array $a First set of arguments.
	 * @param array $b Second set of arguments.
	 *
	 * @return array Parsed arguments.
	 */
	public static function parse_args_r( &$a, $b ) {
		$a = (array) $a;
		$b = (array) $b;
		$r = $b;

		foreach ( $a as $k => &$v ) {
			if ( is_array( $v ) && isset( $r[ $k ] ) ) {
				$r[ $k ] = self::parse_args_r( $v, $r[ $k ] );
			} else {
				$r[ $k ] = $v;
			}
		}

		return $r;
	}

	/** Legacy Support ********************************************************/

	/*
	 * In BuddyPress 1.8, the recommended technique for configuring
	 * extensions changed from directly setting various object properties
	 * in the class constructor, to passing a configuration array to
	 * parent::init(). The following methods ensure that extensions created
	 * in the old way continue to work, by converting legacy configuration
	 * data to the new format.
	 */

	/**
	 * Provide access to otherwise unavailable object properties.
	 *
	 * This magic method is here for backward compatibility with plugins
	 * that refer to config properties that have moved to a different
	 * location (such as enable_create_step, which is now at
	 * $this->screens['create']['enabled']
	 *
	 * The legacy_properties array is set up in
	 * self::setup_legacy_properties().
	 *
	 * @since 1.8.0
	 *
	 * @param string $key Property name.
	 *
	 * @return mixed The value if found, otherwise null.
	 */
	public function __get( $key ) {
		if ( isset( $this->legacy_properties[ $key ] ) ) {
			return $this->legacy_properties[ $key ];
		} elseif ( isset( $this->data[ $key ] ) ) {
			return $this->data[ $key ];
		} else {
			return null;
		}
	}

	/**
	 * Provide a fallback for isset( $this->foo ) when foo is unavailable.
	 *
	 * This magic method is here for backward compatibility with plugins
	 * that have set their class config options directly in the class
	 * constructor. The parse_legacy_properties() method of the current
	 * class needs to check whether any legacy keys have been put into the
	 * $this->data array.
	 *
	 * @since 1.8.0
	 *
	 * @param string $key Property name.
	 *
	 * @return bool True if the value is set, otherwise false.
	 */
	public function __isset( $key ) {
		if ( isset( $this->legacy_properties[ $key ] ) ) {
			return true;
		} elseif ( isset( $this->data[ $key ] ) ) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Allow plugins to set otherwise unavailable object properties.
	 *
	 * This magic method is here for backward compatibility with plugins
	 * that may attempt to modify the group extension by manually assigning
	 * a value to an object property that no longer exists, such as
	 * $this->enable_create_step.
	 *
	 * @since 1.8.0
	 *
	 * @param string $key Property name.
	 * @param mixed $value Property value.
	 */
	public function __set( $key, $value ) {

		if ( empty( $this->initialized ) ) {
			$this->data[ $key ] = $value;
		}

		switch ( $key ) {
			case 'enable_create_step' :
				$this->screens['create']['enabled'] = $value;
				break;

			case 'enable_edit_item' :
				$this->screens['edit']['enabled'] = $value;
				break;

			case 'enable_admin_item' :
				$this->screens['admin']['enabled'] = $value;
				break;

			case 'create_step_position' :
				$this->screens['create']['position'] = $value;
				break;

			// Note: 'admin' becomes 'edit' to distinguish from Dashboard 'admin'
			case 'admin_name' :
				$this->screens['edit']['name'] = $value;
				break;

			case 'admin_slug' :
				$this->screens['edit']['slug'] = $value;
				break;

			case 'create_name' :
				$this->screens['create']['name'] = $value;
				break;

			case 'create_slug' :
				$this->screens['create']['slug'] = $value;
				break;

			case 'admin_metabox_context' :
				$this->screens['admin']['metabox_context'] = $value;
				break;

			case 'admin_metabox_priority' :
				$this->screens['admin']['metabox_priority'] = $value;
				break;

			default :
				$this->data[ $key ] = $value;
				break;
		}
	}

	/**
	 * Return a list of legacy properties.
	 *
	 * The legacy implementation of BP_Group_Extension used all of these
	 * object properties for configuration. Some have been moved.
	 *
	 * @since 1.8.0
	 *
	 * @return array List of legacy property keys.
	 */
	protected function get_legacy_property_list() {
		return array(
			'name',
			'slug',
			'admin_name',
			'admin_slug',
			'create_name',
			'create_slug',
			'visibility',
			'create_step_position',
			'nav_item_position',
			'admin_metabox_context',
			'admin_metabox_priority',
			'enable_create_step',
			'enable_nav_item',
			'enable_edit_item',
			'enable_admin_item',
			'nav_item_name',
			'display_hook',
			'template_file',
		);
	}

	/**
	 * Parse legacy properties.
	 *
	 * The old standard for BP_Group_Extension was for plugins to register
	 * their settings as properties in their constructor. The new method is
	 * to pass a config array to the init() method. In order to support
	 * legacy plugins, we slurp up legacy properties, and later on we'll
	 * parse them into the new init() array.
	 *
	 * @since 1.8.0
	 */
	protected function parse_legacy_properties() {

		// Only run this one time
		if ( ! empty( $this->legacy_properties_converted ) ) {
			return;
		}

		$properties = $this->get_legacy_property_list();

		// By-reference variable for convenience
		$lpc =& $this->legacy_properties_converted;

		foreach ( $properties as $property ) {

			// No legacy config exists for this key
			if ( ! isset( $this->{$property} ) ) {
				continue;
			}

			// Grab the value and record it as appropriate
			$value = $this->{$property};

			switch ( $property ) {
				case 'enable_create_step' :
					$lpc['screens']['create']['enabled'] = (bool) $value;
					break;

				case 'enable_edit_item' :
					$lpc['screens']['edit']['enabled'] = (bool) $value;
					break;

				case 'enable_admin_item' :
					$lpc['screens']['admin']['enabled'] = (bool) $value;
					break;

				case 'create_step_position' :
					$lpc['screens']['create']['position'] = $value;
					break;

				// Note: 'admin' becomes 'edit' to distinguish from Dashboard 'admin'
				case 'admin_name' :
					$lpc['screens']['edit']['name'] = $value;
					break;

				case 'admin_slug' :
					$lpc['screens']['edit']['slug'] = $value;
					break;

				case 'create_name' :
					$lpc['screens']['create']['name'] = $value;
					break;

				case 'create_slug' :
					$lpc['screens']['create']['slug'] = $value;
					break;

				case 'admin_metabox_context' :
					$lpc['screens']['admin']['metabox_context'] = $value;
					break;

				case 'admin_metabox_priority' :
					$lpc['screens']['admin']['metabox_priority'] = $value;
					break;

				default :
					$lpc[ $property ] = $value;
					break;
			}
		}
	}

	/**
	 * Set up legacy properties.
	 *
	 * This method is responsible for ensuring that all legacy config
	 * properties are stored in an array $this->legacy_properties, so that
	 * they remain available to plugins that reference the variables at
	 * their old locations.
	 *
	 * @since 1.8.0
	 *
	 * @see BP_Group_Extension::__get()
	 */
	protected function setup_legacy_properties() {

		// Only run this one time
		if ( ! empty( $this->legacy_properties ) ) {
			return;
		}

		$properties = $this->get_legacy_property_list();
		$params     = $this->params;
		$lp         =& $this->legacy_properties;

		foreach ( $properties as $property ) {
			switch ( $property ) {
				case 'enable_create_step' :
					$lp['enable_create_step'] = $params['screens']['create']['enabled'];
					break;

				case 'enable_edit_item' :
					$lp['enable_edit_item'] = $params['screens']['edit']['enabled'];
					break;

				case 'enable_admin_item' :
					$lp['enable_admin_item'] = $params['screens']['admin']['enabled'];
					break;

				case 'create_step_position' :
					$lp['create_step_position'] = $params['screens']['create']['position'];
					break;

				// Note: 'admin' becomes 'edit' to distinguish from Dashboard 'admin'
				case 'admin_name' :
					$lp['admin_name'] = $params['screens']['edit']['name'];
					break;

				case 'admin_slug' :
					$lp['admin_slug'] = $params['screens']['edit']['slug'];
					break;

				case 'create_name' :
					$lp['create_name'] = $params['screens']['create']['name'];
					break;

				case 'create_slug' :
					$lp['create_slug'] = $params['screens']['create']['slug'];
					break;

				case 'admin_metabox_context' :
					$lp['admin_metabox_context'] = $params['screens']['admin']['metabox_context'];
					break;

				case 'admin_metabox_priority' :
					$lp['admin_metabox_priority'] = $params['screens']['admin']['metabox_priority'];
					break;

				default :
					// All other items get moved over
					$lp[ $property ] = $params[ $property ];

					// Also reapply to the object, for backpat
					$this->{$property} = $params[ $property ];

					break;
			}
		}
	}
}

/**
 * Register a new Group Extension.
 *
 * @param string Name of the Extension class.
 * @return false|null Returns false on failure, otherwise null.
 */
function bp_register_group_extension( $group_extension_class = '' ) {

	if ( ! class_exists( $group_extension_class ) ) {
		return false;
	}

	// Register the group extension on the bp_init action so we have access
	// to all plugins.
	add_action( 'bp_init', create_function( '', '
		$extension = new ' . $group_extension_class . ';
		add_action( "bp_actions", array( &$extension, "_register" ), 8 );
		add_action( "admin_init", array( &$extension, "_register" ) );
	' ), 11 );
}
