<?php
/**
 * Core BuddyPress Navigational Functions.
 *
 * @package BuddyPress
 * @subpackage Core
 * @todo Deprecate BuddyBar functions.
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Add an item to the main BuddyPress navigation array.
 *
 * @param array|string $args {
 *     Array describing the new nav item.
 *     @type string      $name                    Display name for the nav item.
 *     @type string      $slug                    Unique URL slug for the nav item.
 *     @type bool|string $item_css_id             Optional. 'id' attribute for the nav item. Default: the value of `$slug`.
 *     @type bool        $show_for_displayed_user Optional. Whether the nav item should be visible when viewing a
 *                                                member profile other than your own. Default: true.
 *     @type bool        $site_admin_only         Optional. Whether the nav item should be visible only to site admins
 *                                                (those with the 'bp_moderate' cap). Default: false.
 *     @type int         $position                Optional. Numerical index specifying where the item should appear in
 *                                                the nav array. Default: 99.
 *     @type callable    $screen_function         The callback function that will run when the nav item is clicked.
 *     @type bool|string $default_subnav_slug     Optional. The slug of the default subnav item to select when the nav
 *                                                item is clicked.
 * }
 * @return bool|null Returns false on failure.
 */
function bp_core_new_nav_item( $args = '' ) {

	$defaults = array(
		'name'                    => false, // Display name for the nav item
		'slug'                    => false, // URL slug for the nav item
		'item_css_id'             => false, // The CSS ID to apply to the HTML of the nav item
		'show_for_displayed_user' => true,  // When viewing another user does this nav item show up?
		'site_admin_only'         => false, // Can only site admins see this nav item?
		'position'                => 99,    // Index of where this nav item should be positioned
		'screen_function'         => false, // The name of the function to run when clicked
		'default_subnav_slug'     => false  // The slug of the default subnav item to select when clicked
	);

	$r = wp_parse_args( $args, $defaults );

	// First, add the nav item link to the bp_nav array.
	$created = bp_core_create_nav_link( $r );

	// To mimic the existing behavior, if bp_core_create_nav_link()
	// returns false, we make an early exit and don't attempt to register
	// the screen function.
	if ( false === $created ) {
		return false;
	}

	// Then, hook the screen function for the added nav item.
	$hooked = bp_core_register_nav_screen_function( $r );
	if ( false === $hooked ){
		return false;
	}

	/**
	 * Fires after adding an item to the main BuddyPress navigation array.
	 * Note that, when possible, the more specific action hooks
	 * `bp_core_create_nav_link` or `bp_core_register_nav_screen_function`
	 * should be used.
	 *
	 * @since 1.5.0
	 *
	 * @param array $r        Parsed arguments for the nav item.
	 * @param array $args     Originally passed in arguments for the nav item.
	 * @param array $defaults Default arguments for a nav item.
	 */
	do_action( 'bp_core_new_nav_item', $r, $args, $defaults );
}

/**
 * Add a link to the main BuddyPress navigation array.
 *
 * @since 2.4.0
 *
 * @param array|string $args {
 *     Array describing the new nav item.
 *     @type string      $name                    Display name for the nav item.
 *     @type string      $slug                    Unique URL slug for the nav item.
 *     @type bool|string $item_css_id             Optional. 'id' attribute for the nav item. Default: the value of `$slug`.
 *     @type bool        $show_for_displayed_user Optional. Whether the nav item should be visible when viewing a
 *                                                member profile other than your own. Default: true.
 *     @type bool        $site_admin_only         Optional. Whether the nav item should be visible only to site admins
 *                                                (those with the 'bp_moderate' cap). Default: false.
 *     @type int         $position                Optional. Numerical index specifying where the item should appear in
 *                                                the nav array. Default: 99.
 *     @type callable    $screen_function         The callback function that will run when the nav item is clicked.
 *     @type bool|string $default_subnav_slug     Optional. The slug of the default subnav item to select when the nav
 *                                                item is clicked.
 * }
 * @return bool|null Returns false on failure.
 */
