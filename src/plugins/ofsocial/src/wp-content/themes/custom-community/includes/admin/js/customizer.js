/**
 * Theme Customizer enhancements for a better user experience.
 * Contains handlers to make Theme Customizer preview reload changes asynchronously.
 * In other words: this file here handles the live preview 
 * 
 * Also @see wp-admin/js/customize-controls.js ;)
 */ 

( function( exports, $ ) {
	var api = parent.wp.customize;
	
	// Site Title and Description.
	wp.customize( 'blogname', function( value ) {
		value.bind( function( to ) {
			while( value !== to) {
			$( '.site-title a' ).text( to );
			}
		} );
	} );
	wp.customize( 'blogdescription', function( value ) {
		value.bind( function( to ) {
			$( '.site-description' ).text( to );
		} );
	} );
		
	
	
	// Header Title Color.
	wp.customize( 'header_titlecolor', function( value ) {
		value.bind( function( to ) {
			if ( 'blank' === to ) {
				$( '.site-title a, .site-description' ).css( {
					'clip': 'rect(1px, 1px, 1px, 1px)',
					'position': 'absolute'
				} );
			} else {
				$( '.site-title a, .site-description' ).css( {
					'clip': 'auto',
					'position': 'relative'
				} );
				$( '.site-title a' ).css( {
					'color': to
				} );
			}
		} );
	} );

	// Header Text Color.
	wp.customize( 'header_textcolor', function( value ) {
		value.bind( function( to ) {
			if ( 'blank' === to ) {
				$( '.site-title a, .site-description' ).css( {
					'clip': 'rect(1px, 1px, 1px, 1px)',
					'position': 'absolute'
				} );
			} else {
				$( '.site-title a, .site-description' ).css( {
					'clip': 'auto',
					'color': to,
					'position': 'relative'
				} );
			}
		} );
	} );
	
	// Container Color.
	wp.customize( 'container_color', function( value ) {
		value.bind( function( to ) {
			if ( '' === to ) {
				$( '.container' ).css( {
					'background-color': 'transparent'
				} );
			} else {
				$( '.container' ).css( {
					'background-color': to
				} );
			}
		} );
	} );
	
	// Header Background Color.
	wp.customize( 'header_background_color', function( value ) {
		value.bind( function( to ) {
			if ( '' === to ) {
				$( '.site-header' ).css( {
					'background-color': 'transparent'
				} );
			} else {
				$( '.site-header' ).css( {
					'background-color': to
				} );
			}
		} );
	} );
	

	
	// some tests
	
	//console.log( api.instance('footer_fullwidth_border_bottom_color').get() );
	/*var btn_ColorPickerTransparency = api.control.instance('footer_fullwidth_border_bottom_color').selector + ' .color-picker-set-transparent';
	jQuery( document ).on( 'click', btn_ColorPickerTransparency, function( elem ) {
		//elem.preventDefault();
		
		
	} );
 	*/
	/*wp.customize.CustomColorControl = wp.customize.Control.extend({
		ready: function() {
			var control = this,
				picker = this.container.find('.color-picker-hex');

			picker.val( control.setting() ).wpColorPicker({
				change: function() {
					control.setting.set( picker.wpColorPicker('color') );
				},
				clear: function() {
					control.setting.set( false );
				}
			});
			
			jQuery('.color-picker-set-transparent').on('click', function() {
				console.info( 'reset cc2_colorPicker to transparent');
			})
			
			
		}
	});*/
	
	
	// Font Color.
	// wp.customize( 'font_color', function( value ) {
		// value.bind( function( to ) {
			// $( 'body, p' ).css( {
				// 'color': to
			// } );
		// } );
	// } );
	
	// Font Family.
	wp.customize( 'font_family', function( value ) {
		value.bind( function( to ) {
			$( 'body, p' ).css( {
				'font-family': to
			} );
		} );
	} );

	// Link Color.
	// wp.customize( 'link_color', function( value ) {
		// value.bind( function( to ) {
			// $( 'a' ).css( {
				// 'color': to
			// } );
		// } );
	// } );
	
	// Title Font Family.
	wp.customize( 'title_font_family', function( value ) {
		value.bind( function( to ) {
			$( 'h1, h2, h3, h4, h5, h6' ).css( {
				'font-family': to
			} );
		} );
	} );

		
} )( wp, jQuery );
