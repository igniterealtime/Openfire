<?php
/**
 * BuddyPress Options.
 *
 * @package BuddyPress
 * @subpackage Options
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Get the default site options and their values.
 *
 * @since 1.6.0
 *
 * @return array Filtered option names and values.
 */
function bp_get_default_options() {

	// Default options
	$options = array (

		/** Components ********************************************************/

		'bp-deactivated-components'            => array(),

		/** bbPress ***********************************************************/

		// Legacy bbPress config location
		'bb-config-location'                   => ABSPATH . 'bb-config.php',

		/** XProfile **********************************************************/

		// Base profile groups name
		'bp-xprofile-base-group-name'          => 'Base',

		// Base fullname field name
		'bp-xprofile-fullname-field-name'      => 'Name',

		/** Blogs *************************************************************/

		// Used to decide if blogs need indexing
		'bp-blogs-first-install'               => false,

		/** Settings **********************************************************/

		// Disable the WP to BP profile sync
		'bp-disable-profile-sync'              => false,

		// Hide the Toolbar for logged out users
		'hide-loggedout-adminbar'              => false,

		// Avatar uploads
		'bp-disable-avatar-uploads'            => false,

		// Cover image uploads
		'bp-disable-cover-image-uploads'       => false,

		// Group Profile Photos
		'bp-disable-group-avatar-uploads'      => false,

		// Group Cover image uploads
		'bp-disable-group-cover-image-uploads' => false,

		// Allow users to delete their own accounts
		'bp-disable-account-deletion'          => false,

		// Allow comments on blog and forum activity items
		'bp-disable-blogforum-comments'        => true,

		// The ID for the current theme package.
		'_bp_theme_package_id'                 => 'legacy',

		/** Groups ************************************************************/

		// @todo Move this into the groups component

		// Restrict group creation to super admins
		'bp_restrict_group_creation'           => false,

		/** Akismet ***********************************************************/

		// Users from all sites can post
		'_bp_enable_akismet'                   => true,

		/** Activity HeartBeat ************************************************/

		// HeartBeat is on to refresh activities
		'_bp_enable_heartbeat_refresh'         => true,

		/** BuddyBar **********************************************************/

		// Force the BuddyBar
		'_bp_force_buddybar'                   => false,

		/** Legacy theme *********************************************/

		// Whether to register the bp-default themes directory
		'_bp_retain_bp_default'                => false,

		/** Widgets **************************************************/
		'widget_bp_core_login_widget'                => false,
		'widget_bp_core_members_widget'              => false,
		'widget_bp_core_whos_online_widget'          => false,
		'widget_bp_core_recently_active_widget'      => false,
		'widget_bp_groups_widget'                    => false,
		'widget_bp_messages_sitewide_notices_widget' => false,
	);

	/**
	 * Filters the default options to be set upon activation.
	 *
	 * @since 1.6.0
	 *
	 * @param array $options Array of default options to set.
	 */
	return apply_filters( 'bp_get_default_options', $options );
}

/**
 * Add default options when BuddyPress is first activated.
 *
 * Only called once when BuddyPress is activated.
 * Non-destructive, so existing settings will not be overridden.
 *
 * @since 1.6.0
 *
 * @uses bp_get_default_options() To get default options.
 * @uses add_option() Adds default options.
 * @uses do_action() Calls 'bp_add_options'.
 */
function bp_add_options() {

	// Get the default options and values
	$options = bp_get_default_options();

	// Add default options
	foreach ( $options as $key => $value ) {
		bp_add_option( $key, $value );
	}

	/**
	 * Fires after the addition of default options when BuddyPress is first activated.
	 *
	 * Allows previously activated plugins to append their own options.
	 *
	 * @since 1.6.0
	 */
	do_action( 'bp_add_options' );
}

/**
 * Delete default options.
 *
 * Hooked to bp_uninstall, it is only called once when BuddyPress is uninstalled.
 * This is destructive, so existing settings will be destroyed.
 *
 * Currently unused.
 *
 * @since 1.6.0
 *
 * @uses bp_get_default_options() To get default options.
 * @uses delete_option() Removes default options.
 * @uses do_action() Calls 'bp_delete_options'.
 */
function bp_delete_options() {

	// Get the default options and values
	$options = bp_get_default_options();

	// Add default options
	foreach ( array_keys( $options ) as $key ) {
		delete_option( $key );
	}

	/**
	 * Fires after the deletion of default options when BuddyPress is first deactivated.
	 *
	 * Allows previously activated plugins to append their own options.
	 *
	 * @since 1.6.0
	 */
	do_action( 'bp_delete_options' );
}

