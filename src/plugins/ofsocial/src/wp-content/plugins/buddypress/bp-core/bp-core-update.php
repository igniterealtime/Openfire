<?php
/**
 * BuddyPress Updater.
 *
 * @package BuddyPress
 * @subpackage Updater
 */

// Exit if accessed directly
defined( 'ABSPATH' ) || exit;

/**
 * Is this a fresh installation of BuddyPress?
 *
 * If there is no raw DB version, we infer that this is the first installation.
 *
 * @since 1.7.0
 *
 * @uses get_option()
 * @uses bp_get_db_version() To get BuddyPress's database version.
 *
 * @return bool True if this is a fresh BP install, otherwise false.
 */
function bp_is_install() {
    return ! bp_get_db_version_raw();
}

/**
 * Is this a BuddyPress update?
 *
 * Determined by comparing the registered BuddyPress version to the version
 * number stored in the database. If the registered version is greater, it's
 * an update.
 *
 * @since 1.6.0
 *
 * @uses get_option()
 * @uses bp_get_db_version() To get BuddyPress's database version.
 *
 * @return bool True if update, otherwise false.
 */
function bp_is_update() {

    // Current DB version of this site (per site in a multisite network)
    $current_db   = bp_get_option( '_bp_db_version' );
    $current_live = bp_get_db_version();

    // Compare versions (cast as int and bool to be safe)
    $is_update = (bool) ( (int) $current_db < (int) $current_live );

    // Return the product of version comparison
    return $is_update;
}

/**
 * Determine whether BuddyPress is in the process of being activated.
 *
 * @since 1.6.0
 *
 * @uses buddypress()
 *
 * @param string $basename BuddyPress basename.
 *
 * @return bool True if activating BuddyPress, false if not.
 */
function bp_is_activation( $basename = '' ) {
    $bp     = buddypress();
    $action = false;

    if ( ! empty( $_REQUEST['action'] ) && ( '-1' != $_REQUEST['action'] ) ) {
        $action = $_REQUEST['action'];
    } elseif ( ! empty( $_REQUEST['action2'] ) && ( '-1' != $_REQUEST['action2'] ) ) {
        $action = $_REQUEST['action2'];
    }

    // Bail if not activating
    if ( empty( $action ) || !in_array( $action, array( 'activate', 'activate-selected' ) ) ) {
        return false;
    }

    // The plugin(s) being activated
    if ( $action == 'activate' ) {
        $plugins = isset( $_GET['plugin'] ) ? array( $_GET['plugin'] ) : array();
    } else {
        $plugins = isset( $_POST['checked'] ) ? (array) $_POST['checked'] : array();
    }

    // Set basename if empty
    if ( empty( $basename ) && !empty( $bp->basename ) ) {
        $basename = $bp->basename;
    }

    // Bail if no basename
    if ( empty( $basename ) ) {
        return false;
    }

    // Is BuddyPress being activated?
    return in_array( $basename, $plugins );
}

/**
 * Determine whether BuddyPress is in the process of being deactivated.
 *
 * @since 1.6.0
 *
 * @uses buddypress()
 *
 * @param string $basename BuddyPress basename.
 *
 * @return bool True if deactivating BuddyPress, false if not.
 */
function bp_is_deactivation( $basename = '' ) {
    $bp     = buddypress();
    $action = false;

    if ( ! empty( $_REQUEST['action'] ) && ( '-1' != $_REQUEST['action'] ) ) {
        $action = $_REQUEST['action'];
    } elseif ( ! empty( $_REQUEST['action2'] ) && ( '-1' != $_REQUEST['action2'] ) ) {
        $action = $_REQUEST['action2'];
    }

    // Bail if not deactivating
    if ( empty( $action ) || !in_array( $action, array( 'deactivate', 'deactivate-selected' ) ) ) {
        return false;
    }

    // The plugin(s) being deactivated
    if ( 'deactivate' == $action ) {
        $plugins = isset( $_GET['plugin'] ) ? array( $_GET['plugin'] ) : array();
    } else {
        $plugins = isset( $_POST['checked'] ) ? (array) $_POST['checked'] : array();
    }

    // Set basename if empty
    if ( empty( $basename ) && !empty( $bp->basename ) ) {
        $basename = $bp->basename;
    }

    // Bail if no basename
    if ( empty( $basename ) ) {
        return false;
    }

    // Is bbPress being deactivated?
    return in_array( $basename, $plugins );
}

