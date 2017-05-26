<?php
/**
 * BuddyPress Activity Functions.
 *
 * Functions for the Activity Streams component.
 *
 * @package BuddyPress
 * @subpackage ActivityFunctions
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Check whether the $bp global lists an activity directory page.
 *
 * @since 1.5.0
 *
 * @return bool True if activity directory page is found, otherwise false.
 */
function bp_activity_has_directory() {
	return (bool) !empty( buddypress()->pages->activity->id );
}

/**
 * Are mentions enabled or disabled?
 *
 * The Mentions feature does a number of things, all of which will be turned
 * off if you disable mentions:
 *   - Detecting and auto-linking @username in all BP/WP content.
 *   - Sending BP notifications and emails to users when they are mentioned
 *     using the @username syntax.
 *   - The Public Message button on user profiles.
 *
 * Mentions are enabled by default. To disable, put the following line in
 * bp-custom.php or your theme's functions.php file:
 *
 *   add_filter( 'bp_activity_do_mentions', '__return_false' );
 *
 * @since 1.8.0
 *
 * @uses apply_filters() To call 'bp_activity_do_mentions' hook.
 *
 * @return bool $retval True to enable mentions, false to disable.
 */
function bp_activity_do_mentions() {

	/**
	 * Filters whether or not mentions are enabled.
	 *
	 * @since 1.8.0
	 *
	 * @param bool $enabled True to enable mentions, false to disable.
	 */
	return (bool) apply_filters( 'bp_activity_do_mentions', true );
}

/**
 * Should BuddyPress load the mentions scripts and related assets, including results to prime the
 * mentions suggestions?
 *
 * @since 2.1.0
 *
 * @return bool True if mentions scripts should be loaded.
 */
function bp_activity_maybe_load_mentions_scripts() {
	$mentions_enabled = bp_activity_do_mentions() && bp_is_user_active();
	$load_mentions    = $mentions_enabled && ( bp_is_activity_component() || is_admin() );

	/**
	 * Filters whether or not BuddyPress should load mentions scripts and assets.
	 *
	 * @since 2.1.0
	 *
	 * @param bool $load_mentions    True to load mentions assets, false otherwise.
	 * @param bool $mentions_enabled True if mentions are enabled.
	 */
	return (bool) apply_filters( 'bp_activity_maybe_load_mentions_scripts', $load_mentions, $mentions_enabled );
}

/**
 * Locate usernames in an activity content string, as designated by an @ sign.
 *
 * @since 1.5.0
 *
 * @param string $content The content of the activity, usually found in
 *                        $activity->content.
 * @return array|bool Associative array with user ID as key and username as
 *                    value. Boolean false if no mentions found.
 */
function bp_activity_find_mentions( $content ) {

	$pattern = '/[@]+([A-Za-z0-9-_\.@]+)\b/';
	preg_match_all( $pattern, $content, $usernames );

	// Make sure there's only one instance of each username.
	$usernames = array_unique( $usernames[1] );

	// Bail if no usernames.
	if ( empty( $usernames ) ) {
		return false;
	}

	$mentioned_users = array();

	// We've found some mentions! Check to see if users exist.
	foreach( (array) array_values( $usernames ) as $username ) {
		$user_id = bp_activity_get_userid_from_mentionname( $username );

		// The user ID exists, so let's add it to our array.
		if ( ! empty( $user_id ) ) {
			$mentioned_users[ $user_id ] = $username;
		}
	}

	if ( empty( $mentioned_users ) ) {
		return false;
	}

	return $mentioned_users;
}

/**
 * Reset a user's unread mentions list and count.
 *
 * @since 1.5.0
 *
 * @uses bp_delete_user_meta()
 *
 * @param int $user_id The id of the user whose unread mentions are being reset.
 */
function bp_activity_clear_new_mentions( $user_id ) {
	bp_delete_user_meta( $user_id, 'bp_new_mention_count' );
	bp_delete_user_meta( $user_id, 'bp_new_mentions'      );
}

/**
 * Adjusts mention count for mentioned users in activity items.
 *
 * This function is useful if you only have the activity ID handy and you
 * haven't parsed an activity item for @mentions yet.
 *
 * Currently, only used in {@link bp_activity_delete()}.
 *
 * @since 1.5.0
 *
 * @uses bp_activity_find_mentions()
 * @uses bp_activity_update_mention_count_for_user()
 *
 * @param int    $activity_id The unique id for the activity item.
 * @param string $action      Can be 'delete' or 'add'. Defaults to 'add'.
 * @return bool
 */
function bp_activity_adjust_mention_count( $activity_id = 0, $action = 'add' ) {

	// Bail if no activity ID passed.
	if ( empty( $activity_id ) ) {
		return false;
	}

	// Get activity object.
	$activity  = new BP_Activity_Activity( (int) $activity_id );

	// Try to find mentions.
	$usernames = bp_activity_find_mentions( strip_tags( $activity->content ) );

	// Still empty? Stop now.
	if ( empty( $usernames ) ) {
		return false;
	}

	// Increment mention count foreach mentioned user.
	foreach( (array) array_keys( $usernames ) as $user_id ) {
		bp_activity_update_mention_count_for_user( $user_id, $activity_id, $action );
	}
}

/**
 * Update the mention count for a given user.
 *
 * This function should be used when you've already parsed your activity item
 * for @mentions.
 *
 * @since 1.7.0
 *
 * @uses bp_get_user_meta()
 * @uses bp_update_user_meta()
 *
 * @param int    $user_id     The user ID.
 * @param int    $activity_id The unique ID for the activity item.
 * @param string $action      'delete' or 'add'. Default: 'add'.
 * @return bool
 */
function bp_activity_update_mention_count_for_user( $user_id, $activity_id, $action = 'add' ) {

	if ( empty( $user_id ) || empty( $activity_id ) ) {
		return false;
	}

	// Adjust the mention list and count for the member.
	$new_mention_count = (int) bp_get_user_meta( $user_id, 'bp_new_mention_count', true );
	$new_mentions      =       bp_get_user_meta( $user_id, 'bp_new_mentions',      true );

	// Make sure new mentions is an array.
	if ( empty( $new_mentions ) ) {
		$new_mentions = array();
	}

	switch ( $action ) {
		case 'delete' :
			$key = array_search( $activity_id, $new_mentions );

			if ( $key !== false ) {
				unset( $new_mentions[$key] );
			}

			break;

		case 'add' :
		default :
			if ( !in_array( $activity_id, $new_mentions ) ) {
				$new_mentions[] = (int) $activity_id;
			}

			break;
	}

	// Get an updated mention count.
	$new_mention_count = count( $new_mentions );

	// Resave the user_meta.
	bp_update_user_meta( $user_id, 'bp_new_mention_count', $new_mention_count );
	bp_update_user_meta( $user_id, 'bp_new_mentions',      $new_mentions );

	return true;
}

/**
 * Determine a user's "mentionname", the name used for that user in @-mentions.
 *
 * @since 1.9.0
 *
 * @param int|string $user_id ID of the user to get @-mention name for.
 * @return string $mentionname User name appropriate for @-mentions.
 */
function bp_activity_get_user_mentionname( $user_id ) {
	$mentionname = '';

	$userdata = bp_core_get_core_userdata( $user_id );

	if ( $userdata ) {
		if ( bp_is_username_compatibility_mode() ) {
			$mentionname = str_replace( ' ', '-', $userdata->user_login );
		} else {
			$mentionname = $userdata->user_nicename;
		}
	}

	return $mentionname;
}

/**
 * Get a user ID from a "mentionname", the name used for a user in @-mentions.
 *
 * @since 1.9.0
 *
 * @param string $mentionname Username of user in @-mentions.
 * @return int|bool ID of the user, if one is found. Otherwise false.
 */
function bp_activity_get_userid_from_mentionname( $mentionname ) {
	$user_id = false;

	/*
	 * In username compatibility mode, hyphens are ambiguous between
	 * actual hyphens and converted spaces.
	 *
	 * @todo There is the potential for username clashes between 'foo bar'
	 * and 'foo-bar' in compatibility mode. Come up with a system for
	 * unique mentionnames.
	 */
	if ( bp_is_username_compatibility_mode() ) {
		// First, try the raw username.
		$userdata = get_user_by( 'login', $mentionname );

		// Doing a direct query to use proper regex. Necessary to
		// account for hyphens + spaces in the same user_login.
		if ( empty( $userdata ) || ! is_a( $userdata, 'WP_User' ) ) {
			global $wpdb;
			$regex   = esc_sql( str_replace( '-', '[ \-]', $mentionname ) );
			$user_id = $wpdb->get_var( "SELECT ID FROM {$wpdb->users} WHERE user_login REGEXP '{$regex}'" );
		} else {
			$user_id = $userdata->ID;
		}

	// When username compatibility mode is disabled, the mentionname is
	// the same as the nicename.
	} else {
		$user_id = bp_core_get_userid_from_nicename( $mentionname );
	}


	return $user_id;
}

/** Actions ******************************************************************/

/**
 * Register an activity 'type' and its action description/callback.
 *
 * Activity actions are strings used to describe items in the activity stream,
 * such as 'Joe became a registered member' or 'Bill and Susie are now
 * friends'. Each activity type (such as 'new_member' or 'friendship_created')
 * used by a component should be registered using this function.
 *
 * While it's possible to post items to the activity stream whose types are
 * not registered using bp_activity_set_action(), it is not recommended;
 * unregistered types will not be displayed properly in the activity admin
 * panel, and dynamic action generation (which is essential for multilingual
 * sites, etc) will not work.
 *
 * @since 1.1.0
 *
 * @param  string        $component_id    The unique string ID of the component.
 * @param  string        $type            The action type.
 * @param  string        $description     The action description.
 * @param  callable|bool $format_callback Callback for formatting the action string.
 * @param  string|bool   $label           String to describe this action in the activity stream filter dropdown.
 * @param  array         $context         Optional. Activity stream contexts where the filter should appear.
 *                                        Values: 'activity', 'member', 'member_groups', 'group'.
 * @param  int           $position        Optional. The position of the action when listed in dropdowns.
 * @return bool False if any param is empty, otherwise true.
 */
function bp_activity_set_action( $component_id, $type, $description, $format_callback = false, $label = false, $context = array(), $position = 0 ) {
	$bp = buddypress();

	// Return false if any of the above values are not set.
	if ( empty( $component_id ) || empty( $type ) || empty( $description ) ) {
		return false;
	}

	// Set activity action.
	if ( ! isset( $bp->activity->actions ) || ! is_object( $bp->activity->actions ) ) {
		$bp->activity->actions = new stdClass;
	}

	// Verify callback.
	if ( ! is_callable( $format_callback ) ) {
		$format_callback = '';
	}

	if ( ! isset( $bp->activity->actions->{$component_id} ) || ! is_object( $bp->activity->actions->{$component_id} ) ) {
		$bp->activity->actions->{$component_id} = new stdClass;
	}

	/**
	 * Filters the action type being set for the current activity item.
	 *
	 * @since 1.1.0
	 *
	 * @param array    $array           Array of arguments for action type being set.
	 * @param string   $component_id    ID of the current component being set.
	 * @param string   $type            Action type being set.
	 * @param string   $description     Action description for action being set.
	 * @param callable $format_callback Callback for formatting the action string.
	 * @param string   $label           String to describe this action in the activity stream filter dropdown.
	 * @param array    $context         Activity stream contexts where the filter should appear. 'activity', 'member',
	 *                                  'member_groups', 'group'.
	 */
	$bp->activity->actions->{$component_id}->{$type} = apply_filters( 'bp_activity_set_action', array(
		'key'             => $type,
		'value'           => $description,
		'format_callback' => $format_callback,
		'label'           => $label,
		'context'         => $context,
		'position'        => $position,
	), $component_id, $type, $description, $format_callback, $label, $context );

	return true;
}

/**
 * Set tracking arguments for a given post type.
 *
 * @since 2.2.0
 *
 * @global $wp_post_types
 *
 * @param string $post_type The name of the post type, as registered with WordPress. Eg 'post' or 'page'.
 * @param array  $args {
 *     An associative array of tracking parameters. All items are optional.
 *     @type string   $bp_activity_admin_filter String to use in the Dashboard > Activity dropdown.
 *     @type string   $bp_activity_front_filter String to use in frontend dropdown.
 *     @type string   $bp_activity_new_post     String format to use for generating the activity action. Should be a
 *                                              translatable string where %1$s is replaced by a user link and %2$s is
 *                                              the URL of the newly created post.
 *     @type string   $bp_activity_new_post_ms  String format to use for generating the activity action on Multisite.
 *                                              Should be a translatable string where %1$s is replaced by a user link,
 *                                              %2$s is the URL of the newly created post, and %3$s is a link to
 *                                              the site.
 *     @type string   $component_id             ID of the BuddyPress component to associate the activity item.
 *     @type string   $action_id                Value for the 'type' param of the new activity item.
 *     @type callable $format_callback          Callback for formatting the activity action string.
 *                                              Default: 'bp_activity_format_activity_action_custom_post_type_post'.
 *     @type array    $contexts                 The directory contexts in which the filter will show.
 *                                              Default: array( 'activity' ).
 *     @type array    $position                 Position of the item in filter dropdowns.
 *     @type string   $singular                 Singular, translatable name of the post type item. If no value is
 *                                              provided, it's pulled from the 'singular_name' of the post type.
 *     @type bool     $activity_comment         Whether to allow comments on the activity items. Defaults to true if
 *                                              the post type does not natively support comments, otherwise false.
 * }
 * @return bool
 */
