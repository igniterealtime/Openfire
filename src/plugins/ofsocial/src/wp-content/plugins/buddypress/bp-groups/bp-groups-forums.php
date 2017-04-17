<?php
/**
 * BuddyPress Groups Forums.
 *
 * Action functions are exactly the same as screen functions, however they do not
 * have a template screen associated with them. Usually they will send the user
 * back to the default screen after execution.
 *
 * Note that this file is only used for the retired version of bbPress (1.x) and
 * will see minimal updates as of BuddyPress 1.9.0.
 *
 * @package BuddyPress
 * @subpackage GroupsForums
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Creates a new forum inside a specific BuddyPress group.
 *
 * Uses the bundled version of bbPress packaged with BuddyPress.
 *
 * @since 1.0.0
 *
 * @param int    $group_id   The group ID that the new forum should be attached to.
 * @param string $group_name The group name.
 * @param string $group_desc The group description.
 */
function groups_new_group_forum( $group_id = 0, $group_name = '', $group_desc = '' ) {

	if ( empty( $group_id ) ) {
		$group_id = bp_get_current_group_id();
	}

	if ( empty( $group_name ) ) {
		$group_name = bp_get_current_group_name();
	}

	if ( empty( $group_desc ) ) {
		$group_desc = bp_get_current_group_description();
	}

	$forum_id = bp_forums_new_forum( array(
		'forum_name' => $group_name,
		'forum_desc' => $group_desc
	) );

	groups_update_groupmeta( $group_id, 'forum_id', $forum_id );

	/**
	 * Fires after the creation of a new forum inside a specific BuddyPress group.
	 *
	 * @since 1.0.0
	 *
	 * @param int $forum_id ID of the newly created forum.
	 * @param int $group_id ID of the associated group.
	 */
	do_action( 'groups_new_group_forum', $forum_id, $group_id );
}

/**
 * Update group forum metadata (title, description, slug) when the group's details are edited.
 *
 * @since 1.1.0
 *
 * @param int $group_id Group id, passed from groups_details_updated.
 *
 * @return mixed
 */
function groups_update_group_forum( $group_id ) {

	$group = groups_get_group( array( 'group_id' => $group_id ) );

	/**
	 * Bail in the following three situations:
	 *  1. Forums are not enabled for this group
	 *  2. The BP Forum component is not enabled
	 *  3. The built-in bbPress forums are not correctly installed (usually means they've been
	 *     uninstalled)
	 */
	if ( empty( $group->enable_forum ) || !bp_is_active( 'forums' ) || ( function_exists( 'bp_forums_is_installed_correctly' ) && !bp_forums_is_installed_correctly() ) ) {
		return false;
	}

	/**
	 * Filters the group forum metadata value argument.
	 *
	 * @since 1.2.5
	 *
	 * @param array $value Array of metadata values to update the group forum with.
	 */
	bp_forums_update_forum( apply_filters( 'groups_update_group_forum', array(
		'forum_id'   => groups_get_groupmeta( $group_id, 'forum_id' ),
		'forum_name' => $group->name,
		'forum_desc' => $group->description,
		'forum_slug' => $group->slug
	) ) );
}
add_action( 'groups_details_updated', 'groups_update_group_forum' );

/**
 * Create a new group forum post.
 *
 * Uses the bundled version of bbPress packaged with BuddyPress.
 *
 * @since 1.0.0
 *
 * @param string $post_text The text for the forum post.
 * @param int    $topic_id  The topic ID used so we can identify where the new
 *                          forum post should reside.
 * @param mixed  $page      The page number where the new forum post should reside.
 *                          Default: false.
 *
 * @return mixed The new forum post ID on success. Boolean false on failure.
 */
