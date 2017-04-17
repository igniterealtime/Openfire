<?php
/**
 * BuddyPress Filters.
 *
 * This file contains the filters that are used throughout BuddyPress. They are
 * consolidated here to make searching for them easier, and to help developers
 * understand at a glance the order in which things occur.
 *
 * There are a few common places that additional filters can currently be found.
 *
 *  - BuddyPress: In {@link BuddyPress::setup_actions()} in buddypress.php
 *  - Component: In {@link BP_Component::setup_actions()} in
 *                bp-core/bp-core-component.php
 *  - Admin: More in {@link BP_Admin::setup_actions()} in
 *            bp-core/bp-core-admin.php
 *
 * @package BuddyPress
 * @subpackage Core
 * @see bp-core-actions.php
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Attach BuddyPress to WordPress.
 *
 * BuddyPress uses its own internal actions to help aid in third-party plugin
 * development, and to limit the amount of potential future code changes when
 * updates to WordPress core occur.
 *
 * These actions exist to create the concept of 'plugin dependencies'. They
 * provide a safe way for plugins to execute code *only* when BuddyPress is
 * installed and activated, without needing to do complicated guesswork.
 *
 * For more information on how this works, see the 'Plugin Dependency' section
 * near the bottom of this file.
 *
 *           v--WordPress Actions       v--BuddyPress Sub-actions
 */
add_filter( 'request',                 'bp_request',             10    );
add_filter( 'template_include',        'bp_template_include',    10    );
add_filter( 'login_redirect',          'bp_login_redirect',      10, 3 );
add_filter( 'map_meta_cap',            'bp_map_meta_caps',       10, 4 );

// Add some filters to feedback messages
add_filter( 'bp_core_render_message_content', 'wptexturize'       );
add_filter( 'bp_core_render_message_content', 'convert_smilies'   );
add_filter( 'bp_core_render_message_content', 'convert_chars'     );
add_filter( 'bp_core_render_message_content', 'wpautop'           );
add_filter( 'bp_core_render_message_content', 'shortcode_unautop' );
add_filter( 'bp_core_render_message_content', 'wp_kses_data', 5   );

/**
 * Template Compatibility.
 *
 * If you want to completely bypass this and manage your own custom BuddyPress
 * template hierarchy, start here by removing this filter, then look at how
 * bp_template_include() works and do something similar. :)
 */
add_filter( 'bp_template_include',   'bp_template_include_theme_supports', 2, 1 );
add_filter( 'bp_template_include',   'bp_template_include_theme_compat',   4, 2 );

// Filter BuddyPress template locations
add_filter( 'bp_get_template_stack', 'bp_add_template_stack_locations' );

// Turn comments off for BuddyPress pages
add_filter( 'comments_open', 'bp_comments_open', 10, 2 );

/**
 * Prevent specific pages (eg 'Activate') from showing on page listings.
 *
 * @uses bp_is_active() checks if a BuddyPress component is active.
 *
 * @param array $pages List of excluded page IDs, as passed to the
 *                     'wp_list_pages_excludes' filter.
 *
 * @return array The exclude list, with BP's pages added.
 */
function bp_core_exclude_pages( $pages = array() ) {

	// Bail if not the root blog
	if ( ! bp_is_root_blog() )
		return $pages;

	$bp = buddypress();

	if ( !empty( $bp->pages->activate ) )
		$pages[] = $bp->pages->activate->id;

	if ( !empty( $bp->pages->register ) )
		$pages[] = $bp->pages->register->id;

	if ( !empty( $bp->pages->forums ) && ( !bp_is_active( 'forums' ) || ( bp_is_active( 'forums' ) && bp_forums_has_directory() && !bp_forums_is_installed_correctly() ) ) )
		$pages[] = $bp->pages->forums->id;

	/**
	 * Filters specific pages that shouldn't show up on page listings.
	 *
	 * @since 1.5.0
	 *
	 * @param array $pages Array of pages to exclude.
	 */
	return apply_filters( 'bp_core_exclude_pages', $pages );
}
add_filter( 'wp_list_pages_excludes', 'bp_core_exclude_pages' );

/**
 * Prevent specific pages (eg 'Activate') from showing in the Pages meta box of the Menu Administration screen.
 *
 * @since 2.0.0
 *
 * @uses bp_is_root_blog() checks if current blog is root blog.
 * @uses buddypress() gets BuddyPress main instance
 *
 * @param object|null $object The post type object used in the meta box.
 *
 * @return object|null The $object, with a query argument to remove register and activate pages id.
 */
function bp_core_exclude_pages_from_nav_menu_admin( $object = null ) {

	// Bail if not the root blog
	if ( ! bp_is_root_blog() ) {
		return $object;
	}

	if ( 'page' != $object->name ) {
		return $object;
	}

	$bp = buddypress();
	$pages = array();

	if ( ! empty( $bp->pages->activate ) ) {
		$pages[] = $bp->pages->activate->id;
	}

	if ( ! empty( $bp->pages->register ) ) {
		$pages[] = $bp->pages->register->id;
	}

	if ( ! empty( $pages ) ) {
		$object->_default_query['post__not_in'] = $pages;
	}

	return $object;
}
add_filter( 'nav_menu_meta_box_object', 'bp_core_exclude_pages_from_nav_menu_admin', 11, 1 );

