<?php

/* Users */

function bb_block_current_user() {
	global $bbdb;
	if ( $id = bb_get_current_user_info( 'id' ) )
		bb_update_usermeta( $id, $bbdb->prefix . 'been_blocked', 1 ); // Just for logging.
	bb_logout();
	bb_die(__("You've been blocked.  If you think a mistake has been made, contact this site's administrator."));
}

function bb_get_user( $user_id, $args = null ) {
	global $bbdb, $wp_users_object;

	// Get user
	$user = $wp_users_object->get_user( $user_id, $args );

	// Return on no user or error object
	if ( !is_object( $user ) || is_wp_error( $user ) )
		return false;

	// Re calculate the user's meta in case we're pulling from a value cached on another site
	if ( $user_vars = get_object_vars( $user ) ) {
		$prefix_length = strlen( $bbdb->prefix );
		foreach ( $user_vars as $k => $v ) {
			if ( 0 === strpos( $k, $bbdb->prefix ) ) {
				$user->{substr( $k, $prefix_length )} = $v;
			}
		}
	}

	return $user;
}

function bb_cache_users( $ids ) {
	global $wp_users_object;
	$wp_users_object->get_user( $ids );
}

function bb_get_user_by_nicename( $nicename ) {
	global $wp_users_object;
	$user = $wp_users_object->get_user( $nicename, array( 'by' => 'nicename' ) );
	if ( is_wp_error($user) )
		return false;
	return $user;
}

function bb_delete_user( $user_id, $reassign = 0 ) {
	global $wp_users_object, $bbdb;

	if ( !$user = bb_get_user( $user_id ) )
		return false;

	if ( $reassign ) {
		if ( !$new_user = bb_get_user( $reassign ) )
			return false;
		$bbdb->update( $bbdb->posts, array( 'poster_id' => $new_user->ID ), array( 'poster_id' => $user->ID ) );
		$bbdb->update( $bbdb->term_relationships, array( 'user_id' => $new_user->ID ), array( 'user_id' => $user->ID ) );
		$bbdb->update( $bbdb->topics, array( 'topic_poster' => $new_user->ID, 'topic_poster_name' => $new_user->user_login), array( 'topic_poster' => $user->ID ) );
		$bbdb->update( $bbdb->topics, array( 'topic_last_poster' => $new_user->ID, 'topic_last_poster_name' => $new_user->user_login ), array( 'topic_last_poster' => $user->ID ) );
		bb_update_topics_replied( $new_user->ID );
		wp_cache_flush( 'bb_post' );
		wp_cache_flush( 'bb_thread' );
		wp_cache_flush( 'bb_topic_tag' );
		wp_cache_flush( 'bb_topic' );
	}

	do_action( 'bb_delete_user', $user->ID, $reassign );

	$wp_users_object->delete_user( $user->ID );

	return true;
}

function bb_update_topics_replied( $user_id ) {
	global $bbdb;

	$user_id = (int) $user_id;

	if ( !$user = bb_get_user( $user_id ) )
		return false;

	$topics_replied = (int) $bbdb->get_var( $bbdb->prepare( "SELECT COUNT(DISTINCT topic_id) FROM $bbdb->posts WHERE post_status = '0' AND poster_id = %d", $user_id ) );
	return bb_update_usermeta( $user_id, $bbdb->prefix . 'topics_replied', $topics_replied );
}

function bb_update_user_status( $user_id, $user_status = 0 ) {
	global $wp_users_object;
	$user = bb_get_user( $user_id );
	$user_status = (int) $user_status;
	$wp_users_object->update_user( $user->ID, compact( 'user_status' ) );
}

function bb_trusted_roles() {
	return apply_filters( 'bb_trusted_roles', array('moderator', 'administrator', 'keymaster') );
}

function bb_is_trusted_user( $user ) { // ID, user_login, WP_User, DB user obj
	if ( is_numeric($user) || is_string($user) )
		$user = new BP_User( $user );
	elseif ( is_object($user) && is_a($user, 'BP_User') ); // Intentional
	elseif ( is_object($user) && isset($user->ID) && isset($user->user_login) ) // Make sure it's actually a user object
		$user = new BP_User( $user->ID );
	else
		return;

	if ( !$user->ID )
		return;

	return apply_filters( 'bb_is_trusted_user', (bool) array_intersect(bb_trusted_roles(), $user->roles), $user->ID );
}