function groups_new_group_forum_post( $post_text, $topic_id, $page = false ) {
	if ( empty( $post_text ) ) {
		return false;
	}

	/**
	 * Filters the text for the forum post before save.
	 *
	 * @since 1.2.0
	 *
	 * @param string $post_text Text for the forum post.
	 */
	$post_text = apply_filters( 'group_forum_post_text_before_save',     $post_text );

	/**
	 * Filters the ID for the forum post before save.
	 *
	 * @since 1.2.0
	 *
	 * @param int $topic_id ID of the topic the post will be associated with.
	 */
	$topic_id  = apply_filters( 'group_forum_post_topic_id_before_save', $topic_id  );
	$post_id   = bp_forums_insert_post( array(
		'post_text' => $post_text,
		'topic_id'  => $topic_id
	) );

	if ( empty( $post_id ) ) {
		return false;
	}

	$topic            = bp_forums_get_topic_details( $topic_id );
	$activity_action  = sprintf( __( '%1$s replied to the forum topic %2$s in the group %3$s', 'buddypress'), bp_core_get_userlink( bp_loggedin_user_id() ), '<a href="' . bp_get_group_permalink( groups_get_current_group() ) . 'forum/topic/' . $topic->topic_slug .'/">' . esc_attr( $topic->topic_title ) . '</a>', '<a href="' . bp_get_group_permalink( groups_get_current_group() ) . '">' . esc_attr( bp_get_current_group_name() ) . '</a>' );
	$activity_content = bp_create_excerpt( $post_text );
	$primary_link     = bp_get_group_permalink( groups_get_current_group() ) . 'forum/topic/' . $topic->topic_slug . '/';

	if ( !empty( $page ) ) {
		$primary_link .= "?topic_page=" . $page;
	}

	/**
	 * Filters the new groups activity forum post action.
	 *
	 * @since 1.2.0
	 *
	 * @param string $activity_action Formatted action related to group activity posting.
	 * @param int    $post_id         ID of the newly created group forum post.
	 * @param string $post_text       The text for the forum post.
	 * @param object $topic           Object holding current topic details. Passed by reference.
	 */
	$action = apply_filters_ref_array( 'groups_activity_new_forum_post_action',  array( $activity_action,  $post_id, $post_text, &$topic ) );

	/**
	 * Filters the new groups activity forum post content.
	 *
	 * @since 1.2.0
	 *
	 * @param string $activity_content Excerpt-length activity content to be posted.
	 * @param int    $post_id          ID of the newly created group forum post.
	 * @param string $post_text        The text for the forum post.
	 * @param object $topic            Object holding current topic details. Passed by reference.
	 */
	$content = apply_filters_ref_array( 'groups_activity_new_forum_post_content', array( $activity_content, $post_id, $post_text, &$topic ) );

	/**
	 * Filters the new groups activity forum post primary link.
	 *
	 * @since 1.1.0
	 *
	 * @param string $value URL to the newly posted forum post.
	 */
	$filtered_primary_link = apply_filters( 'groups_activity_new_forum_post_primary_link', "{$primary_link}#post-{$post_id}" );

	groups_record_activity( array(
		'action'            => $action,
		'content'           => $content,
		'primary_link'      => $filtered_primary_link,
		'type'              => 'new_forum_post',
		'item_id'           => bp_get_current_group_id(),
		'secondary_item_id' => $post_id
	) );

	/**
	 * Fires after the creation of a new group forum topic post.
	 *
	 * @since 1.0.0
	 *
	 * @param int $value   ID of the current group.
	 * @param int $post_id ID of the new created forum topic post.
	 */
	do_action( 'groups_new_forum_topic_post', bp_get_current_group_id(), $post_id );

	return $post_id;
}

/**
 * Create a new group forum topic.
 *
 * Uses the bundled version of bbPress packaged with BuddyPress.
 *
 * @since 1.0.0
 *
 * @param string $topic_title The title for the forum topic.
 * @param string $topic_text  The text for the forum topic.
 * @param string $topic_tags  A comma-delimited string of topic tags.
 * @param int    $forum_id    The forum ID this forum topic resides in.
 *
 * @return mixed The new topic object on success. Boolean false on failure.
 */
