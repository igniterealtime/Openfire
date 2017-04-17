<?php
/**
 * BuddyPress Groups Screen Functions
 *
 * Screen functions are the controllers of BuddyPress. They will execute when
 * their specific URL is caught. They will first save or manipulate data using
 * business functions, then pass on the user to a template file.
 *
 * @package BuddyPress
 * @subpackage GroupsScreens
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Handle the display of the Groups directory index.
 */
function groups_directory_groups_setup() {
	if ( bp_is_groups_directory() ) {
		bp_update_is_directory( true, 'groups' );

		/**
		 * Fires before the loading of the Groups directory index.
		 *
		 * @since 1.1.0
		 */
		do_action( 'groups_directory_groups_setup' );

		/**
		 * Filters the template to load for the Groups directory index.
		 *
		 * @since 1.0.0
		 *
		 * @param string $value Path to the groups directory index template to load.
		 */
		bp_core_load_template( apply_filters( 'groups_template_directory_groups', 'groups/index' ) );
	}
}
add_action( 'bp_screens', 'groups_directory_groups_setup', 2 );

/**
 * Handle the loading of the My Groups page.
 */
function groups_screen_my_groups() {

	/**
	 * Fires before the loading of the My Groups page.
	 *
	 * @since 1.1.0
	 */
	do_action( 'groups_screen_my_groups' );

	/**
	 * Filters the template to load for the My Groups page.
	 *
	 * @since 1.0.0
	 *
	 * @param string $value Path to the My Groups page template to load.
	 */
	bp_core_load_template( apply_filters( 'groups_template_my_groups', 'members/single/home' ) );
}

/**
 * Handle the loading of a user's Groups > Invites page.
 */
function groups_screen_group_invites() {
	$group_id = (int)bp_action_variable( 1 );

	if ( bp_is_action_variable( 'accept' ) && is_numeric( $group_id ) ) {
		// Check the nonce
		if ( !check_admin_referer( 'groups_accept_invite' ) )
			return false;

		if ( !groups_accept_invite( bp_loggedin_user_id(), $group_id ) ) {
			bp_core_add_message( __('Group invite could not be accepted', 'buddypress'), 'error' );
		} else {
			bp_core_add_message( __('Group invite accepted', 'buddypress') );

			// Record this in activity streams
			$group = groups_get_group( array( 'group_id' => $group_id ) );

			groups_record_activity( array(
				'type'    => 'joined_group',
				'item_id' => $group->id
			) );
		}

		if ( isset( $_GET['redirect_to'] ) ) {
			$redirect_to = urldecode( $_GET['redirect_to'] );
		} else {
			$redirect_to = trailingslashit( bp_loggedin_user_domain() . bp_get_groups_slug() . '/' . bp_current_action() );
		}

		bp_core_redirect( $redirect_to );

	} elseif ( bp_is_action_variable( 'reject' ) && is_numeric( $group_id ) ) {
		// Check the nonce
		if ( !check_admin_referer( 'groups_reject_invite' ) )
			return false;

		if ( !groups_reject_invite( bp_loggedin_user_id(), $group_id ) ) {
			bp_core_add_message( __( 'Group invite could not be rejected', 'buddypress' ), 'error' );
		} else {
			bp_core_add_message( __( 'Group invite rejected', 'buddypress' ) );
		}

		if ( isset( $_GET['redirect_to'] ) ) {
			$redirect_to = urldecode( $_GET['redirect_to'] );
		} else {
			$redirect_to = trailingslashit( bp_loggedin_user_domain() . bp_get_groups_slug() . '/' . bp_current_action() );
		}

		bp_core_redirect( $redirect_to );
	}

	/**
	 * Fires before the loading of a users Groups > Invites template.
	 *
	 * @since 1.0.0
	 *
	 * @param int $group_id ID of the group being displayed
	 */
	do_action( 'groups_screen_group_invites', $group_id );

	/**
	 * Filters the template to load for a users Groups > Invites page.
	 *
	 * @since 1.0.0
	 *
	 * @param string $value Path to a users Groups > Invites page template.
	 */
	bp_core_load_template( apply_filters( 'groups_template_group_invites', 'members/single/home' ) );
}

/**
 * Handle the loading of a single group's page.
 */
function groups_screen_group_home() {

	if ( ! bp_is_single_item() ) {
		return false;
	}

	/**
	 * Fires before the loading of a single group's page.
	 *
	 * @since 1.0.0
	 */
	do_action( 'groups_screen_group_home' );

	/**
	 * Filters the template to load for a single group's page.
	 *
	 * @since 1.0.0
	 *
	 * @param string $value Path to a single group's template to load.
	 */
	bp_core_load_template( apply_filters( 'groups_template_group_home', 'groups/single/home' ) );
}

/**
 * This screen function handles actions related to group forums.
 */
