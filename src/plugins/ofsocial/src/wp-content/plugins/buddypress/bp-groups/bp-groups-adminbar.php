<?php
/**
 * BuddyPress Groups Toolbar.
 *
 * Handles the groups functions related to the WordPress Toolbar.
 *
 * @package BuddyPress
 * @subpackage Groups
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Add the Group Admin top-level menu when viewing group pages.
 *
 * @since 1.5.0
 *
 * @todo Add dynamic menu items for group extensions.
 *
 * @return false|null False if not on a group page, or if user does not have
 *                    access to group admin options.
 */
function bp_groups_group_admin_menu() {
	global $wp_admin_bar;
	$bp = buddypress();

	// Only show if viewing a group
	if ( ! bp_is_group() || bp_is_group_create() ) {
		return false;
	}

	// Only show this menu to group admins and super admins
	if ( ! bp_current_user_can( 'bp_moderate' ) && ! bp_group_is_admin() ) {
		return false;
	}

	// Unique ID for the 'Edit Group' menu
	$bp->group_admin_menu_id = 'group-admin';

	// Add the top-level Group Admin button
	$wp_admin_bar->add_menu( array(
		'id'    => $bp->group_admin_menu_id,
		'title' => __( 'Edit Group', 'buddypress' ),
		'href'  => bp_get_group_permalink( $bp->groups->current_group )
	) );

	// Index of the Manage tabs parent slug
	$nav_index = $bp->groups->current_group->slug . '_manage';

	// Check if current group has Manage tabs
	if ( empty( $bp->bp_options_nav[ $nav_index ] ) ) {
		return;
	}

	// Build the Group Admin menus
	foreach ( $bp->bp_options_nav[ $nav_index ] as $menu ) {
		/**
		 * Should we add the current manage link in the Group's "Edit" Admin Bar menu ?
		 *
		 * All core items will be added, plugins can use a new parameter in the BP Group Extension API
		 * to also add the link to the "edit screen" of their group component. To do so, set the
		 * the 'show_in_admin_bar' argument of your edit screen to true
		 */
		if ( $menu['show_in_admin_bar'] ) {
			$title = sprintf( _x( 'Edit Group %s', 'Group WP Admin Bar manage links', 'buddypress' ), $menu['name'] );

			// Title is specific for delete
			if ( 'delete-group' == $menu['slug'] ) {
				$title = sprintf( _x( '%s Group', 'Group WP Admin Bar delete link', 'buddypress' ), $menu['name'] );
			}

			$wp_admin_bar->add_menu( array(
				'parent' => $bp->group_admin_menu_id,
				'id'     => $menu['slug'],
				'title'  => $title,
				'href'   => bp_get_groups_action_link( 'admin/' . $menu['slug'] )
			) );
		}
	}
}
add_action( 'admin_bar_menu', 'bp_groups_group_admin_menu', 99 );

/**
 * Remove rogue WP core Edit menu when viewing a single group.
 *
 * @since 1.6.0
 */
function bp_groups_remove_edit_page_menu() {
	if ( bp_is_group() ) {
		remove_action( 'admin_bar_menu', 'wp_admin_bar_edit_menu', 80 );
	}
}
add_action( 'add_admin_bar_menus', 'bp_groups_remove_edit_page_menu' );