function bp_activity_set_post_type_tracking_args( $post_type = '', $args = array() ) {
	global $wp_post_types;

	if ( empty( $wp_post_types[ $post_type ] ) || ! post_type_supports( $post_type, 'buddypress-activity' ) || ! is_array( $args ) ) {
		return false;
	}

	// Labels are loaded into the post type object.
	foreach ( array( 'bp_activity_admin_filter', 'bp_activity_front_filter', 'bp_activity_new_post', 'bp_activity_new_post_ms' ) as $label_type ) {
		if ( ! empty( $args[ $label_type ] ) ) {
			$wp_post_types[ $post_type ]->labels->{$label_type} = $args[ $label_type ];
			unset( $args[ $post_type ] );
		}
	}

	// If there are any additional args, put them in the bp_activity attribute of the post type.
	if ( ! empty( $args ) ) {
		$wp_post_types[ $post_type ]->bp_activity = $args;
	}
}

/**
 * Get tracking arguments for a specific post type.
 *
 * @since 2.2.0
 *
 * @param  string $post_type Name of the post type.
 * @return object The tracking arguments of the post type.
 */
function bp_activity_get_post_type_tracking_args( $post_type ) {
	if ( ! post_type_supports( $post_type, 'buddypress-activity' ) ) {
		return false;
	}

	$post_type_object = get_post_type_object( $post_type );

	$post_type_activity = array(
		'component_id'      => buddypress()->activity->id,
		'action_id'         => 'new_' . $post_type,
		'format_callback'   => 'bp_activity_format_activity_action_custom_post_type_post',
		'front_filter'      => $post_type_object->labels->name,
		'contexts'          => array( 'activity' ),
		'position'          => 0,
		'singular'          => strtolower( $post_type_object->labels->singular_name ),
		'activity_comment' => ! post_type_supports( $post_type, 'comments' ),
	);

	if ( ! empty( $post_type_object->bp_activity ) ) {
		$post_type_activity = bp_parse_args( (array) $post_type_object->bp_activity, $post_type_activity, $post_type . '_tracking_args' );
	}

	$post_type_activity = (object) $post_type_activity;

	// Try to get the admin filter from the post type labels.
	if ( ! empty( $post_type_object->labels->bp_activity_admin_filter ) ) {
		$post_type_activity->admin_filter = $post_type_object->labels->bp_activity_admin_filter;

	// Fall back to a generic name.
	} else {
		$post_type_activity->admin_filter = _x( 'New item published', 'Post Type generic activity post admin filter', 'buddypress' );
	}

	// Check for the front filter in the post type labels.
	if ( ! empty( $post_type_object->labels->bp_activity_front_filter ) ) {
		$post_type_activity->front_filter = $post_type_object->labels->bp_activity_front_filter;
	}

	// Try to get the action for new post type action on non-multisite installations.
	if ( ! empty( $post_type_object->labels->bp_activity_new_post ) ) {
		$post_type_activity->new_post_type_action = $post_type_object->labels->bp_activity_new_post;
	}

	// Try to get the action for new post type action on multisite installations.
	if ( ! empty( $post_type_object->labels->bp_activity_new_post_ms ) ) {
		$post_type_activity->new_post_type_action_ms = $post_type_object->labels->bp_activity_new_post_ms;
	}

	/**
	 * Filters tracking arguments for a specific post type.
	 *
	 * @since 2.2.0
	 *
	 * @param object $post_type_activity The tracking arguments of the post type.
	 * @param string $post_type          Name of the post type.
	 */
	return apply_filters( 'bp_activity_get_post_type_tracking_args', $post_type_activity, $post_type );
}

/**
 * Get tracking arguments for all post types.
 *
 * @since 2.2.0
 *
 * @return array List of post types with their tracking arguments.
 */
function bp_activity_get_post_types_tracking_args() {
	// Fetch all public post types.
	$post_types = get_post_types( array( 'public' => true ), 'names' );

	$post_types_tracking_args = array();

	foreach ( $post_types as $post_type ) {
		$track_post_type = bp_activity_get_post_type_tracking_args( $post_type );

		if ( ! empty( $track_post_type ) ) {
			$post_types_tracking_args[ $track_post_type->action_id ] = $track_post_type;
		}

	}

	/**
	 * Filters tracking arguments for all post types.
	 *
	 * @since 2.2.0
	 *
	 * @param array $post_types_tracking_args Array of post types with
	 *                                        their tracking arguments.
	 */
	return apply_filters( 'bp_activity_get_post_types_tracking_args', $post_types_tracking_args );
}

/**
 * Get all components' activity actions, sorted by their position attribute.
 *
 * @since 2.2.0
 *
 * @return object Actions ordered by their position.
 */
function bp_activity_get_actions() {
	$bp = buddypress();

	$post_types = bp_activity_get_post_types_tracking_args();

	// Create the actions for the post types, if they haven't already been created.
	if ( ! empty( $post_types ) ) {
		foreach ( $post_types as $post_type ) {
			if ( isset( $bp->activity->actions->{$post_type->component_id}->{$post_type->action_id} ) ) {
				continue;
			}

			bp_activity_set_action(
				$post_type->component_id,
				$post_type->action_id,
				$post_type->admin_filter,
				$post_type->format_callback,
				$post_type->front_filter,
				$post_type->contexts,
				$post_type->position
			);
		}
	}

	// Sort the actions by their position within each component.
	foreach ( $bp->activity->actions as $component => $actions ) {
		$actions = (array) $actions;
		$temp = bp_sort_by_key( $actions, 'position', 'num' );

		// Restore keys.
		$bp->activity->actions->{$component} = new stdClass;
		foreach ( $temp as $key_ordered ) {
			$bp->activity->actions->{$component}->{$key_ordered['key']} = $key_ordered;
		}
	}

	return $bp->activity->actions;
}

/**
 * Retrieve the current action from a component and key.
 *
 * @since 1.1.0
 *
 * @uses apply_filters() To call the 'bp_activity_get_action' hook.
 *
 * @param string $component_id The unique string ID of the component.
 * @param string $key          The action key.
 * @return string|bool Action value if found, otherwise false.
 */
function bp_activity_get_action( $component_id, $key ) {

	// Return false if any of the above values are not set.
	if ( empty( $component_id ) || empty( $key ) ) {
		return false;
	}

	$actions = bp_activity_get_actions();
	$retval  = false;

	if ( isset( $actions->{$component_id}->{$key} ) ) {
		$retval = $actions->{$component_id}->{$key};
	}

	/**
	 * Filters the current action by component and key.
	 *
	 * @since 1.1.0
	 *
	 * @param string|bool $retval       The action key.
	 * @param string      $component_id The unique string ID of the component.
	 * @param string      $key          The action key.
	 */
	return apply_filters( 'bp_activity_get_action', $retval, $component_id, $key );
}

/**
 * Fetch details of all registered activity types.
 *
 * @since 1.7.0
 *
 * @return array array( type => description ), ...
 */
function bp_activity_get_types() {
	$actions  = array();

	// Walk through the registered actions, and build an array of actions/values.
	foreach ( bp_activity_get_actions() as $action ) {
		$action = array_values( (array) $action );

		for ( $i = 0, $i_count = count( $action ); $i < $i_count; $i++ ) {
			$actions[ $action[$i]['key'] ] = $action[$i]['value'];
		}
	}

	// This was a mis-named activity type from before BP 1.6.
	unset( $actions['friends_register_activity_action'] );

	/**
	 * Filters the available activity types.
	 *
	 * @since 1.7.0
	 *
	 * @param array $actions Array of registered activity types.
	 */
	return apply_filters( 'bp_activity_get_types', $actions );
}

/** Favorites ****************************************************************/

/**
 * Get a users favorite activity stream items.
 *
 * @since 1.2.0
 *
 * @uses bp_get_user_meta()
 * @uses apply_filters() To call the 'bp_activity_get_user_favorites' hook.
 *
 * @param int $user_id ID of the user whose favorites are being queried.
 * @return array IDs of the user's favorite activity items.
 */
function bp_activity_get_user_favorites( $user_id = 0 ) {

	// Fallback to logged in user if no user_id is passed.
	if ( empty( $user_id ) ) {
		$user_id = bp_displayed_user_id();
	}

	// Get favorites for user.
	$favs = bp_get_user_meta( $user_id, 'bp_favorite_activities', true );

	/**
	 * Filters the favorited activity items for a specified user.
	 *
	 * @since 1.2.0
	 *
	 * @param array $favs Array of user's favorited activity items.
	 */
	return apply_filters( 'bp_activity_get_user_favorites', $favs );
}

/**
 * Add an activity stream item as a favorite for a user.
 *
 * @since 1.2.0
 *
 * @uses is_user_logged_in()
 * @uses bp_get_user_meta()
 * @uses bp_activity_get_meta()
 * @uses bp_update_user_meta()
 * @uses bp_activity_update_meta()
 * @uses do_action() To call the 'bp_activity_add_user_favorite' hook.
 * @uses do_action() To call the 'bp_activity_add_user_favorite_fail' hook.
 *
 * @param int $activity_id ID of the activity item being favorited.
 * @param int $user_id     ID of the user favoriting the activity item.
 * @return bool True on success, false on failure.
 */
function bp_activity_add_user_favorite( $activity_id, $user_id = 0 ) {

	// Favorite activity stream items are for logged in users only.
	if ( ! is_user_logged_in() ) {
		return false;
	}

	// Fallback to logged in user if no user_id is passed.
	if ( empty( $user_id ) ) {
		$user_id = bp_loggedin_user_id();
	}

	$my_favs = bp_get_user_meta( $user_id, 'bp_favorite_activities', true );
	if ( empty( $my_favs ) || ! is_array( $my_favs ) ) {
		$my_favs = array();
	}

	// Bail if the user has already favorited this activity item.
	if ( in_array( $activity_id, $my_favs ) ) {
		return false;
	}

	// Add to user's favorites.
	$my_favs[] = $activity_id;

	// Update the total number of users who have favorited this activity.
	$fav_count = bp_activity_get_meta( $activity_id, 'favorite_count' );
	$fav_count = !empty( $fav_count ) ? (int) $fav_count + 1 : 1;

	// Update user meta.
	bp_update_user_meta( $user_id, 'bp_favorite_activities', $my_favs );

	// Update activity meta counts.
	if ( bp_activity_update_meta( $activity_id, 'favorite_count', $fav_count ) ) {

		/**
		 * Fires if bp_activity_update_meta() for favorite_count is successful and before returning a true value for success.
		 *
		 * @since 1.2.1
		 *
		 * @param int $activity_id ID of the activity item being favorited.
		 * @param int $user_id     ID of the user doing the favoriting.
		 */
		do_action( 'bp_activity_add_user_favorite', $activity_id, $user_id );

		// Success.
		return true;

	// Saving meta was unsuccessful for an unknown reason.
	} else {

		/**
		 * Fires if bp_activity_update_meta() for favorite_count is unsuccessful and before returning a false value for failure.
		 *
		 * @since 1.5.0
		 *
		 * @param int $activity_id ID of the activity item being favorited.
		 * @param int $user_id     ID of the user doing the favoriting.
		 */
		do_action( 'bp_activity_add_user_favorite_fail', $activity_id, $user_id );

		return false;
	}
}

/**
 * Remove an activity stream item as a favorite for a user.
 *
 * @since 1.2.0
 *
 * @uses is_user_logged_in()
 * @uses bp_get_user_meta()
 * @uses bp_activity_get_meta()
 * @uses bp_activity_update_meta()
 * @uses bp_update_user_meta()
 * @uses do_action() To call the 'bp_activity_remove_user_favorite' hook.
 *
 * @param int $activity_id ID of the activity item being unfavorited.
 * @param int $user_id     ID of the user unfavoriting the activity item.
 * @return bool True on success, false on failure.
 */
function bp_activity_remove_user_favorite( $activity_id, $user_id = 0 ) {

	// Favorite activity stream items are for logged in users only.
	if ( ! is_user_logged_in() ) {
		return false;
	}

	// Fallback to logged in user if no user_id is passed.
	if ( empty( $user_id ) ) {
		$user_id = bp_loggedin_user_id();
	}

	$my_favs = bp_get_user_meta( $user_id, 'bp_favorite_activities', true );
	$my_favs = array_flip( (array) $my_favs );

	// Bail if the user has not previously favorited the item.
	if ( ! isset( $my_favs[ $activity_id ] ) ) {
		return false;
	}

	// Remove the fav from the user's favs.
	unset( $my_favs[$activity_id] );
	$my_favs = array_unique( array_flip( $my_favs ) );

	// Update the total number of users who have favorited this activity.
	$fav_count = bp_activity_get_meta( $activity_id, 'favorite_count' );
	if ( ! empty( $fav_count ) ) {

		// Deduct from total favorites.
		if ( bp_activity_update_meta( $activity_id, 'favorite_count', (int) $fav_count - 1 ) ) {

			// Update users favorites.
			if ( bp_update_user_meta( $user_id, 'bp_favorite_activities', $my_favs ) ) {

				/**
				 * Fires if bp_update_user_meta() is successful and before returning a true value for success.
				 *
				 * @since 1.2.1
				 *
				 * @param int $activity_id ID of the activity item being unfavorited.
				 * @param int $user_id     ID of the user doing the unfavoriting.
				 */
				do_action( 'bp_activity_remove_user_favorite', $activity_id, $user_id );

				// Success.
				return true;

			// Error updating.
			} else {
				return false;
			}

		// Error updating favorite count.
		} else {
			return false;
		}

	// Error getting favorite count.
	} else {
		return false;
	}
}

