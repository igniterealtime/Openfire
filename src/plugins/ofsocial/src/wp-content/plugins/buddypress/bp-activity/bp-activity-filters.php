<?php
/**
 * Filters related to the Activity component.
 *
 * @package BuddyPress
 * @subpackage ActivityFilters
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/** Filters *******************************************************************/

// Apply WordPress defined filters.
add_filter( 'bp_get_activity_action',                'bp_activity_filter_kses', 1 );
add_filter( 'bp_get_activity_content_body',          'bp_activity_filter_kses', 1 );
add_filter( 'bp_get_activity_content',               'bp_activity_filter_kses', 1 );
add_filter( 'bp_get_activity_parent_content',        'bp_activity_filter_kses', 1 );
add_filter( 'bp_get_activity_latest_update',         'bp_activity_filter_kses', 1 );
add_filter( 'bp_get_activity_latest_update_excerpt', 'bp_activity_filter_kses', 1 );
add_filter( 'bp_get_activity_feed_item_description', 'bp_activity_filter_kses', 1 );
add_filter( 'bp_activity_content_before_save',       'bp_activity_filter_kses', 1 );
add_filter( 'bp_activity_action_before_save',        'bp_activity_filter_kses', 1 );
add_filter( 'bp_activity_latest_update_content',     'bp_activity_filter_kses', 1 );

add_filter( 'bp_get_activity_action',                'force_balance_tags' );
add_filter( 'bp_get_activity_content_body',          'force_balance_tags' );
add_filter( 'bp_get_activity_content',               'force_balance_tags' );
add_filter( 'bp_get_activity_latest_update',         'force_balance_tags' );
add_filter( 'bp_get_activity_latest_update_excerpt', 'force_balance_tags' );
add_filter( 'bp_get_activity_feed_item_description', 'force_balance_tags' );
add_filter( 'bp_activity_content_before_save',       'force_balance_tags' );
add_filter( 'bp_activity_action_before_save',        'force_balance_tags' );

add_filter( 'bp_get_activity_action',                'wptexturize' );
add_filter( 'bp_get_activity_content_body',          'wptexturize' );
add_filter( 'bp_get_activity_content',               'wptexturize' );
add_filter( 'bp_get_activity_parent_content',        'wptexturize' );
add_filter( 'bp_get_activity_latest_update',         'wptexturize' );
add_filter( 'bp_get_activity_latest_update_excerpt', 'wptexturize' );

add_filter( 'bp_get_activity_action',                'convert_smilies' );
add_filter( 'bp_get_activity_content_body',          'convert_smilies' );
add_filter( 'bp_get_activity_content',               'convert_smilies' );
add_filter( 'bp_get_activity_parent_content',        'convert_smilies' );
add_filter( 'bp_get_activity_latest_update',         'convert_smilies' );
add_filter( 'bp_get_activity_latest_update_excerpt', 'convert_smilies' );

add_filter( 'bp_get_activity_action',                'convert_chars' );
add_filter( 'bp_get_activity_content_body',          'convert_chars' );
add_filter( 'bp_get_activity_content',               'convert_chars' );
add_filter( 'bp_get_activity_parent_content',        'convert_chars' );
add_filter( 'bp_get_activity_latest_update',         'convert_chars' );
add_filter( 'bp_get_activity_latest_update_excerpt', 'convert_chars' );

add_filter( 'bp_get_activity_action',                'wpautop' );
add_filter( 'bp_get_activity_content_body',          'wpautop' );
add_filter( 'bp_get_activity_content',               'wpautop' );
add_filter( 'bp_get_activity_feed_item_description', 'wpautop' );

add_filter( 'bp_get_activity_action',                'make_clickable', 9 );
add_filter( 'bp_get_activity_content_body',          'make_clickable', 9 );
add_filter( 'bp_get_activity_content',               'make_clickable', 9 );
add_filter( 'bp_get_activity_parent_content',        'make_clickable', 9 );
add_filter( 'bp_get_activity_latest_update',         'make_clickable', 9 );
add_filter( 'bp_get_activity_latest_update_excerpt', 'make_clickable', 9 );
add_filter( 'bp_get_activity_feed_item_description', 'make_clickable', 9 );