function groups_new_group_forum_topic( $topic_title, $topic_text, $topic_tags, $forum_id ) {
	if ( empty( $topic_title ) || empty( $topic_text ) )
		return false;

	/**
	 * Filters the new groups forum topic title before saving.
	 *
	 * @since 1.2.0
	 *
	 * @param string $topic_title The title for the forum topic.
	 */
	$topic_title = apply_filters( 'group_forum_topic_title_before_save',    $topic_title );

	/**
	 * Filters the new groups forum topic text before saving.
	 *
	 * @since 1.2.0
	 *
	 * @param string $topic_text The text for the forum topic.
	 */
	$topic_text  = apply_filters( 'group_forum_topic_text_before_save',     $topic_text  );

	/**
	 * Filters the new groups forum topic tags before saving.
	 *
	 * @since 1.2.0
	 *
	 * @param string $topic_tags A comma-delimited string of topic tags.
	 */
	$topic_tags  = apply_filters( 'group_forum_topic_tags_before_save',     $topic_tags  );

	/**
	 * Filters the forum ID this forum topic resides in.
	 *
	 * @since 1.2.0
	 *
	 * @param string $forum_id The forum ID this forum topic resides in.
	 */
	$forum_id    = apply_filters( 'group_forum_topic_forum_id_before_save', $forum_id    );
	$topic_id    = bp_forums_new_topic( array(
		'topic_title' => $topic_title,
		'topic_text'  => $topic_text,
		'topic_tags'  => $topic_tags,
		'forum_id'    => $forum_id
	) );

	if ( empty( $topic_id ) ) {
		return false;
	}

	$topic            = bp_forums_get_topic_details( $topic_id );
	$activity_action  = sprintf( __( '%1$s started the forum topic %2$s in the group %3$s', 'buddypress'), bp_core_get_userlink( bp_loggedin_user_id() ), '<a href="' . bp_get_group_permalink( groups_get_current_group() ) . 'forum/topic/' . $topic->topic_slug .'/">' . esc_attr( $topic->topic_title ) . '</a>', '<a href="' . bp_get_group_permalink( groups_get_current_group() ) . '">' . esc_attr( bp_get_current_group_name() ) . '</a>' );
	$activity_content = bp_create_excerpt( $topic_text );

	/**
	 * Filters the new groups activity forum topic action.
	 *
	 * @since 1.2.0
	 *
	 * @param string $activity_action Formatted action related to forum topic.
	 * @param string $topic_text      New topic text.
	 * @param object $topic           Object holding current topic details. Passed by reference.
	 */
	$action = apply_filters_ref_array( 'groups_activity_new_forum_topic_action',  array( $activity_action,  $topic_text, &$topic ) );

	/**
	 * Filters the new groups activity forum topic content.
	 *
	 * @since 1.2.0
	 *
	 * @param string $activity_content Excerpt-length activity content to be posted.
	 * @param string $topic_text       New topic text.
	 * @param object $topic            Object holding current topic details. Passed by reference.
	 */
	$content = apply_filters_ref_array( 'groups_activity_new_forum_topic_content', array( $activity_content, $topic_text, &$topic ) );

	/**
	 * Filters the new groups activity forum topic primary link.
	 *
	 * @since 1.1.0
	 *
	 * @param string $value Concatenated primary link.
	 */
	$primary_link = apply_filters( 'groups_activity_new_forum_topic_primary_link', bp_get_group_permalink( groups_get_current_group() ) . 'forum/topic/' . $topic->topic_slug . '/' );

	groups_record_activity( array(
		'action'            => $action,
		'content'           => $content,
		'primary_link'      => $primary_link,
		'type'              => 'new_forum_topic',
		'item_id'           => bp_get_current_group_id(),
		'secondary_item_id' => $topic->topic_id
	) );

	/**
	 * Fires after the creation of a new group forum topic.
	 *
	 * @since 1.0.0
	 *
	 * @param int    $value ID of the current group.
	 * @param object $topic Object holding current topic details. Passed by reference.
	 */
	do_action_ref_array( 'groups_new_forum_topic', array( bp_get_current_group_id(), &$topic ) );

	return $topic;
}

