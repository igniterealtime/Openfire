<?php
/**
 * BuddyPress Common Admin Functions.
 *
 * @package BuddyPress
 * @subpackage CoreAdministration
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/** Menu **********************************************************************/

/**
 * Initializes the wp-admin area "BuddyPress" menus and sub menus.
 *
 * @package BuddyPress Core
 * @uses bp_current_user_can() returns true if the current user is a site admin, false if not
 */
function bp_core_admin_menu_init() {
	add_action( bp_core_admin_hook(), 'bp_core_add_admin_menu', 9 );
}

/**
 * In BP 1.6, the top-level admin menu was removed. For backpat, this function
 * keeps the top-level menu if a plugin has registered a menu into the old
 * 'bp-general-settings' menu.
 *
 * The old "bp-general-settings" page was renamed "bp-components".
 *
 * @global array $_parent_pages
 * @global array $_registered_pages
 * @global array $submenu
 * @since 1.6.0
 */
function bp_core_admin_backpat_menu() {
	global $_parent_pages, $_registered_pages, $submenu;

	// If there's no bp-general-settings menu (perhaps because the current
	// user is not an Administrator), there's nothing to do here
	if ( ! isset( $submenu['bp-general-settings'] ) ) {
		return;
	}

	/**
	 * By default, only the core "Help" submenu is added under the top-level BuddyPress menu.
	 * This means that if no third-party plugins have registered their admin pages into the
	 * 'bp-general-settings' menu, it will only contain one item. Kill it.
	 */
	if ( 1 != count( $submenu['bp-general-settings'] ) ) {
		return;
	}

	// This removes the top-level menu
	remove_submenu_page( 'bp-general-settings', 'bp-general-settings' );
	remove_menu_page( 'bp-general-settings' );

	// These stop people accessing the URL directly
	unset( $_parent_pages['bp-general-settings'] );
	unset( $_registered_pages['toplevel_page_bp-general-settings'] );
}
add_action( bp_core_admin_hook(), 'bp_core_admin_backpat_menu', 999 );

/**
 * This tells WP to highlight the Settings > BuddyPress menu item,
 * regardless of which actual BuddyPress admin screen we are on.
 *
 * The conditional prevents the behaviour when the user is viewing the
 * backpat "Help" page, the Activity page, or any third-party plugins.
 *
 * @global string $plugin_page
 * @global array $submenu
 * @since 1.6.0
 */
function bp_core_modify_admin_menu_highlight() {
	global $plugin_page, $submenu_file;

	// This tweaks the Settings subnav menu to show only one BuddyPress menu item
	if ( ! in_array( $plugin_page, array( 'bp-activity', 'bp-general-settings', ) ) ) {
		$submenu_file = 'bp-components';
	}

	// Network Admin > Tools
	if ( in_array( $plugin_page, array( 'bp-tools', 'available-tools' ) ) ) {
		$submenu_file = $plugin_page;
	}
}

/**
 * Generates markup for a fallback top-level BuddyPress menu page, if the site is running
 * a legacy plugin which hasn't been updated. If the site is up to date, this page
 * will never appear.
 *
 * @see bp_core_admin_backpat_menu()
 * @since 1.6.0
 * @todo Add convenience links into the markup once new positions are finalised.
 */
function bp_core_admin_backpat_page() {
	$url          = bp_core_do_network_admin() ? network_admin_url( 'settings.php' ) : admin_url( 'options-general.php' );
	$settings_url = add_query_arg( 'page', 'bp-components', $url ); ?>

	<div class="wrap">
		<h2><?php _e( 'Why have all my BuddyPress menus disappeared?', 'buddypress' ); ?></h2>

		<p><?php _e( "Don't worry! We've moved the BuddyPress options into more convenient and easier to find locations. You're seeing this page because you are running a legacy BuddyPress plugin which has not been updated.", 'buddypress' ); ?></p>
		<p><?php printf( __( 'Components, Pages, Settings, and Forums, have been moved to <a href="%s">Settings &gt; BuddyPress</a>. Profile Fields has been moved into the <a href="%s">Users</a> menu.', 'buddypress' ), esc_url( $settings_url ), bp_get_admin_url( 'users.php?page=bp-profile-setup' ) ); ?></p>
	</div>

	<?php
}

/** Notices *******************************************************************/