function groups_screen_group_forum() {

	if ( !bp_is_active( 'forums' ) || !bp_forums_is_installed_correctly() )
		return false;

	if ( bp_action_variable( 0 ) && !bp_is_action_variable( 'topic', 0 ) ) {
		bp_do_404();
		return;
	}

	$bp = buddypress();

	if ( !$bp->groups->current_group->user_has_access ) {
		bp_core_no_access();
		return;
	}

	if ( ! bp_is_single_item() )
		return false;

	// Fetch the details we need
	$topic_slug	= (string)bp_action_variable( 1 );
	$topic_id       = bp_forums_get_topic_id_from_slug( $topic_slug );
	$forum_id       = groups_get_groupmeta( $bp->groups->current_group->id, 'forum_id' );
	$user_is_banned = false;

	if ( !bp_current_user_can( 'bp_moderate' ) && groups_is_user_banned( bp_loggedin_user_id(), $bp->groups->current_group->id ) )
		$user_is_banned = true;

	if ( !empty( $topic_slug ) && !empty( $topic_id ) ) {

		// Posting a reply
		if ( !$user_is_banned && !bp_action_variable( 2 ) && isset( $_POST['submit_reply'] ) ) {
			// Check the nonce
			check_admin_referer( 'bp_forums_new_reply' );

			// Auto join this user if they are not yet a member of this group
			if ( bp_groups_auto_join() && !bp_current_user_can( 'bp_moderate' ) && 'public' == $bp->groups->current_group->status && !groups_is_user_member( bp_loggedin_user_id(), $bp->groups->current_group->id ) ) {
				groups_join_group( $bp->groups->current_group->id, bp_loggedin_user_id() );
			}

			$topic_page = isset( $_GET['topic_page'] ) ? $_GET['topic_page'] : false;

			// Don't allow reply flooding
			if ( bp_forums_reply_exists( $_POST['reply_text'], $topic_id, bp_loggedin_user_id() ) ) {
				bp_core_add_message( __( 'It looks like you\'ve already said that!', 'buddypress' ), 'error' );
			} else {
				if ( !$post_id = groups_new_group_forum_post( $_POST['reply_text'], $topic_id, $topic_page ) ) {
					bp_core_add_message( __( 'There was an error when replying to that topic', 'buddypress'), 'error' );
				} else {
					bp_core_add_message( __( 'Your reply was posted successfully', 'buddypress') );
				}
			}

			$query_vars = isset( $_SERVER['QUERY_STRING'] ) ? '?' . $_SERVER['QUERY_STRING'] : '';

			$redirect = bp_get_group_permalink( groups_get_current_group() ) . 'forum/topic/' . $topic_slug . '/' . $query_vars;

			if ( !empty( $post_id ) ) {
				$redirect .= '#post-' . $post_id;
			}

			bp_core_redirect( $redirect );
		}

		// Sticky a topic
		elseif ( bp_is_action_variable( 'stick', 2 ) && ( bp_is_item_admin() || bp_is_item_mod() ) ) {
			// Check the nonce
			check_admin_referer( 'bp_forums_stick_topic' );

			if ( !bp_forums_sticky_topic( array( 'topic_id' => $topic_id ) ) ) {
				bp_core_add_message( __( 'There was an error when making that topic a sticky', 'buddypress' ), 'error' );
			} else {
				bp_core_add_message( __( 'The topic was made sticky successfully', 'buddypress' ) );
			}

			/**
			 * Fires after a group forum topic has been stickied.
			 *
			 * @since 1.1.0
			 *
			 * @param int $topic_id ID of the topic being stickied.
			 */
			do_action( 'groups_stick_forum_topic', $topic_id );
			bp_core_redirect( wp_get_referer() );
		}

		// Un-Sticky a topic
		elseif ( bp_is_action_variable( 'unstick', 2 ) && ( bp_is_item_admin() || bp_is_item_mod() ) ) {
			// Check the nonce
			check_admin_referer( 'bp_forums_unstick_topic' );

			if ( !bp_forums_sticky_topic( array( 'topic_id' => $topic_id, 'mode' => 'unstick' ) ) ) {
				bp_core_add_message( __( 'There was an error when unsticking that topic', 'buddypress'), 'error' );
			} else {
				bp_core_add_message( __( 'The topic was unstuck successfully', 'buddypress') );
			}

			/**
			 * Fires after a group forum topic has been un-stickied.
			 *
			 * @since 1.1.0
			 *
			 * @param int $topic_id ID of the topic being un-stickied.
			 */
			do_action( 'groups_unstick_forum_topic', $topic_id );
			bp_core_redirect( wp_get_referer() );
		}

		// Close a topic
		elseif ( bp_is_action_variable( 'close', 2 ) && ( bp_is_item_admin() || bp_is_item_mod() ) ) {
			// Check the nonce
			check_admin_referer( 'bp_forums_close_topic' );

			if ( !bp_forums_openclose_topic( array( 'topic_id' => $topic_id ) ) ) {
				bp_core_add_message( __( 'There was an error when closing that topic', 'buddypress'), 'error' );
			} else {
				bp_core_add_message( __( 'The topic was closed successfully', 'buddypress') );
			}

			/**
			 * Fires after a group forum topic has been closed.
			 *
			 * @since 1.1.0
			 *
			 * @param int $topic_id ID of the topic being closed.
			 */
			do_action( 'groups_close_forum_topic', $topic_id );
			bp_core_redirect( wp_get_referer() );
		}

		// Open a topic
		elseif ( bp_is_action_variable( 'open', 2 ) && ( bp_is_item_admin() || bp_is_item_mod() ) ) {
			// Check the nonce
			check_admin_referer( 'bp_forums_open_topic' );

			if ( !bp_forums_openclose_topic( array( 'topic_id' => $topic_id, 'mode' => 'open' ) ) ) {
				bp_core_add_message( __( 'There was an error when opening that topic', 'buddypress'), 'error' );
			} else {
				bp_core_add_message( __( 'The topic was opened successfully', 'buddypress') );
			}

			/**
			 * Fires after a group forum topic has been opened.
			 *
			 * @since 1.1.0
			 *
			 * @param int $topic_id ID of the topic being opened.
			 */
			do_action( 'groups_open_forum_topic', $topic_id );
			bp_core_redirect( wp_get_referer() );
		}

		// Delete a topic
		elseif ( empty( $user_is_banned ) && bp_is_action_variable( 'delete', 2 ) && !bp_action_variable( 3 ) ) {
			// Fetch the topic
			$topic = bp_forums_get_topic_details( $topic_id );

			/* Check the logged in user can delete this topic */
			if ( ! bp_is_item_admin() && ! bp_is_item_mod() && ( (int) bp_loggedin_user_id() != (int) $topic->topic_poster ) ) {
				bp_core_redirect( wp_get_referer() );
			}

			// Check the nonce
			check_admin_referer( 'bp_forums_delete_topic' );

			/**
			 * Fires before a group forum topic is deleted.
			 *
			 * @since 1.5.0
			 *
			 * @param int $topic_id ID of the topic being deleted.
			 */
			do_action( 'groups_before_delete_forum_topic', $topic_id );

			if ( !groups_delete_group_forum_topic( $topic_id ) ) {
				bp_core_add_message( __( 'There was an error deleting the topic', 'buddypress' ), 'error' );
			} else {
				bp_core_add_message( __( 'The topic was deleted successfully', 'buddypress' ) );
			}

			/**
			 * Fires after a group forum topic has been deleted.
			 *
			 * @since 1.5.0
			 *
			 * @param int $topic_id ID of the topic being deleted.
			 */
			do_action( 'groups_delete_forum_topic', $topic_id );
			bp_core_redirect( bp_get_group_permalink( groups_get_current_group() ) . 'forum/' );
		}

		// Editing a topic
		elseif ( empty( $user_is_banned ) && bp_is_action_variable( 'edit', 2 ) && !bp_action_variable( 3 ) ) {
			// Fetch the topic
			$topic = bp_forums_get_topic_details( $topic_id );

			// Check the logged in user can edit this topic
			if ( ! bp_is_item_admin() && ! bp_is_item_mod() && ( (int) bp_loggedin_user_id() != (int) $topic->topic_poster ) ) {
				bp_core_redirect( wp_get_referer() );
			}

			if ( isset( $_POST['save_changes'] ) ) {
				// Check the nonce
				check_admin_referer( 'bp_forums_edit_topic' );

				$topic_tags = !empty( $_POST['topic_tags'] ) ? $_POST['topic_tags'] : false;

				if ( !groups_update_group_forum_topic( $topic_id, $_POST['topic_title'], $_POST['topic_text'], $topic_tags ) ) {
					bp_core_add_message( __( 'There was an error when editing that topic', 'buddypress'), 'error' );
				} else {
					bp_core_add_message( __( 'The topic was edited successfully', 'buddypress') );
				}

				/**
				 * Fires after a group forum topic has been edited.
				 *
				 * @since 1.1.0
				 *
				 * @param int $topic_id ID of the topic being edited.
				 */
				do_action( 'groups_edit_forum_topic', $topic_id );
				bp_core_redirect( bp_get_group_permalink( groups_get_current_group() ) . 'forum/topic/' . $topic_slug . '/' );
			}

			/**
			 * Filters the template to load for a topic edit page.
			 *
			 * @since 1.1.0
			 *
			 * @param string $value Path to a topic edit template.
			 */
			bp_core_load_template( apply_filters( 'groups_template_group_forum_topic_edit', 'groups/single/home' ) );

		// Delete a post
		} elseif ( empty( $user_is_banned ) && bp_is_action_variable( 'delete', 2 ) && $post_id = bp_action_variable( 4 ) ) {
			// Fetch the post
			$post = bp_forums_get_post( $post_id );

			// Check the logged in user can edit this topic
			if ( ! bp_is_item_admin() && ! bp_is_item_mod() && ( (int) bp_loggedin_user_id() != (int) $post->poster_id ) ) {
				bp_core_redirect( wp_get_referer() );
			}

			// Check the nonce
			check_admin_referer( 'bp_forums_delete_post' );

			/**
			 * Fires before the deletion of a group forum post.
			 *
			 * @since 1.5.0
			 *
			 * @param int $post_id ID of the forum post being deleted.
			 */
			do_action( 'groups_before_delete_forum_post', $post_id );

			if ( !groups_delete_group_forum_post( $post_id ) ) {
				bp_core_add_message( __( 'There was an error deleting that post', 'buddypress'), 'error' );
			} else {
				bp_core_add_message( __( 'The post was deleted successfully', 'buddypress') );
			}

			/**
			 * Fires after the deletion of a group forum post.
			 *
			 * @since 1.1.0
			 *
			 * @param int $post_id ID of the forum post being deleted.
			 */
			do_action( 'groups_delete_forum_post', $post_id );
			bp_core_redirect( wp_get_referer() );

		// Editing a post
		} elseif ( empty( $user_is_banned ) && bp_is_action_variable( 'edit', 2 ) && $post_id = bp_action_variable( 4 ) ) {

			// Fetch the post
			$post = bp_forums_get_post( $post_id );

			// Check the logged in user can edit this topic
			if ( ! bp_is_item_admin() && ! bp_is_item_mod() && ( (int) bp_loggedin_user_id() != (int) $post->poster_id ) ) {
				bp_core_redirect( wp_get_referer() );
			}

			if ( isset( $_POST['save_changes'] ) ) {
				// Check the nonce
				check_admin_referer( 'bp_forums_edit_post' );

				$topic_page = isset( $_GET['topic_page'] ) ? $_GET['topic_page'] : false;

				if ( !$post_id = groups_update_group_forum_post( $post_id, $_POST['post_text'], $topic_id, $topic_page ) ) {
					bp_core_add_message( __( 'There was an error when editing that post', 'buddypress'), 'error' );
				} else {
					bp_core_add_message( __( 'The post was edited successfully', 'buddypress') );
				}

				if ( $_SERVER['QUERY_STRING'] ) {
					$query_vars = '?' . $_SERVER['QUERY_STRING'];
				}

				/**
				 * Fires after the editing of a group forum post.
				 *
				 * @since 1.1.0
				 *
				 * @param int $post_id ID of the forum post being edited.
				 */
				do_action( 'groups_edit_forum_post', $post_id );
				bp_core_redirect( bp_get_group_permalink( $bp->groups->current_group ) . 'forum/topic/' . $topic_slug . '/' . $query_vars . '#post-' . $post_id );
			}

			/** This filter is documented in bp-groups/bp-groups-screens.php */
			bp_core_load_template( apply_filters( 'groups_template_group_forum_topic_edit', 'groups/single/home' ) );

		// Standard topic display
		} else {
			if ( !empty( $user_is_banned ) ) {
				bp_core_add_message( __( "You have been banned from this group.", 'buddypress' ) );
			}

			/**
			 * Filters the template to load for a topic page.
			 *
			 * @since 1.1.0
			 *
			 * @param string $value Path to a topic template.
			 */
			bp_core_load_template( apply_filters( 'groups_template_group_forum_topic', 'groups/single/home' ) );
		}

	// Forum topic does not exist
	} elseif ( !empty( $topic_slug ) && empty( $topic_id ) ) {
		bp_do_404();
		return;

	} else {
		// Posting a topic
		if ( isset( $_POST['submit_topic'] ) && bp_is_active( 'forums' ) ) {

			// Check the nonce
			check_admin_referer( 'bp_forums_new_topic' );

			if ( $user_is_banned ) {
				$error_message = __( "You have been banned from this group.", 'buddypress' );

			} elseif ( bp_groups_auto_join() && !bp_current_user_can( 'bp_moderate' ) && 'public' == $bp->groups->current_group->status && !groups_is_user_member( bp_loggedin_user_id(), $bp->groups->current_group->id ) ) {
				// Auto join this user if they are not yet a member of this group
				groups_join_group( $bp->groups->current_group->id, bp_loggedin_user_id() );
			}

			if ( empty( $_POST['topic_title'] ) ) {
				$error_message = __( 'Please provide a title for your forum topic.', 'buddypress' );
			} elseif ( empty( $_POST['topic_text'] ) ) {
				$error_message = __( 'Forum posts cannot be empty. Please enter some text.', 'buddypress' );
			}

			if ( empty( $forum_id ) ) {
				$error_message = __( 'This group does not have a forum setup yet.', 'buddypress' );
			}

			if ( isset( $error_message ) ) {
				bp_core_add_message( $error_message, 'error' );
				$redirect = bp_get_group_permalink( $bp->groups->current_group ) . 'forum';
			} else {
				if ( !$topic = groups_new_group_forum_topic( $_POST['topic_title'], $_POST['topic_text'], $_POST['topic_tags'], $forum_id ) ) {
					bp_core_add_message( __( 'There was an error when creating the topic', 'buddypress'), 'error' );
					$redirect = bp_get_group_permalink( $bp->groups->current_group ) . 'forum';
				} else {
					bp_core_add_message( __( 'The topic was created successfully', 'buddypress') );
					$redirect = bp_get_group_permalink( $bp->groups->current_group ) . 'forum/topic/' . $topic->topic_slug . '/';
				}
			}

			bp_core_redirect( $redirect );
		}

		/**
		 * Fires at the end of the group forum screen loading process.
		 *
		 * @since 1.0.0
		 *
		 * @param int $topic_id ID of the topic being displayed.
		 * @param int $forum_id ID of the forum being displayed.
		 */
		do_action( 'groups_screen_group_forum', $topic_id, $forum_id );

		/**
		 * Filters the template to load for a group forum page.
		 *
		 * @since 1.0.0
		 *
		 * @param string $value Path to a group forum template.
		 */
		bp_core_load_template( apply_filters( 'groups_template_group_forum', 'groups/single/home' ) );
	}
}

