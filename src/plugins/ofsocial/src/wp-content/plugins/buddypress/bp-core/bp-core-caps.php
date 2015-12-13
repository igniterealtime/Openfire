<?php
/**
 * BuddyPress Capabilities.
 *
 * @package BuddyPress
 * @subpackage Capabilities
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Return an array of roles from the currently loaded blog.
 *
 * WordPress roles are dynamically flipped when calls to switch_to_blog() and
 * restore_current_blog() are made, so we use and trust WordPress core to have
 * loaded the correct results for us here. As enhancements are made to
 * WordPress's RBAC, so should our capability functions here.
 *
 * @since 2.1.0
 *
 * @return array
 */
function bp_get_current_blog_roles() {
	global $wp_roles;

	// Sanity check on roles global variable
	$roles = isset( $wp_roles->roles )
		? $wp_roles->roles
		: array();

	/**
	 * Filters the list of editable roles.
	 *
	 * @since 2.1.0
	 *
	 * @param array $roles List of roles.
	 */
	$roles = apply_filters( 'editable_roles', $roles );

	/**
	 * Filters the array of roles from the currently loaded blog.
	 *
	 * @since 2.1.0
	 *
	 * @param array    $roles    Available roles.
	 * @param WP_Roles $wp_roles Object of WordPress roles.
	 */
	return apply_filters( 'bp_get_current_blog_roles', $roles, $wp_roles );
}

/**
 * Add capabilities to WordPress user roles.
 *
 * This is called on plugin activation.
 *
 * @since 1.6.0
 *
 * @uses get_role() To get the administrator, default and moderator roles.
 * @uses WP_Role::add_cap() To add various capabilities.
 * @uses do_action() Calls 'bp_add_caps'.
 */
function bp_add_caps() {
	global $wp_roles;

	// Load roles if not set
	if ( ! isset( $wp_roles ) ) {
		$wp_roles = new WP_Roles();
	}

	// Loop through available roles and add them
	foreach( $wp_roles->role_objects as $role ) {
		foreach ( bp_get_caps_for_role( $role->name ) as $cap ) {
			$role->add_cap( $cap );
		}
	}

	/**
	 * Fires after the addition of capabilities to WordPress user roles.
	 *
	 * This is called on plugin activation.
	 *
	 * @since 1.6.0
	 */
	do_action( 'bp_add_caps' );
}

/**
 * Remove capabilities from WordPress user roles.
 *
 * This is called on plugin deactivation.
 *
 * @since 1.6.0
 *
 * @uses get_role() To get the administrator and default roles.
 * @uses WP_Role::remove_cap() To remove various capabilities.
 * @uses do_action() Calls 'bp_remove_caps'.
 */
function bp_remove_caps() {
	global $wp_roles;

	// Load roles if not set
	if ( ! isset( $wp_roles ) ) {
		$wp_roles = new WP_Roles();
	}

	// Loop through available roles and remove them
	foreach( $wp_roles->role_objects as $role ) {
		foreach ( bp_get_caps_for_role( $role->name ) as $cap ) {
			$role->remove_cap( $cap );
		}
	}

	/**
	 * Fires after the removal of capabilities from WordPress user roles.
	 *
	 * This is called on plugin deactivation.
	 *
	 * @since 1.6.0
	 */
	do_action( 'bp_remove_caps' );
}

/**
 * Map community caps to built in WordPress caps.
 *
 * @since 1.6.0
 *
 * @see WP_User::has_cap() for description of the arguments passed to the
 *      'map_meta_cap' filter.
 * @uses apply_filters() Calls 'bp_map_meta_caps' with caps, cap, user ID and
 *       args.
 *
 * @param array  $caps    See {@link WP_User::has_cap()}.
 * @param string $cap     See {@link WP_User::has_cap()}.
 * @param int    $user_id See {@link WP_User::has_cap()}.
 * @param mixed  $args    See {@link WP_User::has_cap()}.
 *
 * @return array Actual capabilities for meta capability. See {@link WP_User::has_cap()}.
 */