/**
 * Print admin messages to admin_notices or network_admin_notices.
 *
 * BuddyPress combines all its messages into a single notice, to avoid a preponderance of yellow
 * boxes.
 *
 * @since 1.5.0
 *
 * @uses bp_current_user_can() to check current user permissions before showing the notices.
 * @uses bp_is_root_blog()
 */
function bp_core_print_admin_notices() {

	// Only the super admin should see messages
	if ( ! bp_current_user_can( 'bp_moderate' ) ) {
		return;
	}

	// On multisite installs, don't show on a non-root blog, unless
	// 'do_network_admin' is overridden.
	if ( is_multisite() && bp_core_do_network_admin() && ! bp_is_root_blog() ) {
		return;
	}

	$notice_types = array();
	foreach ( buddypress()->admin->notices as $notice ) {
		$notice_types[] = $notice['type'];
	}
	$notice_types = array_unique( $notice_types );

	foreach ( $notice_types as $type ) {
		$notices = wp_list_filter( buddypress()->admin->notices, array( 'type' => $type ) );
		printf( '<div id="message" class="fade %s">', sanitize_html_class( $type ) );

		foreach ( $notices as $notice ) {
			printf( '<p>%s</p>', $notice['message'] );
		}

		printf( '</div>' );
	}
}
add_action( 'admin_notices',         'bp_core_print_admin_notices' );
add_action( 'network_admin_notices', 'bp_core_print_admin_notices' );

/**
 * Add an admin notice to the BP queue.
 *
 * Messages added with this function are displayed in BuddyPress's general purpose admin notices
 * box. It is recommended that you hook this function to admin_init, so that your messages are
 * loaded in time.
 *
 * @since 1.5.0
 *
 * @param string $notice The notice you are adding to the queue.
 * @param string $type   The notice type; optional. Usually either "updated" or "error".
 */
function bp_core_add_admin_notice( $notice = '', $type = 'updated' ) {

	// Do not add if the notice is empty
	if ( empty( $notice ) ) {
		return;
	}

	// Double check the object before referencing it
	if ( ! isset( buddypress()->admin->notices ) ) {
		buddypress()->admin->notices = array();
	}

	// Add the notice
	buddypress()->admin->notices[] = array(
		'message' => $notice,
		'type'    => $type,
	);
}

/**
 * Verify that some BP prerequisites are set up properly, and notify the admin if not.
 *
 * On every Dashboard page, this function checks the following:
 *   - that pretty permalinks are enabled.
 *   - that every BP component that needs a WP page for a directory has one.
 *   - that no WP page has multiple BP components associated with it.
 * The administrator will be shown a notice for each check that fails.
 *
 * @global WPDB $wpdb WordPress DB object
 * @global WP_Rewrite $wp_rewrite
 * @since 1.2.0
 */