function bb_apply_wp_role_map_to_user( $user, $reload = true ) {
	// Expects only user ids
	if ( !is_numeric( $user ) ) {
		return;
	}

	$user = (int) $user;

	if ( !$wordpress_table_prefix = bb_get_option('wp_table_prefix') ) {
		return;
	}

	if ( $wordpress_mu_primary_blog_id = bb_get_option( 'wordpress_mu_primary_blog_id' ) ) {
		$wordpress_table_prefix .= $wordpress_mu_primary_blog_id . '_';
	}

	if ( !$wordpress_roles_map = bb_get_option( 'wp_roles_map' ) ) {
		return;
	}

	global $bbdb;
	global $wp_roles;
	global $bb;

	static $bbpress_roles_map = false;

	if ( !$bbpress_roles_map ) {
		$bbpress_roles_map = array();
		foreach ( $wp_roles->get_names() as $_bbpress_role => $_bbpress_rolename ) {
			$bbpress_roles_map[$_bbpress_role] = 'subscriber';
		}
		unset( $_bbpress_role, $_bbpress_rolename );
		$bbpress_roles_map = array_merge( $bbpress_roles_map, array_flip( $wordpress_roles_map ) );
		unset( $bbpress_roles_map['inactive'], $bbpress_roles_map['blocked'] );
	}

	static $wordpress_userlevel_map = array(
		'administrator' => 10,
		'editor' => 7,
		'author' => 2,
		'contributor' => 1,
		'subscriber' => 0
	);

	$bbpress_roles = bb_get_usermeta( $user, $bbdb->prefix . 'capabilities' );
	$wordpress_roles = bb_get_usermeta( $user, $wordpress_table_prefix . 'capabilities' );

	if ( !$bbpress_roles && is_array( $wordpress_roles ) ) {
		$bbpress_roles_new = array();

		foreach ( $wordpress_roles as $wordpress_role => $wordpress_role_value ) {
			if ( $wordpress_roles_map[strtolower( $wordpress_role )] && $wordpress_role_value ) {
				$bbpress_roles_new[$wordpress_roles_map[strtolower( $wordpress_role )]] = true;
			}
		}

		if ( count( $bbpress_roles_new ) ) {
			bb_update_usermeta( $user, $bbdb->prefix . 'capabilities', $bbpress_roles_new );
			if ( $reload ) {
				header( 'Location: ' . bb_get_uri( null, null, BB_URI_CONTEXT_HEADER ) );
				exit;
			}
		}
	} elseif ( !$wordpress_roles && is_array( $bbpress_roles ) ) {
		$wordpress_roles_new = array();

		foreach ( $bbpress_roles as $bbpress_role => $bbpress_role_value ) {
			if ( $bbpress_roles_map[strtolower( $bbpress_role )] && $bbpress_role_value ) {
				$wordpress_roles_new[$bbpress_roles_map[strtolower( $bbpress_role )]] = true;
				$wordpress_userlevels_new[] = $wordpress_userlevel_map[$bbpress_roles_map[strtolower( $bbpress_role )]];
			}
		}

		if ( count( $wordpress_roles_new ) ) {
			bb_update_usermeta( $user, $wordpress_table_prefix . 'capabilities', $wordpress_roles_new );
			bb_update_usermeta( $user, $wordpress_table_prefix . 'user_level', max( $wordpress_userlevels_new ) );
		}
	}
}

function bb_apply_wp_role_map_to_orphans() {
	if ( !$wordpress_table_prefix = bb_get_option('wp_table_prefix') ) {
		return;
	}

	if ( $wordpress_mu_primary_blog_id = bb_get_option( 'wordpress_mu_primary_blog_id' ) ) {
		$wordpress_table_prefix .= $wordpress_mu_primary_blog_id . '_';
	}

	$role_query = <<<EOQ
		SELECT
			ID
		FROM
			`%1\$s`
		LEFT JOIN `%2\$s` AS bbrole
			ON ID = bbrole.user_id
			AND bbrole.meta_key = '%3\$scapabilities'
		LEFT JOIN `%2\$s` AS wprole
			ON ID = wprole.user_id
			AND wprole.meta_key = '%4\$scapabilities'
		WHERE
			bbrole.meta_key IS NULL OR
			bbrole.meta_value IS NULL OR
			wprole.meta_key IS NULL OR
			wprole.meta_value IS NULL
		ORDER BY
			ID
EOQ;

	global $bbdb;

	$role_query = $bbdb->prepare( $role_query, $bbdb->users, $bbdb->usermeta, $bbdb->prefix, $wordpress_table_prefix );

	if ( $user_ids = $bbdb->get_col( $role_query ) ) {
		foreach ( $user_ids as $user_id ) {
			bb_apply_wp_role_map_to_user( $user_id, false );
		}
	}
}