/**
 * Handle the display of a group's Members page.
 */
function groups_screen_group_members() {

	if ( !bp_is_single_item() )
		return false;

	$bp = buddypress();

	// Refresh the group member count meta
	groups_update_groupmeta( $bp->groups->current_group->id, 'total_member_count', groups_get_total_member_count( $bp->groups->current_group->id ) );

	/**
	 * Fires before the loading of a group's Members page.
	 *
	 * @since 1.0.0
	 *
	 * @param int $id ID of the group whose members are being displayed.
	 */
	do_action( 'groups_screen_group_members', $bp->groups->current_group->id );

	/**
	 * Filters the template to load for a group's Members page.
	 *
	 * @since 1.0.0
	 *
	 * @param string $value Path to a group's Members template.
	 */
	bp_core_load_template( apply_filters( 'groups_template_group_members', 'groups/single/home' ) );
}

/**
 * Handle the display of a group's Send Invites page.
 */
function groups_screen_group_invite() {

	if ( !bp_is_single_item() )
		return false;

	$bp = buddypress();

	if ( bp_is_action_variable( 'send', 0 ) ) {

		if ( !check_admin_referer( 'groups_send_invites', '_wpnonce_send_invites' ) )
			return false;

		if ( !empty( $_POST['friends'] ) ) {
			foreach( (array) $_POST['friends'] as $friend ) {
				groups_invite_user( array( 'user_id' => $friend, 'group_id' => $bp->groups->current_group->id ) );
			}
		}

		// Send the invites.
		groups_send_invites( bp_loggedin_user_id(), $bp->groups->current_group->id );
		bp_core_add_message( __('Group invites sent.', 'buddypress') );

		/**
		 * Fires after the sending of a group invite inside the group's Send Invites page.
		 *
		 * @since 1.0.0
		 *
		 * @param int $id ID of the group whose members are being displayed.
		 */
		do_action( 'groups_screen_group_invite', $bp->groups->current_group->id );
		bp_core_redirect( bp_get_group_permalink( $bp->groups->current_group ) );

	} elseif ( !bp_action_variable( 0 ) ) {

		/**
		 * Filters the template to load for a group's Send Invites page.
		 *
		 * @since 1.0.0
		 *
		 * @param string $value Path to a group's Send Invites template.
		 */
		bp_core_load_template( apply_filters( 'groups_template_group_invite', 'groups/single/home' ) );

	} else {
		bp_do_404();
	}
}

/**
 * Process group invitation removal requests.
 *
 * Note that this function is only used when JS is disabled. Normally, clicking
 * Remove Invite removes the invitation via AJAX.
 *
 * @since 2.0.0
 */