function bp_core_activation_notice() {
	global $wp_rewrite, $wpdb;

	// Only the super admin gets warnings
	if ( ! bp_current_user_can( 'bp_moderate' ) ) {
		return;
	}

	// Bail in user admin
	if ( is_user_admin() ) {
		return;
	}

	// On multisite installs, don't load on a non-root blog, unless do_network_admin is overridden
	if ( is_multisite() && bp_core_do_network_admin() && ! bp_is_root_blog() ) {
		return;
	}

	// Bail if in network admin, and BuddyPress is not network activated
	if ( is_network_admin() && ! bp_is_network_activated() ) {
		return;
	}

	/**
	 * Check to make sure that the blog setup routine has run. This can't
	 * happen during the wizard because of the order which the components
	 * are loaded.
	 */
	if ( bp_is_active( 'blogs' ) ) {
		$bp    = buddypress();
		$count = $wpdb->get_var( "SELECT COUNT(*) FROM {$bp->blogs->table_name}" );

		if ( empty( $count ) ) {
			bp_blogs_record_existing_blogs();
		}
	}

	// Add notice if no rewrite rules are enabled
	if ( empty( $wp_rewrite->permalink_structure ) ) {
		bp_core_add_admin_notice( sprintf( __( '<strong>BuddyPress is almost ready</strong>. You must <a href="%s">update your permalink structure</a> to something other than the default for it to work.', 'buddypress' ), admin_url( 'options-permalink.php' ) ), 'error' );
	}

	// Get BuddyPress instance
	$bp = buddypress();

	/**
	 * Check for orphaned BP components (BP component is enabled, no WP page exists)
	 */
	$orphaned_components = array();
	$wp_page_components  = array();

	// Only components with 'has_directory' require a WP page to function
	foreach( array_keys( $bp->loaded_components ) as $component_id ) {
		if ( !empty( $bp->{$component_id}->has_directory ) ) {
			$wp_page_components[] = array(
				'id'   => $component_id,
				'name' => isset( $bp->{$component_id}->name ) ? $bp->{$component_id}->name : ucwords( $bp->{$component_id}->id )
			);
		}
	}

	// Activate and Register are special cases. They are not components but they need WP pages.
	// If user registration is disabled, we can skip this step.
	if ( bp_get_signup_allowed() ) {
		$wp_page_components[] = array(
			'id'   => 'activate',
			'name' => __( 'Activate', 'buddypress' )
		);

		$wp_page_components[] = array(
			'id'   => 'register',
			'name' => __( 'Register', 'buddypress' )
		);
	}

	// On the first admin screen after a new installation, this isn't set, so grab it to suppress a misleading error message.
	if ( empty( $bp->pages->members ) ) {
		$bp->pages = bp_core_get_directory_pages();
	}

	foreach( $wp_page_components as $component ) {
		if ( !isset( $bp->pages->{$component['id']} ) ) {
			$orphaned_components[] = $component['name'];
		}
	}

	// Special case: If the Forums component is orphaned, but the bbPress 1.x installation is
	// not correctly set up, don't show a nag. (In these cases, it's probably the case that the
	// user is using bbPress 2.x; see https://buddypress.trac.wordpress.org/ticket/4292
	if ( isset( $bp->forums->name ) && in_array( $bp->forums->name, $orphaned_components ) && !bp_forums_is_installed_correctly() ) {
		$forum_key = array_search( $bp->forums->name, $orphaned_components );
		unset( $orphaned_components[$forum_key] );
		$orphaned_components = array_values( $orphaned_components );
	}

	if ( !empty( $orphaned_components ) ) {
		$admin_url = bp_get_admin_url( add_query_arg( array( 'page' => 'bp-page-settings' ), 'admin.php' ) );
		$notice    = sprintf( __( 'The following active BuddyPress Components do not have associated WordPress Pages: %2$s. <a href="%1$s">Repair</a>', 'buddypress' ), esc_url( $admin_url ), '<strong>' . implode( '</strong>, <strong>', $orphaned_components ) . '</strong>' );

		bp_core_add_admin_notice( $notice );
	}

	// BP components cannot share a single WP page. Check for duplicate assignments, and post a message if found.
	$dupe_names = array();
	$page_ids   = (array)bp_core_get_directory_page_ids();
	$dupes      = array_diff_assoc( $page_ids, array_unique( $page_ids ) );

	if ( !empty( $dupes ) ) {
		foreach( array_keys( $dupes ) as $dupe_component ) {
			$dupe_names[] = $bp->pages->{$dupe_component}->title;
		}

		// Make sure that there are no duplicate duplicates :)
		$dupe_names = array_unique( $dupe_names );
	}

	// If there are duplicates, post a message about them
	if ( !empty( $dupe_names ) ) {
		$admin_url = bp_get_admin_url( add_query_arg( array( 'page' => 'bp-page-settings' ), 'admin.php' ) );
		$notice    = sprintf( __( 'Each BuddyPress Component needs its own WordPress page. The following WordPress Pages have more than one component associated with them: %2$s. <a href="%1$s">Repair</a>', 'buddypress' ), esc_url( $admin_url ), '<strong>' . implode( '</strong>, <strong>', $dupe_names ) . '</strong>' );

		bp_core_add_admin_notice( $notice );
	}
}

/**
 * Redirect user to BuddyPress's What's New page on activation.
 *
 * @since 1.7.0
 *
 * @internal Used internally to redirect BuddyPress to the about page on activation.
 *
 * @uses get_transient() To see if transient to redirect exists.
 * @uses delete_transient() To delete the transient if it exists.
 * @uses is_network_admin() To bail if being network activated.
 * @uses wp_safe_redirect() To redirect.
 * @uses add_query_arg() To help build the URL to redirect to.
 * @uses admin_url() To get the admin URL to index.php.
 */