/**
 * Add filters to each BP option, allowing them to be overloaded from inside the $bp->options array.
 *
 * Currently unused.
 *
 * @since 1.6.0
 *
 * @uses bp_get_default_options() To get default options.
 * @uses add_filter() To add filters to 'pre_option_{$key}'.
 * @uses do_action() Calls 'bp_add_option_filters'.
 */
function bp_setup_option_filters() {

	// Get the default options and values
	$options = bp_get_default_options();

	// Add filters to each BuddyPress option
	foreach ( array_keys( $options ) as $key ) {
		add_filter( 'pre_option_' . $key, 'bp_pre_get_option' );
	}

	/**
	 * Fires after the addition of filters to each BuddyPress option.
	 *
	 * Allows previously activated plugins to append their own options.
	 *
	 * @since 1.6.0
	 */
	do_action( 'bp_setup_option_filters' );
}

/**
 * Filter default options and allow them to be overloaded from inside the $bp->options array.
 *
 * Currently unused.
 *
 * @since 1.6.0
 *
 * @param bool $value Optional. Default value false.
 *
 * @return mixed False if not overloaded, mixed if set.
 */
function bp_pre_get_option( $value = false ) {
	$bp = buddypress();

	// Get the name of the current filter so we can manipulate it
	$filter = current_filter();

	// Remove the filter prefix
	$option = str_replace( 'pre_option_', '', $filter );

	// Check the options global for preset value
	if ( ! empty( $bp->options[ $option ] ) ) {
		$value = $bp->options[ $option ];
	}

	// Always return a value, even if false
	return $value;
}

/**
 * Retrieve an option.
 *
 * This is a wrapper for {@link get_blog_option()}, which in turn stores settings data
 * (such as bp-pages) on the appropriate blog, given your current setup.
 *
 * The 'bp_get_option' filter is primarily for backward-compatibility.
 *
 * @since 1.2.0
 *
 * @uses bp_get_root_blog_id()
 *
 * @param string $option_name The option to be retrieved.
 * @param string $default     Optional. Default value to be returned if the option
 *                            isn't set. See {@link get_blog_option()}.
 *
 * @return mixed The value for the option.
 */
function bp_get_option( $option_name, $default = '' ) {
	$value = get_blog_option( bp_get_root_blog_id(), $option_name, $default );

	/**
	 * Filters the option value for the requested option.
	 *
	 * @since 1.2.0
	 *
	 * @param mixed $value The value for the option.
	 */
	return apply_filters( 'bp_get_option', $value );
}

/**
 * Add an option.
 *
 * This is a wrapper for {@link add_blog_option()}, which in turn stores
 * settings data on the appropriate blog, given your current setup.
 *
 * @since 2.0.0
 *
 * @param string $option_name The option key to be set.
 * @param mixed  $value       The value to be set.
 *
 * @return bool True on success, false on failure.
 */
function bp_add_option( $option_name, $value ) {
	return add_blog_option( bp_get_root_blog_id(), $option_name, $value );
}

/**
 * Save an option.
 *
 * This is a wrapper for {@link update_blog_option()}, which in turn stores
 * settings data (such as bp-pages) on the appropriate blog, given your current
 * setup.
 *
 * @since 1.5.0
 *
 * @uses bp_get_root_blog_id()
 *
 * @param string $option_name The option key to be set.
 * @param string $value       The value to be set.
 *
 * @return bool True on success, false on failure.
 */
function bp_update_option( $option_name, $value ) {
	return update_blog_option( bp_get_root_blog_id(), $option_name, $value );
}

/**
 * Delete an option.
 *
 * This is a wrapper for {@link delete_blog_option()}, which in turn deletes
 * settings data (such as bp-pages) on the appropriate blog, given your current
 * setup.
 *
 * @since 1.5.0
 *
 * @uses bp_get_root_blog_id()
 *
 * @param string $option_name The option key to be deleted.
 *
 * @return bool True on success, false on failure.
 */
function bp_delete_option( $option_name ) {
	return delete_blog_option( bp_get_root_blog_id(), $option_name );
}

/**
 * Copy BP options from a single site to multisite config.
 *
 * Run when switching from single to multisite and we need to copy blog options
 * to site options.
 *
 * This function is no longer used.
 *
 * @deprecated 1.6.0
 *
 * @param array $keys
 *
 * @return bool
 */
