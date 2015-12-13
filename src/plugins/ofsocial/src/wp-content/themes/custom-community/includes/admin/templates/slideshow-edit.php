<?php
/**
 * cc2 Template: Manage Slideshow
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0
 */
 
//new __debug( $cc_slider_options, 'slideshows' );
?>
	<p>Add, reorder or remove the slides of a slideshow!</p>

	<p><select name="select_slides_list" id="select_slides_list">
		<option value='-1'>Select the Slideshow</option>
		
	<?php	
	if( !empty( $cc_slider_options ) ) {
		//$arrSlideshows = array_keys( $cc_slider_options );
		
		foreach ($cc_slider_options as $slide_slug => $slide_data) {
			if( !empty( $slide_slug ) ) {
				/**
				 * FIXME: Some troubles are starting here
				 */
				
				
				$slide_title = ( isset( $slide_data['title'] ) && !empty( $slide_data['title'] ) ? $slide_data['title'] : $slide_slug );
			
	?>
		<option value="<?php echo $slide_slug; ?>" data-slug="<?php echo $slide_slug; ?>"><?php echo $slide_title; ?></option>
	<?php 	}
		}
	} ?>
	</select></p>

	<div id="display_slides_list"></div>

	<p><button name="safety_switch" id="reset-slideshows" class="button button-reset button-delete">Reset all slideshows</button></p>

	<p class="description"><strong>Note:</strong> <strong>ALL</strong> and any changes you make to the current slideshow will be <strong>automatically</strong> saved. So no matter what you do, all is still gonna be well ;-)</p>
