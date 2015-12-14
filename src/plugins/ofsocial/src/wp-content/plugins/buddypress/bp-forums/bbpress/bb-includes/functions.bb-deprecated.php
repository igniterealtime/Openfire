<?php
/**
 * Deprecated functions from past bbPress versions. You shouldn't use these
 * globals and functions and look for the alternatives instead. The functions
 * and globals will be removed in a later version.
 *
 * @package bbPress
 * @subpackage Deprecated
 */



function bb_specialchars( $text, $quotes = 0 ) {
	bb_log_deprecated('function', __FUNCTION__, 'esc_html');
	return esc_html( $text, $quotes );
}

function bb_make_clickable( $ret ) {
	bb_log_deprecated('function', __FUNCTION__, 'make_clickable');
	return make_clickable( $ret );
}

function bb_apply_filters($tag, $string, $filter = true) {
	bb_log_deprecated('function', __FUNCTION__, 'apply_filters');
	$args = func_get_args();
	return call_user_func_array('apply_filters', $args);
}

function bb_add_filter($tag, $function_to_add, $priority = 10) {
	bb_log_deprecated('function', __FUNCTION__, 'add_filter');
	$args = func_get_args();
	return call_user_func_array('add_filter', $args);
}

function bb_remove_filter($tag, $function_to_remove, $priority = 10) {
	bb_log_deprecated('function', __FUNCTION__, 'remove_filter');
	$args = func_get_args();
	return call_user_func_array('remove_filter', $args);
}

function bb_do_action($tag) {
	bb_log_deprecated('function', __FUNCTION__, 'do_action');
	$args = func_get_args();
	return call_user_func_array('do_action', $args);
}

function bb_add_action($tag, $function_to_add, $priority = 10) {
	bb_log_deprecated('function', __FUNCTION__, 'add_action');
	$args = func_get_args();
	return call_user_func_array('add_action', $args);
}

function bb_remove_action($tag, $function_to_remove, $priority = 10) {
	bb_log_deprecated('function', __FUNCTION__, 'remove_action');
	$args = func_get_args();
	return call_user_func_array('remove_action', $args);
}

function bb_add_query_arg() {
	bb_log_deprecated('function', __FUNCTION__, 'add_query_arg');
	$args = func_get_args();
	return call_user_func_array('add_query_arg', $args);
}

function bb_remove_query_arg($key, $query = '') {
	bb_log_deprecated('function', __FUNCTION__, 'remove_query_arg');
	return remove_query_arg($key, $query);
}

if ( !function_exists('language_attributes') ) :
function language_attributes( $xhtml = 0 ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_language_attributes');
	bb_language_attributes( $xhtml );
}
endif;

function cast_meta_value( $data ) {
	bb_log_deprecated('function', __FUNCTION__, 'maybe_unserialize');
	return maybe_unserialize( $data );
}

function option( $option ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_option');
	return bb_option( $option );
}

// Use topic_time
function topic_date( $format = '', $id = 0 ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_gmdate_i18n( $format, get_topic_timestamp( $id ) )');
	echo bb_gmdate_i18n( $format, get_topic_timestamp( $id ) );
}
function get_topic_date( $format = '', $id = 0 ){
	bb_log_deprecated('function', __FUNCTION__, 'bb_gmdate_i18n( $format, get_topic_timestamp( $id ) )');
	return bb_gmdate_i18n( $format, get_topic_timestamp( $id ) );
}
function get_topic_timestamp( $id = 0 ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_gmtstrtotime( $topic->topic_time )');
	global $topic;
	if ( $id )
		$topic = get_topic( $id );
	return bb_gmtstrtotime( $topic->topic_time );
}

// Use topic_start_time
function topic_start_date( $format = '', $id = 0 ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_gmdate_i18n( $format, get_topic_start_timestamp( $id ) )');
	echo bb_gmdate_i18n( $format, get_topic_start_timestamp( $id ) );
}
function get_topic_start_timestamp( $id = 0 ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_gmtstrtotime( $topic->topic_start_time )');
	global $topic;
	if ( $id )
		$topic = get_topic( $id );
	return bb_gmtstrtotime( $topic->topic_start_time );
}

// Use bb_post_time
function post_date( $format ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_gmdate_i18n( $format, bb_gmtstrtotime( $bb_post->post_time ) )');
	global $bb_post;
	echo bb_gmdate_i18n( $format, bb_gmtstrtotime( $bb_post->post_time ) );
}
function get_post_timestamp() {
	bb_log_deprecated('function', __FUNCTION__, 'bb_gmtstrtotime( $bb_post->post_time )');
	global $bb_post;
	return bb_gmtstrtotime( $bb_post->post_time );
}