function bp_core_activate_site_options( $keys = array() ) {

	if ( !empty( $keys ) && is_array( $keys ) ) {
		$bp = buddypress();

		$errors = false;

		foreach ( $keys as $key => $default ) {
			if ( empty( $bp->site_options[ $key ] ) ) {
				$bp->site_options[ $key ] = bp_get_option( $key, $default );

				if ( !bp_update_option( $key, $bp->site_options[ $key ] ) ) {
					$errors = true;
				}
			}
		}

		if ( empty( $errors ) ) {
			return true;
		}
	}

	return false;
}

/**
 * Fetch global BP options.
 *
 * BuddyPress uses common options to store configuration settings. Many of these
 * settings are needed at run time. Instead of fetching them all and adding many
 * initial queries to each page load, let's fetch them all in one go.
 *
 * @todo Use settings API and audit these methods.
 *
 * @return array $root_blog_options_meta List of options.
 */
function bp_core_get_root_options() {
	global $wpdb;

	// Get all the BuddyPress settings, and a few useful WP ones too
	$root_blog_options                   = bp_get_default_options();
	$root_blog_options['registration']   = '0';
	$root_blog_options['avatar_default'] = 'mysteryman';
	$root_blog_option_keys               = array_keys( $root_blog_options );

	// Do some magic to get all the root blog options in 1 swoop
	// Check cache first - We cache here instead of using the standard WP
	// settings cache because the current blog may not be the root blog,
	// and it's not practical to access the cache across blogs
	$root_blog_options_meta = wp_cache_get( 'root_blog_options', 'bp' );

	if ( false === $root_blog_options_meta ) {
		$blog_options_keys      = "'" . join( "', '", (array) $root_blog_option_keys ) . "'";
		$blog_options_table	    = bp_is_multiblog_mode() ? $wpdb->options : $wpdb->get_blog_prefix( bp_get_root_blog_id() ) . 'options';
		$blog_options_query     = "SELECT option_name AS name, option_value AS value FROM {$blog_options_table} WHERE option_name IN ( {$blog_options_keys} )";
		$root_blog_options_meta = $wpdb->get_results( $blog_options_query );

		// On Multisite installations, some options must always be fetched from sitemeta
		if ( is_multisite() ) {

			/**
			 * Filters multisite options retrieved from sitemeta.
			 *
			 * @since 1.5.0
			 *
			 * @param array $value Array of multisite options from sitemeta table.
			 */
			$network_options = apply_filters( 'bp_core_network_options', array(
				'tags_blog_id'       => '0',
				'sitewide_tags_blog' => '',
				'registration'       => '0',
				'fileupload_maxk'    => '1500'
			) );

			$current_site           = get_current_site();
			$network_option_keys    = array_keys( $network_options );
			$sitemeta_options_keys  = "'" . join( "', '", (array) $network_option_keys ) . "'";
			$sitemeta_options_query = $wpdb->prepare( "SELECT meta_key AS name, meta_value AS value FROM {$wpdb->sitemeta} WHERE meta_key IN ( {$sitemeta_options_keys} ) AND site_id = %d", $current_site->id );
			$network_options_meta   = $wpdb->get_results( $sitemeta_options_query );

			// Sitemeta comes second in the merge, so that network 'registration' value wins
			$root_blog_options_meta = array_merge( $root_blog_options_meta, $network_options_meta );
		}

		// Missing some options, so do some one-time fixing
		if ( empty( $root_blog_options_meta ) || ( count( $root_blog_options_meta ) < count( $root_blog_option_keys ) ) ) {

			// Get a list of the keys that are already populated
			$existing_options = array();
			foreach( $root_blog_options_meta as $already_option ) {
				$existing_options[$already_option->name] = $already_option->value;
			}

			// Unset the query - We'll be resetting it soon
			unset( $root_blog_options_meta );

			// Loop through options
			foreach ( $root_blog_options as $old_meta_key => $old_meta_default ) {

				if ( isset( $existing_options[$old_meta_key] ) ) {
					continue;
				}

				// Get old site option
				if ( is_multisite() ) {
					$old_meta_value = get_site_option( $old_meta_key );
				}

				// No site option so look in root blog
				if ( empty( $old_meta_value ) ) {
					$old_meta_value = bp_get_option( $old_meta_key, $old_meta_default );
				}

				// Update the root blog option
				bp_update_option( $old_meta_key, $old_meta_value );

				// Update the global array
				$root_blog_options_meta[$old_meta_key] = $old_meta_value;

				// Clear out the value for the next time around
				unset( $old_meta_value );
			}

			$root_blog_options_meta = array_merge( $root_blog_options_meta, $existing_options );
			unset( $existing_options );

		// We're all matched up
		} else {
			// Loop through our results and make them usable
			foreach ( $root_blog_options_meta as $root_blog_option ) {
				$root_blog_options[$root_blog_option->name] = $root_blog_option->value;
			}

			// Copy the options no the return val
			$root_blog_options_meta = $root_blog_options;

			// Clean up our temporary copy
			unset( $root_blog_options );
		}

		wp_cache_set( 'root_blog_options', $root_blog_options_meta, 'bp' );
	}

	/**
	 * Filters the global BP options.
	 *
	 * @since 1.5.0
	 *
	 * @param array $root_blog_options_meta Array of global BP options.
	 */
	return apply_filters( 'bp_core_get_root_options', $root_blog_options_meta );
}

