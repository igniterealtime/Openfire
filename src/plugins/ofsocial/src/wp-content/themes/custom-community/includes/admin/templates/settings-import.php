<?php
/**
 * cc2 Template: Import settings
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0
 */

?>

	<div class="backup-settings settings-import">
		<p><?php _e('Import theme settings from the format of your choice.', 'cc2'); ?></p>
		
	<?php if( !empty($available_formats) ) : ?>
		<p><label><?php _e('Select import format:', 'cc2'); ?>
			<select name="import_format">
		<?php foreach( $available_formats as $strFormat => $strFormatLabel ) : ?>
			<option value="<?php echo $strFormat; ?>" <?php selected( $strFormat, $strImportFormat ); ?>><?php echo $strFormatLabel; ?></option>
		<?php endforeach; ?>
			</select>
		</label></p>
		
	<?php endif; ?>
	
<?php if( empty($import_result) ) : ?>
		<p class="description"><label for="field-import-data"><?php _e('Copy + paste the data you want to import into the following text field:', 'cc2'); ?></label></p>
		
		<p><textarea class="large-text" rows="10" cols="50" name="import_data" id="field-import-data"></textarea>
	
		
	<?php 
	/**
	  * Set submit button with name = backup_action and value = import
	  * NOTE: When clicking this button, only THIS value is being sent, other submit button values are IGNORED.
	  */
	
	proper_submit_button( __('Start Import', 'cc2'), 'primary large', 'backup_action', true, array('value' => 'import', 'id' => 'init-settings-import')  ); ?>
<?php else : ?>

	<?php if( !empty( $import_result ) && is_array( $import_result ) ) : ?>
		<div class="updated">
			<p><?php _e('Successfully imported all settings.', 'cc2'); ?></p>
		</div>
		<p class="import-message">
			<strong><?php printf(__('%s the following data:', 'cc2'), 'Imported'); ?></strong>
		</p>	
		
		<ul class="import-result-list">
		<?php foreach( $import_result as $importedItem => $arrItemData ) :
			$importedItemKey = (is_int( $importedItem ) ? $importedItem+1 : $importedItem );
		 ?>
			<li>
				<span class="import-result-count"><?php echo sprintf( ( is_int($importedItem ) ? '#%d - ' : '%s: ' ), $importedItemKey ); ?></span> <span class="import-result-title"><?php echo $arrItemData['title']; ?></span> <span class="import-result-amount"><?php 
				if( !empty($arrItemData['original_number'] ) ) {
					printf( __( '(%1$d/%2$d items)', 'cc2' ), $arrItemData['number'], $arrItemData['original_number'] );
				} else {
					printf( __( '(%d items)', 'cc2' ), $arrItemData['number'] );
				} ?></span> 
				<a href="#import-result-<?php echo $importedItemKey . '-' . $arrItemData['number']; ?>" class="import-result-details-link"><?php _e('Display details', 'cc2'); ?></a>
				<div class="import-result-details" id="import-result-<?php echo $importedItemKey . '-' . $arrItemData['number']; ?>" style="display:none">
					<pre><?php print_r( $arrItemData['test_data_result'] ); ?></pre>
				</div>
				
				</li>
		<?php endforeach; ?>
		</ul>
		
		
		
	<?php else: ?>
		
	<?php endif; ?>
		
<?php endif; ?>
			
	</div>