function get_inception() {
	bb_log_deprecated('function', __FUNCTION__, 'bb_get_inception( \'timestamp\' )');
	return bb_get_inception( 'timestamp' );
}

function forum_dropdown( $c = false, $a = false ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_forum_dropdown');
	bb_forum_dropdown( $c, $a );
}

function get_ids_by_role( $role = 'moderator', $sort = 0, $limit_str = '' ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_get_ids_by_role');
	return bb_get_ids_by_role( $role , $sort , $limit_str);
}

function get_deleted_posts( $page = 1, $limit = false, $status = 1, $topic_status = 0 ) {
	bb_log_deprecated('function', __FUNCTION__, 'no alternative');
}

function bozo_posts( $where ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_bozo_posts');
	return bb_bozo_posts( $where );
}

function bozo_topics( $where ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_bozo_topics');
	return bb_bozo_topics( $where );
}

function get_bozos( $page = 1 ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_get_bozos');
	return bb_get_bozos($page);
}

function current_user_is_bozo( $topic_id = false ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_current_user_is_bozo');
	return bb_current_user_is_bozo( $topic_id );
}

function bozo_pre_permalink() {
	bb_log_deprecated('function', __FUNCTION__, 'bb_bozo_pre_permalink');
	return bb_bozo_pre_permalink();
}

function bozo_latest_filter() {
	bb_log_deprecated('function', __FUNCTION__, 'bb_bozo_latest_filter');
	return bb_bozo_latest_filter();
}

function bozo_topic_db_filter() {
	bb_log_deprecated('function', __FUNCTION__, 'bb_bozo_topic_db_filter');
	return bb_bozo_topic_db_filter();
}

function bozo_profile_db_filter() {
	bb_log_deprecated('function', __FUNCTION__, 'bb_bozo_profile_db_filter');
	return bb_bozo_profile_db_filter();
}

function bozo_recount_topics() {
	bb_log_deprecated('function', __FUNCTION__, 'bb_bozo_recount_topics');
	return bb_bozo_recount_topics();
}

function bozo_recount_users() {
	bb_log_deprecated('function', __FUNCTION__, 'bb_bozo_recount_users');
	return bb_bozo_recount_users();
}

function bozo_post_del_class( $status ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_bozo_post_del_class');
	return bb_bozo_post_del_class( $status );
}

function bozo_add_recount_list() {
	bb_log_deprecated('function', __FUNCTION__, 'bb_bozo_add_recount_list');
	return bb_bozo_add_recount_list() ;
}

function bozo_topic_pages_add( $add ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_bozo_topic_pages_add');
	return bb_bozo_topic_pages_add( $add );
}

function bozo_get_topic_posts( $topic_posts ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_bozo_get_topic_posts');
	return bb_bozo_get_topic_posts( $topic_posts ) ;
}

function bozo_new_post( $post_id ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_bozo_new_post');
	return bb_bozo_new_post( $post_id );
}

function bozo_pre_post_status( $status, $post_id, $topic_id ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_bozo_pre_post_status');
	return bb_bozo_pre_post_status( $status, $post_id, $topic_id ) ;
}

function bozo_delete_post( $post_id, $new_status, $old_status ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_bozo_delete_post');
	return bb_bozo_delete_post( $post_id, $new_status, $old_status ) ;
}

function bozon( $user_id, $topic_id = 0 ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_bozon');
	return bb_bozon( $user_id, $topic_id ) ;
}

function fermion( $user_id, $topic_id = 0 ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_fermion');
	return bb_fermion( $user_id, $topic_id ) ;
}

function bozo_profile_admin_keys( $a ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_bozo_profile_admin_keys');
	return bb_bozo_profile_admin_keys( $a ) ;
}
function bozo_add_admin_page() {
	bb_log_deprecated('function', __FUNCTION__, 'bb_bozo_add_admin_page');
	return bb_bozo_add_admin_page() ;
}

function bozo_admin_page() {
	bb_log_deprecated('function', __FUNCTION__, 'bb_bozo_admin_page');
	return bb_bozo_admin_page() ;
}

function encodeit( $matches ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_encodeit');
	return bb_encodeit( $matches ) ;
}

function decodeit( $matches ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_decodeit');
	return bb_decodeit( $matches ) ;
}

function code_trick( $text ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_code_trick');
	return bb_code_trick( $text ) ;
}

function code_trick_reverse( $text ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_code_trick_reverse');
	return bb_code_trick_reverse( $text ) ;
}

function encode_bad( $text ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_encode_bad');
	return bb_encode_bad( $text ) ;
}

function user_sanitize( $text, $strict = false ) {
	bb_log_deprecated('function', __FUNCTION__, 'sanitize_user');
	return sanitize_user( $text, $strict );
}