/**
 * Update the BP version stored in the database to the current version.
 *
 * @since 1.6.0
 *
 * @uses bp_get_db_version() To get BuddyPress's database version.
 * @uses bp_update_option() To update BuddyPress's database version.
 */
function bp_version_bump() {
    bp_update_option( '_bp_db_version', bp_get_db_version() );
}

/**
 * Set up the BuddyPress updater.
 *
 * @since 1.6.0
 */
function bp_setup_updater() {

    // Are we running an outdated version of BuddyPress?
    if ( ! bp_is_update() ) {
        return;
    }

    bp_version_updater();
}

/**
 * Initialize an update or installation of BuddyPress.
 *
 * BuddyPress's version updater looks at what the current database version is,
 * and runs whatever other code is needed - either the "update" or "install"
 * code.
 *
 * This is most often used when the data schema changes, but should also be used
 * to correct issues with BuddyPress metadata silently on software update.
 *
 * @since 1.7.0
 */
function bp_version_updater() {

    // Get the raw database version
    $raw_db_version = (int) bp_get_db_version_raw();

    /**
     * Filters the default components to activate for a new install.
     *
     * @since 1.7.0
     *
     * @param array $value Array of default components to activate.
     */
    $default_components = apply_filters( 'bp_new_install_default_components', array(
        'activity'      => 1,
        'members'       => 1,
        'settings'      => 1,
        'xprofile'      => 1,
        'notifications' => 1,
    ) );

    require_once( buddypress()->plugin_dir . '/bp-core/admin/bp-core-admin-schema.php' );

    // Install BP schema and activate only Activity and XProfile
    if ( bp_is_install() ) {

        // Apply schema and set Activity and XProfile components as active
        bp_core_install( $default_components );
        bp_update_option( 'bp-active-components', $default_components );
        bp_core_add_page_mappings( $default_components, 'delete' );

    // Upgrades
    } else {

        // Run the schema install to update tables
        bp_core_install();

        // 1.5.0
        if ( $raw_db_version < 1801 ) {
            bp_update_to_1_5();
            bp_core_add_page_mappings( $default_components, 'delete' );
        }

        // 1.6.0
        if ( $raw_db_version < 6067 ) {
            bp_update_to_1_6();
        }

        // 1.9.0
        if ( $raw_db_version < 7553 ) {
            bp_update_to_1_9();
        }

        // 1.9.2
        if ( $raw_db_version < 7731 ) {
            bp_update_to_1_9_2();
        }

        // 2.0.0
        if ( $raw_db_version < 7892 ) {
            bp_update_to_2_0();
        }

        // 2.0.1
        if ( $raw_db_version < 8311 ) {
            bp_update_to_2_0_1();
        }

        // 2.2.0
        if ( $raw_db_version < 9181 ) {
            bp_update_to_2_2();
        }

        // 2.3.0
        if ( $raw_db_version < 9615 ) {
            bp_update_to_2_3();
        }
    }

    /** All done! *************************************************************/

    // Bump the version
    bp_version_bump();
}

/**
 * Perform database operations that must take place before the general schema upgrades.
 *
 * `dbDelta()` cannot handle certain operations - like changing indexes - so we do it here instead.
 *
 * @since 2.3.0
 */
