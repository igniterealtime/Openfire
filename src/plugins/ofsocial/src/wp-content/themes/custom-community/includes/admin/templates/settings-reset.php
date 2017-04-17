<?php
/**
 * cc2 Template: Reset settings
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0
 */

?>

	<div class="backup-settings settings-reset">
		<p><?php echo __('Reset all or just selected settings.', 'cc2'); ?></p>
				
	<?php if( !empty($data_items) ) :
		$settings_field_name = 'reset';
	
		include ( get_template_directory() . '/includes/admin/templates/part-settings-select.php' ); ?>

		<?php 
		proper_submit_button( __('Reset settings', 'cc2'), 'delete', 'backup_action', false, array('value' => 'reset', 'id' => 'init-settings-reset') );
		?>
		
	<?php endif; ?>

	<?php if( isset($result_reset) ) : ?>
	<div id="reset-result">
		<p><?php echo sprintf(__('%s the following data:', 'cc2'), 'Reset'); ?></p>
		
		<ul class="reset-messages">
		<?php foreach( $result_reset as $resetItem => $arrItemData ) : ?>
			<li><?php echo sprintf( ( is_int($resetItem) ? '#%d - ' : '%s: ' ), (is_int( $resetItem) ? $resetItem+1 : $resetItem ) ) . $arrItemData['title']; ?></li>
			<!-- <?php echo $resetItem; ?>: <?php print_r( $arrItemData); ?>  -->
		<?php endforeach; ?>
		</ul>
	</div>
	<?php endif; ?>
			
