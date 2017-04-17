<?php
/**
 * Sets up the default filters and actions for most
 * of the bbPress hooks.
 *
 * If you need to remove a default hook, this file will
 * give you the priority for which to use to remove the
 * hook.
 *
 * Not all of the default hooks are found in this files
 *
 * @package bbPress
 */

// Strip, trim, kses, special chars for string saves
$filters = array( 'pre_term_name', 'bb_pre_forum_name', 'pre_topic_title' );
foreach ( $filters as $filter ) {
	add_filter( $filter, 'strip_tags' );
	add_filter( $filter, 'trim' );
	add_filter( $filter, 'bb_filter_kses' );
	add_filter( $filter, 'esc_html', 30 );
}

// Kses only for textarea saves
$filters = array( 'pre_term_description', 'bb_pre_forum_desc' );
foreach ( $filters as $filter ) {
	add_filter( $filter, 'bb_filter_kses' );
}

// Slugs
add_filter( 'pre_term_slug', 'bb_pre_term_slug' );
add_filter( 'editable_slug', 'urldecode');

// DB truncations
add_filter( 'pre_topic_title', 'bb_trim_for_db_150', 9999 );
add_filter( 'bb_pre_forum_name', 'bb_trim_for_db_150', 9999 );
add_filter( 'pre_term_name', 'bb_trim_for_db_55', 9999 );

// Format Strings for Display
$filters = array( 'forum_name', 'topic_title', 'bb_title', 'bb_option_name' );
foreach ( $filters as $filter ) {
	add_filter( $filter, 'esc_html' );
}

// Numbers
$filters = array( 'forum_topics', 'forum_posts', 'total_posts', 'total_users', 'total_topics' );
foreach ( $filters as $filter ) {
	add_filter( $filter, 'bb_number_format_i18n' );
}

// Offset Times
$filters = array( 'topic_time', 'topic_start_time', 'bb_post_time' );
foreach ( $filters as $filter ) {
	add_filter( $filter, 'bb_offset_time', 10, 2 );
}

add_filter('bb_topic_labels', 'bb_closed_label', 10);
add_filter('bb_topic_labels', 'bb_sticky_label', 20);

add_filter('pre_post', 'trim');
add_filter('pre_post', 'bb_encode_bad');
add_filter('pre_post', 'bb_code_trick');
add_filter('pre_post', 'force_balance_tags');
add_filter('pre_post', 'bb_filter_kses', 50);
add_filter('pre_post', 'bb_autop', 60);

add_filter('post_text', 'do_shortcode');

function bb_contextualise_search_post_text()
{
	if ( bb_is_search() ) {
		add_filter( 'get_post_text', 'bb_post_text_context' );
	}
}
add_action( 'bb_init', 'bb_contextualise_search_post_text' );

add_filter('post_text', 'make_clickable');

add_filter('edit_text', 'bb_code_trick_reverse');
add_filter('edit_text', 'wp_specialchars');
add_filter('edit_text', 'trim', 15);

add_filter('pre_sanitize_with_dashes', 'bb_pre_sanitize_with_dashes_utf8', 10, 3 );

add_filter('get_user_link', 'bb_fix_link');

add_filter('sanitize_profile_info', 'esc_html');
add_filter('sanitize_profile_admin', 'esc_html');

add_filter( 'get_recent_user_replies_fields', 'bb_get_recent_user_replies_fields' );
add_filter( 'get_recent_user_replies_group_by', 'bb_get_recent_user_replies_group_by' );

add_filter('sort_tag_heat_map', 'bb_sort_tag_heat_map');

// URLS

if ( !bb_get_option( 'mod_rewrite' ) ) {
	add_filter( 'bb_stylesheet_uri', 'esc_attr', 1, 9999 );
	add_filter( 'forum_link', 'esc_attr', 1, 9999 );
	add_filter( 'bb_forum_posts_rss_link', 'esc_attr', 1, 9999 );
	add_filter( 'bb_forum_topics_rss_link', 'esc_attr', 1, 9999 );
	add_filter( 'bb_tag_link', 'esc_attr', 1, 9999 );
	add_filter( 'tag_rss_link', 'esc_attr', 1, 9999 );
	add_filter( 'topic_link', 'esc_attr', 1, 9999 );
	add_filter( 'topic_rss_link', 'esc_attr', 1, 9999 );
	add_filter( 'post_link', 'esc_attr', 1, 9999 );
	add_filter( 'post_anchor_link', 'esc_attr', 1, 9999 );
	add_filter( 'user_profile_link', 'esc_attr', 1, 9999 );
	add_filter( 'profile_tab_link', 'esc_attr', 1, 9999 );
	add_filter( 'favorites_link', 'esc_attr', 1, 9999 );
	add_filter( 'view_link', 'esc_attr', 1, 9999 );
}

// Feed Stuff

function bb_filter_feed_content()
{
	if ( bb_is_feed() ) {
		add_filter( 'bb_title_rss', 'strip_tags' );
		add_filter( 'bb_title_rss', 'ent2ncr', 8 );
		add_filter( 'bb_title_rss', 'esc_html' );

		add_filter( 'bb_description_rss', 'strip_tags' );
		add_filter( 'bb_description_rss', 'ent2ncr', 8 );
		add_filter( 'bb_description_rss', 'esc_html' );

		add_filter( 'post_author', 'ent2ncr', 8 );
		add_filter( 'post_link', 'esc_html' );
		add_filter( 'post_text', 'ent2ncr', 8 );
		add_filter( 'post_text', 'bb_convert_chars' );
	}
}
add_action( 'bb_init', 'bb_filter_feed_content' );

add_action( 'init_roles', 'bb_init_roles' );
add_filter( 'map_meta_cap', 'bb_map_meta_cap', 1, 4 );

// Actions

add_action( 'bb_head', 'bb_generator' );
add_action('bb_head', 'bb_template_scripts');
add_action('bb_head', 'wp_print_scripts');
add_action('bb_head', 'wp_print_styles');
add_action('bb_head', 'bb_rsd_link');
add_action('bb_head', 'bb_pingback_link');
if ( $bb_log->type === 'console' ) {
	add_action('bb_head', array(&$bb_log, 'console_javascript'));
	add_action('bb_admin_head', array(&$bb_log, 'console_javascript'));
}
add_action('bb_send_headers', 'bb_pingback_header');
add_action('bb_admin_print_scripts', 'wp_print_scripts');

add_action('bb_user_has_no_caps', 'bb_give_user_default_role');

add_action('do_pingbacks', array('BB_Pingbacks', 'send_all'), 10, 1);

add_action( 'bb_init', 'bb_register_default_views' );

add_action( 'set_current_user', 'bb_apply_wp_role_map_to_user' );

add_filter( 'bb_pre_get_option_gmt_offset', 'wp_timezone_override_offset' );

// Subscriptions

if ( bb_is_subscriptions_active() ) {
	add_action( 'bb_new_post', 'bb_notify_subscribers' );
	add_action( 'bb_insert_post', 'bb_user_subscribe_checkbox_update' );
	add_action( 'topicmeta', 'bb_user_subscribe_link' );
	add_action( 'edit_form', 'bb_user_subscribe_checkbox' ); 
	add_action( 'post_form', 'bb_user_subscribe_checkbox' );
}

add_action( 'bb_post-form.php', 'bb_anonymous_post_form' );

unset( $filters, $filter );
