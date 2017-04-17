<?php
/**
 * BuddyPress Groups Caching.
 *
 * Caching functions handle the clearing of cached objects and pages on specific
 * actions throughout BuddyPress.
 *
 * @package BuddyPress
 * @subpackage Groups
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Slurp up metadata for a set of groups.
 *
 * This function is called in two places in the BP_Groups_Group class:
 *   - in the populate() method, when single group objects are populated
 *   - in the get() method, when multiple groups are queried
 *
 * It grabs all groupmeta associated with all of the groups passed in
 * $group_ids and adds it to WP cache. This improves efficiency when using
 * groupmeta within a loop context.
 *
 *
 * @param int|string|array|bool $group_ids Accepts a single group_id, or a
 *                                         comma-separated list or array of
 *                                         group ids.
 */
function bp_groups_update_meta_cache( $group_ids = false ) {
	$bp = buddypress();

	$cache_args = array(
		'object_ids' 	   => $group_ids,
		'object_type' 	   => $bp->groups->id,
		'cache_group'      => 'group_meta',
		'object_column'    => 'group_id',
		'meta_table' 	   => $bp->groups->table_name_groupmeta,
		'cache_key_prefix' => 'bp_groups_groupmeta'
	);

	bp_update_meta_cache( $cache_args );
}

/**
 * Clear the cached group count.
 *
 * @param int $group_id Not used.
 */
function groups_clear_group_object_cache( $group_id ) {
	wp_cache_delete( 'bp_total_group_count', 'bp' );
}
add_action( 'groups_group_deleted',              'groups_clear_group_object_cache' );
add_action( 'groups_settings_updated',           'groups_clear_group_object_cache' );
add_action( 'groups_details_updated',            'groups_clear_group_object_cache' );
add_action( 'groups_group_avatar_updated',       'groups_clear_group_object_cache' );
add_action( 'groups_create_group_step_complete', 'groups_clear_group_object_cache' );

/**
 * Bust group caches when editing or deleting.
 *
 * @since 1.7.0
 *
 * @param int $group_id The group being edited.
 */
function bp_groups_delete_group_cache( $group_id = 0 ) {
	wp_cache_delete( $group_id, 'bp_groups' );
}
add_action( 'groups_delete_group',     'bp_groups_delete_group_cache' );
add_action( 'groups_update_group',     'bp_groups_delete_group_cache' );
add_action( 'groups_details_updated',  'bp_groups_delete_group_cache' );
add_action( 'groups_settings_updated', 'bp_groups_delete_group_cache' );

/**
 * Bust group cache when modifying metadata.
 *
 * @since 2.0.0
 *
 * @param int $meta_id
 * @param int $group_id
 */
function bp_groups_delete_group_cache_on_metadata_change( $meta_id, $group_id ) {
	wp_cache_delete( $group_id, 'bp_groups' );
}
add_action( 'updated_group_meta', 'bp_groups_delete_group_cache_on_metadata_change', 10, 2 );
add_action( 'added_group_meta', 'bp_groups_delete_group_cache_on_metadata_change', 10, 2 );

/**
 * Clear caches for the group creator when a group is created.
 *
 * @since 1.6.0
 *
 * @param int             $group_id  ID of the group.
 * @param BP_Groups_Group $group_obj Group object.
 */
function bp_groups_clear_group_creator_cache( $group_id, $group_obj ) {
	// Clears the 'total groups' for this user
	groups_clear_group_user_object_cache( $group_obj->id, $group_obj->creator_id );
}
add_action( 'groups_created_group', 'bp_groups_clear_group_creator_cache', 10, 2 );

/**
 * Clears caches for all members in a group when a group is deleted.
 *
 * @since 1.6.0
 *
 * @param BP_Groups_Group $group_obj Group object.
 * @param array           $user_ids  User IDs who were in this group.
 */
function bp_groups_clear_group_members_caches( $group_obj, $user_ids ) {
	// Clears the 'total groups' cache for each member in a group
	foreach ( (array) $user_ids as $user_id )
		groups_clear_group_user_object_cache( $group_obj->id, $user_id );
}
add_action( 'bp_groups_delete_group', 'bp_groups_clear_group_members_caches', 10, 2 );

/**
 * Clear a user's cached total group invite count.
 *
 * Count is cleared when an invite is accepted, rejected or deleted.
 *
 * @since 2.0.0
 *
 * @param int $user_id The user ID.
 */
function bp_groups_clear_invite_count_for_user( $user_id ) {
	wp_cache_delete( $user_id, 'bp_group_invite_count' );
}
add_action( 'groups_accept_invite', 'bp_groups_clear_invite_count_for_user' );
add_action( 'groups_reject_invite', 'bp_groups_clear_invite_count_for_user' );
add_action( 'groups_delete_invite', 'bp_groups_clear_invite_count_for_user' );

