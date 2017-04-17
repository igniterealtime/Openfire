<?php
require_once( './bb-load.php' );

// Redirect if we require SSL and it isn't
bb_ssl_redirect();

// Authenticate against the "logged_in" cookie
bb_auth( 'logged_in' );

// Check that the current user can do this, if not kick them to the front page
if ( !bb_current_user_can( 'edit_user', $user_id ) ) {
	$sendto = bb_get_uri( null, null, BB_URI_CONTEXT_HEADER );
	wp_redirect( $sendto );
	exit;
}

// Store the current user id
$bb_current_id = bb_get_current_user_info( 'id' );

// I don't know how this would ever get triggered
if ( !bb_is_profile() ) {
	$sendto = get_profile_tab_link( $bb_current_id, 'edit' );
	wp_redirect( $sendto );
	exit;
}

// Set some low capabilities if the current user has none
if ( !isset( $user->capabilities ) ) {
	$user->capabilities = array( 'inactive' => true );
}

// Store the profile info keys
$profile_info_keys = bb_get_profile_info_keys( 'profile-edit' );

// Store additional keys if the current user has access to them
if ( bb_current_user_can('edit_users') ) {
	$profile_admin_keys = bb_get_profile_admin_keys( 'profile-edit' );
	$assignable_caps = bb_get_assignable_caps();
}

// Instantiate the error object
$errors = new WP_Error;

if ( 'post' == strtolower($_SERVER['REQUEST_METHOD']) ) {
	$_POST = stripslashes_deep( $_POST );
	bb_check_admin_referer( 'edit-profile_' . $user_id );

	// Fix the URL before sanitizing it
	$user_url = bb_fix_link( $_POST['user_url'] );

	// Sanitize the profile info keys and check for missing required data
	foreach ( $profile_info_keys as $key => $label ) {
		$$key = apply_filters( 'sanitize_profile_info', $_POST[$key], $key, $_POST[$key] );
		if ( !$$key && $label[0] == 1 ) {
			$errors->add( $key, sprintf( __( '%s is required.' ), esc_html( $label[1] ) ) );
			$$key = false;
		}
	}

	// Find out if we have a valid email address
	if ( isset( $user_email ) && !$user_email = is_email( $user_email ) ) {
		$errors->add( 'user_email', __( 'Invalid email address' ), array( 'data' => $_POST['user_email'] ) );
	}

	// Deal with errors for users who can edit others data
	if ( bb_current_user_can('edit_users') ) {
		// Get the user object
		$user_obj = new BP_User( $user->ID );
		
		// If we are deleting just do it and redirect
		if ( isset( $_POST['delete-user'] ) && $_POST['delete-user'] && $bb_current_id != $user->ID ) {
			if ( !bb_current_user_can( 'keep_gate' ) && 'keymaster' == $user_obj->roles[0] ) { /* Only a keymaster can delete another keymaster */
				$errors->add( 'delete', __( 'You can not delete this user!' ) );
			} else {
				bb_delete_user( $user->ID );
				wp_redirect( bb_get_uri(null, null, BB_URI_CONTEXT_HEADER) );
				exit;
			}
		}

		// Store the new role
		$role = $_POST['role'];

		// Deal with errors with the role
		if ( !isset($wp_roles->role_objects[$role]) ) {
			$errors->add( 'role', __( 'Invalid Role' ) );
		} elseif ( !bb_current_user_can( 'keep_gate' ) && ( 'keymaster' == $role || 'keymaster' == $user_obj->roles[0] ) ) {
			$errors->add( 'role', __( 'You are not the Gate Keeper.' ) );
		} elseif ( 'keymaster' == $user_obj->roles[0] && 'keymaster' != $role && $bb_current_id == $user->ID ) {
			$errors->add( 'role', __( 'You are Keymaster, so you may not demote yourself.' ) );
		}

		// Sanitize the profile admin keys and check for missing required data
		foreach ( $profile_admin_keys as $key => $label ) {
			if ( isset( $$key ) )
				continue;

			$$key = apply_filters( 'sanitize_profile_admin', $_POST[$key], $key, $_POST[$key] );
			if ( !$$key && $label[0] == 1 ) {
				$errors->add( $key, sprintf( __( '%s is required.' ), esc_html( $label[1] ) ) );
				$$key = false;
			}
		}

		// Create variable for the requested roles
		foreach ( $assignable_caps as $cap => $label ) {
			if ( isset($$cap) )
				continue;

			$$cap = ( isset($_POST[$cap]) && $_POST[$cap] ) ? 1 : 0;
		}
	}

	// Deal with errors generated from the password form
	if ( bb_current_user_can( 'change_user_password', $user->ID ) ) {
		if ( ( !empty($_POST['pass1']) || !empty($_POST['pass2']) ) && $_POST['pass1'] !== $_POST['pass2'] ) {
			$errors->add( 'pass', __( 'You must enter the same password twice.' ) );
		} elseif( !empty($_POST['pass1']) && !bb_current_user_can( 'change_user_password', $user->ID ) ) {
			$errors->add( 'pass', __( "You are not allowed to change this user's password." ) );
		}
	}

	// If there are no errors then update the records
	if ( !$errors->get_error_codes() ) {
		do_action('before_profile_edited', $user->ID);
		
		if ( bb_current_user_can( 'edit_user', $user->ID ) ) {
			// All these are always set at this point
			bb_update_user( $user->ID, $user_email, $user_url, $display_name );

			// Add user meta data
			foreach( $profile_info_keys as $key => $label ) {
				if ( 'display_name' == $key || 'ID' == $key || strpos($key, 'user_') === 0 )
					continue;
				if ( $$key != '' || isset($user->$key) )
					bb_update_usermeta( $user->ID, $key, $$key );
			}
		}

		if ( bb_current_user_can( 'edit_users' ) ) {
			if ( !array_key_exists($role, $user->capabilities) ) {
				$user_obj->set_role($role); // Only support one role for now
				if ( 'blocked' == $role && 'blocked' != $old_role )
					bb_break_password( $user->ID );
				elseif ( 'blocked' != $role && array_key_exists( 'blocked', $user->capabilities ) )
					bb_fix_password( $user->ID );
			}
			foreach( $profile_admin_keys as $key => $label )
				if ( $$key != ''  || isset($user->$key) )
					bb_update_usermeta( $user->ID, $key, $$key );
			foreach( $assignable_caps as $cap => $label ) {
				if ( ( !$already = array_key_exists($cap, $user->capabilities) ) && $$cap) {
					$user_obj->add_cap($cap);
				} elseif ( !$$cap && $already ) {
					$user_obj->remove_cap($cap);
				}
			}
		}

		if ( bb_current_user_can( 'change_user_password', $user->ID ) && !empty($_POST['pass1']) ) {
			$_POST['pass1'] = addslashes($_POST['pass1']);
			bb_update_user_password( $user->ID, $_POST['pass1'] );

			if ( bb_get_current_user_info( 'ID' ) == $user->ID ) {
				bb_clear_auth_cookie();
				bb_set_auth_cookie( $user->ID );
			}
		}
		
		do_action('profile_edited', $user->ID);

		wp_redirect( add_query_arg( 'updated', 'true', get_user_profile_link( $user->ID ) ) );
		exit;
	}
}

bb_load_template( 'profile-edit.php', array('profile_info_keys', 'profile_admin_keys', 'assignable_caps', 'user_email', 'bb_roles', 'errors', 'self') );

?>
