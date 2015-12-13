<?php
/**
 * BuddyPress - Members Settings Capabilities
 *
 * @package BuddyPress
 * @subpackage bp-legacy
 */

/** This action is documented in bp-templates/bp-legacy/buddypress/members/single/settings/profile.php */
do_action( 'bp_before_member_settings_template' ); ?>

<form action="<?php echo bp_displayed_user_domain() . bp_get_settings_slug() . '/capabilities/'; ?>" name="account-capabilities-form" id="account-capabilities-form" class="standard-form" method="post">

	<?php

	/**
	 * Fires before the display of the submit button for user capabilities saving.
	 *
	 * @since 1.6.0
	 */
	do_action( 'bp_members_capabilities_account_before_submit' ); ?>

	<label for="user-spammer">
		<input type="checkbox" name="user-spammer" id="user-spammer" value="1" <?php checked( bp_is_user_spammer( bp_displayed_user_id() ) ); ?> />
		 <?php _e( 'This user is a spammer.', 'buddypress' ); ?>
	</label>

	<div class="submit">
		<input type="submit" value="<?php esc_attr_e( 'Save', 'buddypress' ); ?>" id="capabilities-submit" name="capabilities-submit" />
	</div>

	<?php

	/**
	 * Fires after the display of the submit button for user capabilities saving.
	 *
	 * @since 1.6.0
	 */
	do_action( 'bp_members_capabilities_account_after_submit' ); ?>

	<?php wp_nonce_field( 'capabilities' ); ?>

</form>

<?php

/** This action is documented in bp-templates/bp-legacy/buddypress/members/single/settings/profile.php */
do_action( 'bp_after_member_settings_template' ); ?>
