<?php
/**
 * BuddyPress Groups Notification Functions.
 *
 * These functions handle the recording, deleting and formatting of notifications
 * for the user and for this specific component.
 *
 * @package BuddyPress
 * @subpackage GroupsActivity
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/** Emails ********************************************************************/

/**
 * Notify all group members when a group is updated.
 *
 * @since 1.0.0
 *
 * @param int                  $group_id  ID of the group.
 * @param BP_Groups_Group|null $old_group Group before new details were saved.
 */
function groups_notification_group_updated( $group_id = 0, $old_group = null ) {

	$group = groups_get_group( array( 'group_id' => $group_id ) );

	if ( $old_group instanceof BP_Groups_Group ) {
		$changed = array();

		if ( $group->name !== $old_group->name ) {
			$changed[] = sprintf(
				_x( '* Name changed from "%s" to "%s"', 'Group update email text', 'buddypress' ),
				esc_html( $old_group->name ),
				esc_html( $group->name )
			);
		}

		if ( $group->description !== $old_group->description ) {
			$changed[] = sprintf(
				_x( '* Description changed from "%s" to "%s"', 'Group update email text', 'buddypress' ),
				esc_html( $old_group->description ),
				esc_html( $group->description )
			);
		}
	}

	/**
	 * Filters the bullet points listing updated items in the email notification after a group is updated.
	 *
	 * @since 2.2.0
	 *
	 * @param array $changed Array of bullet points.
	 */
	$changed = apply_filters( 'groups_notification_group_update_updated_items', $changed );

	$changed_text = '';
	if ( ! empty( $changed ) ) {
		$changed_text = "\n\n" . implode( "\n", $changed );
	}

	$subject  = bp_get_email_subject( array( 'text' => __( 'Group Details Updated', 'buddypress' ) ) );
	$user_ids = BP_Groups_Member::get_group_member_ids( $group->id );

	foreach ( (array) $user_ids as $user_id ) {

		// Continue if member opted out of receiving this email
		if ( 'no' === bp_get_user_meta( $user_id, 'notification_groups_group_updated', true ) ) {
			continue;
		}

		$ud = bp_core_get_core_userdata( $user_id );

		// Set up and send the message
		$to = $ud->user_email;

		$group_link    = bp_get_group_permalink( $group );
		$settings_slug = function_exists( 'bp_get_settings_slug' ) ? bp_get_settings_slug() : 'settings';
		$settings_link = bp_core_get_user_domain( $user_id ) . $settings_slug . '/notifications/';

		$message = sprintf( __(
'Group details for the group "%1$s" were updated: %2$s

To view the group: %3$s

---------------------
', 'buddypress' ), $group->name, $changed_text, $group_link );

		$message .= sprintf( __( 'To disable these notifications please log in and go to: %s', 'buddypress' ), $settings_link );

		/**
		 * Filters the user email that the group update notification will be sent to.
		 *
		 * @since 1.2.0
		 *
		 * @param string $to User email the notification is being sent to.
		 */
		$to      = apply_filters( 'groups_notification_group_updated_to', $to );

		/**
		 * Filters the group update notification subject that will be sent to user.
		 *
		 * @since 1.2.0
		 *
		 * @param string          $subject Email notification subject text.
		 * @param BP_Groups_Group $group   Object holding the current group instance. Passed by reference.
		 */
		$subject = apply_filters_ref_array( 'groups_notification_group_updated_subject', array( $subject, &$group ) );

		/**
		 * Filters the group update notification message that will be sent to user.
		 *
		 * @since 1.2.0
		 *
		 * @param string          $message       Email notification message text.
		 * @param BP_Groups_Group $group         Object holding the current group instance. Passed by reference.
		 * @param string          $group_link    URL permalink to the group that was updated.
		 * @param string          $settings_link URL permalink for the user's notification settings area.
		 */
		$message = apply_filters_ref_array( 'groups_notification_group_updated_message', array( $message, &$group, $group_link, $settings_link ) );

		wp_mail( $to, $subject, $message );

		unset( $message, $to );
	}

	/**
	 * Fires after the notification is sent that a group has been updated.
	 *
	 * See https://buddypress.trac.wordpress.org/ticket/3644 for blank message parameter.
	 *
	 * @since 1.5.0
	 *
	 * @param array  $user_ids Array of user IDs to notify about the update.
	 * @param string $subject  Email notification subject text.
	 * @param string $value    Empty string preventing PHP error.
	 * @param int    $group_id ID of the group that was updated.
	 */
	do_action( 'bp_groups_sent_updated_email', $user_ids, $subject, '', $group_id );
}

/**
 * Notify group admin about new membership request.
 *
 * @since 1.0.0
 *
 * @param int $requesting_user_id ID of the user requesting group membership.
 * @param int $admin_id           ID of the group admin.
 * @param int $group_id           ID of the group.
 * @param int $membership_id      ID of the group membership object.
 *
 * @return false|null False on failure.
 */
function groups_notification_new_membership_request( $requesting_user_id = 0, $admin_id = 0, $group_id = 0, $membership_id = 0 ) {

	// Trigger a BuddyPress Notification
	if ( bp_is_active( 'notifications' ) ) {
		bp_notifications_add_notification( array(
			'user_id'           => $admin_id,
			'item_id'           => $group_id,
			'secondary_item_id' => $requesting_user_id,
			'component_name'    => buddypress()->groups->id,
			'component_action'  => 'new_membership_request'
		) );
	}

	// Bail if member opted out of receiving this email
	if ( 'no' === bp_get_user_meta( $admin_id, 'notification_groups_membership_request', true ) ) {
		return false;
	}

	// Username of the user requesting a membership: %1$s in mail
	$requesting_user_name = bp_core_get_user_displayname( $requesting_user_id );
	$group                = groups_get_group( array( 'group_id' => $group_id ) );

	// Group Administrator user's data
	$ud             = bp_core_get_core_userdata( $admin_id );
	$group_requests = bp_get_group_permalink( $group ) . 'admin/membership-requests';

	// Link to the user's profile who's requesting a membership: %3$s in mail
	$profile_link   = bp_core_get_user_domain( $requesting_user_id );

	$settings_slug  = function_exists( 'bp_get_settings_slug' ) ? bp_get_settings_slug() : 'settings';
	// Link to the group administrator email settings: %s in "disable notifications" part of the email
	$settings_link  = bp_core_get_user_domain( $admin_id ) . $settings_slug . '/notifications/';

	// Fetch the message, if there's one to fetch.
	$membership = new BP_Groups_Member( false, false, $membership_id );

	// Set up and send the message
	$to       = $ud->user_email;
	$subject  = bp_get_email_subject( array( 'text' => sprintf( __( 'Membership request for group: %s', 'buddypress' ), $group->name ) ) );

	if ( ! empty( $membership->comments ) ) {
		$message = sprintf( __(
'%1$s wants to join the group "%2$s".

Message from %1$s: "%3$s"

Because you are the administrator of this group, you must either accept or reject the membership request.

To view all pending membership requests for this group, please visit:
%4$s

To view %5$s\'s profile: %6$s

---------------------
', 'buddypress' ), $requesting_user_name, $group->name, esc_html( $membership->comments ), $group_requests, $requesting_user_name, $profile_link );

	} else {

		$message = sprintf( __(
'%1$s wants to join the group "%2$s".

Because you are the administrator of this group, you must either accept or reject the membership request.

To view all pending membership requests for this group, please visit:
%3$s

To view %4$s\'s profile: %5$s

---------------------
', 'buddypress' ), $requesting_user_name, $group->name, $group_requests, $requesting_user_name, $profile_link );
	}

	// Only show the disable notifications line if the settings component is enabled
	if ( bp_is_active( 'settings' ) ) {
		$message .= sprintf( __( 'To disable these notifications please log in and go to: %s', 'buddypress' ), $settings_link );
	}

	/**
	 * Filters the user email that the group membership request will be sent to.
	 *
	 * @since 1.2.0
	 *
	 * @param string $to User email the request is being sent to.
	 */
	$to      = apply_filters( 'groups_notification_new_membership_request_to', $to );

	/**
	 * Filters the group membership request subject that will be sent to user.
	 *
	 * @since 1.2.0
	 *
	 * @param string          $subject Membership request email subject text.
	 * @param BP_Groups_Group $group   Object holding the current group instance. Passed by reference.
	 */
	$subject = apply_filters_ref_array( 'groups_notification_new_membership_request_subject', array( $subject, &$group ) );

	/**
	 * Filters the group membership request message that will be sent to user.
	 *
	 * @since 1.2.0
	 *
	 * @param string          $message              Membership request email message text.
	 * @param BP_Groups_Group $group                Object holding the current group instance. Passed by reference.
	 * @param string          $requesting_user_name Username of who is requesting membership.
	 * @param string          $profile_link         URL permalink for the profile for the user requesting membership.
	 * @param string          $group_requests       URL permalink for the group requests screen for group being requested membership to.
	 * @param string          $settings_link        URL permalink for the user's notification settings area.
	 */
	$message = apply_filters_ref_array( 'groups_notification_new_membership_request_message', array( $message, &$group, $requesting_user_name, $profile_link, $group_requests, $settings_link ) );

	wp_mail( $to, $subject, $message );

	/**
	 * Fires after the notification is sent that a member has requested group membership.
	 *
	 * @since 1.5.0
	 *
	 * @param int    $admin_id           ID of the group administrator.
	 * @param string $subject            Email notification subject text.
	 * @param string $message            Email notification message text.
	 * @param int    $requesting_user_id ID of the user requesting membership.
	 * @param int    $group_id           ID of the group receiving membership request.
	 * @param int    $membership_id      ID of the group membership object.
	 */
	do_action( 'bp_groups_sent_membership_request_email', $admin_id, $subject, $message, $requesting_user_id, $group_id, $membership_id );
}

/**
 * Notify member about their group membership request.
 *
 * @since 1.0.0
 *
 * @param int  $requesting_user_id ID of the user requesting group membership.
 * @param int  $group_id           ID of the group.
 * @param bool $accepted           Optional. Whether the membership request was accepted.
 *                                 Default: true.
 *
 * @return false|null
 */
function groups_notification_membership_request_completed( $requesting_user_id = 0, $group_id = 0, $accepted = true ) {

	// Trigger a BuddyPress Notification
	if ( bp_is_active( 'notifications' ) ) {

		// What type of acknowledgement
		$type = ! empty( $accepted )
			? 'membership_request_accepted'
			: 'membership_request_rejected';

		bp_notifications_add_notification( array(
			'user_id'           => $requesting_user_id,
			'item_id'           => $group_id,
			'component_name'    => buddypress()->groups->id,
			'component_action'  => $type
		) );
	}

	// Bail if member opted out of receiving this email
	if ( 'no' === bp_get_user_meta( $requesting_user_id, 'notification_membership_request_completed', true ) ) {
		return false;
	}

	$group         = groups_get_group( array( 'group_id' => $group_id ) );
	$ud            = bp_core_get_core_userdata( $requesting_user_id );
	$group_link    = bp_get_group_permalink( $group );
	$settings_slug = function_exists( 'bp_get_settings_slug' ) ? bp_get_settings_slug() : 'settings';
	$settings_link = bp_core_get_user_domain( $requesting_user_id ) . $settings_slug . '/notifications/';
	$to            = $ud->user_email;

	// Set up and send the message
	if ( ! empty( $accepted ) ) {
		$subject = bp_get_email_subject( array( 'text' => sprintf( __( 'Membership request for group "%s" accepted', 'buddypress' ), $group->name ) ) );
		$message = sprintf( __(
'Your membership request for the group "%1$s" has been accepted.

To view the group please login and visit: %2$s

---------------------
', 'buddypress' ), $group->name, $group_link );

	} else {
		$subject = bp_get_email_subject( array( 'text' => sprintf( __( 'Membership request for group "%s" rejected', 'buddypress' ), $group->name ) ) );
		$message = sprintf( __(
'Your membership request for the group "%1$s" has been rejected.

To submit another request please log in and visit: %2$s

---------------------
', 'buddypress' ), $group->name, $group_link );
	}

	// Only show the disable notifications line if the settings component is enabled
	if ( bp_is_active( 'settings' ) ) {
		$message .= sprintf( __( 'To disable these notifications please log in and go to: %s', 'buddypress' ), $settings_link );
	}

	/**
	 * Filters the user email that the group membership request result will be sent to.
	 *
	 * @since 1.2.0
	 *
	 * @param string $to User email the request result is being sent to.
	 */
	$to      = apply_filters( 'groups_notification_membership_request_completed_to', $to );

	/**
	 * Filters the group membership request result subject that will be sent to user.
	 *
	 * @since 1.2.0
	 *
	 * @param string          $subject Membership request result email subject text.
	 * @param BP_Groups_Group $group   Object holding the current group instance. Passed by reference.
	 */
	$subject = apply_filters_ref_array( 'groups_notification_membership_request_completed_subject', array( $subject, &$group ) );

	/**
	 * Filters the group membership request result message that will be sent to user.
	 *
	 * @since 1.2.0
	 *
	 * @param string          $message       Membership request result email message text.
	 * @param BP_Groups_Group $group         Object holding the current group instance. Passed by reference.
	 * @param string          $group_link    URL permalink for the group that was requested membership for.
	 * @param string          $settings_link URL permalink for the user's notification settings area.
	 */
	$message = apply_filters_ref_array( 'groups_notification_membership_request_completed_message', array( $message, &$group, $group_link, $settings_link ) );

	wp_mail( $to, $subject, $message );

	/**
	 * Fires after the notification is sent that a membership has been approved.
	 *
	 * @since 1.5.0
	 *
	 * @param int    $requesting_user_id ID of the user whose membership was approved.
	 * @param string $subject            Email notification subject text.
	 * @param string $message            Email notification message text.
	 * @param int    $group_id           ID of the group that was joined.
	 */
	do_action( 'bp_groups_sent_membership_approved_email', $requesting_user_id, $subject, $message, $group_id );
}
add_action( 'groups_membership_accepted', 'groups_notification_membership_request_completed', 10, 3 );
add_action( 'groups_membership_rejected', 'groups_notification_membership_request_completed', 10, 3 );

/**
 * Notify group member they have been promoted.
 *
 * @since 1.0.0
 *
 * @param int $user_id  ID of the user.
 * @param int $group_id ID of the group.
 *
 * @return false|null False on failure.
 */
function groups_notification_promoted_member( $user_id = 0, $group_id = 0 ) {

	// What type of promotion is this?
	if ( groups_is_user_admin( $user_id, $group_id ) ) {
		$promoted_to = __( 'an administrator', 'buddypress' );
		$type        = 'member_promoted_to_admin';
	} else {
		$promoted_to = __( 'a moderator', 'buddypress' );
		$type        = 'member_promoted_to_mod';
	}

	// Trigger a BuddyPress Notification
	if ( bp_is_active( 'notifications' ) ) {
		bp_notifications_add_notification( array(
			'user_id'           => $user_id,
			'item_id'           => $group_id,
			'component_name'    => buddypress()->groups->id,
			'component_action'  => $type
		) );
	}

	// Bail if admin opted out of receiving this email
	if ( 'no' === bp_get_user_meta( $user_id, 'notification_groups_admin_promotion', true ) ) {
		return false;
	}

	$group         = groups_get_group( array( 'group_id' => $group_id ) );
	$ud            = bp_core_get_core_userdata($user_id);
	$group_link    = bp_get_group_permalink( $group );
	$settings_slug = function_exists( 'bp_get_settings_slug' ) ? bp_get_settings_slug() : 'settings';
	$settings_link = bp_core_get_user_domain( $user_id ) . $settings_slug . '/notifications/';

	// Set up and send the message
	$to       = $ud->user_email;
	$subject  = bp_get_email_subject( array( 'text' => sprintf( __( 'You have been promoted in the group: "%s"', 'buddypress' ), $group->name ) ) );
	$message  = sprintf( __(
'You have been promoted to %1$s for the group: "%2$s".

To view the group please visit: %3$s

---------------------
', 'buddypress' ), $promoted_to, $group->name, $group_link );

	// Only show the disable notifications line if the settings component is enabled
	if ( bp_is_active( 'settings' ) ) {
		$message .= sprintf( __( 'To disable these notifications please log in and go to: %s', 'buddypress' ), $settings_link );
	}

	/**
	 * Filters the user email that the group promotion notification will be sent to.
	 *
	 * @since 1.2.0
	 *
	 * @param string $to User email the promotion notification is being sent to.
	 */
	$to      = apply_filters( 'groups_notification_promoted_member_to', $to );

	/**
	 * Filters the group promotion notification subject that will be sent to user.
	 *
	 * @since 1.2.0
	 *
	 * @param string          $subject Promotion notification email subject text.
	 * @param BP_Groups_Group $group   Object holding the current group instance. Passed by reference.
	 */
	$subject = apply_filters_ref_array( 'groups_notification_promoted_member_subject', array( $subject, &$group ) );

	/**
	 * Filters the group promotion notification message that will be sent to user.
	 *
	 * @since 1.2.0
	 *
	 * @param string          $message       Promotion notification email message text.
	 * @param BP_Groups_Group $group         Object holding the current group instance. Passed by reference.
	 * @param string          $promoted_to   Role that the user was promoted to within the group.
	 * @param string          $group_link    URL permalink for the group that the promotion was related to.
	 * @param string          $settings_link URL permalink for the user's notification settings area.
	 */
	$message = apply_filters_ref_array( 'groups_notification_promoted_member_message', array( $message, &$group, $promoted_to, $group_link, $settings_link ) );

	wp_mail( $to, $subject, $message );

	/**
	 * Fires after the notification is sent that a member has been promoted.
	 *
	 * @since 1.5.0
	 *
	 * @param int    $user_id  ID of the user who was promoted.
	 * @param string $subject  Email notification subject text.
	 * @param string $message  Email notification message text.
	 * @param int    $group_id ID of the group that the user is a member of.
	 */
	do_action( 'bp_groups_sent_promoted_email', $user_id, $subject, $message, $group_id );
}
add_action( 'groups_promoted_member', 'groups_notification_promoted_member', 10, 2 );

/**
 * Notify a member they have been invited to a group.
 *
 * @since 1.0.0
 *
 * @param BP_Groups_Group  $group           Group object.
 * @param BP_Groups_Member $member          Member object.
 * @param int              $inviter_user_id ID of the user who sent the invite.
 *
 * @return null|false False on failure.
 */
function groups_notification_group_invites( &$group, &$member, $inviter_user_id ) {

	// Bail if member has already been invited
	if ( ! empty( $member->invite_sent ) ) {
		return;
	}

	// @todo $inviter_ud may be used for caching, test without it
	$inviter_ud   = bp_core_get_core_userdata( $inviter_user_id );
	$inviter_name = bp_core_get_userlink( $inviter_user_id, true, false, true );
	$inviter_link = bp_core_get_user_domain( $inviter_user_id );
	$group_link   = bp_get_group_permalink( $group );

	// Setup the ID for the invited user
	$invited_user_id = $member->user_id;

	// Trigger a BuddyPress Notification
	if ( bp_is_active( 'notifications' ) ) {
		bp_notifications_add_notification( array(
			'user_id'          => $invited_user_id,
			'item_id'          => $group->id,
			'component_name'   => buddypress()->groups->id,
			'component_action' => 'group_invite'
		) );
	}

	// Bail if member opted out of receiving this email
	if ( 'no' === bp_get_user_meta( $invited_user_id, 'notification_groups_invite', true ) ) {
		return false;
	}

	$invited_ud    = bp_core_get_core_userdata( $invited_user_id );
	$settings_slug = function_exists( 'bp_get_settings_slug' ) ? bp_get_settings_slug() : 'settings';
	$settings_link = bp_core_get_user_domain( $invited_user_id ) . $settings_slug . '/notifications/';
	$invited_link  = bp_core_get_user_domain( $invited_user_id );
	$invites_link  = trailingslashit( $invited_link . bp_get_groups_slug() . '/invites' );

	// Set up and send the message
	$to       = $invited_ud->user_email;
	$subject  = bp_get_email_subject( array( 'text' => sprintf( __( 'You have an invitation to the group: "%s"', 'buddypress' ), $group->name ) ) );
	$message  = sprintf( __(
'One of your friends %1$s has invited you to the group: "%2$s".

To view your group invites visit: %3$s

To view the group visit: %4$s

To view %5$s\'s profile visit: %6$s

---------------------
', 'buddypress' ), $inviter_name, $group->name, $invites_link, $group_link, $inviter_name, $inviter_link );

	// Only show the disable notifications line if the settings component is enabled
	if ( bp_is_active( 'settings' ) ) {
		$message .= sprintf( __( 'To disable these notifications please log in and go to: %s', 'buddypress' ), $settings_link );
	}

	/**
	 * Filters the user email that the group invite notification will be sent to.
	 *
	 * @since 1.2.0
	 *
	 * @param string $to User email the invite notification is being sent to.
	 */
	$to      = apply_filters( 'groups_notification_group_invites_to', $to );

	/**
	 * Filters the group invite notification subject that will be sent to user.
	 *
	 * @since 1.2.0
	 *
	 * @param string          $subject Invite notification email subject text.
	 * @param BP_Groups_Group $group   Object holding the current group instance. Passed by reference.
	 */
	$subject = apply_filters_ref_array( 'groups_notification_group_invites_subject', array( $subject, &$group ) );

	/**
	 * Filters the group invite notification message that will be sent to user.
	 *
	 * @since 1.2.0
	 *
	 * @param string          $message       Invite notification email message text.
	 * @param BP_Groups_Group $group         Object holding the current group instance. Passed by reference.
	 * @param string          $inviter_name  Username for the person doing the inviting.
	 * @param string          $inviter_link  Profile link for the person doing the inviting.
	 * @param string          $invites_link  URL permalink for the invited user's invite management screen.
	 * @param string          $group_link    URL permalink for the group that the invite was related to.
	 * @param string          $settings_link URL permalink for the user's notification settings area.
	 */
	$message = apply_filters_ref_array( 'groups_notification_group_invites_message', array( $message, &$group, $inviter_name, $inviter_link, $invites_link, $group_link, $settings_link ) );

	wp_mail( $to, $subject, $message );

	/**
	 * Fires after the notification is sent that a member has been invited to a group.
	 *
	 * @since 1.5.0
	 *
	 * @param int             $invited_user_id  ID of the user who was invited.
	 * @param string          $subject          Email notification subject text.
	 * @param string          $message          Email notification message text.
	 * @param BP_Groups_Group $group            Group object.
	 */
	do_action( 'bp_groups_sent_invited_email', $invited_user_id, $subject, $message, $group );
}

/** Notifications *************************************************************/

/**
 * Format notifications for the Groups component.
 *
 * @since 1.0.0
 *
 * @param string $action            The kind of notification being rendered.
 * @param int    $item_id           The primary item ID.
 * @param int    $secondary_item_id The secondary item ID.
 * @param int    $total_items       The total number of messaging-related notifications
 *                                  waiting for the user.
 * @param string $format            'string' for BuddyBar-compatible notifications; 'array'
 *                                  for WP Toolbar. Default: 'string'.
 *
 * @return string
 */
function groups_format_notifications( $action, $item_id, $secondary_item_id, $total_items, $format = 'string' ) {

	switch ( $action ) {
		case 'new_membership_request':
			$group_id = $item_id;
			$requesting_user_id = $secondary_item_id;

			$group = groups_get_group( array( 'group_id' => $group_id ) );
			$group_link = bp_get_group_permalink( $group );
			$amount = 'single';

			// Set up the string and the filter
			// Because different values are passed to the filters, we'll return the
			// values inline
			if ( (int) $total_items > 1 ) {
				$text = sprintf( __( '%1$d new membership requests for the group "%2$s"', 'buddypress' ), (int) $total_items, $group->name );
				$amount = 'multiple';
				$notification_link = $group_link . 'admin/membership-requests/?n=1';

				if ( 'string' == $format ) {

					/**
					 * Filters groups multiple new membership request notification for string format.
					 *
					 * This is a dynamic filter that is dependent on item count and action.
					 * Complete filter - bp_groups_multiple_new_membership_requests_notification.
					 *
					 * @since 1.0.0
					 *
					 * @param string $string            HTML anchor tag for request.
					 * @param string $group_link        The permalink for the group.
					 * @param int    $total_items       Total number of membership requests.
					 * @param string $group->name       Name of the group.
					 * @param string $text              Notification content.
					 * @param string $notification_link The permalink for notification.
					 */
					return apply_filters( 'bp_groups_' . $amount . '_' . $action . 's_notification', '<a href="' . $notification_link . '" title="' . __( 'Group Membership Requests', 'buddypress' ) . '">' . $text . '</a>', $group_link, $total_items, $group->name, $text, $notification_link );
				} else {

					/**
					 * Filters groups multiple new membership request notification for any non-string format.
					 *
					 * This is a dynamic filter that is dependent on item count and action.
					 * Complete filter - bp_groups_multiple_new_membership_requests_notification.
					 *
					 * @since 1.0.0
					 *
					 * @param array  $array             Array holding permalink and content for notification.
					 * @param string $group_link        The permalink for the group.
					 * @param int    $total_items       Total number of membership requests.
					 * @param string $group->name       Name of the group.
					 * @param string $text              Notification content.
					 * @param string $notification_link The permalink for notification.
					 */
					return apply_filters( 'bp_groups_' . $amount . '_' . $action . 's_notification', array(
						'link' => $notification_link,
						'text' => $text
					), $group_link, $total_items, $group->name, $text, $notification_link );
				}
			} else {
				$user_fullname = bp_core_get_user_displayname( $requesting_user_id );
				$text = sprintf( __( '%s requests group membership', 'buddypress' ), $user_fullname );
				$notification_link = $group_link . 'admin/membership-requests/?n=1';

				if ( 'string' == $format ) {

					/**
					 * Filters groups single new membership request notification for string format.
					 *
					 * This is a dynamic filter that is dependent on item count and action.
					 * Complete filter - bp_groups_single_new_membership_request_notification.
					 *
					 * @since 1.0.0
					 *
					 * @param string $string            HTML anchor tag for request.
					 * @param string $group_link        The permalink for the group.
					 * @param string $user_fullname     Full name of requesting user.
					 * @param string $group->name       Name of the group.
					 * @param string $text              Notification content.
					 * @param string $notification_link The permalink for notification.
					 */
					return apply_filters( 'bp_groups_' . $amount . '_' . $action . '_notification', '<a href="' . $notification_link . '" title="' . sprintf( __( '%s requests group membership', 'buddypress' ), $user_fullname ) . '">' . $text . '</a>', $group_link, $user_fullname, $group->name, $text, $notification_link );
				} else {

					/**
					 * Filters groups single new membership request notification for any non-string format.
					 *
					 * This is a dynamic filter that is dependent on item count and action.
					 * Complete filter - bp_groups_single_new_membership_request_notification.
					 *
					 * @since 1.0.0
					 *
					 * @param array  $array             Array holding permalink and content for notification.
					 * @param string $group_link        The permalink for the group.
					 * @param string $user_fullname     Full name of requesting user.
					 * @param string $group->name       Name of the group.
					 * @param string $text              Notification content.
					 * @param string $notification_link The permalink for notification.
					 */
					return apply_filters( 'bp_groups_' . $amount . '_' . $action . '_notification', array(
						'link' => $notification_link,
						'text' => $text
					), $group_link, $user_fullname, $group->name, $text, $notification_link );
				}
			}

			break;

		case 'membership_request_accepted':
			$group_id = $item_id;

			$group = groups_get_group( array( 'group_id' => $group_id ) );
			$group_link = bp_get_group_permalink( $group );
			$amount = 'single';

			if ( (int) $total_items > 1 ) {
				$text = sprintf( __( '%d accepted group membership requests', 'buddypress' ), (int) $total_items, $group->name );
				$amount = 'multiple';
				$notification_link = trailingslashit( bp_loggedin_user_domain() . bp_get_groups_slug() ) . '?n=1';

				if ( 'string' == $format ) {

					/**
					 * Filters multiple accepted group membership requests notification for string format.
					 * Complete filter - bp_groups_multiple_membership_request_accepted_notification.
					 *
					 * @since 1.0.0
					 *
					 * @param string $string            HTML anchor tag for notification.
					 * @param int    $total_items       Total number of accepted requests.
					 * @param string $group->name       Name of the group.
					 * @param string $text              Notification content.
					 * @param string $notification_link The permalink for notification.
					 */
					return apply_filters( 'bp_groups_' . $amount . '_' . $action . '_notification', '<a href="' . $notification_link . '" title="' . __( 'Groups', 'buddypress' ) . '">' . $text . '</a>', $total_items, $group->name, $text, $notification_link );
				} else {

					/**
					 * Filters multiple accepted group membership requests notification for non-string format.
					 * Complete filter - bp_groups_multiple_membership_request_accepted_notification.
					 *
					 * @since 1.0.0
					 *
					 * @param array  $array             Array holding permalink and content for notification
					 * @param int    $total_items       Total number of accepted requests.
					 * @param string $group->name       Name of the group.
					 * @param string $text              Notification content.
					 * @param string $notification_link The permalink for notification.
					 */
					return apply_filters( 'bp_groups_' . $amount . '_' . $action . '_notification', array(
						'link' => $notification_link,
						'text' => $text
					), $total_items, $group->name, $text, $notification_link );
				}
			} else {
				$text = sprintf( __( 'Membership for group "%s" accepted', 'buddypress' ), $group->name );
				$filter = 'bp_groups_single_membership_request_accepted_notification';
				$notification_link = $group_link . '?n=1';

				if ( 'string' == $format ) {

					/**
					 * Filters single accepted group membership request notification for string format.
					 * Complete filter - bp_groups_single_membership_request_accepted_notification.
					 *
					 * @since 1.0.0
					 *
					 * @param string $string            HTML anchor tag for notification.
					 * @param string $group_link        The permalink for the group.
					 * @param string $group->name       Name of the group.
					 * @param string $text              Notification content.
					 * @param string $notification_link The permalink for notification.
					 */
					return apply_filters( 'bp_groups_' . $amount . '_' . $action . '_notification', '<a href="' . $notification_link . '">' . $text . '</a>', $group_link, $group->name, $text, $notification_link );
				} else {

					/**
					 * Filters single accepted group membership request notification for non-string format.
					 * Complete filter - bp_groups_single_membership_request_accepted_notification.
					 *
					 * @since 1.0.0
					 *
					 * @param array  $array             Array holding permalink and content for notification.
					 * @param string $group_link        The permalink for the group.
					 * @param string $group->name       Name of the group.
					 * @param string $text              Notification content.
					 * @param string $notification_link The permalink for notification.
					 */
					return apply_filters( $filter, array(
						'link' => $notification_link,
						'text' => $text
					), $group_link, $group->name, $text, $notification_link );
				}
			}

			break;

		case 'membership_request_rejected':
			$group_id = $item_id;

			$group = groups_get_group( array( 'group_id' => $group_id ) );
			$group_link = bp_get_group_permalink( $group );
			$amount = 'single';

			if ( (int) $total_items > 1 ) {
				$text = sprintf( __( '%d rejected group membership requests', 'buddypress' ), (int) $total_items, $group->name );
				$amount = 'multiple';
				$notification_link = trailingslashit( bp_loggedin_user_domain() . bp_get_groups_slug() ) . '?n=1';

				if ( 'string' == $format ) {

					/**
					 * Filters multiple rejected group membership requests notification for string format.
					 * Complete filter - bp_groups_multiple_membership_request_rejected_notification.
					 *
					 * @since 1.0.0
					 *
					 * @param string $string            HTML anchor tag for notification.
					 * @param int    $total_items       Total number of rejected requests.
					 * @param string $group->name       Name of the group.
					 * @param string $text              Notification content.
					 * @param string $notification_link The permalink for notification.
					 */
					return apply_filters( 'bp_groups_' . $amount . '_' . $action . '_notification', '<a href="' . $notification_link . '" title="' . __( 'Groups', 'buddypress' ) . '">' . $text . '</a>', $total_items, $group->name );
				} else {

					/**
					 * Filters multiple rejected group membership requests notification for non-string format.
					 * Complete filter - bp_groups_multiple_membership_request_rejected_notification.
					 *
					 * @since 1.0.0
					 *
					 * @param array  $array             Array holding permalink and content for notification.
					 * @param int    $total_items       Total number of rejected requests.
					 * @param string $group->name       Name of the group.
					 * @param string $text              Notification content.
					 * @param string $notification_link The permalink for notification.
					 */
					return apply_filters( 'bp_groups_' . $amount . '_' . $action . '_notification', array(
						'link' => $notification_link,
						'text' => $text
					), $total_items, $group->name, $text, $notification_link );
				}
			} else {
				$text = sprintf( __( 'Membership for group "%s" rejected', 'buddypress' ), $group->name );
				$notification_link = $group_link . '?n=1';

				if ( 'string' == $format ) {

					/**
					 * Filters single rejected group membership requests notification for string format.
					 * Complete filter - bp_groups_single_membership_request_rejected_notification.
					 *
					 * @since 1.0.0
					 *
					 * @param string $string            HTML anchor tag for notification.
					 * @param int    $group_link        The permalink for the group.
					 * @param string $group->name       Name of the group.
					 * @param string $text              Notification content.
					 * @param string $notification_link The permalink for notification.
					 */
					return apply_filters( 'bp_groups_' . $amount . '_' . $action . '_notification', '<a href="' . $notification_link . '">' . $text . '</a>', $group_link, $group->name, $text, $notification_link );
				} else {

					/**
					 * Filters single rejected group membership requests notification for non-string format.
					 * Complete filter - bp_groups_single_membership_request_rejected_notification.
					 *
					 * @since 1.0.0
					 *
					 * @param array  $array             Array holding permalink and content for notification.
					 * @param int    $group_link        The permalink for the group.
					 * @param string $group->name       Name of the group.
					 * @param string $text              Notification content.
					 * @param string $notification_link The permalink for notification.
					 */
					return apply_filters( 'bp_groups_' . $amount . '_' . $action . '_notification', array(
						'link' => $notification_link,
						'text' => $text
					), $group_link, $group->name, $text, $notification_link );
				}
			}

			break;

		case 'member_promoted_to_admin':
			$group_id = $item_id;

			$group = groups_get_group( array( 'group_id' => $group_id ) );
			$group_link = bp_get_group_permalink( $group );
			$amount = 'single';

			if ( (int) $total_items > 1 ) {
				$text = sprintf( __( 'You were promoted to an admin in %d groups', 'buddypress' ), (int) $total_items );
				$amount = 'multiple';
				$notification_link = trailingslashit( bp_loggedin_user_domain() . bp_get_groups_slug() ) . '?n=1';

				if ( 'string' == $format ) {
					/**
					 * Filters multiple promoted to group admin notification for string format.
					 * Complete filter - bp_groups_multiple_member_promoted_to_admin_notification.
					 *
					 * @since 1.0.0
					 *
					 * @param string $string            HTML anchor tag for notification.
					 * @param int    $total_items       Total number of rejected requests.
					 * @param string $text              Notification content.
					 * @param string $notification_link The permalink for notification.
					 */
					return apply_filters( 'bp_groups_' . $amount . '_' . $action . '_notification', '<a href="' . $notification_link . '" title="' . __( 'Groups', 'buddypress' ) . '">' . $text . '</a>', $total_items, $text, $notification_link );
				} else {
					/**
					 * Filters multiple promoted to group admin notification for non-string format.
					 * Complete filter - bp_groups_multiple_member_promoted_to_admin_notification.
					 *
					 * @since 1.0.0
					 *
					 * @param array  $array             Array holding permalink and content for notification.
					 * @param int    $total_items       Total number of rejected requests.
					 * @param string $text              Notification content.
					 * @param string $notification_link The permalink for notification.
					 */
					return apply_filters( 'bp_groups_' . $amount . '_' . $action . '_notification', array(
						'link' => $notification_link,
						'text' => $text
					), $total_items, $text, $notification_link );
				}
			} else {
				$text = sprintf( __( 'You were promoted to an admin in the group "%s"', 'buddypress' ), $group->name );
				$notification_link = $group_link . '?n=1';

				if ( 'string' == $format ) {
					/**
					 * Filters single promoted to group admin notification for non-string format.
					 * Complete filter - bp_groups_single_member_promoted_to_admin_notification.
					 *
					 * @since 1.0.0
					 *
					 * @param string $string            HTML anchor tag for notification.
					 * @param int    $group_link        The permalink for the group.
					 * @param string $group->name       Name of the group.
					 * @param string $text              Notification content.
					 * @param string $notification_link The permalink for notification.
					 */
					return apply_filters( 'bp_groups_' . $amount . '_' . $action . '_notification', '<a href="' . $notification_link . '">' . $text . '</a>', $group_link, $group->name, $text, $notification_link );
				} else {
					/**
					 * Filters single promoted to group admin notification for non-string format.
					 * Complete filter - bp_groups_single_member_promoted_to_admin_notification.
					 *
					 * @since 1.0.0
					 *
					 * @param array  $array             Array holding permalink and content for notification.
					 * @param int    $group_link        The permalink for the group.
					 * @param string $group->name       Name of the group.
					 * @param string $text              Notification content.
					 * @param string $notification_link The permalink for notification.
					 */
					return apply_filters( 'bp_groups_' . $amount . '_' . $action . '_notification', array(
						'link' => $notification_link,
						'text' => $text
					), $group_link, $group->name, $text, $notification_link );
				}
			}

			break;

		case 'member_promoted_to_mod':
			$group_id = $item_id;

			$group = groups_get_group( array( 'group_id' => $group_id ) );
			$group_link = bp_get_group_permalink( $group );
			$amount = 'single';

			if ( (int) $total_items > 1 ) {
				$text = sprintf( __( 'You were promoted to a mod in %d groups', 'buddypress' ), (int) $total_items );
				$amount = 'multiple';
				$notification_link = trailingslashit( bp_loggedin_user_domain() . bp_get_groups_slug() ) . '?n=1';

				if ( 'string' == $format ) {
					/**
					 * Filters multiple promoted to group mod notification for string format.
					 * Complete filter - bp_groups_multiple_member_promoted_to_mod_notification.
					 *
					 * @since 1.0.0
					 *
					 * @param string $string            HTML anchor tag for notification.
					 * @param int    $total_items       Total number of rejected requests.
					 * @param string $text              Notification content.
					 * @param string $notification_link The permalink for notification.
					 */
					return apply_filters( 'bp_groups_' . $amount . '_' . $action . '_notification', '<a href="' . $notification_link . '" title="' . __( 'Groups', 'buddypress' ) . '">' . $text . '</a>', $total_items, $text, $notification_link );
				} else {
					/**
					 * Filters multiple promoted to group mod notification for non-string format.
					 * Complete filter - bp_groups_multiple_member_promoted_to_mod_notification.
					 *
					 * @since 1.0.0
					 *
					 * @param array  $array             Array holding permalink and content for notification.
					 * @param int    $total_items       Total number of rejected requests.
					 * @param string $text              Notification content.
					 * @param string $notification_link The permalink for notification.
					 */
					return apply_filters( 'bp_groups_' . $amount . '_' . $action . '_notification', array(
						'link' => $notification_link,
						'text' => $text
					), $total_items, $text, $notification_link );
				}
			} else {
				$text = sprintf( __( 'You were promoted to a mod in the group "%s"', 'buddypress' ), $group->name );
				$notification_link = $group_link . '?n=1';

				if ( 'string' == $format ) {
					/**
					 * Filters single promoted to group mod notification for string format.
					 * Complete filter - bp_groups_single_member_promoted_to_mod_notification.
					 *
					 * @since 1.0.0
					 *
					 * @param string $string            HTML anchor tag for notification.
					 * @param int    $group_link        The permalink for the group.
					 * @param string $group->name       Name of the group.
					 * @param string $text              Notification content.
					 * @param string $notification_link The permalink for notification.
					 */
					return apply_filters( 'bp_groups_' . $amount . '_' . $action . '_notification', '<a href="' . $notification_link . '">' . $text . '</a>', $group_link, $group->name, $text, $notification_link );
				} else {
					/**
					 * Filters single promoted to group admin notification for non-string format.
					 * Complete filter - bp_groups_single_member_promoted_to_mod_notification.
					 *
					 * @since 1.0.0
					 *
					 * @param array  $array             Array holding permalink and content for notification.
					 * @param int    $group_link        The permalink for the group.
					 * @param string $group->name       Name of the group.
					 * @param string $text              Notification content.
					 * @param string $notification_link The permalink for notification.
					 */
					return apply_filters( 'bp_groups_' . $amount . '_' . $action . '_notification', array(
						'link' => $notification_link,
						'text' => $text
					), $group_link, $group->name, $text, $notification_link );
				}
			}

			break;

		case 'group_invite':
			$group_id = $item_id;
			$group = groups_get_group( array( 'group_id' => $group_id ) );
			$group_link = bp_get_group_permalink( $group );
			$amount = 'single';

			$notification_link = bp_loggedin_user_domain() . bp_get_groups_slug() . '/invites/?n=1';

			if ( (int) $total_items > 1 ) {
				$text = sprintf( __( 'You have %d new group invitations', 'buddypress' ), (int) $total_items );
				$amount = 'multiple';

				if ( 'string' == $format ) {
					/**
					 * Filters multiple group invitation notification for string format.
					 * Complete filter - bp_groups_multiple_group_invite_notification.
					 *
					 * @since 1.0.0
					 *
					 * @param string $string            HTML anchor tag for notification.
					 * @param int    $total_items       Total number of rejected requests.
					 * @param string $text              Notification content.
					 * @param string $notification_link The permalink for notification.
					 */
					return apply_filters( 'bp_groups_' . $amount . '_' . $action . '_notification', '<a href="' . $notification_link . '" title="' . __( 'Group Invites', 'buddypress' ) . '">' . $text . '</a>', $total_items, $text, $notification_link );
				} else {
					/**
					 * Filters multiple group invitation notification for non-string format.
					 * Complete filter - bp_groups_multiple_group_invite_notification.
					 *
					 * @since 1.0.0
					 *
					 * @param array  $array             Array holding permalink and content for notification.
					 * @param int    $total_items       Total number of rejected requests.
					 * @param string $text              Notification content.
					 * @param string $notification_link The permalink for notification.
					 */
					return apply_filters( 'bp_groups_' . $amount . '_' . $action . '_notification', array(
						'link' => $notification_link,
						'text' => $text
					), $total_items, $text, $notification_link );
				}
			} else {
				$text = sprintf( __( 'You have an invitation to the group: %s', 'buddypress' ), $group->name );
				$filter = 'bp_groups_single_group_invite_notification';

				if ( 'string' == $format ) {
					/**
					 * Filters single group invitation notification for string format.
					 * Complete filter - bp_groups_single_group_invite_notification.
					 *
					 * @since 1.0.0
					 *
					 * @param string $string            HTML anchor tag for notification.
					 * @param int    $group_link        The permalink for the group.
					 * @param string $group->name       Name of the group.
					 * @param string $text              Notification content.
					 * @param string $notification_link The permalink for notification.
					 */
					return apply_filters( 'bp_groups_' . $amount . '_' . $action . '_notification', '<a href="' . $notification_link . '">' . $text . '</a>', $group_link, $group->name, $text, $notification_link );
				} else {
					/**
					 * Filters single group invitation notification for non-string format.
					 * Complete filter - bp_groups_single_group_invite_notification.
					 *
					 * @since 1.0.0
					 *
					 * @param array  $array             Array holding permalink and content for notification.
					 * @param int    $group_link        The permalink for the group.
					 * @param string $group->name       Name of the group.
					 * @param string $text              Notification content.
					 * @param string $notification_link The permalink for notification.
					 */
					return apply_filters( 'bp_groups_' . $amount . '_' . $action . '_notification', array(
						'link' => $notification_link,
						'text' => $text
					), $group_link, $group->name, $text, $notification_link );
				}
			}

			break;

		default:

			/**
			 * Filters plugin-added group-related custom component_actions.
			 *
			 * @since 2.4.0
			 *
			 * @param string $notification      Null value.
			 * @param int    $item_id           The primary item ID.
			 * @param int    $secondary_item_id The secondary item ID.
			 * @param int    $total_items       The total number of messaging-related notifications
			 *                                  waiting for the user.
			 * @param string $format            'string' for BuddyBar-compatible notifications;
			 *                                  'array' for WP Toolbar.
			 */
			$custom_action_notification = apply_filters( 'bp_groups_' . $action . '_notification', null, $item_id, $secondary_item_id, $total_items, $format );

			if ( ! is_null( $custom_action_notification ) ) {
				return $custom_action_notification;
			}

			break;
	}

	/**
	 * Fires right before returning the formatted group notifications.
	 *
	 * @since 1.0.0
	 *
	 * @param string $action            The type of notification being rendered.
	 * @param int    $item_id           The primary item ID.
	 * @param int    $secondary_item_id The secondary item ID.
	 * @param int    $total_items       Total amount of items to format.
	 */
	do_action( 'groups_format_notifications', $action, $item_id, $secondary_item_id, $total_items );

	return false;
}

/**
 * Remove all notifications for any member belonging to a specific group.
 *
 * @since 1.9.0
 *
 * @param int $group_id ID of the group.
 */
function bp_groups_delete_group_delete_all_notifications( $group_id ) {
	if ( bp_is_active( 'notifications' ) ) {
		bp_notifications_delete_all_notifications_by_type( $group_id, buddypress()->groups->id );
	}
}
add_action( 'groups_delete_group', 'bp_groups_delete_group_delete_all_notifications', 10 );

/**
 * When a demotion takes place, delete any corresponding promotion notifications.
 *
 * @since 2.0.0
 *
 * @param int $user_id  ID of the user.
 * @param int $group_id ID of the group.
 */
function bp_groups_delete_promotion_notifications( $user_id = 0, $group_id = 0 ) {
	if ( bp_is_active( 'notifications' ) && ! empty( $group_id ) && ! empty( $user_id ) ) {
		bp_notifications_delete_notifications_by_item_id( $user_id, $group_id, buddypress()->groups->id, 'member_promoted_to_admin' );
		bp_notifications_delete_notifications_by_item_id( $user_id, $group_id, buddypress()->groups->id, 'member_promoted_to_mod' );
	}
}
add_action( 'groups_demoted_member', 'bp_groups_delete_promotion_notifications', 10, 2 );

/**
 * Mark notifications read when a member accepts a group invitation.
 *
 * @since 1.9.0
 *
 * @param int $user_id  ID of the user.
 * @param int $group_id ID of the group.
 */
function bp_groups_accept_invite_mark_notifications( $user_id, $group_id ) {
	if ( bp_is_active( 'notifications' ) ) {
		bp_notifications_mark_notifications_by_item_id( $user_id, $group_id, buddypress()->groups->id, 'group_invite' );
	}
}
add_action( 'groups_accept_invite', 'bp_groups_accept_invite_mark_notifications', 10, 2 );
add_action( 'groups_reject_invite', 'bp_groups_accept_invite_mark_notifications', 10, 2 );
add_action( 'groups_delete_invite', 'bp_groups_accept_invite_mark_notifications', 10, 2 );

/**
 * Mark notifications read when a member views their group memberships.
 *
 * @since 1.9.0
 */
function bp_groups_screen_my_groups_mark_notifications() {

	// Delete group request notifications for the user
	if ( isset( $_GET['n'] ) && bp_is_active( 'notifications' ) ) {

		// Get the necessary ID's
		$group_id = buddypress()->groups->id;
		$user_id  = bp_loggedin_user_id();

		// Mark notifications read
		bp_notifications_mark_notifications_by_type( $user_id, $group_id, 'membership_request_accepted' );
		bp_notifications_mark_notifications_by_type( $user_id, $group_id, 'membership_request_rejected' );
		bp_notifications_mark_notifications_by_type( $user_id, $group_id, 'member_promoted_to_mod'      );
		bp_notifications_mark_notifications_by_type( $user_id, $group_id, 'member_promoted_to_admin'    );
	}
}
add_action( 'groups_screen_my_groups',  'bp_groups_screen_my_groups_mark_notifications', 10 );
add_action( 'groups_screen_group_home', 'bp_groups_screen_my_groups_mark_notifications', 10 );

/**
 * Mark group invitation notifications read when a member views their invitations.
 *
 * @since 1.9.0
 */
function bp_groups_screen_invites_mark_notifications() {
	if ( bp_is_active( 'notifications' ) ) {
		bp_notifications_mark_notifications_by_type( bp_loggedin_user_id(), buddypress()->groups->id, 'group_invite' );
	}
}
add_action( 'groups_screen_group_invites', 'bp_groups_screen_invites_mark_notifications', 10 );

/**
 * Mark group join requests read when an admin or moderator visits the group administration area.
 *
 * @since 1.9.0
 */
function bp_groups_screen_group_admin_requests_mark_notifications() {
	if ( bp_is_active( 'notifications' ) ) {
		bp_notifications_mark_notifications_by_type( bp_loggedin_user_id(), buddypress()->groups->id, 'new_membership_request' );
	}
}
add_action( 'groups_screen_group_admin_requests', 'bp_groups_screen_group_admin_requests_mark_notifications', 10 );

/**
 * Delete new group membership notifications when a user is being deleted.
 *
 * @since 1.9.0
 *
 * @param int $user_id ID of the user.
 */
function bp_groups_remove_data_for_user_notifications( $user_id ) {
	if ( bp_is_active( 'notifications' ) ) {
		bp_notifications_delete_notifications_from_user( $user_id, buddypress()->groups->id, 'new_membership_request' );
	}
}
add_action( 'groups_remove_data_for_user', 'bp_groups_remove_data_for_user_notifications', 10 );
