<?php
/**
 * cc2 Template: Additional bootstrap niceties
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0
 * 
 * Settings:
 * 
 * - smartmenus.js
 * - scroll-top button 
 * 		=> http://stackoverflow.com/questions/22413203/bootstrap-affix-back-to-top-link
 * 		=> http://markgoodyear.com/labs/scrollup/?theme=link
 * 		=> further styling: premium extension
 */
	
?>
	<div class="advanced-settings-bootstrap">
		<p><?php _e('A few settings for enabling optional components for Bootstrap.', 'cc2'); ?></p>


		<!-- switch: smartmenus.js enable/disable -->
		<?php
		/**
		 * NOTE: Postponed till 2.1 - needs more adjustments in the CSS part ..
		 */
		/*
		<p><label><input type="checkbox" name="load_smartmenus_js" <?php checked( $options['load_smartmenus_js'], 1); ?> value="1" /> <?php _e('Enable <a href="http://www.smartmenus.org/blog/smartmenus/create-advanced-bootstrap-3-navbars-zero-config/">Smartmenus.js</a> (enhanced drop-down navigation)', 'cc2'); ?></label></p>
		*/
		?>
		
		<!-- switch: hover-dropdown enable/disable -->
		<p><label><input type="checkbox" name="load_hover_dropdown_css" <?php checked( $options['load_hover_dropdown_css'], 1 ); ?> value="1" /> <?php echo sprintf( __('Enable simple CSS-based dropdown hover menus', 'cc2'), 'https://github.com/CWSpear/bootstrap-hover-dropdown' ); ?></label></p>
		
		
		<!-- switch: scroll-top button enable/disable -->
		<p><label><input type="checkbox" name="load_scroll_top" <?php checked( $options['load_scroll_top'], 1 ); ?> value="1" /> <?php _e('Enable scroll-to-top button', 'cc2'); ?></label></p>
		
	<?php if( defined('CC2_THEME_DEBUG') ) : ?>
		<!-- enable test.js -->
		<p><label><input type="checkbox" name="load_test_js" <?php checked( $options['load_test_js'], 1 ); ?> value="1" /> <?php echo sprintf( __('Enable %s', 'cc2'), 'test.js' ); ?></label></p>
	<?php endif; ?>
	</div>


