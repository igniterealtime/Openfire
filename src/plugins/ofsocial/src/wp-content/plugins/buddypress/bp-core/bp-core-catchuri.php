<?php
/**
 * BuddyPress URI catcher.
 *
 * Functions for parsing the URI and determining which BuddyPress template file
 * to use on-screen.
 *
 * @package BuddyPress
 * @subpackage Core
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Analyze the URI and break it down into BuddyPress-usable chunks.
 *
 * BuddyPress can use complete custom friendly URIs without the user having to
 * add new rewrite rules. Custom components are able to use their own custom
 * URI structures with very little work.
 *
 * The URIs are broken down as follows:
 *   - http:// example.com / members / andy / [current_component] / [current_action] / [action_variables] / [action_variables] / ...
 *   - OUTSIDE ROOT: http:// example.com / sites / buddypress / members / andy / [current_component] / [current_action] / [action_variables] / [action_variables] / ...
 *
 *	Example:
 *    - http://example.com/members/andy/profile/edit/group/5/
 *    - $bp->current_component: string 'xprofile'
 *    - $bp->current_action: string 'edit'
 *    - $bp->action_variables: array ['group', 5]
 *
 * @since 1.0.0
 */
function bp_core_set_uri_globals() {
	global $current_blog, $wp_rewrite;

	// Don't catch URIs on non-root blogs unless multiblog mode is on
	if ( !bp_is_root_blog() && !bp_is_multiblog_mode() )
		return false;

	$bp = buddypress();

	// Define local variables
	$root_profile = $match   = false;
	$key_slugs    = $matches = $uri_chunks = array();

	// Fetch all the WP page names for each component
	if ( empty( $bp->pages ) )
		$bp->pages = bp_core_get_directory_pages();

	// Ajax or not?
	if ( defined( 'DOING_AJAX' ) && DOING_AJAX || strpos( $_SERVER['REQUEST_URI'], 'wp-load.php' ) )
		$path = bp_get_referer_path();
	else
		$path = esc_url( $_SERVER['REQUEST_URI'] );

	/**
	 * Filters the BuddyPress global URI path.
	 *
	 * @since 1.0.0
	 *
	 * @param string $path Path to set.
	 */
	$path = apply_filters( 'bp_uri', $path );

	// Take GET variables off the URL to avoid problems
	$path = strtok( $path, '?' );

	// Fetch current URI and explode each part separated by '/' into an array
	$bp_uri = explode( '/', $path );

	// Loop and remove empties
	foreach ( (array) $bp_uri as $key => $uri_chunk ) {
		if ( empty( $bp_uri[$key] ) ) {
			unset( $bp_uri[$key] );
		}
	}

	// If running off blog other than root, any subdirectory names must be
	// removed from $bp_uri. This includes two cases:
	//
	//    1. when WP is installed in a subdirectory,
	//    2. when BP is running on secondary blog of a subdirectory
	//       multisite installation. Phew!
	if ( is_multisite() && !is_subdomain_install() && ( bp_is_multiblog_mode() || 1 != bp_get_root_blog_id() ) ) {

		// Blow chunks
		$chunks = explode( '/', $current_blog->path );

		// If chunks exist...
		if ( !empty( $chunks ) ) {

			// ...loop through them...
			foreach( $chunks as $key => $chunk ) {
				$bkey = array_search( $chunk, $bp_uri );

				// ...and unset offending keys
				if ( false !== $bkey ) {
					unset( $bp_uri[$bkey] );
				}

				$bp_uri = array_values( $bp_uri );
			}
		}
	}

	// Get site path items
	$paths = explode( '/', bp_core_get_site_path() );

	// Take empties off the end of path
	if ( empty( $paths[count( $paths ) - 1] ) )
		array_pop( $paths );

	// Take empties off the start of path
	if ( empty( $paths[0] ) )
		array_shift( $paths );

	// Reset indexes
	$bp_uri = array_values( $bp_uri );
	$paths  = array_values( $paths );

	// Unset URI indices if they intersect with the paths
	foreach ( (array) $bp_uri as $key => $uri_chunk ) {
		if ( isset( $paths[$key] ) && $uri_chunk == $paths[$key] ) {
			unset( $bp_uri[$key] );
		}
	}

	// Reset the keys by merging with an empty array
	$bp_uri = array_merge( array(), $bp_uri );

	// If a component is set to the front page, force its name into $bp_uri
	// so that $current_component is populated (unless a specific WP post is being requested
	// via a URL parameter, usually signifying Preview mode)
	if ( 'page' == get_option( 'show_on_front' ) && get_option( 'page_on_front' ) && empty( $bp_uri ) && empty( $_GET['p'] ) && empty( $_GET['page_id'] ) ) {
		$post = get_post( get_option( 'page_on_front' ) );
		if ( !empty( $post ) ) {
			$bp_uri[0] = $post->post_name;
		}
	}

	// Keep the unfiltered URI safe
	$bp->unfiltered_uri = $bp_uri;

	// Don't use $bp_unfiltered_uri, this is only for backpat with old plugins. Use $bp->unfiltered_uri.
	$GLOBALS['bp_unfiltered_uri'] = &$bp->unfiltered_uri;

	// Get slugs of pages into array
	foreach ( (array) $bp->pages as $page_key => $bp_page )
		$key_slugs[$page_key] = trailingslashit( '/' . $bp_page->slug );

	// Bail if keyslugs are empty, as BP is not setup correct
	if ( empty( $key_slugs ) )
		return;

	// Loop through page slugs and look for exact match to path
	foreach ( $key_slugs as $key => $slug ) {
		if ( $slug == $path ) {
			$match      = $bp->pages->{$key};
			$match->key = $key;
			$matches[]  = 1;
			break;
		}
	}

	// No exact match, so look for partials
	if ( empty( $match ) ) {

		// Loop through each page in the $bp->pages global
		foreach ( (array) $bp->pages as $page_key => $bp_page ) {

			// Look for a match (check members first)
			if ( in_array( $bp_page->name, (array) $bp_uri ) ) {

				// Match found, now match the slug to make sure.
				$uri_chunks = explode( '/', $bp_page->slug );

				// Loop through uri_chunks
				foreach ( (array) $uri_chunks as $key => $uri_chunk ) {

					// Make sure chunk is in the correct position
					if ( !empty( $bp_uri[$key] ) && ( $bp_uri[$key] == $uri_chunk ) ) {
						$matches[] = 1;

					// No match
					} else {
						$matches[] = 0;
					}
				}

				// Have a match
				if ( !in_array( 0, (array) $matches ) ) {
					$match      = $bp_page;
					$match->key = $page_key;
					break;
				};

				// Unset matches
				unset( $matches );
			}

			// Unset uri chunks
			unset( $uri_chunks );
		}
	}

	// URLs with BP_ENABLE_ROOT_PROFILES enabled won't be caught above
	if ( empty( $matches ) && bp_core_enable_root_profiles() ) {

		// Switch field based on compat
		$field = bp_is_username_compatibility_mode() ? 'login' : 'slug';

		// Make sure there's a user corresponding to $bp_uri[0]
		if ( !empty( $bp->pages->members ) && !empty( $bp_uri[0] ) && $root_profile = get_user_by( $field, $bp_uri[0] ) ) {

			// Force BP to recognize that this is a members page
			$matches[]  = 1;
			$match      = $bp->pages->members;
			$match->key = 'members';
		}
	}

	// Search doesn't have an associated page, so we check for it separately
	if ( !empty( $bp_uri[0] ) && ( bp_get_search_slug() == $bp_uri[0] ) ) {
		$matches[]   = 1;
		$match       = new stdClass;
		$match->key  = 'search';
		$match->slug = bp_get_search_slug();
	}

	// This is not a BuddyPress page, so just return.
	if ( empty( $matches ) )
		return false;

	$wp_rewrite->use_verbose_page_rules = false;

	// Find the offset. With $root_profile set, we fudge the offset down so later parsing works
	$slug       = !empty ( $match ) ? explode( '/', $match->slug ) : '';
	$uri_offset = empty( $root_profile ) ? 0 : -1;

	// Rejig the offset
	if ( !empty( $slug ) && ( 1 < count( $slug ) ) ) {
		// Only offset if not on a root profile. Fixes issue when Members page is nested.
		if ( false === $root_profile ) {
			array_pop( $slug );
			$uri_offset = count( $slug );
		}
	}

	// Global the unfiltered offset to use in bp_core_load_template().
	// To avoid PHP warnings in bp_core_load_template(), it must always be >= 0
	$bp->unfiltered_uri_offset = $uri_offset >= 0 ? $uri_offset : 0;

	// We have an exact match
	if ( isset( $match->key ) ) {

		// Set current component to matched key
		$bp->current_component = $match->key;

		// If members component, do more work to find the actual component
		if ( 'members' == $match->key ) {

			$after_member_slug = false;
			if ( ! empty( $bp_uri[ $uri_offset + 1 ] ) ) {
				$after_member_slug = $bp_uri[ $uri_offset + 1 ];
			}

			// Are we viewing a specific user?
			if ( $after_member_slug ) {
				// If root profile, we've already queried for the user
				if ( $root_profile instanceof WP_User ) {
					$bp->displayed_user->id = $root_profile->ID;

				// Switch the displayed_user based on compatibility mode
				} elseif ( bp_is_username_compatibility_mode() ) {
					$bp->displayed_user->id = (int) bp_core_get_userid( urldecode( $after_member_slug ) );

				} else {
					$bp->displayed_user->id = (int) bp_core_get_userid_from_nicename( $after_member_slug );
				}
			}

			// Is this a member type directory?
			if ( ! bp_displayed_user_id() && $after_member_slug === apply_filters( 'bp_members_member_type_base', _x( 'type', 'member type URL base', 'buddypress' ) ) && ! empty( $bp_uri[ $uri_offset + 2 ] ) ) {
				$matched_types = bp_get_member_types( array(
					'has_directory'  => true,
					'directory_slug' => $bp_uri[ $uri_offset + 2 ],
				) );

				if ( ! empty( $matched_types ) ) {
					$bp->current_member_type = reset( $matched_types );
					unset( $bp_uri[ $uri_offset + 1 ] );
				}
			}

			// If the slug matches neither a member type nor a specific member, 404.
			if ( ! bp_displayed_user_id() && ! bp_get_current_member_type() && $after_member_slug ) {
				// Prevent components from loading their templates.
				$bp->current_component = '';
				bp_do_404();
				return;
			}

			// If the displayed user is marked as a spammer, 404 (unless logged-in user is a super admin)
			if ( bp_displayed_user_id() && bp_is_user_spammer( bp_displayed_user_id() ) ) {
				if ( bp_current_user_can( 'bp_moderate' ) ) {
					bp_core_add_message( __( 'This user has been marked as a spammer. Only site admins can view this profile.', 'buddypress' ), 'warning' );
				} else {
					bp_do_404();
					return;
				}
			}

			// Bump the offset.
			if ( bp_displayed_user_id() ) {
				if ( isset( $bp_uri[$uri_offset + 2] ) ) {
					$bp_uri                = array_merge( array(), array_slice( $bp_uri, $uri_offset + 2 ) );
					$bp->current_component = $bp_uri[0];

				// No component, so default will be picked later
				} else {
					$bp_uri                = array_merge( array(), array_slice( $bp_uri, $uri_offset + 2 ) );
					$bp->current_component = '';
				}

				// Reset the offset
				$uri_offset = 0;
			}
		}
	}

	// Determine the current action.
	$current_action = isset( $bp_uri[ $uri_offset + 1 ] ) ? $bp_uri[ $uri_offset + 1 ] : '';

	/*
	 * If a BuddyPress directory is set to the WP front page, URLs like example.com/members/?s=foo
	 * shouldn't interfere with blog searches.
	 */
	if ( empty( $current_action) && ! empty( $_GET['s'] ) && 'page' == get_option( 'show_on_front' ) && ! empty( $match->id ) ) {
		$page_on_front = (int) get_option( 'page_on_front' );
		if ( (int) $match->id === $page_on_front ) {
			$bp->current_component = '';
			return false;
		}
	}

	$bp->current_action = $current_action;

	// Slice the rest of the $bp_uri array and reset offset
	$bp_uri      = array_slice( $bp_uri, $uri_offset + 2 );
	$uri_offset  = 0;

	// Set the entire URI as the action variables, we will unset the current_component and action in a second
	$bp->action_variables = $bp_uri;

	// Reset the keys by merging with an empty array
	$bp->action_variables = array_merge( array(), $bp->action_variables );
}

