<?php
/**
 * BuddyPress Members Admin
 *
 * @package BuddyPress
 * @subpackage MembersAdmin
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

if ( !class_exists( 'BP_Members_Admin' ) ) :

/**
 * Load Members admin area.
 *
 * @since 2.0.0
 */
class BP_Members_Admin {

	/** Directory *************************************************************/

	/**
	 * Path to the BP Members Admin directory.
	 *
	 * @var string $admin_dir
	 */
	public $admin_dir = '';

	/** URLs ******************************************************************/

	/**
	 * URL to the BP Members Admin directory.
	 *
	 * @var string $admin_url
	 */
	public $admin_url = '';

	/**
	 * URL to the BP Members Admin CSS directory.
	 *
	 * @var string $css_url
	 */
	public $css_url = '';

	/**
	 * URL to the BP Members Admin JS directory.
	 *
	 * @var string
	 */
	public $js_url = '';

	/** Other *****************************************************************/

	/**
	 * Screen id for edit user's profile page.
	 *
	 * @var string
	 */
	public $user_page = '';

	/**
	 * Setup BP Members Admin.
	 *
	 * @since 2.0.0
	 *
	 * @uses buddypress() to get BuddyPress main instance.
	 */
	public static function register_members_admin() {
		if ( ! is_admin() ) {
			return;
		}

		$bp = buddypress();

		if ( empty( $bp->members->admin ) ) {
			$bp->members->admin = new self;
		}

		return $bp->members->admin;
	}

	/**
	 * Constructor method.
	 *
	 * @since 2.0.0
	 */
	public function __construct() {
		$this->setup_globals();
		$this->setup_actions();
	}

	/**
	 * Set admin-related globals.
	 *
	 * @since 2.0.0
	 */
	private function setup_globals() {
		$bp = buddypress();

		// Paths and URLs
		$this->admin_dir = trailingslashit( $bp->plugin_dir  . 'bp-members/admin' ); // Admin path.
		$this->admin_url = trailingslashit( $bp->plugin_url  . 'bp-members/admin' ); // Admin URL.
		$this->css_url   = trailingslashit( $this->admin_url . 'css' ); // Admin CSS URL.
		$this->js_url    = trailingslashit( $this->admin_url . 'js'  ); // Admin CSS URL.

		// Capability depends on config.
		$this->capability = bp_core_do_network_admin() ? 'manage_network_users' : 'edit_users';

		// The Edit Profile Screen id.
		$this->user_page = '';

		// The Show Profile Screen id.
		$this->user_profile = is_network_admin() ? 'users' : 'profile';

		// The current user id.
		$this->current_user_id = get_current_user_id();

		// The user id being edited.
		$this->user_id = 0;

		// Is a member editing their own profile.
		$this->is_self_profile = false;

		// The screen ids to load specific css for.
		$this->screen_id = array();

		// The stats metabox default position.
		$this->stats_metabox = new StdClass();

		// BuddyPress edit user's profile args.
		$this->edit_profile_args = array( 'page' => 'bp-profile-edit' );
		$this->edit_profile_url  = '';
		$this->edit_url          = '';

		// Data specific to signups.
		$this->users_page   = '';
		$this->signups_page = '';
		$this->users_url    = bp_get_admin_url( 'users.php' );
		$this->users_screen = bp_core_do_network_admin() ? 'users-network' : 'users';

		// Specific config: BuddyPress is not network activated.
		$this->subsite_activated = (bool) is_multisite() && ! bp_is_network_activated();

		// When BuddyPress is not network activated, only Super Admin can moderate signups.
		if ( ! empty( $this->subsite_activated ) ) {
			$this->capability = 'manage_network_users';
		}
	}

	/**
	 * Set admin-related actions and filters.
	 *
	 * @since 2.0.0
	 */
	private function setup_actions() {

		/** Extended Profile *************************************************
		 */

		// Enqueue all admin JS and CSS.
		add_action( 'bp_admin_enqueue_scripts', array( $this, 'enqueue_scripts'   )        );

		// Add some page specific output to the <head>.
		add_action( 'bp_admin_head',            array( $this, 'admin_head'        ), 999   );

		// Add menu item to all users menu.
		add_action( 'admin_menu',               array( $this, 'admin_menus'       ), 5     );
		add_action( 'network_admin_menu',       array( $this, 'admin_menus'       ), 5     );
		add_action( 'user_admin_menu',          array( $this, 'user_profile_menu' ), 5     );

		// Create the Profile Navigation (Profile/Extended Profile).
		add_action( 'edit_user_profile',        array( $this, 'profile_nav'       ), 99, 1 );
		add_action( 'show_user_profile',        array( $this, 'profile_nav'       ), 99, 1 );

		// Editing users of a specific site.
		add_action( "admin_head-site-users.php", array( $this, 'profile_admin_head' ) );

		// Add a row action to users listing.
		if ( bp_core_do_network_admin() ) {
			add_filter( 'ms_user_row_actions',        array( $this, 'row_actions'                    ), 10, 2 );
			add_action( 'admin_init',                 array( $this, 'add_edit_profile_url_filter'    )        );
			add_action( 'wp_after_admin_bar_render',  array( $this, 'remove_edit_profile_url_filter' )        );
		}

		// Add user row actions for single site.
		add_filter( 'user_row_actions', array( $this, 'row_actions' ), 10, 2 );

		// Process changes to member type.
		add_action( 'bp_members_admin_load', array( $this, 'process_member_type_update' ) );

		/** Signups **********************************************************
		 */

		if ( is_admin() ) {

			// Filter non multisite user query to remove sign-up users.
			if ( ! is_multisite() ) {
				add_action( 'pre_user_query', array( $this, 'remove_signups_from_user_query' ), 10, 1 );
			}

			// Reorganise the views navigation in users.php and signups page.
			if ( current_user_can( $this->capability ) ) {
				add_filter( "views_{$this->users_screen}", array( $this, 'signup_filter_view'    ), 10, 1 );
				add_filter( 'set-screen-option',           array( $this, 'signup_screen_options' ), 10, 3 );
			}
		}
	}

	/**
	 * Get the user ID.
	 *
	 * Look for $_GET['user_id']. If anything else, force the user ID to the
	 * current user's ID so they aren't left without a user to edit.
	 *
	 * @since 2.1.0
	 *
	 * @return int
	 */
	private function get_user_id() {
		if ( ! empty( $this->user_id ) ) {
			return $this->user_id;
		}

		$this->user_id = (int) get_current_user_id();

		// We'll need a user ID when not on self profile.
		if ( ! empty( $_GET['user_id'] ) ) {
			$this->user_id = (int) $_GET['user_id'];
		}

		return $this->user_id;
	}

	/**
	 * Can the current user edit the one displayed.
	 *
	 * Self profile editing / or bp_moderate check.
	 * This might be replaced by more granular capabilities
	 * in the future.
	 *
	 * @since 2.1.0
	 *
	 * @param int $user_id ID of the user being checked for edit ability.
	 *
	 * @return bool
	 */
	private function member_can_edit( $user_id = 0 ) {
		$retval = false;

		// Bail if no user ID was passed.
		if ( empty( $user_id ) ) {
			return $retval;
		}

		// Member can edit if they are viewing their own profile.
		if ( $this->current_user_id === $user_id ) {
			$retval = true;

		// Trust the 'bp_moderate' capability.
		} else {
			$retval = bp_current_user_can( 'bp_moderate' );
		}

		return $retval;
	}

