<?php
/**
 * BuddyPress Groups Actions.
 *
 * Action functions are exactly the same as screen functions, however they do
 * not have a template screen associated with them. Usually they will send the
 * user back to the default screen after execution.
 *
 * @package BuddyPress
 * @subpackage GroupsActions
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Protect access to single groups.
 *
 * @since 2.1.0
 */
function bp_groups_group_access_protection() {
	if ( ! bp_is_group() ) {
		return;
	}

	$current_group   = groups_get_current_group();
	$user_has_access = $current_group->user_has_access;
	$no_access_args  = array();

	if ( ! $user_has_access && 'hidden' !== $current_group->status ) {
		// Always allow access to home and request-membership
		if ( bp_is_current_action( 'home' ) || bp_is_current_action( 'request-membership' ) ) {
			$user_has_access = true;

		// User doesn't have access, so set up redirect args
		} elseif ( is_user_logged_in() ) {
			$no_access_args = array(
				'message'  => __( 'You do not have access to this group.', 'buddypress' ),
				'root'     => bp_get_group_permalink( $current_group ) . 'home/',
				'redirect' => false
			);
		}
	}

	// Protect the admin tab from non-admins
	if ( bp_is_current_action( 'admin' ) && ! bp_is_item_admin() ) {
		$user_has_access = false;
		$no_access_args  = array(
			'message'  => __( 'You are not an admin of this group.', 'buddypress' ),
			'root'     => bp_get_group_permalink( $current_group ),
			'redirect' => false
		);
	}

	/**
	 * Allow plugins to filter whether the current user has access to this group content.
	 *
	 * Note that if a plugin sets $user_has_access to false, it may also
	 * want to change the $no_access_args, to avoid problems such as
	 * logged-in users being redirected to wp-login.php.
	 *
	 * @since 2.1.0
	 *
	 * @param bool  $user_has_access True if the user has access to the
	 *                               content, otherwise false.
	 * @param array $no_access_args  Arguments to be passed to bp_core_no_access() in case
	 *                               of no access. Note that this value is passed by reference,
	 *                               so it can be modified by the filter callback.
	 */
	$user_has_access = apply_filters_ref_array( 'bp_group_user_has_access', array( $user_has_access, &$no_access_args ) );

	// If user has access, we return rather than redirect.
	if ( $user_has_access ) {
		return;
	}

	// Hidden groups should return a 404 for non-members.
	// Unset the current group so that you're not redirected
	// to the default group tab.
	if ( 'hidden' == $current_group->status ) {
		buddypress()->groups->current_group = 0;
		buddypress()->is_single_item        = false;
		bp_do_404();
		return;
	} else {
		bp_core_no_access( $no_access_args );
	}

}
add_action( 'bp_actions', 'bp_groups_group_access_protection' );

/**
 * Catch and process group creation form submissions.
 */