/**
 * Check whether an activity item exists with a given content string.
 *
 * @since 1.1.0
 *
 * @uses BP_Activity_Activity::check_exists_by_content() {@link BP_Activity_Activity}
 * @uses apply_filters() To call the 'bp_activity_check_exists_by_content' hook.
 *
 * @param string $content The content to filter by.
 * @return int|null The ID of the located activity item. Null if none is found.
 */
function bp_activity_check_exists_by_content( $content ) {

	/**
	 * Filters the results of the check for whether an activity item exists by specified content.
	 *
	 * @since 1.1.0
	 *
	 * @param BP_Activity_Activity $content_exists ID of the activity if found, else null.
	 */
	return apply_filters( 'bp_activity_check_exists_by_content', BP_Activity_Activity::check_exists_by_content( $content ) );
}

/**
 * Retrieve the last time activity was updated.
 *
 * @since 1.0.0
 *
 * @uses BP_Activity_Activity::get_last_updated() {@link BP_Activity_Activity}
 * @uses apply_filters() To call the 'bp_activity_get_last_updated' hook.
 *
 * @return string Date last updated.
 */
function bp_activity_get_last_updated() {

	/**
	 * Filters the value for the last updated time for an activity item.
	 *
	 * @since 1.1.0
	 *
	 * @param BP_Activity_Activity $last_updated Date last updated.
	 */
	return apply_filters( 'bp_activity_get_last_updated', BP_Activity_Activity::get_last_updated() );
}

/**
 * Retrieve the number of favorite activity stream items a user has.
 *
 * @since 1.2.0
 *
 * @uses BP_Activity_Activity::total_favorite_count() {@link BP_Activity_Activity}
 *
 * @param int $user_id ID of the user whose favorite count is being requested.
 * @return int Total favorite count for the user.
 */
function bp_activity_total_favorites_for_user( $user_id = 0 ) {

	// Fallback on displayed user, and then logged in user.
	if ( empty( $user_id ) ) {
		$user_id = ( bp_displayed_user_id() ) ? bp_displayed_user_id() : bp_loggedin_user_id();
	}

	return BP_Activity_Activity::total_favorite_count( $user_id );
}

/** Meta *********************************************************************/

/**
 * Delete a meta entry from the DB for an activity stream item.
 *
 * @since 1.2.0
 *
 * @global object $wpdb WordPress database access object.
 *
 * @param int    $activity_id ID of the activity item whose metadata is being deleted.
 * @param string $meta_key    Optional. The key of the metadata being deleted. If
 *                            omitted, all metadata associated with the activity
 *                            item will be deleted.
 * @param string $meta_value  Optional. If present, the metadata will only be
 *                            deleted if the meta_value matches this parameter.
 * @param bool   $delete_all  Optional. If true, delete matching metadata entries
 *                            for all objects, ignoring the specified object_id. Otherwise,
 *                            only delete matching metadata entries for the specified
 *                            activity item. Default: false.
 * @return bool True on success, false on failure.
 */
function bp_activity_delete_meta( $activity_id, $meta_key = '', $meta_value = '', $delete_all = false ) {

	// Legacy - if no meta_key is passed, delete all for the item.
	if ( empty( $meta_key ) ) {
		$all_meta = bp_activity_get_meta( $activity_id );
		$keys     = ! empty( $all_meta ) ? array_keys( $all_meta ) : array();

		// With no meta_key, ignore $delete_all.
		$delete_all = false;
	} else {
		$keys = array( $meta_key );
	}

	$retval = true;

	add_filter( 'query', 'bp_filter_metaid_column_name' );
	foreach ( $keys as $key ) {
		$retval = delete_metadata( 'activity', $activity_id, $key, $meta_value, $delete_all );
	}
	remove_filter( 'query', 'bp_filter_metaid_column_name' );

	return $retval;
}

/**
 * Get metadata for a given activity item.
 *
 * @since 1.2.0
 *
 * @uses apply_filters() To call the 'bp_activity_get_meta' hook.
 *
 * @param int    $activity_id ID of the activity item whose metadata is being requested.
 * @param string $meta_key    Optional. If present, only the metadata matching
 *                            that meta key will be returned. Otherwise, all metadata for the
 *                            activity item will be fetched.
 * @param bool   $single      Optional. If true, return only the first value of the
 *                            specified meta_key. This parameter has no effect if meta_key is not
 *                            specified. Default: true.
 * @return mixed The meta value(s) being requested.
 */
function bp_activity_get_meta( $activity_id = 0, $meta_key = '', $single = true ) {
	add_filter( 'query', 'bp_filter_metaid_column_name' );
	$retval = get_metadata( 'activity', $activity_id, $meta_key, $single );
	remove_filter( 'query', 'bp_filter_metaid_column_name' );

	/**
	 * Filters the metadata for a specified activity item.
	 *
	 * @since 1.5.0
	 *
	 * @param mixed  $retval      The meta values for the activity item.
	 * @param int    $activity_id ID of the activity item.
	 * @param string $meta_key    Meta key for the value being requested.
	 * @param bool   $single      Whether to return one matched meta key row or all.
	 */
	return apply_filters( 'bp_activity_get_meta', $retval, $activity_id, $meta_key, $single );
}

/**
 * Update a piece of activity meta.
 *
 * @since 1.2.0
 *
 * @param int    $activity_id ID of the activity item whose metadata is being updated.
 * @param string $meta_key    Key of the metadata being updated.
 * @param mixed  $meta_value  Value to be set.
 * @param mixed  $prev_value  Optional. If specified, only update existing metadata entries
 *                            with the specified value. Otherwise, update all entries.
 * @return bool|int Returns false on failure. On successful update of existing
 *                  metadata, returns true. On successful creation of new metadata,
 *                  returns the integer ID of the new metadata row.
 */
function bp_activity_update_meta( $activity_id, $meta_key, $meta_value, $prev_value = '' ) {
	add_filter( 'query', 'bp_filter_metaid_column_name' );
	$retval = update_metadata( 'activity', $activity_id, $meta_key, $meta_value, $prev_value );
	remove_filter( 'query', 'bp_filter_metaid_column_name' );

	return $retval;
}

/**
 * Add a piece of activity metadata.
 *
 * @since 2.0.0
 *
 * @param int    $activity_id ID of the activity item.
 * @param string $meta_key    Metadata key.
 * @param mixed  $meta_value  Metadata value.
 * @param bool   $unique      Optional. Whether to enforce a single metadata value for the
 *                            given key. If true, and the object already has a value for
 *                            the key, no change will be made. Default: false.
 * @return int|bool The meta ID on successful update, false on failure.
 */
function bp_activity_add_meta( $activity_id, $meta_key, $meta_value, $unique = false ) {
	add_filter( 'query', 'bp_filter_metaid_column_name' );
	$retval = add_metadata( 'activity', $activity_id, $meta_key, $meta_value, $unique );
	remove_filter( 'query', 'bp_filter_metaid_column_name' );

	return $retval;
}

/** Clean up *****************************************************************/

/**
 * Completely remove a user's activity data.
 *
 * @since 1.5.0
 *
 * @uses is_user_logged_in()
 * @uses bp_activity_delete()
 * @uses bp_delete_user_meta()
 * @uses do_action() To call the 'bp_activity_remove_data' hook.
 * @uses do_action() To call the 'bp_activity_remove_all_user_data' hook.
 *
 * @param int $user_id ID of the user whose activity is being deleted.
 * @return bool
 */
function bp_activity_remove_all_user_data( $user_id = 0 ) {

	// Do not delete user data unless a logged in user says so.
	if ( empty( $user_id ) || ! is_user_logged_in() ) {
		return false;
	}

	// Clear the user's activity from the sitewide stream and clear their activity tables.
	bp_activity_delete( array( 'user_id' => $user_id ) );

	// Remove any usermeta.
	bp_delete_user_meta( $user_id, 'bp_latest_update'       );
	bp_delete_user_meta( $user_id, 'bp_favorite_activities' );

	// Execute additional code
	do_action( 'bp_activity_remove_data', $user_id ); // Deprecated! Do not use!

	/**
	 * Fires after the removal of all of a user's activity data.
	 *
	 * @since 1.5.0
	 *
	 * @param int $user_id ID of the user being deleted.
	 */
	do_action( 'bp_activity_remove_all_user_data', $user_id );
}
add_action( 'wpmu_delete_user',  'bp_activity_remove_all_user_data' );
add_action( 'delete_user',       'bp_activity_remove_all_user_data' );

/**
 * Mark all of the user's activity as spam.
 *
 * @since 1.6.0
 *
 * @global object $wpdb WordPress database access object.
 *
 * @param int $user_id ID of the user whose activity is being spammed.
 * @return bool
 */
function bp_activity_spam_all_user_data( $user_id = 0 ) {
	global $wpdb;

	// Do not delete user data unless a logged in user says so.
	if ( empty( $user_id ) || ! is_user_logged_in() ) {
		return false;
	}

	// Get all the user's activities.
	$activities = bp_activity_get( array(
		'display_comments' => 'stream',
		'filter'           => array( 'user_id' => $user_id ),
		'show_hidden'      => true
	) );

	$bp = buddypress();

	// Mark each as spam.
	foreach ( (array) $activities['activities'] as $activity ) {

		// Create an activity object.
		$activity_obj = new BP_Activity_Activity;
		foreach ( $activity as $k => $v ) {
			$activity_obj->$k = $v;
		}

		// Mark as spam.
		bp_activity_mark_as_spam( $activity_obj );

		/*
		 * If Akismet is present, update the activity history meta.
		 *
		 * This is usually taken care of when BP_Activity_Activity::save() happens, but
		 * as we're going to be updating all the activity statuses directly, for efficiency,
		 * we need to update manually.
		 */
		if ( ! empty( $bp->activity->akismet ) ) {
			$bp->activity->akismet->update_activity_spam_meta( $activity_obj );
		}

		// Tidy up.
		unset( $activity_obj );
	}

	// Mark all of this user's activities as spam.
	$wpdb->query( $wpdb->prepare( "UPDATE {$bp->activity->table_name} SET is_spam = 1 WHERE user_id = %d", $user_id ) );

	/**
	 * Fires after all activity data from a user has been marked as spam.
	 *
	 * @since 1.6.0
	 *
	 * @param int   $user_id    ID of the user whose activity is being marked as spam.
	 * @param array $activities Array of activity items being marked as spam.
	 */
	do_action( 'bp_activity_spam_all_user_data', $user_id, $activities['activities'] );
}
add_action( 'bp_make_spam_user', 'bp_activity_spam_all_user_data' );

/**
 * Mark all of the user's activity as ham (not spam).
 *
 * @since 1.6.0
 *
 * @global object $wpdb WordPress database access object.
 *
 * @param int $user_id ID of the user whose activity is being hammed.
 * @return bool
 */
function bp_activity_ham_all_user_data( $user_id = 0 ) {
	global $wpdb;

	// Do not delete user data unless a logged in user says so.
	if ( empty( $user_id ) || ! is_user_logged_in() ) {
		return false;
	}

	// Get all the user's activities.
	$activities = bp_activity_get( array(
		'display_comments' => 'stream',
		'filter'           => array( 'user_id' => $user_id ),
		'show_hidden'      => true,
		'spam'             => 'all'
	) );

	$bp = buddypress();

	// Mark each as not spam.
	foreach ( (array) $activities['activities'] as $activity ) {

		// Create an activity object.
		$activity_obj = new BP_Activity_Activity;
		foreach ( $activity as $k => $v ) {
			$activity_obj->$k = $v;
		}

		// Mark as not spam.
		bp_activity_mark_as_ham( $activity_obj );

		/*
		 * If Akismet is present, update the activity history meta.
		 *
		 * This is usually taken care of when BP_Activity_Activity::save() happens, but
		 * as we're going to be updating all the activity statuses directly, for efficiency,
		 * we need to update manually.
		 */
		if ( ! empty( $bp->activity->akismet ) ) {
			$bp->activity->akismet->update_activity_ham_meta( $activity_obj );
		}

		// Tidy up.
		unset( $activity_obj );
	}

	// Mark all of this user's activities as not spam.
	$wpdb->query( $wpdb->prepare( "UPDATE {$bp->activity->table_name} SET is_spam = 0 WHERE user_id = %d", $user_id ) );

	/**
	 * Fires after all activity data from a user has been marked as ham.
	 *
	 * @since 1.6.0
	 *
	 * @param int   $user_id    ID of the user whose activity is being marked as ham.
	 * @param array $activities Array of activity items being marked as ham.
	 */
	do_action( 'bp_activity_ham_all_user_data', $user_id, $activities['activities'] );
}
add_action( 'bp_make_ham_user', 'bp_activity_ham_all_user_data' );

/**
 * Register the activity stream actions for updates.
 *
 * @since 1.6.0
 */
