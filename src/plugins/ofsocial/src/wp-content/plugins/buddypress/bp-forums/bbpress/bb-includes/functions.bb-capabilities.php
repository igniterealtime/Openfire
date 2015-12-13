<?php
/**
 * bbPress Roles and Capabilities Wrapping Functions.
 *
 * @package bbPress
 * @subpackage User
 */



/**
 * Whether current user has capability or role.
 *
 * @since 0.7.2
 * @uses $bb_current_user Current User Object
 *
 * @param string $capability Capability or role name.
 * @return bool
 */
function bb_current_user_can($capability) {
	global $bb_current_user;

	$args = array_slice(func_get_args(), 1);
	$args = array_merge(array($capability), $args);

	if ( empty($bb_current_user) ) {
		$retvalue = false;
		if ( ( $capability == 'write_topic' || $capability == 'write_topics' ) && !bb_is_login_required() )
			$retvalue = true;
	} else {
		$retvalue = call_user_func_array(array(&$bb_current_user, 'has_cap'), $args);
	}
	
	// Use bb_user_has_cap whenever possible!  This will not work everywhere.
	return apply_filters('bb_current_user_can', $retvalue, $capability, $args);
}

/**
 * Give a user the default role
 *
 * @since 0.7.2
 *
 * @param BP_User $user User object to give default role to
 */
function bb_give_user_default_role( $user ) {
	if ( !( is_object($user) && is_a($user, 'BP_User') ) )
		return;
	$user->set_role('member');
}

/**
 * Setup all default roles and associate them with capabilities
 *
 * @since 0.7.2
 *
 * @param BP_Roles $roles Roles object to add default roles to
 */
function bb_init_roles( &$roles ) {
	$roles->add_role( 'keymaster', __('Key Master'), array(
		'use_keys' => true,				// Verb forms of roles - keymaster
		'administrate' => true,			// administrator
		'moderate' => true, 			// moderator
		'participate' => true,			// member

		'keep_gate' => true,			// Make new Key Masters		//+
		'import_export' => true,		// Import and export data	//+
		'recount' => true,				// bb-do-counts.php			//+
		'manage_options' => true,		// backend					//+
		'manage_themes' => true,		// Themes					//+
		'manage_plugins' => true,		// Plugins					//+
		'manage_options' => true,		// Options					//+
		'edit_users' => true,
		'manage_tags' => true,			// Rename, Merge, Destroy
		'edit_others_favorites' => true,
		'manage_forums' => true,		// Add/Rename forum
		'delete_forums' => true,		// Delete forum
		'delete_topics' => true,
		'close_topics' => true,
		'stick_topics' => true,
		'move_topics' => true,
		'view_by_ip' => true,			// view-ip.php
		'edit_closed' => true,			// Edit closed topics
		'edit_deleted' => true,			// Edit deleted topics/posts
		'browse_deleted' => true,		// Use 'deleted' view
		'edit_others_tags' => true,
		'edit_others_topics' => true,
		'delete_posts' => true,
		'throttle' => true,				// Post back to back arbitrarily quickly
		'ignore_edit_lock' => true,
		'edit_others_posts' => true,
		'edit_favorites' => true,
		'edit_tags' => true,
		'edit_topics' => true,			// Edit title, resolution status
		'edit_posts' => true,
		'edit_profile' => true,
		'write_topics' => true,
		'write_posts' => true,
		'change_password' => true,
		'read' => true
	) );

	$roles->add_role( 'administrator', __('Administrator'), array(
		'administrate' => true,
		'moderate' => true,
		'participate' => true,

		'edit_users' => true,				//+
		'edit_others_favorites' => true,	//+
		'manage_forums' => true,			//+
		'delete_forums' => true,			//+
		'manage_tags' => true,
		'delete_topics' => true,
		'close_topics' => true,
		'stick_topics' => true,
		'move_topics' => true,
		'view_by_ip' => true,
		'edit_closed' => true,
		'edit_deleted' => true,
		'browse_deleted' => true,
		'edit_others_tags' => true,
		'edit_others_topics' => true,
		'delete_posts' => true,
		'throttle' => true,
		'ignore_edit_lock' => true,
		'edit_others_posts' => true,
		'edit_favorites' => true,
		'edit_tags' => true,
		'edit_topics' => true,
		'edit_posts' => true,
		'edit_profile' => true,
		'write_topics' => true,
		'write_posts' => true,
		'change_password' => true,
		'read' => true
	) );

	$roles->add_role( 'moderator', __('Moderator'), array(
		'moderate' => true,
		'participate' => true,

		'manage_tags' => true,			//+
		'delete_topics' => true,		//+
		'close_topics' => true,			//+
		'stick_topics' => true,			//+
		'move_topics' => true,			//+
		'view_by_ip' => true,			//+
		'edit_closed' => true,			//+
		'edit_deleted' => true,			//+
		'browse_deleted' => true,		//+
		'edit_others_tags' => true,		//+
		'edit_others_topics' => true,	//+
		'delete_posts' => true,			//+
		'throttle' => true,				//+
		'ignore_edit_lock' => true,		//+
		'edit_others_posts' => true,	//+
		'edit_favorites' => true,
		'edit_tags' => true,
		'edit_topics' => true,
		'edit_posts' => true,
		'edit_profile' => true,
		'write_topics' => true,
		'write_posts' => true,
		'change_password' => true,
		'read' => true
	) );


	$roles->add_role( 'member', __('Member'), array(
		'participate' => true,

		'edit_favorites' => true,
		'edit_tags' => true,
		'edit_topics' => true,
		'edit_posts' => true,
		'edit_profile' => true,
		'write_topics' => true,
		'write_posts' => true,
		'change_password' => true,
		'read' => true
	) );

	$roles->add_role( 'inactive', __('Inactive'), array(
		'change_password' => true,
		'read' => true
	) );

	$roles->add_role( 'blocked', __('Blocked'), array(
		'not_play_nice' => true // Madness - a negative capability.  Don't try this at home.
	) );
}