function groups_action_create_group() {

	// If we're not at domain.org/groups/create/ then return false
	if ( !bp_is_groups_component() || !bp_is_current_action( 'create' ) )
		return false;

	if ( !is_user_logged_in() )
		return false;

 	if ( !bp_user_can_create_groups() ) {
		bp_core_add_message( __( 'Sorry, you are not allowed to create groups.', 'buddypress' ), 'error' );
		bp_core_redirect( bp_get_groups_directory_permalink() );
	}

	$bp = buddypress();

	// Make sure creation steps are in the right order
	groups_action_sort_creation_steps();

	// If no current step is set, reset everything so we can start a fresh group creation
	$bp->groups->current_create_step = bp_action_variable( 1 );
	if ( !bp_get_groups_current_create_step() ) {
		unset( $bp->groups->current_create_step );
		unset( $bp->groups->completed_create_steps );

		setcookie( 'bp_new_group_id', false, time() - 1000, COOKIEPATH );
		setcookie( 'bp_completed_create_steps', false, time() - 1000, COOKIEPATH );

		$reset_steps = true;
		$keys        = array_keys( $bp->groups->group_creation_steps );
		bp_core_redirect( trailingslashit( bp_get_groups_directory_permalink() . 'create/step/' . array_shift( $keys ) ) );
	}

	// If this is a creation step that is not recognized, just redirect them back to the first screen
	if ( bp_get_groups_current_create_step() && empty( $bp->groups->group_creation_steps[bp_get_groups_current_create_step()] ) ) {
		bp_core_add_message( __('There was an error saving group details. Please try again.', 'buddypress'), 'error' );
		bp_core_redirect( trailingslashit( bp_get_groups_directory_permalink() . 'create' ) );
	}

	// Fetch the currently completed steps variable
	if ( isset( $_COOKIE['bp_completed_create_steps'] ) && !isset( $reset_steps ) )
		$bp->groups->completed_create_steps = json_decode( base64_decode( stripslashes( $_COOKIE['bp_completed_create_steps'] ) ) );

	// Set the ID of the new group, if it has already been created in a previous step
	if ( isset( $_COOKIE['bp_new_group_id'] ) ) {
		$bp->groups->new_group_id = (int) $_COOKIE['bp_new_group_id'];
		$bp->groups->current_group = groups_get_group( array( 'group_id' => $bp->groups->new_group_id ) );

		// Only allow the group creator to continue to edit the new group
		if ( ! bp_is_group_creator( $bp->groups->current_group, bp_loggedin_user_id() ) ) {
			bp_core_add_message( __( 'Only the group creator may continue editing this group.', 'buddypress' ), 'error' );
			bp_core_redirect( trailingslashit( bp_get_groups_directory_permalink() . 'create' ) );
		}
	}

	// If the save, upload or skip button is hit, lets calculate what we need to save
	if ( isset( $_POST['save'] ) ) {

		// Check the nonce
		check_admin_referer( 'groups_create_save_' . bp_get_groups_current_create_step() );

		if ( 'group-details' == bp_get_groups_current_create_step() ) {
			if ( empty( $_POST['group-name'] ) || empty( $_POST['group-desc'] ) || !strlen( trim( $_POST['group-name'] ) ) || !strlen( trim( $_POST['group-desc'] ) ) ) {
				bp_core_add_message( __( 'Please fill in all of the required fields', 'buddypress' ), 'error' );
				bp_core_redirect( trailingslashit( bp_get_groups_directory_permalink() . 'create/step/' . bp_get_groups_current_create_step() ) );
			}

			$new_group_id = isset( $bp->groups->new_group_id ) ? $bp->groups->new_group_id : 0;

			if ( !$bp->groups->new_group_id = groups_create_group( array( 'group_id' => $new_group_id, 'name' => $_POST['group-name'], 'description' => $_POST['group-desc'], 'slug' => groups_check_slug( sanitize_title( esc_attr( $_POST['group-name'] ) ) ), 'date_created' => bp_core_current_time(), 'status' => 'public' ) ) ) {
				bp_core_add_message( __( 'There was an error saving group details. Please try again.', 'buddypress' ), 'error' );
				bp_core_redirect( trailingslashit( bp_get_groups_directory_permalink() . 'create/step/' . bp_get_groups_current_create_step() ) );
			}
		}

		if ( 'group-settings' == bp_get_groups_current_create_step() ) {
			$group_status = 'public';
			$group_enable_forum = 1;

			if ( !isset($_POST['group-show-forum']) ) {
				$group_enable_forum = 0;
			} else {
				// Create the forum if enable_forum = 1
				if ( bp_is_active( 'forums' ) && !groups_get_groupmeta( $bp->groups->new_group_id, 'forum_id' ) ) {
					groups_new_group_forum();
				}
			}

			if ( 'private' == $_POST['group-status'] )
				$group_status = 'private';
			elseif ( 'hidden' == $_POST['group-status'] )
				$group_status = 'hidden';

			if ( !$bp->groups->new_group_id = groups_create_group( array( 'group_id' => $bp->groups->new_group_id, 'status' => $group_status, 'enable_forum' => $group_enable_forum ) ) ) {
				bp_core_add_message( __( 'There was an error saving group details. Please try again.', 'buddypress' ), 'error' );
				bp_core_redirect( trailingslashit( bp_get_groups_directory_permalink() . 'create/step/' . bp_get_groups_current_create_step() ) );
			}

			/**
			 * Filters the allowed invite statuses.
			 *
			 * @since 1.5.0
			 *
			 * @param array $value Array of statuses allowed.
			 *                     Possible values are 'members,
			 *                     'mods', and 'admins'.
			 */
			$allowed_invite_status = apply_filters( 'groups_allowed_invite_status', array( 'members', 'mods', 'admins' ) );
			$invite_status	       = !empty( $_POST['group-invite-status'] ) && in_array( $_POST['group-invite-status'], (array) $allowed_invite_status ) ? $_POST['group-invite-status'] : 'members';

			groups_update_groupmeta( $bp->groups->new_group_id, 'invite_status', $invite_status );
		}

		if ( 'group-invites' === bp_get_groups_current_create_step() ) {
			if ( ! empty( $_POST['friends'] ) ) {
				foreach ( (array) $_POST['friends'] as $friend ) {
					groups_invite_user( array(
						'user_id'  => (int) $friend,
						'group_id' => $bp->groups->new_group_id,
					) );
				}
			}

			groups_send_invites( bp_loggedin_user_id(), $bp->groups->new_group_id );
		}

		/**
		 * Fires before finalization of group creation and cookies are set.
		 *
		 * This hook is a variable hook dependent on the current step
		 * in the creation process.
		 *
		 * @since 1.1.0
		 */
		do_action( 'groups_create_group_step_save_' . bp_get_groups_current_create_step() );

		/**
		 * Fires after the group creation step is completed.
		 *
		 * Mostly for clearing cache on a generic action name.
		 *
		 * @since 1.1.0
		 */
		do_action( 'groups_create_group_step_complete' );

		/**
		 * Once we have successfully saved the details for this step of the creation process
		 * we need to add the current step to the array of completed steps, then update the cookies
		 * holding the information
		 */
		$completed_create_steps = isset( $bp->groups->completed_create_steps ) ? $bp->groups->completed_create_steps : array();
		if ( !in_array( bp_get_groups_current_create_step(), $completed_create_steps ) )
			$bp->groups->completed_create_steps[] = bp_get_groups_current_create_step();

		// Reset cookie info
		setcookie( 'bp_new_group_id', $bp->groups->new_group_id, time()+60*60*24, COOKIEPATH );
		setcookie( 'bp_completed_create_steps', base64_encode( json_encode( $bp->groups->completed_create_steps ) ), time()+60*60*24, COOKIEPATH );

		// If we have completed all steps and hit done on the final step we
		// can redirect to the completed group
		$keys = array_keys( $bp->groups->group_creation_steps );
		if ( count( $bp->groups->completed_create_steps ) == count( $keys ) && bp_get_groups_current_create_step() == array_pop( $keys ) ) {
			unset( $bp->groups->current_create_step );
			unset( $bp->groups->completed_create_steps );

			setcookie( 'bp_new_group_id', false, time() - 3600, COOKIEPATH );
			setcookie( 'bp_completed_create_steps', false, time() - 3600, COOKIEPATH );

			// Once we completed all steps, record the group creation in the activity stream.
			groups_record_activity( array(
				'type' => 'created_group',
				'item_id' => $bp->groups->new_group_id
			) );

			/**
			 * Fires after the group has been successfully created.
			 *
			 * @since 1.1.0
			 *
			 * @param int $new_group_id ID of the newly created group.
			 */
			do_action( 'groups_group_create_complete', $bp->groups->new_group_id );

			bp_core_redirect( bp_get_group_permalink( $bp->groups->current_group ) );
		} else {
			/**
			 * Since we don't know what the next step is going to be (any plugin can insert steps)
			 * we need to loop the step array and fetch the next step that way.
			 */
			foreach ( $keys as $key ) {
				if ( $key == bp_get_groups_current_create_step() ) {
					$next = 1;
					continue;
				}

				if ( isset( $next ) ) {
					$next_step = $key;
					break;
				}
			}

			bp_core_redirect( trailingslashit( bp_get_groups_directory_permalink() . 'create/step/' . $next_step ) );
		}
	}

	// Remove invitations
	if ( 'group-invites' === bp_get_groups_current_create_step() && ! empty( $_REQUEST['user_id'] ) && is_numeric( $_REQUEST['user_id'] ) ) {
		if ( ! check_admin_referer( 'groups_invite_uninvite_user' ) ) {
			return false;
		}

		$message = __( 'Invite successfully removed', 'buddypress' );
		$error   = false;

		if( ! groups_uninvite_user( (int) $_REQUEST['user_id'], $bp->groups->new_group_id ) ) {
			$message = __( 'There was an error removing the invite', 'buddypress' );
			$error   = 'error';
		}

		bp_core_add_message( $message, $error );
		bp_core_redirect( trailingslashit( bp_get_groups_directory_permalink() . 'create/step/group-invites' ) );
	}

	// Group avatar is handled separately
	if ( 'group-avatar' == bp_get_groups_current_create_step() && isset( $_POST['upload'] ) ) {
		if ( ! isset( $bp->avatar_admin ) ) {
			$bp->avatar_admin = new stdClass();
		}

		if ( !empty( $_FILES ) && isset( $_POST['upload'] ) ) {
			// Normally we would check a nonce here, but the group save nonce is used instead

			// Pass the file to the avatar upload handler
			if ( bp_core_avatar_handle_upload( $_FILES, 'groups_avatar_upload_dir' ) ) {
				$bp->avatar_admin->step = 'crop-image';

				// Make sure we include the jQuery jCrop file for image cropping
				add_action( 'wp_print_scripts', 'bp_core_add_jquery_cropper' );
			}
		}

		// If the image cropping is done, crop the image and save a full/thumb version
		if ( isset( $_POST['avatar-crop-submit'] ) && isset( $_POST['upload'] ) ) {
			// Normally we would check a nonce here, but the group save nonce is used instead

			if ( !bp_core_avatar_handle_crop( array( 'object' => 'group', 'avatar_dir' => 'group-avatars', 'item_id' => $bp->groups->current_group->id, 'original_file' => $_POST['image_src'], 'crop_x' => $_POST['x'], 'crop_y' => $_POST['y'], 'crop_w' => $_POST['w'], 'crop_h' => $_POST['h'] ) ) )
				bp_core_add_message( __( 'There was an error saving the group profile photo, please try uploading again.', 'buddypress' ), 'error' );
			else
				bp_core_add_message( __( 'The group profile photo was uploaded successfully.', 'buddypress' ) );
		}
	}

	/**
	 * Filters the template to load for the group creation screen.
	 *
	 * @since 1.0.0
	 *
	 * @param string $value Path to the group creation template to load.
	 */
	bp_core_load_template( apply_filters( 'groups_template_create_group', 'groups/create' ) );
}
add_action( 'bp_actions', 'groups_action_create_group' );