function bb_user_sanitize( $text, $strict = false ) {
	bb_log_deprecated('function', __FUNCTION__, 'sanitize_user');
	return sanitize_user( $text, $strict );
}

function utf8_cut( $utf8_string, $length ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_utf8_cut');
	return bb_utf8_cut( $utf8_string, $length ) ;
}

function tag_sanitize( $tag ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_tag_sanitize');
	return bb_tag_sanitize( $tag ) ;
}

function sanitize_with_dashes( $text, $length = 200 ) { // Multibyte aware
	bb_log_deprecated('function', __FUNCTION__, 'bb_sanitize_with_dashes');
	return bb_sanitize_with_dashes( $text, $length ) ;
}

function bb_make_feed( $link ) {
	bb_log_deprecated('function', __FUNCTION__, 'no aternative');
	return trim( $link );
}

function show_context( $term, $text ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_show_context');
	return bb_show_context( $term, $text );
}

function closed_title( $title ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_closed_label');
	return bb_closed_label( $title );
}

// Closed label now applied using bb_topic_labels filters
function bb_closed_title( $title ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_closed_label');
	return bb_closed_label( $title );
}

function make_link_view_all( $link ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_make_link_view_all');
	return bb_make_link_view_all( $link );
}

function remove_topic_tag( $tag_id, $user_id, $topic_id ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_remove_topic_tag');
	return bb_remove_topic_tag( $tag_id, $user_id, $topic_id );
}

function add_topic_tag( $topic_id, $tag ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_add_topic_tag');
	return bb_add_topic_tag( $topic_id, $tag );
}

function add_topic_tags( $topic_id, $tags ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_add_topic_tags');
	return bb_add_topic_tags( $topic_id, $tags );
}

function create_tag( $tag ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_create_tag');
	return bb_create_tag( $tag );
}

function destroy_tag( $tag_id, $recount_topics = true ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_destroy_tag');
	return bb_destroy_tag( $tag_id, $recount_topics );
}

if ( !function_exists( 'get_tag_id' ) ) :
function get_tag_id( $tag ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_get_tag_id');
	return bb_get_tag_id( $tag );
}
endif;

if ( !function_exists( 'get_tag' ) ) :
function get_tag( $tag_id, $user_id = 0, $topic_id = 0 ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_get_tag');
	return bb_get_tag( $tag_id, $user_id, $topic_id );
}
endif;

if ( !function_exists( 'get_tag_by_name' ) ) :
function get_tag_by_name( $tag ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_get_tag_by_name');
	return bb_get_tag( $tag );
}
endif;

function get_topic_tags( $topic_id ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_get_topic_tags');
	return bb_get_topic_tags( $topic_id );
}

function get_user_tags( $topic_id, $user_id ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_get_user_tags');
	return bb_get_user_tags( $topic_id, $user_id );
}

function get_other_tags( $topic_id, $user_id ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_get_other_tags');
	return bb_get_other_tags( $topic_id, $user_id );
}

function get_public_tags( $topic_id ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_get_public_tags');
	return bb_get_public_tags( $topic_id );
}

function get_tagged_topic_ids( $tag_id ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_get_tagged_topic_ids');
	return bb_get_tagged_topic_ids( $tag_id );
}

if ( !function_exists( 'get_top_tags' ) ) :
function get_top_tags( $recent = true, $limit = 40 ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_get_top_tags');
	return bb_get_top_tags( array( 'number' => $limit ) );
}
endif;

if ( !function_exists( 'get_tag_name' ) ) :
function get_tag_name( $id = 0 ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_get_tag_name');
	return bb_get_tag_name( $id );
}
endif;

if ( !function_exists( 'tag_name' ) ) :
function tag_name( $id = 0 ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_tag_name');
	bb_tag_name( $id );
}
endif;

if ( !function_exists( 'get_tag_rss_link' ) ) :
function get_tag_rss_link( $id = 0 ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_get_tag_posts_rss_link');
	return bb_get_tag_posts_rss_link( $id );
}
endif;

if ( !function_exists( 'tag_rss_link' ) ) :
function tag_rss_link( $id = 0 ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_tag_posts_rss_link');
	bb_tag_posts_rss_link( $id );
}
endif;

if ( !function_exists( 'get_tag_page_link' ) ) :
function get_tag_page_link() {
	bb_log_deprecated('function', __FUNCTION__, 'bb_get_tag_page_link');
	bb_get_tag_page_link();
}
endif;

if ( !function_exists( 'tag_page_link' ) ) :
function tag_page_link() {
	bb_log_deprecated('function', __FUNCTION__, 'bb_tag_page_link');
	bb_tag_page_link();
}
endif;