function bp_activity_register_activity_actions() {
	$bp = buddypress();

	bp_activity_set_action(
		$bp->activity->id,
		'activity_update',
		__( 'Posted a status update', 'buddypress' ),
		'bp_activity_format_activity_action_activity_update',
		__( 'Updates', 'buddypress' ),
		array( 'activity', 'group', 'member', 'member_groups' )
	);

	bp_activity_set_action(
		$bp->activity->id,
		'activity_comment',
		__( 'Replied to a status update', 'buddypress' ),
		'bp_activity_format_activity_action_activity_comment',
		__( 'Activity Comments', 'buddypress' )
	);

	/**
	 * Fires at the end of the activity actions registration.
	 *
	 * Allows plugin authors to add their own activity actions alongside the core actions.
	 *
	 * @since 1.6.0
	 */
	do_action( 'bp_activity_register_activity_actions' );

	// Backpat. Don't use this.
	do_action( 'updates_register_activity_actions' );
}
add_action( 'bp_register_activity_actions', 'bp_activity_register_activity_actions' );

/**
 * Generate an activity action string for an activity item.
 *
 * @param object $activity Activity data object.
 * @return string|bool Returns false if no callback is found, otherwise returns
 *                     the formatted action string.
 */
function bp_activity_generate_action_string( $activity ) {

	// Check for valid input.
	if ( empty( $activity->component ) || empty( $activity->type ) ) {
		return false;
	}

	// Check for registered format callback.
	$actions = bp_activity_get_actions();
	if ( empty( $actions->{$activity->component}->{$activity->type}['format_callback'] ) ) {
		return false;
	}

	// We apply the format_callback as a filter.
	add_filter( 'bp_activity_generate_action_string', $actions->{$activity->component}->{$activity->type}['format_callback'], 10, 2 );

	/**
	 * Filters the string for the activity action being returned.
	 *
	 * @since 2.0.0
	 *
	 * @param BP_Activity_Activity $action   Action string being requested.
	 * @param BP_Activity_Activity $activity Activity item object.
	 */
	$action = apply_filters( 'bp_activity_generate_action_string', $activity->action, $activity );

	// Remove the filter for future activity items.
	remove_filter( 'bp_activity_generate_action_string', $actions->{$activity->component}->{$activity->type}['format_callback'], 10, 2 );

	return $action;
}

/**
 * Format 'activity_update' activity actions.
 *
 * @since 2.0.0
 *
 * @param string $action   Static activity action.
 * @param object $activity Activity data object.
 * @return string $action
 */
function bp_activity_format_activity_action_activity_update( $action, $activity ) {
	$action = sprintf( __( '%s posted an update', 'buddypress' ), bp_core_get_userlink( $activity->user_id ) );

	/**
	 * Filters the formatted activity action update string.
	 *
	 * @since 1.2.0
	 *
	 * @param string               $action   Activity action string value.
	 * @param BP_Activity_Activity $activity Activity item object.
	 */
	return apply_filters( 'bp_activity_new_update_action', $action, $activity );
}

/**
 * Format 'activity_comment' activity actions.
 *
 * @since 2.0.0
 *
 * @param string $action   Static activity action.
 * @param object $activity Activity data object.
 * @return string $action
 */
function bp_activity_format_activity_action_activity_comment( $action, $activity ) {
	$action = sprintf( __( '%s posted a new activity comment', 'buddypress' ), bp_core_get_userlink( $activity->user_id ) );

	/**
	 * Filters the formatted activity action comment string.
	 *
	 * @since 1.2.0
	 *
	 * @param string               $action   Activity action string value.
	 * @param BP_Activity_Activity $activity Activity item object.
	 */
	return apply_filters( 'bp_activity_comment_action', $action, $activity );
}

/**
 * Format activity action strings for custom post types.
 *
 * @since 2.2.0
 *
 * @param string $action   Static activity action.
 * @param object $activity Activity data object.
 * @return string $action
 */
function bp_activity_format_activity_action_custom_post_type_post( $action, $activity ) {
	$bp = buddypress();

	// Fetch all the tracked post types once.
	if ( empty( $bp->activity->track ) ) {
		$bp->activity->track = bp_activity_get_post_types_tracking_args();
	}

	if ( empty( $activity->type ) || empty( $bp->activity->track[ $activity->type ] ) ) {
		return $action;
	}

	$user_link = bp_core_get_userlink( $activity->user_id );
	$blog_url  = get_home_url( $activity->item_id );

	if ( empty( $activity->post_url ) ) {
		$post_url = add_query_arg( 'p', $activity->secondary_item_id, trailingslashit( $blog_url ) );
	} else {
		$post_url = $activity->post_url;
	}

	if ( is_multisite() ) {
		$blog_link = '<a href="' . esc_url( $blog_url ) . '">' . get_blog_option( $activity->item_id, 'blogname' ) . '</a>';

		if ( ! empty( $bp->activity->track[ $activity->type ]->new_post_type_action_ms ) ) {
			$action = sprintf( $bp->activity->track[ $activity->type ]->new_post_type_action_ms, $user_link, $post_url, $blog_link );
		} else {
			$action = sprintf( _x( '%1$s wrote a new <a href="%2$s">item</a>, on the site %3$s', 'Activity Custom Post Type post action', 'buddypress' ), $user_link, esc_url( $post_url ), $blog_link );
		}
	} else {
		if ( ! empty( $bp->activity->track[ $activity->type ]->new_post_type_action ) ) {
			$action = sprintf( $bp->activity->track[ $activity->type ]->new_post_type_action, $user_link, $post_url );
		} else {
			$action = sprintf( _x( '%1$s wrote a new <a href="%2$s">item</a>', 'Activity Custom Post Type post action', 'buddypress' ), $user_link, esc_url( $post_url ) );
		}
	}

	/**
	 * Filters the formatted custom post type activity post action string.
	 *
	 * @since 2.2.0
	 *
	 * @param string               $action   Activity action string value.
	 * @param BP_Activity_Activity $activity Activity item object.
	 */
	return apply_filters( 'bp_activity_custom_post_type_post_action', $action, $activity );
}

/*
 * Business functions are where all the magic happens in BuddyPress. They will
 * handle the actual saving or manipulation of information. Usually they will
 * hand off to a database class for data access, then return
 * true or false on success or failure.
 */

/**
 * Retrieve an activity or activities.
 *
 * The bp_activity_get() function shares all arguments with BP_Activity_Activity::get().
 * The following is a list of bp_activity_get() parameters that have different
 * default values from BP_Activity_Activity::get() (value in parentheses is
 * the default for the bp_activity_get()).
 *   - 'per_page' (false)
 *
 * @since 1.2.0
 * @since 2.4.0 Introduced the `$fields` parameter.
 *
 * @see BP_Activity_Activity::get() For more information on accepted arguments
 *      and the format of the returned value.
 * @uses wp_parse_args()
 * @uses wp_cache_get()
 * @uses wp_cache_set()
 * @uses BP_Activity_Activity::get() {@link BP_Activity_Activity}
 * @uses apply_filters_ref_array() To call the 'bp_activity_get' hook.
 *
 * @param array|string $args See BP_Activity_Activity::get() for description.
 * @return array $activity See BP_Activity_Activity::get() for description.
 */
function bp_activity_get( $args = '' ) {

	$r = bp_parse_args( $args, array(
		'max'               => false,        // Maximum number of results to return
		'fields'            => 'all',
		'page'              => 1,            // page 1 without a per_page will result in no pagination.
		'per_page'          => false,        // results per page
		'sort'              => 'DESC',       // sort ASC or DESC
		'display_comments'  => false,        // false for no comments. 'stream' for within stream display, 'threaded' for below each activity item

		'search_terms'      => false,        // Pass search terms as a string
		'meta_query'        => false,        // Filter by activity meta. See WP_Meta_Query for format
		'date_query'        => false,        // Filter by date. See first parameter of WP_Date_Query for format
		'filter_query'      => false,
		'show_hidden'       => false,        // Show activity items that are hidden site-wide?
		'exclude'           => false,        // Comma-separated list of activity IDs to exclude.
		'in'                => false,        // Comma-separated list or array of activity IDs to which you
		                                     // want to limit the query.
		'spam'              => 'ham_only',   // 'ham_only' (default), 'spam_only' or 'all'.
		'update_meta_cache' => true,
		'count_total'       => false,
		'scope'             => false,

		/**
		 * Pass filters as an array -- all filter items can be multiple values comma separated:
		 * array(
		 *     'user_id'      => false, // User ID to filter on.
		 *     'object'       => false, // Object to filter on e.g. groups, profile, status, friends.
		 *     'action'       => false, // Action to filter on e.g. activity_update, profile_updated.
		 *     'primary_id'   => false, // Object ID to filter on e.g. a group_id or forum_id or blog_id etc.
		 *     'secondary_id' => false, // Secondary object ID to filter on e.g. a post_id.
		 * );
		 */
		'filter' => array()
	) );

	// Attempt to return a cached copy of the first page of sitewide activity.
	if ( ( 1 === (int) $r['page'] ) && empty( $r['max'] ) && ( 'all' === $r['fields'] ) && empty( $r['search_terms'] ) && empty( $r['meta_query'] ) && empty( $r['date_query'] ) && empty( $r['filter_query'] ) && empty( $r['filter'] ) && empty( $r['scope'] )&& empty( $r['exclude'] ) && empty( $r['in'] ) && ( 'DESC' === $r['sort'] ) && empty( $r['exclude'] ) && ( 'ham_only' === $r['spam'] ) ) {

		$activity = wp_cache_get( 'bp_activity_sitewide_front', 'bp' );
		if ( false === $activity ) {

			$activity = BP_Activity_Activity::get( array(
				'page'              => $r['page'],
				'per_page'          => $r['per_page'],
				'max'               => $r['max'],
				'fields'            => $r['fields'],
				'sort'              => $r['sort'],
				'search_terms'      => $r['search_terms'],
				'meta_query'        => $r['meta_query'],
				'date_query'        => $r['date_query'],
				'filter_query'      => $r['filter_query'],
				'filter'            => $r['filter'],
				'scope'             => $r['scope'],
				'display_comments'  => $r['display_comments'],
				'show_hidden'       => $r['show_hidden'],
				'spam'              => $r['spam'],
				'update_meta_cache' => $r['update_meta_cache'],
				'count_total'       => $r['count_total'],
			) );

			wp_cache_set( 'bp_activity_sitewide_front', $activity, 'bp' );
		}

	} else {
		$activity = BP_Activity_Activity::get( array(
			'page'             => $r['page'],
			'per_page'         => $r['per_page'],
			'max'              => $r['max'],
			'sort'             => $r['sort'],
			'search_terms'     => $r['search_terms'],
			'meta_query'       => $r['meta_query'],
			'date_query'       => $r['date_query'],
			'filter_query'     => $r['filter_query'],
			'filter'           => $r['filter'],
			'scope'            => $r['scope'],
			'display_comments' => $r['display_comments'],
			'show_hidden'      => $r['show_hidden'],
			'exclude'          => $r['exclude'],
			'in'               => $r['in'],
			'spam'             => $r['spam'],
			'count_total'      => $r['count_total'],
		) );
	}

	/**
	 * Filters the requested activity item(s).
	 *
	 * @since 1.2.0
	 *
	 * @param BP_Activity_Activity $activity Requested activity object.
	 * @param array                $r        Arguments used for the activity query.
	 */
	return apply_filters_ref_array( 'bp_activity_get', array( &$activity, &$r ) );
}

/**
 * Fetch specific activity items.
 *
 * @since 1.2.0
 *
 * @see BP_Activity_Activity::get() For more information on accepted arguments.
 * @uses wp_parse_args()
 * @uses apply_filters() To call the 'bp_activity_get_specific' hook.
 * @uses BP_Activity_Activity::get() {@link BP_Activity_Activity}
 *
 * @param array|string $args {
 *     All arguments and defaults are shared with BP_Activity_Activity::get(),
 *     except for the following:
 *     @type string|int|array Single activity ID, comma-separated list of IDs,
 *                            or array of IDs.
 * }
 * @return array $activity See BP_Activity_Activity::get() for description.
 */
function bp_activity_get_specific( $args = '' ) {

	$r = bp_parse_args( $args, array(
		'activity_ids'      => false,      // A single activity_id or array of IDs.
		'display_comments'  => false,      // True or false to display threaded comments for these specific activity items.
		'max'               => false,      // Maximum number of results to return.
		'page'              => 1,          // Page 1 without a per_page will result in no pagination.
		'per_page'          => false,      // Results per page.
		'show_hidden'       => true,       // When fetching specific items, show all.
		'sort'              => 'DESC',     // Sort ASC or DESC
		'spam'              => 'ham_only', // Retrieve items marked as spam.
		'update_meta_cache' => true,
	) );

	$get_args = array(
		'display_comments'  => $r['display_comments'],
		'in'                => $r['activity_ids'],
		'max'               => $r['max'],
		'page'              => $r['page'],
		'per_page'          => $r['per_page'],
		'show_hidden'       => $r['show_hidden'],
		'sort'              => $r['sort'],
		'spam'              => $r['spam'],
		'update_meta_cache' => $r['update_meta_cache'],
	);

	/**
	 * Filters the requested specific activity item.
	 *
	 * @since 1.2.0
	 *
	 * @param BP_Activity_Activity $activity Requested activity object.
	 * @param array                $args     Original passed in arguments.
	 * @param array                $get_args Constructed arguments used with request.
	 */
	return apply_filters( 'bp_activity_get_specific', BP_Activity_Activity::get( $get_args ), $args, $get_args );
}

