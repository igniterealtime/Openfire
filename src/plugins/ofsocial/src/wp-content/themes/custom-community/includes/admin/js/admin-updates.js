/**
 * Theme update: Hook up "run updates" button
 * Script will only be loaded if theme (options) updates are pending.
 *
 * @author Fabian Wolf
 * @since 2.0r1
 * @package cc2
 */
 
jQuery(function() {
	// 
	jQuery('#run-theme-updates').after('<span id="theme-update-result-message" class="theme-update-result"></span>');
	
	jQuery( document ).on('click', '#run-theme-updates', function() {
		jQuery.ajax({
			dataType: 'json',
            url: ajaxurl,
            data: {
				'run_theme_updates': '1', 
			},
            success: function(data ) {
				jQuery('#theme-update-result-message').text( data.message );
                ///jQuery("#select_slides_list").val(select_slides_list);
                //jQuery("#select_slides_list").trigger('change');
            },
            error: function() {
				jQuery('#theme-update-result-message').text( data.message );
				
                console.info('Something went wrong.. ;-(sorry)');
            }
        });
	});
	
});
