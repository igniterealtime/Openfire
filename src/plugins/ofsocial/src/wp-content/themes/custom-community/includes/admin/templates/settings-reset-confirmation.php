<?php
/**
 * cc2 Template: Reset settings confirmation
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0-rc2
 */

?>

	<div class="backup-settings settings-reset-confirmation">
	<?php if( !empty( $reset_items ) ) { ?>
		<input type="hidden" name="backup_action" value="reset" />
		
		<p><?php _e('Do you really want to reset the following settings?', 'cc2'); ?></p>
		
		<ol class="reset-items-list">
		<?php foreach( $reset_items as $iItemCount => $strItemID ) : ?>
			<?php if( array_key_exists( $strItemID, $data_items ) ) : ?>
				<li><?php echo $data_items[$strItemID]['title']; ?> <input type="hidden" name="reset_item[]" value="<?php echo $strItemID; ?>" /></li>
				
			<?php endif; ?>
		<?php endforeach; ?>
		</ol>
		
		
		<p><?php proper_submit_button(  __('No, abort now!', 'cc2'), 'abort large', 'settings_reset_confirm', false, array('value' => 'nope', 'id' => 'init-settings-confirm-abort') ); ?> &nbsp; <?php 
		proper_submit_button( __('Yes, please reset!', 'cc2'), 'delete large', 'settings_reset_confirm', false, array('value' => 'yes', 'id' => 'init-settings-confirm-continue') ); ?>
		</p>
		
	<?php } else { ?>
		
		<p><?php _e('Nothing to do here. NO settings selected.', 'cc2'); ?></p>
	<?php } ?>
	</div>
			
