<?php
/**
 * BuddyPress Groups Activity Functions.
 *
 * These functions handle the recording, deleting and formatting of activity
 * for the user and for this specific component.
 *
 * @package BuddyPress
 * @subpackage GroupsActivity
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Register activity actions for the Groups component.
 *
 * @return bool|null False on failure.
 */
function groups_register_activity_actions() {
	$bp = buddypress();

	if ( ! bp_is_active( 'activity' ) ) {
		return false;
	}

	bp_activity_set_action(
		$bp->groups->id,
		'created_group',
		__( 'Created a group', 'buddypress' ),
		'bp_groups_format_activity_action_created_group',
		__( 'New Groups', 'buddypress' ),
		array( 'activity', 'member', 'member_groups' )
	);

	bp_activity_set_action(
		$bp->groups->id,
		'joined_group',
		__( 'Joined a group', 'buddypress' ),
		'bp_groups_format_activity_action_joined_group',
		__( 'Group Memberships', 'buddypress' ),
		array( 'activity', 'group', 'member', 'member_groups' )
	);

	bp_activity_set_action(
		$bp->groups->id,
		'group_details_updated',
		__( 'Group details edited', 'buddypress' ),
		'bp_groups_format_activity_action_group_details_updated',
		__( 'Group Updates', 'buddypress' ),
		array( 'activity', 'group', 'member', 'member_groups' )
	);

	// These actions are for the legacy forums
	// Since the bbPress plugin also shares the same 'forums' identifier, we also
	// check for the legacy forums loader class to be extra cautious
	if ( bp_is_active( 'forums' ) && class_exists( 'BP_Forums_Component' ) ) {
		bp_activity_set_action(
			$bp->groups->id,
			'new_forum_topic',
			__( 'New group forum topic', 'buddypress' ),
			false,
			__( 'Forum Topics', 'buddypress' ),
			array( 'activity', 'group', 'member', 'member_groups' )
		);

		bp_activity_set_action(
			$bp->groups->id,
			'new_forum_post',
			__( 'New group forum post',  'buddypress' ),
			false,
			__( 'Forum Replies', 'buddypress' ),
			array( 'activity', 'group', 'member', 'member_groups' )
		);
	}

	/**
	 * Fires at end of registration of the default activity actions for the Groups component.
	 *
	 * @since 1.1.0
	 */
	do_action( 'groups_register_activity_actions' );
}
add_action( 'bp_register_activity_actions', 'groups_register_activity_actions' );

/**
 * Format 'created_group' activity actions.
 *
 * @since 2.0.0
 *
 * @param string $action   Static activity action.
 * @param object $activity Activity data object.
 *
 * @return string
 */
function bp_groups_format_activity_action_created_group( $action, $activity ) {
	$user_link = bp_core_get_userlink( $activity->user_id );

	$group = groups_get_group( array(
		'group_id'        => $activity->item_id,
		'populate_extras' => false,
	) );
	$group_link = '<a href="' . esc_url( bp_get_group_permalink( $group ) ) . '">' . esc_html( $group->name ) . '</a>';

	$action = sprintf( __( '%1$s created the group %2$s', 'buddypress'), $user_link, $group_link );

	/**
	 * Filters the 'created_group' activity actions.
	 *
	 * @since 1.2.0
	 *
	 * @param string $action   The 'created_group' activity action.
	 * @param object $activity Activity data object.
	 */
	return apply_filters( 'groups_activity_created_group_action', $action, $activity );
}

/**
 * Format 'joined_group' activity actions.
 *
 * @since 2.0.0
 *
 * @param string $action   Static activity action.
 * @param object $activity Activity data object.
 *
 * @return string
 */
function bp_groups_format_activity_action_joined_group( $action, $activity ) {
	$user_link = bp_core_get_userlink( $activity->user_id );

	$group = groups_get_group( array(
		'group_id'        => $activity->item_id,
		'populate_extras' => false,
	) );
	$group_link = '<a href="' . esc_url( bp_get_group_permalink( $group ) ) . '">' . esc_html( $group->name ) . '</a>';

	$action = sprintf( __( '%1$s joined the group %2$s', 'buddypress' ), $user_link, $group_link );

	// Legacy filters (do not follow parameter patterns of other activity
	// action filters, and requires apply_filters_ref_array())
	if ( has_filter( 'groups_activity_membership_accepted_action' ) ) {
		$action = apply_filters_ref_array( 'groups_activity_membership_accepted_action', array( $action, $user_link, &$group ) );
	}

	// Another legacy filter
	if ( has_filter( 'groups_activity_accepted_invite_action' ) ) {
		$action = apply_filters_ref_array( 'groups_activity_accepted_invite_action', array( $action, $activity->user_id, &$group ) );
	}

	/**
	 * Filters the 'joined_group' activity actions.
	 *
	 * @since 2.0.0
	 *
	 * @param string $action   The 'joined_group' activity actions.
	 * @param object $activity Activity data object.
	 */
	return apply_filters( 'bp_groups_format_activity_action_joined_group', $action, $activity );
}