/**
 * Update an existing group forum topic.
 *
 * Uses the bundled version of bbPress packaged with BuddyPress.
 *
 * @since 1.1.0
 *
 * @param int    $topic_id    The topic ID of the existing forum topic.
 * @param string $topic_title The title for the forum topic.
 * @param string $topic_text  The text for the forum topic.
 * @param mixed  $topic_tags  A comma-delimited string of topic tags. Optional.
 *
 * @return mixed The topic object on success. Boolean false on failure.
 */
function groups_update_group_forum_topic( $topic_id, $topic_title, $topic_text, $topic_tags = false ) {
	$bp = buddypress();

	/** This filter is documented in bp-groups/bp-groups-forums.php */
	$topic_title = apply_filters( 'group_forum_topic_title_before_save', $topic_title );

	/** This filter is documented in bp-groups/bp-groups-forums.php */
	$topic_text  = apply_filters( 'group_forum_topic_text_before_save',  $topic_text  );
	$topic       = bp_forums_update_topic( array(
		'topic_title' => $topic_title,
		'topic_text' => $topic_text,
		'topic_id' => $topic_id,
		'topic_tags' => $topic_tags
	) );

	if ( empty( $topic ) ) {
		return false;
	}

	// Get the corresponding activity item
	if ( bp_is_active( 'activity' ) ) {
		$id = bp_activity_get_activity_id( array(
			'item_id'           => bp_get_current_group_id(),
			'secondary_item_id' => $topic_id,
			'component'         => $bp->groups->id,
			'type'              => 'new_forum_topic'
		) );
	}

	$activity_action  = sprintf( __( '%1$s edited the forum topic %2$s in the group %3$s', 'buddypress'), bp_core_get_userlink( $topic->topic_poster ), '<a href="' . bp_get_group_permalink( groups_get_current_group() ) . 'forum/topic/' . $topic->topic_slug .'/">' . esc_attr( $topic->topic_title ) . '</a>', '<a href="' . bp_get_group_permalink( groups_get_current_group() ) . '">' . esc_attr( bp_get_current_group_name() ) . '</a>' );
	$activity_content = bp_create_excerpt( $topic_text );

	/** This filter is documented in bp-groups/bp-groups-forums.php */
	$action = apply_filters_ref_array( 'groups_activity_new_forum_topic_action',  array( $activity_action,  $topic_text, &$topic ) );

	/** This filter is documented in bp-groups/bp-groups-forums.php */
	$content = apply_filters_ref_array( 'groups_activity_new_forum_topic_content', array( $activity_content, $topic_text, &$topic ) );

	/** This filter is documented in bp-groups/bp-groups-forums.php */
	$primary_link = apply_filters( 'groups_activity_new_forum_topic_primary_link', bp_get_group_permalink( groups_get_current_group() ) . 'forum/topic/' . $topic->topic_slug . '/' );

	groups_record_activity( array(
		'id'                => $id,
		'action'            => $action,
		'content'           => $content,
		'primary_link'      => $primary_link,
		'type'              => 'new_forum_topic',
		'item_id'           => (int) bp_get_current_group_id(),
		'user_id'           => (int) $topic->topic_poster,
		'secondary_item_id' => $topic->topic_id,
		'recorded_time '    => $topic->topic_time
	) );

	/**
	 * Fires after the update of a group forum topic.
	 *
	 * @since 1.1.0
	 *
	 * @param object $topic Object holding current topic being updated. Passed by reference.
	 */
	do_action_ref_array( 'groups_update_group_forum_topic', array( &$topic ) );

	return $topic;
}

/**
 * Update an existing group forum post.
 *
 * Uses the bundled version of bbPress packaged with BuddyPress.
 *
 * @since 1.1.0
 *
 * @param int    $post_id   The post ID of the existing forum post.
 * @param string $post_text The text for the forum post.
 * @param int    $topic_id  The topic ID of the existing forum topic.
 * @param mixed  $page      The page number where the new forum post should reside. Optional.
 *
 * @return mixed The forum post ID on success. Boolean false on failure.
 */
