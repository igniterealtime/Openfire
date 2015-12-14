<?php
/**
 * BuddyPress Groups Loader.
 *
 * A groups component, for users to group themselves together. Includes a
 * robust sub-component API that allows Groups to be extended.
 * Comes preconfigured with an activity stream, discussion forums, and settings.
 *
 * @package BuddyPress
 * @subpackage GroupsLoader
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

class BP_Groups_Component extends BP_Component {

	/**
	 * Auto-join group when non group member performs group activity.
	 *
	 * @since 1.5.0
	 * @var bool
	 */
	public $auto_join;

	/**
	 * The group being currently accessed.
	 *
	 * @since 1.5.0
	 * @var BP_Groups_Group
	 */
	public $current_group;

	/**
	 * Default group extension.
	 *
	 * @since 1.6.0
	 * @todo Is this used anywhere? Is this a duplicate of $default_extension?
	 */
	var $default_component;

	/**
	 * Default group extension.
	 *
	 * @since 1.6.0
	 * @var string
	 */
	public $default_extension;

	/**
	 * Illegal group names/slugs.
	 *
	 * @since 1.5.0
	 * @var array
	 */
	public $forbidden_names;

	/**
	 * Group creation/edit steps (e.g. Details, Settings, Avatar, Invites).
	 *
	 * @since 1.5.0
	 * @var array
	 */
	public $group_creation_steps;

	/**
	 * Types of group statuses (Public, Private, Hidden).
	 *
	 * @since 1.5.0
	 * @var array
	 */
	public $valid_status;

	/**
	 * Start the groups component creation process.
	 *
	 * @since 1.5.0
	 */
	public function __construct() {
		parent::start(
			'groups',
			_x( 'User Groups', 'Group screen page <title>', 'buddypress' ),
			buddypress()->plugin_dir,
			array(
				'adminbar_myaccount_order' => 70,
				'search_query_arg' => 'groups_search',
			)
		);
	}

	/**
	 * Include Groups component files.
	 *
	 * @since 1.5.0
	 *
	 * @see BP_Component::includes() for a description of arguments.
	 *
	 * @param array $includes See BP_Component::includes() for a description.
	 */
	public function includes( $includes = array() ) {
		$includes = array(
			'cache',
			'forums',
			'actions',
			'filters',
			'screens',
			'classes',
			'widgets',
			'activity',
			'template',
			'adminbar',
			'functions',
			'notifications'
		);

		if ( is_admin() ) {
			$includes[] = 'admin';
		}

		parent::includes( $includes );
	}

	/**
	 * Set up component global data.
	 *
	 * The BP_GROUPS_SLUG constant is deprecated, and only used here for
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

		// Define a slug, if necessary
		if ( ! defined( 'BP_GROUPS_SLUG' ) ) {
			define( 'BP_GROUPS_SLUG', $this->id );
		}

		// Global tables for groups component
		$global_tables = array(
			'table_name'           => $bp->table_prefix . 'bp_groups',
			'table_name_members'   => $bp->table_prefix . 'bp_groups_members',
			'table_name_groupmeta' => $bp->table_prefix . 'bp_groups_groupmeta'
		);

		// Metadata tables for groups component
		$meta_tables = array(
			'group' => $bp->table_prefix . 'bp_groups_groupmeta',
		);

		// All globals for groups component.
		// Note that global_tables is included in this array.
		$args = array(
			'slug'                  => BP_GROUPS_SLUG,
			'root_slug'             => isset( $bp->pages->groups->slug ) ? $bp->pages->groups->slug : BP_GROUPS_SLUG,
			'has_directory'         => true,
			'directory_title'       => _x( 'Groups', 'component directory title', 'buddypress' ),
			'notification_callback' => 'groups_format_notifications',
			'search_string'         => _x( 'Search Groups...', 'Component directory search', 'buddypress' ),
			'global_tables'         => $global_tables,
			'meta_tables'           => $meta_tables,
		);

		parent::setup_globals( $args );

		/** Single Group Globals **********************************************/

		// Are we viewing a single group?
		if ( bp_is_groups_component() && $group_id = BP_Groups_Group::group_exists( bp_current_action() ) ) {

			$bp->is_single_item  = true;

			/**
			 * Filters the current PHP Class being used.
			 *
			 * @since 1.5.0
			 *
			 * @param string $value Name of the class being used.
			 */
			$current_group_class = apply_filters( 'bp_groups_current_group_class', 'BP_Groups_Group' );

			if ( $current_group_class == 'BP_Groups_Group' ) {
				$this->current_group = groups_get_group( array(
					'group_id'        => $group_id,
					'populate_extras' => true,
				) );

			} else {

				/**
				 * Filters the current group object being instantiated from previous filter.
				 *
				 * @since 1.5.0
				 *
				 * @param object $value Newly instantiated object for the group.
				 */
				$this->current_group = apply_filters( 'bp_groups_current_group_object', new $current_group_class( $group_id ) );
			}

			// When in a single group, the first action is bumped down one because of the
			// group name, so we need to adjust this and set the group name to current_item.
			$bp->current_item   = bp_current_action();
			$bp->current_action = bp_action_variable( 0 );
			array_shift( $bp->action_variables );

			// Using "item" not "group" for generic support in other components.
			if ( bp_current_user_can( 'bp_moderate' ) ) {
				bp_update_is_item_admin( true, 'groups' );
			} else {
				bp_update_is_item_admin( groups_is_user_admin( bp_loggedin_user_id(), $this->current_group->id ), 'groups' );
			}

			// If the user is not an admin, check if they are a moderator
			if ( ! bp_is_item_admin() ) {
				bp_update_is_item_mod  ( groups_is_user_mod  ( bp_loggedin_user_id(), $this->current_group->id ), 'groups' );
			}

			// Is the logged in user a member of the group?
			if ( ( is_user_logged_in() && groups_is_user_member( bp_loggedin_user_id(), $this->current_group->id ) ) ) {
				$this->current_group->is_user_member = true;
			} else {
				$this->current_group->is_user_member = false;
			}

			// Should this group be visible to the logged in user?
			if ( 'public' == $this->current_group->status || $this->current_group->is_user_member ) {
				$this->current_group->is_visible = true;
			} else {
				$this->current_group->is_visible = false;
			}

			// If this is a private or hidden group, does the user have access?
			if ( 'private' == $this->current_group->status || 'hidden' == $this->current_group->status ) {
				if ( $this->current_group->is_user_member && is_user_logged_in() || bp_current_user_can( 'bp_moderate' ) ) {
					$this->current_group->user_has_access = true;
				} else {
					$this->current_group->user_has_access = false;
				}
			} else {
				$this->current_group->user_has_access = true;
			}

			// Check once if the current group has a custom front template
			$this->current_group->front_template = bp_groups_get_front_template( $this->current_group );

		// Set current_group to 0 to prevent debug errors
		} else {
			$this->current_group = 0;
		}

		/**
		 * Filters the list of illegal groups names/slugs.
		 *
		 * @since 1.0.0
		 *
		 * @param array $value Array of illegal group names/slugs.
		 */
		$this->forbidden_names = apply_filters( 'groups_forbidden_names', array(
			'my-groups',
			'create',
			'invites',
			'send-invites',
			'forum',
			'delete',
			'add',
			'admin',
			'request-membership',
			'members',
			'settings',
			'avatar',
			$this->slug,
			$this->root_slug,
		) );

		// If the user was attempting to access a group, but no group by that name was found, 404
		if ( bp_is_groups_component() && empty( $this->current_group ) && bp_current_action() && !in_array( bp_current_action(), $this->forbidden_names ) ) {
			bp_do_404();
			return;
		}

		/**
		 * Filters the preconfigured groups creation steps.
		 *
		 * @since 1.1.0
		 *
		 * @param array $value Array of preconfigured group creation steps.
		 */
		$this->group_creation_steps = apply_filters( 'groups_create_group_steps', array(
			'group-details'  => array(
				'name'       => _x( 'Details', 'Group screen nav', 'buddypress' ),
				'position'   => 0
			),
			'group-settings' => array(
				'name'       => _x( 'Settings', 'Group screen nav', 'buddypress' ),
				'position'   => 10
			)
		) );

		// If avatar uploads are not disabled, add avatar option
		$disabled_avatar_uploads = (int) bp_disable_group_avatar_uploads();
		if ( ! $disabled_avatar_uploads && $bp->avatar->show_avatars ) {
			$this->group_creation_steps['group-avatar'] = array(
				'name'     => _x( 'Photo', 'Group screen nav', 'buddypress' ),
				'position' => 20
			);
		}

		if ( bp_group_use_cover_image_header() ) {
			$this->group_creation_steps['group-cover-image'] = array(
				'name'     => _x( 'Cover Image', 'Group screen nav', 'buddypress' ),
				'position' => 25
			);
		}

		// If friends component is active, add invitations
		if ( bp_is_active( 'friends' ) ) {
			$this->group_creation_steps['group-invites'] = array(
				'name'     => _x( 'Invites',  'Group screen nav', 'buddypress' ),
				'position' => 30
			);
		}

		/**
		 * Filters the list of valid groups statuses.
		 *
		 * @since 1.1.0
		 *
		 * @param array $value Array of valid group statuses.
		 */
		$this->valid_status = apply_filters( 'groups_valid_status', array(
			'public',
			'private',
			'hidden'
		) );

		// Auto join group when non group member performs group activity
		$this->auto_join = defined( 'BP_DISABLE_AUTO_GROUP_JOIN' ) && BP_DISABLE_AUTO_GROUP_JOIN ? false : true;
	}

	/**
	 * Set up canonical stack for this component.
	 *
	 * @since 2.1.0
	 */
	public function setup_canonical_stack() {
		if ( ! bp_is_groups_component() ) {
			return;
		}

		if ( empty( $this->current_group ) ) {
			return;
		}

		/**
		 * Filters the default groups extension.
		 *
		 * @since 1.6.0
		 *
		 * @param string $value BP_GROUPS_DEFAULT_EXTENSION constant if defined,
		 *                      else 'home'.
		 */
		$this->default_extension = apply_filters( 'bp_groups_default_extension', defined( 'BP_GROUPS_DEFAULT_EXTENSION' ) ? BP_GROUPS_DEFAULT_EXTENSION : 'home' );

		$bp = buddypress();

		// If the activity component is not active and the current group has no custom front, members are displayed in the home nav
		if ( 'members' === $this->default_extension && ! bp_is_active( 'activity' ) && ! $this->current_group->front_template ) {
			$this->default_extension = 'home';
		}

		if ( ! bp_current_action() ) {
			$bp->current_action = $this->default_extension;
		}

		// Prepare for a redirect to the canonical URL
		$bp->canonical_stack['base_url'] = bp_get_group_permalink( $this->current_group );

		if ( bp_current_action() ) {
			$bp->canonical_stack['action'] = bp_current_action();
		}

		/**
		 * If there's no custom front.php template for the group, we need to make sure the canonical stack action
		 * is set to 'home' in these 2 cases:
		 *
		 * - the current action is 'activity' (eg: site.url/groups/single/activity) and the Activity component is active
		 * - the current action is 'members' (eg: site.url/groups/single/members) and the Activity component is *not* active.
		 */
		if ( ! $this->current_group->front_template && ( bp_is_current_action( 'activity' ) || ( ! bp_is_active( 'activity' ) && bp_is_current_action( 'members' ) ) ) ) {
			$bp->canonical_stack['action'] = 'home';
		}

		if ( ! empty( $bp->action_variables ) ) {
			$bp->canonical_stack['action_variables'] = bp_action_variables();
		}

		// When viewing the default extension, the canonical URL should not have
		// that extension's slug, unless more has been tacked onto the URL via
		// action variables
		if ( bp_is_current_action( $this->default_extension ) && empty( $bp->action_variables ) )  {
			unset( $bp->canonical_stack['action'] );
		}
	}

	/**
	 * Set up component navigation.
	 *
	 * @since 1.5.0
	 *
	 * @see BP_Component::setup_nav() for a description of arguments.
	 *
	 * @param array $main_nav Optional. See BP_Component::setup_nav() for description.
	 * @param array $sub_nav  Optional. See BP_Component::setup_nav() for description.
	 */
	public function setup_nav( $main_nav = array(), $sub_nav = array() ) {

		// Determine user to use
		if ( bp_displayed_user_domain() ) {
			$user_domain = bp_displayed_user_domain();
		} elseif ( bp_loggedin_user_domain() ) {
			$user_domain = bp_loggedin_user_domain();
		} else {
			$user_domain = false;
		}

		// Only grab count if we're on a user page
		if ( bp_is_user() ) {
			$count    = bp_get_total_group_count_for_user();
			$class    = ( 0 === $count ) ? 'no-count' : 'count';
			$nav_name = sprintf( _x( 'Groups <span class="%s">%s</span>', 'Group screen nav with counter', 'buddypress' ), esc_attr( $class ), bp_core_number_format( $count ) );
		} else {
			$nav_name = _x( 'Groups', 'Group screen nav without counter', 'buddypress' );
		}

		$slug = bp_get_groups_slug();

		// Add 'Groups' to the main navigation
		$main_nav = array(
			'name'                => $nav_name,
			'slug'                => $slug,
			'position'            => 70,
			'screen_function'     => 'groups_screen_my_groups',
			'default_subnav_slug' => 'my-groups',
			'item_css_id'         => $this->id
		);

		if ( ! empty( $user_domain ) ) {
			$access      = bp_core_can_edit_settings();
			$groups_link = trailingslashit( $user_domain . $slug );

			// Add the My Groups nav item
			$sub_nav[] = array(
				'name'            => __( 'Memberships', 'buddypress' ),
				'slug'            => 'my-groups',
				'parent_url'      => $groups_link,
				'parent_slug'     => $slug,
				'screen_function' => 'groups_screen_my_groups',
				'position'        => 10,
				'item_css_id'     => 'groups-my-groups'
			);

			// Add the Group Invites nav item
			$sub_nav[] = array(
				'name'            => __( 'Invitations', 'buddypress' ),
				'slug'            => 'invites',
				'parent_url'      => $groups_link,
				'parent_slug'     => $slug,
				'screen_function' => 'groups_screen_group_invites',
				'user_has_access' => $access,
				'position'        => 30
			);

			parent::setup_nav( $main_nav, $sub_nav );
		}

		if ( bp_is_groups_component() && bp_is_single_item() ) {

			// Reset sub nav
			$sub_nav = array();

			// Add 'Groups' to the main navigation
			$main_nav = array(
				'name'                => __( 'Memberships', 'buddypress' ),
				'slug'                => $this->current_group->slug,
				'position'            => -1, // Do not show in BuddyBar
				'screen_function'     => 'groups_screen_group_home',
				'default_subnav_slug' => $this->default_extension,
				'item_css_id'         => $this->id
			);

			$group_link = bp_get_group_permalink( $this->current_group );

			// Add the "Home" subnav item, as this will always be present
			$sub_nav[] = array(
				'name'            =>  _x( 'Home', 'Group screen navigation title', 'buddypress' ),
				'slug'            => 'home',
				'parent_url'      => $group_link,
				'parent_slug'     => $this->current_group->slug,
				'screen_function' => 'groups_screen_group_home',
				'position'        => 10,
				'item_css_id'     => 'home'
			);

			// If this is a private group, and the user is not a
			// member and does not have an outstanding invitation,
			// show a "Request Membership" nav item.
			if ( is_user_logged_in() &&
				 ! $this->current_group->is_user_member &&
				 ! groups_check_for_membership_request( bp_loggedin_user_id(), $this->current_group->id ) &&
				 $this->current_group->status == 'private' &&
				 ! groups_check_user_has_invite( bp_loggedin_user_id(), $this->current_group->id )
				) {

				$sub_nav[] = array(
					'name'            => _x( 'Request Membership','Group screen nav', 'buddypress' ),
					'slug'            => 'request-membership',
					'parent_url'      => $group_link,
					'parent_slug'     => $this->current_group->slug,
					'screen_function' => 'groups_screen_group_request_membership',
					'position'        => 30
				);
			}

			// Forums are enabled and turned on
			if ( $this->current_group->enable_forum && bp_is_active( 'forums' ) ) {
				$sub_nav[] = array(
					'name'            => _x( 'Forum', 'My Group screen nav', 'buddypress' ),
					'slug'            => 'forum',
					'parent_url'      => $group_link,
					'parent_slug'     => $this->current_group->slug,
					'screen_function' => 'groups_screen_group_forum',
					'position'        => 40,
					'user_has_access' => $this->current_group->user_has_access,
					'item_css_id'     => 'forums'
				);
			}

			if ( $this->current_group->front_template || bp_is_active( 'activity' ) ) {
				/**
				 * If the theme is using a custom front, create activity subnav.
				 */
				if ( $this->current_group->front_template && bp_is_active( 'activity' ) ) {
					$sub_nav[] = array(
						'name'            => _x( 'Activity', 'My Group screen nav', 'buddypress' ),
						'slug'            => 'activity',
						'parent_url'      => $group_link,
						'parent_slug'     => $this->current_group->slug,
						'screen_function' => 'groups_screen_group_activity',
						'position'        => 11,
						'user_has_access' => $this->current_group->user_has_access,
						'item_css_id'     => 'activity',
						'no_access_url'   => $group_link,
					);
				}

				/**
				 * Only add the members subnav if it's not the home's nav
				 */
				$sub_nav[] = array(
					'name'            => sprintf( _x( 'Members %s', 'My Group screen nav', 'buddypress' ), '<span>' . number_format( $this->current_group->total_member_count ) . '</span>' ),
					'slug'            => 'members',
					'parent_url'      => $group_link,
					'parent_slug'     => $this->current_group->slug,
					'screen_function' => 'groups_screen_group_members',
					'position'        => 60,
					'user_has_access' => $this->current_group->user_has_access,
					'item_css_id'     => 'members',
					'no_access_url'   => $group_link,
				);
			}

			if ( bp_is_active( 'friends' ) && bp_groups_user_can_send_invites() ) {
				$sub_nav[] = array(
					'name'            => _x( 'Send Invites', 'My Group screen nav', 'buddypress' ),
					'slug'            => 'send-invites',
					'parent_url'      => $group_link,
					'parent_slug'     => $this->current_group->slug,
					'screen_function' => 'groups_screen_group_invite',
					'item_css_id'     => 'invite',
					'position'        => 70,
					'user_has_access' => $this->current_group->user_has_access,
					'no_access_url'   => $group_link,
				);
			}

			// If the user is a group admin, then show the group admin nav item
			if ( bp_is_item_admin() ) {
				$sub_nav[] = array(
					'name'            => _x( 'Manage', 'My Group screen nav', 'buddypress' ),
					'slug'            => 'admin',
					'parent_url'      => $group_link,
					'parent_slug'     => $this->current_group->slug,
					'screen_function' => 'groups_screen_group_admin',
					'position'        => 1000,
					'user_has_access' => true,
					'item_css_id'     => 'admin',
					'no_access_url'   => $group_link,
				);

				$admin_link = trailingslashit( $group_link . 'admin' );

				// Common params to all nav items
				$default_params = array(
					'parent_url'        => $admin_link,
					'parent_slug'       => $this->current_group->slug . '_manage',
					'screen_function'   => 'groups_screen_group_admin',
					'user_has_access'   => bp_is_item_admin(),
					'show_in_admin_bar' => true,
				);

				$sub_nav[] = array_merge( array(
					'name'     => __( 'Details', 'buddypress' ),
					'slug'     => 'edit-details',
					'position' => 0,
				), $default_params );

				$sub_nav[] = array_merge( array(
					'name'     => __( 'Settings', 'buddypress' ),
					'slug'     => 'group-settings',
					'position' => 10,
				), $default_params );

				if ( ! bp_disable_group_avatar_uploads() && buddypress()->avatar->show_avatars ) {
					$sub_nav[] = array_merge( array(
						'name'     => __( 'Photo', 'buddypress' ),
						'slug'     => 'group-avatar',
						'position' => 20,
					), $default_params );
				}

				if ( bp_group_use_cover_image_header() ) {
					$sub_nav[] = array_merge( array(
						'name'     => __( 'Cover Image', 'buddypress' ),
						'slug'     => 'group-cover-image',
						'position' => 25,
					), $default_params );
				}

				$sub_nav[] = array_merge( array(
					'name'     => __( 'Members', 'buddypress' ),
					'slug'     => 'manage-members',
					'position' => 30,
				), $default_params );

				if ( 'private' == $this->current_group->status ) {
					$sub_nav[] = array_merge( array(
						'name'     => __( 'Requests', 'buddypress' ),
						'slug'     => 'membership-requests',
						'position' => 40,
					), $default_params );
				}

				$sub_nav[] = array_merge( array(
					'name'     => __( 'Delete', 'buddypress' ),
					'slug'     => 'delete-group',
					'position' => 1000,
				), $default_params );
			}

			parent::setup_nav( $main_nav, $sub_nav );
		}

		if ( isset( $this->current_group->user_has_access ) ) {

			/**
			 * Fires at the end of the groups navigation setup if user has access.
			 *
			 * @since 1.0.2
			 *
			 * @param bool $user_has_access Whether or not user has access.
			 */
			do_action( 'groups_setup_nav', $this->current_group->user_has_access );
		} else {

			/** This action is documented in bp-groups/bp-groups-loader.php */
			do_action( 'groups_setup_nav');
		}
	}

	/**
	 * Set up the component entries in the WordPress Admin Bar.
	 *
	 * @see BP_Component::setup_nav() for a description of the $wp_admin_nav
	 *      parameter array.
	 *
	 * @param array $wp_admin_nav See BP_Component::setup_admin_bar() for a description.
	 */
	public function setup_admin_bar( $wp_admin_nav = array() ) {

		// Menus for logged in user
		if ( is_user_logged_in() ) {

			// Setup the logged in user variables
			$groups_link = trailingslashit( bp_loggedin_user_domain() . bp_get_groups_slug() );

			// Pending group invites
			$count   = groups_get_invite_count_for_user();
			$title   = _x( 'Groups', 'My Account Groups', 'buddypress' );
			$pending = _x( 'No Pending Invites', 'My Account Groups sub nav', 'buddypress' );

			if ( ! empty( $count['total'] ) ) {
				$title   = sprintf( _x( 'Groups <span class="count">%s</span>',          'My Account Groups nav',     'buddypress' ), bp_core_number_format( $count ) );
				$pending = sprintf( _x( 'Pending Invites <span class="count">%s</span>', 'My Account Groups sub nav', 'buddypress' ), bp_core_number_format( $count ) );
			}

			// Add the "My Account" sub menus
			$wp_admin_nav[] = array(
				'parent' => buddypress()->my_account_menu_id,
				'id'     => 'my-account-' . $this->id,
				'title'  => $title,
				'href'   => $groups_link
			);

			// My Groups
			$wp_admin_nav[] = array(
				'parent' => 'my-account-' . $this->id,
				'id'     => 'my-account-' . $this->id . '-memberships',
				'title'  => _x( 'Memberships', 'My Account Groups sub nav', 'buddypress' ),
				'href'   => $groups_link
			);

			// Invitations
			$wp_admin_nav[] = array(
				'parent' => 'my-account-' . $this->id,
				'id'     => 'my-account-' . $this->id . '-invites',
				'title'  => $pending,
				'href'   => trailingslashit( $groups_link . 'invites' )
			);

			// Create a Group
			if ( bp_user_can_create_groups() ) {
				$wp_admin_nav[] = array(
					'parent' => 'my-account-' . $this->id,
					'id'     => 'my-account-' . $this->id . '-create',
					'title'  => _x( 'Create a Group', 'My Account Groups sub nav', 'buddypress' ),
					'href'   => trailingslashit( bp_get_groups_directory_permalink() . 'create' )
				);
			}
		}

		parent::setup_admin_bar( $wp_admin_nav );
	}

	/**
	 * Set up the title for pages and <title>.
	 */
	public function setup_title() {

		if ( bp_is_groups_component() ) {
			$bp = buddypress();

			if ( bp_is_my_profile() && !bp_is_single_item() ) {
				$bp->bp_options_title = _x( 'Memberships', 'My Groups page <title>', 'buddypress' );

			} elseif ( !bp_is_my_profile() && !bp_is_single_item() ) {
				$bp->bp_options_avatar = bp_core_fetch_avatar( array(
					'item_id' => bp_displayed_user_id(),
					'type'    => 'thumb',
					'alt'     => sprintf( __( 'Profile picture of %s', 'buddypress' ), bp_get_displayed_user_fullname() )
				) );
				$bp->bp_options_title = bp_get_displayed_user_fullname();

			// We are viewing a single group, so set up the
			// group navigation menu using the $this->current_group global.
			} elseif ( bp_is_single_item() ) {
				$bp->bp_options_title  = $this->current_group->name;
				$bp->bp_options_avatar = bp_core_fetch_avatar( array(
					'item_id'    => $this->current_group->id,
					'object'     => 'group',
					'type'       => 'thumb',
					'avatar_dir' => 'group-avatars',
					'alt'        => __( 'Group Profile Photo', 'buddypress' )
				) );

				if ( empty( $bp->bp_options_avatar ) ) {
					$bp->bp_options_avatar = '<img src="' . esc_url( bp_core_avatar_default_thumb() ) . '" alt="' . esc_attr__( 'No Group Profile Photo', 'buddypress' ) . '" class="avatar" />';
				}
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

		// Global groups
		wp_cache_add_global_groups( array(
			'bp_groups',
			'bp_group_admins',
			'bp_group_invite_count',
			'group_meta'
		) );

		parent::setup_cache_groups();
	}
}

/**
 * Bootstrap the Notifications component.
 *
 * @since 1.5.0
 */
function bp_setup_groups() {
	buddypress()->groups = new BP_Groups_Component();
}
add_action( 'bp_setup_components', 'bp_setup_groups', 6 );