	/**
	 * Get admin notice when saving a user or member profile.
	 *
	 * @since 2.1.0
	 *
	 * @return array
	 */
	private function get_user_notice() {

		// Setup empty notice for return value.
		$notice = array();

		// Updates.
		if ( ! empty( $_REQUEST['updated'] ) ) {
			switch ( $_REQUEST['updated'] ) {
			case 'avatar':
				$notice = array(
					'class'   => 'updated',
					'message' => __( 'Profile photo was deleted.', 'buddypress' )
				);
				break;
			case 'ham' :
				$notice = array(
					'class'   => 'updated',
					'message' => __( 'User removed as spammer.', 'buddypress' )
				);
				break;
			case 'spam' :
				$notice = array(
					'class'   => 'updated',
					'message' => __( 'User marked as spammer. Spam users are visible only to site admins.', 'buddypress' )
				);
				break;
			case 1 :
				$notice = array(
					'class'   => 'updated',
					'message' => __( 'Profile updated.', 'buddypress' )
				);
				break;
			}
		}

		// Errors.
		if ( ! empty( $_REQUEST['error'] ) ) {
			switch ( $_REQUEST['error'] ) {
			case 'avatar':
				$notice = array(
					'class'   => 'error',
					'message' => __( 'There was a problem deleting that profile photo. Please try again.', 'buddypress' )
				);
				break;
			case 'ham' :
				$notice = array(
					'class'   => 'error',
					'message' => __( 'User could not be removed as spammer.', 'buddypress' )
				);
				break;
			case 'spam' :
				$notice = array(
					'class'   => 'error',
					'message' => __( 'User could not be marked as spammer.', 'buddypress' )
				);
				break;
			case 1 :
				$notice = array(
					'class'   => 'error',
					'message' => __( 'An error occurred while trying to update the profile.', 'buddypress' )
				);
				break;
			case 2:
				$notice = array(
					'class'   => 'error',
					'message' => __( 'Please make sure you fill in all required fields in this profile field group before saving.', 'buddypress' )
				);
				break;
			case 3:
				$notice = array(
					'class'   => 'error',
					'message' => __( 'There was a problem updating some of your profile information. Please try again.', 'buddypress' )
				);
				break;
			}
		}

		return $notice;
	}

	/**
	 * Create the /user/ admin Profile submenus for all members.
	 *
	 * @since 2.1.0
	 *
	 * @uses add_submenu_page() To add the Edit Profile page in Profile section.
	 */
	public function user_profile_menu() {

		// Setup the hooks array.
		$hooks = array();

		// Add the faux "Edit Profile" submenu page.
		$hooks['user'] = $this->user_page = add_submenu_page(
			'profile.php',
			__( 'Edit Profile',  'buddypress' ),
			__( 'Edit Profile',  'buddypress' ),
			'exist',
			'bp-profile-edit',
			array( $this, 'user_admin' )
		);

		// Setup the screen ID's.
		$this->screen_id = array(
			$this->user_page    . '-user',
			$this->user_profile . '-user'
		);

		// Loop through new hooks and add method actions.
		foreach ( $hooks as $key => $hook ) {
			add_action( "load-{$hook}", array( $this, $key . '_admin_load' ) );
		}

		// Add the profile_admin_head method to proper admin_head actions.
		add_action( "admin_head-{$this->user_page}", array( $this, 'profile_admin_head' ) );
		add_action( "admin_head-profile.php",        array( $this, 'profile_admin_head' ) );
	}

	/**
	 * Create the All Users / Profile > Edit Profile and All Users Signups submenus.
	 *
	 * @since 2.0.0
	 *
	 * @uses add_submenu_page() To add the Edit Profile page in Users/Profile section.
	 */
	public function admin_menus() {

		// Setup the hooks array.
		$hooks = array();

		// Manage user's profile.
		$hooks['user'] = $this->user_page = add_submenu_page(
			$this->user_profile . '.php',
			__( 'Edit Profile',  'buddypress' ),
			__( 'Edit Profile',  'buddypress' ),
			'read',
			'bp-profile-edit',
			array( $this, 'user_admin' )
		);

		// Only show sign-ups where they belong.
		if ( ! is_multisite() || is_network_admin() ) {

			// Manage signups.
			$hooks['signups'] = $this->signups_page = add_users_page(
				__( 'Manage Signups',  'buddypress' ),
				__( 'Manage Signups',  'buddypress' ),
				$this->capability,
				'bp-signups',
				array( $this, 'signups_admin' )
			);
		}

		$edit_page         = 'user-edit';
		$profile_page      = 'profile';
		$this->users_page  = 'users';

		// Self profile check is needed for this pages.
		$page_head = array(
			$edit_page        . '.php',
			$profile_page     . '.php',
			$this->user_page,
			$this->users_page . '.php',
		);

		// Append '-network' to each array item if in network admin.
		if ( is_network_admin() ) {
			$edit_page          .= '-network';
			$profile_page       .= '-network';
			$this->user_page    .= '-network';
			$this->users_page   .= '-network';
			$this->signups_page .= '-network';
		}

		// Setup the screen ID's.
		$this->screen_id = array(
			$edit_page,
			$this->user_page,
			$profile_page
		);

		// Loop through new hooks and add method actions.
		foreach ( $hooks as $key => $hook ) {
			add_action( "load-{$hook}", array( $this, $key . '_admin_load' ) );
		}

		// Add the profile_admin_head method to proper admin_head actions.
		foreach ( $page_head as $head ) {
			add_action( "admin_head-{$head}", array( $this, 'profile_admin_head' ) );
		}
	}

	/**
	 * Highlight the Users menu if on Edit Profile and check if on the user's admin profile.
	 *
	 * @since 2.1.0
	 */
	public function profile_admin_head() {
		global $submenu_file, $parent_file;

		// Is the user editing their own profile?
		if ( is_user_admin() || ( defined( 'IS_PROFILE_PAGE' ) && IS_PROFILE_PAGE ) ) {
			$this->is_self_profile = true;

		// Is the user attempting to edit their own profile.
		} elseif ( isset( $_GET['user_id' ] ) || ( isset( $_GET['page'] ) && ( 'bp-profile-edit' === $_GET['page'] ) ) ) {
			$this->is_self_profile = (bool) ( $this->get_user_id() === $this->current_user_id );
		}

		// Force the parent file to users.php to open the correct top level menu
		// but only if not editing a site via the network site editing page.
		if ( 'sites.php' !== $parent_file ) {
			$parent_file  = 'users.php';
			$submenu_file = 'users.php';
		}

		// Editing your own profile, so recheck some vars.
		if ( true === $this->is_self_profile ) {

			// Use profile.php as the edit page.
			$edit_page = 'profile.php';

			// Set profile.php as the parent & sub files to correct the menu nav.
			if ( is_blog_admin() || is_user_admin() ) {
				$parent_file  = 'profile.php';
				$submenu_file = 'profile.php';
			}

		// Not editing yourself, so use user-edit.php.
		} else {
			$edit_page = 'user-edit.php';
		}

		if ( is_user_admin() ) {
			$this->edit_profile_url = add_query_arg( $this->edit_profile_args, user_admin_url( 'profile.php' ) );
			$this->edit_url         = user_admin_url( 'profile.php' );

		} elseif ( is_blog_admin() ) {
			$this->edit_profile_url = add_query_arg( $this->edit_profile_args, admin_url( 'users.php' ) );
			$this->edit_url         = admin_url( $edit_page );

		} elseif ( is_network_admin() ) {
			$this->edit_profile_url = add_query_arg( $this->edit_profile_args, network_admin_url( 'users.php' ) );
			$this->edit_url         = network_admin_url( $edit_page );
		}
	}

	/**
	 * Remove the Edit Profile page.
	 *
	 * We add these pages in order to integrate with WP's Users panel, but
	 * we want them to show up as a row action of the WP panel, not as separate
	 * subnav items under the Users menu.
	 *
	 * @since 2.0.0
	 */
	public function admin_head() {
		remove_submenu_page( 'users.php',   'bp-profile-edit' );
		remove_submenu_page( 'profile.php', 'bp-profile-edit' );
	}

	/** Community Profile *****************************************************/