/**
 * Adds current page CSS classes to the parent BP page in a WP Page Menu.
 *
 * Because BuddyPress primarily uses virtual pages, we need a way to highlight
 * the BP parent page during WP menu generation.  This function checks the
 * current BP component against the current page in the WP menu to see if we
 * should highlight the WP page.
 *
 * @since 2.2.0
 *
 * @param array   $retval CSS classes for the current menu page in the menu.
 * @param WP_Post $page   The page properties for the current menu item.
 *
 * @return array
 */
function bp_core_menu_highlight_parent_page( $retval, $page ) {
	if ( ! is_buddypress() ) {
		return $retval;
	}

	$page_id = false;

	// loop against all BP component pages
	foreach ( (array) buddypress()->pages as $component => $bp_page ) {
		// handles the majority of components
		if ( bp_is_current_component( $component ) ) {
	                $page_id = (int) $bp_page->id;
		}

		// stop if not on a user page
		if ( ! bp_is_user() && ! empty( $page_id ) ) {
			break;
		}

		// members component requires an explicit check due to overlapping components
		if ( bp_is_user() && 'members' === $component ) {
			$page_id = (int) $bp_page->id;
			break;
		}
	}

	// duplicate some logic from Walker_Page::start_el() to highlight menu items
	if ( ! empty( $page_id ) ) {
		$_bp_page = get_post( $page_id );
		if ( in_array( $page->ID, $_bp_page->ancestors, true ) ) {
			$retval[] = 'current_page_ancestor';
		}
		if ( $page->ID === $page_id ) {
			$retval[] = 'current_page_item';
		} elseif ( $_bp_page && $page->ID === $_bp_page->post_parent ) {
			$retval[] = 'current_page_parent';
		}
	}

	$retval = array_unique( $retval );

	return $retval;
}
add_filter( 'page_css_class', 'bp_core_menu_highlight_parent_page', 10, 2 );

/**
 * Adds current page CSS classes to the parent BP page in a WP Nav Menu.
 *
 * When {@link wp_nav_menu()} is used, this function helps to highlight the
 * current BP parent page during nav menu generation.
 *
 * @since 2.2.0
 *
 * @param array   $retval CSS classes for the current nav menu item in the menu.
 * @param WP_Post $item   The properties for the current nav menu item.
 *
 * @return array
 */
function bp_core_menu_highlight_nav_menu_item( $retval, $item ) {
	// If we're not on a BP page or if the current nav item is not a page, stop!
	if ( ! is_buddypress() || 'page' !== $item->object ) {
		return $retval;
	}

	// get the WP page
	$page   = get_post( $item->object_id );

	// see if we should add our highlight CSS classes for the page
	$retval = bp_core_menu_highlight_parent_page( $retval, $page );

	return $retval;
}
add_filter( 'nav_menu_css_class', 'bp_core_menu_highlight_nav_menu_item', 10, 2 );

/**
 * Set "From" name in outgoing email to the site name.
 *
 * @uses bp_get_option() fetches the value for a meta_key in the wp_X_options table.
 *
 * @return string The blog name for the root blog.
 */
function bp_core_email_from_name_filter() {

	/**
	 * Filters the "From" name in outgoing email to the site name.
	 *
	 * @since 1.2.0
	 *
	 * @param string $value Value to set the "From" name to.
	 */
 	return apply_filters( 'bp_core_email_from_name_filter', bp_get_option( 'blogname', 'WordPress' ) );
}
add_filter( 'wp_mail_from_name', 'bp_core_email_from_name_filter' );

/**
 * Filter the blog post comments array and insert BuddyPress URLs for users.
 *
 * @param array $comments The array of comments supplied to the comments template.
 * @param int   $post_id  The post ID.
 *
 * @return array $comments The modified comment array.
 */
function bp_core_filter_comments( $comments, $post_id ) {
	global $wpdb;

	foreach( (array) $comments as $comment ) {
		if ( $comment->user_id )
			$user_ids[] = $comment->user_id;
	}

	if ( empty( $user_ids ) )
		return $comments;

	$user_ids = implode( ',', wp_parse_id_list( $user_ids ) );

	if ( !$userdata = $wpdb->get_results( "SELECT ID as user_id, user_login, user_nicename FROM {$wpdb->users} WHERE ID IN ({$user_ids})" ) )
		return $comments;

	foreach( (array) $userdata as $user )
		$users[$user->user_id] = bp_core_get_user_domain( $user->user_id, $user->user_nicename, $user->user_login );

	foreach( (array) $comments as $i => $comment ) {
		if ( !empty( $comment->user_id ) ) {
			if ( !empty( $users[$comment->user_id] ) )
				$comments[$i]->comment_author_url = $users[$comment->user_id];
		}
	}

	return $comments;
}
add_filter( 'comments_array', 'bp_core_filter_comments', 10, 2 );