add_filter( 'bp_acomment_name',                      'stripslashes_deep', 5 );
add_filter( 'bp_get_activity_action',                'stripslashes_deep', 5 );
add_filter( 'bp_get_activity_content',               'stripslashes_deep', 5 );
add_filter( 'bp_get_activity_content_body',          'stripslashes_deep', 5 );
add_filter( 'bp_get_activity_parent_content',        'stripslashes_deep', 5 );
add_filter( 'bp_get_activity_latest_update',         'stripslashes_deep', 5 );
add_filter( 'bp_get_activity_latest_update_excerpt', 'stripslashes_deep', 5 );
add_filter( 'bp_get_activity_feed_item_description', 'stripslashes_deep', 5 );

add_filter( 'bp_activity_primary_link_before_save',  'esc_url_raw' );

// Apply BuddyPress-defined filters.
add_filter( 'bp_get_activity_content',               'bp_activity_make_nofollow_filter' );
add_filter( 'bp_get_activity_content_body',          'bp_activity_make_nofollow_filter' );
add_filter( 'bp_get_activity_parent_content',        'bp_activity_make_nofollow_filter' );
add_filter( 'bp_get_activity_latest_update',         'bp_activity_make_nofollow_filter' );
add_filter( 'bp_get_activity_latest_update_excerpt', 'bp_activity_make_nofollow_filter' );
add_filter( 'bp_get_activity_feed_item_description', 'bp_activity_make_nofollow_filter' );

add_filter( 'pre_comment_content',                   'bp_activity_at_name_filter' );
add_filter( 'group_forum_topic_text_before_save',    'bp_activity_at_name_filter' );
add_filter( 'group_forum_post_text_before_save',     'bp_activity_at_name_filter' );
add_filter( 'the_content',                           'bp_activity_at_name_filter' );

add_filter( 'bp_get_activity_parent_content',        'bp_create_excerpt' );

add_filter( 'bp_get_activity_content_body', 'bp_activity_truncate_entry', 5 );
add_filter( 'bp_get_activity_content',      'bp_activity_truncate_entry', 5 );

add_filter( 'bp_get_total_favorite_count_for_user', 'bp_core_number_format' );
add_filter( 'bp_get_total_mention_count_for_user',  'bp_core_number_format' );

/** Actions *******************************************************************/

// At-name filter.
add_action( 'bp_activity_before_save', 'bp_activity_at_name_filter_updates' );

// Activity stream moderation.
add_action( 'bp_activity_before_save', 'bp_activity_check_moderation_keys', 2, 1 );
add_action( 'bp_activity_before_save', 'bp_activity_check_blacklist_keys',  2, 1 );

/** Functions *****************************************************************/

/**
 * Types of activity stream items to moderate.
 *
 * @since 1.6.0
 *
 * @return array $types List of the activity types to moderate.
 */
function bp_activity_get_moderated_activity_types() {
	$types = array(
		'activity_comment',
		'activity_update'
	);

	/**
	 * Filters the default activity types that BuddyPress should moderate.
	 *
	 * @since 1.6.0
	 *
	 * @param array $types Default activity types to moderate.
	 */
	return apply_filters( 'bp_activity_check_activity_types', $types );
}

/**
 * Moderate the posted activity item, if it contains moderate keywords.
 *
 * @since 1.6.0
 *
 * @param BP_Activity_Activity $activity The activity object to check.
 */
function bp_activity_check_moderation_keys( $activity ) {

	// Only check specific types of activity updates.
	if ( !in_array( $activity->type, bp_activity_get_moderated_activity_types() ) )
		return;

	// Unset the activity component so activity stream update fails
	// @todo This is temporary until some kind of moderation is built.
	if ( !bp_core_check_for_moderation( $activity->user_id, '', $activity->content ) )
		$activity->component = false;
}

/**
 * Mark the posted activity as spam, if it contains blacklist keywords.
 *
 * @since 1.6.0
 *
 * @param BP_Activity_Activity $activity The activity object to check.
 */
function bp_activity_check_blacklist_keys( $activity ) {

	// Only check specific types of activity updates.
	if ( ! in_array( $activity->type, bp_activity_get_moderated_activity_types() ) )
		return;

	// Mark as spam.
	if ( ! bp_core_check_for_blacklist( $activity->user_id, '', $activity->content ) )
		bp_activity_mark_as_spam( $activity, 'by_blacklist' );
}