/**
 * Are root profiles enabled and allowed?
 *
 * @since 1.6.0
 *
 * @return bool True if yes, false if no.
 */
function bp_core_enable_root_profiles() {

	$retval = false;

	if ( defined( 'BP_ENABLE_ROOT_PROFILES' ) && ( true == BP_ENABLE_ROOT_PROFILES ) )
		$retval = true;

	/**
	 * Filters whether or not root profiles are enabled and allowed.
	 *
	 * @since 1.6.0
	 *
	 * @param bool $retval Whether or not root profiles are available.
	 */
	return apply_filters( 'bp_core_enable_root_profiles', $retval );
}

/**
 * Load a specific template file with fallback support.
 *
 * Example:
 *   bp_core_load_template( 'members/index' );
 * Loads:
 *   wp-content/themes/[activated_theme]/members/index.php
 *
 * @param array $templates Array of templates to attempt to load.
 *
 * @return bool|null Returns false on failure.
 */
function bp_core_load_template( $templates ) {
	global $wp_query;

	// Reset the post
	bp_theme_compat_reset_post( array(
		'ID'          => 0,
		'is_404'      => true,
		'post_status' => 'publish',
	) );

	// Set theme compat to false since the reset post function automatically sets
	// theme compat to true
	bp_set_theme_compat_active( false );

	// Fetch each template and add the php suffix
	$filtered_templates = array();
	foreach ( (array) $templates as $template ) {
		$filtered_templates[] = $template . '.php';
	}

	// Only perform template lookup for bp-default themes
	if ( ! bp_use_theme_compat_with_current_theme() ) {
		$template = locate_template( (array) $filtered_templates, false );

	// Theme compat doesn't require a template lookup
	} else {
		$template = '';
	}

	/**
	 * Filters the template locations.
	 *
	 * Allows plugins to alter where the template files are located.
	 *
	 * @since 1.1.0
	 *
	 * @param string $template           Located template path.
	 * @param array  $filtered_templates Array of templates to attempt to load.
	 */
	$located_template = apply_filters( 'bp_located_template', $template, $filtered_templates );
	if ( !empty( $located_template ) ) {
		// Template was located, lets set this as a valid page and not a 404.
		status_header( 200 );
		$wp_query->is_page     = true;
		$wp_query->is_singular = true;
		$wp_query->is_404      = false;

		/**
		 * Fires before the loading of a located template file.
		 *
		 * @since 1.6.0
		 *
		 * @param string $located_template Template found to be loaded.
		 */
		do_action( 'bp_core_pre_load_template', $located_template );

		/**
		 * Filters the selected template right before loading.
		 *
		 * @since 1.1.0
		 *
		 * @param string $located_template Template found to be loaded.
		 */
		load_template( apply_filters( 'bp_load_template', $located_template ) );

		/**
		 * Fires after the loading of a located template file.
		 *
		 * @since 1.6.0
		 *
		 * @param string $located_template Template found that was loaded.
		 */
		do_action( 'bp_core_post_load_template', $located_template );

		// Kill any other output after this.
		exit();

	// No template found, so setup theme compatibility
	// @todo Some other 404 handling if theme compat doesn't kick in
	} else {

		// We know where we are, so reset important $wp_query bits here early.
		// The rest will be done by bp_theme_compat_reset_post() later.
		if ( is_buddypress() ) {
			status_header( 200 );
			$wp_query->is_page     = true;
			$wp_query->is_singular = true;
			$wp_query->is_404      = false;
		}

		/**
		 * Fires if there are no found templates to load and theme compat is needed.
		 *
		 * @since 1.7.0
		 */
		do_action( 'bp_setup_theme_compat' );
	}
}