/**
 * Updates a user's details in the database
 *
 * {@internal Missing Long Description}}
 *
 * @since 0.7.2
 * @global bbdb $bbdb
 *
 * @param int $user_id
 * @param string $user_email
 * @param string $user_url
 * @return int
 */
function bb_update_user( $user_id, $user_email, $user_url, $display_name ) {
	global $wp_users_object;

	$user_id = (int) $user_id;
	$user_url = bb_fix_link( $user_url );

	$wp_users_object->update_user( $user_id, compact( 'user_email', 'user_url', 'display_name' ) );

	do_action('bb_update_user', $user_id);
	return $user_id;
}

/**
 * Sends a reset password email
 *
 * Sends an email to the email address specified in the user's profile
 * prompting them to change their password.
 *
 * @since 0.7.2
 * @global bbdb $bbdb
 *
 * @param string $user_login
 * @return bool
 */
function bb_reset_email( $user_login )
{
	global $bbdb;

	$user_login = sanitize_user( $user_login, true );

	if ( !$user = $bbdb->get_row( $bbdb->prepare( "SELECT * FROM $bbdb->users WHERE user_login = %s", $user_login ) ) ) {
		return new WP_Error( 'user_does_not_exist', __( 'The specified user does not exist.' ) );
	}

	$resetkey = substr( md5( bb_generate_password() ), 0, 15 );
	bb_update_usermeta( $user->ID, 'newpwdkey', $resetkey );

	$reseturi = bb_get_uri(
		'bb-reset-password.php',
		array( 'key' => $resetkey ),
		BB_URI_CONTEXT_TEXT + BB_URI_CONTEXT_BB_USER_FORMS
	);

	$message = sprintf(
		__( "If you wanted to reset your password, you may do so by visiting the following address:\n\n%s\n\nIf you don't want to reset your password, just ignore this email. Thanks!" ),
		$reseturi
	);
	$message = apply_filters( 'bb_reset_email_message', $message, $user, $reseturi, $resetkey );

	$subject = sprintf(
		__( '%s: Password Reset' ),
		bb_get_option( 'name' )
	);
	$subject = apply_filters( 'bb_reset_email_subject', $subject, $user );

	$mail_result = bb_mail(
		bb_get_user_email( $user->ID ),
		$subject,
		$message
	);

	if ( !$mail_result ) {
		return new WP_Error( 'sending_mail_failed', __( 'The email containing the password reset link could not be sent.' ) );
	}

	return true;
}

/**
 * Handles the resetting of users' passwords
 *
 * Handles resetting a user's password, prompted by an email sent by
 * {@see bb_reset_email()}
 *
 * @since 0.7.2
 * @global bbdb $bbdb
 *
 * @param string $key
 * @return unknown
 */
function bb_reset_password( $key )
{
	global $bbdb;

	$key = sanitize_user( $key, true );

	if ( empty( $key ) || !is_string( $key ) ) {
		return new WP_Error( 'invalid_key', __( 'Invalid key' ) );
	}

	if ( !$user_id = $bbdb->get_var( $bbdb->prepare( "SELECT user_id FROM $bbdb->usermeta WHERE meta_key = 'newpwdkey' AND meta_value = %s", $key ) ) ) {
		return new WP_Error( 'invalid_key', __( 'Invalid key' ) );
	}

	$user = new BP_User( $user_id );

	if ( !$user || is_wp_error( $user ) ) {
		return new WP_Error( 'invalid_key', __( 'Invalid key' ) );
	}

	if ( bb_has_broken_pass( $user->ID ) ) {
		bb_block_current_user();
	}

	if ( !$user->has_cap( 'change_user_password', $user->ID ) ) {
		return new WP_Error( 'permission_denied', __( 'You are not allowed to change your password.' ) );
	}

	$newpass = bb_generate_password();
	bb_update_user_password( $user->ID, $newpass );
	if ( !bb_send_pass( $user->ID, $newpass ) ) {
		return new WP_Error( 'sending_mail_failed', __( 'The email containing the new password could not be sent.' ) );
	}

	bb_update_usermeta( $user->ID, 'newpwdkey', '' );
	return true;
}