/**
 * Custom kses filtering for activity content.
 *
 * @since 1.1.0
 *
 * @uses apply_filters() To call the 'bp_activity_allowed_tags' hook.
 * @uses wp_kses()
 *
 * @param string $content The activity content.
 * @return string $content Filtered activity content.
 */
function bp_activity_filter_kses( $content ) {
	global $allowedtags;

	$activity_allowedtags = $allowedtags;
	$activity_allowedtags['a']['class']    = array();
	$activity_allowedtags['a']['id']       = array();
	$activity_allowedtags['a']['rel']      = array();
	$activity_allowedtags['a']['title']    = array();
	$activity_allowedtags['b']             = array();
	$activity_allowedtags['code']          = array();
	$activity_allowedtags['i']             = array();
	$activity_allowedtags['img']           = array();
	$activity_allowedtags['img']['src']    = array();
	$activity_allowedtags['img']['alt']    = array();
	$activity_allowedtags['img']['width']  = array();
	$activity_allowedtags['img']['height'] = array();
	$activity_allowedtags['img']['class']  = array();
	$activity_allowedtags['img']['id']     = array();
	$activity_allowedtags['img']['title']  = array();
	$activity_allowedtags['span']          = array();
	$activity_allowedtags['span']['class'] = array();


	/**
	 * Filters the allowed HTML tags for BuddyPress Activity content.
	 *
	 * @since 1.2.0
	 *
	 * @param array $value Array of allowed HTML tags and attributes.
	 */
	$activity_allowedtags = apply_filters( 'bp_activity_allowed_tags', $activity_allowedtags );
	return wp_kses( $content, $activity_allowedtags );
}

/**
 * Find and link @-mentioned users in the contents of a given item.
 *
 * @since 1.2.0
 *
 * @param string $content     The contents of a given item.
 * @param int    $activity_id The activity id. Deprecated.
 * @return string $content Content filtered for mentions.
 */
function bp_activity_at_name_filter( $content, $activity_id = 0 ) {

	// Are mentions disabled?
	if ( ! bp_activity_do_mentions() ) {
		return $content;
	}

	// Try to find mentions.
	$usernames = bp_activity_find_mentions( $content );

	// No mentions? Stop now!
	if ( empty( $usernames ) )
		return $content;

	// We don't want to link @mentions that are inside of links, so we
	// temporarily remove them.
	$replace_count = 0;
	$replacements = array();
	foreach ( $usernames as $username ) {
		// Prevent @ name linking inside <a> tags.
		preg_match_all( '/(<a.*?(?!<\/a>)@' . $username . '.*?<\/a>)/', $content, $content_matches );
		if ( ! empty( $content_matches[1] ) ) {
			foreach ( $content_matches[1] as $replacement ) {
				$replacements[ '#BPAN' . $replace_count ] = $replacement;
				$content = str_replace( $replacement, '#BPAN' . $replace_count, $content );
				$replace_count++;
			}
		}
	}

	// Linkify the mentions with the username.
	foreach ( (array) $usernames as $user_id => $username ) {
		$content = preg_replace( '/(@' . $username . '\b)/', "<a href='" . bp_core_get_user_domain( $user_id ) . "' rel='nofollow'>@$username</a>", $content );
	}

	// Put everything back.
	if ( ! empty( $replacements ) ) {
		foreach ( $replacements as $placeholder => $original ) {
			$content = str_replace( $placeholder, $original, $content );
		}
	}

	// Return the content.
	return $content;
}

/**
 * Catch mentions in an activity item before it is saved into the database.
 *
 * If mentions are found, replace @mention text with user links and add our
 * hook to send mention notifications after the activity item is saved.
 *
 * @since 1.5.0
 *
 * @uses bp_activity_find_mentions()
 *
 * @param BP_Activity_Activity $activity Activity Object.
 */