function bp_do_activation_redirect() {

	// Bail if no activation redirect
	if ( ! get_transient( '_bp_activation_redirect' ) ) {
		return;
	}

	// Delete the redirect transient
	delete_transient( '_bp_activation_redirect' );

	// Bail if activating from network, or bulk
	if ( isset( $_GET['activate-multi'] ) ) {
		return;
	}

	$query_args = array( 'page' => 'bp-about' );
	if ( get_transient( '_bp_is_new_install' ) ) {
		$query_args['is_new_install'] = '1';
		delete_transient( '_bp_is_new_install' );
	}

	// Redirect to BuddyPress about page
	wp_safe_redirect( add_query_arg( $query_args, bp_get_admin_url( 'index.php' ) ) );
}

/** UI/Styling ****************************************************************/

/**
 * Output the tabs in the admin area.
 *
 * @since 1.5.0
 *
 * @param string $active_tab Name of the tab that is active. Optional.
 */
function bp_core_admin_tabs( $active_tab = '' ) {
	$tabs_html    = '';
	$idle_class   = 'nav-tab';
	$active_class = 'nav-tab nav-tab-active';

	/**
	 * Filters the admin tabs to be displayed.
	 *
	 * @since 1.9.0
	 *
	 * @param array $value Array of tabs to output to the admin area.
	 */
	$tabs         = apply_filters( 'bp_core_admin_tabs', bp_core_get_admin_tabs( $active_tab ) );

	// Loop through tabs and build navigation
	foreach ( array_values( $tabs ) as $tab_data ) {
		$is_current = (bool) ( $tab_data['name'] == $active_tab );
		$tab_class  = $is_current ? $active_class : $idle_class;
		$tabs_html .= '<a href="' . esc_url( $tab_data['href'] ) . '" class="' . esc_attr( $tab_class ) . '">' . esc_html( $tab_data['name'] ) . '</a>';
	}

	echo $tabs_html;

	/**
	 * Fires after the output of tabs for the admin area.
	 *
	 * @since 1.5.0
	 */
	do_action( 'bp_admin_tabs' );
}

/**
 * Get the data for the tabs in the admin area.
 *
 * @since 2.2.0
 *
 * @param string $active_tab Name of the tab that is active. Optional.
 *
 * @return string
 */
function bp_core_get_admin_tabs( $active_tab = '' ) {
	$tabs = array(
		'0' => array(
			'href' => bp_get_admin_url( add_query_arg( array( 'page' => 'bp-components' ), 'admin.php' ) ),
			'name' => __( 'Components', 'buddypress' )
		),
		'1' => array(
			'href' => bp_get_admin_url( add_query_arg( array( 'page' => 'bp-page-settings' ), 'admin.php' ) ),
			'name' => __( 'Pages', 'buddypress' )
		),
		'2' => array(
			'href' => bp_get_admin_url( add_query_arg( array( 'page' => 'bp-settings' ), 'admin.php' ) ),
			'name' => __( 'Settings', 'buddypress' )
		),
	);

	// If forums component is active, add additional tab
	if ( bp_is_active( 'forums' ) && class_exists( 'BP_Forums_Component' ) ) {

		// enqueue thickbox
		wp_enqueue_script( 'thickbox' );
		wp_enqueue_style( 'thickbox' );

		$tabs['3'] = array(
			'href' => bp_get_admin_url( add_query_arg( array( 'page' => 'bb-forums-setup'  ), 'admin.php' ) ),
			'name' => __( 'Forums', 'buddypress' )
		);
	}

	/**
	 * Filters the tab data used in our wp-admin screens.
	 *
	 * @since 2.2.0
	 *
	 * @param array $tabs Tab data.
	 */
	return apply_filters( 'bp_core_get_admin_tabs', $tabs );
}

/** Help **********************************************************************/

/**
 * adds contextual help to BuddyPress admin pages.
 *
 * @since 1.7.0
 * @todo Make this part of the BP_Component class and split into each component
 *
 * @param string $screen
 */