/**
 * Format 'group_details_updated' activity actions.
 *
 * @since 2.2.0
 *
 * @param  string $action   Static activity action.
 * @param  object $activity Activity data object.
 *
 * @return string
 */
function bp_groups_format_activity_action_group_details_updated( $action, $activity ) {
	$user_link = bp_core_get_userlink( $activity->user_id );

	$group = groups_get_group( array(
		'group_id'        => $activity->item_id,
		'populate_extras' => false,
	) );
	$group_link = '<a href="' . esc_url( bp_get_group_permalink( $group ) ) . '">' . esc_html( $group->name ) . '</a>';

	/*
	 * Changed group details are stored in groupmeta, keyed by the activity
	 * timestamp. See {@link bp_groups_group_details_updated_add_activity()}.
	 */
	$changed = groups_get_groupmeta( $activity->item_id, 'updated_details_' . $activity->date_recorded );

	// No changed details were found, so use a generic message.
	if ( empty( $changed ) ) {
		$action = sprintf( __( '%1$s updated details for the group %2$s', 'buddypress' ), $user_link, $group_link );

	// Name and description changed - to keep things short, don't describe changes in detail.
	} elseif ( isset( $changed['name'] ) && isset( $changed['description'] ) ) {
		$action = sprintf( __( '%1$s changed the name and description of the group %2$s', 'buddypress' ), $user_link, $group_link );

	// Name only.
	} elseif ( ! empty( $changed['name']['old'] ) && ! empty( $changed['name']['new'] ) ) {
		$action = sprintf( __( '%1$s changed the name of the group %2$s from "%3$s" to "%4$s"', 'buddypress' ), $user_link, $group_link, esc_html( $changed['name']['old'] ), esc_html( $changed['name']['new'] ) );

	// Description only.
	} elseif ( ! empty( $changed['description']['old'] ) && ! empty( $changed['description']['new'] ) ) {
		$action = sprintf( __( '%1$s changed the description of the group %2$s from "%3$s" to "%4$s"', 'buddypress' ), $user_link, $group_link, esc_html( $changed['description']['old'] ), esc_html( $changed['description']['new'] ) );

	}

	/**
	 * Filters the 'group_details_updated' activity actions.
	 *
	 * @since 2.0.0
	 *
	 * @param string $action   The 'group_details_updated' activity actions.
	 * @param object $activity Activity data object.
	 */
	return apply_filters( 'bp_groups_format_activity_action_joined_group', $action, $activity );
}

/**
 * Fetch data related to groups at the beginning of an activity loop.
 *
 * This reduces database overhead during the activity loop.
 *
 * @since 2.0.0
 *
 * @param array $activities Array of activity items.
 *
 * @return array
 */
function bp_groups_prefetch_activity_object_data( $activities ) {
	$group_ids = array();

	if ( empty( $activities ) ) {
		return $activities;
	}

	foreach ( $activities as $activity ) {
		if ( buddypress()->groups->id !== $activity->component ) {
			continue;
		}

		$group_ids[] = $activity->item_id;
	}

	if ( ! empty( $group_ids ) ) {

		// TEMPORARY - Once the 'populate_extras' issue is solved
		// in the groups component, we can do this with groups_get_groups()
		// rather than manually
		$uncached_ids = array();
		foreach ( $group_ids as $group_id ) {
			if ( false === wp_cache_get( $group_id, 'bp_groups' ) ) {
				$uncached_ids[] = $group_id;
			}
		}

		if ( ! empty( $uncached_ids ) ) {
			global $wpdb;
			$bp = buddypress();
			$uncached_ids_sql = implode( ',', wp_parse_id_list( $uncached_ids ) );
			$groups = $wpdb->get_results( "SELECT * FROM {$bp->groups->table_name} WHERE id IN ({$uncached_ids_sql})" );
			foreach ( $groups as $group ) {
				wp_cache_set( $group->id, $group, 'bp_groups' );
			}
		}
	}

	return $activities;
}
add_filter( 'bp_activity_prefetch_object_data', 'bp_groups_prefetch_activity_object_data' );

/**
 * Set up activity arguments for use with the 'groups' scope.
 *
 * @since 2.2.0
 *
 * @param array $retval Empty array by default
 * @param array $filter Current activity arguments
 *
 * @return array
 */