if ( !function_exists( 'get_tag_remove_link' ) ) :
function get_tag_remove_link() {
	bb_log_deprecated('function', __FUNCTION__, 'bb_get_tag_remove_link');
	bb_get_tag_remove_link();
}
endif;

if ( !function_exists( 'tag_remove_link' ) ) :
function tag_remove_link() {
	bb_log_deprecated('function', __FUNCTION__, 'bb_tag_remove_link');
	bb_tag_remove_link();
}
endif;

if ( !function_exists( 'tag_heat_map' ) ) :
function tag_heat_map( $args = '' ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_tag_heat_map (with variations to arguments)');
	$defaults = array( 'smallest' => 8, 'largest' => 22, 'unit' => 'pt', 'limit' => 45, 'format' => 'flat' );
	$args = wp_parse_args( $args, $defaults );

	if ( 1 < $fn = func_num_args() ) : // For back compat
		$args['smallest'] = func_get_arg(0);
		$args['largest']  = func_get_arg(1);
		$args['unit']     = 2 < $fn ? func_get_arg(2) : $unit;
		$args['limit']    = 3 < $fn ? func_get_arg(3) : $limit;
	endif;

	bb_tag_heat_map( $args );
}
endif;

function get_bb_location() {
	bb_log_deprecated('function', __FUNCTION__, 'no alternative');
	$r = bb_get_location();
	if ( !$r )
		$r = apply_filters( 'get_bb_location', '' ); // Deprecated filter
	return $r;
}

function bb_parse_args( $args, $defaults = '' ) {
	bb_log_deprecated('function', __FUNCTION__, 'wp_parse_args');
	return wp_parse_args( $args, $defaults );
}

if ( !function_exists( 'is_tag' ) ) :
function is_tag() {
	bb_log_deprecated('function', __FUNCTION__, 'bb_is_tag');
	return bb_is_tag();
}
endif;

if ( !function_exists( 'is_tags' ) ) :
function is_tags() {
	bb_log_deprecated('function', __FUNCTION__, 'bb_is_tags');
	return bb_is_tags();
}
endif;

if ( !function_exists( 'tag_link' ) ) :
function tag_link() {
	bb_log_deprecated('function', __FUNCTION__, 'bb_tag_link');
	bb_tag_link();
}
endif;

if ( !function_exists( 'tag_link_base' ) ) :
function tag_link_base() {
	bb_log_deprecated('function', __FUNCTION__, 'bb_tag_link_base');
	bb_tag_link_base();
}
endif;

if ( !function_exists( 'get_tag_link' ) ) :
function get_tag_link() {
	bb_log_deprecated('function', __FUNCTION__, 'bb_get_tag_link');
	bb_get_tag_link();
}
endif;

if ( !function_exists( 'get_tag_link_base' ) ) :
function get_tag_link_base() {
	bb_log_deprecated('function', __FUNCTION__, 'bb_get_tag_link_base');
	bb_get_tag_link_base();
}
endif;

// It's not omnipotent
function bb_path_to_url( $path ) {
	bb_log_deprecated('function', __FUNCTION__, 'no alternative');
	return apply_filters( 'bb_path_to_url', bb_convert_path_base( $path, BB_PATH, bb_get_uri(null, null, BB_URI_CONTEXT_TEXT) ), $path );
}

// Neither is this one
function bb_url_to_path( $url ) {
	bb_log_deprecated('function', __FUNCTION__, 'no alternative');
	return apply_filters( 'bb_url_to_path', bb_convert_path_base( $url, bb_get_uri(null, null, BB_URI_CONTEXT_TEXT), BB_PATH ), $url );
}

function bb_convert_path_base( $path, $from_base, $to_base ) {
	bb_log_deprecated('function', __FUNCTION__, 'no alternative');
	$last_char = $path{strlen($path)-1};
	if ( '/' != $last_char && '\\' != $last_char )
		$last_char = '';

	list($from_base, $to_base) = bb_trim_common_path_right($from_base, $to_base);

	if ( 0 === strpos( $path, $from_base ) )
		$r = $to_base . substr($path, strlen($from_base)) . $last_char;
	else
		return false;

	$r = str_replace(array('//', '\\\\'), array('/', '\\'), $r);
	$r = preg_replace('|:/([^/])|', '://$1', $r);

	return $r;
}

function bb_trim_common_path_right( $one, $two ) {
	bb_log_deprecated('function', __FUNCTION__, 'no alternative');
	$root_one = false;
	$root_two = false;

	while ( false === $root_one ) {
		$base_one = basename($one);
		$base_two = basename($two);
		if ( !$base_one || !$base_two )
			break;		
		if ( $base_one == $base_two ) {
			$one = dirname($one);
			$two = dirname($two);
		} else {
			$root_one = $one;
			$root_two = $two;
		}
	}

	return array($root_one, $root_two);
}