function groups_remove_group_invite() {
	if ( ! bp_is_group_invites() ) {
		return;
	}

	if ( ! bp_is_action_variable( 'remove', 0 ) || ! is_numeric( bp_action_variable( 1 ) ) ) {
		return;
	}

	if ( ! check_admin_referer( 'groups_invite_uninvite_user' ) ) {
		return false;
	}

	$friend_id = intval( bp_action_variable( 1 ) );
	$group_id  = bp_get_current_group_id();
	$message   = __( 'Invite successfully removed', 'buddypress' );
	$redirect  = wp_get_referer();
	$error     = false;

	if ( ! bp_groups_user_can_send_invites( $group_id ) ) {
		$message = __( 'You are not allowed to send or remove invites', 'buddypress' );
		$error = 'error';
	} elseif ( BP_Groups_Member::check_for_membership_request( $friend_id, $group_id ) ) {
		$message = __( 'The member requested to join the group', 'buddypress' );
		$error = 'error';
	} elseif ( ! groups_uninvite_user( $friend_id, $group_id ) ) {
		$message = __( 'There was an error removing the invite', 'buddypress' );
		$error = 'error';
	}

	bp_core_add_message( $message, $error );
	bp_core_redirect( $redirect );
}
add_action( 'bp_screens', 'groups_remove_group_invite' );

/**
 * Handle the display of a group's Request Membership page.
 */
function groups_screen_group_request_membership() {

	if ( !is_user_logged_in() )
		return false;

	$bp = buddypress();

	if ( 'private' != $bp->groups->current_group->status )
		return false;

	// If the user is already invited, accept invitation
	if ( groups_check_user_has_invite( bp_loggedin_user_id(), $bp->groups->current_group->id ) ) {
		if ( groups_accept_invite( bp_loggedin_user_id(), $bp->groups->current_group->id ) )
			bp_core_add_message( __( 'Group invite accepted', 'buddypress' ) );
		else
			bp_core_add_message( __( 'There was an error accepting the group invitation. Please try again.', 'buddypress' ), 'error' );
		bp_core_redirect( bp_get_group_permalink( $bp->groups->current_group ) );
	}

	// If the user has submitted a request, send it.
	if ( isset( $_POST['group-request-send']) ) {

		// Check the nonce
		if ( !check_admin_referer( 'groups_request_membership' ) )
			return false;

		if ( !groups_send_membership_request( bp_loggedin_user_id(), $bp->groups->current_group->id ) ) {
			bp_core_add_message( __( 'There was an error sending your group membership request. Please try again.', 'buddypress' ), 'error' );
		} else {
			bp_core_add_message( __( 'Your membership request was sent to the group administrator successfully. You will be notified when the group administrator responds to your request.', 'buddypress' ) );
		}
		bp_core_redirect( bp_get_group_permalink( $bp->groups->current_group ) );
	}

	/**
	 * Fires before the loading of a group's Request Memebership page.
	 *
	 * @since 1.0.0
	 *
	 * @param int $id ID of the group currently being displayed.
	 */
	do_action( 'groups_screen_group_request_membership', $bp->groups->current_group->id );

	/**
	 * Filters the template to load for a group's Request Membership page.
	 *
	 * @since 1.0.0
	 *
	 * @param string $value Path to a group's Request Membership template.
	 */
	bp_core_load_template( apply_filters( 'groups_template_group_request_membership', 'groups/single/home' ) );
}

/**
 * Handle the loading of a single group's activity.
 *
 * @since 2.4.0
 */
function groups_screen_group_activity() {

	if ( ! bp_is_single_item() ) {
		return false;
	}

	/**
	 * Fires before the loading of a single group's activity page.
	 *
	 * @since 2.4.0
	 */
	do_action( 'groups_screen_group_activity' );

	/**
	 * Filters the template to load for a single group's activity page.
	 *
	 * @since 2.4.0
	 *
	 * @param string $value Path to a single group's template to load.
	 */
	bp_core_load_template( apply_filters( 'groups_screen_group_activity', 'groups/single/activity' ) );
}

/**
 * Handle the display of a single group activity item.
 */
function groups_screen_group_activity_permalink() {

	if ( !bp_is_groups_component() || !bp_is_active( 'activity' ) || ( bp_is_active( 'activity' ) && !bp_is_current_action( bp_get_activity_slug() ) ) || !bp_action_variable( 0 ) )
		return false;

	buddypress()->is_single_item = true;

	/** This filter is documented in bp-groups/bp-groups-screens.php */
	bp_core_load_template( apply_filters( 'groups_template_group_home', 'groups/single/home' ) );
}
add_action( 'bp_screens', 'groups_screen_group_activity_permalink' );

/**
 * Handle the display of a group's Admin pages.
 */
function groups_screen_group_admin() {
	if ( !bp_is_groups_component() || !bp_is_current_action( 'admin' ) )
		return false;

	if ( bp_action_variables() )
		return false;

	bp_core_redirect( bp_get_group_permalink( groups_get_current_group() ) . 'admin/edit-details/' );
}

/**
 * Handle the display of a group's admin/edit-details page.
 */
function groups_screen_group_admin_edit_details() {

	if ( 'edit-details' != bp_get_group_current_admin_tab() )
		return false;

	if ( bp_is_item_admin() ) {

		$bp = buddypress();

		// If the edit form has been submitted, save the edited details
		if ( isset( $_POST['save'] ) ) {
			// Check the nonce
			if ( !check_admin_referer( 'groups_edit_group_details' ) )
				return false;

			$group_notify_members = isset( $_POST['group-notify-members'] ) ? (int) $_POST['group-notify-members'] : 0;

			if ( !groups_edit_base_group_details( $_POST['group-id'], $_POST['group-name'], $_POST['group-desc'], $group_notify_members ) ) {
				bp_core_add_message( __( 'There was an error updating group details. Please try again.', 'buddypress' ), 'error' );
			} else {
				bp_core_add_message( __( 'Group details were successfully updated.', 'buddypress' ) );
			}

			/**
			 * Fires before the redirect if a group details has been edited and saved.
			 *
			 * @since 1.0.0
			 *
			 * @param int $id ID of the group that was edited.
			 */
			do_action( 'groups_group_details_edited', $bp->groups->current_group->id );

			bp_core_redirect( bp_get_group_permalink( groups_get_current_group() ) . 'admin/edit-details/' );
		}

		/**
		 * Fires before the loading of the group admin/edit-details page template.
		 *
		 * @since 1.0.0
		 *
		 * @param int $id ID of the group that is being displayed.
		 */
		do_action( 'groups_screen_group_admin_edit_details', $bp->groups->current_group->id );

		/**
		 * Filters the template to load for a group's admin/edit-details page.
		 *
		 * @since 1.0.0
		 *
		 * @param string $value Path to a group's admin/edit-details template.
		 */
		bp_core_load_template( apply_filters( 'groups_template_group_admin', 'groups/single/home' ) );
	}
}
add_action( 'bp_screens', 'groups_screen_group_admin_edit_details' );

/**
 * Handle the display of a group's admin/group-settings page.
 */