/**
 * Redirect away from /profile URIs if XProfile is not enabled.
 */
function bp_core_catch_profile_uri() {
	if ( !bp_is_active( 'xprofile' ) ) {

		/**
		 * Filters the path to redirect users to if XProfile is not enabled.
		 *
		 * @since 1.0.0
		 *
		 * @param string $value Path to redirect users to.
		 */
		bp_core_load_template( apply_filters( 'bp_core_template_display_profile', 'members/single/home' ) );
	}
}

/**
 * Catch unauthorized access to certain BuddyPress pages and redirect accordingly.
 *
 * @since 1.5.0
 */
function bp_core_catch_no_access() {
	global $wp_query;

	$bp = buddypress();

	// If coming from bp_core_redirect() and $bp_no_status_set is true,
	// we are redirecting to an accessible page so skip this check.
	if ( !empty( $bp->no_status_set ) )
		return false;

	if ( !isset( $wp_query->queried_object ) && !bp_is_blog_page() ) {
		bp_do_404();
	}
}
add_action( 'bp_template_redirect', 'bp_core_catch_no_access', 1 );

/**
 * Redirect a user to log in for BP pages that require access control.
 *
 * Add an error message (if one is provided).
 *
 * If authenticated, redirects user back to requested content by default.
 *
 * @since 1.5.0
 *
 * @param array|string $args {
 *     @type int    $mode     Specifies the destination of the redirect. 1 will
 *                            direct to the root domain (home page), which assumes you have a
 *                            log-in form there; 2 directs to wp-login.php. Default: 2.
 *     @type string $redirect The URL the user will be redirected to after successfully
 *                            logging in. Default: the URL originally requested.
 *     @type string $root     The root URL of the site, used in case of error or mode 1 redirects.
 *                            Default: the value of {@link bp_get_root_domain()}.
 *     @type string $message  An error message to display to the user on the log-in page.
 *                            Default: "You must log in to access the page you requested."
 * }
 */
