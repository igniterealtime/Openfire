<?php
/*
Plugin Name: Bozo Users
Plugin URI: http://bbpress.org/
Description: Allows moderators to mark certain users as a "bozo". Bozo users can post, but their content is only visible to themselves.
Author: Michael Adams
Version: 1.1
Author URI: http://blogwaffe.com/
*/

function bb_bozo_posts( $where ) {
	if ( !$id = bb_get_current_user_info( 'id' ) )
		return $where;

	return preg_replace(
		'/(\w+\.)?post_status = ["\']?0["\']?/',
		"( \\1post_status = 0 OR \\1post_status > 1 AND \\1poster_id = '$id' )",
	$where);
}

function bb_bozo_topics( $where ) {
	if ( !$id = bb_get_current_user_info( 'id' ) )
		return $where;

	return preg_replace(
		'/(\w+\.)?topic_status = ["\']?0["\']?/',
		"( \\1topic_status = 0 OR \\1topic_status > 1 AND \\1topic_poster = '$id' )",
	$where);
}

// Gets those users with the bozo bit.  Does not grab users who have been bozoed on a specific topic.
// NOT bbdb::prepared
function bb_get_bozos( $page = 1 ) {
	global $bbdb, $bb_last_countable_query;
	$page = (int) $page;
	$limit = (int) bb_get_option('page_topics');
	if ( 1 < $page )
		$limit = ($limit * ($page - 1)) . ", $limit";
	$bb_last_countable_query = "SELECT user_id FROM $bbdb->usermeta WHERE meta_key='is_bozo' AND meta_value='1' ORDER BY umeta_id DESC LIMIT $limit";
	if ( $ids = (array) $bbdb->get_col( $bb_last_countable_query ) )
		bb_cache_users( $ids );
	return $ids;
}

function bb_current_user_is_bozo( $topic_id = false ) {
	global $bb_current_user;
	if ( bb_current_user_can('browse_deleted') && 'all' == @$_GET['view'] )
		return false;
	$is_bozo = isset($bb_current_user->data->is_bozo) && $bb_current_user->data->is_bozo;
	if ( !$topic_id || $is_bozo )
		return $is_bozo;

	global $topic;
	$topic = get_topic( $topic_id );
	$id = bb_get_current_user_info( 'id' );
	return isset($topic->bozos[$id]) && $topic->bozos[$id];
}

function bb_bozo_pre_permalink() {
	if ( bb_is_topic() )
		add_filter( 'get_topic_where', 'bb_bozo_topics' );
}

function bb_bozo_post_permalink() {
	if ( bb_is_topic() )
		remove_filter( 'get_topic_where', 'bb_bozo_topics' );
}

function bb_bozo_latest_filter() {
	global $bb_current_user;
	if ( isset($bb_current_user->data->bozo_topics) && $bb_current_user->data->bozo_topics )
		add_filter( 'get_latest_topics_where', 'bb_bozo_topics' );
}

function bb_bozo_topic_db_filter() {
	global $topic, $topic_id;
	if ( bb_current_user_is_bozo( $topic->topic_id ? $topic->topic_id : $topic_id ) ) {
		add_filter( 'get_thread_where', 'bb_bozo_posts' );
		add_filter( 'get_thread_post_ids_where', 'bb_bozo_posts' );
	}
}

function bb_bozo_profile_db_filter() {
	global $user;
	if ( bb_get_current_user_info( 'id' ) == $user->ID && @is_array($user->bozo_topics) )
		add_filter( 'get_recent_user_replies_where', 'bb_bozo_posts' );
}