function bp_activity_at_name_filter_updates( $activity ) {
	// Are mentions disabled?
	if ( ! bp_activity_do_mentions() ) {
		return;
	}

	// If activity was marked as spam, stop the rest of this function.
	if ( ! empty( $activity->is_spam ) )
		return;

	// Try to find mentions.
	$usernames = bp_activity_find_mentions( $activity->content );

	// We have mentions!
	if ( ! empty( $usernames ) ) {
		// Replace @mention text with userlinks.
		foreach( (array) $usernames as $user_id => $username ) {
			$activity->content = preg_replace( '/(@' . $username . '\b)/', "<a href='" . bp_core_get_user_domain( $user_id ) . "' rel='nofollow'>@$username</a>", $activity->content );
		}

		// Add our hook to send @mention emails after the activity item is saved.
		add_action( 'bp_activity_after_save', 'bp_activity_at_name_send_emails' );

		// Temporary variable to avoid having to run bp_activity_find_mentions() again.
		buddypress()->activity->mentioned_users = $usernames;
	}
}

/**
 * Sends emails and BP notifications for users @-mentioned in an activity item.
 *
 * @since 1.7.0
 *
 * @uses bp_activity_at_message_notification()
 * @uses bp_activity_update_mention_count_for_user()
 *
 * @param BP_Activity_Activity $activity The BP_Activity_Activity object.
 */
function bp_activity_at_name_send_emails( $activity ) {
	// Are mentions disabled?
	if ( ! bp_activity_do_mentions() ) {
		return;
	}

	// If our temporary variable doesn't exist, stop now.
	if ( empty( buddypress()->activity->mentioned_users ) )
		return;

	// Grab our temporary variable from bp_activity_at_name_filter_updates().
	$usernames = buddypress()->activity->mentioned_users;

	// Get rid of temporary variable.
	unset( buddypress()->activity->mentioned_users );

	// Send @mentions and setup BP notifications.
	foreach( (array) $usernames as $user_id => $username ) {

		/**
		 * Filters BuddyPress' ability to send email notifications for @mentions.
		 *
		 * @since 1.6.0
		 *
		 * @param bool  $value     Whether or not BuddyPress should send a notification to the mentioned users.
		 * @param array $usernames Array of users potentially notified.
		 */
		if ( apply_filters( 'bp_activity_at_name_do_notifications', true, $usernames ) ) {
			bp_activity_at_message_notification( $activity->id, $user_id );
		}

		// Updates mention count for the user.
		bp_activity_update_mention_count_for_user( $user_id, $activity->id );
	}
}

/**
 * Catch links in activity text so rel=nofollow can be added.
 *
 * @since 1.2.0
 *
 * @param string $text Activity text.
 * @return string $text Text with rel=nofollow added to any links.
 */
function bp_activity_make_nofollow_filter( $text ) {
	return preg_replace_callback( '|<a (.+?)>|i', 'bp_activity_make_nofollow_filter_callback', $text );
}

	/**
	 * Add rel=nofollow to a link.
	 *
	 * @since 1.2.0
	 *
	 * @param array $matches Items matched by preg_replace_callback() in bp_activity_make_nofollow_filter().
	 * @return string $text Link with rel=nofollow added.
	 */
	function bp_activity_make_nofollow_filter_callback( $matches ) {
		$text = $matches[1];
		$text = str_replace( array( ' rel="nofollow"', " rel='nofollow'"), '', $text );
		return "<a $text rel=\"nofollow\">";
	}

/**
 * Truncate long activity entries when viewed in activity streams.
 *
 * This method can only be used inside the Activity loop.
 *
 * @since 1.5.0
 *
 * @uses bp_is_single_activity()
 * @uses apply_filters() To call the 'bp_activity_excerpt_append_text' hook.
 * @uses apply_filters() To call the 'bp_activity_excerpt_length' hook.
 * @uses bp_create_excerpt()
 * @uses bp_get_activity_id()
 * @uses bp_get_activity_thread_permalink()
 * @uses apply_filters() To call the 'bp_activity_truncate_entry' hook.
 *
 * @param string $text The original activity entry text.
 * @return string $excerpt The truncated text.
 */