/**
 * When a user logs in, redirect him in a logical way.
 *
 * @uses apply_filters() Filter 'bp_core_login_redirect' to modify where users
 *       are redirected to on login.
 *
 * @param string  $redirect_to     The URL to be redirected to, sanitized in wp-login.php.
 * @param string  $redirect_to_raw The unsanitized redirect_to URL ($_REQUEST['redirect_to']).
 * @param WP_User $user            The WP_User object corresponding to a successfully
 *                                 logged-in user. Otherwise a WP_Error object.
 *
 * @return string The redirect URL.
 */
function bp_core_login_redirect( $redirect_to, $redirect_to_raw, $user ) {

	// Only modify the redirect if we're on the main BP blog
	if ( !bp_is_root_blog() ) {
		return $redirect_to;
	}

	// Only modify the redirect once the user is logged in
	if ( !is_a( $user, 'WP_User' ) ) {
		return $redirect_to;
	}

	/**
	 * Filters whether or not to redirect.
	 *
	 * Allows plugins to have finer grained control of redirect upon login.
	 *
	 * @since 1.6.0
	 *
  * @param bool    $value           Whether or not to redirect.
	 * @param string  $redirect_to     Sanitized URL to be redirected to.
	 * @param string  $redirect_to_raw Unsanitized URL to be redirected to.
	 * @param WP_User $user            The WP_User object corresponding to a
	 *                                 successfully logged in user.
	 */
	$maybe_redirect = apply_filters( 'bp_core_login_redirect', false, $redirect_to, $redirect_to_raw, $user );
	if ( false !== $maybe_redirect ) {
		return $maybe_redirect;
	}

	// If a 'redirect_to' parameter has been passed that contains 'wp-admin', verify that the
	// logged-in user has any business to conduct in the Dashboard before allowing the
	// redirect to go through
	if ( !empty( $redirect_to ) && ( false === strpos( $redirect_to, 'wp-admin' ) || user_can( $user, 'edit_posts' ) ) ) {
		return $redirect_to;
	}

	if ( false === strpos( wp_get_referer(), 'wp-login.php' ) && false === strpos( wp_get_referer(), 'activate' ) && empty( $_REQUEST['nr'] ) ) {
		return wp_get_referer();
	}

	/**
	 * Filters the URL to redirect users to upon successful login.
	 *
	 * @since 1.9.0
	 *
	 * @param string $value URL to redirect to.
	 */
	return apply_filters( 'bp_core_login_redirect_to', bp_get_root_domain() );
}
add_filter( 'bp_login_redirect', 'bp_core_login_redirect', 10, 3 );

/**
 * Replace the generated password in the welcome email with '[User Set]'.
 *
 * On a standard BP installation, users who register themselves also set their
 * own passwords. Therefore there is no need for the insecure practice of
 * emailing the plaintext password to the user in the welcome email.
 *
 * This filter will not fire when a user is registered by the site admin.
 *
 * @param string $welcome_email Complete email passed through WordPress.
 *
 * @return string Filtered $welcome_email with the password replaced
 *                by '[User Set]'.
 */
function bp_core_filter_user_welcome_email( $welcome_email ) {

	// Don't touch the email when a user is registered by the site admin
	if ( ( is_admin() || is_network_admin() ) && buddypress()->members->admin->signups_page != get_current_screen()->id ) {
		return $welcome_email;
	}

	if ( strpos( bp_get_requested_url(), 'wp-activate.php' ) !== false ) {
		return $welcome_email;
	}

	// Don't touch the email if we don't have a custom registration template
	if ( ! bp_has_custom_signup_page() ) {
		return $welcome_email;
	}

	// [User Set] Replaces 'PASSWORD' in welcome email; Represents value set by user
	return str_replace( 'PASSWORD', __( '[User Set]', 'buddypress' ), $welcome_email );
}
add_filter( 'update_welcome_user_email', 'bp_core_filter_user_welcome_email' );

/**
 * Replace the generated password in the welcome email with '[User Set]'.
 *
 * On a standard BP installation, users who register themselves also set their
 * own passwords. Therefore there is no need for the insecure practice of
 * emailing the plaintext password to the user in the welcome email.
 *
 * This filter will not fire when a user is registered by the site admin.
 *
 * @param string $welcome_email Complete email passed through WordPress.
 * @param int    $blog_id       ID of the blog user is joining.
 * @param int    $user_id       ID of the user joining.
 * @param string $password      Password of user.
 *
 * @return string Filtered $welcome_email with $password replaced by '[User Set]'.
 */
function bp_core_filter_blog_welcome_email( $welcome_email, $blog_id, $user_id, $password ) {

	// Don't touch the email when a user is registered by the site admin
	if ( ( is_admin() || is_network_admin() ) && buddypress()->members->admin->signups_page != get_current_screen()->id ) {
		return $welcome_email;
	}

	// Don't touch the email if we don't have a custom registration template
	if ( ! bp_has_custom_signup_page() )
		return $welcome_email;

	// [User Set] Replaces $password in welcome email; Represents value set by user
	return str_replace( $password, __( '[User Set]', 'buddypress' ), $welcome_email );
}
add_filter( 'update_welcome_email', 'bp_core_filter_blog_welcome_email', 10, 4 );