function bp_groups_filter_activity_scope( $retval = array(), $filter = array() ) {

	// Determine the user_id
	if ( ! empty( $filter['user_id'] ) ) {
		$user_id = $filter['user_id'];
	} else {
		$user_id = bp_displayed_user_id()
			? bp_displayed_user_id()
			: bp_loggedin_user_id();
	}

	// Determine groups of user
	$groups = groups_get_user_groups( $user_id );
	if ( empty( $groups['groups'] ) ) {
		$groups = array( 'groups' => 0 );
	}

	// Should we show all items regardless of sitewide visibility?
	$show_hidden = array();
	if ( ! empty( $user_id ) && ( $user_id !== bp_loggedin_user_id() ) ) {
		$show_hidden = array(
			'column' => 'hide_sitewide',
			'value'  => 0
		);
	}

	$retval = array(
		'relation' => 'AND',
		array(
			'relation' => 'AND',
			array(
				'column' => 'component',
				'value'  => buddypress()->groups->id
			),
			array(
				'column'  => 'item_id',
				'compare' => 'IN',
				'value'   => (array) $groups['groups']
			),
		),
		$show_hidden,

		// overrides
		'override' => array(
			'filter'      => array( 'user_id' => 0 ),
			'show_hidden' => true
		),
	);

	return $retval;
}
add_filter( 'bp_activity_set_groups_scope_args', 'bp_groups_filter_activity_scope', 10, 2 );

/**
 * Record an activity item related to the Groups component.
 *
 * A wrapper for {@link bp_activity_add()} that provides some Groups-specific
 * defaults.
 *
 * @see bp_activity_add() for more detailed description of parameters and
 *      return values.
 *
 * @param array|string $args {
 *     An array of arguments for the new activity item. Accepts all parameters
 *     of {@link bp_activity_add()}. However, this wrapper provides some
 *     additional defaults, as described below:
 *     @type string $component     Default: the id of your Groups component
 *                                 (usually 'groups').
 *     @type bool   $hide_sitewide Default: True if the current group is not
 *                                 public, otherwise false.
 * }
 * @return bool See {@link bp_activity_add()}.
 */
function groups_record_activity( $args = '' ) {

	if ( ! bp_is_active( 'activity' ) ) {
		return false;
	}

	// Set the default for hide_sitewide by checking the status of the group
	$hide_sitewide = false;
	if ( !empty( $args['item_id'] ) ) {
		if ( bp_get_current_group_id() == $args['item_id'] ) {
			$group = groups_get_current_group();
		} else {
			$group = groups_get_group( array( 'group_id' => $args['item_id'] ) );
		}

		if ( isset( $group->status ) && 'public' != $group->status ) {
			$hide_sitewide = true;
		}
	}

	$r = wp_parse_args( $args, array(
		'id'                => false,
		'user_id'           => bp_loggedin_user_id(),
		'action'            => '',
		'content'           => '',
		'primary_link'      => '',
		'component'         => buddypress()->groups->id,
		'type'              => false,
		'item_id'           => false,
		'secondary_item_id' => false,
		'recorded_time'     => bp_core_current_time(),
		'hide_sitewide'     => $hide_sitewide
	) );

	return bp_activity_add( $r );
}

/**
 * Update the last_activity meta value for a given group.
 *
 * @param int $group_id Optional. The ID of the group whose last_activity is
 *                      being updated. Default: the current group's ID.
 *
 * @return bool|null False on failure.
 */
function groups_update_last_activity( $group_id = 0 ) {

	if ( empty( $group_id ) ) {
		$group_id = buddypress()->groups->current_group->id;
	}

	if ( empty( $group_id ) ) {
		return false;
	}

	groups_update_groupmeta( $group_id, 'last_activity', bp_core_current_time() );
}
add_action( 'groups_join_group',           'groups_update_last_activity' );
add_action( 'groups_leave_group',          'groups_update_last_activity' );
add_action( 'groups_created_group',        'groups_update_last_activity' );
add_action( 'groups_new_forum_topic',      'groups_update_last_activity' );
add_action( 'groups_new_forum_topic_post', 'groups_update_last_activity' );

/**
 * Add an activity stream item when a member joins a group.
 *
 * @since 1.9.0
 *
 * @param int $user_id  ID of the user joining the group.
 * @param int $group_id ID of the group.
 *
 * @return bool|null False on failure.
 */