/**
 * Catch and process "Join Group" button clicks.
 */
function groups_action_join_group() {

	if ( !bp_is_single_item() || !bp_is_groups_component() || !bp_is_current_action( 'join' ) )
		return false;

	// Nonce check
	if ( !check_admin_referer( 'groups_join_group' ) )
		return false;

	$bp = buddypress();

	// Skip if banned or already a member
	if ( !groups_is_user_member( bp_loggedin_user_id(), $bp->groups->current_group->id ) && !groups_is_user_banned( bp_loggedin_user_id(), $bp->groups->current_group->id ) ) {

		// User wants to join a group that is not public
		if ( $bp->groups->current_group->status != 'public' ) {
			if ( !groups_check_user_has_invite( bp_loggedin_user_id(), $bp->groups->current_group->id ) ) {
				bp_core_add_message( __( 'There was an error joining the group.', 'buddypress' ), 'error' );
				bp_core_redirect( bp_get_group_permalink( $bp->groups->current_group ) );
			}
		}

		// User wants to join any group
		if ( !groups_join_group( $bp->groups->current_group->id ) )
			bp_core_add_message( __( 'There was an error joining the group.', 'buddypress' ), 'error' );
		else
			bp_core_add_message( __( 'You joined the group!', 'buddypress' ) );

		bp_core_redirect( bp_get_group_permalink( $bp->groups->current_group ) );
	}

	/**
	 * Filters the template to load for the single group screen.
	 *
	 * @since 1.0.0
	 *
	 * @param string $value Path to the single group template to load.
	 */
	bp_core_load_template( apply_filters( 'groups_template_group_home', 'groups/single/home' ) );
}
add_action( 'bp_actions', 'groups_action_join_group' );