function groups_update_group_forum_post( $post_id, $post_text, $topic_id, $page = false ) {
	$bp = buddypress();

	/** This filter is documented in bp-groups/bp-groups-forums.php */
	$post_text = apply_filters( 'group_forum_post_text_before_save', $post_text );

	/** This filter is documented in bp-groups/bp-groups-forums.php */
	$topic_id  = apply_filters( 'group_forum_post_topic_id_before_save', $topic_id );
	$post      = bp_forums_get_post( $post_id );
	$post_id   = bp_forums_insert_post( array(
		'post_id'   => $post_id,
		'post_text' => $post_text,
		'post_time' => $post->post_time,
		'topic_id'  => $topic_id,
		'poster_id' => $post->poster_id
	) );

	if ( empty( $post_id ) ) {
		return false;
	}

	$topic            = bp_forums_get_topic_details( $topic_id );
	$activity_action  = sprintf( __( '%1$s replied to the forum topic %2$s in the group %3$s', 'buddypress'), bp_core_get_userlink( $post->poster_id ), '<a href="' . bp_get_group_permalink( groups_get_current_group() ) . 'forum/topic/' . $topic->topic_slug .'">' . esc_attr( $topic->topic_title ) . '</a>', '<a href="' . bp_get_group_permalink( groups_get_current_group() ) . '">' . esc_attr( bp_get_current_group_name() ) . '</a>' );
	$activity_content = bp_create_excerpt( $post_text );
	$primary_link     = bp_get_group_permalink( groups_get_current_group() ) . 'forum/topic/' . $topic->topic_slug . '/';

	if ( !empty( $page ) ) {
		$primary_link .= "?topic_page=" . $page;
	}

	// Get the corresponding activity item
	if ( bp_is_active( 'activity' ) ) {
		$id = bp_activity_get_activity_id( array(
			'user_id'           => $post->poster_id,
			'component'         => $bp->groups->id,
			'type'              => 'new_forum_post',
			'item_id'           => bp_get_current_group_id(),
			'secondary_item_id' => $post_id
		 ) );
	}

	/** This filter is documented in bp-groups/bp-groups-forums.php */
	$action = apply_filters_ref_array( 'groups_activity_new_forum_post_action',  array( $activity_action,  $post_text, &$topic, &$topic ) );

	/** This filter is documented in bp-groups/bp-groups-forums.php */
	$content = apply_filters_ref_array( 'groups_activity_new_forum_post_content', array( $activity_content, $post_text, &$topic, &$topic ) );

	/** This filter is documented in bp-groups/bp-groups-forums.php */
	$filtered_primary_link = apply_filters( 'groups_activity_new_forum_post_primary_link', $primary_link . "#post-" . $post_id );

	groups_record_activity( array(
		'id'                => $id,
		'action'            => $action,
		'content'           => $content,
		'primary_link'      => $filtered_primary_link,
		'type'              => 'new_forum_post',
		'item_id'           => (int) bp_get_current_group_id(),
		'user_id'           => (int) $post->poster_id,
		'secondary_item_id' => $post_id,
		'recorded_time'     => $post->post_time
	) );

	/**
	 * Fires after the update of a group forum post.
	 *
	 * @since 1.1.0
	 *
	 * @param object $post  Object holding current post being updated.
	 * @param object $topic Object holding current topic details. Passed by reference.
	 */
	do_action_ref_array( 'groups_update_group_forum_post', array( $post, &$topic ) );

	return $post_id;
}

/**
 * Delete a group forum topic and also any corresponding activity items.
 *
 * Uses the bundled version of bbPress packaged with BuddyPress.
 *
 * @since 1.1.0
 *
 * @param int $topic_id The ID of the topic to be deleted.
 *
 * @return bool True if the delete routine went through properly.
 */
