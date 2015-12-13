jQuery(document).ready( function() {
	jQuery('.widget div#friends-list-options a').on('click',
		function() {
			var link = this;
			jQuery(link).addClass('loading');

			jQuery('.widget div#friends-list-options a').removeClass('selected');
			jQuery(this).addClass('selected');

			jQuery.post( ajaxurl, {
				action: 'widget_friends',
				'cookie': encodeURIComponent(document.cookie),
				'_wpnonce': jQuery('input#_wpnonce-friends').val(),
				'max-friends': jQuery('input#friends_widget_max').val(),
				'filter': jQuery(this).attr('id')
			},
			function(response)
			{
				jQuery(link).removeClass('loading');
				friend_widget_response(response);
			});

			return false;
		}
	);
});

function friend_widget_response(response) {
	response = response.substr(0, response.length-1);
	response = response.split('[[SPLIT]]');

	if ( response[0] !== '-1' ) {
		jQuery('.widget ul#friends-list').fadeOut(200,
			function() {
				jQuery('.widget ul#friends-list').html(response[1]);
				jQuery('.widget ul#friends-list').fadeIn(200);
			}
		);

	} else {
		jQuery('.widget ul#friends-list').fadeOut(200,
			function() {
				var message = '<p>' + response[1] + '</p>';
				jQuery('.widget ul#friends-list').html(message);
				jQuery('.widget ul#friends-list').fadeIn(200);
			}
		);
	}
}