function groups_screen_group_admin_settings() {

	if ( 'group-settings' != bp_get_group_current_admin_tab() )
		return false;

	if ( ! bp_is_item_admin() )
		return false;

	$bp = buddypress();

	// If the edit form has been submitted, save the edited details
	if ( isset( $_POST['save'] ) ) {
		$enable_forum   = ( isset($_POST['group-show-forum'] ) ) ? 1 : 0;

		// Checked against a whitelist for security
		/** This filter is documented in bp-groups/bp-groups-admin.php */
		$allowed_status = apply_filters( 'groups_allowed_status', array( 'public', 'private', 'hidden' ) );
		$status         = ( in_array( $_POST['group-status'], (array) $allowed_status ) ) ? $_POST['group-status'] : 'public';

		// Checked against a whitelist for security
		/** This filter is documented in bp-groups/bp-groups-admin.php */
		$allowed_invite_status = apply_filters( 'groups_allowed_invite_status', array( 'members', 'mods', 'admins' ) );
		$invite_status	       = isset( $_POST['group-invite-status'] ) && in_array( $_POST['group-invite-status'], (array) $allowed_invite_status ) ? $_POST['group-invite-status'] : 'members';

		// Check the nonce
		if ( !check_admin_referer( 'groups_edit_group_settings' ) )
			return false;

		if ( !groups_edit_group_settings( $_POST['group-id'], $enable_forum, $status, $invite_status ) ) {
			bp_core_add_message( __( 'There was an error updating group settings. Please try again.', 'buddypress' ), 'error' );
		} else {
			bp_core_add_message( __( 'Group settings were successfully updated.', 'buddypress' ) );
		}

		/**
		 * Fires before the redirect if a group settings has been edited and saved.
		 *
		 * @since 1.0.0
		 *
		 * @param int $id ID of the group that was edited.
		 */
		do_action( 'groups_group_settings_edited', $bp->groups->current_group->id );

		bp_core_redirect( bp_get_group_permalink( groups_get_current_group() ) . 'admin/group-settings/' );
	}

	/**
	 * Fires before the loading of the group admin/group-settings page template.
	 *
	 * @since 1.0.0
	 *
	 * @param int $id ID of the group that is being displayed.
	 */
	do_action( 'groups_screen_group_admin_settings', $bp->groups->current_group->id );

	/**
	 * Filters the template to load for a group's admin/group-settings page.
	 *
	 * @since 1.0.0
	 *
	 * @param string $value Path to a group's admin/group-settings template.
	 */
	bp_core_load_template( apply_filters( 'groups_template_group_admin_settings', 'groups/single/home' ) );
}
add_action( 'bp_screens', 'groups_screen_group_admin_settings' );

/**
 * Handle the display of a group's Change Avatar page.
 */
function groups_screen_group_admin_avatar() {

	if ( 'group-avatar' != bp_get_group_current_admin_tab() )
		return false;

	// If the logged-in user doesn't have permission or if avatar uploads are disabled, then stop here
	if ( ! bp_is_item_admin() || bp_disable_group_avatar_uploads() || ! buddypress()->avatar->show_avatars )
		return false;

	$bp = buddypress();

	// If the group admin has deleted the admin avatar
	if ( bp_is_action_variable( 'delete', 1 ) ) {

		// Check the nonce
		check_admin_referer( 'bp_group_avatar_delete' );

		if ( bp_core_delete_existing_avatar( array( 'item_id' => $bp->groups->current_group->id, 'object' => 'group' ) ) ) {
			bp_core_add_message( __( 'The group profile photo was deleted successfully!', 'buddypress' ) );
		} else {
			bp_core_add_message( __( 'There was a problem deleting the group profile photo. Please try again.', 'buddypress' ), 'error' );
		}
	}

	if ( ! isset( $bp->avatar_admin ) ) {
		$bp->avatar_admin = new stdClass();
	}

	$bp->avatar_admin->step = 'upload-image';

	if ( !empty( $_FILES ) ) {

		// Check the nonce
		check_admin_referer( 'bp_avatar_upload' );

		// Pass the file to the avatar upload handler
		if ( bp_core_avatar_handle_upload( $_FILES, 'groups_avatar_upload_dir' ) ) {
			$bp->avatar_admin->step = 'crop-image';

			// Make sure we include the jQuery jCrop file for image cropping
			add_action( 'wp_print_scripts', 'bp_core_add_jquery_cropper' );
		}

	}

	// If the image cropping is done, crop the image and save a full/thumb version
	if ( isset( $_POST['avatar-crop-submit'] ) ) {

		// Check the nonce
		check_admin_referer( 'bp_avatar_cropstore' );

		$args = array(
			'object'        => 'group',
			'avatar_dir'    => 'group-avatars',
			'item_id'       => $bp->groups->current_group->id,
			'original_file' => $_POST['image_src'],
			'crop_x'        => $_POST['x'],
			'crop_y'        => $_POST['y'],
			'crop_w'        => $_POST['w'],
			'crop_h'        => $_POST['h']
		);

		if ( !bp_core_avatar_handle_crop( $args ) ) {
			bp_core_add_message( __( 'There was a problem cropping the group profile photo.', 'buddypress' ), 'error' );
		} else {
			bp_core_add_message( __( 'The new group profile photo was uploaded successfully.', 'buddypress' ) );
		}
	}

	/**
	 * Fires before the loading of the group Change Avatar page template.
	 *
	 * @since 1.0.0
	 *
	 * @param int $id ID of the group that is being displayed.
	 */
	do_action( 'groups_screen_group_admin_avatar', $bp->groups->current_group->id );

	/**
	 * Filters the template to load for a group's Change Avatar page.
	 *
	 * @since 1.0.0
	 *
	 * @param string $value Path to a group's Change Avatar template.
	 */
	bp_core_load_template( apply_filters( 'groups_template_group_admin_avatar', 'groups/single/home' ) );
}
add_action( 'bp_screens', 'groups_screen_group_admin_avatar' );

/**
 * Handle the display of a group's Change cover image page.
 *
 * @since 2.4.0
 */
function groups_screen_group_admin_cover_image() {
	if ( 'group-cover-image' != bp_get_group_current_admin_tab() ) {
		return false;
	}

	// If the logged-in user doesn't have permission or if cover image uploads are disabled, then stop here
	if ( ! bp_is_item_admin() || ! bp_group_use_cover_image_header() ) {
		return false;
	}

	/**
	 * Fires before the loading of the group Change cover image page template.
	 *
	 * @since 2.4.0
	 *
	 * @param int $id ID of the group that is being displayed.
	 */
	do_action( 'groups_screen_group_admin_cover_image', bp_get_current_group_id() );

	/**
	 * Filters the template to load for a group's Change cover image page.
	 *
	 * @since 2.4.0
	 *
	 * @param string $value Path to a group's Change cover image template.
	 */
	bp_core_load_template( apply_filters( 'groups_template_group_admin_cover_image', 'groups/single/home' ) );
}
add_action( 'bp_screens', 'groups_screen_group_admin_cover_image' );

/**
 * This function handles actions related to member management on the group admin.
 */
