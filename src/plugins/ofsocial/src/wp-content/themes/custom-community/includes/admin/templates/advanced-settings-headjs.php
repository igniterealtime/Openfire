<?php
/**
 * cc2 Template: A few settings for HeadJS support
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0
 */
 
	
?>

	<div class="advanced-settings-headjs">
		<p><?php _e('Fine-tune <a href="http://headjs.com/">HeadJS</a>, the responsive webdesign JS helper.', 'cc2'); ?></p>


		<!-- switch: default redux version enable/disable -->
		<p><label><input type="radio" <?php checked( $options['headjs_type'], 'redux'); ?> name="headjs_type" value="redux" /> <?php _e('Load reduced HeadJS version (no async. loading)', 'cc2'); ?></label></p>
		
		<!-- switch: default redux version enable/disable -->
		<p><label><input type="radio" <?php checked( $options['headjs_type'], 'full' ); ?> name="headjs_type" value="full" /> <?php _e('Enable full HeadJS version', 'cc2'); ?></label></p>
		
		<div class="settings-enable-full-headjs">
			<p><?php _e('Load from URL:', 'cc2'); ?> <input type="text" class="regular-text" name="headjs_url" value="<?php _notempty( $options['headjs_url'] ); ?>" /></label></p>
			
			<p class="description"><?php _e('If you don\'t add a URL, the full HeadJS version shipped together with the theme is automatically being used. Also, to improve handling of local scripts, you may use the following keywords inside the URL field, which are automatically replaced with their respective URL parts: <a href="http://codex.wordpress.org/template_directory_uri">%template_directory_uri%</a>, <a href="http://codex.wordpress.org/stylesheet_directory_uri">%stylesheet_directory_uri%</a>', 'cc2'); ?></p>
		</div>
		
		<!-- switch: completely disable headjs -->
		<p><label><input type="radio" <?php checked( $options['headjs_type'], 'none' ); ?> name="headjs_type" value="none" /> <?php _e('Completely disable HeadJS', 'cc2'); ?></label></p>
		
	</div>