function bp_core_no_access( $args = '' ) {

 	// Build the redirect URL
 	$redirect_url  = is_ssl() ? 'https://' : 'http://';
 	$redirect_url .= $_SERVER['HTTP_HOST'];
 	$redirect_url .= $_SERVER['REQUEST_URI'];

	$defaults = array(
		'mode'     => 2,                    // 1 = $root, 2 = wp-login.php
		'redirect' => $redirect_url,        // the URL you get redirected to when a user successfully logs in
		'root'     => bp_get_root_domain(),	// the landing page you get redirected to when a user doesn't have access
		'message'  => __( 'You must log in to access the page you requested.', 'buddypress' )
	);

	$r = wp_parse_args( $args, $defaults );

	/**
	 * Filters the arguments used for user redirecting when visiting access controlled areas.
	 *
	 * @since 1.6.0
	 *
	 * @param array $r Array of parsed arguments for redirect determination.
	 */
	$r = apply_filters( 'bp_core_no_access', $r );
	extract( $r, EXTR_SKIP );

	/**
	 * @ignore Ignore these filters and use 'bp_core_no_access' above
	 */
	$mode     = apply_filters( 'bp_no_access_mode',     $mode,     $root,     $redirect, $message );
	$redirect = apply_filters( 'bp_no_access_redirect', $redirect, $root,     $message,  $mode    );
	$root     = apply_filters( 'bp_no_access_root',     $root,     $redirect, $message,  $mode    );
	$message  = apply_filters( 'bp_no_access_message',  $message,  $root,     $redirect, $mode    );
	$root     = trailingslashit( $root );

	switch ( $mode ) {

		// Option to redirect to wp-login.php
		// Error message is displayed with bp_core_no_access_wp_login_error()
		case 2 :
			if ( !empty( $redirect ) ) {
				bp_core_redirect( add_query_arg( array( 'action' => 'bpnoaccess' ), wp_login_url( $redirect ) ) );
			} else {
				bp_core_redirect( $root );
			}

			break;

		// Redirect to root with "redirect_to" parameter
		// Error message is displayed with bp_core_add_message()
		case 1 :
		default :

			$url = $root;
			if ( !empty( $redirect ) ) {
				$url = add_query_arg( 'redirect_to', urlencode( $redirect ), $root );
			}

			if ( !empty( $message ) ) {
				bp_core_add_message( $message, 'error' );
			}

			bp_core_redirect( $url );

			break;
	}
}