function groups_screen_group_admin_manage_members() {

	if ( 'manage-members' != bp_get_group_current_admin_tab() )
		return false;

	if ( ! bp_is_item_admin() )
		return false;

	$bp = buddypress();

	if ( bp_action_variable( 1 ) && bp_action_variable( 2 ) && bp_action_variable( 3 ) ) {
		if ( bp_is_action_variable( 'promote', 1 ) && ( bp_is_action_variable( 'mod', 2 ) || bp_is_action_variable( 'admin', 2 ) ) && is_numeric( bp_action_variable( 3 ) ) ) {
			$user_id = bp_action_variable( 3 );
			$status  = bp_action_variable( 2 );

			// Check the nonce first.
			if ( !check_admin_referer( 'groups_promote_member' ) )
				return false;

			// Promote a user.
			if ( !groups_promote_member( $user_id, $bp->groups->current_group->id, $status ) )
				bp_core_add_message( __( 'There was an error when promoting that user. Please try again.', 'buddypress' ), 'error' );
			else
				bp_core_add_message( __( 'User promoted successfully', 'buddypress' ) );

			/**
			 * Fires before the redirect after a group member has been promoted.
			 *
			 * @since 1.0.0
			 *
			 * @param int $user_id ID of the user being promoted.
			 * @param int $id      ID of the group user is promoted within.
			 */
			do_action( 'groups_promoted_member', $user_id, $bp->groups->current_group->id );

			bp_core_redirect( bp_get_group_permalink( groups_get_current_group() ) . 'admin/manage-members/' );
		}
	}

	if ( bp_action_variable( 1 ) && bp_action_variable( 2 ) ) {
		if ( bp_is_action_variable( 'demote', 1 ) && is_numeric( bp_action_variable( 2 ) ) ) {
			$user_id = bp_action_variable( 2 );

			// Check the nonce first.
			if ( !check_admin_referer( 'groups_demote_member' ) )
				return false;

			// Stop sole admins from abandoning their group
			$group_admins = groups_get_group_admins( $bp->groups->current_group->id );
			if ( 1 == count( $group_admins ) && $group_admins[0]->user_id == $user_id )
				bp_core_add_message( __( 'This group must have at least one admin', 'buddypress' ), 'error' );

			// Demote a user.
			elseif ( !groups_demote_member( $user_id, $bp->groups->current_group->id ) )
				bp_core_add_message( __( 'There was an error when demoting that user. Please try again.', 'buddypress' ), 'error' );
			else
				bp_core_add_message( __( 'User demoted successfully', 'buddypress' ) );

			/**
			 * Fires before the redirect after a group member has been demoted.
			 *
			 * @since 1.0.0
			 *
			 * @param int $user_id ID of the user being demoted.
			 * @param int $id      ID of the group user is demoted within.
			 */
			do_action( 'groups_demoted_member', $user_id, $bp->groups->current_group->id );

			bp_core_redirect( bp_get_group_permalink( groups_get_current_group() ) . 'admin/manage-members/' );
		}

		if ( bp_is_action_variable( 'ban', 1 ) && is_numeric( bp_action_variable( 2 ) ) ) {
			$user_id = bp_action_variable( 2 );

			// Check the nonce first.
			if ( !check_admin_referer( 'groups_ban_member' ) )
				return false;

			// Ban a user.
			if ( !groups_ban_member( $user_id, $bp->groups->current_group->id ) )
				bp_core_add_message( __( 'There was an error when banning that user. Please try again.', 'buddypress' ), 'error' );
			else
				bp_core_add_message( __( 'User banned successfully', 'buddypress' ) );

			/**
			 * Fires before the redirect after a group member has been banned.
			 *
			 * @since 1.0.0
			 *
			 * @param int $user_id ID of the user being banned.
			 * @param int $id      ID of the group user is banned from.
			 */
			do_action( 'groups_banned_member', $user_id, $bp->groups->current_group->id );

			bp_core_redirect( bp_get_group_permalink( groups_get_current_group() ) . 'admin/manage-members/' );
		}

		if ( bp_is_action_variable( 'unban', 1 ) && is_numeric( bp_action_variable( 2 ) ) ) {
			$user_id = bp_action_variable( 2 );

			// Check the nonce first.
			if ( !check_admin_referer( 'groups_unban_member' ) )
				return false;

			// Remove a ban for user.
			if ( !groups_unban_member( $user_id, $bp->groups->current_group->id ) )
				bp_core_add_message( __( 'There was an error when unbanning that user. Please try again.', 'buddypress' ), 'error' );
			else
				bp_core_add_message( __( 'User ban removed successfully', 'buddypress' ) );

			/**
			 * Fires before the redirect after a group member has been unbanned.
			 *
			 * @since 1.0.0
			 *
			 * @param int $user_id ID of the user being unbanned.
			 * @param int $id      ID of the group user is unbanned from.
			 */
			do_action( 'groups_unbanned_member', $user_id, $bp->groups->current_group->id );

			bp_core_redirect( bp_get_group_permalink( groups_get_current_group() ) . 'admin/manage-members/' );
		}

		if ( bp_is_action_variable( 'remove', 1 ) && is_numeric( bp_action_variable( 2 ) ) ) {
			$user_id = bp_action_variable( 2 );

			// Check the nonce first.
			if ( !check_admin_referer( 'groups_remove_member' ) )
				return false;

			// Remove a user.
			if ( !groups_remove_member( $user_id, $bp->groups->current_group->id ) )
				bp_core_add_message( __( 'There was an error removing that user from the group. Please try again.', 'buddypress' ), 'error' );
			else
				bp_core_add_message( __( 'User removed successfully', 'buddypress' ) );

			/**
			 * Fires before the redirect after a group member has been removed.
			 *
			 * @since 1.2.6
			 *
			 * @param int $user_id ID of the user being removed.
			 * @param int $id      ID of the group the user is removed from.
			 */
			do_action( 'groups_removed_member', $user_id, $bp->groups->current_group->id );

			bp_core_redirect( bp_get_group_permalink( groups_get_current_group() ) . 'admin/manage-members/' );
		}
	}

	/**
	 * Fires before the loading of a group's manage members template.
	 *
	 * @since 1.0.0
	 *
	 * @param int $id ID of the group whose manage members page is being displayed.
	 */
	do_action( 'groups_screen_group_admin_manage_members', $bp->groups->current_group->id );

	/**
	 * Filters the template to load for a group's manage members page.
	 *
	 * @since 1.0.0
	 *
	 * @param string $value Path to a group's manage members template.
	 */
	bp_core_load_template( apply_filters( 'groups_template_group_admin_manage_members', 'groups/single/home' ) );
}
add_action( 'bp_screens', 'groups_screen_group_admin_manage_members' );

/**
 * Handle the display of Admin > Membership Requests.
 */
function groups_screen_group_admin_requests() {
	$bp = buddypress();

	if ( 'membership-requests' != bp_get_group_current_admin_tab() ) {
		return false;
	}

	if ( ! bp_is_item_admin() || ( 'public' == $bp->groups->current_group->status ) ) {
		return false;
	}

	$request_action = (string) bp_action_variable( 1 );
	$membership_id  = (int) bp_action_variable( 2 );

	if ( !empty( $request_action ) && !empty( $membership_id ) ) {
		if ( 'accept' == $request_action && is_numeric( $membership_id ) ) {

			// Check the nonce first.
			if ( !check_admin_referer( 'groups_accept_membership_request' ) )
				return false;

			// Accept the membership request
			if ( !groups_accept_membership_request( $membership_id ) )
				bp_core_add_message( __( 'There was an error accepting the membership request. Please try again.', 'buddypress' ), 'error' );
			else
				bp_core_add_message( __( 'Group membership request accepted', 'buddypress' ) );

		} elseif ( 'reject' == $request_action && is_numeric( $membership_id ) ) {
			/* Check the nonce first. */
			if ( !check_admin_referer( 'groups_reject_membership_request' ) )
				return false;

			// Reject the membership request
			if ( !groups_reject_membership_request( $membership_id ) )
				bp_core_add_message( __( 'There was an error rejecting the membership request. Please try again.', 'buddypress' ), 'error' );
			else
				bp_core_add_message( __( 'Group membership request rejected', 'buddypress' ) );
		}

		/**
		 * Fires before the redirect if a group membership request has been handled.
		 *
		 * @since 1.0.0
		 *
		 * @param int    $id             ID of the group that was edited.
		 * @param string $request_action Membership request action being performed.
		 * @param int    $membership_id  The key of the action_variables array that you want.
		 */
		do_action( 'groups_group_request_managed', $bp->groups->current_group->id, $request_action, $membership_id );
		bp_core_redirect( bp_get_group_permalink( groups_get_current_group() ) . 'admin/membership-requests/' );
	}

	/**
	 * Fires before the loading of the group membership request page template.
	 *
	 * @since 1.0.0
	 *
	 * @param int $id ID of the group that is being displayed.
	 */
	do_action( 'groups_screen_group_admin_requests', $bp->groups->current_group->id );

	/**
	 * Filters the template to load for a group's membership request page.
	 *
	 * @since 1.0.0
	 *
	 * @param string $value Path to a group's membership request template.
	 */
	bp_core_load_template( apply_filters( 'groups_template_group_admin_requests', 'groups/single/home' ) );
}
add_action( 'bp_screens', 'groups_screen_group_admin_requests' );