	/**
	 * Add some specific styling to the Edit User and Edit User's Profile page.
	 *
	 * @since 2.0.0
	 */
	public function enqueue_scripts() {
		if ( ! in_array( get_current_screen()->id, $this->screen_id ) ) {
			return;
		}

		$min = defined( 'SCRIPT_DEBUG' ) && SCRIPT_DEBUG ? '' : '.min';
		$css = $this->css_url . "admin{$min}.css";

		/**
		 * Filters the CSS URL to enqueue in the Members admin area.
		 *
		 * @since 2.0.0
		 *
		 * @param string $css URL to the CSS admin file to load.
		 */
		$css = apply_filters( 'bp_members_admin_css', $css );

		wp_enqueue_style( 'bp-members-css', $css, array(), bp_get_version() );

		wp_style_add_data( 'bp-members-css', 'rtl', true );
		if ( $min ) {
			wp_style_add_data( 'bp-members-css', 'suffix', $min );
		}

		// Only load JavaScript for BuddyPress profile.
		if ( get_current_screen()->id == $this->user_page ) {
			$js = $this->js_url . "admin{$min}.js";

			/**
			 * Filters the JS URL to enqueue in the Members admin area.
			 *
			 * @since 2.0.0
			 *
			 * @param string $js URL to the JavaScript admin file to load.
			 */
			$js = apply_filters( 'bp_members_admin_js', $js );
			wp_enqueue_script( 'bp-members-js', $js, array( 'jquery' ), bp_get_version(), true );
		}

		/**
		 * Fires after all of the members JavaScript and CSS are enqueued.
		 *
		 * @since 2.0.0
		 *
		 * @param string $id        ID of the current screen.
		 * @param array  $screen_id Array of allowed screens to add scripts and styles to.
		 */
		do_action( 'bp_members_admin_enqueue_scripts', get_current_screen()->id, $this->screen_id );
	}

	/**
	 * Create the Profile navigation in Edit User & Edit Profile pages.
	 *
	 * @since 2.0.0
	 *
	 * @param object|null $user   User to create profile navigation for.
	 * @param string      $active Which profile to highlight.
	 * @return string
	 */
	public function profile_nav( $user = null, $active = 'WordPress' ) {

		// Bail if no user ID exists here.
		if ( empty( $user->ID ) ) {
			return;
		}

		// Add the user ID to query arguments when not editing yourself.
		if ( false === $this->is_self_profile ) {
			$query_args = array( 'user_id' => $user->ID );
		} else {
			$query_args = array();
		}

		// Conditionally add a referer if it exists in the existing request.
		if ( ! empty( $_REQUEST['wp_http_referer'] ) ) {
			$query_args['wp_http_referer'] = urlencode( stripslashes_deep( $_REQUEST['wp_http_referer'] ) );
		}

		// Setup the two distinct "edit" URL's.
		$community_url = add_query_arg( $query_args, $this->edit_profile_url );
		$wordpress_url = add_query_arg( $query_args, $this->edit_url         );

		$bp_active = false;
		$wp_active = ' nav-tab-active';
		if ( 'BuddyPress' === $active ) {
			$bp_active = ' nav-tab-active';
			$wp_active = false;
		} ?>

		<h2 id="profile-nav" class="nav-tab-wrapper">
			<?php
			/**
			 * In configs where BuddyPress is not network activated, as regular
			 * admins do not have the capacity to edit other users, we must add
			 * this check.
			 */
			if ( current_user_can( 'edit_user', $user->ID ) ) : ?>

				<a class="nav-tab<?php echo esc_attr( $wp_active ); ?>" href="<?php echo esc_url( $wordpress_url );?>"><?php _e( 'Profile', 'buddypress' ); ?></a>

			<?php endif; ?>

			<a class="nav-tab<?php echo esc_attr( $bp_active ); ?>" href="<?php echo esc_url( $community_url );?>"><?php _e( 'Extended Profile', 'buddypress' ); ?></a>
		</h2>

		<?php
	}

	/**
	 * Set up the user's profile admin page.
	 *
	 * Loaded before the page is rendered, this function does all initial
	 * setup, including: processing form requests, registering contextual
	 * help, and setting up screen options.
	 *
	 * @since 2.0.0
	 */
	public function user_admin_load() {

		// Get the user ID.
		$user_id = $this->get_user_id();

		// Can current user edit this profile?
		if ( ! $this->member_can_edit( $user_id ) ) {
			wp_die( __( 'You cannot edit the requested user.', 'buddypress' ) );
		}

		// Build redirection URL.
		$redirect_to = remove_query_arg( array( 'action', 'error', 'updated', 'spam', 'ham', 'delete_avatar' ), $_SERVER['REQUEST_URI'] );
		$doaction    = ! empty( $_REQUEST['action'] ) ? $_REQUEST['action'] : false;

		if ( ! empty( $_REQUEST['user_status'] ) ) {
			$spam = (bool) ( 'spam' === $_REQUEST['user_status'] );

			if ( $spam !== bp_is_user_spammer( $user_id ) ) {
				$doaction = $_REQUEST['user_status'];
			}
		}

		/**
		 * Fires at the start of the signups admin load.
		 *
		 * @since 2.0.0
		 *
		 * @param string $doaction Current bulk action being processed.
		 * @param array  $_REQUEST Current $_REQUEST global.
		 */
		do_action_ref_array( 'bp_members_admin_load', array( $doaction, $_REQUEST ) );

		/**
		 * Filters the allowed actions for use in the user admin page.
		 *
		 * @since 2.0.0
		 *
		 * @param array $value Array of allowed actions to use.
		 */
		$allowed_actions = apply_filters( 'bp_members_admin_allowed_actions', array( 'update', 'delete_avatar', 'spam', 'ham' ) );

		// Prepare the display of the Community Profile screen.
		if ( ! in_array( $doaction, $allowed_actions ) ) {
			add_screen_option( 'layout_columns', array( 'default' => 2, 'max' => 2, ) );

			get_current_screen()->add_help_tab( array(
				'id'      => 'bp-profile-edit-overview',
				'title'   => __( 'Overview', 'buddypress' ),
				'content' =>
				'<p>' . __( 'This is the admin view of a user&#39;s profile.', 'buddypress' ) . '</p>' .
				'<p>' . __( 'In the main column, you can edit the fields of the user&#39;s extended profile.', 'buddypress' ) . '</p>' .
				'<p>' . __( 'In the right-hand column, you can update the user&#39;s status, delete the user&#39;s avatar, and view recent statistics.', 'buddypress' ) . '</p>'
			) );

			// Help panel - sidebar links.
			get_current_screen()->set_help_sidebar(
				'<p><strong>' . __( 'For more information:', 'buddypress' ) . '</strong></p>' .
				'<p>' . __( '<a href="https://codex.buddypress.org/administrator-guide/extended-profiles/">Managing Profiles</a>', 'buddypress' ) . '</p>' .
				'<p>' . __( '<a href="https://buddypress.org/support/">Support Forums</a>', 'buddypress' ) . '</p>'
			);

			// Register metaboxes for the edit screen.
			add_meta_box(
				'submitdiv',
				_x( 'Status', 'members user-admin edit screen', 'buddypress' ),
				array( $this, 'user_admin_status_metabox' ),
				get_current_screen()->id,
				'side',
				'core'
			);

			// In case xprofile is not active.
			$this->stats_metabox->context  = 'normal';
			$this->stats_metabox->priority = 'core';

			/**
			 * Fires before loading the profile fields if component is active.
			 *
			 * Plugins should not use this hook, please use 'bp_members_admin_user_metaboxes' instead.
			 *
			 * @since 2.0.0
			 *
			 * @param int    $user_id       Current user ID for the screen.
			 * @param string $id            Current screen ID.
			 * @param object $stats_metabox Object holding position data for use with the stats metabox.
			 */
			do_action_ref_array( 'bp_members_admin_xprofile_metabox', array( $user_id, get_current_screen()->id, $this->stats_metabox ) );

			// If xProfile is inactive, difficult to know what's profile we're on.
			if ( 'normal' === $this->stats_metabox->context ) {
				$display_name = bp_core_get_user_displayname( $user_id );
			} else {
				$display_name = __( 'Member', 'buddypress' );
			}

			// User Stat metabox.
			add_meta_box(
				'bp_members_admin_user_stats',
				sprintf( _x( "%s's Stats", 'members user-admin edit screen', 'buddypress' ), $display_name ),
				array( $this, 'user_admin_stats_metabox' ),
				get_current_screen()->id,
				sanitize_key( $this->stats_metabox->context ),
				sanitize_key( $this->stats_metabox->priority )
			);

			// Member Type metabox. Only added if member types have been registered.
			$member_types = bp_get_member_types();
			if ( ! empty( $member_types ) ) {
				add_meta_box(
					'bp_members_admin_member_type',
					_x( 'Member Type', 'members user-admin edit screen', 'buddypress' ),
					array( $this, 'user_admin_member_type_metabox' ),
					get_current_screen()->id,
					'side',
					'core'
				);
			}

			/**
			 * Fires at the end of the Community Profile screen.
			 *
			 * Plugins can restrict metabox to "bp_moderate" admins by checking if
			 * the first argument ($this->is_self_profile) is false in their callback.
			 * They can also restrict their metabox to self profile editing
			 * by setting it to true.
			 *
			 * @since 2.0.0
			 *
			 * @param bool $is_self_profile Whether or not it is the current user's profile.
			 * @param int  $user_id         Current user ID.
			 */
			do_action( 'bp_members_admin_user_metaboxes', $this->is_self_profile, $user_id );

			// Enqueue JavaScript files.
			wp_enqueue_script( 'postbox'   );
			wp_enqueue_script( 'dashboard' );

		// Spam or Ham user.
		} elseif ( in_array( $doaction, array( 'spam', 'ham' ) ) && empty( $this->is_self_profile ) ) {

			check_admin_referer( 'edit-bp-profile_' . $user_id );

			if ( bp_core_process_spammer_status( $user_id, $doaction ) ) {
				$redirect_to = add_query_arg( 'updated', $doaction, $redirect_to );
			} else {
				$redirect_to = add_query_arg( 'error', $doaction, $redirect_to );
			}

			bp_core_redirect( $redirect_to );

		// Update other stuff once above ones are done.
		} else {
			$this->redirect = $redirect_to;

			/**
			 * Fires at end of user profile admin load if doaction does not match any available actions.
			 *
			 * @since 2.0.0
			 *
			 * @param string $doaction Current bulk action being processed.
			 * @param int    $user_id  Current user ID.
			 * @param array  $_REQUEST Current $_REQUEST global.
			 * @param string $redirect Determined redirect url to send user to.
			 */
			do_action_ref_array( 'bp_members_admin_update_user', array( $doaction, $user_id, $_REQUEST, $this->redirect ) );

			bp_core_redirect( $this->redirect );
		}
	}