/**
 * Add an activity item.
 *
 * @since 1.1.0
 *
 * @uses wp_parse_args()
 * @uses BP_Activity_Activity::save() {@link BP_Activity_Activity}
 * @uses BP_Activity_Activity::rebuild_activity_comment_tree() {@link BP_Activity_Activity}
 * @uses wp_cache_delete()
 * @uses do_action() To call the 'bp_activity_add' hook.
 *
 * @param array|string $args {
 *     An array of arguments.
 *     @type int|bool $id                Pass an activity ID to update an existing item, or
 *                                       false to create a new item. Default: false.
 *     @type string   $action            Optional. The activity action/description, typically
 *                                       something like "Joe posted an update". Values passed to this param
 *                                       will be stored in the database and used as a fallback for when the
 *                                       activity item's format_callback cannot be found (eg, when the
 *                                       component is disabled). As long as you have registered a
 *                                       format_callback for your $type, it is unnecessary to include this
 *                                       argument - BP will generate it automatically.
 *                                       See {@link bp_activity_set_action()}.
 *     @type string   $content           Optional. The content of the activity item.
 *     @type string   $component         The unique name of the component associated with
 *                                       the activity item - 'groups', 'profile', etc.
 *     @type string   $type              The specific activity type, used for directory
 *                                       filtering. 'new_blog_post', 'activity_update', etc.
 *     @type string   $primary_link      Optional. The URL for this item, as used in
 *                                       RSS feeds. Defaults to the URL for this activity
 *                                       item's permalink page.
 *     @type int|bool $user_id           Optional. The ID of the user associated with the activity
 *                                       item. May be set to false or 0 if the item is not related
 *                                       to any user. Default: the ID of the currently logged-in user.
 *     @type int      $item_id           Optional. The ID of the associated item.
 *     @type int      $secondary_item_id Optional. The ID of a secondary associated item.
 *     @type string   $date_recorded     Optional. The GMT time, in Y-m-d h:i:s format, when
 *                                       the item was recorded. Defaults to the current time.
 *     @type bool     $hide_sitewide     Should the item be hidden on sitewide streams?
 *                                       Default: false.
 *     @type bool     $is_spam           Should the item be marked as spam? Default: false.
 * }
 * @return int|bool The ID of the activity on success. False on error.
 */
function bp_activity_add( $args = '' ) {

	$r = bp_parse_args( $args, array(
		'id'                => false,                  // Pass an existing activity ID to update an existing entry.
		'action'            => '',                     // The activity action - e.g. "Jon Doe posted an update"
		'content'           => '',                     // Optional: The content of the activity item e.g. "BuddyPress is awesome guys!"
		'component'         => false,                  // The name/ID of the component e.g. groups, profile, mycomponent.
		'type'              => false,                  // The activity type e.g. activity_update, profile_updated.
		'primary_link'      => '',                     // Optional: The primary URL for this item in RSS feeds (defaults to activity permalink).
		'user_id'           => bp_loggedin_user_id(),  // Optional: The user to record the activity for, can be false if this activity is not for a user.
		'item_id'           => false,                  // Optional: The ID of the specific item being recorded, e.g. a blog_id.
		'secondary_item_id' => false,                  // Optional: A second ID used to further filter e.g. a comment_id.
		'recorded_time'     => bp_core_current_time(), // The GMT time that this activity was recorded.
		'hide_sitewide'     => false,                  // Should this be hidden on the sitewide activity stream?
		'is_spam'           => false,                  // Is this activity item to be marked as spam?
	), 'activity_add' );

	// Make sure we are backwards compatible.
	if ( empty( $r['component'] ) && !empty( $r['component_name'] ) ) {
		$r['component'] = $r['component_name'];
	}

	if ( empty( $r['type'] ) && !empty( $r['component_action'] ) ) {
		$r['type'] = $r['component_action'];
	}

	// Setup activity to be added.
	$activity                    = new BP_Activity_Activity( $r['id'] );
	$activity->user_id           = $r['user_id'];
	$activity->component         = $r['component'];
	$activity->type              = $r['type'];
	$activity->content           = $r['content'];
	$activity->primary_link      = $r['primary_link'];
	$activity->item_id           = $r['item_id'];
	$activity->secondary_item_id = $r['secondary_item_id'];
	$activity->date_recorded     = $r['recorded_time'];
	$activity->hide_sitewide     = $r['hide_sitewide'];
	$activity->is_spam           = $r['is_spam'];
	$activity->action            = ! empty( $r['action'] )
										? $r['action']
										: bp_activity_generate_action_string( $activity );

	if ( ! $activity->save() ) {
		return false;
	}

	// If this is an activity comment, rebuild the tree.
	if ( 'activity_comment' === $activity->type ) {
		// Also clear the comment cache for the parent activity ID.
		wp_cache_delete( $activity->item_id, 'bp_activity_comments' );

		BP_Activity_Activity::rebuild_activity_comment_tree( $activity->item_id );
	}

	wp_cache_delete( 'bp_activity_sitewide_front', 'bp' );

	/**
	 * Fires at the end of the execution of adding a new activity item, before returning the new activity item ID.
	 *
	 * @since 1.1.0
	 *
	 * @param array $r Array of parsed arguments for the activity item being added.
	 */
	do_action( 'bp_activity_add', $r );

	return $activity->id;
}

/**
 * Post an activity update.
 *
 * @since 1.2.0
 *
 * @uses wp_parse_args()
 * @uses bp_is_user_inactive()
 * @uses bp_core_get_userlink()
 * @uses bp_activity_add()
 * @uses apply_filters() To call the 'bp_activity_new_update_action' hook.
 * @uses apply_filters() To call the 'bp_activity_new_update_content' hook.
 * @uses apply_filters() To call the 'bp_activity_new_update_primary_link' hook.
 * @uses bp_update_user_meta()
 * @uses wp_filter_kses()
 * @uses do_action() To call the 'bp_activity_posted_update' hook.
 *
 * @param array|string $args {
 *     @type string $content The content of the activity update.
 *     @type int    $user_id Optional. Defaults to the logged-in user.
 * }
 * @return int $activity_id The activity id.
 */
function bp_activity_post_update( $args = '' ) {

	$r = wp_parse_args( $args, array(
		'content' => false,
		'user_id' => bp_loggedin_user_id()
	) );

	if ( empty( $r['content'] ) || !strlen( trim( $r['content'] ) ) ) {
		return false;
	}

	if ( bp_is_user_inactive( $r['user_id'] ) ) {
		return false;
	}

	// Record this on the user's profile.
	$activity_content = $r['content'];
	$primary_link     = bp_core_get_userlink( $r['user_id'], false, true );

	/**
	 * Filters the new activity content for current activity item.
	 *
	 * @since 1.2.0
	 *
	 * @param string $activity_content Activity content posted by user.
	 */
	$add_content = apply_filters( 'bp_activity_new_update_content', $activity_content );

	/**
	 * Filters the activity primary link for current activity item.
	 *
	 * @since 1.2.0
	 *
	 * @param string $primary_link Link to the profile for the user who posted the activity.
	 */
	$add_primary_link = apply_filters( 'bp_activity_new_update_primary_link', $primary_link );

	// Now write the values.
	$activity_id = bp_activity_add( array(
		'user_id'      => $r['user_id'],
		'content'      => $add_content,
		'primary_link' => $add_primary_link,
		'component'    => buddypress()->activity->id,
		'type'         => 'activity_update',
	) );

	/**
	 * Filters the latest update content for the activity item.
	 *
	 * @since 1.6.0
	 *
	 * @param string $r                Content of the activity update.
	 * @param string $activity_content Content of the activity update.
	 */
	$activity_content = apply_filters( 'bp_activity_latest_update_content', $r['content'], $activity_content );

	// Add this update to the "latest update" usermeta so it can be fetched anywhere.
	bp_update_user_meta( bp_loggedin_user_id(), 'bp_latest_update', array(
		'id'      => $activity_id,
		'content' => $activity_content
	) );

	/**
	 * Fires at the end of an activity post update, before returning the updated activity item ID.
	 *
	 * @since 1.2.0
	 *
	 * @param string $content     Content of the activity post update.
	 * @param int    $user_id     ID of the user posting the activity update.
	 * @param int    $activity_id ID of the activity item being updated.
	 */
	do_action( 'bp_activity_posted_update', $r['content'], $r['user_id'], $activity_id );

	return $activity_id;
}

/**
 * Create an activity item for a newly published post type post.
 *
 * @since 2.2.0
 *
 * @param int     $post_id ID of the new post.
 * @param WP_Post $post    Post object.
 * @param int     $user_id ID of the post author.
 * @return int|bool The ID of the activity on success. False on error.
 */
function bp_activity_post_type_publish( $post_id = 0, $post = null, $user_id = 0 ) {

	if ( ! is_a( $post, 'WP_Post' ) ) {
		return;
	}

	// Get the post type tracking args.
	$activity_post_object = bp_activity_get_post_type_tracking_args( $post->post_type );

	if ( 'publish' != $post->post_status || ! empty( $post->post_password ) || empty( $activity_post_object->action_id ) ) {
		return;
	}

	if ( empty( $post_id ) ) {
		$post_id = $post->ID;
	}

	$blog_id = get_current_blog_id();

	if ( empty( $user_id ) ) {
		$user_id = (int) $post->post_author;
	}

	// Bail if an activity item already exists for this post.
	$existing = bp_activity_get( array(
		'filter' => array(
			'action'       => $activity_post_object->action_id,
			'primary_id'   => $blog_id,
			'secondary_id' => $post_id,
		)
	) );

	if ( ! empty( $existing['activities'] ) ) {
		return;
	}

	/**
	 * Filters whether or not to post the activity.
	 *
	 * This is a variable filter, dependent on the post type,
	 * that lets components or plugins bail early if needed.
	 *
	 * @since 2.2.0
	 *
	 * @param bool $value   Whether or not to continue.
	 * @param int  $blog_id ID of the current site.
	 * @param int  $post_id ID of the current post being published.
	 * @param int  $user_id ID of the current user or post author.
	 */
	if ( false === apply_filters( "bp_activity_{$post->post_type}_pre_publish", true, $blog_id, $post_id, $user_id ) ) {
		return;
	}

	// Record this in activity streams.
	$blog_url = get_home_url( $blog_id );
	$post_url = add_query_arg(
		'p',
		$post_id,
		trailingslashit( $blog_url )
	);

	// Backward compatibility filters for the 'blogs' component.
	if ( 'blogs' == $activity_post_object->component_id )  {
		$activity_content      = apply_filters( 'bp_blogs_activity_new_post_content', $post->post_content, $post, $post_url, $post->post_type );
		$activity_primary_link = apply_filters( 'bp_blogs_activity_new_post_primary_link', $post_url, $post_id, $post->post_type );
	} else {
		$activity_content      = $post->post_content;
		$activity_primary_link = $post_url;
	}

	$activity_args = array(
		'user_id'           => $user_id,
		'content'           => $activity_content,
		'primary_link'      => $activity_primary_link,
		'component'         => $activity_post_object->component_id,
		'type'              => $activity_post_object->action_id,
		'item_id'           => $blog_id,
		'secondary_item_id' => $post_id,
		'recorded_time'     => $post->post_date_gmt,
	);

	if ( ! empty( $activity_args['content'] ) ) {
		// Create the excerpt.
		$activity_summary = bp_activity_create_summary( $activity_args['content'], $activity_args );

		// Backward compatibility filter for blog posts.
		if ( 'blogs' == $activity_post_object->component_id )  {
			$activity_args['content'] = apply_filters( 'bp_blogs_record_activity_content', $activity_summary, $activity_args['content'], $activity_args, $post->post_type );
		} else {
			$activity_args['content'] = $activity_summary;
		}
	}

	// Set up the action by using the format functions.
	$action_args = array_merge( $activity_args, array(
		'post_title' => $post->post_title,
		'post_url'   => $post_url,
	) );

	$activity_args['action'] = call_user_func_array( $activity_post_object->format_callback, array( '', (object) $action_args ) );

	// Make sure the action is set.
	if ( empty( $activity_args['action'] ) ) {
		return;
	} else {
		// Backward compatibility filter for the blogs component.
		if ( 'blogs' == $activity_post_object->component_id )  {
			$activity_args['action'] = apply_filters( 'bp_blogs_record_activity_action', $activity_args['action'] );
		}
	}

	$activity_id = bp_activity_add( $activity_args );

	/**
	 * Fires after the publishing of an activity item for a newly published post type post.
	 *
	 * @since 2.2.0
	 *
	 * @param int     $activity_id   ID of the newly published activity item.
	 * @param WP_Post $post          Post object.
	 * @param array   $activity_args Array of activity arguments.
	 */
	do_action( 'bp_activity_post_type_published', $activity_id, $post, $activity_args );

	return $activity_id;
}

/**
 * Update the activity item for a custom post type entry.
 *
 * @since 2.2.0
 *
 * @param WP_Post $post Post item.
 * @return bool True on success, false on failure.
 */