/**
 * Notify new users of a successful registration (with blog).
 *
 * This function filter's WP's 'wpmu_signup_blog_notification', and replaces
 * WP's default welcome email with a BuddyPress-specific message.
 *
 * @see wpmu_signup_blog_notification() for a description of parameters.
 *
 * @param string $domain     The new blog domain.
 * @param string $path       The new blog path.
 * @param string $title      The site title.
 * @param string $user       The user's login name.
 * @param string $user_email The user's email address.
 * @param string $key        The activation key created in wpmu_signup_blog()
 * @param array  $meta       By default, contains the requested privacy setting and
 *                           lang_id.
 *
 * @return bool True on success, false on failure.
 */
function bp_core_activation_signup_blog_notification( $domain, $path, $title, $user, $user_email, $key, $meta ) {

	// Set up activation link
	$activate_url = bp_get_activation_page() ."?key=$key";
	$activate_url = esc_url( $activate_url );

	// Email contents
	$message = sprintf( __( "%1\$s,\n\n\n\nThanks for registering! To complete the activation of your account and blog, please click the following link:\n\n%2\$s\n\n\n\nAfter you activate, you can visit your blog here:\n\n%3\$s", 'buddypress' ), $user, $activate_url, esc_url( "http://{$domain}{$path}" ) );
	$subject = bp_get_email_subject( array( 'text' => sprintf( __( 'Activate %s', 'buddypress' ), 'http://' . $domain . $path ) ) );

	/**
	 * Filters the email that the notification is going to upon successful registration with blog.
	 *
	 * @since 1.2.0
	 *
	 * @param string $user_email The user's email address.
	 * @param string $domain     The new blog domain.
	 * @param string $path       The new blog path.
	 * @param string $title      The site title.
	 * @param string $user       The user's login name.
	 * @param string $user_email The user's email address.
	 * @param string $key        The activation key created in wpmu_signup_blog().
	 * @param array  $meta       Array of meta values for the created site.
	 */
	$to      = apply_filters( 'bp_core_activation_signup_blog_notification_to',   $user_email, $domain, $path, $title, $user, $user_email, $key, $meta );

	/**
	 * Filters the subject that the notification uses upon successful registration with blog.
	 *
	 * @since 1.2.0
	 *
	 * @param string $subject    The subject to use.
	 * @param string $domain     The new blog domain.
	 * @param string $path       The new blog path.
	 * @param string $title      The site title.
	 * @param string $user       The user's login name.
	 * @param string $user_email The user's email address.
	 * @param string $key        The activation key created in wpmu_signup_blog().
	 * @param array  $meta       Array of meta values for the created site.
	 */
	$subject = apply_filters( 'bp_core_activation_signup_blog_notification_subject', $subject, $domain, $path, $title, $user, $user_email, $key, $meta );

	/**
	 * Filters the message that the notification uses upon successful registration with blog.
	 *
	 * @since 1.2.0
	 *
	 * @param string $message    The message to use.
	 * @param string $domain     The new blog domain.
	 * @param string $path       The new blog path.
	 * @param string $title      The site title.
	 * @param string $user       The user's login name.
	 * @param string $user_email The user's email address.
	 * @param string $key        The activation key created in wpmu_signup_blog().
	 * @param array  $meta       Array of meta values for the created site.
	 */
	$message = apply_filters( 'bp_core_activation_signup_blog_notification_message', $message, $domain, $path, $title, $user, $user_email, $key, $meta );

	// Send the email
	wp_mail( $to, $subject, $message );

	// Set up the $admin_email to pass to the filter
	$admin_email = bp_get_option( 'admin_email' );

	/**
	 * Fires after the sending of the notification to new users for successful registration with blog.
	 *
	 * @since 1.5.0
	 *
	 * @param string $admin_email Admin Email address for the site.
	 * @param string $subject     Subject used in the notification email.
	 * @param string $message     Message used in the notification email.
	 * @param string $domain      The new blog domain.
	 * @param string $path        The new blog path.
	 * @param string $title       The site title.
	 * @param string $user        The user's login name.
	 * @param string $user_email  The user's email address.
	 * @param string $key         The activation key created in wpmu_signup_blog().
	 * @param array  $meta        Array of meta values for the created site.
	 */
	do_action( 'bp_core_sent_blog_signup_email', $admin_email, $subject, $message, $domain, $path, $title, $user, $user_email, $key, $meta );

	// Return false to stop the original WPMU function from continuing
	return false;
}
add_filter( 'wpmu_signup_blog_notification', 'bp_core_activation_signup_blog_notification', 1, 7 );

/**
 * Notify new users of a successful registration (without blog).
 *
 * @see wpmu_signup_user_notification() for a full description of params.
 *
 * @param string $user       The user's login name.
 * @param string $user_email The user's email address.
 * @param string $key        The activation key created in wpmu_signup_user()
 * @param array  $meta       By default, an empty array.
 *
 * @return bool|string True on success, false on failure.
 */