function bp_core_create_nav_link( $args = '' ) {
	$bp = buddypress();

	$defaults = array(
		'name'                    => false, // Display name for the nav item
		'slug'                    => false, // URL slug for the nav item
		'item_css_id'             => false, // The CSS ID to apply to the HTML of the nav item
		'show_for_displayed_user' => true,  // When viewing another user does this nav item show up?
		'site_admin_only'         => false, // Can only site admins see this nav item?
		'position'                => 99,    // Index of where this nav item should be positioned
		'screen_function'         => false, // The name of the function to run when clicked
		'default_subnav_slug'     => false  // The slug of the default subnav item to select when clicked
	);

	$r = wp_parse_args( $args, $defaults );

	// If we don't have the required info we need, don't create this nav item.
	if ( empty( $r['name'] ) || empty( $r['slug'] ) ) {
		return false;
	}

	// If this is for site admins only and the user is not one, don't create the nav item.
	if ( ! empty( $r['site_admin_only'] ) && ! bp_current_user_can( 'bp_moderate' ) ) {
		return false;
	}

	if ( empty( $r['item_css_id'] ) ) {
		$r['item_css_id'] = $r['slug'];
	}

	$bp->bp_nav[$r['slug']] = array(
		'name'                    => $r['name'],
		'slug'                    => $r['slug'],
		'link'                    => trailingslashit( bp_loggedin_user_domain() . $r['slug'] ),
		'css_id'                  => $r['item_css_id'],
		'show_for_displayed_user' => $r['show_for_displayed_user'],
		'position'                => $r['position'],
		'screen_function'         => &$r['screen_function'],
		'default_subnav_slug'	  => $r['default_subnav_slug']
	);

	/**
	 * Fires after a link is added to the main BuddyPress navigation array.
	 *
	 * @since 2.4.0
	 *
	 * @param array $r        Parsed arguments for the nav item.
	 * @param array $args     Originally passed in arguments for the nav item.
	 * @param array $defaults Default arguments for a nav item.
	 */
	do_action( 'bp_core_create_nav_link', $r, $args, $defaults );
}

/**
 * Register a screen function for an item in the main nav array.
 *
 * @since 2.4.0
 *
 * @param array|string $args {
 *     Array describing the new nav item.
 *     @type string      $name                    Display name for the nav item.
 *     @type string      $slug                    Unique URL slug for the nav item.
 *     @type bool|string $item_css_id             Optional. 'id' attribute for the nav item. Default: the value of `$slug`.
 *     @type bool        $show_for_displayed_user Optional. Whether the nav item should be visible when viewing a
 *                                                member profile other than your own. Default: true.
 *     @type bool        $site_admin_only         Optional. Whether the nav item should be visible only to site admins
 *                                                (those with the 'bp_moderate' cap). Default: false.
 *     @type int         $position                Optional. Numerical index specifying where the item should appear in
 *                                                the nav array. Default: 99.
 *     @type callable    $screen_function         The callback function that will run when the nav item is clicked.
 *     @type bool|string $default_subnav_slug     Optional. The slug of the default subnav item to select when the nav
 *                                                item is clicked.
 * }
 * @return bool|null Returns false on failure.
 */
