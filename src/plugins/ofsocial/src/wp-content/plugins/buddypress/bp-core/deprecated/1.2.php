<?php
/**
 * Deprecated Functions
 *
 * @package BuddyPress
 * @subpackage Core
 */

/**
 * Retrieve sitewide activity
 *
 * You should use bp_activity_get() instead
 *
 * @since 1.0.0
 * @deprecated 1.2.0
 *
 * @param array $args
 *
 * @uses BP_Activity_Activity::get() {@link BP_Activity_Activity}
 *
 * @return object $activity The activity/activities object
 */
function bp_activity_get_sitewide( $args = '' ) {
	_deprecated_function( __FUNCTION__, '1.2', 'bp_activity_get()' );

	$defaults = array(
		'max' => false, // Maximum number of results to return
		'page' => 1, // page 1 without a per_page will result in no pagination.
		'per_page' => false, // results per page
		'sort' => 'DESC', // sort ASC or DESC
		'display_comments' => false, // false for no comments. 'stream' for within stream display, 'threaded' for below each activity item

		'search_terms' => false, // Pass search terms as a string
		'show_hidden' => false, // Show activity items that are hidden site-wide?

		/**
		 * Pass filters as an array:
		 * array(
		 * 	'user_id' => false, // user_id to filter on
		 *	'object' => false, // object to filter on e.g. groups, profile, status, friends
		 *	'action' => false, // action to filter on e.g. new_wire_post, new_forum_post, profile_updated
		 *	'primary_id' => false, // object ID to filter on e.g. a group_id or forum_id or blog_id etc.
		 *	'secondary_id' => false, // secondary object ID to filter on e.g. a post_id
		 * );
		 */
		'filter' => array()
	);

	$args = wp_parse_args( $args, $defaults );

	return apply_filters( 'bp_activity_get_sitewide', BP_Activity_Activity::get( $args ), $r );
}