function bp_core_add_contextual_help( $screen = '' ) {

	$screen = get_current_screen();

	switch ( $screen->id ) {

		// Component page
		case 'settings_page_bp-components' :

			// help tabs
			$screen->add_help_tab( array(
				'id'      => 'bp-comp-overview',
				'title'   => __( 'Overview', 'buddypress' ),
				'content' => bp_core_add_contextual_help_content( 'bp-comp-overview' ),
			) );

			// help panel - sidebar links
			$screen->set_help_sidebar(
				'<p><strong>' . __( 'For more information:', 'buddypress' ) . '</strong></p>' .
				'<p>' . __( '<a href="https://codex.buddypress.org/getting-started/configure-components/">Managing Components</a>', 'buddypress' ) . '</p>' .
				'<p>' . __( '<a href="https://buddypress.org/support/">Support Forums</a>', 'buddypress' ) . '</p>'
			);
			break;

		// Pages page
		case 'settings_page_bp-page-settings' :

			// Help tabs
			$screen->add_help_tab( array(
				'id' => 'bp-page-overview',
				'title' => __( 'Overview', 'buddypress' ),
				'content' => bp_core_add_contextual_help_content( 'bp-page-overview' ),
			) );

			// Help panel - sidebar links
			$screen->set_help_sidebar(
				'<p><strong>' . __( 'For more information:', 'buddypress' ) . '</strong></p>' .
				'<p>' . __( '<a href="https://codex.buddypress.org/getting-started/configure-components/#settings-buddypress-pages">Managing Pages</a>', 'buddypress' ) . '</p>' .
				'<p>' . __( '<a href="https://buddypress.org/support/">Support Forums</a>', 'buddypress' ) . '</p>'
			);

			break;

		// Settings page
		case 'settings_page_bp-settings' :

			// Help tabs
			$screen->add_help_tab( array(
				'id'      => 'bp-settings-overview',
				'title'   => __( 'Overview', 'buddypress' ),
				'content' => bp_core_add_contextual_help_content( 'bp-settings-overview' ),
			) );

			// Help panel - sidebar links
			$screen->set_help_sidebar(
				'<p><strong>' . __( 'For more information:', 'buddypress' ) . '</strong></p>' .
				'<p>' . __( '<a href="https://codex.buddypress.org/getting-started/configure-components/#settings-buddypress-settings">Managing Settings</a>', 'buddypress' ) . '</p>' .
				'<p>' . __( '<a href="https://buddypress.org/support/">Support Forums</a>', 'buddypress' ) . '</p>'
			);

			break;

		// Profile fields page
		case 'users_page_bp-profile-setup' :

			// Help tabs
			$screen->add_help_tab( array(
				'id'      => 'bp-profile-overview',
				'title'   => __( 'Overview', 'buddypress' ),
				'content' => bp_core_add_contextual_help_content( 'bp-profile-overview' ),
			) );

			// Help panel - sidebar links
			$screen->set_help_sidebar(
				'<p><strong>' . __( 'For more information:', 'buddypress' ) . '</strong></p>' .
				'<p>' . __( '<a href="https://codex.buddypress.org/administrator-guide/extended-profiles/">Managing Profile Fields</a>', 'buddypress' ) . '</p>' .
				'<p>' . __( '<a href="https://buddypress.org/support/">Support Forums</a>', 'buddypress' ) . '</p>'
			);

			break;
	}
}
add_action( 'contextual_help', 'bp_core_add_contextual_help' );

/**
 * renders contextual help content to contextual help tabs.
 *
 * @since 1.7.0
 *
 * @param string $tab
 *
 * @return string
 */
function bp_core_add_contextual_help_content( $tab = '' ) {

	switch ( $tab ) {
		case 'bp-comp-overview' :
			$retval = __( 'By default, all but four of the BuddyPress components are enabled. You can selectively enable or disable any of the components by using the form below. Your BuddyPress installation will continue to function. However, the features of the disabled components will no longer be accessible to anyone using the site.', 'buddypress' );
			break;

		case 'bp-page-overview' :
			$retval = __( 'BuddyPress Components use WordPress Pages for their root directory/archive pages. You can change the page associations for each active component by using the form below.', 'buddypress' );
			break;

		case 'bp-settings-overview' :
			$retval = __( 'Extra configuration settings are provided and activated. You can selectively enable or disable any setting by using the form on this screen.', 'buddypress' );
			break;

		case 'bp-profile-overview' :
			$retval = __( 'Your users will distinguish themselves through their profile page. Create relevant profile fields that will show on each users profile.', 'buddypress' ) . '<br /><br />' . __( 'Note: Any fields in the first group will appear on the signup page.', 'buddypress' );
			break;

		default:
			$retval = false;
			break;
	}

	// Wrap text in a paragraph tag
	if ( !empty( $retval ) ) {
		$retval = '<p>' . $retval . '</p>';
	}

	return $retval;
}