function bp_core_register_nav_screen_function( $args = '' ) {
	$bp = buddypress();

	$defaults = array(
		'name'                    => false, // Display name for the nav item
		'slug'                    => false, // URL slug for the nav item
		'item_css_id'             => false, // The CSS ID to apply to the HTML of the nav item
		'show_for_displayed_user' => true,  // When viewing another user does this nav item show up?
		'site_admin_only'         => false, // Can only site admins see this nav item?
		'position'                => 99,    // Index of where this nav item should be positioned
		'screen_function'         => false, // The name of the function to run when clicked
		'default_subnav_slug'     => false  // The slug of the default subnav item to select when clicked
	);

	$r = wp_parse_args( $args, $defaults );

	// If we don't have the required info we need, don't register this screen function.
	if ( empty( $r['slug'] ) ) {
		return false;
	}

	/**
	* If this is for site admins only and the user is not one,
	* don't register this screen function.
	*/
	if ( ! empty( $r['site_admin_only'] ) && ! bp_current_user_can( 'bp_moderate' ) ) {
		return false;
	}

 	/**
	 * If this nav item is hidden for the displayed user, and
	 * the logged in user is not the displayed user
	 * looking at their own profile, don't don't register this screen function.
	 */
	if ( empty( $r['show_for_displayed_user'] ) && ! bp_user_has_access() ) {
		return false;
	}

	/**
 	 * If the nav item is visible, we are not viewing a user, and this is a root
	 * component, don't attach the default subnav function so we can display a
	 * directory or something else.
 	 */
	if ( ( -1 != $r['position'] ) && bp_is_root_component( $r['slug'] ) && ! bp_displayed_user_id() ) {
		return;
	}

	// Look for current component
	if ( bp_is_current_component( $r['slug'] ) || bp_is_current_item( $r['slug'] ) ) {

		// The requested URL has explicitly included the default subnav
		// (eg: http://example.com/members/membername/activity/just-me/)
		// The canonical version will not contain this subnav slug.
		if ( ! empty( $r['default_subnav_slug'] ) && bp_is_current_action( $r['default_subnav_slug'] ) && ! bp_action_variable( 0 ) ) {
			unset( $bp->canonical_stack['action'] );
		} elseif ( ! bp_current_action() ) {

			// Add our screen hook if screen function is callable
			if ( is_callable( $r['screen_function'] ) ) {
				add_action( 'bp_screens', $r['screen_function'], 3 );
			}

			if ( ! empty( $r['default_subnav_slug'] ) ) {

				/**
				 * Filters the default component subnav item.
				 *
				 * @since 1.5.0
				 *
				 * @param string $value The slug of the default subnav item
				 *                      to select when clicked.
				 * @param array  $r     Parsed arguments for the nav item.
				 */
				$bp->current_action = apply_filters( 'bp_default_component_subnav', $r['default_subnav_slug'], $r );
			}
		}
	}

	/**
	 * Fires after the screen function for an item in the BuddyPress main
	 * navigation is registered.
	 *
	 * @since 2.4.0
	 *
	 * @param array $r        Parsed arguments for the nav item.
	 * @param array $args     Originally passed in arguments for the nav item.
	 * @param array $defaults Default arguments for a nav item.
	 */
	do_action( 'bp_core_register_nav_screen_function', $r, $args, $defaults );
}

/**
 * Modify the default subnav item that loads when a top level nav item is clicked.
 *
 * @param array|string $args {
 *     @type string   $parent_slug     The slug of the nav item whose default is being changed.
 *     @type callable $screen_function The new default callback function that will run when the nav item is clicked.
 *     @type string   $subnav_slug     The slug of the new default subnav item.
 * }
 */
function bp_core_new_nav_default( $args = '' ) {
	$bp = buddypress();

	$defaults = array(
		'parent_slug'     => false, // Slug of the parent
		'screen_function' => false, // The name of the function to run when clicked
		'subnav_slug'     => false  // The slug of the subnav item to select when clicked
	);

	$r = wp_parse_args( $args, $defaults );

	if ( $function = $bp->bp_nav[$r['parent_slug']]['screen_function'] ) {
		// Remove our screen hook if screen function is callable
		if ( is_callable( $function ) ) {
			remove_action( 'bp_screens', $function, 3 );
		}
	}

	$bp->bp_nav[$r['parent_slug']]['screen_function'] = &$r['screen_function'];

	if ( bp_is_current_component( $r['parent_slug'] ) ) {

		// The only way to tell whether to set the subnav is to peek at the unfiltered_uri
		// Find the component
		$component_uri_key = array_search( $r['parent_slug'], $bp->unfiltered_uri );

		if ( false !== $component_uri_key ) {
			if ( ! empty( $bp->unfiltered_uri[$component_uri_key + 1] ) ) {
				$unfiltered_action = $bp->unfiltered_uri[$component_uri_key + 1];
			}
		}

		// No subnav item has been requested in the URL, so set a new nav default
		if ( empty( $unfiltered_action ) ) {
			if ( ! bp_is_current_action( $r['subnav_slug'] ) ) {
				if ( is_callable( $r['screen_function'] ) ) {
					add_action( 'bp_screens', $r['screen_function'], 3 );
				}

				$bp->current_action = $r['subnav_slug'];
				unset( $bp->canonical_stack['action'] );
			}

		// The URL is explicitly requesting the new subnav item, but should be
		// directed to the canonical URL
		} elseif ( $unfiltered_action == $r['subnav_slug'] ) {
			unset( $bp->canonical_stack['action'] );

		// In all other cases (including the case where the original subnav item
		// is explicitly called in the URL), the canonical URL will contain the
		// subnav slug
		} else {
			$bp->canonical_stack['action'] = bp_current_action();
		}
	}

	return;
}