/**
 * Handle the display of the Delete Group page.
 */
function groups_screen_group_admin_delete_group() {

	if ( 'delete-group' != bp_get_group_current_admin_tab() )
		return false;

	if ( ! bp_is_item_admin() && !bp_current_user_can( 'bp_moderate' ) )
		return false;

	$bp = buddypress();

	if ( isset( $_REQUEST['delete-group-button'] ) && isset( $_REQUEST['delete-group-understand'] ) ) {

		// Check the nonce first.
		if ( !check_admin_referer( 'groups_delete_group' ) ) {
			return false;
		}

		/**
		 * Fires before the deletion of a group from the Delete Group page.
		 *
		 * @since 1.5.0
		 *
		 * @param int $id ID of the group being deleted.
		 */
		do_action( 'groups_before_group_deleted', $bp->groups->current_group->id );

		// Group admin has deleted the group, now do it.
		if ( !groups_delete_group( $bp->groups->current_group->id ) ) {
			bp_core_add_message( __( 'There was an error deleting the group. Please try again.', 'buddypress' ), 'error' );
		} else {
			bp_core_add_message( __( 'The group was deleted successfully.', 'buddypress' ) );

			/**
			 * Fires after the deletion of a group from the Delete Group page.
			 *
			 * @since 1.0.0
			 *
			 * @param int $id ID of the group being deleted.
			 */
			do_action( 'groups_group_deleted', $bp->groups->current_group->id );

			bp_core_redirect( trailingslashit( bp_loggedin_user_domain() . bp_get_groups_slug() ) );
		}

		bp_core_redirect( trailingslashit( bp_loggedin_user_domain() . bp_get_groups_slug() ) );
	}

	/**
	 * Fires before the loading of the Delete Group page template.
	 *
	 * @since 1.0.0
	 *
	 * @param int $id ID of the group that is being displayed.
	 */
	do_action( 'groups_screen_group_admin_delete_group', $bp->groups->current_group->id );

	/**
	 * Filters the template to load for the Delete Group page.
	 *
	 * @since 1.0.0
	 *
	 * @param string $value Path to the Delete Group template.
	 */
	bp_core_load_template( apply_filters( 'groups_template_group_admin_delete_group', 'groups/single/home' ) );
}
add_action( 'bp_screens', 'groups_screen_group_admin_delete_group' );

/**
 * Render the group settings fields on the Notification Settings page.
 */
function groups_screen_notification_settings() {

	if ( !$group_invite = bp_get_user_meta( bp_displayed_user_id(), 'notification_groups_invite', true ) )
		$group_invite  = 'yes';

	if ( !$group_update = bp_get_user_meta( bp_displayed_user_id(), 'notification_groups_group_updated', true ) )
		$group_update  = 'yes';

	if ( !$group_promo = bp_get_user_meta( bp_displayed_user_id(), 'notification_groups_admin_promotion', true ) )
		$group_promo   = 'yes';

	if ( !$group_request = bp_get_user_meta( bp_displayed_user_id(), 'notification_groups_membership_request', true ) )
		$group_request = 'yes'; ?>

	<table class="notification-settings" id="groups-notification-settings">
		<thead>
			<tr>
				<th class="icon"></th>
				<th class="title"><?php _ex( 'Groups', 'Group settings on notification settings page', 'buddypress' ) ?></th>
				<th class="yes"><?php _e( 'Yes', 'buddypress' ) ?></th>
				<th class="no"><?php _e( 'No', 'buddypress' )?></th>
			</tr>
		</thead>

		<tbody>
			<tr id="groups-notification-settings-invitation">
				<td></td>
				<td><?php _ex( 'A member invites you to join a group', 'group settings on notification settings page','buddypress' ) ?></td>
				<td class="yes"><input type="radio" name="notifications[notification_groups_invite]" id="notification-groups-invite-yes" value="yes" <?php checked( $group_invite, 'yes', true ) ?>/><label for="notification-groups-invite-yes" class="bp-screen-reader-text"><?php _e( 'Yes, send email', 'buddypress' ); ?></label></td>
				<td class="no"><input type="radio" name="notifications[notification_groups_invite]" id="notification-groups-invite-no" value="no" <?php checked( $group_invite, 'no', true ) ?>/><label for="notification-groups-invite-no" class="bp-screen-reader-text"><?php _e( 'No, do not send email', 'buddypress' ); ?></label></td>
			</tr>
			<tr id="groups-notification-settings-info-updated">
				<td></td>
				<td><?php _ex( 'Group information is updated', 'group settings on notification settings page', 'buddypress' ) ?></td>
				<td class="yes"><input type="radio" name="notifications[notification_groups_group_updated]" id="notification-groups-group-updated-yes" value="yes" <?php checked( $group_update, 'yes', true ) ?>/><label for="notification-groups-group-updated-yes" class="bp-screen-reader-text"><?php _e( 'Yes, send email', 'buddypress' ); ?></label></td>
				<td class="no"><input type="radio" name="notifications[notification_groups_group_updated]" id="notification-groups-group-updated-no" value="no" <?php checked( $group_update, 'no', true ) ?>/><label for="notification-groups-group-updated-no" class="bp-screen-reader-text"><?php _e( 'No, do not send email', 'buddypress' ); ?></label></td>
			</tr>
			<tr id="groups-notification-settings-promoted">
				<td></td>
				<td><?php _ex( 'You are promoted to a group administrator or moderator', 'group settings on notification settings page', 'buddypress' ) ?></td>
				<td class="yes"><input type="radio" name="notifications[notification_groups_admin_promotion]" id="notification-groups-admin-promotion-yes" value="yes" <?php checked( $group_promo, 'yes', true ) ?>/><label for="notification-groups-admin-promotion-yes" class="bp-screen-reader-text"><?php _e( 'Yes, send email', 'buddypress' ); ?></label></td>
				<td class="no"><input type="radio" name="notifications[notification_groups_admin_promotion]" id="notification-groups-admin-promotion-no" value="no" <?php checked( $group_promo, 'no', true ) ?>/><label for="notification-groups-admin-promotion-no" class="bp-screen-reader-text"><?php _e( 'No, do not send email', 'buddypress' ); ?></label></td>
			</tr>
			<tr id="groups-notification-settings-request">
				<td></td>
				<td><?php _ex( 'A member requests to join a private group for which you are an admin', 'group settings on notification settings page', 'buddypress' ) ?></td>
				<td class="yes"><input type="radio" name="notifications[notification_groups_membership_request]" id="notification-groups-membership-request-yes" value="yes" <?php checked( $group_request, 'yes', true ) ?>/><label for="notification-groups-membership-request-yes" class="bp-screen-reader-text"><?php _e( 'Yes, send email', 'buddypress' ); ?></label></td>
				<td class="no"><input type="radio" name="notifications[notification_groups_membership_request]" id="notification-groups-membership-request-no" value="no" <?php checked( $group_request, 'no', true ) ?>/><label for="notification-groups-membership-request-no" class="bp-screen-reader-text"><?php _e( 'No, do not send email', 'buddypress' ); ?></label></td>
			</tr>

			<?php

			/**
			 * Fires at the end of the available group settings fields on Notification Settings page.
			 *
			 * @since 1.0.0
			 */
			do_action( 'groups_screen_notification_settings' ); ?>

		</tbody>
	</table>

<?php
}
add_action( 'bp_notification_settings', 'groups_screen_notification_settings' );