	/**
	 * Display the user's profile.
	 *
	 * @since 2.0.0
	 */
	public function user_admin() {

		if ( ! bp_current_user_can( 'bp_moderate' ) && empty( $this->is_self_profile ) ) {
			die( '-1' );
		}

		// Get the user ID.
		$user_id = $this->get_user_id();
		$user    = get_user_to_edit( $user_id );

		// Construct title.
		if ( true === $this->is_self_profile ) {
			$title = __( 'Profile',   'buddypress' );
		} else {
			$title = __( 'Edit User', 'buddypress' );
		}

		// Construct URL for form.
		$request_url     = remove_query_arg( array( 'action', 'error', 'updated', 'spam', 'ham' ), $_SERVER['REQUEST_URI'] );
		$form_action_url = add_query_arg( 'action', 'update', $request_url );
		$wp_http_referer = false;
		if ( ! empty( $_REQUEST['wp_http_referer'] ) ) {
			$wp_http_referer = remove_query_arg( array( 'action', 'updated' ), $_REQUEST['wp_http_referer'] );
		}

		// Prepare notice for admin.
		$notice = $this->get_user_notice();

		if ( ! empty( $notice ) ) : ?>

			<div <?php if ( 'updated' === $notice['class'] ) : ?>id="message" <?php endif; ?>class="<?php echo esc_attr( $notice['class'] ); ?>">

				<p><?php echo esc_html( $notice['message'] ); ?></p>

				<?php if ( !empty( $wp_http_referer ) && ( 'updated' === $notice['class'] ) ) : ?>

					<p><a href="<?php echo esc_url( $wp_http_referer ); ?>"><?php esc_html_e( '&larr; Back to Users', 'buddypress' ); ?></a></p>

				<?php endif; ?>

			</div>

		<?php endif; ?>

		<div class="wrap" id="community-profile-page">
			<h2><?php echo esc_html( $title ); ?>

				<?php if ( empty( $this->is_self_profile ) ) : ?>

					<?php if ( current_user_can( 'create_users' ) ) : ?>

						<a href="user-new.php" class="add-new-h2"><?php echo esc_html_x( 'Add New', 'user', 'buddypress' ); ?></a>

					<?php elseif ( is_multisite() && current_user_can( 'promote_users' ) ) : ?>

						<a href="user-new.php" class="add-new-h2"><?php echo esc_html_x( 'Add Existing', 'user', 'buddypress' ); ?></a>

					<?php endif; ?>

				<?php endif; ?>
			</h2>

			<?php if ( ! empty( $user ) ) :

				$this->profile_nav( $user, 'BuddyPress' ); ?>

				<form action="<?php echo esc_url( $form_action_url ); ?>" id="your-profile" method="post">
					<div id="poststuff">

						<div id="post-body" class="metabox-holder columns-<?php echo 1 == get_current_screen()->get_columns() ? '1' : '2'; ?>">

							<div id="postbox-container-1" class="postbox-container">
								<?php do_meta_boxes( get_current_screen()->id, 'side', $user ); ?>
							</div>

							<div id="postbox-container-2" class="postbox-container">
								<?php do_meta_boxes( get_current_screen()->id, 'normal',   $user ); ?>
								<?php do_meta_boxes( get_current_screen()->id, 'advanced', $user ); ?>
							</div>
						</div><!-- #post-body -->

					</div><!-- #poststuff -->

					<?php wp_nonce_field( 'closedpostboxes', 'closedpostboxesnonce', false ); ?>
					<?php wp_nonce_field( 'meta-box-order',  'meta-box-order-nonce', false ); ?>
					<?php wp_nonce_field( 'edit-bp-profile_' . $user->ID ); ?>

				</form>

			<?php else : ?>

				<p><?php printf( __( 'No user found with this ID. <a href="%s">Go back and try again</a>.', 'buddypress' ), esc_url( bp_get_admin_url( 'users.php' ) ) ); ?></p>

			<?php endif; ?>

		</div><!-- .wrap -->
		<?php
	}

	/**
	 * Render the Status metabox for user's profile screen.
	 *
	 * Actions are:
	 * - Update profile fields if xProfile component is active
	 * - Spam/Unspam user
	 *
	 * @since 2.0.0
	 *
	 * @param WP_User $user The WP_User object to be edited.
	 */
	public function user_admin_status_metabox( $user = null ) {

		// Bail if no user id or if the user has not activated their account yet.
		if ( empty( $user->ID ) ) {
			return;
		}

		// Bail if user has not been activated yet (how did you get here?).
		if ( isset( $user->user_status ) && ( 2 == $user->user_status ) ) : ?>

			<p class="not-activated"><?php esc_html_e( 'User account has not yet been activated', 'buddypress' ); ?></p><br/>

			<?php return;

		endif; ?>

		<div class="submitbox" id="submitcomment">
			<div id="minor-publishing">
				<div id="misc-publishing-actions">
					<?php

					// Get the spam status once here to compare against below.
					$is_spammer = bp_is_user_spammer( $user->ID );

					/**
					 * In configs where BuddyPress is not network activated,
					 * regular admins cannot mark a user as a spammer on front
					 * end. This prevent them to do it in backend.
					 *
					 * Also prevent admins from marking themselves or other
					 * admins as spammers.
					 */
					if ( ( empty( $this->is_self_profile ) && ( ! in_array( $user->user_login, get_super_admins() ) ) && empty( $this->subsite_activated ) ) || ( ! empty( $this->subsite_activated ) && current_user_can( 'manage_network_users' ) ) ) : ?>

						<div class="misc-pub-section" id="comment-status-radio">
							<label class="approved"><input type="radio" name="user_status" value="ham" <?php checked( $is_spammer, false ); ?>><?php esc_html_e( 'Active', 'buddypress' ); ?></label><br />
							<label class="spam"><input type="radio" name="user_status" value="spam" <?php checked( $is_spammer, true ); ?>><?php esc_html_e( 'Spammer', 'buddypress' ); ?></label>
						</div>

					<?php endif ;?>

					<div class="misc-pub-section curtime misc-pub-section-last">
						<?php

						// Translators: Publish box date format, see http://php.net/date.
						$datef = __( 'M j, Y @ G:i', 'buddypress' );
						$date  = date_i18n( $datef, strtotime( $user->user_registered ) );
						?>
						<span id="timestamp"><?php printf( __( 'Registered on: %s', 'buddypress' ), '<strong>' . $date . '</strong>' ); ?></span>
					</div>
				</div> <!-- #misc-publishing-actions -->

				<div class="clear"></div>
			</div><!-- #minor-publishing -->

			<div id="major-publishing-actions">

				<div id="publishing-action">
					<a class="button bp-view-profile" href="<?php echo esc_url( bp_core_get_user_domain( $user->ID ) ); ?>" target="_blank"><?php esc_html_e( 'View Profile', 'buddypress' ); ?></a>
					<?php submit_button( esc_html__( 'Update Profile', 'buddypress' ), 'primary', 'save', false ); ?>
				</div>
				<div class="clear"></div>
			</div><!-- #major-publishing-actions -->

		</div><!-- #submitcomment -->

		<?php
	}