/**
 * Add an error message to wp-login.php.
 *
 * Hooks into the "bpnoaccess" action defined in bp_core_no_access().
 *
 * @since 1.5.0
 *
 * @global string $error Error message to pass to wp-login.php.
 */
function bp_core_no_access_wp_login_error() {
	global $error;

	/**
	 * Filters the error message for wp-login.php when needing to log in before accessing.
	 *
	 * @since 1.5.0
	 *
	 * @param string $value Error message to display.
	 * @param string $value URL to redirect user to after successful login.
	 */
	$error = apply_filters( 'bp_wp_login_error', __( 'You must log in to access the page you requested.', 'buddypress' ), $_REQUEST['redirect_to'] );

	// shake shake shake!
	add_action( 'login_head', 'wp_shake_js', 12 );
}
add_action( 'login_form_bpnoaccess', 'bp_core_no_access_wp_login_error' );

/**
 * Canonicalize BuddyPress URLs.
 *
 * This function ensures that requests for BuddyPress content are always
 * redirected to their canonical versions. Canonical versions are always
 * trailingslashed, and are typically the most general possible versions of the
 * URL - eg, example.com/groups/mygroup/ instead of
 * example.com/groups/mygroup/home/.
 *
 * @since 1.6.0
 *
 * @see BP_Members_Component::setup_globals() where
 *      $bp->canonical_stack['base_url'] and ['component'] may be set.
 * @see bp_core_new_nav_item() where $bp->canonical_stack['action'] may be set.
 * @uses bp_get_canonical_url()
 * @uses bp_get_requested_url()
 */
