<?php

/**
 * Functionality related to bp-activity
 *
 * @since 1.7
 */

/**
 * Post an activity item when a comment is posted to a doc.
 *
 * @package BuddyPress Docs
 * @since 1.0-beta
 *
 * @param obj $comment_id The id of the comment that's just been saved
 * @return int $activity_id The id number of the activity created
 */
function bp_docs_post_comment_activity( $comment_id ) {
	if ( empty( $comment_id ) ) {
		return false;
	}

	$comment = get_comment( $comment_id );
	$doc     = !empty( $comment->comment_post_ID ) ? get_post( $comment->comment_post_ID ) : false;

	if ( empty( $doc ) ) {
		return false;
	}

	// Only continue if this is a BP Docs post
	if ( $doc->post_type != bp_docs_get_post_type_name() ) {
		return;
	}

	$doc_id = ! empty( $doc->ID ) ? $doc->ID : false;

	if ( ! $doc_id ) {
		return false;
	}

	// Make sure that BP doesn't record this comment with its native functions
	remove_action( 'comment_post', 'bp_blogs_record_comment', 10, 2 );

	// Until better individual activity item privacy controls are available in BP,
	// comments will only be shown in the activity stream if "Who can read comments on
	// this doc?" is set to "Anyone", "Logged-in Users" or "Group members"
	$doc_settings = bp_docs_get_doc_settings( $doc_id );

	if ( ! empty( $doc_settings['read_comments'] ) && ! in_array( $doc_settings['read_comments'], array( 'anyone', 'loggedin', 'group-members' ) ) ) {
		return false;
	}

	// See if we're associated with a group
	$group_id = bp_docs_get_associated_group_id( $doc_id );

	if ( $group_id ) {
		$component = 'groups';
		$item = $group_id;
	} else {
		$component = 'bp_docs';
		$item = 0;
	}

	// Set the action. Filterable so that other integration pieces can alter it
	$action       = '';
	$commenter    = get_user_by( 'email', $comment->comment_author_email );
	$commenter_id = !empty( $commenter->ID ) ? $commenter->ID : false;

	// Since BP Docs only allows member comments, the following should never happen
	if ( !$commenter_id ) {
		return false;
	}

	$user_link    = bp_core_get_userlink( $commenter_id );
	$doc_url      = bp_docs_get_doc_link( $doc_id );
	$comment_url  = $doc_url . '#comment-' . $comment->comment_ID;
	$comment_link = '<a href="' . $comment_url . '">' . $doc->post_title . '</a>';

	$action = sprintf( __( '%1$s commented on the doc %2$s', 'bp-docs' ), $user_link, $comment_link );

	$action	= apply_filters( 'bp_docs_comment_activity_action', $action, $user_link, $comment_link, $component, $item );

	// Set the type, to be used in activity filtering
	$type = 'bp_doc_comment';

	$hide_sitewide = bp_docs_hide_sitewide_for_doc( $doc_id );

	$args = array(
		'user_id'		=> $commenter_id,
		'action'		=> $action,
		'content'		=> $comment->comment_content,
		'primary_link'		=> $comment_url,
		'component'		=> $component,
		'type'			=> $type,
		'item_id'		=> $item, // Set to the group/user/etc id, for better consistency with other BP components
		'secondary_item_id'	=> $comment_id, // The id of the doc itself. Note: limitations in the BP activity API mean I don't get to store the doc_id, but at least it can be abstracted from the comment_id
		'recorded_time'		=> bp_core_current_time(),
		'hide_sitewide'		=> apply_filters( 'bp_docs_hide_sitewide', $hide_sitewide, $comment, $doc, $item, $component ) // Filtered to allow plugins and integration pieces to dictate
	);

	do_action( 'bp_docs_before_comment_activity_save', $args );

	$activity_id = bp_activity_add( apply_filters( 'bp_docs_comment_activity_args', $args ) );

	do_action( 'bp_docs_after_comment_activity_save', $activity_id, $args );

	return $activity_id;
}
add_action( 'comment_post', 'bp_docs_post_comment_activity', 8 );

/**
 * Post an activity item on doc save.
 *
 * @package BuddyPress Docs
 * @since 1.0-beta
 *
 * @param obj $query The query object created in BP_Docs_Query and passed to the
 *        bp_docs_doc_saved filter
 * @return int $activity_id The id number of the activity created
 */