/**
 * Clear a user's cached total group invite count when a user is uninvited.
 *
 * Groan. Our API functions are not consistent.
 *
 * @since 2.0.0
 *
 * @param int $group_id The group ID. Not used in this function.
 * @param int $user_id  The user ID.
 */
function bp_groups_clear_invite_count_on_uninvite( $group_id, $user_id ) {
	bp_groups_clear_invite_count_for_user( $user_id );
}
add_action( 'groups_uninvite_user', 'bp_groups_clear_invite_count_on_uninvite', 10, 2 );

/**
 * Clear a user's cached total group invite count when a new invite is sent.
 *
 * @since 2.0.0
 *
 * @param int   $group_id      The group ID. Not used in this function.
 * @param array $invited_users Array of invited user IDs.
 */
function bp_groups_clear_invite_count_on_send( $group_id, $invited_users ) {
	foreach ( $invited_users as $user_id ) {
		bp_groups_clear_invite_count_for_user( $user_id );
	}
}
add_action( 'groups_send_invites', 'bp_groups_clear_invite_count_on_send', 10, 2 );

/**
 * Clear a user's cached group count.
 *
 * @param int $group_id The group ID. Not used in this function.
 * @param int $user_id  The user ID.
 */
function groups_clear_group_user_object_cache( $group_id, $user_id ) {
	wp_cache_delete( 'bp_total_groups_for_user_' . $user_id, 'bp' );
}
add_action( 'groups_join_group',    'groups_clear_group_user_object_cache', 10, 2 );
add_action( 'groups_leave_group',   'groups_clear_group_user_object_cache', 10, 2 );
add_action( 'groups_ban_member',    'groups_clear_group_user_object_cache', 10, 2 );
add_action( 'groups_unban_member',  'groups_clear_group_user_object_cache', 10, 2 );
add_action( 'groups_uninvite_user', 'groups_clear_group_user_object_cache', 10, 2 );
add_action( 'groups_remove_member', 'groups_clear_group_user_object_cache', 10, 2 );

/**
 * Clear group administrator cache.
 *
 * @since 2.1.0
 *
 * @param int $group_id The group ID.
 */
function groups_clear_group_administrator_cache( $group_id ) {
	wp_cache_delete( $group_id, 'bp_group_admins' );
}
add_action( 'groups_promote_member', 'groups_clear_group_administrator_cache' );
add_action( 'groups_demote_member',  'groups_clear_group_administrator_cache' );
add_action( 'groups_delete_group',   'groups_clear_group_administrator_cache' );

/**
 * Clear group administrator cache when a group member is saved.
 *
 * This accounts for situations where group administrators are added manually
 * using {@link BP_Groups_Member::save()}.  Usually via a plugin.
 *
 * @since 2.1.0
 *
 * @param BP_Groups_Member $member
 */
function groups_clear_group_administrator_cache_on_member_save( BP_Groups_Member $member ) {
	groups_clear_group_administrator_cache( $member->group_id );
}
add_action( 'groups_member_after_save', 'groups_clear_group_administrator_cache_on_member_save' );

/* List actions to clear super cached pages on, if super cache is installed */
add_action( 'groups_join_group',                 'bp_core_clear_cache' );
add_action( 'groups_leave_group',                'bp_core_clear_cache' );
add_action( 'groups_accept_invite',              'bp_core_clear_cache' );
add_action( 'groups_reject_invite',              'bp_core_clear_cache' );
add_action( 'groups_invite_user',                'bp_core_clear_cache' );
add_action( 'groups_uninvite_user',              'bp_core_clear_cache' );
add_action( 'groups_details_updated',            'bp_core_clear_cache' );
add_action( 'groups_settings_updated',           'bp_core_clear_cache' );
add_action( 'groups_unban_member',               'bp_core_clear_cache' );
add_action( 'groups_ban_member',                 'bp_core_clear_cache' );
add_action( 'groups_demote_member',              'bp_core_clear_cache' );
add_action( 'groups_promote_member',             'bp_core_clear_cache' );
add_action( 'groups_membership_rejected',        'bp_core_clear_cache' );
add_action( 'groups_membership_accepted',        'bp_core_clear_cache' );
add_action( 'groups_membership_requested',       'bp_core_clear_cache' );
add_action( 'groups_create_group_step_complete', 'bp_core_clear_cache' );
add_action( 'groups_created_group',              'bp_core_clear_cache' );
add_action( 'groups_group_avatar_updated',       'bp_core_clear_cache' );