/**
 * Get a root option.
 *
 * "Root options" are those that apply across an entire installation, and are fetched only a single
 * time during a pageload and stored in `buddypress()->site_options` to prevent future lookups.
 * See {@see bp_core_get_root_options()}.
 *
 * @since 2.3.0
 *
 * @param  string $option Name of the option key.
 *
 * @return mixed Value, if found.
 */
function bp_core_get_root_option( $option ) {
	$bp = buddypress();

	if ( ! isset( $bp->site_options ) ) {
		$bp->site_options = bp_core_get_root_options();
	}

	$value = '';
	if ( isset( $bp->site_options[ $option ] ) ) {
		$value = $bp->site_options[ $option ];
	}

	return $value;
}

/** Active? *******************************************************************/

/**
 * Is profile syncing disabled?
 *
 * @since 1.6.0
 *
 * @uses bp_get_option() To get the profile sync option.
 *
 * @param bool $default Optional. Fallback value if not found in the database.
 *                      Default: true.
 *
 * @return bool True if profile sync is enabled, otherwise false.
 */
function bp_disable_profile_sync( $default = false ) {

	/**
	 * Filters whether or not profile syncing is disabled.
	 *
	 * @since 1.6.0
	 *
	 * @param bool $value Whether or not syncing is disabled.
	 */
	return (bool) apply_filters( 'bp_disable_profile_sync', (bool) bp_get_option( 'bp-disable-profile-sync', $default ) );
}

/**
 * Is the Toolbar hidden for logged out users?
 *
 * @since 1.6.0
 *
 * @uses bp_get_option() To get the logged out Toolbar option.
 *
 * @param bool $default Optional. Fallback value if not found in the database.
 *                      Default: true.
 *
 * @return bool True if the admin bar should be hidden for logged-out users,
 *              otherwise false.
 */
function bp_hide_loggedout_adminbar( $default = true ) {

	/**
	 * Filters whether or not the toolbar is hidden for logged out users.
	 *
	 * @since 1.6.0
	 *
	 * @param bool $value Whether or not the toolbar is hidden.
	 */
	return (bool) apply_filters( 'bp_hide_loggedout_adminbar', (bool) bp_get_option( 'hide-loggedout-adminbar', $default ) );
}

/**
 * Are members able to upload their own avatars?
 *
 * @since 1.6.0
 *
 * @uses bp_get_option() To get the avatar uploads option.
 *
 * @param bool $default Optional. Fallback value if not found in the database.
 *                      Default: true.
 *
 * @return bool True if avatar uploads are disabled, otherwise false.
 */
function bp_disable_avatar_uploads( $default = true ) {

	/**
	 * Filters whether or not members are able to upload their own avatars.
	 *
	 * @since 1.6.0
	 *
	 * @param bool $value Whether or not members are able to upload their own avatars.
	 */
	return (bool) apply_filters( 'bp_disable_avatar_uploads', (bool) bp_get_option( 'bp-disable-avatar-uploads', $default ) );
}

/**
 * Are members able to upload their own cover images?
 *
 * @since 2.4.0
 *
 * @uses bp_get_option() To get the cover image uploads option.
 *
 * @param bool $default Optional. Fallback value if not found in the database.
 *                      Default: false.
 *
 * @return bool True if cover image uploads are disabled, otherwise false.
 */
function bp_disable_cover_image_uploads( $default = false ) {

	/**
	 * Filters whether or not members are able to upload their own cover images.
	 *
	 * @since 2.4.0
	 *
	 * @param bool $value Whether or not members are able to upload their own cover images.
	 */
	return (bool) apply_filters( 'bp_disable_cover_image_uploads', (bool) bp_get_option( 'bp-disable-cover-image-uploads', $default ) );
}