/** Separator *****************************************************************/

/**
 * Add a separator to the WordPress admin menus.
 *
 * @since 1.7.0
 *
 * @uses bp_current_user_can() To check users capability on root blog.
 */
function bp_admin_separator() {

	// Bail if BuddyPress is not network activated and viewing network admin
	if ( is_network_admin() && ! bp_is_network_activated() ) {
		return;
	}

	// Bail if BuddyPress is network activated and viewing site admin
	if ( ! is_network_admin() && bp_is_network_activated() ) {
		return;
	}

	// Prevent duplicate separators when no core menu items exist
	if ( ! bp_current_user_can( 'bp_moderate' ) ) {
		return;
	}

	// Bail if there are no components with admin UI's. Hardcoded for now, until
	// there's a real API for determining this later.
	if ( ! bp_is_active( 'activity' ) && ! bp_is_active( 'groups' ) ) {
		return;
	}

	global $menu;

	$menu[] = array( '', 'read', 'separator-buddypress', '', 'wp-menu-separator buddypress' );
}

/**
 * Tell WordPress we have a custom menu order.
 *
 * @since 1.7.0
 * @uses bp_current_user_can() To check users capability on root blog.
 *
 * @param bool $menu_order Menu order.
 *
 * @return bool Always true
 */
function bp_admin_custom_menu_order( $menu_order = false ) {

	// Bail if user cannot see admin pages
	if ( ! bp_current_user_can( 'bp_moderate' ) ) {
		return $menu_order;
	}

	return true;
}

/**
 * Move our custom separator above our custom post types.
 *
 * @since 1.7.0
 * @uses bp_current_user_can() To check users capability on root blog.
 *
 * @param array $menu_order Menu Order.
 *
 * @return array Modified menu order
 */
function bp_admin_menu_order( $menu_order = array() ) {

	// Bail if user cannot see admin pages
	if ( empty( $menu_order ) || ! bp_current_user_can( 'bp_moderate' ) ) {
		return $menu_order;
	}

	// Initialize our custom order array
	$bp_menu_order = array();

	// Menu values
	$last_sep     = is_network_admin() ? 'separator1' : 'separator2';

	/**
	 * Filters the custom admin menus.
	 *
	 * @since 1.7.0
	 *
	 * @param array $value Empty array.
	 */
	$custom_menus = (array) apply_filters( 'bp_admin_menu_order', array() );

	// Bail if no components have top level admin pages
	if ( empty( $custom_menus ) ) {
		return $menu_order;
	}

	// Add our separator to beginning of array
	array_unshift( $custom_menus, 'separator-buddypress' );

	// Loop through menu order and do some rearranging
	foreach ( (array) $menu_order as $item ) {

		// Position BuddyPress menus above appearance
		if ( $last_sep == $item ) {

			// Add our custom menus
			foreach( (array) $custom_menus as $custom_menu ) {
				if ( array_search( $custom_menu, $menu_order ) ) {
					$bp_menu_order[] = $custom_menu;
				}
			}

			// Add the appearance separator
			$bp_menu_order[] = $last_sep;

		// Skip our menu items
		} elseif ( ! in_array( $item, $custom_menus ) ) {
			$bp_menu_order[] = $item;
		}
	}

	// Return our custom order
	return $bp_menu_order;
}

/** Utility  *****************************************************************/

/**
 * When using a WP_List_Table, get the currently selected bulk action.
 *
 * WP_List_Tables have bulk actions at the top and at the bottom of the tables,
 * and the inputs have different keys in the $_REQUEST array. This function
 * reconciles the two values and returns a single action being performed.
 *
 * @since 1.7.0
 *
 * @return string
 */
function bp_admin_list_table_current_bulk_action() {

	$action = ! empty( $_REQUEST['action'] ) ? $_REQUEST['action'] : '';

	// If the bottom is set, let it override the action
	if ( ! empty( $_REQUEST['action2'] ) && $_REQUEST['action2'] != "-1" ) {
		$action = $_REQUEST['action2'];
	}

	return $action;
}

/** Menus *********************************************************************/