function bp_core_activation_signup_user_notification( $user, $user_email, $key, $meta ) {

	if ( is_admin() ) {
		// If the user is created from the WordPress Add User screen, don't send BuddyPress signup notifications
		if( in_array( get_current_screen()->id, array( 'user', 'user-network' ) ) ) {
			// If the Super Admin want to skip confirmation email
			if ( isset( $_POST[ 'noconfirmation' ] ) && is_super_admin() ) {
				return false;

			// WordPress will manage the signup process
			} else {
				return $user;
			}

		/**
		 * There can be a case where the user was created without the skip confirmation
		 * And the super admin goes in pending accounts to resend it. In this case, as the
		 * meta['password'] is not set, the activation url must be WordPress one
		 */
		} elseif ( buddypress()->members->admin->signups_page == get_current_screen()->id ) {
			$is_hashpass_in_meta = maybe_unserialize( $meta );

			if ( empty( $is_hashpass_in_meta['password'] ) ) {
				return $user;
			}
		}
	}

	// Set up activation link
	$activate_url = bp_get_activation_page() . "?key=$key";
	$activate_url = esc_url( $activate_url );

	// Email contents
	$message = sprintf( __( "Thanks for registering! To complete the activation of your account please click the following link:\n\n%1\$s\n\n", 'buddypress' ), $activate_url );
	$subject = bp_get_email_subject( array( 'text' => __( 'Activate Your Account', 'buddypress' ) ) );

	/**
	 * Filters the email that the notification is going to upon successful registration without blog.
	 *
	 * @since 1.2.0
	 *
	 * @param string $user_email The user's email address.
	 * @param string $user       The user's login name.
	 * @param string $user_email The user's email address.
	 * @param string $key        The activation key created in wpmu_signup_blog().
	 * @param array  $meta       Array of meta values for the created site.
	 */
	$to      = apply_filters( 'bp_core_activation_signup_user_notification_to',   $user_email, $user, $user_email, $key, $meta );

	/**
	 * Filters the subject that the notification uses upon successful registration without blog.
	 *
	 * @since 1.2.0
	 *
	 * @param string $subject    The subject to use.
	 * @param string $user       The user's login name.
	 * @param string $user_email The user's email address.
	 * @param string $key        The activation key created in wpmu_signup_blog().
	 * @param array  $meta       Array of meta values for the created site.
	 */
	$subject = apply_filters( 'bp_core_activation_signup_user_notification_subject', $subject, $user, $user_email, $key, $meta );

	/**
	 * Filters the message that the notification uses upon successful registration without blog.
	 *
	 * @since 1.2.0
	 *
	 * @param string $message    The message to use.
	 * @param string $user       The user's login name.
	 * @param string $user_email The user's email address.
	 * @param string $key        The activation key created in wpmu_signup_blog().
	 * @param array  $meta       Array of meta values for the created site.
	 */
	$message = apply_filters( 'bp_core_activation_signup_user_notification_message', $message, $user, $user_email, $key, $meta );

	// Send the email
	wp_mail( $to, $subject, $message );

	// Set up the $admin_email to pass to the filter
	$admin_email = bp_get_option( 'admin_email' );

	/**
	 * Fires after the sending of the notification to new users for successful registration without blog.
	 *
	 * @since 1.5.0
	 *
	 * @param string $admin_email Admin Email address for the site.
	 * @param string $subject     Subject used in the notification email.
	 * @param string $message     Message used in the notification email.
	 * @param string $user        The user's login name.
	 * @param string $user_email  The user's email address.
	 * @param string $key         The activation key created in wpmu_signup_blog().
	 * @param array  $meta        Array of meta values for the created site. Default empty array.
	 */
	do_action( 'bp_core_sent_user_signup_email', $admin_email, $subject, $message, $user, $user_email, $key, $meta );

	// Return false to stop the original WPMU function from continuing
	return false;
}
add_filter( 'wpmu_signup_user_notification', 'bp_core_activation_signup_user_notification', 1, 4 );

/**
 * Filter the page title for BuddyPress pages.
 *
 * @since 1.5.0
 *
 * @see wp_title()
 * @global object $bp BuddyPress global settings.
 *
 * @param  string $title       Original page title.
 * @param  string $sep         How to separate the various items within the page title.
 * @param  string $seplocation Direction to display title.
 *
 * @return string              New page title.
 */