function bp_map_meta_caps( $caps, $cap, $user_id, $args ) {

	/**
	 * Filters the community caps mapping to be built in WordPress caps.
	 *
	 * @since 1.6.0
	 *
	 * @param array  $caps    Returns the user's actual capabilities.
	 * @param string $cap     Capability name.
	 * @param int    $user_id The user ID.
	 * @param array  $args    Adds the context to the cap. Typically the object ID.
	 */
	return apply_filters( 'bp_map_meta_caps', $caps, $cap, $user_id, $args );
}

/**
 * Return community capabilities.
 *
 * @since 1.6.0
 *
 * @uses apply_filters() Calls 'bp_get_community_caps' with the capabilities.
 *
 * @return array Community capabilities.
 */
function bp_get_community_caps() {

	// Forum meta caps
	$caps = array();

	/**
	 * Filters community capabilities.
	 *
	 * @since 1.6.0
	 *
	 * @param array $caps Array of capabilities to add. Empty by default.
	 */
	return apply_filters( 'bp_get_community_caps', $caps );
}

/**
 * Return an array of capabilities based on the role that is being requested.
 *
 * @since 1.6.0
 *
 * @uses apply_filters() Allow return value to be filtered.
 *
 * @param string $role The role for which you're loading caps.
 *
 * @return array Capabilities for $role.
 */
function bp_get_caps_for_role( $role = '' ) {

	// Which role are we looking for?
	switch ( $role ) {

		// Administrator
		case 'administrator' :
			$caps = array(
				// Misc
				'bp_moderate',
			);

			break;

		// All other default WordPress blog roles
		case 'editor'      :
		case 'author'      :
		case 'contributor' :
		case 'subscriber'  :
		default            :
			$caps = array();
			break;
	}

	/**
	 * Filters the array of capabilities based on the role that is being requested.
	 *
	 * @since 1.6.0
	 *
	 * @param array  $caps Array of capabilities to return.
	 * @param string $role The role currently being loaded.
	 */
	return apply_filters( 'bp_get_caps_for_role', $caps, $role );
}

/**
 * Set a default role for the current user.
 *
 * Give a user the default role when creating content on a site they do not
 * already have a role or capability on.
 *
 * @since 1.6.0
 *
 * @global BuddyPress $bp Global BuddyPress settings object.
 *
 * @uses is_multisite()
 * @uses bp_allow_global_access()
 * @uses bp_is_user_inactive()
 * @uses is_user_logged_in()
 * @uses current_user_can()
 * @uses WP_User::set_role()
 */
function bp_set_current_user_default_role() {

	// Bail if not multisite or not root blog
	if ( ! is_multisite() || ! bp_is_root_blog() ) {
		return;
	}

	// Bail if user is not logged in or already a member
	if ( ! is_user_logged_in() || is_user_member_of_blog() ) {
		return;
	}

	// Bail if user is not active
	if ( bp_is_user_inactive() ) {
		return;
	}

	// Set the current users default role
	buddypress()->current_user->set_role( bp_get_option( 'default_role', 'subscriber' ) );
}

/**
 * Check whether the current user has a given capability.
 *
 * @since 1.6.0
 * @since 2.4.0 Second argument modified to accept an array, rather than `$blog_id`.
 *
 * @param string    $capability Capability or role name.
 * @param array|int $args {
 *     Array of extra arguments applicable to the capability check.
 *     @type int   $blog_id Optional. Blog ID. Defaults to the BP root blog.
 *     @type mixed $a,...   Optional. Extra arguments applicable to the capability check.
 * }
 *
 * @return bool True if the user has the cap for the given parameters.
 */
function bp_current_user_can( $capability, $args = array() ) {
	$blog_id = 0;

	// Backward compatibility for older $blog_id parameter.
	if ( is_int( $args ) ) {
		$blog_id = $args;
		$args = array();

	// New format for second parameter.
	} elseif ( is_array( $args ) && isset( $args['blog_id'] ) ) {
		// Get the blog ID if set, but don't pass along to `current_user_can_for_blog()`.
		$blog_id = (int) $args['blog_id'];
		unset( $args['blog_id'] );
	}

	// Backward compatibility for older bp_current_user_can() checks
	if ( empty( $args ) ) {
		$args = null;
	}

	// Use root blog if no ID passed
	if ( empty( $blog_id ) ) {
		$blog_id = bp_get_root_blog_id();
	}

	$args   = array( $blog_id, $capability, $args );
	$retval = call_user_func_array( 'current_user_can_for_blog', $args );

	/**
	 * Filters whether or not the current user has a given capability.
	 *
	 * @since 1.6.0
	 * @since 2.4.0 Pass `$args` variable.
	 *
	 * @param bool   $retval     Whether or not the current user has the capability.
	 * @param string $capability The capability being checked for.
	 * @param int    $blog_id    Blog ID. Defaults to the BP root blog.
	 * @param array  $args       Array of extra arguments passed.
	 */
	return (bool) apply_filters( 'bp_current_user_can', $retval, $capability, $blog_id, $args );
}

