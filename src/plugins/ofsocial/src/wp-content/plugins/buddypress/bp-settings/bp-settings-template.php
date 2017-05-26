<?php
/**
 * BuddyPress Settings Template Functions.
 *
 * @package BuddyPress
 * @subpackage SettingsTemplate
 */

// Exit if accessed directly.
defined( 'ABSPATH' ) || exit;

/**
 * Output the settings component slug.
 *
 * @package BuddyPress
 * @subpackage SettingsTemplate
 * @since 1.5.0
 *
 * @uses bp_get_settings_slug()
 */
function bp_settings_slug() {
	echo bp_get_settings_slug();
}
	/**
	 * Return the settings component slug.
	 *
	 * @package BuddyPress
	 * @subpackage SettingsTemplate
	 * @since 1.5.0
	 */
	function bp_get_settings_slug() {

		/**
		 * Filters the Settings component slug.
		 *
		 * @since 1.5.0
		 *
		 * @param string $slug Settings component slug.
		 */
		return apply_filters( 'bp_get_settings_slug', buddypress()->settings->slug );
	}

/**
 * Output the settings component root slug.
 *
 * @package BuddyPress
 * @subpackage SettingsTemplate
 * @since 1.5.0
 *
 * @uses bp_get_settings_root_slug()
 */
function bp_settings_root_slug() {
	echo bp_get_settings_root_slug();
}
	/**
	 * Return the settings component root slug.
	 *
	 * @package BuddyPress
	 * @subpackage SettingsTemplate
	 * @since 1.5.0
	 */
	function bp_get_settings_root_slug() {

		/**
		 * Filters the Settings component root slug.
		 *
		 * @since 1.5.0
		 *
		 * @param string $root_slug Settings component root slug.
		 */
		return apply_filters( 'bp_get_settings_root_slug', buddypress()->settings->root_slug );
	}

/**
 * Add the 'pending email change' message to the settings page.
 *
 * @since 2.1.0
 */
function bp_settings_pending_email_notice() {
	$pending_email = bp_get_user_meta( bp_displayed_user_id(), 'pending_email_change', true );

	if ( empty( $pending_email['newemail'] ) ) {
		return;
	}

	if ( bp_get_displayed_user_email() == $pending_email['newemail'] ) {
		return;
	}

	?>

	<div id="message" class="bp-template-notice error">
		<p><?php printf(
			__( 'There is a pending change of your email address to %1$s.<br />Check your email (%2$s) for the verification link. <a href="%3$s">Cancel</a>', 'buddypress' ),
			'<code>' . $pending_email['newemail'] . '</code>',
			'<code>' . $pending_email['newemail'] . '</code>',
			esc_url( bp_displayed_user_domain() . bp_get_settings_slug() . '/?dismiss_email_change=1' )
		);
		?></p>
	</div>

	<?php
}
add_action( 'bp_before_member_settings_template', 'bp_settings_pending_email_notice' );