/**
 * Sort the navigation menu items.
 *
 * The sorting is split into a separate function because it can only happen
 * after all plugins have had a chance to register their navigation items.
 *
 * @return bool|null Returns false on failure.
 */
function bp_core_sort_nav_items() {
	$bp = buddypress();

	if ( empty( $bp->bp_nav ) || ! is_array( $bp->bp_nav ) ) {
		return false;
	}

	$temp = array();

	foreach ( (array) $bp->bp_nav as $slug => $nav_item ) {
		if ( empty( $temp[$nav_item['position']] ) ) {
			$temp[$nav_item['position']] = $nav_item;
		} else {
			// increase numbers here to fit new items in.
			do {
				$nav_item['position']++;
			} while ( ! empty( $temp[$nav_item['position']] ) );

			$temp[$nav_item['position']] = $nav_item;
		}
	}

	ksort( $temp );
	$bp->bp_nav = &$temp;
}
add_action( 'wp_head',    'bp_core_sort_nav_items' );
add_action( 'admin_head', 'bp_core_sort_nav_items' );

/**
 * Add a subnav item to the BuddyPress navigation.
 *
 * @param array|string $args {
 *     Array describing the new subnav item.
 *     @type string      $name              Display name for the subnav item.
 *     @type string      $slug              Unique URL slug for the subnav item.
 *     @type string      $parent_slug       Slug of the top-level nav item under which the new subnav item should
 *                                          be added.
 *     @type string      $parent_url        URL of the parent nav item.
 *     @type bool|string $item_css_id       Optional. 'id' attribute for the nav item. Default: the value of `$slug`.
 *     @type bool        $user_has_access   Optional. True if the logged-in user has access to the subnav item,
 *                                          otherwise false. Can be set dynamically when registering the subnav;
 *                                          eg, use `bp_is_my_profile()` to restrict access to profile owners only.
 *                                          Default: true.
 *     @type bool        $site_admin_only   Optional. Whether the nav item should be visible only to site admins
 *                                          (those with the 'bp_moderate' cap). Default: false.
 *     @type int         $position          Optional. Numerical index specifying where the item should appear in the
 *                                          subnav array. Default: 90.
 *     @type callable    $screen_function   The callback function that will run when the nav item is clicked.
 *     @type string      $link              Optional. The URL that the subnav item should point to. Defaults to a value
 *                                          generated from the `$parent_url` + `$slug`.
 *     @type bool        $show_in_admin_bar Optional. Whether the nav item should be added into the group's "Edit"
 *                                          Admin Bar menu for group admins. Default: false.
 * }
 * @return bool|null Returns false on failure.
 */
function bp_core_new_subnav_item( $args = '' ) {

	// First, add the subnav item link to the bp_options_nav array.
	$created = bp_core_create_subnav_link( $args );

	// To mimic the existing behavior, if bp_core_create_subnav_link()
	// returns false, we make an early exit and don't attempt to register
	// the screen function.
	if ( false === $created ) {
		return false;
	}

	// Then, hook the screen function for the added subnav item.
	$hooked = bp_core_register_subnav_screen_function( $args );
	if ( false === $hooked ) {
		return false;
	}
}