function bp_redirect_canonical() {

	/**
	 * Filters whether or not to do canonical redirects on BuddyPress URLs.
	 *
	 * @since 1.6.0
	 *
	 * @param bool $value Whether or not to do canonical redirects. Default true.
	 */
	if ( !bp_is_blog_page() && apply_filters( 'bp_do_redirect_canonical', true ) ) {
		// If this is a POST request, don't do a canonical redirect.
		// This is for backward compatibility with plugins that submit form requests to
		// non-canonical URLs. Plugin authors should do their best to use canonical URLs in
		// their form actions.
		if ( !empty( $_POST ) ) {
			return;
		}

		// build the URL in the address bar
		$requested_url  = bp_get_requested_url();

		// Stash query args
		$url_stack      = explode( '?', $requested_url );
		$req_url_clean  = $url_stack[0];
		$query_args     = isset( $url_stack[1] ) ? $url_stack[1] : '';

		$canonical_url  = bp_get_canonical_url();

		// Only redirect if we've assembled a URL different from the request
		if ( $canonical_url !== $req_url_clean ) {

			$bp = buddypress();

			// Template messages have been deleted from the cookie by this point, so
			// they must be readded before redirecting
			if ( isset( $bp->template_message ) ) {
				$message      = stripslashes( $bp->template_message );
				$message_type = isset( $bp->template_message_type ) ? $bp->template_message_type : 'success';

				bp_core_add_message( $message, $message_type );
			}

			if ( !empty( $query_args ) ) {
				$canonical_url .= '?' . $query_args;
			}

			bp_core_redirect( $canonical_url, 301 );
		}
	}
}

/**
 * Output rel=canonical header tag for BuddyPress content.
 *
 * @since 1.6.0
 */