function bb_bozo_recount_topics() {
	global $bbdb;
	global $messages;
	if ( isset($_POST['topic-bozo-posts']) && 1 == $_POST['topic-bozo-posts'] ):
		$old = (array) $bbdb->get_col("SELECT object_id FROM $bbdb->meta WHERE object_type = 'bb_topic' AND meta_key = 'bozos'");
		$old = array_flip($old);
		$messages[] = __('Counted the number of bozo posts in each topic');
		if ( $topics = (array) $bbdb->get_col("SELECT topic_id, poster_id, COUNT(post_id) FROM $bbdb->posts WHERE post_status > 1 GROUP BY topic_id, poster_id") ) :
			$unique_topics = array_unique($topics);
			$posters = (array) $bbdb->get_col('', 1);
			$counts = (array) $bbdb->get_col('', 2);
			foreach ($unique_topics as $i):
				$bozos = array();
				$indices = array_keys($topics, $i);
				foreach ( $indices as $index )
					$bozos[(int) $posters[$index]] = (int) $counts[$index]; 
				if ( $bozos ) :
					bb_update_topicmeta( $i, 'bozos', $bozos );
					unset($indices, $index, $old[$i]);
				endif;
			endforeach;
			unset($topics, $i, $counts, $posters, $bozos);
		endif;
		if ( $old ) :
			$old = join(',', array_map('intval', array_flip($old)));
			$bbdb->query("DELETE FROM $bbdb->meta WHERE object_type = 'bb_topic' AND object_id IN ($old) AND meta_key = 'bozos'");
		endif;
	endif;
}

function bb_bozo_recount_users() {
	global $bbdb;
	global $messages;
	if ( isset($_POST['topics-replied-with-bozos']) && 1 == $_POST['topics-replied-with-bozos'] ) :
		$messages[] = __('Counted each bozo user&#039;s total posts as well as the total topics to which they have replied');
		if ( $users = (array) $bbdb->get_col("SELECT ID FROM $bbdb->users") ) :
			$no_bozos = array();
			$bozo_mkey = $bbdb->prefix . 'bozo_topics';
			foreach ( $users as $user ) :
				$topics_replied = (int) $bbdb->get_var( $bbdb->prepare(
					"SELECT COUNT(DISTINCT topic_id) FROM $bbdb->posts WHERE post_status = 0 AND poster_id = %d",
					$user
				) );
				bb_update_usermeta( $user, $bbdb->prefix. 'topics_replied', $topics_replied );
				$bozo_keys = (array) $bbdb->get_col( $bbdb->prepare(
					"SELECT topic_id, COUNT(post_id) FROM $bbdb->posts WHERE post_status > 1 AND poster_id = %d GROUP BY topic_id",
					$user
				) );
				$bozo_values = (array) $bbdb->get_col('', 1);
				if ( $c = count($bozo_keys) ) :
					for ( $i=0; $i < $c; $i++ )
						$bozo_topics[(int) $bozo_keys[$i]] = (int) $bozo_values[$i];
					bb_update_usermeta( $user, $bozo_mkey, $bozo_topics );
				else :
					$no_bozos[] = (int) $user;
				endif;
			endforeach;
			if ( $no_bozos ) :
				$no_bozos = join(',', $no_bozos);
				$bbdb->query( $bbdb->prepare(
					"DELETE FROM $bbdb->usermeta WHERE user_id IN ($no_bozos) AND meta_key = %s",
					$bozo_mkey
				) );
			endif;
			unset($users, $user, $topics_replied, $bozo_keys, $bozo_values, $bozo_topics);
		endif;
	endif;
}

function bb_bozo_post_del_class( $classes, $post_id, $post )
{
	if ( 1 < $post->post_status && bb_current_user_can('browse_deleted') ) {
		if ( $classes ) {
			return $classes . ' bozo';
		}
		return 'bozo';
	}
	return $classes;
}

function bb_bozo_add_recount_list() {
	global $recount_list;
	$recount_list[21] = array('topics-replied-with-bozos', __('Count each bozo user&#039;s total posts as well as the total topics to which they have replied'), 'bb_bozo_recount_users');
	$recount_list[22] = array('topic-bozo-posts', __('Count the number of bozo posts in each topic'), 'bb_bozo_recount_topics');
	return;
}

function bb_bozo_topic_pages_add( $add ) {
	global $topic;
	if ( isset($_GET['view']) && 'all' == $_GET['view'] && bb_current_user_can('browse_deleted') ) :
		$add += @array_sum($topic->bozos);
	endif;
	if ( bb_current_user_is_bozo( $topic->topic_id ) )
		$add += $topic->bozos[bb_get_current_user_info( 'id' )];
	return $add;
}