function bp_activity_truncate_entry( $text ) {
	global $activities_template;

	/**
	 * Provides a filter that lets you choose whether to skip this filter on a per-activity basis.
	 *
	 * @since 2.3.0
	 *
	 * @param bool $value If true, text should be checked to see if it needs truncating.
	 */
	$maybe_truncate_text = apply_filters(
		'bp_activity_maybe_truncate_entry',
		isset( $activities_template->activity->type ) && ! in_array( $activities_template->activity->type, array( 'new_blog_post', ), true )
	);

	// The full text of the activity update should always show on the single activity screen.
	if ( ! $maybe_truncate_text || bp_is_single_activity() ) {
		return $text;
	}

	/**
	 * Filters the appended text for the activity excerpt.
	 *
	 * @since 1.5.0
	 *
	 * @param string $value Internationalized "Read more" text.
	 */
	$append_text    = apply_filters( 'bp_activity_excerpt_append_text', __( '[Read more]', 'buddypress' ) );

	/**
	 * Filters the excerpt length for the activity excerpt.
	 *
	 * @since 1.5.0
	 *
	 * @param int $value Number indicating how many words to trim the excerpt down to.
	 */
	$excerpt_length = apply_filters( 'bp_activity_excerpt_length', 358 );

	// Run the text through the excerpt function. If it's too short, the original text will be returned.
	$excerpt        = bp_create_excerpt( $text, $excerpt_length, array( 'ending' => __( '&hellip;', 'buddypress' ) ) );

	/*
	 * If the text returned by bp_create_excerpt() is different from the original text (ie it's
	 * been truncated), add the "Read More" link. Note that bp_create_excerpt() is stripping
	 * shortcodes, so we have strip them from the $text before the comparison.
	 */
	if ( $excerpt != strip_shortcodes( $text ) ) {
		$id = !empty( $activities_template->activity->current_comment->id ) ? 'acomment-read-more-' . $activities_template->activity->current_comment->id : 'activity-read-more-' . bp_get_activity_id();

		$excerpt = sprintf( '%1$s<span class="activity-read-more" id="%2$s"><a href="%3$s" rel="nofollow">%4$s</a></span>', $excerpt, $id, bp_get_activity_thread_permalink(), $append_text );
	}

	/**
	 * Filters the composite activity excerpt entry.
	 *
	 * @since 1.5.0
	 *
	 * @param string $excerpt     Excerpt text and markup to be displayed.
	 * @param string $text        The original activity entry text.
	 * @param string $append_text The final append text applied.
	 */
	return apply_filters( 'bp_activity_truncate_entry', $excerpt, $text, $append_text );
}

/**
 * Include extra JavaScript dependencies for activity component.
 *
 * @since 2.0.0
 *
 * @uses bp_activity_do_heartbeat() to check if heartbeat is required.
 *
 * @param array $js_handles The original dependencies.
 * @return array $js_handles The new dependencies.
 */
function bp_activity_get_js_dependencies( $js_handles = array() ) {
	if ( bp_activity_do_heartbeat() ) {
		$js_handles[] = 'heartbeat';
	}

	return $js_handles;
}
add_filter( 'bp_core_get_js_dependencies', 'bp_activity_get_js_dependencies', 10, 1 );

/**
 * Add a just-posted classes to the most recent activity item.
 *
 * We use these classes to avoid pagination issues when items are loaded
 * dynamically into the activity stream.
 *
 * @since 2.0.0
 *
 * @param string $classes Array of classes for most recent activity item.
 * @return string $classes
 */
function bp_activity_newest_class( $classes = '' ) {
	$bp = buddypress();

	if ( ! empty( $bp->activity->last_recorded ) && $bp->activity->last_recorded == bp_get_activity_date_recorded() ) {
		$classes .= ' new-update';
	}

	$classes .= ' just-posted';
	return $classes;
}

/**
 * Check if Activity Heartbeat feature i on to add a timestamp class.
 *
 * @since 2.0.0
 *
 * @param string $classes Array of classes for timestamp.
 * @return string $classes
 */
function bp_activity_timestamp_class( $classes = '' ) {

	if ( ! bp_activity_do_heartbeat() ) {
		return $classes;
	}

	$activity_date = bp_get_activity_date_recorded();

	if ( empty( $activity_date ) ) {
		return $classes;
	}

	$classes .= ' date-recorded-' . strtotime( $activity_date );

	return $classes;
}
add_filter( 'bp_get_activity_css_class', 'bp_activity_timestamp_class', 9, 1 );

