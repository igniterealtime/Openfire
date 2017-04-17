<?php
/**
 * BuddyPress Forums Filters.
 *
 * @package BuddyPress
 * @subpackage ForumsFilters
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/* Apply WordPress defined filters */
add_filter( 'bp_forums_bbconfig_location', 'wp_filter_kses', 1 );
add_filter( 'bp_forums_bbconfig_location', 'esc_attr', 1 );

add_filter( 'bp_get_the_topic_title', 'wp_filter_kses', 1 );
add_filter( 'bp_get_the_topic_latest_post_excerpt', 'bp_forums_filter_kses', 1 );
add_filter( 'bp_get_the_topic_post_content', 'bp_forums_filter_kses', 1 );

add_filter( 'bp_get_the_topic_title', 'force_balance_tags' );
add_filter( 'bp_get_the_topic_latest_post_excerpt', 'force_balance_tags' );
add_filter( 'bp_get_the_topic_post_content', 'force_balance_tags' );

add_filter( 'bp_get_the_topic_title', 'wptexturize' );
add_filter( 'bp_get_the_topic_poster_name', 'wptexturize' );
add_filter( 'bp_get_the_topic_last_poster_name', 'wptexturize' );
add_filter( 'bp_get_the_topic_post_content', 'wptexturize' );
add_filter( 'bp_get_the_topic_post_poster_name', 'wptexturize' );

add_filter( 'bp_get_the_topic_title', 'convert_smilies' );
add_filter( 'bp_get_the_topic_latest_post_excerpt', 'convert_smilies' );
add_filter( 'bp_get_the_topic_post_content', 'convert_smilies' );

add_filter( 'bp_get_the_topic_title', 'convert_chars' );
add_filter( 'bp_get_the_topic_latest_post_excerpt', 'convert_chars' );
add_filter( 'bp_get_the_topic_post_content', 'convert_chars' );

add_filter( 'bp_get_the_topic_post_content', 'wpautop' );
add_filter( 'bp_get_the_topic_latest_post_excerpt', 'wpautop' );

add_filter( 'bp_get_the_topic_post_content', 'stripslashes_deep' );
add_filter( 'bp_get_the_topic_title', 'stripslashes_deep' );
add_filter( 'bp_get_the_topic_latest_post_excerpt', 'stripslashes_deep' );
add_filter( 'bp_get_the_topic_poster_name', 'stripslashes_deep' );
add_filter( 'bp_get_the_topic_last_poster_name', 'stripslashes_deep' );
add_filter( 'bp_get_the_topic_object_name', 'stripslashes_deep' );

add_filter( 'bp_get_the_topic_post_content', 'make_clickable', 9 );

add_filter( 'bp_get_forum_topic_count_for_user', 'bp_core_number_format' );
add_filter( 'bp_get_forum_topic_count', 'bp_core_number_format' );

add_filter( 'bp_get_the_topic_title', 'bp_forums_make_nofollow_filter' );
add_filter( 'bp_get_the_topic_latest_post_excerpt', 'bp_forums_make_nofollow_filter' );
add_filter( 'bp_get_the_topic_post_content', 'bp_forums_make_nofollow_filter' );

/**
 * Custom KSES filter for the Forums component.
 *
 * @param string $content Content to sanitize.
 * @return string Sanitized string.
 */
function bp_forums_filter_kses( $content ) {
	global $allowedtags;

	$forums_allowedtags = $allowedtags;
	$forums_allowedtags['span'] = array();
	$forums_allowedtags['span']['class'] = array();
	$forums_allowedtags['div'] = array();
	$forums_allowedtags['div']['class'] = array();
	$forums_allowedtags['div']['id'] = array();
	$forums_allowedtags['a']['class'] = array();
	$forums_allowedtags['img'] = array();
	$forums_allowedtags['br'] = array();
	$forums_allowedtags['p'] = array();
	$forums_allowedtags['img']['src'] = array();
	$forums_allowedtags['img']['alt'] = array();
	$forums_allowedtags['img']['class'] = array();
	$forums_allowedtags['img']['width'] = array();
	$forums_allowedtags['img']['height'] = array();
	$forums_allowedtags['img']['class'] = array();
	$forums_allowedtags['img']['id'] = array();
	$forums_allowedtags['code'] = array();
	$forums_allowedtags['blockquote'] = array();

	/**
	 * Filters the allowed HTML tags for forum posts.
	 *
	 * @since 1.2.0
	 *
	 * @param array $forums_allowedtags Array of allowed HTML tags.
	 */
	$forums_allowedtags = apply_filters( 'bp_forums_allowed_tags', $forums_allowedtags );
	return wp_kses( $content, $forums_allowedtags );
}

/**
 * Get a link for a forum topic tags directory.
 *
 * @param string $link    Link passed from filter.
 * @param string $tag     Name of the tag.
 * @param string $page    Page number, passed from the filter.
 * @param string $context Passed from the filter but unused here.
 * @return string Link of the form http://example.com/forums/tag/tagname/.
 */