function bp_docs_post_activity( $query ) {
	global $bp;

	// todo: exception for autosave?

	$doc_id	= !empty( $query->doc_id ) ? $query->doc_id : false;

	if ( !$doc_id )
		return false;

	$last_editor	= get_post_meta( $doc_id, 'bp_docs_last_editor', true );

	// Throttle 'doc edited' posts. By default, one per user per hour
	if ( !$query->is_new_doc ) {
		// Look for an existing activity item corresponding to this user editing
		// this doc
		$already_args = array(
			'max'		=> 1,
			'sort'		=> 'DESC',
			'show_hidden'	=> 1, // We need to compare against all activity
			'filter'	=> array(
				'user_id'	=> $last_editor,
				'action'	=> 'bp_doc_edited', // BP bug. 'action' is type
				'secondary_id'	=> $doc_id // We don't really care about the item_id for these purposes (it could have been changed)
			),
		);

		$already_activity = bp_activity_get( $already_args );

		// If any activity items are found, compare its date_recorded with time() to
		// see if it's within the allotted throttle time. If so, don't record the
		// activity item
		if ( !empty( $already_activity['activities'] ) ) {
			$date_recorded 	= $already_activity['activities'][0]->date_recorded;
			$drunix 	= strtotime( $date_recorded );
			if ( time() - $drunix <= apply_filters( 'bp_docs_edit_activity_throttle_time', 60*60 ) )
				return;
		}
	}

	$doc = get_post( $doc_id );

	// Set the action. Filterable so that other integration pieces can alter it
	$action 	= '';
	$user_link 	= bp_core_get_userlink( $last_editor );
	$doc_url	= bp_docs_get_doc_link( $doc_id );
	$doc_link	= '<a href="' . $doc_url . '">' . $doc->post_title . '</a>';

	if ( $query->is_new_doc ) {
		$action = sprintf( __( '%1$s created the doc %2$s', 'bp-docs' ), $user_link, $doc_link );
	} else {
		$action = sprintf( __( '%1$s edited the doc %2$s', 'bp-docs' ), $user_link, $doc_link );
	}

	$action	= apply_filters( 'bp_docs_activity_action', $action, $user_link, $doc_link, $query->is_new_doc, $query );

	$hide_sitewide = bp_docs_hide_sitewide_for_doc( $doc_id );

	$component = 'bp_docs';

	// This is only temporary! This item business needs to be component-neutral
	$item = isset( $bp->groups->current_group->id ) ? $bp->groups->current_group->id : false;

	// Set the type, to be used in activity filtering
	$type = $query->is_new_doc ? 'bp_doc_created' : 'bp_doc_edited';

	$args = array(
		'user_id'		=> $last_editor,
		'action'		=> $action,
		'primary_link'		=> $doc_url,
		'component'		=> $component,
		'type'			=> $type,
		'item_id'		=> $query->item_id, // Set to the group/user/etc id, for better consistency with other BP components
		'secondary_item_id'	=> $doc_id, // The id of the doc itself
		'recorded_time'		=> bp_core_current_time(),
		'hide_sitewide'		=> apply_filters( 'bp_docs_hide_sitewide', $hide_sitewide, false, $doc, $item, $component ) // Filtered to allow plugins and integration pieces to dictate
	);

	do_action( 'bp_docs_before_activity_save', $args );

	$activity_id = bp_activity_add( apply_filters( 'bp_docs_activity_args', $args, $query ) );

	do_action( 'bp_docs_after_activity_save', $activity_id, $args );

	return $activity_id;
}
add_action( 'bp_docs_doc_saved', 'bp_docs_post_activity' );

/**
 * Delete activity associated with a Doc
 *
 * Run on transition_post_status, to catch deletes from all locations
 *
 * @since 1.3
 *
 * @param string $new_status
 * @param string $old_status
 * @param obj WP_Post object
 */
function bp_docs_delete_doc_activity( $new_status, $old_status, $post ) {
	if ( ! bp_is_active( 'activity' ) ) {
		return;
	}

	if ( bp_docs_get_post_type_name() != $post->post_type ) {
		return;
	}

	if ( 'trash' != $new_status ) {
		return;
	}


	$activities = bp_activity_get(
		array(
			'filter' => array(
				'secondary_id' => $post->ID,
				'component' => 'docs',
			),
		)
	);

	foreach ( (array) $activities['activities'] as $activity ) {
		bp_activity_delete( array( 'id' => $activity->id ) );
	}
}
add_action( 'transition_post_status', 'bp_docs_delete_doc_activity', 10, 3 );

/**
 * Register BP Docs activity actions.
 *
 * @since 1.7.0
 */
function bp_docs_register_activity_actions() {
	bp_activity_set_action(
		'bp_docs',
		'bp_doc_created',
		__( 'Created a Doc', 'bp-docs' ),
		'bp_docs_format_activity_action_bp_doc_created'
	);

	bp_activity_set_action(
		'bp_docs',
		'bp_doc_edited',
		__( 'Edited a Doc', 'bp-docs' ),
		'bp_docs_format_activity_action_bp_doc_edited'
	);

	bp_activity_set_action(
		'bp_docs',
		'bp_doc_comment',
		__( 'Commented on a Doc', 'bp-docs' ),
		'bp_docs_format_activity_action_bp_doc_comment'
	);
}
add_action( 'bp_register_activity_actions', 'bp_docs_register_activity_actions' );

/**
 * Format 'bp_doc_created' activity actions.
 *
 * @since 1.7.0
 *
 * @param string $action Activity action.
 * @param object $activity Activity object.
 * @return string
 */