	/**
	 * Render the fallback metabox in case a user has been marked as a spammer.
	 *
	 * @since 2.0.0
	 *
	 * @param WP_User $user The WP_User object to be edited.
	 */
	public function user_admin_spammer_metabox( $user = null ) {
	?>
		<p><?php printf( __( '%s has been marked as a spammer. All BuddyPress data associated with the user has been removed', 'buddypress' ), esc_html( bp_core_get_user_displayname( $user->ID ) ) ) ;?></p>
	<?php
	}

	/**
	 * Render the Stats metabox to moderate inappropriate images.
	 *
	 * @since 2.0.0
	 *
	 * @param WP_User $user The WP_User object to be edited.
	 */
	public function user_admin_stats_metabox( $user = null ) {

		// Bail if no user ID.
		if ( empty( $user->ID ) ) {
			return;
		}

		// If account is not activated last activity is the time user registered.
		if ( isset( $user->user_status ) && 2 == $user->user_status ) {
			$last_active = $user->user_registered;

		// Account is activated, getting user's last activity.
		} else {
			$last_active = bp_get_user_last_activity( $user->ID );
		}

		$datef = __( 'M j, Y @ G:i', 'buddypress' );
		$date  = date_i18n( $datef, strtotime( $last_active ) ); ?>

		<ul>
			<li class="bp-members-profile-stats"><?php printf( __( 'Last active: %1$s', 'buddypress' ), '<strong>' . $date . '</strong>' ); ?></li>

			<?php
			// Loading other stats only if user has activated their account.
			if ( empty( $user->user_status ) ) {

				/**
				 * Fires in the user stats metabox if the user has activated their account.
				 *
				 * @since 2.0.0
				 *
				 * @param array  $value Array holding the user ID.
				 * @param object $user  Current displayed user object.
				 */
				do_action( 'bp_members_admin_user_stats', array( 'user_id' => $user->ID ), $user );
			}
			?>
		</ul>

		<?php
	}

	/**
	 * Render the Member Type metabox.
	 *
	 * @since 2.2.0
	 *
	 * @param WP_User $user The WP_User object to be edited.
	 */
	public function user_admin_member_type_metabox( $user = null ) {

		// Bail if no user ID.
		if ( empty( $user->ID ) ) {
			return;
		}

		$types = bp_get_member_types( array(), 'objects' );
		$current_type = bp_get_member_type( $user->ID );
		?>

		<label for="bp-members-profile-member-type" class="screen-reader-text"><?php esc_html_e( 'Select member type', 'buddypress' ); ?></label>
		<select name="bp-members-profile-member-type" id="bp-members-profile-member-type">
			<option value="" <?php selected( '', $current_type ); ?>><?php /* translators: no option picked in select box */ esc_attr_e( '----', 'buddypress' ) ?></option>
			<?php foreach ( $types as $type ) : ?>
				<option value="<?php echo esc_attr( $type->name ) ?>" <?php selected( $type->name, $current_type ) ?>><?php echo esc_html( $type->labels['singular_name'] ) ?></option>
			<?php endforeach; ?>
		</select>

		<?php

		wp_nonce_field( 'bp-member-type-change-' . $user->ID, 'bp-member-type-nonce' );
	}

	/**
	 * Process changes from the Member Type metabox.
	 *
	 * @since 2.2.0
	 */
	public function process_member_type_update() {
		if ( ! isset( $_POST['bp-member-type-nonce'] ) || ! isset( $_POST['bp-members-profile-member-type'] ) ) {
			return;
		}

		$user_id = $this->get_user_id();

		check_admin_referer( 'bp-member-type-change-' . $user_id, 'bp-member-type-nonce' );

		// Permission check.
		if ( ! current_user_can( 'bp_moderate' ) && $user_id != bp_loggedin_user_id() ) {
			return;
		}

		// Member type string must either reference a valid member type, or be empty.
		$member_type = stripslashes( $_POST['bp-members-profile-member-type'] );
		if ( ! empty( $member_type ) && ! bp_get_member_type_object( $member_type ) ) {
			return;
		}

		/*
		 * If an invalid member type is passed, someone's doing something
		 * fishy with the POST request, so we can fail silently.
		 */
		if ( bp_set_member_type( $user_id, $member_type ) ) {
			// @todo Success messages can't be posted because other stuff happens on the page load.
		}
	}

	/**
	 * Add a link to Profile in Users listing row actions.
	 *
	 * @since 2.0.0
	 *
	 * @param array|string $actions WordPress row actions (edit, delete).
	 * @param object       $user    The object for the user row.
	 * @return array Merged actions.
	 */
	public function row_actions( $actions = '', $user = null ) {

		// Bail if no user ID.
		if ( empty( $user->ID ) ) {
			return;
		}

		// Setup args array.
		$args = array();

		// Add the user ID if it's not for the current user.
		if ( $user->ID !== $this->current_user_id ) {
			$args['user_id'] = $user->ID;
		}

		// Add the referer.
		$args['wp_http_referer'] = urlencode( wp_unslash( $_SERVER['REQUEST_URI'] ) );

		// Add the "Extended" link if the current user can edit this user.
		if ( current_user_can( 'edit_user', $user->ID ) || bp_current_user_can( 'bp_moderate' ) ) {

			// Add query args and setup the Extended link.
			$edit_profile      = add_query_arg( $args, $this->edit_profile_url );
			$edit_profile_link = sprintf( '<a href="%1$s">%2$s</a>',  esc_url( $edit_profile ), esc_html__( 'Extended', 'buddypress' ) );

			/**
			 * Check the edit action is available
			 * and preserve the order edit | profile | remove/delete.
			 */
			if ( ! empty( $actions['edit'] ) ) {
				$edit_action = $actions['edit'];
				unset( $actions['edit'] );

				$new_edit_actions = array(
					'edit'         => $edit_action,
					'edit-profile' => $edit_profile_link,
				);

			// If not available simply add the edit profile action.
			} else {
				$new_edit_actions = array( 'edit-profile' => $edit_profile_link );
			}

			$actions = array_merge( $new_edit_actions, $actions );
		}

		return $actions;
	}

	/**
	 * Add a filter to edit profile url in WP Admin Bar.
	 *
	 * @since 2.1.0
	 */
	public function add_edit_profile_url_filter() {
		add_filter( 'bp_members_edit_profile_url', array( $this, 'filter_adminbar_profile_link' ), 10, 3 );
	}

	/**
	 * Filter the profile url.
	 *
	 * @since 2.1.0
	 *
	 * @uses  user_admin_url()
	 *
	 * @param string $profile_link Profile Link for admin bar.
	 * @param string $url          Profile URL.
	 * @param int    $user_id      User ID.
	 * @return string
	 */
	public function filter_adminbar_profile_link( $profile_link = '', $url = '', $user_id = 0 ) {
		if ( ! is_super_admin( $user_id ) && is_admin() ) {
			$profile_link = user_admin_url( 'profile.php' );
		}
		return $profile_link;
	}