function bp_activity_post_type_update( $post = null ) {

	if ( ! is_a( $post, 'WP_Post' ) ) {
		return;
	}

	// Get the post type tracking args.
	$activity_post_object = bp_activity_get_post_type_tracking_args( $post->post_type );

	if ( empty( $activity_post_object->action_id ) ) {
		return;
	}

	$activity_id = bp_activity_get_activity_id( array(
		'component'         => $activity_post_object->component_id,
		'item_id'           => get_current_blog_id(),
		'secondary_item_id' => $post->ID,
		'type'              => $activity_post_object->action_id,
	) );

	// Activity ID doesn't exist, so stop!
	if ( empty( $activity_id ) ) {
		return;
	}

	// Delete the activity if the post was updated with a password.
	if ( ! empty( $post->post_password ) ) {
		bp_activity_delete( array( 'id' => $activity_id ) );
	}

	// Update the activity entry.
	$activity = new BP_Activity_Activity( $activity_id );

	if ( ! empty( $post->post_content ) ) {
		$activity_summary = bp_activity_create_summary( $post->post_content, (array) $activity );

		// Backward compatibility filter for the blogs component.
		if ( 'blogs' == $activity_post_object->component_id ) {
			$activity->content = apply_filters( 'bp_blogs_record_activity_content', $activity_summary, $post->post_content, (array) $activity, $post->post_type );
		} else {
			$activity->content = $activity_summary;
		}
	}

	// Save the updated activity.
	$updated = $activity->save();

	/**
	 * Fires after the updating of an activity item for a custom post type entry.
	 *
	 * @since 2.2.0
	 *
	 * @param WP_Post              $post     Post object.
	 * @param BP_Activity_Activity $activity Activity object.
	 */
	do_action( 'bp_activity_post_type_updated', $post, $activity );

	return $updated;
}

/**
 * Unpublish an activity for the custom post type.
 *
 * @since 2.2.0
 *
 * @param  int     $post_id ID of the post being unpublished.
 * @param  WP_Post $post    Post object.
 * @return bool True on success, false on failure.
 */
function bp_activity_post_type_unpublish( $post_id = 0, $post = null ) {

	if ( ! is_a( $post, 'WP_Post' ) ) {
		return;
	}

	// Get the post type tracking args.
	$activity_post_object = bp_activity_get_post_type_tracking_args( $post->post_type );

	if ( empty( $activity_post_object->action_id ) ) {
		return;
	}

	if ( empty( $post_id ) ) {
		$post_id = $post->ID;
	}

	$delete_activity_args = array(
		'item_id'           => get_current_blog_id(),
		'secondary_item_id' => $post_id,
		'component'         => $activity_post_object->component_id,
		'type'              => $activity_post_object->action_id,
		'user_id'           => false,
	);

	$deleted = bp_activity_delete_by_item_id( $delete_activity_args );

	/**
	 * Fires after the unpublishing for the custom post type.
	 *
	 * @since 2.2.0
	 *
	 * @param array   $delete_activity_args Array of arguments for activity deletion.
	 * @param WP_Post $post                 Post object.
	 * @param bool    $activity             Whether or not the activity was successfully deleted.
	 */
	do_action( 'bp_activity_post_type_unpublished', $delete_activity_args, $post, $deleted );

	return $deleted;
}

/**
 * Add an activity comment.
 *
 * @since 1.2.0
 *
 * @uses wp_parse_args()
 * @uses bp_activity_add()
 * @uses apply_filters() To call the 'bp_activity_comment_action' hook.
 * @uses apply_filters() To call the 'bp_activity_comment_content' hook.
 * @uses wp_cache_delete()
 * @uses do_action() To call the 'bp_activity_comment_posted' hook.
 *
 * @param array|string $args {
 *     @type int    $id          Optional. Pass an ID to update an existing comment.
 *     @type string $content     The content of the comment.
 *     @type int    $user_id     Optional. The ID of the user making the comment.
 *                               Defaults to the ID of the logged-in user.
 *     @type int    $activity_id The ID of the "root" activity item, ie the oldest
 *                               ancestor of the comment.
 *     @type int    $parent_id   Optional. The ID of the parent activity item, ie the item to
 *                               which the comment is an immediate reply. If not provided,
 *                               this value defaults to the $activity_id.
 * }
 * @return int|bool The ID of the comment on success, otherwise false.
 */
function bp_activity_new_comment( $args = '' ) {
	$bp       = buddypress();
	$errors   = new WP_Error();
	$feedback = __( 'There was an error posting your reply. Please try again.', 'buddypress' );

	if ( empty( $bp->activity->errors ) ) {
		$bp->activity->errors = array();
	}

	$r = wp_parse_args( $args, array(
		'id'          => false,
		'content'     => false,
		'user_id'     => bp_loggedin_user_id(),
		'activity_id' => false, // ID of the root activity item.
		'parent_id'   => false  // ID of a parent comment (optional).
	) );

	// Bail if missing necessary data.
	if ( empty( $r['content'] ) || empty( $r['user_id'] ) || empty( $r['activity_id'] ) ) {
		$errors->add( 'missing_data', $feedback );
		$bp->activity->errors['new_comment'] = $errors;

		return false;
	}

	// Maybe set current activity ID as the parent.
	if ( empty( $r['parent_id'] ) ) {
		$r['parent_id'] = $r['activity_id'];
	}

	$activity_id = $r['activity_id'];

	// Get the parent activity.
	$activity  = new BP_Activity_Activity( $activity_id );

	// Bail if the parent activity does not exist.
	if ( empty( $activity->date_recorded ) ) {
		$errors->add( 'missing_activity', __( 'Sorry, the item you are replying to no longer exists.', 'buddypress' ) );
		$bp->activity->errors['new_comment'] = $errors;

		return false;
	}

	// Check to see if the parent activity is hidden, and if so, hide this comment publicly.
	$is_hidden = ( (int) $activity->hide_sitewide ) ? 1 : 0;

	/**
	 * Filters the content of a new comment.
	 *
	 * @since 1.2.0
	 *
	 * @param string $r Content for the newly posted comment.
	 */
	$comment_content = apply_filters( 'bp_activity_comment_content', $r['content'] );

	// Insert the activity comment.
	$comment_id = bp_activity_add( array(
		'id'                => $r['id'],
		'content'           => $comment_content,
		'component'         => buddypress()->activity->id,
		'type'              => 'activity_comment',
		'user_id'           => $r['user_id'],
		'item_id'           => $activity_id,
		'secondary_item_id' => $r['parent_id'],
		'hide_sitewide'     => $is_hidden
	) );

	// Comment caches are stored only with the top-level item.
	wp_cache_delete( $activity_id, 'bp_activity_comments' );

	// Walk the tree to clear caches for all parent items.
	$clear_id = $r['parent_id'];
	while ( $clear_id != $activity_id ) {
		$clear_object = new BP_Activity_Activity( $clear_id );
		wp_cache_delete( $clear_id, 'bp_activity' );
		$clear_id = intval( $clear_object->secondary_item_id );
	}
	wp_cache_delete( $activity_id, 'bp_activity' );

	/**
	 * Fires near the end of an activity comment posting, before the returning of the comment ID.
	 *
	 * @since 1.2.0
	 *
	 * @param int                  $comment_id ID of the newly posted activity comment.
	 * @param array                $r          Array of parsed comment arguments.
	 * @param BP_Activity_Activity $activity   Activity item being commented on.
	 */
	do_action( 'bp_activity_comment_posted', $comment_id, $r, $activity );

	if ( empty( $comment_id ) ) {
		$errors->add( 'comment_failed', $feedback );
		$bp->activity->errors['new_comment'] = $errors;
	}

	return $comment_id;
}

/**
 * Fetch the activity_id for an existing activity entry in the DB.
 *
 * @since 1.2.0
 *
 * @see BP_Activity_Activity::get() For more information on accepted arguments.
 * @uses wp_parse_args()
 * @uses apply_filters() To call the 'bp_activity_get_activity_id' hook.
 * @uses BP_Activity_Activity::save() {@link BP_Activity_Activity}
 *
 * @param array|string $args See BP_Activity_Activity::get() for description.
 * @return int $activity_id The ID of the activity item found.
 */
function bp_activity_get_activity_id( $args = '' ) {

	$r = bp_parse_args( $args, array(
		'user_id'           => false,
		'component'         => false,
		'type'              => false,
		'item_id'           => false,
		'secondary_item_id' => false,
		'action'            => false,
		'content'           => false,
		'date_recorded'     => false,
	) );

	/**
	 * Filters the activity ID being requested.
	 *
	 * @since 1.2.0
	 *
	 * @param BP_Activity_Activity $value ID returned by BP_Activity_Activity get_id() method with provided arguments.
	 */
	return apply_filters( 'bp_activity_get_activity_id', BP_Activity_Activity::get_id(
		$r['user_id'],
		$r['component'],
		$r['type'],
		$r['item_id'],
		$r['secondary_item_id'],
		$r['action'],
		$r['content'],
		$r['date_recorded']
	) );
}

/**
 * Delete activity item(s).
 *
 * If you're looking to hook into one action that provides the ID(s) of
 * the activity/activities deleted, then use:
 *
 * add_action( 'bp_activity_deleted_activities', 'my_function' );
 *
 * The action passes one parameter that is a single activity ID or an
 * array of activity IDs depending on the number deleted.
 *
 * If you are deleting an activity comment please use bp_activity_delete_comment();
 *
 * @since 1.0.0
 *
 * @see BP_Activity_Activity::get() For more information on accepted arguments.
 * @uses wp_parse_args()
 * @uses bp_activity_adjust_mention_count()
 * @uses BP_Activity_Activity::delete() {@link BP_Activity_Activity}
 * @uses do_action() To call the 'bp_before_activity_delete' hook.
 * @uses bp_get_user_meta()
 * @uses bp_delete_user_meta()
 * @uses do_action() To call the 'bp_activity_delete' hook.
 * @uses do_action() To call the 'bp_activity_deleted_activities' hook.
 * @uses wp_cache_delete()
 *
 * @param array|string $args To delete specific activity items, use
 *                           $args = array( 'id' => $ids ); Otherwise, to use
 *                           filters for item deletion, the argument format is
 *                           the same as BP_Activity_Activity::get().
 *                           See that method for a description.
 * @return bool True on success, false on failure.
 */
function bp_activity_delete( $args = '' ) {

	// Pass one or more the of following variables to delete by those variables.
	$args = bp_parse_args( $args, array(
		'id'                => false,
		'action'            => false,
		'content'           => false,
		'component'         => false,
		'type'              => false,
		'primary_link'      => false,
		'user_id'           => false,
		'item_id'           => false,
		'secondary_item_id' => false,
		'date_recorded'     => false,
		'hide_sitewide'     => false
	) );

	/**
	 * Fires before an activity item proceeds to be deleted.
	 *
	 * @since 1.5.0
	 *
	 * @param array $args Array of arguments to be used with the activity deletion.
	 */
	do_action( 'bp_before_activity_delete', $args );

	// Adjust the new mention count of any mentioned member.
	bp_activity_adjust_mention_count( $args['id'], 'delete' );

	$activity_ids_deleted = BP_Activity_Activity::delete( $args );
	if ( empty( $activity_ids_deleted ) ) {
		return false;
	}

	// Check if the user's latest update has been deleted.
	$user_id = empty( $args['user_id'] )
		? bp_loggedin_user_id()
		: $args['user_id'];

	$latest_update = bp_get_user_meta( $user_id, 'bp_latest_update', true );
	if ( !empty( $latest_update ) ) {
		if ( in_array( (int) $latest_update['id'], (array) $activity_ids_deleted ) ) {
			bp_delete_user_meta( $user_id, 'bp_latest_update' );
		}
	}

	/**
	 * Fires after the activity item has been deleted.
	 *
	 * @since 1.0.0
	 *
	 * @param array $args Array of arguments used with the activity deletion.
	 */
	do_action( 'bp_activity_delete', $args );

	/**
	 * Fires after the activity item has been deleted.
	 *
	 * @since 1.2.0
	 *
	 * @param array $activity_ids_deleted Array of affected activity item IDs.
	 */
	do_action( 'bp_activity_deleted_activities', $activity_ids_deleted );

	wp_cache_delete( 'bp_activity_sitewide_front', 'bp' );

	return true;
}

	/**
	 * Delete an activity item by activity id.
	 *
	 * You should use bp_activity_delete() instead.
	 *
	 * @since 1.1.0
	 * @deprecated 1.2.0
	 *
	 * @uses wp_parse_args()
	 * @uses bp_activity_delete()
	 *
	 * @param array|string $args See BP_Activity_Activity::get for a
	 *                           description of accepted arguments.
	 * @return bool True on success, false on failure.
	 */
	function bp_activity_delete_by_item_id( $args = '' ) {

		$r = bp_parse_args( $args, array(
			'item_id'           => false,
			'component'         => false,
			'type'              => false,
			'user_id'           => false,
			'secondary_item_id' => false
		) );

		return bp_activity_delete( $r );
	}

	/**
	 * Delete an activity item by activity id.
	 *
	 * @since 1.1.0
	 *
	 * @uses bp_activity_delete()
	 *
	 * @param int $activity_id ID of the activity item to be deleted.
	 * @return bool True on success, false on failure.
	 */
	function bp_activity_delete_by_activity_id( $activity_id ) {
		return bp_activity_delete( array( 'id' => $activity_id ) );
	}

	/**
	 * Delete an activity item by its content.
	 *
	 * You should use bp_activity_delete() instead.
	 *
	 * @since 1.1.0
	 * @deprecated 1.2.0
	 *
	 * @uses bp_activity_delete()
	 *
	 * @param int    $user_id   The user id.
	 * @param string $content   The activity id.
	 * @param string $component The activity component.
	 * @param string $type      The activity type.
	 * @return bool True on success, false on failure.
	 */
	function bp_activity_delete_by_content( $user_id, $content, $component, $type ) {
		return bp_activity_delete( array(
			'user_id'   => $user_id,
			'content'   => $content,
			'component' => $component,
			'type'      => $type
		) );
	}

	/**
	 * Delete a user's activity for a component.
	 *
	 * You should use bp_activity_delete() instead.
	 *
	 * @since 1.1.0
	 * @deprecated 1.2.0
	 *
	 * @uses bp_activity_delete()
	 *
	 * @param int    $user_id   The user id.
	 * @param string $component The activity component.
	 * @return bool True on success, false on failure.
	 */
	function bp_activity_delete_for_user_by_component( $user_id, $component ) {
		return bp_activity_delete( array(
			'user_id'   => $user_id,
			'component' => $component
		) );
	}