function bb_bozo_get_topic_posts( $topic_posts ) {
	global $topic;
	if ( bb_current_user_is_bozo( $topic->topic_id ) )
		$topic_posts += $topic->bozos[bb_get_current_user_info( 'id' )];
	return $topic_posts;
}

function bb_bozo_new_post( $post_id ) {
	$bb_post = bb_get_post( $post_id );
	if ( 1 < $bb_post->post_status )
		bb_bozon( $bb_post->poster_id, $bb_post->topic_id );
	$topic = get_topic( $bb_post->topic_id, false );
	if ( 0 == $topic->topic_posts )
		bb_delete_topic( $topic->topic_id, 2 );
}

function bb_bozo_pre_post_status( $status, $post_id, $topic_id ) {
	if ( !$post_id && bb_current_user_is_bozo() )
		$status = 2;
	elseif ( bb_current_user_is_bozo( $topic_id ) )
		$status = 2;
	return $status;
}

function bb_bozo_delete_post( $post_id, $new_status, $old_status ) {
	$bb_post = bb_get_post( $post_id );
	if ( 1 < $new_status && 2 > $old_status )
		bb_bozon( $bb_post->poster_id, $bb_post->topic_id );
	elseif ( 2 > $new_status && 1 < $old_status )
		bb_fermion( $bb_post->poster_id, $bb_post->topic_id );
}

function bb_bozon( $user_id, $topic_id = 0 ) {
	global $bbdb;

	$user_id = (int) $user_id;
	$topic_id = (int) $topic_id;

	if ( !$topic_id )
		bb_update_usermeta( $user_id, 'is_bozo', 1 );
	else {
		$topic = get_topic( $topic_id );
		$user = bb_get_user( $user_id );

		$bozo_topics_key = $bbdb->prefix . 'bozo_topics';

		if ( isset($topic->bozos[$user_id]) )
			$topic->bozos[$user_id]++;
		elseif ( is_array($topic->bozos) )
			$topic->bozos[$user_id] = 1;
		else
			$topic->bozos = array($user_id => 1);
		bb_update_topicmeta( $topic_id, 'bozos', $topic->bozos );
		
		if ( isset($user->{$bozo_topics_key}[$topic_id]) )
			$user->{$bozo_topics_key}[$topic_id]++;
		elseif ( is_array($user->bozo_topics) )
			$user->{$bozo_topics_key}[$topic_id] = 1;
		else
			$user->$bozo_topics_key = array($topic_id => 1);
		bb_update_usermeta( $user_id, $bozo_topics_key, $user->$bozo_topics_key );
	}
}

function bb_fermion( $user_id, $topic_id = 0 ) {
	global $bbdb;

	$user_id = (int) $user_id;
	$topic_id = (int) $topic_id;

	if ( !$topic_id )
		bb_delete_usermeta( $user_id, 'is_bozo' );
	else {
		$topic = get_topic( $topic_id );
		$user = bb_get_user( $user_id );

		$bozo_topics_key = $bbdb->prefix . 'bozo_topics';

		if ( --$topic->bozos[$user_id] < 1 )
			unset($topic->bozos[$user_id]);
		bb_update_topicmeta( $topic_id, 'bozos', $topic->bozos );
		
		if ( --$user->{$bozo_topics_key}[$topic_id] < 1 )
			unset($user->{$bozo_topics_key}[$topic_id]);
		bb_update_usermeta( $user_id, $bozo_topics_key, $user->$bozo_topics_key );
	}
}

function bb_bozo_profile_admin_keys( $a ) {
	$a['is_bozo'] = array(
		0,							// Required
		__('This user is a bozo'),	// Label
		'checkbox',					// Type
		'1',						// Value
		''							// Default when not set
	);
	return $a;
}



function bb_bozo_get_bozo_user_ids()
{
	global $bbdb;
	$sql = "SELECT `user_id` FROM `$bbdb->usermeta` WHERE `meta_key` = 'is_bozo' AND `meta_value` = '1';";
	$user_ids = $bbdb->get_col( $sql, 0 );
	if ( is_wp_error( $user_ids ) || empty( $user_ids ) ) {
		return false;
	}
	return $user_ids;
}

