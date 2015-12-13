<?php
/**
 * BuddyPress - Members Profile Change Cover Image
 *
 * @package BuddyPress
 * @subpackage bp-legacy
 */

?>

<h4><?php _e( 'Change Cover Image', 'buddypress' ); ?></h4>

<?php

/**
 * Fires before the display of profile cover image upload content.
 *
 * @since 2.4.0
 */
do_action( 'bp_before_profile_edit_cover_image' ); ?>

<p><?php _e( 'Your Cover Image will be used to customize the header of your profile.', 'buddypress' ); ?></p>

<?php bp_attachments_get_template_part( 'cover-images/index' ); ?>

<?php

/**
 * Fires after the display of profile cover image upload content.
 *
 * @since 2.4.0
 */
do_action( 'bp_after_profile_edit_cover_image' ); ?>