/**
 * Catch and process "Leave Group" button clicks.
 *
 * When a group member clicks on the "Leave Group" button from a group's page,
 * this function is run.
 *
 * Note: When leaving a group from the group directory, AJAX is used and
 * another function handles this. See {@link bp_legacy_theme_ajax_joinleave_group()}.
 *
 * @since 1.2.4
 */
function groups_action_leave_group() {
	if ( ! bp_is_single_item() || ! bp_is_groups_component() || ! bp_is_current_action( 'leave-group' ) ) {
		return false;
	}

	// Nonce check
	if ( ! check_admin_referer( 'groups_leave_group' ) ) {
		return false;
	}

	// User wants to leave any group
	if ( groups_is_user_member( bp_loggedin_user_id(), bp_get_current_group_id() ) ) {
		$bp = buddypress();

		// Stop sole admins from abandoning their group
		$group_admins = groups_get_group_admins( bp_get_current_group_id() );

		if ( 1 == count( $group_admins ) && $group_admins[0]->user_id == bp_loggedin_user_id() ) {
			bp_core_add_message( __( 'This group must have at least one admin', 'buddypress' ), 'error' );
		} elseif ( ! groups_leave_group( $bp->groups->current_group->id ) ) {
			bp_core_add_message( __( 'There was an error leaving the group.', 'buddypress' ), 'error' );
		} else {
			bp_core_add_message( __( 'You successfully left the group.', 'buddypress' ) );
		}

		$redirect = bp_get_group_permalink( groups_get_current_group() );

		if( 'hidden' == $bp->groups->current_group->status ) {
			$redirect = trailingslashit( bp_loggedin_user_domain() . bp_get_groups_slug() );
		}

		bp_core_redirect( $redirect );
	}

	/** This filter is documented in bp-groups/bp-groups-actions.php */
	bp_core_load_template( apply_filters( 'groups_template_group_home', 'groups/single/home' ) );
}
add_action( 'bp_actions', 'groups_action_leave_group' );