function bb_bozo_user_search_description( $description, $h2_search, $h2_role, $user_search_object )
{
	if ( is_array( $user_search_object->roles ) && in_array( 'bozo', $user_search_object->roles ) ) {
		return sprintf( '%1$s%2$s that are bozos', $h2_search, $h2_role );
	}
	return $description;
}
add_filter( 'bb_user_search_description', 'bb_bozo_user_search_description', 10, 4 );

function bb_bozo_user_search_form_add_inputs( $r, $user_search_object )
{
	$checked = '';
	if ( is_array( $user_search_object->roles ) && in_array( 'bozo', $user_search_object->roles ) ) {
		$checked = ' checked="checked"';
	}
	
	$r .= "\t" . '<div>' . "\n";
	$r .= "\t\t" . '<label for="userbozo">' . __('Bozos only') . '</label>' . "\n";
	$r .= "\t\t" . '<div>' . "\n";
	$r .= "\t\t\t" . '<input class="checkbox-input" type="checkbox" name="userrole[]" id="userbozo" value="bozo"' . $checked . ' />' . "\n";
	$r .= "\t\t" . '</div>' . "\n";
	$r .= "\t" . '</div>' . "\n";

	return $r;
}
add_filter( 'bb_user_search_form_inputs', 'bb_bozo_user_search_form_add_inputs', 10, 2 );

function bb_bozo_user_search_role_user_ids( $role_user_ids, $roles, $args )
{
	if ( !in_array( 'bozo', $roles ) ) {
		return $role_user_ids;
	}

	$bozo_user_ids = bb_bozo_get_bozo_user_ids();

	if ( 1 === count( $roles ) ) {
		return $bozo_user_ids;
	}

	global $bbdb;
	$role_meta_key = $bbdb->escape( $bbdb->prefix . 'capabilities' );
	$role_sql_terms = array();
	foreach ( $roles as $role ) {
		if ( 'bozo' === $role ) {
			continue;
		}
		$role_sql_terms[] = "`meta_value` LIKE '%" . $bbdb->escape( like_escape( $role ) ) . "%'";
	}
	$role_sql_terms = join( ' OR ', $role_sql_terms );
	$role_sql = "SELECT `user_id` FROM `$bbdb->usermeta` WHERE `meta_key` = '$role_meta_key' AND ($role_sql_terms);";
	$role_user_ids = $bbdb->get_col( $role_sql, 0 );
	if ( is_wp_error( $role_user_ids ) || empty( $role_user_ids ) ) {
		return array();
	}

	return array_intersect( (array) $bozo_user_ids, $role_user_ids );
}
add_filter( 'bb_user_search_role_user_ids', 'bb_bozo_user_search_role_user_ids', 10, 3 );




add_filter( 'pre_post_status', 'bb_bozo_pre_post_status', 5, 3 );
add_action( 'bb_new_post', 'bb_bozo_new_post', 5 );
add_action( 'bb_delete_post', 'bb_bozo_delete_post', 5, 3 );

add_action( 'pre_permalink', 'bb_bozo_pre_permalink' );
add_action( 'post_permalink', 'bb_bozo_post_permalink' );
add_action( 'bb_index.php_pre_db', 'bb_bozo_latest_filter' );
add_action( 'bb_forum.php_pre_db', 'bb_bozo_latest_filter' );
add_action( 'bb_topic.php_pre_db', 'bb_bozo_topic_db_filter' );
add_action( 'bb_profile.php_pre_db', 'bb_bozo_profile_db_filter' );

add_action( 'bb_recount_list', 'bb_bozo_add_recount_list' );
add_action( 'topic_pages_add', 'bb_bozo_topic_pages_add' );

add_filter( 'post_del_class', 'bb_bozo_post_del_class', 10, 3 );
add_filter( 'get_topic_posts', 'bb_bozo_get_topic_posts' );

add_filter( 'get_profile_admin_keys', 'bb_bozo_profile_admin_keys' );
?>