/**
 * Updates a user's password in the database
 *
 * {@internal Missing Long Description}}
 *
 * @since 0.7.2
 * @global bbdb $bbdb
 *
 * @param int $user_id
 * @param string $password
 * @return int
 */
function bb_update_user_password( $user_id, $password ) {
	global $wp_users_object;

	$user_id = (int) $user_id;

	$wp_users_object->set_password( $password, $user_id );

	do_action('bb_update_user_password', $user_id);
	return $user_id;
}

/**
 * Sends an email with the user's new password
 *
 * {@internal Missing Long Description}}
 *
 * @since 0.7.2
 * @global bbdb $bbdb {@internal Not used}}
 *
 * @param int|string $user
 * @param string $pass
 * @return bool
 */
function bb_send_pass( $user, $pass )
{
	if ( !$user = bb_get_user( $user ) ) {
		return false;
	}

	$message = sprintf(
		__( "Your username is: %1\$s \nYour password is: %2\$s \nYou can now log in: %3\$s \n\nEnjoy!" ),
		$user->user_login,
		$pass,
		bb_get_uri( null, null, BB_URI_CONTEXT_TEXT )
	);
	$message = apply_filters( 'bb_send_pass_message', $message, $user, $pass );

	$subject = sprintf(
		__( '%s: Password' ),
		bb_get_option( 'name' )
	);
	$subject = apply_filters( 'bb_send_pass_subject', $subject, $user );

	return bb_mail(
		bb_get_user_email( $user->ID ),
		$subject,
		$message
	);
}



/* Favorites */

function get_user_favorites( $user_id, $topics = false ) {
	$user = bb_get_user( $user_id );
	if ( !empty($user->favorites) ) {
		if ( $topics )
			$query = new BB_Query( 'topic', array('favorites' => $user_id, 'index_hint' => 'USE INDEX (`forum_time`)'), 'get_user_favorites' );
		else
			$query = new BB_Query( 'post', array('favorites' => $user_id), 'get_user_favorites' );
		return $query->results;
	}
}

function is_user_favorite( $user_id = 0, $topic_id = 0 ) {
	if ( $user_id )
		$user = bb_get_user( $user_id );
	else
	 	global $user;
	if ( $topic_id )
		$topic = get_topic( $topic_id );
	else
		global $topic;
	if ( !$user || !$topic )
		return;

	if ( isset($user->favorites) )
	        return in_array($topic->topic_id, explode(',', $user->favorites));
	return false;
}

function bb_add_user_favorite( $user_id, $topic_id ) {
	global $bbdb;
	$user_id = (int) $user_id;
	$topic_id = (int) $topic_id;
	$user = bb_get_user( $user_id );
	$topic = get_topic( $topic_id );
	if ( !$user || !$topic )
		return false;

	$favorites_key = $bbdb->prefix . 'favorites';
	$fav = $user->$favorites_key ? explode(',', $user->$favorites_key) : array();
	if ( ! in_array( $topic_id, $fav ) ) {
		$fav[] = $topic_id;
		$fav = implode(',', $fav);
		bb_update_usermeta( $user->ID, $favorites_key, $fav );
	}
	do_action('bb_add_user_favorite', $user_id, $topic_id);
	return true;
}

function bb_remove_user_favorite( $user_id, $topic_id ) {
	global $bbdb;
	$user_id = (int) $user_id;
	$topic_id = (int) $topic_id;
	$user = bb_get_user( $user_id );
	if ( !$user )
		return false;

	$favorites_key = $bbdb->prefix . 'favorites';
	$fav = explode(',', $user->$favorites_key);
	if ( is_int( $pos = array_search($topic_id, $fav) ) ) {
		array_splice($fav, $pos, 1);
		$fav = implode(',', $fav);
		bb_update_usermeta( $user->ID, $favorites_key, $fav);
	}
	do_action('bb_remove_user_favorite', $user_id, $topic_id);
	return true;
}