	/**
	 * Remove the filter to edit profile url in WP Admin Bar.
	 *
	 * @since 2.1.0
	 */
	public function remove_edit_profile_url_filter() {
		remove_filter( 'bp_members_edit_profile_url', array( $this, 'filter_adminbar_profile_link' ), 10, 3 );
	}

	/** Signups Management ****************************************************/

	/**
	 * Display the admin preferences about signups pagination.
	 *
	 * @since 2.0.0
	 *
	 * @param int    $value     Value for signup option.
	 * @param string $option    Value for the option key.
	 * @param int    $new_value Value for the saved option.
	 * @return int The pagination preferences.
	 */
	public function signup_screen_options( $value = 0, $option = '', $new_value = 0 ) {
		if ( 'users_page_bp_signups_network_per_page' != $option && 'users_page_bp_signups_per_page' != $option ) {
			return $value;
		}

		// Per page.
		$new_value = (int) $new_value;
		if ( $new_value < 1 || $new_value > 999 ) {
			return $value;
		}

		return $new_value;
	}

	/**
	 * Make sure no signups will show in users list.
	 *
	 * This is needed to handle signups that may have not been activated
	 * before the 2.0.0 upgrade.
	 *
	 * @since 2.0.0
	 *
	 * @param WP_User_Query $query The users query.
	 * @return WP_User_Query The users query without the signups.
	 */
	public function remove_signups_from_user_query( $query = null ) {
		global $wpdb;

		// Bail if this is an ajax request.
		if ( defined( 'DOING_AJAX' ) ) {
			return;
		}

		// Bail if updating BuddyPress.
		if ( bp_is_update() ) {
			return;
		}

		// Bail if there is no current admin screen.
		if ( ! function_exists( 'get_current_screen' ) || ! get_current_screen() ) {
			return;
		}

		// Get current screen.
		$current_screen = get_current_screen();

		// Bail if not on a users page.
		if ( ! isset( $current_screen->id ) || $this->users_page !== $current_screen->id ) {
			return;
		}

		// Bail if already querying by an existing role.
		if ( ! empty( $query->query_vars['role'] ) ) {
			return;
		}

		$query->query_where .= " AND {$wpdb->users}.user_status != 2";
	}

	/**
	 * Filter the WP Users List Table views to include 'bp-signups'.
	 *
	 * @since 2.0.0
	 *
	 * @param array $views WP List Table views.
	 * @return array The views with the signup view added.
	 */
	public function signup_filter_view( $views = array() ) {

		// Remove the 'current' class from All if we're on the signups view.
		if ( $this->signups_page == get_current_screen()->id ) {
			$views['all'] = str_replace( 'class="current"', '', $views['all'] );
			$class        = 'current';
		} else {
			$class        = '';
		}

		$signups = BP_Signup::count_signups();
		$url     = add_query_arg( 'page', 'bp-signups', bp_get_admin_url( 'users.php' ) );
		$text    = sprintf( _x( 'Pending %s', 'signup users', 'buddypress' ), '<span class="count">(' . number_format_i18n( $signups ) . ')</span>' );

		$views['registered'] = sprintf( '<a href="%1$s" class="%2$s">%3$s</a>', esc_url( $url ), $class, $text );

		return $views;
	}

	/**
	 * Load the Signup WP Users List table.
	 *
	 * @since 2.0.0
	 *
	 * @param string $class    The name of the class to use.
	 * @param string $required The parent class.
	 * @return WP_List_Table The List table.
	 */
	public static function get_list_table_class( $class = '', $required = '' ) {
		if ( empty( $class ) ) {
			return;
		}

		if ( ! empty( $required ) ) {
			require_once( ABSPATH . 'wp-admin/includes/class-wp-' . $required . '-list-table.php' );
			require_once( buddypress()->members->admin->admin_dir . 'bp-members-admin-classes.php' );
		}

		return new $class();
	}

	/**
	 * Set up the signups admin page.
	 *
	 * Loaded before the page is rendered, this function does all initial
	 * setup, including: processing form requests, registering contextual
	 * help, and setting up screen options.
	 *
	 * @since 2.0.0
	 *
	 * @global $bp_members_signup_list_table
	 */
	public function signups_admin_load() {
		global $bp_members_signup_list_table;

		// Build redirection URL.
		$redirect_to = remove_query_arg( array( 'action', 'error', 'updated', 'activated', 'notactivated', 'deleted', 'notdeleted', 'resent', 'notresent', 'do_delete', 'do_resend', 'do_activate', '_wpnonce', 'signup_ids' ), $_SERVER['REQUEST_URI'] );
		$doaction    = bp_admin_list_table_current_bulk_action();

		/**
		 * Fires at the start of the signups admin load.
		 *
		 * @since 2.0.0
		 *
		 * @param string $doaction Current bulk action being processed.
		 * @param array  $_REQUEST Current $_REQUEST global.
		 */
		do_action( 'bp_signups_admin_load', $doaction, $_REQUEST );

		/**
		 * Filters the allowed actions for use in the user signups admin page.
		 *
		 * @since 2.0.0
		 *
		 * @param array $value Array of allowed actions to use.
		 */
		$allowed_actions = apply_filters( 'bp_signups_admin_allowed_actions', array( 'do_delete', 'do_activate', 'do_resend' ) );

		// Prepare the display of the Community Profile screen.
		if ( ! in_array( $doaction, $allowed_actions ) || ( -1 == $doaction ) ) {

			if ( bp_core_do_network_admin() ) {
				$bp_members_signup_list_table = self::get_list_table_class( 'BP_Members_MS_List_Table', 'ms-users' );
			} else {
				$bp_members_signup_list_table = self::get_list_table_class( 'BP_Members_List_Table', 'users' );
			}

			// The per_page screen option.
			add_screen_option( 'per_page', array( 'label' => _x( 'Pending Accounts', 'Pending Accounts per page (screen options)', 'buddypress' ) ) );

			get_current_screen()->add_help_tab( array(
				'id'      => 'bp-signups-overview',
				'title'   => __( 'Overview', 'buddypress' ),
				'content' =>
				'<p>' . __( 'This is the administration screen for pending accounts on your site.', 'buddypress' ) . '</p>' .
				'<p>' . __( 'From the screen options, you can customize the displayed columns and the pagination of this screen.', 'buddypress' ) . '</p>' .
				'<p>' . __( 'You can reorder the list of your pending accounts by clicking on the Username, Email or Registered column headers.', 'buddypress' ) . '</p>' .
				'<p>' . __( 'Using the search form, you can find pending accounts more easily. The Username and Email fields will be included in the search.', 'buddypress' ) . '</p>'
			) );

			get_current_screen()->add_help_tab( array(
				'id'      => 'bp-signups-actions',
				'title'   => __( 'Actions', 'buddypress' ),
				'content' =>
				'<p>' . __( 'Hovering over a row in the pending accounts list will display action links that allow you to manage pending accounts. You can perform the following actions:', 'buddypress' ) . '</p>' .
				'<ul><li>' . __( '"Email" takes you to the confirmation screen before being able to send the activation link to the desired pending account. You can only send the activation email once per day.', 'buddypress' ) . '</li>' .
				'<li>' . __( '"Delete" allows you to delete a pending account from your site. You will be asked to confirm this deletion.', 'buddypress' ) . '</li></ul>' .
				'<p>' . __( 'By clicking on a Username you will be able to activate a pending account from the confirmation screen.', 'buddypress' ) . '</p>' .
				'<p>' . __( 'Bulk actions allow you to perform these 3 actions for the selected rows.', 'buddypress' ) . '</p>'
			) );

			// Help panel - sidebar links.
			get_current_screen()->set_help_sidebar(
				'<p><strong>' . __( 'For more information:', 'buddypress' ) . '</strong></p>' .
				'<p>' . __( '<a href="https://buddypress.org/support/">Support Forums</a>', 'buddypress' ) . '</p>'
			);
		} else {
			if ( ! empty( $_REQUEST['signup_ids' ] ) ) {
				$signups = wp_parse_id_list( $_REQUEST['signup_ids' ] );
			}

			// Handle resent activation links.
			if ( 'do_resend' == $doaction ) {

				// Nonce check.
				check_admin_referer( 'signups_resend' );

				$resent = BP_Signup::resend( $signups );

				if ( empty( $resent ) ) {
					$redirect_to = add_query_arg( 'error', $doaction, $redirect_to );
				} else {
					$query_arg = array( 'updated' => 'resent' );

					if ( ! empty( $resent['resent'] ) ) {
						$query_arg['resent'] = count( $resent['resent'] );
					}

					if ( ! empty( $resent['errors'] ) ) {
						$query_arg['notsent'] = count( $resent['errors'] );
						set_transient( '_bp_admin_signups_errors', $resent['errors'], 30 );
					}

					$redirect_to = add_query_arg( $query_arg, $redirect_to );
				}

				bp_core_redirect( $redirect_to );

			// Handle activated accounts.
			} elseif ( 'do_activate' == $doaction ) {

				// Nonce check.
				check_admin_referer( 'signups_activate' );

				$activated = BP_Signup::activate( $signups );

				if ( empty( $activated ) ) {
					$redirect_to = add_query_arg( 'error', $doaction, $redirect_to );
				} else {
					$query_arg = array( 'updated' => 'activated' );

					if ( ! empty( $activated['activated'] ) ) {
						$query_arg['activated'] = count( $activated['activated'] );
					}

					if ( ! empty( $activated['errors'] ) ) {
						$query_arg['notactivated'] = count( $activated['errors'] );
						set_transient( '_bp_admin_signups_errors', $activated['errors'], 30 );
					}

					$redirect_to = add_query_arg( $query_arg, $redirect_to );
				}

				bp_core_redirect( $redirect_to );

			// Handle sign-ups delete.
			} elseif ( 'do_delete' == $doaction ) {

				// Nonce check.
				check_admin_referer( 'signups_delete' );

				$deleted = BP_Signup::delete( $signups );

				if ( empty( $deleted ) ) {
					$redirect_to = add_query_arg( 'error', $doaction, $redirect_to );
				} else {
					$query_arg = array( 'updated' => 'deleted' );

					if ( ! empty( $deleted['deleted'] ) ) {
						$query_arg['deleted'] = count( $deleted['deleted'] );
					}

					if ( ! empty( $deleted['errors'] ) ) {
						$query_arg['notdeleted'] = count( $deleted['errors'] );
						set_transient( '_bp_admin_signups_errors', $deleted['errors'], 30 );
					}

					$redirect_to = add_query_arg( $query_arg, $redirect_to );
				}

				bp_core_redirect( $redirect_to );

			// Plugins can update other stuff from here.
			} else {
				$this->redirect = $redirect_to;

				/**
				 * Fires at end of signups admin load if doaction does not match any actions.
				 *
				 * @since 2.0.0
				 *
				 * @param string $doaction Current bulk action being processed.
				 * @param array  $_REQUEST Current $_REQUEST global.
				 * @param string $redirect Determined redirect url to send user to.
				 */
				do_action( 'bp_members_admin_update_signups', $doaction, $_REQUEST, $this->redirect );

				bp_core_redirect( $this->redirect );
			}
		}
	}