/**
 * Add a subnav link to the BuddyPress navigation.
 *
 * @param array|string $args {
 *     Array describing the new subnav item.
 *     @type string      $name              Display name for the subnav item.
 *     @type string      $slug              Unique URL slug for the subnav item.
 *     @type string      $parent_slug       Slug of the top-level nav item under which the
 *                                          new subnav item should be added.
 *     @type string      $parent_url        URL of the parent nav item.
 *     @type bool|string $item_css_id       Optional. 'id' attribute for the nav
 *                                          item. Default: the value of $slug.
 *     @type bool        $user_has_access   Optional. True if the logged-in user has access to the
 *                                          subnav item, otherwise false. Can be set dynamically
 *                                          when registering the subnav; eg, use bp_is_my_profile()
 *                                          to restrict access to profile owners only. Default: true.
 *     @type bool        $site_admin_only   Optional. Whether the nav item should be visible only
 *                                          to site admins (those with the 'bp_moderate' cap).
 *                                          Default: false.
 *     @type int         $position          Optional. Numerical index specifying where the item
 *                                          should appear in the subnav array. Default: 90.
 *     @type callable    $screen_function   The callback function that will run
 *                                          when the nav item is clicked.
 *     @type string      $link              Optional. The URL that the subnav item should point
 *                                          to. Defaults to a value generated from the $parent_url + $slug.
 *     @type bool        $show_in_admin_bar Optional. Whether the nav item should be added into
 *                                          the group's "Edit" Admin Bar menu for group admins.
 *                                          Default: false.
 * }
 *
 * @return bool|null Returns false on failure.
 */
function bp_core_create_subnav_link( $args = '' ) {
	$bp = buddypress();

	$r = wp_parse_args( $args, array(
		'name'              => false, // Display name for the nav item
		'slug'              => false, // URL slug for the nav item
		'parent_slug'       => false, // URL slug of the parent nav item
		'parent_url'        => false, // URL of the parent item
		'item_css_id'       => false, // The CSS ID to apply to the HTML of the nav item
		'user_has_access'   => true,  // Can the logged in user see this nav item?
		'no_access_url'     => '',
		'site_admin_only'   => false, // Can only site admins see this nav item?
		'position'          => 90,    // Index of where this nav item should be positioned
		'screen_function'   => false, // The name of the function to run when clicked
		'link'              => '',    // The link for the subnav item; optional, not usually required.
		'show_in_admin_bar' => false, // Show the Manage link in the current group's "Edit" Admin Bar menu
	) );

	// If we don't have the required info we need, don't create this subnav item
	if ( empty( $r['name'] ) || empty( $r['slug'] ) || empty( $r['parent_slug'] ) || empty( $r['parent_url'] ) || empty( $r['screen_function'] ) )
		return false;

	// Link was not forced, so create one
	if ( empty( $r['link'] ) ) {
		$r['link'] = trailingslashit( $r['parent_url'] . $r['slug'] );

		// If this sub item is the default for its parent, skip the slug
		if ( ! empty( $bp->bp_nav[$r['parent_slug']]['default_subnav_slug'] ) && $r['slug'] == $bp->bp_nav[$r['parent_slug']]['default_subnav_slug'] ) {
			$r['link'] = trailingslashit( $r['parent_url'] );
		}
	}

	// If this is for site admins only and the user is not one, don't create the subnav item
	if ( ! empty( $r['site_admin_only'] ) && ! bp_current_user_can( 'bp_moderate' ) ) {
		return false;
	}

	if ( empty( $r['item_css_id'] ) ) {
		$r['item_css_id'] = $r['slug'];
	}

	$subnav_item = array(
		'name'              => $r['name'],
		'link'              => $r['link'],
		'slug'              => $r['slug'],
		'css_id'            => $r['item_css_id'],
		'position'          => $r['position'],
		'user_has_access'   => $r['user_has_access'],
		'no_access_url'     => $r['no_access_url'],
		'screen_function'   => &$r['screen_function'],
		'show_in_admin_bar' => (bool) $r['show_in_admin_bar'],
	);

	$bp->bp_options_nav[$r['parent_slug']][$r['slug']] = $subnav_item;
}