/**
 * Sort the group creation steps.
 *
 * @return false|null False on failure.
 */
function groups_action_sort_creation_steps() {

	if ( !bp_is_groups_component() || !bp_is_current_action( 'create' ) )
		return false;

	$bp = buddypress();

	if ( !is_array( $bp->groups->group_creation_steps ) )
		return false;

	foreach ( (array) $bp->groups->group_creation_steps as $slug => $step ) {
		while ( !empty( $temp[$step['position']] ) )
			$step['position']++;

		$temp[$step['position']] = array( 'name' => $step['name'], 'slug' => $slug );
	}

	// Sort the steps by their position key
	ksort($temp);
	unset($bp->groups->group_creation_steps);

	foreach( (array) $temp as $position => $step )
		$bp->groups->group_creation_steps[$step['slug']] = array( 'name' => $step['name'], 'position' => $position );

	/**
	 * Fires after group creation sets have been sorted.
	 *
	 * @since 2.3.0
	 */
	do_action( 'groups_action_sort_creation_steps' );
}

/**
 * Catch requests for a random group page (example.com/groups/?random-group) and redirect.
 */
function groups_action_redirect_to_random_group() {

	if ( bp_is_groups_component() && isset( $_GET['random-group'] ) ) {
		$group = BP_Groups_Group::get_random( 1, 1 );

		bp_core_redirect( trailingslashit( bp_get_groups_directory_permalink() . $group['groups'][0]->slug ) );
	}
}
add_action( 'bp_actions', 'groups_action_redirect_to_random_group' );

/**
 * Load the activity feed for the current group.
 *
 * @since 1.2.0
 *
 * @return false|null False on failure.
 */
function groups_action_group_feed() {

	// get current group
	$group = groups_get_current_group();

	if ( ! bp_is_active( 'activity' ) || ! bp_is_groups_component() || ! $group || ! bp_is_current_action( 'feed' ) )
		return false;

	// if group isn't public or if logged-in user is not a member of the group, do
	// not output the group activity feed
	if ( ! bp_group_is_visible( $group ) ) {
		return false;
	}

	// setup the feed
	buddypress()->activity->feed = new BP_Activity_Feed( array(
		'id'            => 'group',

		/* translators: Group activity RSS title - "[Site Name] | [Group Name] | Activity" */
		'title'         => sprintf( __( '%1$s | %2$s | Activity', 'buddypress' ), bp_get_site_name(), bp_get_current_group_name() ),

		'link'          => bp_get_group_permalink( $group ),
		'description'   => sprintf( __( "Activity feed for the group, %s.", 'buddypress' ), bp_get_current_group_name() ),
		'activity_args' => array(
			'object'           => buddypress()->groups->id,
			'primary_id'       => bp_get_current_group_id(),
			'display_comments' => 'threaded'
		)
	) );
}
add_action( 'bp_actions', 'groups_action_group_feed' );