/**
 * Register meta box and associated JS for BuddyPress WP Nav Menu.
 *
 * @since 1.9.0
 */
function bp_admin_wp_nav_menu_meta_box() {
	if ( ! bp_is_root_blog() ) {
		return;
	}

	add_meta_box( 'add-buddypress-nav-menu', __( 'BuddyPress', 'buddypress' ), 'bp_admin_do_wp_nav_menu_meta_box', 'nav-menus', 'side', 'default' );

	add_action( 'admin_print_footer_scripts', 'bp_admin_wp_nav_menu_restrict_items' );
}

/**
 * Build and populate the BuddyPress accordion on Appearance > Menus.
 *
 * @since 1.9.0
 *
 * @global $nav_menu_selected_id
 */
function bp_admin_do_wp_nav_menu_meta_box() {
	global $nav_menu_selected_id;

	$walker = new BP_Walker_Nav_Menu_Checklist( false );
	$args   = array( 'walker' => $walker );

	$post_type_name = 'buddypress';

	$tabs = array();

	$tabs['loggedin']['label']  = __( 'Logged-In', 'buddypress' );
	$tabs['loggedin']['pages']  = bp_nav_menu_get_loggedin_pages();

	$tabs['loggedout']['label'] = __( 'Logged-Out', 'buddypress' );
	$tabs['loggedout']['pages'] = bp_nav_menu_get_loggedout_pages();

	?>

	<div id="buddypress-menu" class="posttypediv">
		<h4><?php _e( 'Logged-In', 'buddypress' ) ?></h4>
		<p><?php _e( '<em>Logged-In</em> links are relative to the current user, and are not visible to visitors who are not logged in.', 'buddypress' ) ?></p>

		<div id="tabs-panel-posttype-<?php echo $post_type_name; ?>-loggedin" class="tabs-panel tabs-panel-active">
			<ul id="buddypress-menu-checklist-loggedin" class="categorychecklist form-no-clear">
				<?php echo walk_nav_menu_tree( array_map( 'wp_setup_nav_menu_item', $tabs['loggedin']['pages'] ), 0, (object) $args );?>
			</ul>
		</div>

		<h4><?php _e( 'Logged-Out', 'buddypress' ) ?></h4>
		<p><?php _e( '<em>Logged-Out</em> links are not visible to users who are logged in.', 'buddypress' ) ?></p>

		<div id="tabs-panel-posttype-<?php echo $post_type_name; ?>-loggedout" class="tabs-panel tabs-panel-active">
			<ul id="buddypress-menu-checklist-loggedout" class="categorychecklist form-no-clear">
				<?php echo walk_nav_menu_tree( array_map( 'wp_setup_nav_menu_item', $tabs['loggedout']['pages'] ), 0, (object) $args );?>
			</ul>
		</div>

		<p class="button-controls">
			<span class="add-to-menu">
				<input type="submit"<?php if ( function_exists( 'wp_nav_menu_disabled_check' ) ) : wp_nav_menu_disabled_check( $nav_menu_selected_id ); endif; ?> class="button-secondary submit-add-to-menu right" value="<?php esc_attr_e( 'Add to Menu', 'buddypress' ); ?>" name="add-custom-menu-item" id="submit-buddypress-menu" />
				<span class="spinner"></span>
			</span>
		</p>
	</div><!-- /#buddypress-menu -->

	<?php
}

/**
 * Restrict various items from view if editing a BuddyPress menu.
 *
 * If a person is editing a BP menu item, that person should not be able to
 * see or edit the following fields:
 *
 * - CSS Classes - We use the 'bp-menu' CSS class to determine if the
 *   menu item belongs to BP, so we cannot allow manipulation of this field to
 *   occur.
 * - URL - This field is automatically generated by BP on output, so this
 *   field is useless and can cause confusion.
 *
 * Note: These restrictions are only enforced if JavaScript is enabled.
 *
 * @since 1.9.0
 */
function bp_admin_wp_nav_menu_restrict_items() {
?>
	<script type="text/javascript">
	jQuery( '#menu-to-edit').on( 'click', 'a.item-edit', function() {
		var settings  = jQuery(this).closest( '.menu-item-bar' ).next( '.menu-item-settings' );
		var css_class = settings.find( '.edit-menu-item-classes' );

		if( css_class.val().indexOf( 'bp-menu' ) === 0 ) {
			css_class.attr( 'readonly', 'readonly' );
			settings.find( '.field-url' ).css( 'display', 'none' );
		}
	});
	</script>
<?php
}