function bp_modify_page_title( $title = '', $sep = '&raquo;', $seplocation = 'right' ) {
	global $bp, $paged, $page, $_wp_theme_features;

	// If this is not a BP page, just return the title produced by WP
	if ( bp_is_blog_page() ) {
		return $title;
	}

	// If this is a 404, let WordPress handle it
	if ( is_404() ) {
		return $title;
	}

	// If this is the front page of the site, return WP's title
	if ( is_front_page() || is_home() ) {
		return $title;
	}

	// Return WP's title if not a BuddyPress page
	if ( ! is_buddypress() ) {
		return $title;
	}

	// Setup an empty title parts array
	$title_parts = array();

	// Is there a displayed user, and do they have a name?
	$displayed_user_name = bp_get_displayed_user_fullname();

	// Displayed user
	if ( ! empty( $displayed_user_name ) && ! is_404() ) {

		// Get the component's ID to try and get its name
		$component_id = $component_name = bp_current_component();

		// Set empty subnav name
		$component_subnav_name = '';

		// Use the component nav name
		if ( ! empty( $bp->bp_nav[$component_id] ) ) {
			$component_name = _bp_strip_spans_from_title( $bp->bp_nav[ $component_id ]['name'] );

		// Fall back on the component ID
		} elseif ( ! empty( $bp->{$component_id}->id ) ) {
			$component_name = ucwords( $bp->{$component_id}->id );
		}

		// Append action name if we're on a member component sub-page
		if ( ! empty( $bp->bp_options_nav[ $component_id ] ) && ! empty( $bp->canonical_stack['action'] ) ) {
			$component_subnav_name = wp_filter_object_list( $bp->bp_options_nav[ $component_id ], array( 'slug' => bp_current_action() ), 'and', 'name' );

			if ( ! empty( $component_subnav_name ) ) {
				$component_subnav_name = array_shift( $component_subnav_name );
			}
		}

		// If on the user profile's landing page, just use the fullname
		if ( bp_is_current_component( $bp->default_component ) && ( bp_get_requested_url() === bp_displayed_user_domain() ) ) {
			$title_parts[] = $displayed_user_name;

		// Use component name on member pages
		} else {
			$title_parts = array_merge( $title_parts, array_map( 'strip_tags', array(
				$displayed_user_name,
				$component_name,
			) ) );

			// If we have a subnav name, add it separately for localization
			if ( ! empty( $component_subnav_name ) ) {
				$title_parts[] = strip_tags( $component_subnav_name );
			}
		}

	// A single group
	} elseif ( bp_is_active( 'groups' ) && ! empty( $bp->groups->current_group ) && ! empty( $bp->bp_options_nav[ $bp->groups->current_group->slug ] ) ) {
		$subnav      = isset( $bp->bp_options_nav[ $bp->groups->current_group->slug ][ bp_current_action() ]['name'] ) ? $bp->bp_options_nav[ $bp->groups->current_group->slug ][ bp_current_action() ]['name'] : '';
		$title_parts = array( $bp->bp_options_title, $subnav );

	// A single item from a component other than groups
	} elseif ( bp_is_single_item() ) {
		$title_parts = array( $bp->bp_options_title, $bp->bp_options_nav[ bp_current_item() ][ bp_current_action() ]['name'] );

	// An index or directory
	} elseif ( bp_is_directory() ) {
		$current_component = bp_current_component();

		// No current component (when does this happen?)
		$title_parts = array( _x( 'Directory', 'component directory title', 'buddypress' ) );

		if ( ! empty( $current_component ) ) {
			$title_parts = array( bp_get_directory_title( $current_component ) );
 		}

	// Sign up page
	} elseif ( bp_is_register_page() ) {
		$title_parts = array( __( 'Create an Account', 'buddypress' ) );

	// Activation page
	} elseif ( bp_is_activation_page() ) {
		$title_parts = array( __( 'Activate Your Account', 'buddypress' ) );

	// Group creation page
	} elseif ( bp_is_group_create() ) {
		$title_parts = array( __( 'Create a Group', 'buddypress' ) );

	// Blog creation page
	} elseif ( bp_is_create_blog() ) {
		$title_parts = array( __( 'Create a Site', 'buddypress' ) );
	}

	// Strip spans
	$title_parts = array_map( '_bp_strip_spans_from_title', $title_parts );

	// sep on right, so reverse the order
	if ( 'right' == $seplocation ) {
		$title_parts = array_reverse( $title_parts );
	}

	// Get the blog name, so we can check if the original $title included it
	$blogname = get_bloginfo( 'name', 'display' );

	/**
	 * Are we going to fake 'title-tag' theme functionality?
	 *
	 * @link https://buddypress.trac.wordpress.org/ticket/6107
	 * @see wp_title()
	 */
	$title_tag_compatibility = (bool) ( ! empty( $_wp_theme_features['title-tag'] ) || strstr( $title, $blogname ) );

	// Append the site title to title parts if theme supports title tag
	if ( true === $title_tag_compatibility ) {
		$title_parts[] = $blogname;

		if ( ( $paged >= 2 || $page >= 2 ) && ! is_404() ) {
			$title_parts[] = sprintf( __( 'Page %s', 'buddypress' ), max( $paged, $page ) );
		}
	}

	// Pad the separator with 1 space on each side
	$prefix = str_pad( $sep, strlen( $sep ) + 2, ' ', STR_PAD_BOTH );

	// Join the parts together
	$new_title = join( $prefix, array_filter( $title_parts ) );

	// Append the prefix for pre `title-tag` compatibility
	if ( false === $title_tag_compatibility ) {
		$new_title = $new_title . $prefix;
	}

	/**
	 * Filters the page title for BuddyPress pages.
	 *
	 * @since  1.5.0
	 *
	 * @param  string $new_title   The BuddyPress page title.
	 * @param  string $title       The original WordPress page title.
	 * @param  string $sep         The title parts separator.
	 * @param  string $seplocation Location of the separator (left or right).
	 */
	return apply_filters( 'bp_modify_page_title', $new_title, $title, $sep, $seplocation );
}
add_filter( 'wp_title', 'bp_modify_page_title', 20, 3 );
add_filter( 'bp_modify_page_title', 'wptexturize'     );
add_filter( 'bp_modify_page_title', 'convert_chars'   );
add_filter( 'bp_modify_page_title', 'esc_html'        );