function bp_pre_schema_upgrade() {
    global $wpdb;

    $raw_db_version = (int) bp_get_db_version_raw();
    $bp_prefix      = bp_core_get_table_prefix();

    // 2.3.0: Change index lengths to account for utf8mb4.
    if ( $raw_db_version < 9695 ) {
        // table_name => columns.
        $tables = array(
            $bp_prefix . 'bp_activity_meta'       => array( 'meta_key' ),
            $bp_prefix . 'bp_groups_groupmeta'    => array( 'meta_key' ),
            $bp_prefix . 'bp_messages_meta'       => array( 'meta_key' ),
            $bp_prefix . 'bp_notifications_meta'  => array( 'meta_key' ),
            $bp_prefix . 'bp_user_blogs_blogmeta' => array( 'meta_key' ),
            $bp_prefix . 'bp_xprofile_meta'       => array( 'meta_key' ),
        );

        foreach ( $tables as $table_name => $indexes ) {
            foreach ( $indexes as $index ) {
                if ( $wpdb->query( $wpdb->prepare( "SHOW TABLES LIKE %s", bp_esc_like( $table_name ) ) ) ) {
                    $wpdb->query( "ALTER TABLE {$table_name} DROP INDEX {$index}" );
                }
            }
        }
    }
}

/** Upgrade Routines **********************************************************/

/**
 * Remove unused metadata from database when upgrading from < 1.5.
 *
 * Database update methods based on version numbers.
 *
 * @since 1.7.0
 */
function bp_update_to_1_5() {

    // Delete old database version options
    delete_site_option( 'bp-activity-db-version' );
    delete_site_option( 'bp-blogs-db-version'    );
    delete_site_option( 'bp-friends-db-version'  );
    delete_site_option( 'bp-groups-db-version'   );
    delete_site_option( 'bp-messages-db-version' );
    delete_site_option( 'bp-xprofile-db-version' );
}

/**
 * Remove unused metadata from database when upgrading from < 1.6.
 *
 * Database update methods based on version numbers.
 *
 * @since 1.7.0
 */
function bp_update_to_1_6() {

    // Delete possible site options
    delete_site_option( 'bp-db-version'       );
    delete_site_option( '_bp_db_version'      );
    delete_site_option( 'bp-core-db-version'  );
    delete_site_option( '_bp-core-db-version' );

    // Delete possible blog options
    delete_blog_option( bp_get_root_blog_id(), 'bp-db-version'       );
    delete_blog_option( bp_get_root_blog_id(), 'bp-core-db-version'  );
    delete_site_option( bp_get_root_blog_id(), '_bp-core-db-version' );
    delete_site_option( bp_get_root_blog_id(), '_bp_db_version'      );
}

/**
 * Add the notifications component to active components.
 *
 * Notifications was added in 1.9.0, and previous installations will already
 * have the core notifications API active. We need to add the new Notifications
 * component to the active components option to retain existing functionality.
 *
 * @since 1.9.0
 */
function bp_update_to_1_9() {

    // Setup hardcoded keys
    $active_components_key      = 'bp-active-components';
    $notifications_component_id = 'notifications';

    // Get the active components
    $active_components          = bp_get_option( $active_components_key );

    // Add notifications
    if ( ! in_array( $notifications_component_id, $active_components ) ) {
        $active_components[ $notifications_component_id ] = 1;
    }

    // Update the active components option
    bp_update_option( $active_components_key, $active_components );
}

/**
 * Perform database updates for BP 1.9.2
 *
 * In 1.9, BuddyPress stopped registering its theme directory when it detected
 * that bp-default (or a child theme) was not currently being used, in effect
 * deprecating bp-default. However, this ended up causing problems when site
 * admins using bp-default would switch away from the theme temporarily:
 * bp-default would no longer be available, with no obvious way (outside of
 * a manual filter) to restore it. In 1.9.2, we add an option that flags
 * whether bp-default or a child theme is active at the time of upgrade; if so,
 * the theme directory will continue to be registered even if the theme is
 * deactivated temporarily. Thus, new installations will not see bp-default,
 * but legacy installations using the theme will continue to see it.
 *
 * @since 1.9.2
 */