function bp_docs_format_activity_action_bp_doc_created( $action, $activity ) {
	$user_link = bp_core_get_userlink( $activity->user_id );

	$doc = get_post( $activity->secondary_item_id );
	$doc_url = bp_docs_get_doc_link( $activity->secondary_item_id );
	$doc_link = sprintf( '<a href="%s">%s</a>', $doc_url, $doc->post_title );

	$action = sprintf( __( '%1$s created the doc %2$s', 'bp-docs' ), $user_link, $doc_link );

	return $action;
}

/**
 * Format 'bp_doc_edited' activity actions.
 *
 * @since 1.7.0
 *
 * @param string $action Activity action.
 * @param object $activity Activity object.
 * @return string
 */
function bp_docs_format_activity_action_bp_doc_edited( $action, $activity ) {
	$user_link = bp_core_get_userlink( $activity->user_id );

	$doc = get_post( $activity->secondary_item_id );
	$doc_url = bp_docs_get_doc_link( $activity->secondary_item_id );
	$doc_link = sprintf( '<a href="%s">%s</a>', $doc_url, $doc->post_title );

	$action = sprintf( __( '%1$s edited the doc %2$s', 'bp-docs' ), $user_link, $doc_link );

	return $action;
}

/**
 * Format 'bp_doc_comment' activity actions.
 *
 * @since 1.7.0
 *
 * @param string $action Activity action.
 * @param object $activity Activity object.
 * @return string
 */
function bp_docs_format_activity_action_bp_doc_comment( $action, $activity ) {
	$user_link = bp_core_get_userlink( $activity->user_id );

	$comment = get_comment( $activity->secondary_item_id );
	$doc = get_post( $comment->comment_post_ID );
	$doc_url = bp_docs_get_doc_link( $doc->ID );
	$comment_url = $doc_url . '#comment-' . $comment->comment_ID;
	$doc_link = sprintf( '<a href="%s">%s</a>', $comment_url, $doc->post_title );

	$action = sprintf( __( '%1$s commented on the doc %2$s', 'bp-docs' ), $user_link, $doc_link );

	return $action;
}
/**
 * Fetch data related to Docs at the beginning of an activity loop.
 *
 * This reduces database overhead during the activity loop.
 *
 * @since 1.7.0
 *
 * @param array $activities Array of activity items.
 * @return array
 */
function bp_docs_prefetch_activity_object_data( $activities ) {
	if ( empty( $activities ) ) {
		return $activities;
	}

	$doc_ids = array();
	$doc_comment_ids = array();

	foreach ( $activities as $activity ) {
		if ( 'bp_docs' !== $activity->component ) {
			continue;
		}

		// Doc ID stored in different places
		if ( 'bp_doc_created' === $activity->type || 'bp_doc_edited' === $activity->type ) {
			$doc_ids[] = $activity->secondary_item_id;
		} else if ( 'bp_doc_comment' === $activity->type ) {
			$doc_comment_ids[] = $activity->secondary_item_id;
		}
	}

	// We've got to get the comments. Don't know of an easy way to do this
	// using the WP API
	if ( ! empty( $doc_comment_ids ) ) {
		global $wpdb;
		$doc_comment_ids_sql = implode( ',', wp_parse_id_list( $doc_comment_ids ) );
		$comment_post_ids = $wpdb->get_col( "SELECT comment_post_ID FROM {$wpdb->comments} WHERE comment_ID IN ({$doc_comment_ids_sql})" );
		$doc_ids = array_unique( array_merge( $doc_ids, $comment_post_ids ) );
	}

	if ( ! empty( $doc_ids ) ) {
		// prime post caches
		// using the private function because the public functions
		// weren't caching correctly. @todo fix this
		_prime_post_caches( $doc_ids, false, false );
	}
}
add_filter( 'bp_activity_prefetch_object_data', 'bp_docs_prefetch_activity_object_data' );

/**
 * Adds BP Docs options to activity filter dropdowns
 *
 * @package BuddyPress Docs
 * @since 1.0-beta
 */
function bp_docs_activity_filter_options() {
	?>

	<option value="bp_doc_created"><?php _e( 'New Docs', 'bp-docs' ); ?></option>
	<option value="bp_doc_edited"><?php _e( 'Doc Edits', 'bp-docs' ); ?></option>
	<option value="bp_doc_comment"><?php _e( 'Doc Comments', 'bp-docs' ); ?></option>

	<?php
}

/**
 * Wrapper for activity filter dropdown hooks to avoid polluting global scope.
 *
 * @since 1.7.0
 */
function bp_docs_load_activity_filter_options() {
	// Add BP Docs activity types to the activity filter dropdown
	$dropdowns = apply_filters( 'bp_docs_activity_filter_locations', array(
		'bp_activity_filter_options',
		'bp_group_activity_filter_options',
		'bp_member_activity_filter_options'
	) );
	foreach( $dropdowns as $hook ) {
		add_action( $hook, 'bp_docs_activity_filter_options' );
	}
}
add_action( 'bp_screens', 'bp_docs_load_activity_filter_options', 1 );
