/* exported clear */

( function( $ ) {
	// Profile Visibility Settings

	$( '.visibility-toggle-link' ).on( 'click', function( event ) {
		event.preventDefault();

		$( this ).parent().hide()
			.siblings( '.field-visibility-settings' ).show();
	} );

	$( '.field-visibility-settings-close' ).on( 'click', function( event ) {
		event.preventDefault();

		var settings_div = $(this).parent(),
		vis_setting_text = settings_div.find( 'input:checked' ).parent().text();

		settings_div.hide()
			.siblings( '.field-visibility-settings-toggle' )
				.children( '.current-visibility-level' ).text( vis_setting_text ).end()
			.show();
	} );

} )( jQuery );


/**
 * Deselects any select options or input options for the specified field element.
 *
 * @param {String} container HTML ID of the field
 * @since 1.0.0
 */
function clear( container ) {
	container = document.getElementById( container );
	if ( ! container ) {
		return;
	}

	var radioButtons = container.getElementsByTagName( 'INPUT' ),
		options = container.getElementsByTagName( 'OPTION' ),
		i       = 0;

	if ( radioButtons ) {
		for ( i = 0; i < radioButtons.length; i++ ) {
			radioButtons[i].checked = '';
		}
	}

	if ( options ) {
		for ( i = 0; i < options.length; i++ ) {
			options[i].selected = false;
		}
	}
}