	/**
	 * Display any activation errors.
	 *
	 * @since 2.0.0
	 */
	public function signups_display_errors() {

		// Look for sign-up errors.
		$errors = get_transient( '_bp_admin_signups_errors' );

		// Bail if no activation errors.
		if ( empty( $errors ) ) {
			return;
		}

		// Loop through errors and display them.
		foreach ( $errors as $error ) : ?>

			<li><?php echo esc_html( $error[0] );?>: <?php echo esc_html( $error[1] );?></li>

		<?php endforeach;

		// Delete the redirect transient.
		delete_transient( '_bp_admin_signups_errors' );
	}

	/**
	 * Get admin notice when viewing the sign-up page.
	 *
	 * @since 2.1.0
	 *
	 * @return array
	 */
	private function get_signup_notice() {

		// Setup empty notice for return value.
		$notice = array();

		// Updates.
		if ( ! empty( $_REQUEST['updated'] ) ) {
			switch ( $_REQUEST['updated'] ) {
				case 'resent':
					$notice = array(
						'class'   => 'updated',
						'message' => ''
					);

					if ( ! empty( $_REQUEST['resent'] ) ) {
						$notice['message'] .= sprintf(
							_nx( '%s activation email successfully sent! ', '%s activation emails successfully sent! ',
							 absint( $_REQUEST['resent'] ),
							 'signup resent',
							 'buddypress'
							),
							number_format_i18n( absint( $_REQUEST['resent'] ) )
						);
					}

					if ( ! empty( $_REQUEST['notsent'] ) ) {
						$notice['message'] .= sprintf(
							_nx( '%s activation email was not sent.', '%s activation emails were not sent.',
							 absint( $_REQUEST['notsent'] ),
							 'signup notsent',
							 'buddypress'
							),
							number_format_i18n( absint( $_REQUEST['notsent'] ) )
						);

						if ( empty( $_REQUEST['resent'] ) ) {
							$notice['class'] = 'error';
						}
					}

					break;

				case 'activated':
					$notice = array(
						'class'   => 'updated',
						'message' => ''
					);

					if ( ! empty( $_REQUEST['activated'] ) ) {
						$notice['message'] .= sprintf(
							_nx( '%s account successfully activated! ', '%s accounts successfully activated! ',
							 absint( $_REQUEST['activated'] ),
							 'signup resent',
							 'buddypress'
							),
							number_format_i18n( absint( $_REQUEST['activated'] ) )
						);
					}

					if ( ! empty( $_REQUEST['notactivated'] ) ) {
						$notice['message'] .= sprintf(
							_nx( '%s account was not activated.', '%s accounts were not activated.',
							 absint( $_REQUEST['notactivated'] ),
							 'signup notsent',
							 'buddypress'
							),
							number_format_i18n( absint( $_REQUEST['notactivated'] ) )
						);

						if ( empty( $_REQUEST['activated'] ) ) {
							$notice['class'] = 'error';
						}
					}

					break;

				case 'deleted':
					$notice = array(
						'class'   => 'updated',
						'message' => ''
					);

					if ( ! empty( $_REQUEST['deleted'] ) ) {
						$notice['message'] .= sprintf(
							_nx( '%s sign-up successfully deleted!', '%s sign-ups successfully deleted!',
							 absint( $_REQUEST['deleted'] ),
							 'signup deleted',
							 'buddypress'
							),
							number_format_i18n( absint( $_REQUEST['deleted'] ) )
						);
					}

					if ( ! empty( $_REQUEST['notdeleted'] ) ) {
						$notice['message'] .= sprintf(
							_nx( '%s sign-up was not deleted.', '%s sign-ups were not deleted.',
							 absint( $_REQUEST['notdeleted'] ),
							 'signup notdeleted',
							 'buddypress'
							),
							number_format_i18n( absint( $_REQUEST['notdeleted'] ) )
						);

						if ( empty( $_REQUEST['deleted'] ) ) {
							$notice['class'] = 'error';
						}
					}

					break;
			}
		}

		// Errors.
		if ( ! empty( $_REQUEST['error'] ) ) {
			switch ( $_REQUEST['error'] ) {
				case 'do_resend':
					$notice = array(
						'class'   => 'error',
						'message' => esc_html__( 'There was a problem sending the activation emails. Please try again.', 'buddypress' ),
					);
					break;

				case 'do_activate':
					$notice = array(
						'class'   => 'error',
						'message' => esc_html__( 'There was a problem activating accounts. Please try again.', 'buddypress' ),
					);
					break;

				case 'do_delete':
					$notice = array(
						'class'   => 'error',
						'message' => esc_html__( 'There was a problem deleting sign-ups. Please try again.', 'buddypress' ),
					);
					break;
			}
		}

		return $notice;
	}