function groups_delete_group_forum_topic( $topic_id ) {
	$bp = buddypress();

	// Before deleting the thread, get the post ids so that their activity items can be deleted
	$posts  = bp_forums_get_topic_posts( array( 'topic_id' => $topic_id, 'per_page' => -1 ) );
	$action = bp_forums_delete_topic( array( 'topic_id' => $topic_id ) );

	if ( !empty( $action ) ) {

		/**
		 * Fires before the deletion of a group forum topic.
		 *
		 * @since 1.2.9
		 *
		 * @param int $topic_id ID of the topic to be deleted.
		 */
		do_action( 'groups_before_delete_group_forum_topic', $topic_id );

		// Delete the corresponding activity stream items
		if ( bp_is_active( 'activity' ) ) {

			// The activity item for the initial topic
			bp_activity_delete( array(
				'item_id'           => bp_get_current_group_id(),
				'secondary_item_id' => $topic_id,
				'component'         => $bp->groups->id,
				'type'              => 'new_forum_topic'
			) );

			// The activity item for each post
			foreach ( (array) $posts as $post ) {
				bp_activity_delete( array(
					'item_id'           => bp_get_current_group_id(),
					'secondary_item_id' => $post->post_id,
					'component'         => $bp->groups->id,
					'type'              => 'new_forum_post'
				) );
			}
		}

		/**
		 * Fires after the deletion of a group forum topic.
		 *
		 * @since 1.1.0
		 *
		 * @param int $topic_id ID of the topic that was deleted.
		 */
		do_action( 'groups_delete_group_forum_topic', $topic_id );
	}

	return (bool) $action;
}

/**
 * Delete a group forum post and its corresponding activity item.
 *
 * Uses the bundled version of bbPress packaged with BuddyPress.
 *
 * @since 1.1.0
 *
 * @param int      $post_id  The ID of the post you want to delete.
 * @param int|bool $topic_id Optional. The topic to which the post belongs. This
 *                           value isn't used in the function but is passed along
 *                           to do_action() hooks.
 *
 * @return bool True on success.
 */
function groups_delete_group_forum_post( $post_id, $topic_id = false ) {

	$action = bp_forums_delete_post( array( 'post_id' => $post_id ) );

	if ( !empty( $action ) ) {

		/**
		 * Fires before the deletion of a group forum post.
		 *
		 * @since 1.5.0
		 *
		 * @param int $post_id  ID of the post to be deleted.
		 * @param int $topic_id ID of the associated topic.
		 */
		do_action( 'groups_before_delete_group_forum_post', $post_id, $topic_id );

		// Delete the corresponding activity stream item
		if ( bp_is_active( 'activity' ) ) {
			bp_activity_delete( array(
				'item_id'           => bp_get_current_group_id(),
				'secondary_item_id' => $post_id,
				'component'         => buddypress()->groups->id,
				'type'              => 'new_forum_post'
			) );
		}

		/**
		 * Fires after the deletion of a group forum post.
		 *
		 * @since 1.1.0
		 *
		 * @param int $post_id  ID of the post that was deleted.
		 * @param int $topic_id ID of the associated topic.
		 */
		do_action( 'groups_delete_group_forum_post', $post_id, $topic_id );
	}

	return (bool) $action;
}

/**
 * Get a total count of all public topics of a given type, across groups/forums.
 *
 * @since 1.5.0
 *
 * @param string $type Either 'newest', 'popular', 'unreplied', 'tags'.
 *                     Default: 'newest'.
 *
 * @return int The topic count.
 */
function groups_total_public_forum_topic_count( $type = 'newest' ) {

	/**
	 * Filters the total count of all public topics of a given type, across groups/forums.
	 *
	 * @since 1.1.0
	 *
	 * @param int $value Total count of all public topics.
	 */
	return apply_filters( 'groups_total_public_forum_topic_count', BP_Groups_Group::get_global_forum_topic_count( $type ) );
}

/**
 * Get a total count of all topics of a given status, across groups/forums.
 *
 * @since 1.5.0
 *
 * @param string      $status       Which groups to count. 'public', 'private', 'hidden',
 *                                  'all'. Default: 'public'.
 * @param string|bool $search_terms Optional. Limit by a search term.
 *
 * @return int The topic count.
 */
function groups_total_forum_topic_count( $status = 'public', $search_terms = false ) {

	/**
	 * Filters the total count of all topics of a given status, across groups/forums.
	 *
	 * @since 1.5.0
	 *
	 * @param int $value Total count of all topics.
	 */
	return apply_filters( 'groups_total_forum_topic_count', BP_Groups_Group::get_global_topic_count( $status, $search_terms ) );
}