/**
 * Map meta capabilities to primitive capabilities.
 *
 * This does not actually compare whether the user ID has the actual capability,
 * just what the capability or capabilities are. Meta capability list value can
 * be 'delete_user', 'edit_user', 'delete_post', 'delete_page', 'edit_post',
 * 'edit_page', 'read_post', or 'read_page'.
 *
 * @since 0.7.2
 *
 * @param array $caps Previously existing capabilities
 * @param string $cap Capability name.
 * @param int $user_id User ID.
 * @return array Actual capabilities for meta capability.
 */
function bb_map_meta_cap( $caps, $cap, $user_id, $args ) {
	// Unset the meta cap
	if ( false !== $cap_pos = array_search( $cap, $caps ) )
		unset( $caps[$cap_pos] );

	switch ( $cap ) {
		case 'write_post':
			$caps[] = 'write_posts';
			break;
		case 'edit_post':
			// edit_posts, edit_others_posts, edit_deleted, edit_closed, ignore_edit_lock
			if ( !$bb_post = bb_get_post( $args[0] ) ) {
				$caps[] = 'magically_provide_data_given_bad_input';
				return $caps;
			}
			if ( $user_id == $bb_post->poster_id )
				$caps[] = 'edit_posts';
			else
				$caps[] = 'edit_others_posts';
			if ( $bb_post->post_status == '1' )
				$caps[] = 'edit_deleted';
			if ( !topic_is_open( $bb_post->topic_id ) )
				$caps[] = 'edit_closed';
			$post_time = bb_gmtstrtotime( $bb_post->post_time );
			$curr_time = time() + 1;
			$edit_lock = bb_get_option( 'edit_lock' );
			if ( $edit_lock >= 0 && $curr_time - $post_time > $edit_lock * 60 )
				$caps[] = 'ignore_edit_lock';
			break;
		case 'delete_post' :
			// edit_deleted, delete_posts
			if ( !$bb_post = bb_get_post( $args[0] ) ) {
				$caps[] = 'magically_provide_data_given_bad_input';
				return $caps;
			}
			if ( 0 != $bb_post->post_status )
				$caps[] = 'edit_deleted';
			// NO BREAK
		case 'manage_posts' : // back compat
			$caps[] = 'delete_posts';
			break;
		case 'write_topic':
			$caps[] = 'write_topics';
			break;
		case 'edit_topic':
			// edit_closed, edit_deleted, edit_topics, edit_others_topics
			if ( !$topic = get_topic( $args[0] ) ) {
				$caps[] = 'magically_provide_data_given_bad_input';
				return $caps;
			}
			if ( !topic_is_open( $args[0]) )
				$caps[] = 'edit_closed';
			if ( '1' == $topic->topic_status )
				$caps[] = 'edit_deleted';
			if ( $user_id == $topic->topic_poster )
				$caps[] = 'edit_topics';
			else
				$caps[] = 'edit_others_topics';
			break;
		case 'move_topic' :
			$caps[] = 'move_topics';
			break;
		case 'stick_topic' :
			$caps[] = 'stick_topics';
			break;
		case 'close_topic' :
			$caps[] = 'close_topics';
			break;
		case 'delete_topic' :
			$caps[] = 'delete_topics';
			add_filter( 'get_topic_where', 'bb_no_where', 9999 );
			if ( !$topic = get_topic( $args[0] ) ) {
				$caps[] = 'magically_provide_data_given_bad_input';
				return $caps;
			}
			if ( 0 != $topic->topic_status )
				$caps[] = 'edit_deleted';
			remove_filter( 'get_topic_where', 'bb_no_where', 9999 );
			break;
		case 'manage_topics' :
			// back compat
			$caps[] = 'move_topics';
			$caps[] = 'stick_topics';
			$caps[] = 'close_topics';
			$caps[] = 'delete_topics';
			break;
		case 'add_tag_to':
			// edit_closed, edit_deleted, edit_tags;
			if ( !$topic = get_topic( $args[0] ) ) {
				$caps[] = 'magically_provide_data_given_bad_input';
				return $caps;
			}
			if ( !topic_is_open( $topic->topic_id ) )
				$caps[] = 'edit_closed';
			if ( '1' == $topic->topic_status )
				$caps[] = 'edit_deleted';
			$caps[] = 'edit_tags';
			break;
		case 'edit_tag_by_on':
			// edit_closed, edit_deleted, edit_tags, edit_others_tags
			if ( !$topic = get_topic( $args[1] ) ) {
				$caps[] = 'magically_provide_data_given_bad_input';
				return $caps;
			}
			if ( !topic_is_open( $topic->topic_id ) )
				$caps[] = 'edit_closed';
			if ( '1' == $topic->topic_status )
				$caps[] = 'edit_deleted';
			if ( $user_id == $args[0] )
				$caps[] = 'edit_tags';
			else
				$caps[] = 'edit_others_tags';
			break;
		case 'edit_user':
			// edit_profile, edit_users;
			if ( $user_id == $args[0] )
				$caps[] = 'edit_profile';
			else
				$caps[] = 'edit_users';
			break;
		case 'edit_favorites_of':
			// edit_favorites, edit_others_favorites;
			if ( $user_id == $args[0] )
				$caps[] = 'edit_favorites';
			else
				$caps[] = 'edit_others_favorites';
			break;
		case 'delete_forum':
			$caps[] = 'delete_forums';
			break;
		case 'change_user_password':
			// change_password, edit_users
			$caps[] = 'change_password';
			if ( $user_id != $args[0] )
				$caps[] = 'edit_users';
			break;
		default:
			// If no meta caps match, return the original cap.
			$caps[] = $cap;
	}
	return $caps;
}