/** Theme Compatibility *******************************************************/

/**
 * The main theme compat class for BuddyPress Groups.
 *
 * This class sets up the necessary theme compatibility actions to safely output
 * group template parts to the_title and the_content areas of a theme.
 *
 * @since 1.7.0
 */
class BP_Groups_Theme_Compat {

	/**
	 * Set up theme compatibility for the Groups component.
	 *
	 * @since 1.7.0
	 */
	public function __construct() {
		add_action( 'bp_setup_theme_compat', array( $this, 'is_group' ) );
	}

	/**
	 * Are we looking at something that needs group theme compatibility?
	 *
	 * @since 1.7.0
	 */
	public function is_group() {

		// Bail if not looking at a group
		if ( ! bp_is_groups_component() )
			return;

		// Group Directory
		if ( ! bp_current_action() && ! bp_current_item() ) {
			bp_update_is_directory( true, 'groups' );

			/**
			 * Fires at the start of the group theme compatibility setup.
			 *
			 * @since 1.1.0
			 */
			do_action( 'groups_directory_groups_setup' );

			add_filter( 'bp_get_buddypress_template',                array( $this, 'directory_template_hierarchy' ) );
			add_action( 'bp_template_include_reset_dummy_post_data', array( $this, 'directory_dummy_post' ) );
			add_filter( 'bp_replace_the_content',                    array( $this, 'directory_content'    ) );

		// Creating a group
		} elseif ( bp_is_groups_component() && bp_is_current_action( 'create' ) ) {
			add_filter( 'bp_get_buddypress_template',                array( $this, 'create_template_hierarchy' ) );
			add_action( 'bp_template_include_reset_dummy_post_data', array( $this, 'create_dummy_post' ) );
			add_filter( 'bp_replace_the_content',                    array( $this, 'create_content'    ) );

		// Group page
		} elseif ( bp_is_single_item() ) {
			add_filter( 'bp_get_buddypress_template',                array( $this, 'single_template_hierarchy' ) );
			add_action( 'bp_template_include_reset_dummy_post_data', array( $this, 'single_dummy_post' ) );
			add_filter( 'bp_replace_the_content',                    array( $this, 'single_content'    ) );

		}
	}

	/** Directory *********************************************************/

	/**
	 * Add template hierarchy to theme compat for the group directory page.
	 *
	 * This is to mirror how WordPress has
	 * {@link https://codex.wordpress.org/Template_Hierarchy template hierarchy}.
	 *
	 * @since 1.8.0
	 *
	 * @param string $templates The templates from bp_get_theme_compat_templates().
	 * @return array $templates Array of custom templates to look for.
	 */
	public function directory_template_hierarchy( $templates ) {

		/**
		 * Filters the Groups directory page template hierarchy based on priority.
		 *
		 * @since 1.8.0
		 *
		 * @param array $value Array of default template files to use.
		 */
		$new_templates = apply_filters( 'bp_template_hierarchy_groups_directory', array(
			'groups/index-directory.php'
		) );

		// Merge new templates with existing stack
		// @see bp_get_theme_compat_templates()
		$templates = array_merge( (array) $new_templates, $templates );

		return $templates;
	}

	/**
	 * Update the global $post with directory data.
	 *
	 * @since 1.7.0
	 */
	public function directory_dummy_post() {
		bp_theme_compat_reset_post( array(
			'ID'             => 0,
			'post_title'     => bp_get_directory_title( 'groups' ),
			'post_author'    => 0,
			'post_date'      => 0,
			'post_content'   => '',
			'post_type'      => 'page',
			'post_status'    => 'publish',
			'is_page'        => true,
			'comment_status' => 'closed'
		) );
	}

	/**
	 * Filter the_content with the groups index template part.
	 *
	 * @since 1.7.0
	 */
	public function directory_content() {
		return bp_buffer_template_part( 'groups/index', null, false );
	}

	/** Create ************************************************************/

	/**
	 * Add custom template hierarchy to theme compat for the group create page.
	 *
	 * This is to mirror how WordPress has
	 * {@link https://codex.wordpress.org/Template_Hierarchy template hierarchy}.
	 *
	 * @since 1.8.0
	 *
	 * @param string $templates The templates from bp_get_theme_compat_templates().
	 *
	 * @return array $templates Array of custom templates to look for.
	 */
	public function create_template_hierarchy( $templates ) {

		/**
		 * Filters the Groups create page template hierarchy based on priority.
		 *
		 * @since 1.8.0
		 *
		 * @param array $value Array of default template files to use.
		 */
		$new_templates = apply_filters( 'bp_template_hierarchy_groups_create', array(
			'groups/index-create.php'
		) );

		// Merge new templates with existing stack
		// @see bp_get_theme_compat_templates()
		$templates = array_merge( $new_templates, $templates );

		return $templates;
	}

	/**
	 * Update the global $post with create screen data.
	 *
	 * @since 1.7.0
	 */
	public function create_dummy_post() {

		$title = _x( 'Groups', 'Group creation page', 'buddypress' );

		bp_theme_compat_reset_post( array(
			'ID'             => 0,
			'post_title'     => $title,
			'post_author'    => 0,
			'post_date'      => 0,
			'post_content'   => '',
			'post_type'      => 'page',
			'post_status'    => 'publish',
			'is_page'        => true,
			'comment_status' => 'closed'
		) );
	}

	/**
	 * Filter the_content with the create screen template part.
	 *
	 * @since 1.7.0
	 */
	public function create_content() {
		return bp_buffer_template_part( 'groups/create', null, false );
	}

	/** Single ************************************************************/

	/**
	 * Add custom template hierarchy to theme compat for group pages.
	 *
	 * This is to mirror how WordPress has
	 * {@link https://codex.wordpress.org/Template_Hierarchy template hierarchy}.
	 *
	 * @since 1.8.0
	 *
	 * @param string $templates The templates from bp_get_theme_compat_templates().
	 * @return array $templates Array of custom templates to look for.
	 */
	public function single_template_hierarchy( $templates ) {
		// Setup some variables we're going to reference in our custom templates
		$group = groups_get_current_group();

		/**
		 * Filters the Groups single pages template hierarchy based on priority.
		 *
		 * @since 1.8.0
		 *
		 * @param array $value Array of default template files to use.
		 */
		$new_templates = apply_filters( 'bp_template_hierarchy_groups_single_item', array(
			'groups/single/index-id-'     . sanitize_file_name( bp_get_current_group_id() )   . '.php',
			'groups/single/index-slug-'   . sanitize_file_name( bp_get_current_group_slug() ) . '.php',
			'groups/single/index-action-' . sanitize_file_name( bp_current_action() )         . '.php',
			'groups/single/index-status-' . sanitize_file_name( $group->status )              . '.php',
			'groups/single/index.php'
		) );

		// Merge new templates with existing stack
		// @see bp_get_theme_compat_templates()
		$templates = array_merge( (array) $new_templates, $templates );

		return $templates;
	}

	/**
	 * Update the global $post with single group data.
	 *
	 * @since 1.7.0
	 */
	public function single_dummy_post() {
		bp_theme_compat_reset_post( array(
			'ID'             => 0,
			'post_title'     => bp_get_current_group_name(),
			'post_author'    => 0,
			'post_date'      => 0,
			'post_content'   => '',
			'post_type'      => 'page',
			'post_status'    => 'publish',
			'is_page'        => true,
			'comment_status' => 'closed'
		) );
	}

	/**
	 * Filter the_content with the single group template part.
	 *
	 * @since 1.7.0
	 */
	public function single_content() {
		return bp_buffer_template_part( 'groups/single/home', null, false );
	}
}
new BP_Groups_Theme_Compat();