function bp_forums_filter_tag_link( $link, $tag, $page, $context ) {
	/**
	 * Filters the link for a forum topic tags directory.
	 *
	 * @since 1.1.0
	 *
	 * @param string $value Link for the forum topic tag directory.
	 */
	return apply_filters( 'bp_forums_filter_tag_link', bp_get_root_domain() . '/' . bp_get_forums_root_slug() . '/tag/' . $tag . '/' );
}
add_filter( 'bb_get_tag_link', 'bp_forums_filter_tag_link', 10, 4);

/**
 * Add rel="nofollow" to bbPress content.
 *
 * @param string $text Post content.
 * @return string Modified post content.
 */
function bp_forums_make_nofollow_filter( $text ) {
	return preg_replace_callback( '|<a (.+?)>|i', 'bp_forums_make_nofollow_filter_callback', $text );
}
	/**
	 * Callback for preg_replace_callback() in bp_forums_make_nofollow_filter().
	 *
	 * @param array $matches Regex matches from {@link bp_forums_make_nofollow_filter()}.
	 * @return string Text with nofollow links.
	 */
	function bp_forums_make_nofollow_filter_callback( $matches ) {
		$text = $matches[1];
		$text = str_replace( array( ' rel="nofollow"', " rel='nofollow'"), '', $text );
		return "<a $text rel=\"nofollow\">";
	}

/**
 * Append forum topic to page title.
 *
 * @see bp_modify_page_title()
 *
 * @param string $title          New page title; see {@link bp_modify_page_title()}.
 * @param string $original_title Original page title.
 * @param string $sep            How to separate the various items within the page title.
 * @param string $seplocation    Direction to display title.
 * @return string Page title with forum topic title appended.
 */
function bp_forums_add_forum_topic_to_page_title( $title, $original_title, $sep, $seplocation  ) {

	if ( bp_is_current_action( 'forum' ) && bp_is_action_variable( 'topic', 0 ) )
		if ( bp_has_forum_topic_posts() )
			$title .= bp_get_the_topic_title() . " $sep ";

	return $title;
}
add_filter( 'bp_modify_page_title', 'bp_forums_add_forum_topic_to_page_title', 9, 4 );

/**
 * Remove the anchor tag autogenerated for at-mentions when forum topics and posts are edited.
 *
 * Prevents embedded anchor tags.
 *
 * @param string $content Edited post content.
 * @return string $content Sanitized post content.
 */
function bp_forums_strip_mentions_on_post_edit( $content ) {
	$content   = htmlspecialchars_decode( $content );
	$directory = bp_get_members_directory_permalink();
	$pattern   = "|<a href=&#039;{$directory}[A-Za-z0-9-_\.]+/&#039; rel=&#039;nofollow&#039;>(@[A-Za-z0-9-_\.@]+)</a>|";
	$content   = preg_replace( $pattern, "$1", $content );

	return $content;
}
add_filter( 'bp_get_the_topic_post_edit_text', 'bp_forums_strip_mentions_on_post_edit' );
add_filter( 'bp_get_the_topic_text',           'bp_forums_strip_mentions_on_post_edit' );

/** "Replied to" SQL filters *************************************************/

/**
 * Filter the get_topics_distinct portion of the Forums SQL when on a user's Replied To page.
 *
 * This filter is added in bp_has_forum_topics().
 *
 * @since 1.5.0
 *
 * @param string $sql SQL fragment.
 * @return string $sql SQL fragment of the form "DISTINCT t.topic_id, ".
 */
function bp_forums_add_replied_distinct_sql( $sql ) {
	$sql = "DISTINCT t.topic_id, ";

	return $sql;
}

/**
 * Filter the get_topics_join portion of the Forums sql when on a user's Replied To page.
 *
 * This filter is added in bp_has_forum_topics().
 *
 * @since 1.5.0
 *
 * @global object $bbdb The bbPress database global.
 *
 * @param string $sql SQL statement.
 * @return string $sql SQL statement.
 */
function bp_forums_add_replied_join_sql( $sql ) {
	global $bbdb;

	$sql .= " LEFT JOIN {$bbdb->posts} p ON p.topic_id = t.topic_id ";

	return $sql;
}

/**
 * Filter the get_topics_where portion of the Forums sql when on a user's Replied To page.
 *
 * This filter is added in bp_has_forum_topics().
 *
 * @since 1.5.0
 *
 * @global object $wpdb The WordPress database global.
 *
 * @param string $sql SQL fragment.
 * @return string $sql SQL fragment.
 */
function bp_forums_add_replied_where_sql( $sql ) {
	global $wpdb;

	$sql .= $wpdb->prepare( " AND p.poster_id = %s ", bp_displayed_user_id() );

	// Remove any topic_author information.
	$sql = str_replace( " AND t.topic_poster = '" . bp_displayed_user_id() . "'", '', $sql );

	return $sql;
}