function bp_update_to_1_9_2() {
    if ( 'bp-default' === get_stylesheet() || 'bp-default' === get_template() ) {
        update_site_option( '_bp_retain_bp_default', 1 );
    }
}

/**
 * 2.0 update routine.
 *
 * - Ensure that the activity tables are installed, for last_activity storage.
 * - Migrate last_activity data from usermeta to activity table.
 * - Add values for all BuddyPress options to the options table.
 *
 * @since 2.0.0
 */
function bp_update_to_2_0() {

    /** Install activity tables for 'last_activity' ***************************/

    bp_core_install_activity_streams();

    /** Migrate 'last_activity' data ******************************************/

    bp_last_activity_migrate();

    /** Migrate signups data **************************************************/

    if ( ! is_multisite() ) {

        // Maybe install the signups table
        bp_core_maybe_install_signups();

        // Run the migration script
        bp_members_migrate_signups();
    }

    /** Add BP options to the options table ***********************************/

    bp_add_options();
}

/**
 * 2.0.1 database upgrade routine.
 *
 * @since 2.0.1
 *
 * @return void
 */
function bp_update_to_2_0_1() {

    // We purposely call this during both the 2.0 upgrade and the 2.0.1 upgrade.
    // Don't worry; it won't break anything, and safely handles all cases.
    bp_core_maybe_install_signups();
}

/**
 * 2.2.0 update routine.
 *
 * - Add messages meta table.
 * - Update the component field of the 'new members' activity type.
 * - Clean up hidden friendship activities.
 *
 * @since 2.2.0
 */
function bp_update_to_2_2() {

    // Also handled by `bp_core_install()`
    if ( bp_is_active( 'messages' ) ) {
        bp_core_install_private_messaging();
    }

    if ( bp_is_active( 'activity' ) ) {
        bp_migrate_new_member_activity_component();

        if ( bp_is_active( 'friends' ) ) {
            bp_cleanup_friendship_activities();
        }
    }
}

/**
 * 2.3.0 update routine.
 *
 * - Add notifications meta table.
 *
 * @since 2.3.0
 */
function bp_update_to_2_3() {

    // Also handled by `bp_core_install()`
    if ( bp_is_active( 'notifications' ) ) {
        bp_core_install_notifications();
    }
}

/**
 * Updates the component field for new_members type.
 *
 * @since 2.2.0
 *
 * @global $wpdb
 * @uses   buddypress()
 *
 */
function bp_migrate_new_member_activity_component() {
    global $wpdb;
    $bp = buddypress();

    // Update the component for the new_member type
    $wpdb->update(
        // Activity table
        $bp->members->table_name_last_activity,
        array(
            'component' => $bp->members->id,
        ),
        array(
            'component' => 'xprofile',
            'type'      => 'new_member',
        ),
        // Data sanitization format
        array(
            '%s',
        ),
        // WHERE sanitization format
        array(
            '%s',
            '%s'
        )
    );
}

/**
 * Remove all hidden friendship activities.
 *
 * @since 2.2.0
 *
 * @uses bp_activity_delete() to delete the corresponding friendship activities.
 */
function bp_cleanup_friendship_activities() {
    bp_activity_delete( array(
        'component'     => buddypress()->friends->id,
        'type'          => 'friendship_created',
        'hide_sitewide' => true,
    ) );
 }

/**
 * Redirect user to BP's What's New page on first page load after activation.
 *
 * @since 1.7.0
 *
 * @internal Used internally to redirect BuddyPress to the about page on activation.
 *
 * @uses set_transient() To drop the activation transient for 30 seconds.
 */