function deleted_topics( $where ) {
	bb_log_deprecated('function', __FUNCTION__, 'no alternative');
	return preg_replace( '/(\w+\.)?topic_status = ["\']?0["\']?/', "\\1topic_status = 1", $where);
}

function no_replies( $where ) {
	bb_log_deprecated('function', __FUNCTION__, 'no alternative');
	return $where . ' AND topic_posts = 1 ';
}

function untagged( $where ) {
	bb_log_deprecated('function', __FUNCTION__, 'no alternative');
	return $where . ' AND tag_count = 0 ';
}

function get_views() {
	bb_log_deprecated('function', __FUNCTION__, 'bb_get_views');
	return bb_get_views();
}

if ( !function_exists( 'balanceTags' ) ) :
function balanceTags( $text ) {
	bb_log_deprecated('function', __FUNCTION__, 'force_balance_tags');
	return force_balance_tags( $text );
}
endif;

// With no extra arguments, converts array of objects into object of arrays
// With extra arguments corresponding to name of object properties, returns array of arrays:
//     list($a, $b) = bb_pull_cols( $obj_array, 'a', 'b' );
function bb_pull_cols( $obj_array ) {
	bb_log_deprecated('function', __FUNCTION__, 'no alternative');
	$r = new stdClass;
	foreach ( array_keys($obj_array) as $o )
		foreach ( get_object_vars( $obj_array[$o] ) as $k => $v )
			$r->{$k}[] = $v;

	if ( 1 == func_num_args() )
		return $r;

	$args = func_get_args();
	$args = array_splice($args, 1);

	$a = array();
	foreach ( $args as $arg )
		$a[] = $r->$arg;
	return $a;
}

// $length parameter is deprecated
function bb_random_pass( $length ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_generate_password');
	if ( 12 < (int) $length ) {
		$length = 12;
	}
	return bb_generate_password( $length );
}

// Old RSS related functions
function get_recent_rss_link() {
	bb_log_deprecated('function', __FUNCTION__, 'bb_get_posts_rss_link');
	return bb_get_posts_rss_link();
}

function forum_rss_link( $forum_id = 0 ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_get_forum_posts_rss_link');
	bb_forum_posts_rss_link( $forum_id );
}

function get_forum_rss_link( $forum_id = 0 ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_get_forum_posts_rss_link');
	return bb_get_forum_posts_rss_link( $forum_id );
}

function bb_register_activation_hook($file, $function) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_register_plugin_activation_hook');
	bb_register_plugin_activation_hook($file, $function);
}

function bb_register_deactivation_hook($file, $function) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_register_plugin_deactivation_hook');
	bb_register_plugin_deactivation_hook($file, $function);
}

function bb_enqueue_script( $handle, $src = false, $deps = array(), $ver = false ) {
	bb_log_deprecated('function', __FUNCTION__, 'wp_enqueue_script');
	wp_enqueue_script( $handle, $src, $deps, $ver );
}

function bb_get_user_by_name( $name ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_get_user');
	return bb_get_user( $name, array( 'by' => 'login' ) );
}

function bb_user_exists( $user ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_get_user');
	return bb_get_user( $user );
}

function bb_maybe_serialize( $string ) {
	bb_log_deprecated('function', __FUNCTION__, 'maybe_serialize');
	return maybe_serialize( $string );
}

function bb_maybe_unserialize( $string ) {
	bb_log_deprecated('function', __FUNCTION__, 'maybe_unserialize');
	return maybe_unserialize( $string );
}

function bb_get_active_theme_folder() {
	bb_log_deprecated('function', __FUNCTION__, 'bb_get_active_theme_directory');
	return apply_filters( 'bb_get_active_theme_folder', bb_get_active_theme_directory() );
}

function bb_tag_sanitize( $tag ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_pre_term_slug');
	return bb_pre_term_slug( $tag );
}

function bb_get_tag_by_name( $tag ) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_get_tag');
	return bb_get_tag( $tag );
}

function bb_dbDelta($queries, $execute = true) {
	bb_log_deprecated('function', __FUNCTION__, 'bb_sql_delta');
	return bb_sql_delta($queries, $execute);
}

function bb_make_db_current() {
	bb_log_deprecated('function', __FUNCTION__, 'no alternative');
	return false;
}

function bb_maybe_add_column( $table_name, $column_name, $create_ddl ) {
	bb_log_deprecated('function', __FUNCTION__, 'no alternative');
	return false;
}

class BB_Cache {
	var $use_cache = false;
	var $flush_freq = 100;
	var $flush_time = 172800; // 2 days

