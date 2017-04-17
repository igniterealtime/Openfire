<?php
/**
 * BuddyPress Friends Screen Functions.
 *
 * Screen functions are the controllers of BuddyPress. They will execute when
 * their specific URL is caught. They will first save or manipulate data using
 * business functions, then pass on the user to a template file.
 *
 * @package BuddyPress
 * @subpackage FriendsScreens
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Catch and process the My Friends page.
 */
function friends_screen_my_friends() {

	/**
	 * Fires before the loading of template for the My Friends page.
	 *
	 * @since 1.0.0
	 */
	do_action( 'friends_screen_my_friends' );

	/**
	 * Filters the template used to display the My Friends page.
	 *
	 * @since 1.0.0
	 *
	 * @param string $template Path to the my friends template to load.
	 */
	bp_core_load_template( apply_filters( 'friends_template_my_friends', 'members/single/home' ) );
}

/**
 * Catch and process the Requests page.
 */
function friends_screen_requests() {
	if ( bp_is_action_variable( 'accept', 0 ) && is_numeric( bp_action_variable( 1 ) ) ) {
		// Check the nonce.
		check_admin_referer( 'friends_accept_friendship' );

		if ( friends_accept_friendship( bp_action_variable( 1 ) ) )
			bp_core_add_message( __( 'Friendship accepted', 'buddypress' ) );
		else
			bp_core_add_message( __( 'Friendship could not be accepted', 'buddypress' ), 'error' );

		bp_core_redirect( trailingslashit( bp_loggedin_user_domain() . bp_current_component() . '/' . bp_current_action() ) );

	} elseif ( bp_is_action_variable( 'reject', 0 ) && is_numeric( bp_action_variable( 1 ) ) ) {
		// Check the nonce.
		check_admin_referer( 'friends_reject_friendship' );

		if ( friends_reject_friendship( bp_action_variable( 1 ) ) )
			bp_core_add_message( __( 'Friendship rejected', 'buddypress' ) );
		else
			bp_core_add_message( __( 'Friendship could not be rejected', 'buddypress' ), 'error' );

		bp_core_redirect( trailingslashit( bp_loggedin_user_domain() . bp_current_component() . '/' . bp_current_action() ) );

	} elseif ( bp_is_action_variable( 'cancel', 0 ) && is_numeric( bp_action_variable( 1 ) ) ) {
		// Check the nonce.
		check_admin_referer( 'friends_withdraw_friendship' );

		if ( friends_withdraw_friendship( bp_loggedin_user_id(), bp_action_variable( 1 ) ) )
			bp_core_add_message( __( 'Friendship request withdrawn', 'buddypress' ) );
		else
			bp_core_add_message( __( 'Friendship request could not be withdrawn', 'buddypress' ), 'error' );

		bp_core_redirect( trailingslashit( bp_loggedin_user_domain() . bp_current_component() . '/' . bp_current_action() ) );
	}

	/**
	 * Fires before the loading of template for the friends requests page.
	 *
	 * @since 1.0.0
	 */
	do_action( 'friends_screen_requests' );

	/**
	 * Filters the template used to display the My Friends page.
	 *
	 * @since 1.0.0
	 *
	 * @param string $template Path to the friends request template to load.
	 */
	bp_core_load_template( apply_filters( 'friends_template_requests', 'members/single/home' ) );
}

/**
 * Add Friends-related settings to the Settings > Notifications page.
 */
function friends_screen_notification_settings() {

	if ( !$send_requests = bp_get_user_meta( bp_displayed_user_id(), 'notification_friends_friendship_request', true ) )
		$send_requests   = 'yes';

	if ( !$accept_requests = bp_get_user_meta( bp_displayed_user_id(), 'notification_friends_friendship_accepted', true ) )
		$accept_requests = 'yes'; ?>

	<table class="notification-settings" id="friends-notification-settings">
		<thead>
			<tr>
				<th class="icon"></th>
				<th class="title"><?php _ex( 'Friends', 'Friend settings on notification settings page', 'buddypress' ) ?></th>
				<th class="yes"><?php _e( 'Yes', 'buddypress' ) ?></th>
				<th class="no"><?php _e( 'No', 'buddypress' )?></th>
			</tr>
		</thead>

		<tbody>
			<tr id="friends-notification-settings-request">
				<td></td>
				<td><?php _ex( 'A member sends you a friendship request', 'Friend settings on notification settings page', 'buddypress' ) ?></td>
				<td class="yes"><input type="radio" name="notifications[notification_friends_friendship_request]" id="notification-friends-friendship-request-yes" value="yes" <?php checked( $send_requests, 'yes', true ) ?>/><label for="notification-friends-friendship-request-yes" class="bp-screen-reader-text"><?php _e( 'Yes, send email', 'buddypress' ); ?></label></td>
				<td class="no"><input type="radio" name="notifications[notification_friends_friendship_request]" id="notification-friends-friendship-request-no" value="no" <?php checked( $send_requests, 'no', true ) ?>/><label for="notification-friends-friendship-request-no" class="bp-screen-reader-text"><?php _e( 'No, do not send email', 'buddypress' ); ?></label></td>
			</tr>
			<tr id="friends-notification-settings-accepted">
				<td></td>
				<td><?php _ex( 'A member accepts your friendship request', 'Friend settings on notification settings page', 'buddypress' ) ?></td>
				<td class="yes"><input type="radio" name="notifications[notification_friends_friendship_accepted]" id="notification-friends-friendship-accepted-yes" value="yes" <?php checked( $accept_requests, 'yes', true ) ?>/><label for="notification-friends-friendship-accepted-yes" class="bp-screen-reader-text"><?php _e( 'Yes, send email', 'buddypress' ); ?></label></td>
				<td class="no"><input type="radio" name="notifications[notification_friends_friendship_accepted]" id="notification-friends-friendship-accepted-no" value="no" <?php checked( $accept_requests, 'no', true ) ?>/><label for="notification-friends-friendship-accepted-no" class="bp-screen-reader-text"><?php _e( 'No, do not send email', 'buddypress' ); ?></label></td>
			</tr>

			<?php

			/**
			 * Fires after the last table row on the friends notification screen.
			 *
			 * @since 1.0.0
			 */
			do_action( 'friends_screen_notification_settings' ); ?>

		</tbody>
	</table>

<?php
}
add_action( 'bp_notification_settings', 'friends_screen_notification_settings' );