function bp_rel_canonical() {
	$canonical_url = bp_get_canonical_url();

	// Output rel=canonical tag
	echo "<link rel='canonical' href='" . esc_attr( $canonical_url ) . "' />\n";
}

/**
 * Get the canonical URL of the current page.
 *
 * @since 1.6.0
 *
 * @uses apply_filters() Filter bp_get_canonical_url to modify return value.
 *
 * @param array $args {
 *     Optional array of arguments.
 *     @type bool $include_query_args Whether to include current URL arguments
 *                                    in the canonical URL returned from the function.
 * }
 *
 * @return string Canonical URL for the current page.
 */
function bp_get_canonical_url( $args = array() ) {

	// For non-BP content, return the requested url, and let WP do the work
	if ( bp_is_blog_page() ) {
		return bp_get_requested_url();
	}

	$bp = buddypress();

	$defaults = array(
		'include_query_args' => false // Include URL arguments, eg ?foo=bar&foo2=bar2
	);
	$r = wp_parse_args( $args, $defaults );
	extract( $r );

	// Special case: when a BuddyPress directory (eg example.com/members)
	// is set to be the front page, ensure that the current canonical URL
	// is the home page URL.
	if ( 'page' == get_option( 'show_on_front' ) && $page_on_front = (int) get_option( 'page_on_front' ) ) {
		$front_page_component = array_search( $page_on_front, bp_core_get_directory_page_ids() );

		// If requesting the front page component directory, canonical
		// URL is the front page. We detect whether we're detecting a
		// component *directory* by checking that bp_current_action()
		// is empty - ie, this not a single item or a feed
		if ( false !== $front_page_component && bp_is_current_component( $front_page_component ) && ! bp_current_action() ) {
			$bp->canonical_stack['canonical_url'] = trailingslashit( bp_get_root_domain() );

		// Except when the front page is set to the registration page
		// and the current user is logged in. In this case we send to
		// the members directory to avoid redirect loops
		} elseif ( bp_is_register_page() && 'register' == $front_page_component && is_user_logged_in() ) {

			/**
			 * Filters the logged in register page redirect URL.
			 *
			 * @since 1.5.1
			 *
			 * @param string $value URL to redirect logged in members to.
			 */
			$bp->canonical_stack['canonical_url'] = apply_filters( 'bp_loggedin_register_page_redirect_to', bp_get_members_directory_permalink() );
		}
	}

	if ( empty( $bp->canonical_stack['canonical_url'] ) ) {
		// Build the URL in the address bar
		$requested_url  = bp_get_requested_url();

		// Stash query args
		$url_stack      = explode( '?', $requested_url );

		// Build the canonical URL out of the redirect stack
		if ( isset( $bp->canonical_stack['base_url'] ) )
			$url_stack[0] = $bp->canonical_stack['base_url'];

		if ( isset( $bp->canonical_stack['component'] ) )
			$url_stack[0] = trailingslashit( $url_stack[0] . $bp->canonical_stack['component'] );

		if ( isset( $bp->canonical_stack['action'] ) )
			$url_stack[0] = trailingslashit( $url_stack[0] . $bp->canonical_stack['action'] );

		if ( !empty( $bp->canonical_stack['action_variables'] ) ) {
			foreach( (array) $bp->canonical_stack['action_variables'] as $av ) {
				$url_stack[0] = trailingslashit( $url_stack[0] . $av );
			}
		}

		// Add trailing slash
		$url_stack[0] = trailingslashit( $url_stack[0] );

		// Stash in the $bp global
		$bp->canonical_stack['canonical_url'] = implode( '?', $url_stack );
	}

	$canonical_url = $bp->canonical_stack['canonical_url'];

	if ( !$include_query_args ) {
		$canonical_url = array_reverse( explode( '?', $canonical_url ) );
		$canonical_url = array_pop( $canonical_url );
	}

	/**
	 * Filters the canonical url of the current page.
	 *
	 * @since 1.6.0
	 *
	 * @param string $canonical_url Canonical URL of the current page.
	 * @param array  $args          Array of arguments to help determine canonical URL.
	 */
	return apply_filters( 'bp_get_canonical_url', $canonical_url, $args );
}

