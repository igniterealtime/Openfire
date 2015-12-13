/**
 * Some helpers for the Themekraft support system
 *
 * @author Fabian Wolf
 */
 
/**
 * @link https://gist.github.com/mathiasbynens/1197140
 */
 
// Load scripts asynchronously
jQuery.loadAsync = function(url, callback) {
	// Don't use $.getScript since it disables caching
	jQuery.ajax({
		'url': url,
		'dataType': 'script',
		'cache': true,
		'success': callback || jQuery.noop
	});	
};
 
// on-DOM-ready
jQuery(function() {
	if( typeof tk_support_settings != 'undefined' ) {
		var settings = tk_support_settings.zendesk;


		// brutal style
		//jQuery('<link rel="stylesheet" type="text/css" href="'+ settings.css +'" id="tk-support-zenbox-css">').appendTo("head");
		jQuery.ajax({
			url: settings.css,
			dataType: 'css',
			success: function() {
				jQuery.loadAsync( settings.js, function() {
					
					if (typeof(Zenbox) != 'undefined') {
						Zenbox.init({
							dropboxID:   "20204802",
							url:         "https://themekraft.zendesk.com",
							tabTooltip:  "Support",
							tabColor:    "black",
							tabPosition: "Left",
							hide_tab: true
						});
					}
					
					jQuery('#cc2_get_personal_help').on('click', function() {
						var action = jQuery(this);

						/**
						 * TODO: Replace with jQuery UI modal
						 */
						confirm('Get personal help by the theme authors as soon as you purchase the CC2 Premium Pack.');

						jQuery('#cc2_get_more').css('background', 'papayawhip');
					})
					
				} );
			}
				
		});

	}
});
