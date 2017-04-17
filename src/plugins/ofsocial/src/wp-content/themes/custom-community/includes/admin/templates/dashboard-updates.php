<?php
/**
 * cc2 Template: Dashboard/Updates
 *
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0r1
 */


?>
<div class="section section-update-info">
	<h3><?php _e('Import old theme options', 'cc2'); ?></h3>
	
	<p><?php _e('Before you start exploring the new theme, you might want to import the options of your <strong>old theme</strong> (cc v1).', 'cc2'); ?></p>

	
<?php if( $update_options ) : ?>
	<div class="theme-select-update-options">
		<p><?php _e('Found the following data - optionally <strong>DEselect</strong> what you don NOT want to import:', 'cc2'); ?></p>
	
		<ul class="import-settings">
	<?php 
	
	foreach( $update_options as $strOptionKey => $strOptionDescription ) :
		$update_data_checked = true;
		
		// is there some form data around = form was sent?
		
		if( $_POST[ 'update_action' ] == 'yes' && !isset( $_POST['update_data'][ $strOptionKey ] ) ) { // was UNchecked
			$update_data_checked = false;
		}
		
		// result from the actual importing action
		
		if( $update_result && !empty( $update_result[ $strOptionKey ] ) ) {
			$strMessageClass = 'message-error';
			
			if( stripos( $update_result[ $strOptionKey]['type'], 'success' ) !== false ) {
				$strMessageClass = 'message-success';
				$update_data_checked = false;
			}
		}
	 ?>
			<li>
				<label><input type="checkbox" <?php checked( true, $update_data_checked ); ?> name="update_data[<?php echo $strOptionKey; ?>]" value="yes" /> <?php echo $strOptionDescription ?></label>
			
			<?php if( $strMessageClass ) { ?>
				<span class="<?php echo $strMessageClass; ?>"><?php echo $update_result[ $strOptionKey ]['message'];
				if( $strMessageClass == 'message-error' ) {
					?> <em class="message-note"><?php _e('Note: Empty settings may have been discarded.', 'cc2'); ?></em><?php
				}
				?></span>
			<?php } ?>
			</li>
	<?php endforeach; ?>
		</ul>
	</div>
<?php endif; ?>
	
	<p>
		<label>
			<input type="checkbox" name="update_test_run" value="yes" <?php checked( $_POST['update_test_run'], 'yes' ); ?> />
			<?php _e('Test run', 'cc2'); ?>
		</label>
	
		<?php proper_submit_button( __('Import Old Options', 'cc2'), 'primary large', 'update_action', false, array('value' => 'yes', 'id' => 'init-update-script')  );  ?>
	</p>
	
</div>

