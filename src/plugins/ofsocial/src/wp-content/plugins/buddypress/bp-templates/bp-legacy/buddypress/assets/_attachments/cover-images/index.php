<?php
/**
 * BuddyPress Cover Images main template.
 *
 * This template is used to inject the BuddyPress Backbone views
 * dealing with cover images.
 *
 * It's also used to create the common Backbone views.
 *
 * @since 2.4.0
 *
 * @package BuddyPress
 * @subpackage bp-attachments
 */

?>

<div class="bp-cover-image"></div>
<div class="bp-cover-image-status"></div>
<div class="bp-cover-image-manage"></div>

<?php bp_attachments_get_template_part( 'uploader' ); ?>

<script id="tmpl-bp-cover-image-delete" type="text/html">
	<# if ( 'user' === data.object ) { #>
		<p><?php _e( "If you'd like to delete your current cover image but not upload a new one, please use the delete Cover Image button.", 'buddypress' ); ?></p>
		<p><a class="button edit" id="bp-delete-cover-image" href="#" title="<?php esc_attr_e( 'Delete Cover Image', 'buddypress' ); ?>"><?php esc_html_e( 'Delete My Cover Image', 'buddypress' ); ?></a></p>
	<# } else if ( 'group' === data.object ) { #>
		<p><?php _e( "If you'd like to remove the existing group cover image but not upload a new one, please use the delete group cover image button.", 'buddypress' ); ?></p>
		<p><a class="button edit" id="bp-delete-cover-image" href="#" title="<?php esc_attr_e( 'Delete Cover Image', 'buddypress' ); ?>"><?php esc_html_e( 'Delete Group Cover Image', 'buddypress' ); ?></a></p>
	<# } else { #>
		<?php do_action( 'bp_attachments_cover_image_delete_template' ); ?>
	<# } #>
</script>

<?php do_action( 'bp_attachments_cover_image_main_template' ); ?>