/**
 * Register a screen function, whether or not a related subnav link exists.
 *
 * @param array|string $args {
 *     Array describing the new subnav item.
 *     @type string   $slug              Unique URL slug for the subnav item.
 *     @type string   $parent_slug       Slug of the top-level nav item under which the
 *                                       new subnav item should be added.
 *     @type string   $parent_url        URL of the parent nav item.
 *     @type bool     $user_has_access   Optional. True if the logged-in user has access to the
 *                                       subnav item, otherwise false. Can be set dynamically
 *                                       when registering the subnav; eg, use bp_is_my_profile()
 *                                       to restrict access to profile owners only. Default: true.
 *     @type bool     $site_admin_only   Optional. Whether the nav item should be visible
 *                                       only to site admins (those with the 'bp_moderate' cap).
 *                                       Default: false.
 *     @type int      $position          Optional. Numerical index specifying where the item
 *                                       should appear in the subnav array. Default: 90.
 *     @type callable $screen_function   The callback function that will run
 *                                       when the nav item is clicked.
 *     @type string   $link              Optional. The URL that the subnav item should point to.
 *                                       Defaults to a value generated from the $parent_url + $slug.
 *     @type bool     $show_in_admin_bar Optional. Whether the nav item should be added into
 *                                       the group's "Edit" Admin Bar menu for group admins.
 *                                       Default: false.
 * }
 *
 * @return bool|null Returns false on failure.
 */
function bp_core_register_subnav_screen_function( $args = '' ) {
	$bp = buddypress();

	$r = wp_parse_args( $args, array(
		'slug'              => false, // URL slug for the screen
		'parent_slug'       => false, // URL slug of the parent screen
		'user_has_access'   => true,  // Can the user visit this screen?
		'no_access_url'     => '',
		'site_admin_only'   => false, // Can only site admins visit this screen?
		'screen_function'   => false, // The name of the function to run when clicked
	) );

	/**
	 * Hook the screen function for the added subnav item. But this only needs to
	 * be done if this subnav item is the current view, and the user has access to the
	 * subnav item. We figure out whether we're currently viewing this subnav by
	 * checking the following two conditions:
	 *   (1) Either:
	 *	     (a) the parent slug matches the current_component, or
	 *	     (b) the parent slug matches the current_item
	 *   (2) And either:
	 *	     (a) the current_action matches $slug, or
	 *       (b) there is no current_action (ie, this is the default subnav for the parent nav)
	 *	     and this subnav item is the default for the parent item (which we check by
	 *	     comparing this subnav item's screen function with the screen function of the
	 *	     parent nav item in $bp->bp_nav). This condition only arises when viewing a
	 *	     user, since groups should always have an action set.
	 */

	// If we *don't* meet condition (1), return
	if ( ! bp_is_current_component( $r['parent_slug'] ) && ! bp_is_current_item( $r['parent_slug'] ) ) {
		return;
	}

	// If we *do* meet condition (2), then the added subnav item is currently being requested
	if ( ( bp_current_action() && bp_is_current_action( $r['slug'] ) ) || ( bp_is_user() && ! bp_current_action() && ( $r['screen_function'] == $bp->bp_nav[$r['parent_slug']]['screen_function'] ) ) ) {

		// If this is for site admins only and the user is not one, don't create the subnav item
		if ( ! empty( $r['site_admin_only'] ) && ! bp_current_user_can( 'bp_moderate' ) ) {
			return false;
		}

		$hooked = bp_core_maybe_hook_new_subnav_screen_function( $r );

		// If redirect args have been returned, perform the redirect now
		if ( ! empty( $hooked['status'] ) && 'failure' === $hooked['status'] && isset( $hooked['redirect_args'] ) ) {
			bp_core_no_access( $hooked['redirect_args'] );
		}
	}
}

/**
 * For a given subnav item, either hook the screen function or generate redirect arguments, as necessary.
 *
 * @since 2.1.0
 *
 * @param array $subnav_item The subnav array added to bp_options_nav in `bp_core_new_subnav_item()`.
 *
 * @return array
 */
