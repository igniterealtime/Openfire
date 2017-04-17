<?php
/**
 * cc2 Template: Edit custom CSS
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0
 */

?>
	<p><?php _e('Add, edit or remove custom CSS for the current theme. Will be added <strong>after</strong> the CSS issued by the Customizer and just about everything else.'); ?></p>

	<p><textarea name="custom_css" id="edit-custom-css" rows="10" cols="30" class="large-text"><?php if( !empty( $custom_css ) ) {
		echo $custom_css;
	} ?></textarea></p>