/**
 * Are group avatars disabled?
 *
 * For backward compatibility, this option falls back on the value of 'bp-disable-avatar-uploads' when no value is
 * found in the database.
 *
 * @since 2.3.0
 *
 * @param bool|null $default Optional. Fallback value if not found in the database.
 *                           Defaults to the value of `bp_disable_avatar_uploads()`.
 *
 * @return bool True if group avatar uploads are disabled, otherwise false.
 */
function bp_disable_group_avatar_uploads( $default = null ) {
	$disabled = bp_get_option( 'bp-disable-group-avatar-uploads', '' );

	if ( '' === $disabled ) {
		if ( is_null( $default ) ) {
			$disabled = bp_disable_avatar_uploads();
		} else {
			$disabled = $default;
		}
	}

	/**
	 * Filters whether or not members are able to upload group avatars.
	 *
	 * @since 2.3.0
	 *
	 * @param bool $disabled Whether or not members are able to upload their groups avatars.
	 * @param bool $default  Default value passed to the function.
	 */
	return (bool) apply_filters( 'bp_disable_group_avatar_uploads', $disabled, $default );
}

/**
 * Are group cover images disabled?
 *
 * @since 2.4.0
 *
 * @uses bp_get_option() To get the group cover image uploads option.
 *
 * @param bool $default Optional. Fallback value if not found in the database.
 *                      Default: false.
 *
 * @return bool True if group cover image uploads are disabled, otherwise false.
 */
function bp_disable_group_cover_image_uploads( $default = false ) {

	/**
	 * Filters whether or not members are able to upload group cover images.
	 *
	 * @since 2.4.0
	 *
	 * @param bool $value Whether or not members are able to upload thier groups cover images.
	 */
	return (bool) apply_filters( 'bp_disable_group_cover_image_uploads', (bool) bp_get_option( 'bp-disable-group-cover-image-uploads', $default ) );
}

/**
 * Are members able to delete their own accounts?
 *
 * @since 1.6.0
 *
 * @uses bp_get_option() To get the account deletion option.
 *
 * @param bool $default Optional. Fallback value if not found in the database.
 *                      Default: true.
 *
 * @return bool True if users are able to delete their own accounts, otherwise
 *              false.
 */
function bp_disable_account_deletion( $default = false ) {

	/**
	 * Filters whether or not members are able to delete their own accounts.
	 *
	 * @since 1.6.0
	 *
	 * @param bool $value Whether or not members are able to delete their own accounts.
	 */
	return apply_filters( 'bp_disable_account_deletion', (bool) bp_get_option( 'bp-disable-account-deletion', $default ) );
}

/**
 * Are blog and forum activity stream comments disabled?
 *
 * @since 1.6.0
 *
 * @todo split and move into blog and forum components.
 * @uses bp_get_option() To get the blog/forum comments option.
 *
 * @param bool $default Optional. Fallback value if not found in the database.
 *                      Default: false.
 *
 * @return bool True if activity comments are disabled for blog and forum
 *              items, otherwise false.
 */
function bp_disable_blogforum_comments( $default = false ) {

	/**
	 * Filters whether or not blog and forum activity stream comments are disabled.
	 *
	 * @since 1.6.0
	 *
	 * @param bool $value Whether or not blog and forum activity stream comments are disabled.
	 */
	return (bool) apply_filters( 'bp_disable_blogforum_comments', (bool) bp_get_option( 'bp-disable-blogforum-comments', $default ) );
}

/**
 * Is group creation turned off?
 *
 * @since 1.6.0
 *
 * @todo Move into groups component.
 * @uses bp_get_option() To get the group creation.
 *
 * @param bool $default Optional. Fallback value if not found in the database.
 *                      Default: true.
 *
 * @return bool True if group creation is restricted, otherwise false.
 */
function bp_restrict_group_creation( $default = true ) {

	/**
	 * Filters whether or not group creation is turned off.
	 *
	 * @since 1.6.0
	 *
	 * @param bool $value Whether or not group creation is turned off.
	 */
	return (bool) apply_filters( 'bp_restrict_group_creation', (bool) bp_get_option( 'bp_restrict_group_creation', $default ) );
}

