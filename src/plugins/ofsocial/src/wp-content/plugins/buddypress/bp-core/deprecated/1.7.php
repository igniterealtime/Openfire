<?php
/**
 * Deprecated Functions
 *
 * @package BuddyPress
 * @subpackage Core
 * @deprecated Since 1.7.0
 */

// Exit if accessed directly
defined( 'ABSPATH' ) || exit;

/**
 * Output the BuddyPress maintenance mode
 *
 * @since 1.6.0
 * @deprecated 1.7.0
 * @uses bp_get_maintenance_mode() To get the BuddyPress maintenance mode
 */
function bp_maintenance_mode() {
	echo bp_get_maintenance_mode();
}
	/**
	 * Return the BuddyPress maintenance mode
	 *
	 * @since 1.6.0
	 * @deprecated 1.7.0
	 * @return string The BuddyPress maintenance mode
	 */
	function bp_get_maintenance_mode() {
		return buddypress()->maintenance_mode;
	}

/**
 * @deprecated 1.7.0
 */
function xprofile_get_profile() {
	_deprecated_function( __FUNCTION__, '1.7' );
	bp_locate_template( array( 'profile/profile-loop.php' ), true );
}

/**
 * @deprecated 1.7.0
 */
function bp_get_profile_header() {
	_deprecated_function( __FUNCTION__, '1.7' );
	bp_locate_template( array( 'profile/profile-header.php' ), true );
}

/**
 * @deprecated 1.7.0
 * @param string $component_name
 * @return boolean
 */
function bp_exists( $component_name ) {
	_deprecated_function( __FUNCTION__, '1.7' );
	if ( function_exists( $component_name . '_install' ) )
		return true;

	return false;
}

/**
 * @deprecated 1.7.0
 */
function bp_get_plugin_sidebar() {
	_deprecated_function( __FUNCTION__, '1.7' );
	bp_locate_template( array( 'plugin-sidebar.php' ), true );
}

/**
 * On multiblog installations you must first allow themes to be activated and
 * show up on the theme selection screen. This function will let the BuddyPress
 * bundled themes show up on the root blog selection screen and bypass this
 * step. It also means that the themes won't show for selection on other blogs.
 *
 * @deprecated 1.7.0
 * @package BuddyPress Core
 */
function bp_core_allow_default_theme( $themes ) {
	_deprecated_function( __FUNCTION__, '1.7' );

	if ( !bp_current_user_can( 'bp_moderate' ) )
		return $themes;

	if ( bp_get_root_blog_id() != get_current_blog_id() )
		return $themes;

	if ( isset( $themes['bp-default'] ) )
		return $themes;

	$themes['bp-default'] = true;

	return $themes;
}

/**
 * No longer used by BuddyPress core
 *
 * @deprecated 1.7.0
 * @param string $page
 * @return boolean True if is BuddyPress page
 */
function bp_is_page( $page = '' ) {
	_deprecated_function( __FUNCTION__, '1.7' );

	if ( !bp_is_user() && bp_is_current_component( $page )  )
		return true;

	if ( 'home' == $page )
		return is_front_page();

	return false;
}

/** Admin *********************************************************************/

/**
 * This function was originally used to update pre-1.1 schemas, but that was
 * before we had a legitimate update process.
 *
 * @deprecated 1.7.0
 * @global WPDB $wpdb
 */
function bp_update_db_stuff() {
	global $wpdb;

	$bp        = buddypress();
	$bp_prefix = bp_core_get_table_prefix();

	// Rename the old user activity cached table if needed.
	if ( $wpdb->get_var( "SHOW TABLES LIKE '%{$bp_prefix}bp_activity_user_activity_cached%'" ) ) {
		$wpdb->query( "RENAME TABLE {$bp_prefix}bp_activity_user_activity_cached TO {$bp->activity->table_name}" );
	}

	// Rename fields from pre BP 1.2
	if ( $wpdb->get_var( "SHOW TABLES LIKE '%{$bp->activity->table_name}%'" ) ) {
		if ( $wpdb->get_var( "SHOW COLUMNS FROM {$bp->activity->table_name} LIKE 'component_action'" ) ) {
			$wpdb->query( "ALTER TABLE {$bp->activity->table_name} CHANGE component_action type varchar(75) NOT NULL" );
		}

		if ( $wpdb->get_var( "SHOW COLUMNS FROM {$bp->activity->table_name} LIKE 'component_name'" ) ) {
			$wpdb->query( "ALTER TABLE {$bp->activity->table_name} CHANGE component_name component varchar(75) NOT NULL" );
		}
	}

	// On first installation - record all existing blogs in the system.
	if ( !(int) $bp->site_options['bp-blogs-first-install'] ) {
		bp_blogs_record_existing_blogs();
		bp_update_option( 'bp-blogs-first-install', 1 );
	}

	if ( is_multisite() ) {
		bp_core_add_illegal_names();
	}

	// Update and remove the message threads table if it exists
	if ( $wpdb->get_var( "SHOW TABLES LIKE '%{$bp_prefix}bp_messages_threads%'" ) ) {
		if ( BP_Messages_Thread::update_tables() ) {
			$wpdb->query( "DROP TABLE {$bp_prefix}bp_messages_threads" );
		}
	}
}