function bp_core_maybe_hook_new_subnav_screen_function( $subnav_item ) {
	$retval = array(
		'status' => '',
	);

	// Is this accessible by site admins only?
	$site_admin_restricted = false;
	if ( ! empty( $subnav_item['site_admin_only'] ) && ! bp_current_user_can( 'bp_moderate' ) ) {
		$site_admin_restricted = true;
	}

	// User has access, so let's try to hook the display callback
	if ( ! empty( $subnav_item['user_has_access'] ) && ! $site_admin_restricted ) {

		// Screen function is invalid
		if ( ! is_callable( $subnav_item['screen_function'] ) ) {
			$retval['status'] = 'failure';

		// Success - hook to bp_screens
		} else {
			add_action( 'bp_screens', $subnav_item['screen_function'], 3 );
			$retval['status'] = 'success';
		}

	// User doesn't have access. Determine redirect arguments based on
	// user status
	} else {
		$retval['status'] = 'failure';

		if ( is_user_logged_in() ) {

			$bp = buddypress();

			// If a redirect URL has been passed to the subnav
			// item, respect it
			if ( ! empty( $subnav_item['no_access_url'] ) ) {
				$message     = __( 'You do not have access to this page.', 'buddypress' );
				$redirect_to = trailingslashit( $subnav_item['no_access_url'] );

			// In the case of a user page, we try to assume a
			// redirect URL
			} elseif ( bp_is_user() ) {

				// Redirect to the displayed user's default
				// component, as long as that component is
				// publicly accessible.
				if ( bp_is_my_profile() || ! empty( $bp->bp_nav[ $bp->default_component ]['show_for_displayed_user'] ) ) {
					$message     = __( 'You do not have access to this page.', 'buddypress' );
					$redirect_to = bp_displayed_user_domain();

				// In some cases, the default tab is not accessible to
				// the logged-in user. So we fall back on a tab that we
				// know will be accessible.
				} else {
					// Try 'activity' first
					if ( bp_is_active( 'activity' ) && isset( $bp->pages->activity ) ) {
						$redirect_to = trailingslashit( bp_displayed_user_domain() . bp_get_activity_slug() );
					// Then try 'profile'
					} else {
						$redirect_to = trailingslashit( bp_displayed_user_domain() . ( 'xprofile' == $bp->profile->id ? 'profile' : $bp->profile->id ) );
					}

					$message     = '';
				}

			// Fall back to the home page
			} else {
				$message     = __( 'You do not have access to this page.', 'buddypress' );
				$redirect_to = bp_get_root_domain();
			}

			$retval['redirect_args'] = array(
				'message'  => $message,
				'root'     => $redirect_to,
				'redirect' => false,
			);

		} else {
			// When the user is logged out, pass an empty array
			// This indicates that the default arguments should be
			// used in bp_core_no_access()
			$retval['redirect_args'] = array();
		}
	}

	return $retval;
}

/**
 * Sort all subnavigation arrays.
 *
 * @return bool|null Returns false on failure.
 */
function bp_core_sort_subnav_items() {
	$bp = buddypress();

	if ( empty( $bp->bp_options_nav ) || !is_array( $bp->bp_options_nav ) )
		return false;

	foreach ( (array) $bp->bp_options_nav as $parent_slug => $subnav_items ) {
		if ( !is_array( $subnav_items ) )
			continue;

		foreach ( (array) $subnav_items as $subnav_item ) {
			if ( empty( $temp[$subnav_item['position']]) )
				$temp[$subnav_item['position']] = $subnav_item;
			else {
				// increase numbers here to fit new items in.
				do {
					$subnav_item['position']++;
				} while ( !empty( $temp[$subnav_item['position']] ) );

				$temp[$subnav_item['position']] = $subnav_item;
			}
		}
		ksort( $temp );
		$bp->bp_options_nav[$parent_slug] = &$temp;
		unset( $temp );
	}
}
add_action( 'wp_head',    'bp_core_sort_subnav_items' );
add_action( 'admin_head', 'bp_core_sort_subnav_items' );