function bp_groups_membership_accepted_add_activity( $user_id, $group_id ) {

	// Bail if Activity is not active
	if ( ! bp_is_active( 'activity' ) ) {
		return false;
	}

	// Get the group so we can get it's name
	$group = groups_get_group( array( 'group_id' => $group_id ) );

	/**
	 * Filters the 'membership_accepted' activity actions.
	 *
	 * @since 1.2.0
	 *
	 * @param string $value    The 'membership_accepted' activity action.
	 * @param int    $user_id  ID of the user joining the group.
	 * @param int    $group_id ID of the group. Passed by reference.
	 */
	$action = apply_filters_ref_array( 'groups_activity_membership_accepted_action', array( sprintf( __( '%1$s joined the group %2$s', 'buddypress' ), bp_core_get_userlink( $user_id ), '<a href="' . bp_get_group_permalink( $group ) . '">' . esc_attr( $group->name ) . '</a>' ), $user_id, &$group ) );

	// Record in activity streams
	groups_record_activity( array(
		'action'  => $action,
		'type'    => 'joined_group',
		'item_id' => $group_id,
		'user_id' => $user_id
	) );
}
add_action( 'groups_membership_accepted', 'bp_groups_membership_accepted_add_activity', 10, 2 );

/**
 * Add an activity item when a group's details are updated.
 *
 * @since 2.2.0
 *
 * @param  int             $group_id       ID of the group.
 * @param  BP_Groups_Group $old_group      Group object before the details had been changed.
 * @param  bool            $notify_members True if the admin has opted to notify group members, otherwise false.
 *
 * @return int|bool The ID of the activity on success. False on error.
 */
function bp_groups_group_details_updated_add_activity( $group_id, $old_group, $notify_members ) {

	// Bail if Activity is not active.
	if ( ! bp_is_active( 'activity' ) ) {
		return false;
	}

	if ( ! isset( $old_group->name ) || ! isset( $old_group->description ) ) {
		return false;
	}

	// If the admin has opted not to notify members, don't post an activity item either
	if ( empty( $notify_members ) ) {
		return;
	}

	$group = groups_get_group( array(
		'group_id' => $group_id,
	) );

	/*
	 * Store the changed data, which will be used to generate the activity
	 * action. Since we haven't yet created the activity item, we store the
	 * old group data in groupmeta, keyed by the timestamp that we'll put
	 * on the activity item.
	 */
	$changed = array();

	if ( $group->name !== $old_group->name ) {
		$changed['name'] = array(
			'old' => $old_group->name,
			'new' => $group->name,
		);
	}

	if ( $group->description !== $old_group->description ) {
		$changed['description'] = array(
			'old' => $old_group->description,
			'new' => $group->description,
		);
	}

	// If there are no changes, don't post an activity item.
	if ( empty( $changed ) ) {
		return;
	}

	$time = bp_core_current_time();
	groups_update_groupmeta( $group_id, 'updated_details_' . $time, $changed );

	// Record in activity streams.
	return groups_record_activity( array(
		'type'          => 'group_details_updated',
		'item_id'       => $group_id,
		'user_id'       => bp_loggedin_user_id(),
		'recorded_time' => $time,

	) );

}
add_action( 'groups_details_updated', 'bp_groups_group_details_updated_add_activity', 10, 3 );

/**
 * Delete all activity items related to a specific group.
 *
 * @since 1.9.0
 *
 * @param int $group_id ID of the group.
 */
function bp_groups_delete_group_delete_all_activity( $group_id ) {
	if ( bp_is_active( 'activity' ) ) {
		bp_activity_delete_by_item_id( array(
			'item_id'   => $group_id,
			'component' => buddypress()->groups->id
		) );
	}
}
add_action( 'groups_delete_group', 'bp_groups_delete_group_delete_all_activity', 10 );

/**
 * Delete group member activity if they leave or are removed within 5 minutes of membership modification.
 *
 * If the user joined this group less than five minutes ago, remove the
 * joined_group activity so users cannot flood the activity stream by
 * joining/leaving the group in quick succession.
 *
 * @since 1.9.0
 *
 * @param int $group_id ID of the group.
 * @param int $user_id  ID of the user leaving the group.
 */
function bp_groups_leave_group_delete_recent_activity( $group_id, $user_id ) {

	// Bail if Activity component is not active
	if ( ! bp_is_active( 'activity' ) ) {
		return;
	}

	// Get the member's group membership information
	$membership = new BP_Groups_Member( $user_id, $group_id );

	// Check the time period, and maybe delete their recent group activity
	if ( time() <= strtotime( '+5 minutes', (int) strtotime( $membership->date_modified ) ) ) {
		bp_activity_delete( array(
			'component' => buddypress()->groups->id,
			'type'      => 'joined_group',
			'user_id'   => $user_id,
			'item_id'   => $group_id
		) );
	}
}
add_action( 'groups_leave_group',   'bp_groups_leave_group_delete_recent_activity', 10, 2 );
add_action( 'groups_remove_member', 'bp_groups_leave_group_delete_recent_activity', 10, 2 );
add_action( 'groups_ban_member',    'bp_groups_leave_group_delete_recent_activity', 10, 2 );