/**
 * Temporary implementation of 'bp_moderate' cap.
 *
 * In BuddyPress 1.6, the 'bp_moderate' cap was introduced. In order to
 * enforce that bp_current_user_can( 'bp_moderate' ) always returns true for
 * Administrators, we must manually add the 'bp_moderate' cap to the list of
 * user caps for Admins.
 *
 * Note that this level of enforcement is only necessary in the case of
 * non-Multisite. This is because WordPress automatically assigns every
 * capability - and thus 'bp_moderate' - to Super Admins on a Multisite
 * installation. See {@link WP_User::has_cap()}.
 *
 * This implementation of 'bp_moderate' is temporary, until BuddyPress properly
 * matches caps to roles and stores them in the database.
 *
 * Plugin authors: Please do not use this function; thank you. :)
 *
 * @access private
 * @since 1.6.0
 *
 * @see WP_User::has_cap()
 *
 * @param array  $caps    The caps that WP associates with the given role.
 * @param string $cap     The caps being tested for in WP_User::has_cap().
 * @param int    $user_id ID of the user being checked against.
 * @param array  $args    Miscellaneous arguments passed to the user_has_cap filter.
 *
 * @return array $allcaps The user's cap list, with 'bp_moderate' appended, if relevant.
 */
function _bp_enforce_bp_moderate_cap_for_admins( $caps = array(), $cap = '', $user_id = 0, $args = array() ) {

	// Bail if not checking the 'bp_moderate' cap
	if ( 'bp_moderate' !== $cap ) {
		return $caps;
	}

	// Bail if BuddyPress is not network activated
	if ( bp_is_network_activated() ) {
		return $caps;
	}

	// Never trust inactive users
	if ( bp_is_user_inactive( $user_id ) ) {
		return $caps;
	}

	// Only users that can 'manage_options' on this site can 'bp_moderate'
	return array( 'manage_options' );
}
add_filter( 'map_meta_cap', '_bp_enforce_bp_moderate_cap_for_admins', 10, 4 );

/** Deprecated ****************************************************************/

/**
 * Adds BuddyPress-specific user roles.
 *
 * This is called on plugin activation.
 *
 * @since 1.6.0
 * @deprecated 1.7.0
 */
function bp_add_roles() {
	_doing_it_wrong( 'bp_add_roles', __( 'Special community roles no longer exist. Use mapped capabilities instead', 'buddypress' ), '1.7' );
}

/**
 * Removes BuddyPress-specific user roles.
 *
 * This is called on plugin deactivation.
 *
 * @since 1.6.0
 * @deprecated 1.7.0
 */
function bp_remove_roles() {
	_doing_it_wrong( 'bp_remove_roles', __( 'Special community roles no longer exist. Use mapped capabilities instead', 'buddypress' ), '1.7' );
}


/**
 * The participant role for registered users without roles.
 *
 * This is primarily for multisite compatibility when users without roles on
 * sites that have global communities enabled.
 *
 * @since 1.6.0
 * @deprecated 1.7.0
 */
function bp_get_participant_role() {
	_doing_it_wrong( 'bp_get_participant_role', __( 'Special community roles no longer exist. Use mapped capabilities instead', 'buddypress' ), '1.7' );
}

/**
 * The moderator role for BuddyPress users.
 *
 * @since 1.6.0
 * @deprecated 1.7.0
 */
function bp_get_moderator_role() {
	_doing_it_wrong( 'bp_get_moderator_role', __( 'Special community roles no longer exist. Use mapped capabilities instead', 'buddypress' ), '1.7' );
}