/**
 * Use WordPress Heartbeat API to check for latest activity update.
 *
 * @since 2.0.0
 *
 * @uses bp_activity_get_last_updated() to get the recorded date of the last activity.
 *
 * @param array $response Array containing Heartbeat API response.
 * @param array $data     Array containing data for Heartbeat API response.
 * @return array $response
 */
function bp_activity_heartbeat_last_recorded( $response = array(), $data = array() ) {
	if ( empty( $data['bp_activity_last_recorded'] ) ) {
		return $response;
	}

	// Use the querystring argument stored in the cookie (to preserve
	// filters), but force the offset to get only new items.
	$activity_latest_args = bp_parse_args(
		bp_ajax_querystring( 'activity' ),
		array( 'since' => date( 'Y-m-d H:i:s', $data['bp_activity_last_recorded'] ) ),
		'activity_latest_args'
	);

	if ( ! empty( $data['bp_activity_last_recorded_search_terms'] ) && empty( $activity_latest_args['search_terms'] ) ) {
		$activity_latest_args['search_terms'] = addslashes( $data['bp_activity_last_recorded_search_terms'] );
	}

	$newest_activities = array();
	$last_activity_recorded = 0;

	// Temporarily add a just-posted class for new activity items.
	add_filter( 'bp_get_activity_css_class', 'bp_activity_newest_class', 10, 1 );

	ob_start();
	if ( bp_has_activities( $activity_latest_args ) ) {
		while ( bp_activities() ) {
			bp_the_activity();

			$atime = strtotime( bp_get_activity_date_recorded() );
			if ( $last_activity_recorded < $atime ) {
				$last_activity_recorded = $atime;
			}

			bp_get_template_part( 'activity/entry' );
		}
	}

	$newest_activities['activities']    = ob_get_contents();
	$newest_activities['last_recorded'] = $last_activity_recorded;
	ob_end_clean();

	// Remove the temporary filter.
	remove_filter( 'bp_get_activity_css_class', 'bp_activity_newest_class', 10, 1 );

	if ( ! empty( $newest_activities['last_recorded'] ) ) {
		$response['bp_activity_newest_activities'] = $newest_activities;
	}

	return $response;
}
add_filter( 'heartbeat_received', 'bp_activity_heartbeat_last_recorded', 10, 2 );
add_filter( 'heartbeat_nopriv_received', 'bp_activity_heartbeat_last_recorded', 10, 2 );

/**
 * Set the strings for WP HeartBeat API where needed.
 *
 * @since 2.0.0
 *
 * @param array $strings Localized strings.
 * @return array $strings
 */
function bp_activity_heartbeat_strings( $strings = array() ) {

	if ( ! bp_activity_do_heartbeat() ) {
		return $strings;
	}

	$global_pulse = 0;

	/**
	 * Filter that checks whether the global heartbeat settings already exist.
	 *
	 * @since 2.0.0
	 *
	 * @param array $value Heartbeat settings array.
	 */
	$heartbeat_settings = apply_filters( 'heartbeat_settings', array() );
	if ( ! empty( $heartbeat_settings['interval'] ) ) {
		// 'Fast' is 5
		$global_pulse = is_numeric( $heartbeat_settings['interval'] ) ? absint( $heartbeat_settings['interval'] ) : 5;
	}

	/**
	 * Filters the pulse frequency to be used for the BuddyPress Activity heartbeat.
	 *
	 * @since 2.0.0
	 *
	 * @param int $value The frequency in seconds between pulses.
	 */
	$bp_activity_pulse = apply_filters( 'bp_activity_heartbeat_pulse', 15 );

	/**
	 * Use the global pulse value unless:
	 * a. the BP-specific value has been specifically filtered, or
	 * b. it doesn't exist (ie, BP will be the only one using the heartbeat,
	 *    so we're responsible for enabling it)
	 */
	if ( has_filter( 'bp_activity_heartbeat_pulse' ) || empty( $global_pulse ) ) {
		$pulse = $bp_activity_pulse;
	} else {
		$pulse = $global_pulse;
	}

	$strings = array_merge( $strings, array(
		'newest' => __( 'Load Newest', 'buddypress' ),
		'pulse'  => absint( $pulse ),
	) );

	return $strings;
}
add_filter( 'bp_core_get_js_strings', 'bp_activity_heartbeat_strings', 10, 1 );

