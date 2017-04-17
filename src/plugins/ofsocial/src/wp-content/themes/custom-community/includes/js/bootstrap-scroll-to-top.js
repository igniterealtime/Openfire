/**
 * Scroll-to-top button
 * Inspired by @link http://stackoverflow.com/questions/22413203/bootstrap-affix-back-to-top-link
 * 
 * @author Fabian Wolf
 * @package cc2
 * @since 2.0r1
 */

jQuery(document).ready(function() {
	var options = {
		top_offset: 100,
		slide_offset: 100,
		display_effect: 'slide_into_view',
		scroll_top_speed: 'slow',
		scroll_to_top: 0,
		scroll_top_position: 0,
		
	};
	if( typeof scroll_to_top_settings == 'object' ) {
		console.log( 'got the settings');
		jQuery.each( scroll_to_top_settings, function( settingName, settingValue ) { 
			console.log( 'setting', settingName, 'value', settingValue, 'vs.', options[settingName] )
		} );
	}
		
	//if( jQuery(window).height() + options.top_offset < jQuery(document).height() ) {
		//console.info(jQuery(window).height(), '+', options.top_offset, '=', jQuery(window).height() + options.top_offset, '<', jQuery(document).height() )
	if( ( jQuery(window).height() + options.top_offset ) < jQuery(document).height() ) {
		// activate
		jQuery('#top-link-block').removeClass('hidden').affix({
			// how far to scroll down before link "slides" into view
			offset: { top: options.slide_offset }
		});
	}
	
	jQuery( document ).on('click', '#top-link-block #top-link-button-link', function(e) {
		e.preventDefault();
		
		console.log( 'scroll-to-top fires');
		jQuery('html,body').animate({scrollTop: options.scroll_top_position}, options.scroll_top_speed);
		//return false;
    });
	
});