/**
 * Delete an activity comment.
 *
 * @since 1.2.0
 *
 * @uses apply_filters() To call the 'bp_activity_delete_comment_pre' hook.
 * @uses bp_activity_delete_children()
 * @uses bp_activity_delete()
 * @uses BP_Activity_Activity::rebuild_activity_comment_tree() {@link BP_Activity_Activity}
 * @uses do_action() To call the 'bp_activity_delete_comment' hook.
 * @todo Why is an activity id required? We could look this up.
 * @todo Why do we encourage users to call this function directly? We could just
 *       as easily examine the activity type in bp_activity_delete() and then
 *       call this function with the proper arguments if necessary.
 *
 * @param int $activity_id The ID of the "root" activity, ie the comment's
 *                         oldest ancestor.
 * @param int $comment_id  The ID of the comment to be deleted.
 * @return bool True on success, false on failure.
 */
function bp_activity_delete_comment( $activity_id, $comment_id ) {

	/**
	 * Filters whether BuddyPress should delete an activity comment or not.
	 *
	 * You may want to hook into this filter if you want to override this function and
	 * handle the deletion of child comments differently. Make sure you return false.
	 *
	 * @since 1.2.0
	 *
	 * @param bool $value       Whether BuddyPress should continue or not.
	 * @param int  $activity_id ID of the root activity item being deleted.
	 * @param int  $comment_id  ID of the comment being deleted.
	 */
	if ( ! apply_filters( 'bp_activity_delete_comment_pre', true, $activity_id, $comment_id ) ) {
		return false;
	}

	// Delete any children of this comment.
	bp_activity_delete_children( $activity_id, $comment_id );

	// Delete the actual comment.
	if ( ! bp_activity_delete( array( 'id' => $comment_id, 'type' => 'activity_comment' ) ) ) {
		return false;
	}

	// Purge comment cache for the root activity update.
	wp_cache_delete( $activity_id, 'bp_activity_comments' );

	// Recalculate the comment tree.
	BP_Activity_Activity::rebuild_activity_comment_tree( $activity_id );

	/**
	 * Fires at the end of the deletion of an activity comment, before returning success.
	 *
	 * @since 1.2.0
	 *
	 * @param int $activity_id ID of the activity that has had a comment deleted from.
	 * @param int $comment_id  ID of the comment that was deleted.
	 */
	do_action( 'bp_activity_delete_comment', $activity_id, $comment_id );

	return true;
}

	/**
	 * Delete an activity comment's children.
	 *
	 * @since 1.2.0
	 *
	 * @uses BP_Activity_Activity::get_child_comments() {@link BP_Activity_Activity}
	 * @uses bp_activity_delete_children()
	 * @uses bp_activity_delete()
	 *
	 * @param int $activity_id The ID of the "root" activity, ie the
	 *                         comment's oldest ancestor.
	 * @param int $comment_id  The ID of the comment to be deleted.
	 */
	function bp_activity_delete_children( $activity_id, $comment_id ) {

		// Get activity children to delete.
		$children = BP_Activity_Activity::get_child_comments( $comment_id );

		// Recursively delete all children of this comment.
		if ( ! empty( $children ) ) {
			foreach( (array) $children as $child ) {
				bp_activity_delete_children( $activity_id, $child->id );
			}
		}

		// Delete the comment itself.
		bp_activity_delete( array(
			'secondary_item_id' => $comment_id,
			'type'              => 'activity_comment',
			'item_id'           => $activity_id
		) );
	}

/**
 * Get the permalink for a single activity item.
 *
 * When only the $activity_id param is passed, BP has to instantiate a new
 * BP_Activity_Activity object. To save yourself some processing overhead,
 * be sure to pass the full $activity_obj parameter as well, if you already
 * have it available.
 *
 * @since 1.2.0
 *
 * @uses bp_get_root_domain()
 * @uses bp_get_activity_root_slug()
 * @uses apply_filters_ref_array() To call the 'bp_activity_get_permalink' hook.
 *
 * @param int         $activity_id  The unique id of the activity object.
 * @param object|bool $activity_obj Optional. The activity object.
 * @return string $link Permalink for the activity item.
 */
function bp_activity_get_permalink( $activity_id, $activity_obj = false ) {
	$bp = buddypress();

	if ( empty( $activity_obj ) ) {
		$activity_obj = new BP_Activity_Activity( $activity_id );
	}

	if ( isset( $activity_obj->current_comment ) ) {
		$activity_obj = $activity_obj->current_comment;
	}

	$use_primary_links = array(
		'new_blog_post',
		'new_blog_comment',
		'new_forum_topic',
		'new_forum_post',
	);

	if ( ! empty( $bp->activity->track ) ) {
		$use_primary_links = array_merge( $use_primary_links, array_keys( $bp->activity->track ) );
	}

	if ( false !== array_search( $activity_obj->type, $use_primary_links ) ) {
		$link = $activity_obj->primary_link;
	} else {
		if ( 'activity_comment' == $activity_obj->type ) {
			$link = bp_get_root_domain() . '/' . bp_get_activity_root_slug() . '/p/' . $activity_obj->item_id . '/';
		} else {
			$link = bp_get_root_domain() . '/' . bp_get_activity_root_slug() . '/p/' . $activity_obj->id . '/';
		}
	}

	/**
	 * Filters the activity permalink for the specified activity item.
	 *
	 * @since 1.2.0
	 *
	 * @param array $array Array holding activity permalink and activity item object.
	 */
	return apply_filters_ref_array( 'bp_activity_get_permalink', array( $link, &$activity_obj ) );
}

/**
 * Hide a user's activity.
 *
 * @since 1.2.0
 *
 * @uses BP_Activity_Activity::hide_all_for_user() {@link BP_Activity_Activity}
 *
 * @param int $user_id The ID of the user whose activity is being hidden.
 * @return bool True on success, false on failure.
 */
function bp_activity_hide_user_activity( $user_id ) {
	return BP_Activity_Activity::hide_all_for_user( $user_id );
}

/**
 * Take content, remove images, and replace them with a single thumbnail image.
 *
 * The format of items in the activity stream is such that we do not want to
 * allow an arbitrary number of arbitrarily large images to be rendered.
 * However, the activity stream is built to elegantly display a single
 * thumbnail corresponding to the activity comment. This function looks
 * through the content, grabs the first image and converts it to a thumbnail,
 * and removes the rest of the images from the string.
 *
 * As of BuddyPress 2.3, this function is no longer in use.
 *
 * @since 1.2.0
 *
 * @uses esc_attr()
 * @uses apply_filters() To call the 'bp_activity_thumbnail_content_images' hook.
 *
 * @param string      $content The content of the activity item.
 * @param string|bool $link    Optional. The unescaped URL that the image should link
 *                             to. If absent, the image will not be a link.
 * @param array|bool  $args    Optional. The args passed to the activity
 *                             creation function (eg bp_blogs_record_activity()).
 * @return string $content The content with images stripped and replaced with a
 *                         single thumb.
 */
function bp_activity_thumbnail_content_images( $content, $link = false, $args = false ) {

	preg_match_all( '/<img[^>]*>/Ui', $content, $matches );

	// Remove <img> tags. Also remove caption shortcodes and caption text if present.
	$content = preg_replace('|(\[caption(.*?)\])?<img[^>]*>([^\[\[]*\[\/caption\])?|', '', $content );

	if ( !empty( $matches ) && !empty( $matches[0] ) ) {

		// Get the SRC value.
		preg_match( '/<img.*?(src\=[\'|"]{0,1}.*?[\'|"]{0,1})[\s|>]{1}/i',    $matches[0][0], $src    );

		// Get the width and height.
		preg_match( '/<img.*?(height\=[\'|"]{0,1}.*?[\'|"]{0,1})[\s|>]{1}/i', $matches[0][0], $height );
		preg_match( '/<img.*?(width\=[\'|"]{0,1}.*?[\'|"]{0,1})[\s|>]{1}/i',  $matches[0][0], $width  );

		if ( ! empty( $src ) ) {
			$src = substr( substr( str_replace( 'src=', '', $src[1] ), 0, -1 ), 1 );

			if ( isset( $width[1] ) ) {
				$width = substr( substr( str_replace( 'width=', '', $width[1] ), 0, -1 ), 1 );
			}

			if ( isset( $height[1] ) ) {
				$height = substr( substr( str_replace( 'height=', '', $height[1] ), 0, -1 ), 1 );
			}

			if ( empty( $width ) || empty( $height ) ) {
				$width  = 100;
				$height = 100;
			}

			$ratio      = (int) $width / (int) $height;
			$new_height = (int) $height >= 100 ? 100 : $height;
			$new_width  = $new_height * $ratio;
			$image      = '<img src="' . esc_url( $src ) . '" width="' . absint( $new_width ) . '" height="' . absint( $new_height ) . '" alt="' . __( 'Thumbnail', 'buddypress' ) . '" class="align-left thumbnail" />';

			if ( !empty( $link ) ) {
				$image = '<a href="' . esc_url( $link ) . '">' . $image . '</a>';
			}

			$content = $image . $content;
		}
	}

	/**
	 * Filters the activity content that had a thumbnail replace images.
	 *
	 * @since 1.2.0
	 *
	 * @param string $content Activity content that had images replaced in.
	 * @param array  $matches Array of all image tags found in the posted content.
	 * @param array  $args    Arguments passed into function creating the activity update.
	 */
	return apply_filters( 'bp_activity_thumbnail_content_images', $content, $matches, $args );
}

/**
 * Create a rich summary of an activity item for the activity stream.
 *
 * More than just a simple excerpt, the summary could contain oEmbeds and other types of media.
 * Currently, it's only used for blog post items, but it will probably be used for all types of
 * activity in the future.
 *
 * @since 2.3.0
 *
 * @param string $content  The content of the activity item.
 * @param array  $activity The data passed to bp_activity_add() or the values
 *                         from an Activity obj.
 * @return string $summary
 */
