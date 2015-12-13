<?php
/**
 * BuddyPress Activity Notifications.
 *
 * @package BuddyPress
 * @subpackage ActivityNotifications
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/* Emails *********************************************************************/

/**
 * Send email and BP notifications when a user is mentioned in an update.
 *
 * @since 1.2.0
 *
 * @uses bp_notifications_add_notification()
 * @uses bp_get_user_meta()
 * @uses bp_core_get_user_displayname()
 * @uses bp_activity_get_permalink()
 * @uses bp_core_get_user_domain()
 * @uses bp_get_settings_slug()
 * @uses bp_activity_filter_kses()
 * @uses bp_core_get_core_userdata()
 * @uses wp_specialchars_decode()
 * @uses get_blog_option()
 * @uses bp_is_active()
 * @uses bp_is_group()
 * @uses bp_get_current_group_name()
 * @uses apply_filters() To call the 'bp_activity_at_message_notification_to' hook.
 * @uses apply_filters() To call the 'bp_activity_at_message_notification_subject' hook.
 * @uses apply_filters() To call the 'bp_activity_at_message_notification_message' hook.
 * @uses wp_mail()
 * @uses do_action() To call the 'bp_activity_sent_mention_email' hook.
 *
 * @param int $activity_id      The ID of the activity update.
 * @param int $receiver_user_id The ID of the user who is receiving the update.
 */
