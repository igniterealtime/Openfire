<?php
/**
 * cc2 Template: Export settings
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0
 */

?>

	<div class="backup-settings settings-export">
		<p><?php echo sprintf( __('Export the current theme settings into the %s.', 'cc2'), $strFormatOfChoice ); ?></p>
		
	<?php if( !empty($available_formats) ) : ?>
		<p>
			<label><?php _e('Select export format:', 'cc2'); ?>
				<select name="export_format">
		<?php foreach( $available_formats as $strFormat => $strFormatLabel ) : ?>
					<option value="<?php echo $strFormat; ?>" <?php selected( $strFormat, $strExportFormat ); ?>><?php echo $strFormatLabel; ?></option>
		<?php endforeach; ?>
				</select>
			</label>
		</p>
		
		<?php if( $data_items ) :
			$settings_field_name = 'export';
	
			include_once( get_template_directory() . '/includes/admin/templates/part-settings-select.php' );
			
		endif; ?>

		
		<?php 
		/**
		  * Set submit button with name = backup_action and value = export
		  * NOTE: When clicking this button, only THIS value is being sent, other submit button values are IGNORED.
		  */

		
		//submit_button( __('Start Export', 'cc2'), 'primary large', 'backup_action', false, array('value' => 'export', 'id' => 'init-settings-export') ); 
		proper_submit_button( __('Start Export', 'cc2'), 'primary large', 'backup_action', false, array('value' => 'export', 'id' => 'init-settings-export') );
		
		?>
	<?php endif; ?>

	<?php if( !empty($export_data) ) : ?>
		<div class="result">
			<p>
				<label class="result-title" for="export-data-result" data-for-id="export-data-result">Exported settings:</label> 
				<textarea class="large-text" rows="15" cols="45" id="export-data-result"><?php echo trim($export_data); ?></textarea><br />		
				<a class="" href="#select-export-result" id="select-export-result"><?php _e('Select All', 'cc2'); ?></a>
			</p>
		</div>
	<?php endif; ?>
			
	</div>