/**
 * Return the URL as requested on the current page load by the user agent.
 *
 * @since 1.6.0
 *
 * @return string Requested URL string.
 */
function bp_get_requested_url() {
	$bp = buddypress();

	if ( empty( $bp->canonical_stack['requested_url'] ) ) {
		$bp->canonical_stack['requested_url']  = is_ssl() ? 'https://' : 'http://';
		$bp->canonical_stack['requested_url'] .= $_SERVER['HTTP_HOST'] . $_SERVER['REQUEST_URI'];
	}

	/**
	 * Filters the URL as requested on the current page load by the user agent.
	 *
	 * @since 1.7.0
	 *
	 * @param string $value Requested URL string.
	 */
	return apply_filters( 'bp_get_requested_url', $bp->canonical_stack['requested_url'] );
}

/**
 * Remove WP's canonical redirect when we are trying to load BP-specific content.
 *
 * Avoids issues with WordPress thinking that a BuddyPress URL might actually
 * be a blog post or page.
 *
 * This function should be considered temporary, and may be removed without
 * notice in future versions of BuddyPress.
 *
 * @since 1.6.0
 *
 * @uses bp_is_blog_page()
 */
function _bp_maybe_remove_redirect_canonical() {
	if ( ! bp_is_blog_page() )
		remove_action( 'template_redirect', 'redirect_canonical' );
}
add_action( 'bp_init', '_bp_maybe_remove_redirect_canonical' );

/**
 * Rehook maybe_redirect_404() to run later than the default.
 *
 * WordPress's maybe_redirect_404() allows admins on a multisite installation
 * to define 'NOBLOGREDIRECT', a URL to which 404 requests will be redirected.
 * maybe_redirect_404() is hooked to template_redirect at priority 10, which
 * creates a race condition with bp_template_redirect(), our piggyback hook.
 * Due to a legacy bug in BuddyPress, internal BP content (such as members and
 * groups) is marked 404 in $wp_query until bp_core_load_template(), when BP
 * manually overrides the automatic 404. However, the race condition with
 * maybe_redirect_404() means that this manual un-404-ing doesn't happen in
 * time, with the results that maybe_redirect_404() thinks that the page is
 * a legitimate 404, and redirects incorrectly to NOBLOGREDIRECT.
 *
 * By switching maybe_redirect_404() to catch at a higher priority, we avoid
 * the race condition. If bp_core_load_template() runs, it dies before reaching
 * maybe_redirect_404(). If bp_core_load_template() does not run, it means that
 * the 404 is legitimate, and maybe_redirect_404() can proceed as expected.
 *
 * This function will be removed in a later version of BuddyPress. Plugins
 * (and plugin authors!) should ignore it.
 *
 * @since 1.6.1
 *
 * @link https://buddypress.trac.wordpress.org/ticket/4329
 * @link https://buddypress.trac.wordpress.org/ticket/4415
 */
function _bp_rehook_maybe_redirect_404() {
	if ( defined( 'NOBLOGREDIRECT' ) ) {
		remove_action( 'template_redirect', 'maybe_redirect_404' );
		add_action( 'template_redirect', 'maybe_redirect_404', 100 );
	}
}
add_action( 'template_redirect', '_bp_rehook_maybe_redirect_404', 1 );

/**
 * Remove WP's rel=canonical HTML tag if we are trying to load BP-specific content.
 *
 * This function should be considered temporary, and may be removed without
 * notice in future versions of BuddyPress.
 *
 * @since 1.6.0
 */
function _bp_maybe_remove_rel_canonical() {
	if ( ! bp_is_blog_page() && ! is_404() ) {
		remove_action( 'wp_head', 'rel_canonical' );
		add_action( 'bp_head', 'bp_rel_canonical' );
	}
}
add_action( 'wp_head', '_bp_maybe_remove_rel_canonical', 8 );