function bp_activity_create_summary( $content, $activity ) {
	$args = array(
		'width' => isset( $GLOBALS['content_width'] ) ? (int) $GLOBALS['content_width'] : 'medium',
	);

	// Get the WP_Post object if this activity type is a blog post.
	if ( $activity['type'] === 'new_blog_post' ) {
		$content = get_post( $activity['secondary_item_id'] );
	}

	/**
	 * Filter the class name of the media extractor when creating an Activity summary.
	 *
	 * Use this filter to change the media extractor used to extract media info for the activity item.
	 *
	 * @since 2.3.0
	 *
	 * @param string $extractor Class name.
	 * @param string $content   The content of the activity item.
	 * @param array  $activity  The data passed to bp_activity_add() or the values from an Activity obj.
	 */
	$extractor = apply_filters( 'bp_activity_create_summary_extractor_class', 'BP_Media_Extractor', $content, $activity );
	$extractor = new $extractor;

	/**
	 * Filter the arguments passed to the media extractor when creating an Activity summary.
	 *
	 * @since 2.3.0
	 *
	 * @param array              $args      Array of bespoke data for the media extractor.
	 * @param string             $content   The content of the activity item.
	 * @param array              $activity  The data passed to bp_activity_add() or the values from an Activity obj.
	 * @param BP_Media_Extractor $extractor The media extractor object.
	 */
	$args = apply_filters( 'bp_activity_create_summary_extractor_args', $args, $content, $activity, $extractor );


	// Extract media information from the $content.
	$media = $extractor->extract( $content, BP_Media_Extractor::ALL, $args );

	// If we converted $content to an object earlier, flip it back to a string.
	if ( is_a( $content, 'WP_Post' ) ) {
		$content = $content->post_content;
	}

	$para_count     = substr_count( strtolower( wpautop( $content ) ), '<p>' );
	$has_audio      = ! empty( $media['has']['audio'] )           && $media['has']['audio'];
	$has_videos     = ! empty( $media['has']['videos'] )          && $media['has']['videos'];
	$has_feat_image = ! empty( $media['has']['featured_images'] ) && $media['has']['featured_images'];
	$has_galleries  = ! empty( $media['has']['galleries'] )       && $media['has']['galleries'];
	$has_images     = ! empty( $media['has']['images'] )          && $media['has']['images'];
	$has_embeds     = false;

	// Embeds must be subtracted from the paragraph count.
	if ( ! empty( $media['has']['embeds'] ) ) {
		$has_embeds = $media['has']['embeds'] > 0;
		$para_count -= count( $media['has']['embeds'] );
	}

	$extracted_media = array();
	$use_media_type  = '';
	$image_source    = '';

	// If it's a short article and there's an embed/audio/video, use it.
	if ( $para_count <= 3 ) {
		if ( $has_embeds ) {
			$use_media_type = 'embeds';
		} elseif ( $has_audio ) {
			$use_media_type = 'audio';
		} elseif ( $has_videos ) {
			$use_media_type = 'videos';
		}
	}

	// If not, or in any other situation, try to use an image.
	if ( ! $use_media_type && $has_images ) {
		$use_media_type = 'images';
		$image_source   = 'html';

		// Featured Image > Galleries > inline <img>.
		if ( $has_feat_image ) {
			$image_source = 'featured_images';

		} elseif ( $has_galleries ) {
			$image_source = 'galleries';
		}
	}

	// Extract an item from the $media results.
	if ( $use_media_type ) {
		if ( $use_media_type === 'images' ) {
			$extracted_media = wp_list_filter( $media[ $use_media_type ], array( 'source' => $image_source ) );
			$extracted_media = array_shift( $extracted_media );
		} else {
			$extracted_media = array_shift( $media[ $use_media_type ] );
		}

		/**
		 * Filter the results of the media extractor when creating an Activity summary.
		 *
		 * @since 2.3.0
		 *
		 * @param array  $extracted_media Extracted media item. See {@link BP_Media_Extractor::extract()} for format.
		 * @param string $content         Content of the activity item.
		 * @param array  $activity        The data passed to bp_activity_add() or the values from an Activity obj.
		 * @param array  $media           All results from the media extraction.
		 *                                See {@link BP_Media_Extractor::extract()} for format.
		 * @param string $use_media_type  The kind of media item that was preferentially extracted.
		 * @param string $image_source    If $use_media_type was "images", the preferential source of the image.
		 *                                Otherwise empty.
		 */
		$extracted_media = apply_filters(
			'bp_activity_create_summary_extractor_result',
			$extracted_media,
			$content,
			$activity,
			$media,
			$use_media_type,
			$image_source
		);
	}

	// Generate a text excerpt for this activity item (and remove any oEmbeds URLs).
	$summary = strip_shortcodes( html_entity_decode( strip_tags( $content ) ) );
	$summary = bp_create_excerpt( preg_replace( '#^\s*(https?://[^\s"]+)\s*$#im', '', $summary ) );

	if ( $use_media_type === 'embeds' ) {
		$summary .= PHP_EOL . PHP_EOL . $extracted_media['url'];
	} elseif ( $use_media_type === 'images' ) {
		$summary .= sprintf( ' <img src="%s">', esc_url( $extracted_media['url'] ) );
	} elseif ( in_array( $use_media_type, array( 'audio', 'videos' ), true ) ) {
		$summary .= PHP_EOL . PHP_EOL . $extracted_media['original'];  // Full shortcode.
	}

	/**
	 * Filters the newly-generated summary for the activity item.
	 *
	 * @since 2.3.0
	 *
	 * @param string $summary         Activity summary HTML.
	 * @param string $content         Content of the activity item.
	 * @param array  $activity        The data passed to bp_activity_add() or the values from an Activity obj.
	 * @param array  $extracted_media Media item extracted. See {@link BP_Media_Extractor::extract()} for format.
	 */
	return apply_filters( 'bp_activity_create_summary', $summary, $content, $activity, $extracted_media );
}

/**
 * Fetch whether the current user is allowed to mark items as spam.
 *
 * @since 1.6.0
 *
 * @return bool True if user is allowed to mark activity items as spam.
 */
function bp_activity_user_can_mark_spam() {

	/**
	 * Filters whether the current user should be able to mark items as spam.
	 *
	 * @since 1.6.0
	 *
	 * @param bool $moderate Whether or not the current user has bp_moderate capability.
	 */
	return apply_filters( 'bp_activity_user_can_mark_spam', bp_current_user_can( 'bp_moderate' ) );
}

/**
 * Mark an activity item as spam.
 *
 * @since 1.6.0
 *
 * @param BP_Activity_Activity $activity The activity item to be spammed.
 * @param string               $source   Optional. Default is "by_a_person" (ie, a person has
 *                                       manually marked the activity as spam). BP core also
 *                                       accepts 'by_akismet'.
 */
function bp_activity_mark_as_spam( &$activity, $source = 'by_a_person' ) {
	$bp = buddypress();

	$activity->is_spam = 1;

	// Clear the activity stream first page cache.
	wp_cache_delete( 'bp_activity_sitewide_front', 'bp' );

	// Clear the activity comment cache for this activity item.
	wp_cache_delete( $activity->id, 'bp_activity_comments' );

	// If Akismet is active, and this was a manual spam/ham request, stop Akismet checking the activity.
	if ( 'by_a_person' == $source && !empty( $bp->activity->akismet ) ) {
		remove_action( 'bp_activity_before_save', array( $bp->activity->akismet, 'check_activity' ), 4, 1 );

		// Build data package for Akismet.
		$activity_data = BP_Akismet::build_akismet_data_package( $activity );

		// Tell Akismet this is spam.
		$activity_data = $bp->activity->akismet->send_akismet_request( $activity_data, 'submit', 'spam' );

		// Update meta.
		add_action( 'bp_activity_after_save', array( $bp->activity->akismet, 'update_activity_spam_meta' ), 1, 1 );
	}

	/**
	 * Fires at the end of the process to mark an activity item as spam.
	 *
	 * @since 1.6.0
	 *
	 * @param BP_Activity_Activity $activity Activity item being marked as spam.
	 * @param string               $source   Source of determination of spam status. For example
	 *                                       "by_a_person" or "by_akismet".
	 */
	do_action( 'bp_activity_mark_as_spam', $activity, $source );
}

/**
 * Mark an activity item as ham.
 *
 * @since 1.6.0
 *
 * @param BP_Activity_Activity $activity The activity item to be hammed. Passed by reference.
 * @param string               $source   Optional. Default is "by_a_person" (ie, a person has
 *                                       manually marked the activity as spam). BP core also accepts
 *                                       'by_akismet'.
 */
function bp_activity_mark_as_ham( &$activity, $source = 'by_a_person' ) {
	$bp = buddypress();

	$activity->is_spam = 0;

	// Clear the activity stream first page cache.
	wp_cache_delete( 'bp_activity_sitewide_front', 'bp' );

	// Clear the activity comment cache for this activity item.
	wp_cache_delete( $activity->id, 'bp_activity_comments' );

	// If Akismet is active, and this was a manual spam/ham request, stop Akismet checking the activity.
	if ( 'by_a_person' == $source && !empty( $bp->activity->akismet ) ) {
		remove_action( 'bp_activity_before_save', array( $bp->activity->akismet, 'check_activity' ), 4, 1 );

		// Build data package for Akismet.
		$activity_data = BP_Akismet::build_akismet_data_package( $activity );

		// Tell Akismet this is spam.
		$activity_data = $bp->activity->akismet->send_akismet_request( $activity_data, 'submit', 'ham' );

		// Update meta.
		add_action( 'bp_activity_after_save', array( $bp->activity->akismet, 'update_activity_ham_meta' ), 1, 1 );
	}

	/**
	 * Fires at the end of the process to mark an activity item as ham.
	 *
	 * @since 1.6.0
	 *
	 * @param BP_Activity_Activity $activity Activity item being marked as ham.
	 * @param string               $source   Source of determination of ham status. For example
	 *                                       "by_a_person" or "by_akismet".
	 */
	do_action( 'bp_activity_mark_as_ham', $activity, $source );
}


/** Embeds *******************************************************************/

/**
 * Set up activity oEmbed cache during the activity loop.
 *
 * During an activity loop, this function sets up the hooks necessary to grab
 * each item's embeds from the cache, or put them in the cache if they are
 * not there yet.
 *
 * This does not cover recursive activity comments, as they do not use a real loop.
 * For that, see {@link bp_activity_comment_embed()}.
 *
 * @since 1.5.0
 *
 * @see BP_Embed
 * @see bp_embed_activity_cache()
 * @see bp_embed_activity_save_cache()
 *
 * @uses add_filter() To attach 'bp_get_activity_id' to 'embed_post_id'.
 * @uses add_filter() To attach 'bp_embed_activity_cache' to 'bp_embed_get_cache'.
 * @uses add_action() To attach 'bp_embed_activity_save_cache' to 'bp_embed_update_cache'.
 */
function bp_activity_embed() {
	add_filter( 'embed_post_id',         'bp_get_activity_id'                  );
	add_filter( 'bp_embed_get_cache',    'bp_embed_activity_cache',      10, 3 );
	add_action( 'bp_embed_update_cache', 'bp_embed_activity_save_cache', 10, 3 );
}
add_action( 'activity_loop_start', 'bp_activity_embed' );

/**
 * Set up activity oEmbed cache while recursing through activity comments.
 *
 * While crawling through an activity comment tree
 * ({@link bp_activity_recurse_comments}), this function sets up the hooks
 * necessary to grab each comment's embeds from the cache, or put them in
 * the cache if they are not there yet.
 *
 * @since 1.5.0
 *
 * @see BP_Embed
 * @see bp_embed_activity_cache()
 * @see bp_embed_activity_save_cache()
 *
 * @uses add_filter() To attach 'bp_get_activity_comment_id' to 'embed_post_id'.
 * @uses add_filter() To attach 'bp_embed_activity_cache' to 'bp_embed_get_cache'.
 * @uses add_action() To attach 'bp_embed_activity_save_cache' to 'bp_embed_update_cache'.
 */
function bp_activity_comment_embed() {
	add_filter( 'embed_post_id',         'bp_get_activity_comment_id'          );
	add_filter( 'bp_embed_get_cache',    'bp_embed_activity_cache',      10, 3 );
	add_action( 'bp_embed_update_cache', 'bp_embed_activity_save_cache', 10, 3 );
}
add_action( 'bp_before_activity_comment', 'bp_activity_comment_embed' );

/**
 * When a user clicks on a "Read More" item, make sure embeds are correctly parsed and shown for the expanded content.
 *
 * @since 1.5.0
 *
 * @see BP_Embed
 * @uses add_filter() To attach create_function() to 'embed_post_id'.
 * @uses add_filter() To attach 'bp_embed_activity_cache' to 'bp_embed_get_cache'.
 * @uses add_action() To attach 'bp_embed_activity_save_cache' to 'bp_embed_update_cache'.
 *
 * @param object $activity The activity that is being expanded.
 */
function bp_dtheme_embed_read_more( $activity ) {
	buddypress()->activity->read_more_id = $activity->id;

	add_filter( 'embed_post_id',         create_function( '', 'return buddypress()->activity->read_more_id;' ) );
	add_filter( 'bp_embed_get_cache',    'bp_embed_activity_cache',      10, 3 );
	add_action( 'bp_embed_update_cache', 'bp_embed_activity_save_cache', 10, 3 );
}
add_action( 'bp_dtheme_get_single_activity_content',       'bp_dtheme_embed_read_more' );
add_action( 'bp_legacy_theme_get_single_activity_content', 'bp_dtheme_embed_read_more' );

/**
 * Clean up 'embed_post_id' filter after comment recursion.
 *
 * This filter must be removed so that the non-comment filters take over again
 * once the comments are done being processed.
 *
 * @since 1.5.0
 *
 * @see bp_activity_comment_embed()
 * @uses remove_filter() To remove 'bp_get_activity_comment_id' from 'embed_post_id'.
 */
function bp_activity_comment_embed_after_recurse() {
	remove_filter( 'embed_post_id', 'bp_get_activity_comment_id' );
}
add_action( 'bp_after_activity_comment', 'bp_activity_comment_embed_after_recurse' );

/**
 * Fetch an activity item's cached embeds.
 *
 * Used during {@link BP_Embed::parse_oembed()} via {@link bp_activity_embed()}.
 *
 * @since 1.5.0
 *
 * @see BP_Embed::parse_oembed()
 * @uses bp_activity_get_meta()
 *
 * @param string $cache    An empty string passed by BP_Embed::parse_oembed() for
 *                         functions like this one to filter.
 * @param int    $id       The ID of the activity item.
 * @param string $cachekey The cache key generated in BP_Embed::parse_oembed().
 * @return mixed The cached embeds for this activity item.
 */
function bp_embed_activity_cache( $cache, $id, $cachekey ) {
	return bp_activity_get_meta( $id, $cachekey );
}

/**
 * Set an activity item's embed cache.
 *
 * Used during {@link BP_Embed::parse_oembed()} via {@link bp_activity_embed()}.
 *
 * @since 1.5.0
 *
 * @see BP_Embed::parse_oembed()
 * @uses bp_activity_update_meta()
 *
 * @param string $cache    An empty string passed by BP_Embed::parse_oembed() for
 *                         functions like this one to filter.
 * @param string $cachekey The cache key generated in BP_Embed::parse_oembed().
 * @param int    $id       The ID of the activity item.
 */
function bp_embed_activity_save_cache( $cache, $cachekey, $id ) {
	bp_activity_update_meta( $id, $cachekey, $cache );
}

/**
 * Should we use Heartbeat to refresh activities?
 *
 * @since 2.0.0
 *
 * @uses bp_is_activity_heartbeat_active() to check if heartbeat setting is on.
 * @uses bp_is_activity_directory() to check if the current page is the activity
 *       directory.
 * @uses bp_is_group_activity() to check if on a single group, the current page
 *       is the group activities.
 *
 * @return bool True if activity heartbeat is enabled, otherwise false.
 */
function bp_activity_do_heartbeat() {
	$retval = false;

	if ( ! bp_is_activity_heartbeat_active() ) {
		return $retval;
	}

	if ( bp_is_activity_directory() || bp_is_group_activity() ) {
		$retval = true;
	}

	return $retval;
}