/**
 * Check whether a given nav item has subnav items.
 *
 * @since 1.5.0
 *
 * @param string $nav_item The slug of the top-level nav item whose subnav items you're checking.
 *                         Default: the current component slug.
 *
 * @return bool $has_subnav True if the nav item is found and has subnav items; false otherwise.
 */
function bp_nav_item_has_subnav( $nav_item = '' ) {
	$bp = buddypress();

	if ( !$nav_item )
		$nav_item = bp_current_component();

	$has_subnav = isset( $bp->bp_options_nav[$nav_item] ) && count( $bp->bp_options_nav[$nav_item] ) > 0;

	/**
	 * Filters whether or not a given nav item has subnav items.
	 *
	 * @since 1.5.0
	 *
	 * @param bool   $has_subnav Whether or not there is any subnav items.
	 * @param string $nav_item   The slug of the top-level nav item whose subnav items you're checking.
	 */
	return apply_filters( 'bp_nav_item_has_subnav', $has_subnav, $nav_item );
}

/**
 * Remove a nav item from the navigation array.
 *
 * @param int $parent_id The slug of the parent navigation item.
 *
 * @return bool Returns false on failure, ie if the nav item can't be found.
 */
function bp_core_remove_nav_item( $parent_id ) {
	$bp = buddypress();

	// Unset subnav items for this nav item
	if ( isset( $bp->bp_options_nav[$parent_id] ) && is_array( $bp->bp_options_nav[$parent_id] ) ) {
		foreach( (array) $bp->bp_options_nav[$parent_id] as $subnav_item ) {
			bp_core_remove_subnav_item( $parent_id, $subnav_item['slug'] );
		}
	}

	if ( empty( $bp->bp_nav[ $parent_id ] ) )
		return false;

	if ( $function = $bp->bp_nav[$parent_id]['screen_function'] ) {
		// Remove our screen hook if screen function is callable
		if ( is_callable( $function ) ) {
			remove_action( 'bp_screens', $function, 3 );
		}
	}

	unset( $bp->bp_nav[$parent_id] );
}

/**
 * Remove a subnav item from the navigation array.
 *
 * @param string $parent_id The slug of the parent navigation item.
 * @param string $slug      The slug of the subnav item to be removed.
 */
function bp_core_remove_subnav_item( $parent_id, $slug ) {
	$bp = buddypress();

	$screen_function = isset( $bp->bp_options_nav[$parent_id][$slug]['screen_function'] )
		? $bp->bp_options_nav[$parent_id][$slug]['screen_function']
		: false;

	if ( ! empty( $screen_function ) ) {
		// Remove our screen hook if screen function is callable
		if ( is_callable( $screen_function ) ) {
			remove_action( 'bp_screens', $screen_function, 3 );
		}
	}

	unset( $bp->bp_options_nav[$parent_id][$slug] );

	if ( isset( $bp->bp_options_nav[$parent_id] ) && !count( $bp->bp_options_nav[$parent_id] ) )
		unset($bp->bp_options_nav[$parent_id]);
}

/**
 * Clear all subnav items from a specific nav item.
 *
 * @param string $parent_slug The slug of the parent navigation item.
 */
function bp_core_reset_subnav_items( $parent_slug ) {
	$bp = buddypress();

	unset( $bp->bp_options_nav[$parent_slug] );
}


/**
 * Retrieve the Toolbar display preference of a user based on context.
 *
 * This is a direct copy of WP's private _get_admin_bar_pref()
 *
 * @since 1.5.0
 *
 * @uses get_user_option()
 *
 * @param string $context Context of this preference check. 'admin' or 'front'.
 * @param int    $user    Optional. ID of the user to check. Default: 0 (which falls back to the logged-in user's ID).
 *
 * @return bool True if the toolbar should be showing for this user.
 */
function bp_get_admin_bar_pref( $context, $user = 0 ) {
	$pref = get_user_option( "show_admin_bar_{$context}", $user );
	if ( false === $pref )
		return true;

	return 'true' === $pref;
}