	function get_user( $user_id, $use_cache = true ) {
		bb_log_deprecated('class::function', __CLASS__ . '::' . __FUNCTION__, 'bb_get_user');
		return bb_get_user( $user_id );
	}

	function append_current_user_meta( $user ) {
		bb_log_deprecated('class::function', __CLASS__ . '::' . __FUNCTION__, 'bb_append_meta');
		return bb_append_meta( $user, 'user' );
	}

	function append_user_meta( $user ) {
		bb_log_deprecated('class::function', __CLASS__ . '::' . __FUNCTION__, 'bb_append_meta');
		return bb_append_meta( $user, 'user' );
	}

	// NOT bbdb::prepared
	function cache_users( $ids, $use_cache = true ) {
		bb_log_deprecated('class::function', __CLASS__ . '::' . __FUNCTION__, 'bb_cache_users');
		return bb_cache_users( $ids );
	}

	// NOT bbdb::prepared
	function get_topic( $topic_id, $use_cache = true ) {
		bb_log_deprecated('class::function', __CLASS__ . '::' . __FUNCTION__, 'get_topic');
		return get_topic( $topic_id, $use_cache );
	}

	// NOT bbdb::prepared
	function get_thread( $topic_id, $page = 1, $reverse = 0 ) {
		bb_log_deprecated('class::function', __CLASS__ . '::' . __FUNCTION__, 'get_thread');
		return get_thread( $topic_id, $page, $reverse );
	}

	// NOT bbdb::prepared
	function cache_posts( $query ) { // soft cache
		bb_log_deprecated('class::function', __CLASS__ . '::' . __FUNCTION__, 'bb_cache_posts');
		return bb_cache_posts( $query );
	}

	// NOT bbdb::prepared
	function get_forums() {
		bb_log_deprecated('class::function', __CLASS__ . '::' . __FUNCTION__, 'bb_get_forums');
		return bb_get_forums();
	}

	function get_forum( $forum_id ) {
		bb_log_deprecated('class::function', __CLASS__ . '::' . __FUNCTION__, 'bb_get_forum');
		return bb_get_forum( $forum_id );
	}

	function read_cache($file) {
		bb_log_deprecated('class::function', __CLASS__ . '::' . __FUNCTION__, 'no alternative');
		return false;
	}

	function write_cache($file, $data) {
		bb_log_deprecated('class::function', __CLASS__ . '::' . __FUNCTION__, 'no alternative');
		return false;
	}

	function flush_one( $type, $id = false, $page = 0 ) {
		bb_log_deprecated('class::function', __CLASS__ . '::' . __FUNCTION__, 'no alternative');
		return true;
	}

	function flush_many( $type, $id, $start = 0 ) {
		bb_log_deprecated('class::function', __CLASS__ . '::' . __FUNCTION__, 'no alternative');
		return true;
	}

	function flush_old() {
		bb_log_deprecated('class::function', __CLASS__ . '::' . __FUNCTION__, 'no alternative');
		return true;
	}

	function flush_all() {
		bb_log_deprecated('class::function', __CLASS__ . '::' . __FUNCTION__, 'no alternative');
		return true;
	}

}

function new_topic( $args = null ) {
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_new_topic_link' );
	bb_new_topic_link( $args );
}

function bb_upgrade_1060() {
	bb_log_deprecated( 'function', __FUNCTION__, 'no alternative' );
}

if ( !function_exists( 'paginate_links' ) ) : // Deprecated in bbPress not WordPress
function paginate_links( $args = '' ) {
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_paginate_links' );
	return bb_paginate_links( $args );
}
endif;

if ( !function_exists('wp_clear_auth_cookie') ) : // Deprecated in bbPress not WordPress
function wp_clear_auth_cookie() {
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_clear_auth_cookie' );
	bb_clear_auth_cookie();
}
endif;

if ( !function_exists( 'wp_validate_auth_cookie' ) ) : // Deprecated in bbPress not WordPress
function wp_validate_auth_cookie( $cookie = '', $scheme = 'auth' ) {
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_validate_auth_cookie' );
	return bb_validate_auth_cookie( $cookie, $scheme );
}
endif;

if ( !function_exists( 'wp_set_auth_cookie' ) ) : // Deprecated in bbPress not WordPress
function wp_set_auth_cookie( $user_id, $remember = false, $secure = '' ) {
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_set_auth_cookie' );
	bb_set_auth_cookie( $user_id, $remember, $secure );
}
endif;

if ( !function_exists( 'wp_salt' ) ) : // Deprecated in bbPress not WordPress
function wp_salt( $scheme = 'auth' ) {
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_salt' );
	return bb_salt( $scheme );
}
endif;