/**
 * Strip span tags out of title part strings.
 *
 * This is a temporary function for compatibility with WordPress versions
 * less than 4.0, and should be removed at a later date.
 *
 * @param  string $title_part
 *
 * @return string
 */
function _bp_strip_spans_from_title( $title_part = '' ) {
	$title = $title_part;
	$span = strpos( $title, '<span' );
	if ( false !== $span ) {
		$title = substr( $title, 0, $span - 1 );
	}
	return $title;
}

/**
 * Add BuddyPress-specific items to the wp_nav_menu.
 *
 * @since 1.9.0
 *
 * @param WP_Post $menu_item The menu item.
 *
 * @return WP_Post The modified WP_Post object.
 */
function bp_setup_nav_menu_item( $menu_item ) {
	if ( is_admin() ) {
		return $menu_item;
	}

	// Prevent a notice error when using the customizer
	$menu_classes = $menu_item->classes;

	if ( is_array( $menu_classes ) ) {
		$menu_classes = implode( ' ', $menu_item->classes);
	}

	// We use information stored in the CSS class to determine what kind of
	// menu item this is, and how it should be treated
	preg_match( '/\sbp-(.*)-nav/', $menu_classes, $matches );

	// If this isn't a BP menu item, we can stop here
	if ( empty( $matches[1] ) ) {
		return $menu_item;
	}

	switch ( $matches[1] ) {
		case 'login' :
			if ( is_user_logged_in() ) {
				$menu_item->_invalid = true;
			} else {
				$menu_item->url = wp_login_url( bp_get_requested_url() );
			}

			break;

		case 'logout' :
			if ( ! is_user_logged_in() ) {
				$menu_item->_invalid = true;
			} else {
				$menu_item->url = wp_logout_url( bp_get_requested_url() );
			}

			break;

		// Don't show the Register link to logged-in users
		case 'register' :
			if ( is_user_logged_in() ) {
				$menu_item->_invalid = true;
			}

			break;

		// All other BP nav items are specific to the logged-in user,
		// and so are not relevant to logged-out users
		default:
			if ( is_user_logged_in() ) {
				$menu_item->url = bp_nav_menu_get_item_url( $matches[1] );
			} else {
				$menu_item->_invalid = true;
			}

			break;
	}

	// If component is deactivated, make sure menu item doesn't render
	if ( empty( $menu_item->url ) ) {
		$menu_item->_invalid = true;

	// Highlight the current page
	} else {
		$current = bp_get_requested_url();
		if ( strpos( $current, $menu_item->url ) !== false ) {
			if ( is_array( $menu_item->classes ) ) {
				$menu_item->classes[] = 'current_page_item';
				$menu_item->classes[] = 'current-menu-item';
			} else {
				$menu_item->classes = array( 'current_page_item', 'current-menu-item' );
			}
		}
	}

	return $menu_item;
}
add_filter( 'wp_setup_nav_menu_item', 'bp_setup_nav_menu_item', 10, 1 );

/**
 * Populate BuddyPress user nav items for the customizer
 *
 * @since  2.3.3
 *
 * @param  array   $items  The array of menu items
 * @param  string  $type   The requested type
 * @param  string  $object The requested object name
 * @param  integer $page   The page num being requested
 * @return array           The paginated BuddyPress user nav items.
 */
function bp_customizer_nav_menus_get_items( $items = array(), $type = '', $object = '', $page = 0 ) {
	if ( 'bp_loggedin_nav' === $object ) {
		$bp_items = bp_nav_menu_get_loggedin_pages();
	} elseif ( 'bp_loggedout_nav' === $object ) {
		$bp_items = bp_nav_menu_get_loggedout_pages();
	} else {
		return $items;
	}

	foreach ( $bp_items as $bp_item ) {
		$items[] = array(
			'id'         => "bp-{$bp_item->post_excerpt}",
			'title'      => html_entity_decode( $bp_item->post_title, ENT_QUOTES, get_bloginfo( 'charset' ) ),
			'type'       => $type,
			'url'        => esc_url_raw( $bp_item->guid ),
			'classes'    => "bp-menu bp-{$bp_item->post_excerpt}-nav",
			'type_label' => _x( 'Custom Link', 'customizer menu type label', 'buddypress' ),
			'object'     => $object,
			'object_id'  => -1,
		);
	}

	return array_slice( $items, 10 * $page, 10 );
}
add_filter( 'customize_nav_menu_available_items', 'bp_customizer_nav_menus_get_items', 10, 4 );

/**
 * Set BuddyPress item navs for the customizer
 *
 * @since  2.3.3
 *
 * @param  array $item_types An associative array structured for the customizer.
 * @return array $item_types An associative array structured for the customizer.
 */