	/**
	 * Signups admin page router.
	 *
	 * Depending on the context, display
	 * - the list of signups,
	 * - or the delete confirmation screen,
	 * - or the activate confirmation screen,
	 * - or the "resend" email confirmation screen.
	 *
	 * Also prepare the admin notices.
	 *
	 * @since 2.0.0
	 */
	public function signups_admin() {
		$doaction = bp_admin_list_table_current_bulk_action();

		// Prepare notices for admin.
		$notice = $this->get_signup_notice();

		// Display notices.
		if ( ! empty( $notice ) ) :
			if ( 'updated' === $notice['class'] ) : ?>

				<div id="message" class="<?php echo esc_attr( $notice['class'] ); ?>">

			<?php else: ?>

				<div class="<?php echo esc_attr( $notice['class'] ); ?>">

			<?php endif; ?>

				<p><?php echo $notice['message']; ?></p>

				<?php if ( ! empty( $_REQUEST['notactivated'] ) || ! empty( $_REQUEST['notdeleted'] ) || ! empty( $_REQUEST['notsent'] ) ) :?>

					<ul><?php $this->signups_display_errors();?></ul>

				<?php endif ;?>

			</div>

		<?php endif;

		// Show the proper screen.
		switch ( $doaction ) {
			case 'activate' :
			case 'delete' :
			case 'resend' :
				$this->signups_admin_manage( $doaction );
				break;

			default:
				$this->signups_admin_index();
				break;

		}
	}

	/**
	 * This is the list of the Pending accounts (signups).
	 *
	 * @since 2.0.0
	 *
	 * @global $plugin_page
	 * @global $bp_members_signup_list_table
	 */
	public function signups_admin_index() {
		global $plugin_page, $bp_members_signup_list_table;

		$usersearch = ! empty( $_REQUEST['s'] ) ? stripslashes( $_REQUEST['s'] ) : '';

		// Prepare the group items for display.
		$bp_members_signup_list_table->prepare_items();

		$form_url = add_query_arg(
			array(
				'page' => 'bp-signups',
			),
			bp_get_admin_url( 'users.php' )
		);

		$search_form_url = remove_query_arg(
			array(
				'action',
				'deleted',
				'notdeleted',
				'error',
				'updated',
				'delete',
				'activate',
				'activated',
				'notactivated',
				'resend',
				'resent',
				'notresent',
				'do_delete',
				'do_activate',
				'do_resend',
				'action2',
				'_wpnonce',
				'signup_ids'
			), $_SERVER['REQUEST_URI']
		);

		?>

		<div class="wrap">
			<h2><?php _e( 'Users', 'buddypress' ); ?>

				<?php if ( current_user_can( 'create_users' ) ) : ?>

					<a href="user-new.php" class="add-new-h2"><?php echo esc_html_x( 'Add New', 'user', 'buddypress' ); ?></a>

				<?php elseif ( is_multisite() && current_user_can( 'promote_users' ) ) : ?>

					<a href="user-new.php" class="add-new-h2"><?php echo esc_html_x( 'Add Existing', 'user', 'buddypress' ); ?></a>

				<?php endif;

				if ( $usersearch ) {
					printf( '<span class="subtitle">' . __( 'Search results for &#8220;%s&#8221;', 'buddypress' ) . '</span>', esc_html( $usersearch ) );
				}

				?>
			</h2>

			<?php // Display each signups on its own row. ?>
			<?php $bp_members_signup_list_table->views(); ?>

			<form id="bp-signups-search-form" action="<?php echo esc_url( $search_form_url ) ;?>">
				<input type="hidden" name="page" value="<?php echo esc_attr( $plugin_page ); ?>" />
				<?php $bp_members_signup_list_table->search_box( __( 'Search Pending Users', 'buddypress' ), 'bp-signups' ); ?>
			</form>

			<form id="bp-signups-form" action="<?php echo esc_url( $form_url );?>" method="post">
				<?php $bp_members_signup_list_table->display(); ?>
			</form>
		</div>
	<?php
	}

	/**
	 * This is the confirmation screen for actions.
	 *
	 * @since 2.0.0
	 *
	 * @param string $action Delete, activate, or resend activation link.
	 * @return string
	 */
	public function signups_admin_manage( $action = '' ) {
		if ( ! current_user_can( $this->capability ) || empty( $action ) ) {
			die( '-1' );
		}

		// Get the user IDs from the URL.
		$ids = false;
		if ( ! empty( $_POST['allsignups'] ) ) {
			$ids = wp_parse_id_list( $_POST['allsignups'] );
		} elseif ( ! empty( $_GET['signup_id'] ) ) {
			$ids = absint( $_GET['signup_id'] );
		}

		if ( empty( $ids ) ) {
			return false;
		}

		// Query for signups, and filter out those IDs that don't
		// correspond to an actual signup.
		$signups_query = BP_Signup::get( array(
			'include' => $ids,
		) );

		$signups    = $signups_query['signups'];
		$signup_ids = wp_list_pluck( $signups, 'signup_id' );

		// Set up strings.
		switch ( $action ) {
			case 'delete' :
				$header_text = __( 'Delete Pending Accounts', 'buddypress' );
				if ( 1 == count( $signup_ids ) ) {
					$helper_text = __( 'You are about to delete the following account:', 'buddypress' );
				} else {
					$helper_text = __( 'You are about to delete the following accounts:', 'buddypress' );
				}
				break;

			case 'activate' :
				$header_text = __( 'Activate Pending Accounts', 'buddypress' );
				if ( 1 == count( $signup_ids ) ) {
					$helper_text = __( 'You are about to activate the following account:', 'buddypress' );
				} else {
					$helper_text = __( 'You are about to activate the following accounts:', 'buddypress' );
				}
				break;

			case 'resend' :
				$header_text = __( 'Resend Activation Emails', 'buddypress' );
				if ( 1 == count( $signup_ids ) ) {
					$helper_text = __( 'You are about to resend an activation email to the following account:', 'buddypress' );
				} else {
					$helper_text = __( 'You are about to resend an activation email to the following accounts:', 'buddypress' );
				}
				break;
		}

		// These arguments are added to all URLs.
		$url_args = array( 'page' => 'bp-signups' );

		// These arguments are only added when performing an action.
		$action_args = array(
			'action'     => 'do_' . $action,
			'signup_ids' => implode( ',', $signup_ids )
		);

		$cancel_url = add_query_arg( $url_args, bp_get_admin_url( 'users.php' ) );
		$action_url = wp_nonce_url(
			add_query_arg(
				array_merge( $url_args, $action_args ),
				bp_get_admin_url( 'users.php' )
			),
			'signups_' . $action
		);

		?>

		<div class="wrap">
			<h2><?php echo esc_html( $header_text ); ?></h2>
			<p><?php echo esc_html( $helper_text ); ?></p>

			<ol class="bp-signups-list">
			<?php foreach ( $signups as $signup ) :

				$last_notified = mysql2date( 'Y/m/d g:i:s a', $signup->date_sent ); ?>

				<li>
					<?php echo esc_html( $signup->user_name ) ?> - <?php echo sanitize_email( $signup->user_email );?>

					<?php if ( 'resend' == $action ) : ?>

						<p class="description">
							<?php printf( esc_html__( 'Last notified: %s', 'buddypress'), $last_notified ) ;?>

							<?php if ( ! empty( $signup->recently_sent ) ) : ?>

								<span class="attention wp-ui-text-notification"> <?php esc_html_e( '(less than 24 hours ago)', 'buddypress' ); ?></span>

							<?php endif; ?>
						</p>

					<?php endif; ?>

				</li>

			<?php endforeach; ?>
			</ol>

			<?php if ( 'delete' === $action ) : ?>

				<p><strong><?php esc_html_e( 'This action cannot be undone.', 'buddypress' ) ?></strong></p>

			<?php endif ; ?>

			<a class="button-primary" href="<?php echo esc_url( $action_url ); ?>"><?php esc_html_e( 'Confirm', 'buddypress' ); ?></a>
			<a class="button" href="<?php echo esc_url( $cancel_url ); ?>"><?php esc_html_e( 'Cancel', 'buddypress' ) ?></a>
		</div>

		<?php
	}
}
endif; // End class_exists check.

// Load the BP Members admin.
add_action( 'bp_init', array( 'BP_Members_Admin', 'register_members_admin' ) );