/**
 * Add "Mark as Spam/Ham" button to user row actions.
 *
 * @since 2.0.0
 *
 * @param array  $actions     User row action links.
 * @param object $user_object Current user information.
 *
 * @return array $actions User row action links.
 */
function bp_core_admin_user_row_actions( $actions, $user_object ) {

	// Setup the $user_id variable from the current user object
	$user_id = 0;
	if ( !empty( $user_object->ID ) ) {
		$user_id = absint( $user_object->ID );
	}

	// Bail early if user cannot perform this action, or is looking at themselves
	if ( current_user_can( 'edit_user', $user_id ) && ( bp_loggedin_user_id() !== $user_id ) ) {

		// Admin URL could be single site or network
		$url = bp_get_admin_url( 'users.php' );

		// If spammed, create unspam link
		if ( bp_is_user_spammer( $user_id ) ) {
			$url             = add_query_arg( array( 'action' => 'ham', 'user' => $user_id ), $url );
			$unspam_link     = wp_nonce_url( $url, 'bp-spam-user' );
			$actions['ham']  = sprintf( '<a href="%1$s">%2$s</a>', esc_url( $unspam_link ), esc_html__( 'Not Spam', 'buddypress' ) );

		// If not already spammed, create spam link
		} else {
			$url             = add_query_arg( array( 'action' => 'spam', 'user' => $user_id ), $url );
			$spam_link       = wp_nonce_url( $url, 'bp-spam-user' );
			$actions['spam'] = sprintf( '<a class="submitdelete" href="%1$s">%2$s</a>', esc_url( $spam_link ), esc_html__( 'Spam', 'buddypress' ) );
		}
	}

	// Create a "View" link
	$url             = bp_core_get_user_domain( $user_id );
	$actions['view'] = sprintf( '<a href="%1$s">%2$s</a>', esc_url( $url ), esc_html__( 'View', 'buddypress' ) );

	// Return new actions
	return $actions;
}

/**
 * Catch requests to mark individual users as spam/ham from users.php.
 *
 * @since 2.0.0
 */
function bp_core_admin_user_manage_spammers() {

	// Print our inline scripts on non-Multisite
	add_action( 'admin_footer', 'bp_core_admin_user_spammed_js' );

	$action  = isset( $_REQUEST['action'] ) ? $_REQUEST['action'] : false;
	$updated = isset( $_REQUEST['updated'] ) ? $_REQUEST['updated'] : false;
	$mode    = isset( $_POST['mode'] ) ? $_POST['mode'] : false;

	// if this is a multisite, bulk request, stop now!
	if ( 'list' == $mode ) {
		return;
	}

	// Process a spam/ham request
	if ( ! empty( $action ) && in_array( $action, array( 'spam', 'ham' ) ) ) {

		check_admin_referer( 'bp-spam-user' );

		$user_id = ! empty( $_REQUEST['user'] ) ? intval( $_REQUEST['user'] ) : false;

		if ( empty( $user_id ) ) {
			return;
		}

		$redirect = wp_get_referer();

		$status = ( $action == 'spam' ) ? 'spam' : 'ham';

		// Process the user
		bp_core_process_spammer_status( $user_id, $status );

		$redirect = add_query_arg( array( 'updated' => 'marked-' . $status ), $redirect );

		wp_redirect( $redirect );
	}

	// Display feedback
	if ( ! empty( $updated ) && in_array( $updated, array( 'marked-spam', 'marked-ham' ) ) ) {

		if ( 'marked-spam' === $updated ) {
			$notice = __( 'User marked as spammer. Spam users are visible only to site admins.', 'buddypress' );
		} else {
			$notice = __( 'User removed from spam.', 'buddypress' );
		}

		bp_core_add_admin_notice( $notice );
	}
}

/**
 * Inline script that adds the 'site-spammed' class to spammed users.
 *
 * @since 2.0.0
 */
function bp_core_admin_user_spammed_js() {
	?>
	<script type="text/javascript">
		jQuery( document ).ready( function($) {
			$( '.row-actions .ham' ).each( function() {
				$( this ).closest( 'tr' ).addClass( 'site-spammed' );
			});
		});
	</script>
	<?php
}
