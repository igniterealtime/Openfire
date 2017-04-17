<?php
/**
 * BuddyPress - Members Single Profile
 *
 * @package BuddyPress
 * @subpackage bp-legacy
 */

/**
 * Fires before the display of member settings template.
 *
 * @since 1.5.0
 */
do_action( 'bp_before_member_settings_template' ); ?>

<form action="<?php echo trailingslashit( bp_displayed_user_domain() . bp_get_settings_slug() . '/profile' ); ?>" method="post" class="standard-form" id="settings-form">

	<?php if ( bp_xprofile_get_settings_fields() ) : ?>

		<?php while ( bp_profile_groups() ) : bp_the_profile_group(); ?>

			<?php if ( bp_profile_fields() ) : ?>

				<table class="profile-settings" id="xprofile-settings-<?php bp_the_profile_group_slug(); ?>">
					<thead>
						<tr>
							<th class="title field-group-name"><?php bp_the_profile_group_name(); ?></th>
							<th class="title"><?php _e( 'Visibility', 'buddypress' ); ?></th>
						</tr>
					</thead>

					<tbody>

						<?php while ( bp_profile_fields() ) : bp_the_profile_field(); ?>

							<tr <?php bp_field_css_class(); ?>>
								<td class="field-name"><?php bp_the_profile_field_name(); ?></td>
								<td class="field-visibility"><?php bp_profile_settings_visibility_select(); ?></td>
							</tr>

						<?php endwhile; ?>

					</tbody>
				</table>

			<?php endif; ?>

		<?php endwhile; ?>

	<?php endif; ?>

	<?php

	/**
	 * Fires before the display of the submit button for user profile saving.
	 *
	 * @since 2.0.0
	 */
	do_action( 'bp_core_xprofile_settings_before_submit' ); ?>

	<div class="submit">
		<input id="submit" type="submit" name="xprofile-settings-submit" value="<?php esc_attr_e( 'Save Settings', 'buddypress' ); ?>" class="auto" />
	</div>

	<?php

	/**
	 * Fires after the display of the submit button for user profile saving.
	 *
	 * @since 2.0.0
	 */
	do_action( 'bp_core_xprofile_settings_after_submit' ); ?>

	<?php wp_nonce_field( 'bp_xprofile_settings' ); ?>

	<input type="hidden" name="field_ids" id="field_ids" value="<?php bp_the_profile_field_ids(); ?>" />

</form>

<?php

/**
 * Fires after the display of member settings template.
 *
 * @since 1.5.0
 */
do_action( 'bp_after_member_settings_template' );