function bp_activity_at_message_notification( $activity_id, $receiver_user_id ) {

	// Don't leave multiple notifications for the same activity item.
	$notifications = BP_Core_Notification::get_all_for_user( $receiver_user_id, 'all' );

	foreach( $notifications as $notification ) {
		if ( $activity_id == $notification->item_id ) {
			return;
		}
	}

	$activity = new BP_Activity_Activity( $activity_id );

	$subject = '';
	$message = '';
	$content = '';

	// Now email the user with the contents of the message (if they have enabled email notifications).
	if ( 'no' != bp_get_user_meta( $receiver_user_id, 'notification_activity_new_mention', true ) ) {
		$poster_name = bp_core_get_user_displayname( $activity->user_id );

		$message_link  = bp_activity_get_permalink( $activity_id );
		$settings_slug = function_exists( 'bp_get_settings_slug' ) ? bp_get_settings_slug() : 'settings';
		$settings_link = bp_core_get_user_domain( $receiver_user_id ) . $settings_slug . '/notifications/';

		$poster_name = stripslashes( $poster_name );
		$content = bp_activity_filter_kses( strip_tags( stripslashes( $activity->content ) ) );

		// Set up and send the message.
		$ud       = bp_core_get_core_userdata( $receiver_user_id );
		$to       = $ud->user_email;
		$subject  = bp_get_email_subject( array( 'text' => sprintf( __( '%s mentioned you in an update', 'buddypress' ), $poster_name ) ) );

		if ( bp_is_active( 'groups' ) && bp_is_group() ) {
			$message = sprintf( __(
'%1$s mentioned you in the group "%2$s":

"%3$s"

To view and respond to the message, log in and visit: %4$s

---------------------
', 'buddypress' ), $poster_name, bp_get_current_group_name(), $content, $message_link );
		} else {
			$message = sprintf( __(
'%1$s mentioned you in an update:

"%2$s"

To view and respond to the message, log in and visit: %3$s

---------------------
', 'buddypress' ), $poster_name, $content, $message_link );
		}

		// Only show the disable notifications line if the settings component is enabled.
		if ( bp_is_active( 'settings' ) ) {
			$message .= sprintf( __( 'To disable these notifications please log in and go to: %s', 'buddypress' ), $settings_link );
		}

		/**
		 * Filters the user email that the @mention notification will be sent to.
		 *
		 * @since 1.2.0
		 *
		 * @param string $to User email the notification is being sent to.
		 */
		$to 	 = apply_filters( 'bp_activity_at_message_notification_to', $to );

		/**
		 * Filters the @mention notification subject that will be sent to user.
		 *
		 * @since 1.2.0
		 *
		 * @param string $subject     Email notification subject text.
		 * @param string $poster_name Name of the person who made the @mention.
		 */
		$subject = apply_filters( 'bp_activity_at_message_notification_subject', $subject, $poster_name );

		/**
		 * Filters the @mention notification message that will be sent to user.
		 *
		 * @since 1.2.0
		 *
		 * @param string $message       Email notification message text.
		 * @param string $poster_name   Name of the person who made the @mention.
		 * @param string $content       Content of the @mention.
		 * @param string $message_link  URL permalink for the activity message.
		 * @param string $settings_link URL permalink for the user's notification settings area.
		 */
		$message = apply_filters( 'bp_activity_at_message_notification_message', $message, $poster_name, $content, $message_link, $settings_link );

		wp_mail( $to, $subject, $message );
	}

	/**
	 * Fires after the sending of an @mention email notification.
	 *
	 * @since 1.5.0
	 *
	 * @param BP_Activity_Activity $activity         Activity Item object.
	 * @param string               $subject          Email notification subject text.
	 * @param string               $message          Email notification message text.
	 * @param string               $content          Content of the @mention.
	 * @param int                  $receiver_user_id The ID of the user who is receiving the update.
	 */
	do_action( 'bp_activity_sent_mention_email', $activity, $subject, $message, $content, $receiver_user_id );
}

/**
 * Send email and BP notifications when an activity item receives a comment.
 *
 * @since 1.2.0
 *
 * @uses bp_get_user_meta()
 * @uses bp_core_get_user_displayname()
 * @uses bp_activity_get_permalink()
 * @uses bp_core_get_user_domain()
 * @uses bp_get_settings_slug()
 * @uses bp_activity_filter_kses()
 * @uses bp_core_get_core_userdata()
 * @uses wp_specialchars_decode()
 * @uses get_blog_option()
 * @uses bp_get_root_blog_id()
 * @uses apply_filters() To call the 'bp_activity_new_comment_notification_to' hook.
 * @uses apply_filters() To call the 'bp_activity_new_comment_notification_subject' hook.
 * @uses apply_filters() To call the 'bp_activity_new_comment_notification_message' hook.
 * @uses wp_mail()
 * @uses do_action() To call the 'bp_activity_sent_reply_to_update_email' hook.
 * @uses apply_filters() To call the 'bp_activity_new_comment_notification_comment_author_to' hook.
 * @uses apply_filters() To call the 'bp_activity_new_comment_notification_comment_author_subject' hook.
 * @uses apply_filters() To call the 'bp_activity_new_comment_notification_comment_author_message' hook.
 * @uses do_action() To call the 'bp_activity_sent_reply_to_reply_email' hook.
 *
 * @param int   $comment_id   The comment id.
 * @param int   $commenter_id The ID of the user who posted the comment.
 * @param array $params       {@link bp_activity_new_comment()}.
 * @return bool
 */
function bp_activity_new_comment_notification( $comment_id = 0, $commenter_id = 0, $params = array() ) {

	// Set some default parameters.
	$activity_id = 0;
	$parent_id   = 0;

	extract( $params );

	$original_activity = new BP_Activity_Activity( $activity_id );

	if ( $original_activity->user_id != $commenter_id && 'no' != bp_get_user_meta( $original_activity->user_id, 'notification_activity_new_reply', true ) ) {
		$poster_name   = bp_core_get_user_displayname( $commenter_id );
		$thread_link   = bp_activity_get_permalink( $activity_id );
		$settings_slug = function_exists( 'bp_get_settings_slug' ) ? bp_get_settings_slug() : 'settings';
		$settings_link = bp_core_get_user_domain( $original_activity->user_id ) . $settings_slug . '/notifications/';

		$poster_name = stripslashes( $poster_name );
		$content = bp_activity_filter_kses( stripslashes($content) );

		// Set up and send the message.
		$ud      = bp_core_get_core_userdata( $original_activity->user_id );
		$to      = $ud->user_email;
		$subject = bp_get_email_subject( array( 'text' => sprintf( __( '%s replied to one of your updates', 'buddypress' ), $poster_name ) ) );
		$message = sprintf( __(
'%1$s replied to one of your updates:

"%2$s"

To view your original update and all comments, log in and visit: %3$s

---------------------
', 'buddypress' ), $poster_name, $content, $thread_link );

		// Only show the disable notifications line if the settings component is enabled.
		if ( bp_is_active( 'settings' ) ) {
			$message .= sprintf( __( 'To disable these notifications please log in and go to: %s', 'buddypress' ), $settings_link );
		}

		/**
		 * Filters the user email that the new comment notification will be sent to.
		 *
		 * @since 1.2.0
		 *
		 * @param string $to User email the notification is being sent to.
		 */
		$to = apply_filters( 'bp_activity_new_comment_notification_to', $to );

		/**
		 * Filters the new comment notification subject that will be sent to user.
		 *
		 * @since 1.2.0
		 *
		 * @param string $subject     Email notification subject text.
		 * @param string $poster_name Name of the person who made the comment.
		 */
		$subject = apply_filters( 'bp_activity_new_comment_notification_subject', $subject, $poster_name );

		/**
		 * Filters the new comment notification message that will be sent to user.
		 *
		 * @since 1.2.0
		 *
		 * @param string $message       Email notification message text.
		 * @param string $poster_name   Name of the person who made the comment.
		 * @param string $content       Content of the comment.
		 * @param string $thread_link   URL permalink for the activity thread.
		 * @param string $settings_link URL permalink for the user's notification settings area.
		 */
		$message = apply_filters( 'bp_activity_new_comment_notification_message', $message, $poster_name, $content, $thread_link, $settings_link );

		wp_mail( $to, $subject, $message );

		/**
		 * Fires after the sending of a reply to an update email notification.
		 *
		 * @since 1.5.0
		 *
		 * @param int    $user_id      ID of the original activity item author.
		 * @param string $subject      Email notification subject text.
		 * @param string $message      Email notification message text.
		 * @param int    $comment_id   ID for the newly received comment.
		 * @param int    $commenter_id ID of the user who made the comment.
		 * @param array  $params       Arguments used with the original activity comment.
		 */
		do_action( 'bp_activity_sent_reply_to_update_email', $original_activity->user_id, $subject, $message, $comment_id, $commenter_id, $params );
	}

	/*
	 * If this is a reply to another comment, send an email notification to the
	 * author of the immediate parent comment.
	 */
	if ( empty( $parent_id ) || ( $activity_id == $parent_id ) ) {
		return false;
	}

	$parent_comment = new BP_Activity_Activity( $parent_id );

	if ( $parent_comment->user_id != $commenter_id && $original_activity->user_id != $parent_comment->user_id && 'no' != bp_get_user_meta( $parent_comment->user_id, 'notification_activity_new_reply', true ) ) {
		$poster_name   = bp_core_get_user_displayname( $commenter_id );
		$thread_link   = bp_activity_get_permalink( $activity_id );
		$settings_slug = function_exists( 'bp_get_settings_slug' ) ? bp_get_settings_slug() : 'settings';
		$settings_link = bp_core_get_user_domain( $parent_comment->user_id ) . $settings_slug . '/notifications/';

		// Set up and send the message.
		$ud       = bp_core_get_core_userdata( $parent_comment->user_id );
		$to       = $ud->user_email;
		$subject = bp_get_email_subject( array( 'text' => sprintf( __( '%s replied to one of your comments', 'buddypress' ), $poster_name ) ) );

		$poster_name = stripslashes( $poster_name );
		$content = bp_activity_filter_kses( stripslashes( $content ) );

$message = sprintf( __(
'%1$s replied to one of your comments:

"%2$s"

To view the original activity, your comment and all replies, log in and visit: %3$s

---------------------
', 'buddypress' ), $poster_name, $content, $thread_link );

		// Only show the disable notifications line if the settings component is enabled.
		if ( bp_is_active( 'settings' ) ) {
			$message .= sprintf( __( 'To disable these notifications please log in and go to: %s', 'buddypress' ), $settings_link );
		}

		/**
		 * Filters the user email that the new comment reply notification will be sent to.
		 *
		 * @since 1.2.0
		 *
		 * @param string $to User email the notification is being sent to.
		 */
		$to = apply_filters( 'bp_activity_new_comment_notification_comment_author_to', $to );

		/**
		 * Filters the new comment reply notification subject that will be sent to user.
		 *
		 * @since 1.2.0
		 *
		 * @param string $subject     Email notification subject text.
		 * @param string $poster_name Name of the person who made the comment reply.
		 */
		$subject = apply_filters( 'bp_activity_new_comment_notification_comment_author_subject', $subject, $poster_name );

		/**
		 * Filters the new comment reply notification message that will be sent to user.
		 *
		 * @since 1.2.0
		 *
		 * @param string $message       Email notification message text.
		 * @param string $poster_name   Name of the person who made the comment reply.
		 * @param string $content       Content of the comment reply.
		 * @param string $settings_link URL permalink for the user's notification settings area.
		 * @param string $thread_link   URL permalink for the activity thread.
		 */
		$message = apply_filters( 'bp_activity_new_comment_notification_comment_author_message', $message, $poster_name, $content, $settings_link, $thread_link );

		wp_mail( $to, $subject, $message );

		/**
		 * Fires after the sending of a reply to a reply email notification.
		 *
		 * @since 1.5.0
		 *
		 * @param int    $user_id      ID of the parent activity item author.
		 * @param string $subject      Email notification subject text.
		 * @param string $message      Email notification message text.
		 * @param int    $comment_id   ID for the newly received comment.
		 * @param int    $commenter_id ID of the user who made the comment.
		 * @param array  $params       Arguments used with the original activity comment.
		 */
		do_action( 'bp_activity_sent_reply_to_reply_email', $parent_comment->user_id, $subject, $message, $comment_id, $commenter_id, $params );
	}
}

/**
 * Helper method to map action arguments to function parameters.
 *
 * @since 1.9.0
 *
 * @param int   $comment_id ID of the comment being notified about.
 * @param array $params     Parameters to use with notification.
 */
function bp_activity_new_comment_notification_helper( $comment_id, $params ) {
	bp_activity_new_comment_notification( $comment_id, $params['user_id'], $params );
}
add_action( 'bp_activity_comment_posted', 'bp_activity_new_comment_notification_helper', 10, 2 );

/** Notifications *************************************************************/

/**
 * Format notifications related to activity.
 *
 * @since 1.5.0
 *
 * @uses bp_loggedin_user_domain()
 * @uses bp_get_activity_slug()
 * @uses bp_core_get_user_displayname()
 * @uses apply_filters() To call the 'bp_activity_multiple_at_mentions_notification' hook.
 * @uses apply_filters() To call the 'bp_activity_single_at_mentions_notification' hook.
 * @uses do_action() To call 'activity_format_notifications' hook.
 *
 * @param string $action            The type of activity item. Just 'new_at_mention' for now.
 * @param int    $item_id           The activity ID.
 * @param int    $secondary_item_id In the case of at-mentions, this is the mentioner's ID.
 * @param int    $total_items       The total number of notifications to format.
 * @param string $format            'string' to get a BuddyBar-compatible notification, 'array' otherwise.
 * @return string $return Formatted @mention notification.
 */
function bp_activity_format_notifications( $action, $item_id, $secondary_item_id, $total_items, $format = 'string' ) {

	switch ( $action ) {
		case 'new_at_mention':
			$activity_id      = $item_id;
			$poster_user_id   = $secondary_item_id;
			$at_mention_link  = bp_loggedin_user_domain() . bp_get_activity_slug() . '/mentions/';
			$at_mention_title = sprintf( __( '@%s Mentions', 'buddypress' ), bp_get_loggedin_user_username() );
			$amount = 'single';

			if ( (int) $total_items > 1 ) {
				$text = sprintf( __( 'You have %1$d new mentions', 'buddypress' ), (int) $total_items );
				$amount = 'multiple';
			} else {
				$user_fullname = bp_core_get_user_displayname( $poster_user_id );
				$text =  sprintf( __( '%1$s mentioned you', 'buddypress' ), $user_fullname );
			}
		break;
	}

	if ( 'string' == $format ) {

		/**
		 * Filters the @mention notification for the string format.
		 *
		 * This is a variable filter that is dependent on how many items
		 * need notified about. The two possible hooks are bp_activity_single_at_mentions_notification
		 * or bp_activity_multiple_at_mentions_notification.
		 *
		 * @since 1.5.0
		 *
		 * @param string $string          HTML anchor tag for the mention.
		 * @param string $at_mention_link The permalink for the mention.
		 * @param int    $total_items     How many items being notified about.
		 * @param int    $activity_id     ID of the activity item being formatted.
		 * @param int    $poster_user_id  ID of the user posting the mention.
		 */
		$return = apply_filters( 'bp_activity_' . $amount . '_at_mentions_notification', '<a href="' . esc_url( $at_mention_link ) . '" title="' . esc_attr( $at_mention_title ) . '">' . esc_html( $text ) . '</a>', $at_mention_link, (int) $total_items, $activity_id, $poster_user_id );
	} else {

		/**
		 * Filters the @mention notification for any non-string format.
		 *
		 * This is a variable filter that is dependent on how many items need notified about.
		 * The two possible hooks are bp_activity_single_at_mentions_notification
		 * or bp_activity_multiple_at_mentions_notification.
		 *
		 * @since 1.5.0
		 *
		 * @param array  $array           Array holding the content and permalink for the mention notification.
		 * @param string $at_mention_link The permalink for the mention.
		 * @param int    $total_items     How many items being notified about.
		 * @param int    $activity_id     ID of the activity item being formatted.
		 * @param int    $poster_user_id  ID of the user posting the mention.
		 */
		$return = apply_filters( 'bp_activity_' . $amount . '_at_mentions_notification', array(
			'text' => $text,
			'link' => $at_mention_link
		), $at_mention_link, (int) $total_items, $activity_id, $poster_user_id );
	}

	/**
	 * Fires right before returning the formatted activity notifications.
	 *
	 * @since 1.2.0
	 *
	 * @param string $action            The type of activity item.
	 * @param int    $item_id           The activity ID.
	 * @param int    $secondary_item_id @mention mentioner ID.
	 * @param int    $total_items       Total amount of items to format.
	 */
	do_action( 'activity_format_notifications', $action, $item_id, $secondary_item_id, $total_items );

	return $return;
}

/**
 * Notify a member when their nicename is mentioned in an activity stream item.
 *
 * Hooked to the 'bp_activity_sent_mention_email' action, we piggy back off the
 * existing email code for now, since it does the heavy lifting for us. In the
 * future when we separate emails from Notifications, this will need its own
 * 'bp_activity_at_name_send_emails' equivalent helper function.
 *
 * @since 1.9.0
 *
 * @param object $activity           Activity object.
 * @param string $subject (not used) Notification subject.
 * @param string $message (not used) Notification message.
 * @param string $content (not used) Notification content.
 * @param int    $receiver_user_id   ID of user receiving notification.
 */
function bp_activity_at_mention_add_notification( $activity, $subject, $message, $content, $receiver_user_id ) {
	if ( bp_is_active( 'notifications' ) ) {
		bp_notifications_add_notification( array(
			'user_id'           => $receiver_user_id,
			'item_id'           => $activity->id,
			'secondary_item_id' => $activity->user_id,
			'component_name'    => buddypress()->activity->id,
			'component_action'  => 'new_at_mention',
			'date_notified'     => bp_core_current_time(),
			'is_new'            => 1,
		) );
	}
}
add_action( 'bp_activity_sent_mention_email', 'bp_activity_at_mention_add_notification', 10, 5 );

/**
 * Mark at-mention notifications as read when users visit their Mentions page.
 *
 * @since 1.5.0
 *
 * @uses bp_notifications_mark_all_notifications_by_type()
 */
function bp_activity_remove_screen_notifications() {
	if ( ! bp_is_active( 'notifications' ) ) {
		return;
	}

	// Only mark read if you're looking at your own mentions.
	if ( ! bp_is_my_profile() ) {
		return;
	}

	bp_notifications_mark_notifications_by_type( bp_loggedin_user_id(), buddypress()->activity->id, 'new_at_mention' );
}
add_action( 'bp_activity_screen_mentions', 'bp_activity_remove_screen_notifications' );

/**
 * Mark at-mention notification as read when user visits the activity with the mention.
 *
 * @since 2.0.0
 *
 * @param BP_Activity_Activity $activity Activity object.
 */
function bp_activity_remove_screen_notifications_single_activity_permalink( $activity ) {
	if ( ! bp_is_active( 'notifications' ) ) {
		return;
	}

	if ( ! is_user_logged_in() ) {
		return;
	}

	// Mark as read any notifications for the current user related to this activity item.
	bp_notifications_mark_notifications_by_item_id( bp_loggedin_user_id(), $activity->id, buddypress()->activity->id, 'new_at_mention' );
}
add_action( 'bp_activity_screen_single_activity_permalink', 'bp_activity_remove_screen_notifications_single_activity_permalink' );

/**
 * Delete at-mention notifications when the corresponding activity item is deleted.
 *
 * @since 2.0.0
 *
 * @param array $activity_ids_deleted IDs of deleted activity items.
 */
function bp_activity_at_mention_delete_notification( $activity_ids_deleted = array() ) {
	// Let's delete all without checking if content contains any mentions
	// to avoid a query to get the activity.
	if ( bp_is_active( 'notifications' ) && ! empty( $activity_ids_deleted ) ) {
		foreach ( $activity_ids_deleted as $activity_id ) {
			bp_notifications_delete_all_notifications_by_type( $activity_id, buddypress()->activity->id );
		}
	}
}
add_action( 'bp_activity_deleted_activities', 'bp_activity_at_mention_delete_notification', 10 );