if ( !function_exists( 'wp_hash' ) ) : // Deprecated in bbPress not WordPress
function wp_hash( $data, $scheme = 'auth' ) {
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_hash' );
	return bb_hash( $data, $scheme );
}
endif;

if ( !function_exists( 'wp_hash_password' ) ) : // Deprecated in bbPress not WordPress
function wp_hash_password( $password ) {
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_hash_password' );
	return bb_hash_password( $password );
}
endif;

if ( !function_exists( 'wp_check_password') ) : // Deprecated in bbPress not WordPress
function wp_check_password( $password, $hash, $user_id = '' ) {
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_check_password' );
	return bb_check_password( $password, $hash, $user_id );
}
endif;

if ( !function_exists( 'wp_generate_password' ) ) : // Deprecated in bbPress not WordPress
function wp_generate_password( $length = 12, $special_chars = true ) {
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_generate_password' );
	return bb_generate_password( $length, $special_chars );
}
endif;

if ( !class_exists( 'WP_User' ) ) : // Deprecated in BackPress not WordPress
class WP_User extends BP_User {
	function WP_User( $id, $name = '' ) {
		return parent::BP_User( $id, $name );
	}
}
endif;

function bb_sql_get_column_definition( $column_data ) {
	require_once( BACKPRESS_PATH . 'class.bp-sql-schema-parser.php' );
	bb_log_deprecated( 'function', __FUNCTION__, 'BP_SQL_Schema_Parser::get_column_definition' );
	return BP_SQL_Schema_Parser::get_column_definition( $column_data );
}

function bb_sql_get_index_definition( $index_data ) {
	require_once( BACKPRESS_PATH . 'class.bp-sql-schema-parser.php' );
	bb_log_deprecated( 'function', __FUNCTION__, 'BP_SQL_Schema_Parser::get_index_definition' );
	return BP_SQL_Schema_Parser::get_index_definition( $index_data );
}

function bb_sql_describe_table( $query ) {
	require_once( BACKPRESS_PATH . 'class.bp-sql-schema-parser.php' );
	bb_log_deprecated( 'function', __FUNCTION__, 'BP_SQL_Schema_Parser::describe_table' );
	return BP_SQL_Schema_Parser::describe_table( $query );
}

function bb_sql_parse( $sql ) {
	require_once( BACKPRESS_PATH . 'class.bp-sql-schema-parser.php' );
	bb_log_deprecated( 'function', __FUNCTION__, 'BP_SQL_Schema_Parser::parse' );
	return BP_SQL_Schema_Parser::parse( $sql );
}

function bb_sql_delta( $queries, $execute = true ) {
	require_once( BACKPRESS_PATH . 'class.bp-sql-schema-parser.php' );
	bb_log_deprecated( 'function', __FUNCTION__, 'BP_SQL_Schema_Parser::delta' );
	global $bbdb;
	return BP_SQL_Schema_Parser::delta( $bbdb, $queries, false, $execute );
}

function is_front() {
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_is_front' );
	return bb_is_front();
}

function is_forum() {
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_is_forum' );
	return bb_is_forum();
}

function is_bb_tags() {
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_is_tags' );
	return bb_is_tags();
}

function is_bb_tag() {
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_is_tag' );
	return bb_is_tag();
}

function is_topic_edit() {
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_is_topic_edit' );
	return bb_is_topic_edit();
}

function is_topic() {
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_is_topic' );
	return bb_is_topic();
}

function is_bb_feed() {
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_is_feed' );
	return bb_is_feed();
}

function is_bb_search() {
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_is_search' );
	return bb_is_search();
}

function is_bb_profile() {
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_is_profile' );
	return bb_is_profile();
}

function is_bb_favorites() {
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_is_favorites' );
	return bb_is_favorites();
}

function is_view() {
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_is_view' );
	return bb_is_view();
}

function is_bb_stats() {
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_is_statistics' );
	return bb_is_statistics();
}

function is_bb_admin() {
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_is_admin' );
	return bb_is_admin();
}

function bb_verify_email( $email, $check_dns = false )
{
	bb_log_deprecated( 'function', __FUNCTION__, 'is_email' );
	return is_email( $email, $check_dns );
}

function bb_tag_rss_link( $tag_id = 0, $context = 0 )
{
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_tag_posts_rss_link' );
	return bb_tag_posts_rss_link( $tag_id, $context );
}

function bb_get_tag_rss_link( $tag_id = 0, $context = 0 )
{
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_get_tag_posts_rss_link' );
	return bb_get_tag_posts_rss_link( $tag_id, $context );
}