function bp_add_activation_redirect() {

    // Bail if activating from network, or bulk
    if ( isset( $_GET['activate-multi'] ) ) {
        return;
    }

    // Record that this is a new installation, so we show the right
    // welcome message
    if ( bp_is_install() ) {
        set_transient( '_bp_is_new_install', true, 30 );
    }

    // Add the transient to redirect
    set_transient( '_bp_activation_redirect', true, 30 );
}

/** Signups *******************************************************************/

/**
 * Check if the signups table needs to be created or upgraded.
 *
 * @since 2.0.0
 *
 * @global WPDB $wpdb
 *
 * @return bool|null If signups table exists.
 */
function bp_core_maybe_install_signups() {
    global $wpdb;

    // The table to run queries against
    $signups_table = $wpdb->base_prefix . 'signups';

    // Suppress errors because users shouldn't see what happens next
    $old_suppress  = $wpdb->suppress_errors();

    // Never use bp_core_get_table_prefix() for any global users tables
    $table_exists  = (bool) $wpdb->get_results( "DESCRIBE {$signups_table};" );

    // Table already exists, so maybe upgrade instead?
    if ( true === $table_exists ) {

        // Look for the 'signup_id' column
        $column_exists = $wpdb->query( "SHOW COLUMNS FROM {$signups_table} LIKE 'signup_id'" );

        // 'signup_id' column doesn't exist, so run the upgrade
        if ( empty( $column_exists ) ) {
            bp_core_upgrade_signups();
        }

    // Table does not exist, and we are a single site, so install the multisite
    // signups table using WordPress core's database schema.
    } elseif ( ! is_multisite() ) {
        bp_core_install_signups();
    }

    // Restore previous error suppression setting
    $wpdb->suppress_errors( $old_suppress );
}

/** Activation Actions ********************************************************/

/**
 * Fire activation hooks and events.
 *
 * Runs on BuddyPress activation.
 *
 * @since 1.6.0
 *
 * @uses do_action() Calls 'bp_activation' hook.
 */
function bp_activation() {

    // Force refresh theme roots.
    delete_site_transient( 'theme_roots' );

    // Add options
    bp_add_options();

    /**
     * Fires during the activation of BuddyPress.
     *
     * Use as of (1.6.0)
     *
     * @since 1.6.0
     */
    do_action( 'bp_activation' );

    // @deprecated as of (1.6)
    do_action( 'bp_loader_activate' );
}

/**
 * Fire deactivation hooks and events.
 *
 * Runs on BuddyPress deactivation.
 *
 * @since 1.6.0
 *
 * @uses do_action() Calls 'bp_deactivation' hook.
 */
function bp_deactivation() {

    // Force refresh theme roots.
    delete_site_transient( 'theme_roots' );

    // Switch to WordPress's default theme if current parent or child theme
    // depend on bp-default. This is to prevent white screens of doom.
    if ( in_array( 'bp-default', array( get_template(), get_stylesheet() ) ) ) {
        switch_theme( WP_DEFAULT_THEME, WP_DEFAULT_THEME );
        update_option( 'template_root',   get_raw_theme_root( WP_DEFAULT_THEME, true ) );
        update_option( 'stylesheet_root', get_raw_theme_root( WP_DEFAULT_THEME, true ) );
    }

    /**
     * Fires during the deactivation of BuddyPress.
     *
     * Use as of (1.6.0)
     *
     * @since 1.6.0
     */
    do_action( 'bp_deactivation' );

    // @deprecated as of (1.6)
    do_action( 'bp_loader_deactivate' );
}

/**
 * Fire uninstall hook.
 *
 * Runs when uninstalling BuddyPress.
 *
 * @since 1.6.0
 *
 * @uses do_action() Calls 'bp_uninstall' hook.
 */
function bp_uninstall() {

    /**
     * Fires during the uninstallation of BuddyPress.
     *
     * @since 1.6.0
     */
    do_action( 'bp_uninstall' );
}
