<?php
/**
 * BuddyPress Messages Screens.
 *
 * Screen functions are the controllers of BuddyPress. They will execute when
 * their specific URL is caught. They will first save or manipulate data using
 * business functions, then pass on the user to a template file.
 *
 * @package BuddyPress
 * @subpackage MessagesScreens
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Load the Messages > Inbox screen.
 *
 * @since 1.0.0
 */
function messages_screen_inbox() {

	if ( bp_action_variables() ) {
		bp_do_404();
		return;
	}

	/**
	 * Fires right before the loading of the Messages inbox screen template file.
	 *
	 * @since 1.0.0
	 */
	do_action( 'messages_screen_inbox' );

	/**
	 * Filters the template to load for the Messages inbox screen.
	 *
	 * @since 1.0.0
	 *
	 * @param string $template Path to the messages template to load.
	 */
	bp_core_load_template( apply_filters( 'messages_template_inbox', 'members/single/home' ) );
}

/**
 * Load the Messages > Sent screen.
 *
 * @since 1.0.0
 */
function messages_screen_sentbox() {

	if ( bp_action_variables() ) {
		bp_do_404();
		return;
	}

	/**
	 * Fires right before the loading of the Messages sentbox screen template file.
	 *
	 * @since 1.0.0
	 */
	do_action( 'messages_screen_sentbox' );

	/**
	 * Filters the template to load for the Messages sentbox screen.
	 *
	 * @since 1.0.0
	 *
	 * @param string $template Path to the messages template to load.
	 */
	bp_core_load_template( apply_filters( 'messages_template_sentbox', 'members/single/home' ) );
}

/**
 * Load the Messages > Compose screen.
 *
 * @since 1.0.0
 */
function messages_screen_compose() {

	if ( bp_action_variables() ) {
		bp_do_404();
		return;
	}

	// Remove any saved message data from a previous session.
	messages_remove_callback_values();

	/**
	 * Fires right before the loading of the Messages compose screen template file.
	 *
	 * @since 1.0.0
	 */
	do_action( 'messages_screen_compose' );

	/**
	 * Filters the template to load for the Messages compose screen.
	 *
	 * @since 1.0.0
	 *
	 * @param string $template Path to the messages template to load.
	 */
	bp_core_load_template( apply_filters( 'messages_template_compose', 'members/single/home' ) );
}

/**
 * Load an individual conversation screen.
 *
 * @since 1.0.0
 *
 * @return bool|null False on failure.
 */
function messages_screen_conversation() {

	// Bail if not viewing a single message.
	if ( ! bp_is_messages_component() || ! bp_is_current_action( 'view' ) ) {
		return false;
	}

	$thread_id = (int) bp_action_variable( 0 );

	if ( empty( $thread_id ) || ! messages_is_valid_thread( $thread_id ) || ( ! messages_check_thread_access( $thread_id ) && ! bp_current_user_can( 'bp_moderate' ) ) ) {
		bp_core_redirect( trailingslashit( bp_displayed_user_domain() . bp_get_messages_slug() ) );
	}

	// Load up BuddyPress one time.
	$bp = buddypress();

	// Decrease the unread count in the nav before it's rendered.
	$count    = bp_get_total_unread_messages_count();
	$class    = ( 0 === $count ) ? 'no-count' : 'count';
	$nav_name = sprintf( __( 'Messages <span class="%s">%s</span>', 'buddypress' ), esc_attr( $class ), bp_core_number_format( $count ) );

	$bp->bp_nav[ $bp->messages->slug ]['name'] = $nav_name;

	/**
	 * Fires right before the loading of the Messages view screen template file.
	 *
	 * @since 1.7.0
	 */
	do_action( 'messages_screen_conversation' );

	/**
	 * Filters the template to load for the Messages view screen.
	 *
	 * @since 1.0.0
	 *
	 * @param string $template Path to the messages template to load.
	 */
	bp_core_load_template( apply_filters( 'messages_template_view_message', 'members/single/home' ) );
}
add_action( 'bp_screens', 'messages_screen_conversation' );

/**
 * Load the Messages > Notices screen.
 *
 * @since 1.0.0
 *
 * @return false|null False on failure.
 */
function messages_screen_notices() {

	if ( bp_action_variables() ) {
		bp_do_404();
		return;
	}

	/**
	 * Fires right before the loading of the Messages notices screen template file.
	 *
	 * @since 1.0.0
	 */
	do_action( 'messages_screen_notices' );

	/**
	 * Filters the template to load for the Messages notices screen.
	 *
	 * @since 1.0.0
	 *
	 * @param string $template Path to the messages template to load.
	 */
	bp_core_load_template( apply_filters( 'messages_template_notices', 'members/single/home' ) );
}

/**
 * Render the markup for the Messages section of Settings > Notifications.
 *
 * @since 1.0.0
 */
function messages_screen_notification_settings() {

	if ( bp_action_variables() ) {
		bp_do_404();
		return;
	}

	if ( !$new_messages = bp_get_user_meta( bp_displayed_user_id(), 'notification_messages_new_message', true ) ) {
		$new_messages = 'yes';
	} ?>

	<table class="notification-settings" id="messages-notification-settings">
		<thead>
			<tr>
				<th class="icon"></th>
				<th class="title"><?php _e( 'Messages', 'buddypress' ) ?></th>
				<th class="yes"><?php _e( 'Yes', 'buddypress' ) ?></th>
				<th class="no"><?php _e( 'No', 'buddypress' )?></th>
			</tr>
		</thead>

		<tbody>
			<tr id="messages-notification-settings-new-message">
				<td></td>
				<td><?php _e( 'A member sends you a new message', 'buddypress' ) ?></td>
				<td class="yes"><input type="radio" name="notifications[notification_messages_new_message]" id="notification-messages-new-messages-yes" value="yes" <?php checked( $new_messages, 'yes', true ) ?>/><label for="notification-messages-new-messages-yes" class="bp-screen-reader-text"><?php _e( 'Yes, send email', 'buddypress' ); ?></label></td>
				<td class="no"><input type="radio" name="notifications[notification_messages_new_message]" id="notification-messages-new-messages-no" value="no" <?php checked( $new_messages, 'no', true ) ?>/><label for="notification-messages-new-messages-no" class="bp-screen-reader-text"><?php _e( 'No, do not send email', 'buddypress' ); ?></label></td>
			</tr>

			<?php

			/**
			 * Fires inside the closing </tbody> tag for messages screen notification settings.
			 *
			 * @since 1.0.0
			 */
			do_action( 'messages_screen_notification_settings' ); ?>
		</tbody>
	</table>

<?php
}
add_action( 'bp_notification_settings', 'messages_screen_notification_settings', 2 );
