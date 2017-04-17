<?php
/**
 * Template part: Settings
 *
 * @author Fabian Wolf
 * @since 2.0-rc2
 * @package cc2
 */
if( ! empty( $settings_field_name ) && !empty( $data_items) ) {

?>

		<fieldset id="<?php echo sprintf('select-%s-settings', $settings_field_name ); ?>">
			<legend><?php _e('Choose settings:', 'cc2'); ?></legend>
		
			<ul>
			<?php foreach( $data_items as $strItemType => $arrItemAttributes ) : ?>
			
				<li><label><input type="checkbox" class="<?php printf('field-%s-items', $settings_field_name); ?>" name="<?php printf('%s_items[]', $settings_field_name); ?>"<?php 
				if( !empty( $_POST[ $settings_field_name . '_items'] ) && in_array( $strItemType, $_POST[ $settings_field_name . '_items'] ) ) {
					echo ' checked="checked" ';
				} ?> value="<?php echo $strItemType; ?>" /> <span><?php echo $arrItemAttributes['title']; ?></span></label></li>
			
			<?php endforeach; ?>
			</ul>
			
		</fieldset>

<?php 

}