/** Scopes ********************************************************************/

/**
 * Set up activity arguments for use with the 'just-me' scope.
 *
 * @since 2.2.0
 *
 * @param array $retval Empty array by default.
 * @param array $filter Current activity arguments.
 * @return array $retval
 */
function bp_activity_filter_just_me_scope( $retval = array(), $filter = array() ) {

	// Determine the user_id.
	if ( ! empty( $filter['user_id'] ) ) {
		$user_id = $filter['user_id'];
	} else {
		$user_id = bp_displayed_user_id()
			? bp_displayed_user_id()
			: bp_loggedin_user_id();
	}

	// Should we show all items regardless of sitewide visibility?
	$show_hidden = array();
	if ( ! empty( $user_id ) && $user_id !== bp_loggedin_user_id() ) {
		$show_hidden = array(
			'column' => 'hide_sitewide',
			'value'  => 0
		);
	}

	$retval = array(
		'relation' => 'AND',
		array(
			'column' => 'user_id',
			'value'  => $user_id
		),
		$show_hidden,

		// Overrides.
		'override' => array(
			'display_comments' => 'stream',
			'filter'           => array( 'user_id' => 0 ),
			'show_hidden'      => true
		),
	);

	return $retval;
}
add_filter( 'bp_activity_set_just-me_scope_args', 'bp_activity_filter_just_me_scope', 10, 2 );

/**
 * Set up activity arguments for use with the 'favorites' scope.
 *
 * @since 2.2.0
 *
 * @param array $retval Empty array by default.
 * @param array $filter Current activity arguments.
 * @return array $retval
 */
function bp_activity_filter_favorites_scope( $retval = array(), $filter = array() ) {

	// Determine the user_id.
	if ( ! empty( $filter['user_id'] ) ) {
		$user_id = $filter['user_id'];
	} else {
		$user_id = bp_displayed_user_id()
			? bp_displayed_user_id()
			: bp_loggedin_user_id();
	}

	// Determine the favorites.
	$favs = bp_activity_get_user_favorites( $user_id );
	if ( empty( $favs ) ) {
		$favs = array( 0 );
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
			'column'  => 'id',
			'compare' => 'IN',
			'value'   => (array) $favs
		),
		$show_hidden,

		// Overrides.
		'override' => array(
			'display_comments' => true,
			'filter'           => array( 'user_id' => 0 ),
			'show_hidden'      => true
		),
	);

	return $retval;
}
add_filter( 'bp_activity_set_favorites_scope_args', 'bp_activity_filter_favorites_scope', 10, 2 );


/**
 * Set up activity arguments for use with the 'favorites' scope.
 *
 * @since 2.2.0
 *
 * @param array $retval Empty array by default.
 * @param array $filter Current activity arguments.
 * @return array $retval
 */
function bp_activity_filter_mentions_scope( $retval = array(), $filter = array() ) {

	// Are mentions disabled?
	if ( ! bp_activity_do_mentions() ) {
		return $retval;
	}

	// Determine the user_id.
	if ( ! empty( $filter['user_id'] ) ) {
		$user_id = $filter['user_id'];
	} else {
		$user_id = bp_displayed_user_id()
			? bp_displayed_user_id()
			: bp_loggedin_user_id();
	}

	// Should we show all items regardless of sitewide visibility?
	$show_hidden = array();
	if ( ! empty( $user_id ) && $user_id !== bp_loggedin_user_id() ) {
		$show_hidden = array(
			'column' => 'hide_sitewide',
			'value'  => 0
		);
	}

	$retval = array(
		'relation' => 'AND',
		array(
			'column'  => 'content',
			'compare' => 'LIKE',

			// Start search at @ symbol and stop search at closing tag delimiter.
			'value'   => '@' . bp_activity_get_user_mentionname( $user_id ) . '<'
		),
		$show_hidden,

		// Overrides.
		'override' => array(

			// Clear search terms so 'mentions' scope works with other scopes.
			'search_terms' => false,

			'display_comments' => 'stream',
			'filter'           => array( 'user_id' => 0 ),
			'show_hidden'      => true
		),
	);

	return $retval;
}
add_filter( 'bp_activity_set_mentions_scope_args', 'bp_activity_filter_mentions_scope', 10, 2 );