function rename_tag( $tag_id, $tag_name )
{
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_rename_tag' );
	return bb_rename_tag( $tag_id, $tag_name );
}

function merge_tags( $old_id, $new_id )
{
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_merge_tags' );
	return bb_merge_tags( $old_id, $new_id );
}

function bb_related_tags( $_tag = false, $number = 0 )
{
	bb_log_deprecated( 'function', __FUNCTION__, 'no alternative' );
	return false;
}

function bb_related_tags_heat_map( $args = '' )
{
	bb_log_deprecated( 'function', __FUNCTION__, 'no alternative' );
	return;
}

function bb_cache_javascript_headers()
{
	bb_log_deprecated( 'function', __FUNCTION__, 'cache_javascript_headers' );
	cache_javascript_headers();
}

function bb_is_ssl()
{
	bb_log_deprecated( 'function', __FUNCTION__, 'is_ssl' );
	return is_ssl();
}

function bb_force_ssl_user_forms( $force = '' )
{
	bb_log_deprecated( 'function', __FUNCTION__, 'force_ssl_login' );
	return force_ssl_login( $force );
}

function bb_force_ssl_admin( $force = '' )
{
	bb_log_deprecated( 'function', __FUNCTION__, 'force_ssl_admin' );
	return force_ssl_admin( $force );
}

function get_forums( $args = null )
{
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_get_forums' );
	return bb_get_forums( $args );
}

function get_forum( $id )
{
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_get_forum' );
	return bb_get_forum( $id );
}

function get_latest_posts( $limit = 0, $page = 1 )
{
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_get_latest_posts' );
	return bb_get_latest_posts( $limit, $page );
}

function get_latest_forum_posts( $forum_id, $limit = 0, $page = 1 )
{
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_get_latest_forum_posts' );
	return bb_get_latest_forum_posts( $forum_id, $limit, $page );
}

function update_post_positions( $topic_id )
{
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_update_post_positions' );
	return bb_update_post_positions( $topic_id );
}

function topics_replied_on_undelete_post( $post_id )
{
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_topics_replied_on_undelete_post' );
	return bb_topics_replied_on_undelete_post( $post_id );
}

function post_author_cache( $posts )
{
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_post_author_cache' );
	return bb_post_author_cache( $posts );
}

function get_recent_user_replies( $user_id )
{
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_get_recent_user_replies' );
	return bb_get_recent_user_replies( $user_id );
}

function no_where( $where )
{
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_no_where' );
	return bb_no_where( $where );
}

function get_path( $level = 1, $base = false, $request = false )
{
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_get_path' );
	return bb_get_path( $level, $base, $request );
}

function add_profile_tab( $tab_title, $users_cap, $others_cap, $file, $arg = false )
{
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_add_profile_tab' );
	return bb_add_profile_tab( $tab_title, $users_cap, $others_cap, $file, $arg );
}

function can_access_tab( $profile_tab, $viewer_id, $owner_id )
{
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_can_access_tab' );
	return bb_can_access_tab( $profile_tab, $viewer_id, $owner_id );
}

function get_profile_info_keys( $context = null )
{
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_get_profile_info_keys' );
	return bb_get_profile_info_keys( $context );
}

function get_profile_admin_keys( $context = null )
{
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_get_profile_admin_keys' );
	return bb_get_profile_admin_keys( $context );
}

function get_assignable_caps()
{
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_get_assignable_caps' );
	return bb_get_assignable_caps();
}

function get_page_number( $item, $per_page = 0 )
{
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_get_page_number' );
	return bb_get_page_number( $item, $per_page );
}

function get_recent_registrants( $num = 10 )
{
	bb_log_deprecated('function', __FUNCTION__, 'no aternative');
	return;
}

if ( !function_exists( 'get_total_users' ) ) {
	function get_total_users()
	{
		bb_log_deprecated( 'function', __FUNCTION__, 'bb_get_total_users' );
		return bb_get_total_users();
	}
}

function total_users()
{
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_total_users' );
	bb_total_users();
}

if ( !function_exists( 'update_user_status' ) ) {
	function update_user_status( $user_id, $user_status = 0 )
	{
		bb_log_deprecated( 'function', __FUNCTION__, 'bb_update_user_status' );
		return bb_update_user_status( $user_id, $user_status );
	}
}

function bb_get_current_commenter() {
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_get_current_poster' );
	extract( bb_get_current_poster() );
	return array( 'comment_author' => $post_author, 'comment_email' => $post_author_email, 'comment_author_url' => $post_author_url );
}

function bb_check_comment_flood( $ip = '', $email = '', $date = '' ) {
	bb_log_deprecated( 'function', __FUNCTION__, 'bb_check_post_flood' );
	bb_check_post_flood();
}