/**
 * Should the old BuddyBar be forced in place of the WP admin bar?
 *
 * @since 1.6.0
 *
 * @uses bp_get_option() To get the BuddyBar option.
 *
 * @param bool $default Optional. Fallback value if not found in the database.
 *                      Default: true.
 *
 * @return bool True if the BuddyBar should be forced on, otherwise false.
 */
function bp_force_buddybar( $default = true ) {

	/**
	 * Filters whether or not BuddyBar should be forced in place of WP Admin Bar.
	 *
	 * @since 1.6.0
	 *
	 * @param bool $value Whether or not BuddyBar should be forced in place of WP Admin Bar.
	 */
	return (bool) apply_filters( 'bp_force_buddybar', (bool) bp_get_option( '_bp_force_buddybar', $default ) );
}

/**
 * Output the group forums root parent forum id.
 *
 * @since 1.6.0
 *
 * @param bool|string $default Optional. Default: '0'.
 */
function bp_group_forums_root_id( $default = '0' ) {
	echo bp_get_group_forums_root_id( $default );
}
	/**
	 * Return the group forums root parent forum id.
	 *
	 * @since 1.6.0
	 *
	 * @uses bp_get_option() To get the root forum ID from the database.
	 *
	 * @param bool|string $default Optional. Default: '0'.
	 *
	 * @return int The ID of the group forums root forum.
	 */
	function bp_get_group_forums_root_id( $default = '0' ) {

		/**
		 * Filters the group forums root parent forum id.
		 *
		 * @since 1.6.0
		 *
		 * @param int $value The group forums root parent forum id.
		 */
		return (int) apply_filters( 'bp_get_group_forums_root_id', (int) bp_get_option( '_bp_group_forums_root_id', $default ) );
	}

/**
 * Check whether BuddyPress Group Forums are enabled.
 *
 * @since 1.6.0
 *
 * @uses bp_get_option() To get the group forums option.
 *
 * @param bool $default Optional. Fallback value if not found in the database.
 *                      Default: true.
 *
 * @return bool True if group forums are active, otherwise false.
 */
function bp_is_group_forums_active( $default = true ) {

	/**
	 * Filters whether or not BuddyPress Group Forums are enabled.
	 *
	 * @since 1.6.0
	 *
	 * @param bool $value Whether or not BuddyPress Group Forums are enabled.
	 */
	return (bool) apply_filters( 'bp_is_group_forums_active', (bool) bp_get_option( '_bp_enable_group_forums', $default ) );
}

/**
 * Check whether Akismet is enabled.
 *
 * @since 1.6.0
 *
 * @uses bp_get_option() To get the Akismet option.
 *
 * @param bool $default Optional. Fallback value if not found in the database.
 *                      Default: true.
 *
 * @return bool True if Akismet is enabled, otherwise false.
 */
function bp_is_akismet_active( $default = true ) {

	/**
	 * Filters whether or not Akismet is enabled.
	 *
	 * @since 1.6.0
	 *
	 * @param bool $value Whether or not Akismet is enabled.
	 */
	return (bool) apply_filters( 'bp_is_akismet_active', (bool) bp_get_option( '_bp_enable_akismet', $default ) );
}

/**
 * Check whether Activity Heartbeat refresh is enabled.
 *
 * @since 2.0.0
 *
 * @uses bp_get_option() To get the Heartbeat option.
 *
 * @param bool $default Optional. Fallback value if not found in the database.
 *                      Default: true.
 *
 * @return bool True if Heartbeat refresh is enabled, otherwise false.
 */
function bp_is_activity_heartbeat_active( $default = true ) {

	/**
	 * Filters whether or not Activity Heartbeat refresh is enabled.
	 *
	 * @since 2.0.0
	 *
	 * @param bool $value Whether or not Activity Heartbeat refresh is enabled.
	 */
	return (bool) apply_filters( 'bp_is_activity_heartbeat_active', (bool) bp_get_option( '_bp_enable_heartbeat_refresh', $default ) );
}

/**
 * Get the current theme package ID.
 *
 * @since 1.7.0
 *
 * @uses get_option() To get the theme package option.
 *
 * @param string $default Optional. Fallback value if not found in the database.
 *                        Default: 'legacy'.
 *
 * @return string ID of the theme package.
 */
function bp_get_theme_package_id( $default = 'legacy' ) {

	/**
	 * Filters the current theme package ID.
	 *
	 * @since 1.7.0
	 *
	 * @param string $value The current theme package ID.
	 */
	return apply_filters( 'bp_get_theme_package_id', bp_get_option( '_bp_theme_package_id', $default ) );
}