function bp_customizer_nav_menus_set_item_types( $item_types = array() ) {
	$item_types = array_merge( $item_types, array(
		'bp_loggedin_nav' => array(
			'title'  => _x( 'BuddyPress (logged-in)', 'customizer menu section title', 'buddypress' ),
			'type'   => 'bp_nav',
			'object' => 'bp_loggedin_nav',
		),
		'bp_loggedout_nav' => array(
			'title'  => _x( 'BuddyPress (logged-out)', 'customizer menu section title', 'buddypress' ),
			'type'   => 'bp_nav',
			'object' => 'bp_loggedout_nav',
		),
	) );

	return $item_types;
}
add_filter( 'customize_nav_menu_available_item_types', 'bp_customizer_nav_menus_set_item_types', 10, 1 );

/**
 * Filter SQL query strings to swap out the 'meta_id' column.
 *
 * WordPress uses the meta_id column for commentmeta and postmeta, and so
 * hardcodes the column name into its *_metadata() functions. BuddyPress, on
 * the other hand, uses 'id' for the primary column. To make WP's functions
 * usable for BuddyPress, we use this just-in-time filter on 'query' to swap
 * 'meta_id' with 'id.
 *
 * @since 2.0.0
 *
 * @access private Do not use.
 *
 * @param string $q SQL query.
 *
 * @return string
 */
function bp_filter_metaid_column_name( $q ) {
	/*
	 * Replace quoted content with __QUOTE__ to avoid false positives.
	 * This regular expression will match nested quotes.
	 */
	$quoted_regex = "/'[^'\\\\]*(?:\\\\.[^'\\\\]*)*'/s";
	preg_match_all( $quoted_regex, $q, $quoted_matches );
	$q = preg_replace( $quoted_regex, '__QUOTE__', $q );

	$q = str_replace( 'meta_id', 'id', $q );

	// Put quoted content back into the string.
	if ( ! empty( $quoted_matches[0] ) ) {
		for ( $i = 0; $i < count( $quoted_matches[0] ); $i++ ) {
			$quote_pos = strpos( $q, '__QUOTE__' );
			$q = substr_replace( $q, $quoted_matches[0][ $i ], $quote_pos, 9 );
		}
	}

	return $q;
}

/**
 * Filter the edit post link to avoid its display in BuddyPress pages.
 *
 * @since 2.1.0
 *
 * @param  string $edit_link The edit link.
 * @param  int    $post_id   Post ID.
 *
 * @return bool|string Will be a boolean (false) if $post_id is 0. Will be a string (the unchanged edit link)
 *                     otherwise
 */
function bp_core_filter_edit_post_link( $edit_link = '', $post_id = 0 ) {
	if ( 0 === $post_id ) {
		$edit_link = false;
	}

	return $edit_link;
}

/**
 * Should BuddyPress load the mentions scripts and related assets, including results to prime the
 * mentions suggestions?
 *
 * @since 2.2.0
 *
 * @param bool $load_mentions    True to load mentions assets, false otherwise.
 * @param bool $mentions_enabled True if mentions are enabled.
 *
 * @return bool True if mentions scripts should be loaded.
 */
function bp_maybe_load_mentions_scripts_for_blog_content( $load_mentions, $mentions_enabled ) {
	if ( ! $mentions_enabled ) {
		return $load_mentions;
	}

	if ( $load_mentions || ( bp_is_blog_page() && is_singular() && comments_open() ) ) {
		return true;
	}

	return $load_mentions;
}
add_filter( 'bp_activity_maybe_load_mentions_scripts', 'bp_maybe_load_mentions_scripts_for_blog_content', 10, 2 );

/**
 * Injects specific BuddyPress CSS classes into a widget sidebar.
 *
 * Helps to standardize styling of BuddyPress widgets within a theme that
 * does not use dynamic CSS classes in their widget sidebar's 'before_widget'
 * call.
 *
 * @since 2.4.0
 * @access private
 *
 * @global array $wp_registered_widgets Current registered widgets.
 * @param  array $params                Current sidebar params.
 * @return array
 */
function _bp_core_inject_bp_widget_css_class( $params ) {
	global $wp_registered_widgets;

	$widget_id = $params[0]['widget_id'];

	// If the current widget isn't a BuddyPress one, stop!
	// We determine if a widget is a BuddyPress widget, if the widget class
	// begins with 'bp_'.
	if ( 0 !== strpos( $wp_registered_widgets[ $widget_id ]['callback'][0]->id_base, 'bp_' ) ) {
		return $params;
	}

	// Dynamically add our widget CSS classes for BP widgets if not already there.
	$classes = array();

	// Try to find 'widget' CSS class.
	if ( false === strpos( $params[0]['before_widget'], 'widget ' ) ) {
		$classes[] = 'widget';
	}

	// Try to find 'buddypress' CSS class.
	if ( false === strpos( $params[0]['before_widget'], ' buddypress' ) ) {
		$classes[] = 'buddypress';
	}

	// Stop if widget already has our CSS classes.
	if ( empty( $classes ) ) {
		return $params;
	}

	// CSS injection time!
	$params[0]['before_widget'] = str_replace( 'class="', 'class="' . implode( ' ', $classes ) . ' ', $params[0]['before_widget'] );

	return $params;
}
add_filter( 'dynamic_sidebar_params', '_bp_core_inject_bp_widget_css_class' );
